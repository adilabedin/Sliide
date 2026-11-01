package com.androidlearning.sliide.users.data.repository

import com.androidlearning.sliide.core.database.DatabaseTransactionRunner
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingSyncDao
import com.androidlearning.sliide.core.database.PendingSyncEntity
import com.androidlearning.sliide.core.database.UserDao
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.users.data.mapper.toPendingSync
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.data.mapper.toUserDetails
import com.androidlearning.sliide.users.data.mapper.toUserEntity
import com.androidlearning.sliide.users.data.sync.PendingSyncMerger
import com.androidlearning.sliide.users.domain.UserRemoteDataSource
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.domain.util.DataError
import com.androidlearning.sliide.users.domain.util.EmptyResult
import com.androidlearning.sliide.users.domain.util.Result
import com.androidlearning.sliide.users.domain.util.isRetryable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Offline-first [UserRepository]: Room is the single source of truth, every write lands there first, and writes
 * that fail for a retryable reason wait in the `pending_sync` outbox until [syncPending] replays them.
 *
 * Concurrency (design D5/D7):
 * - [writeMutex] is held for each user write (local step plus one HTTP call) and for each replayed entry, never for
 *   the whole queue, so a user write waits for at most one in-flight replay request.
 * - [syncPending] is single-flight: the running pass is a [Deferred] started in [syncScope], and concurrent callers
 *   await that same pass. Because it lives in [syncScope] rather than in the caller's scope, cancelling one caller
 *   (for example a background task the OS stops) never cancels the pass for the others. Tests inject `backgroundScope`.
 */
class OfflineFirstUserRepository(
    private val userDao: UserDao,
    private val pendingSyncDao: PendingSyncDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val remote: UserRemoteDataSource,
    private val clock: Clock,
    private val syncScope: CoroutineScope = CoroutineScope(SupervisorJob()),
) : UserRepository {
    private val writeMutex = Mutex()
    private val syncStateMutex = Mutex()
    private var inFlightSync: Deferred<Result<SyncReport, DataError.Remote>>? = null

    /**
     * Users removed locally whose delete is neither finalised nor undone yet (the Undo window). Replay skips their
     * entries and refresh does not replace the cache while any exist. Guarded by [writeMutex]; deliberately in memory,
     * so after a process death the skipped entries simply replay.
     */
    private val awaitingFinalize = mutableSetOf<Long>()

    /**
     * Last temp id handed out by this process. [UserDao.nextTempId] only looks at current rows, so after `-1` is
     * re-keyed it would hand out `-1` again, and a stale UI still holding `User(-1)` could edit or delete the new
     * user. Issuing below this value keeps temp ids unique per process; stale references don't survive process death.
     * Guarded by [writeMutex].
     */
    private var lastIssuedTempId = 0L

    override fun observeUsers(): Flow<List<User>> = userDao.observeAll().map { entities -> entities.map { it.toUser() } }

    override suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError> =
        writeMutex.withLock {
            val now = nowMillis()
            // The temp row and its pending CREATE are written together, so a crash mid-call never loses the create.
            val tempEntity =
                transactionRunner.inTransaction {
                    val tempId = minOf(userDao.nextTempId(), lastIssuedTempId - 1)
                    lastIssuedTempId = tempId
                    val entity = details.toUserEntity(id = tempId, createdAtEpochMillis = now)
                    userDao.upsert(entity)
                    pendingSyncDao.upsert(entity.toPendingSync(PendingOperation.CREATE, queuedAtEpochMillis = now))
                    entity
                }
            when (val result = remote.create(details)) {
                is Result.Success -> {
                    rekey(tempEntity, result.data)
                    Result.Success(WriteStatus.SYNCED)
                }
                is Result.Failure -> {
                    if (result.error.isRetryable) {
                        Result.Success(WriteStatus.QUEUED)
                    } else {
                        transactionRunner.inTransaction {
                            userDao.deleteById(tempEntity.id)
                            pendingSyncDao.deleteByUserId(tempEntity.id)
                        }
                        Result.Failure(result.error)
                    }
                }
            }
        }

    override suspend fun updateUser(user: User): Result<WriteStatus, DataError> =
        writeMutex.withLock {
            val previous = userDao.getById(user.id) ?: return@withLock Result.Failure(DataError.Local.NOT_FOUND)
            // The row keeps its original createdAt so the feed order and relative time don't jump on edit.
            val updated = user.toUserEntity().copy(createdAtEpochMillis = previous.createdAtEpochMillis)

            if (user.id < 0) {
                // Never-synced user: no API call, the change folds into its pending CREATE (spec: offline-sync).
                transactionRunner.inTransaction {
                    userDao.upsert(updated)
                    enqueue(updated, PendingOperation.UPDATE)
                }
                return@withLock Result.Success(WriteStatus.QUEUED)
            }

            // Outbox first: the local edit and its merged pending UPDATE commit together before the request, so a
            // cancelled caller or a process death mid-request leaves the edit queued instead of silently unprotected.
            val previousPending = applyLocallyAndEnqueue(updated, PendingOperation.UPDATE) { userDao.upsert(updated) }
            when (val result = remote.update(user.id, updated.toUserDetails())) {
                is Result.Success -> {
                    transactionRunner.inTransaction {
                        userDao.upsert(result.data.toUserEntity(previous.createdAtEpochMillis))
                        // A successful direct write supersedes anything older still queued for this user.
                        pendingSyncDao.deleteByUserId(user.id)
                    }
                    Result.Success(WriteStatus.SYNCED)
                }
                is Result.Failure -> {
                    when {
                        result.error.isRetryable -> Result.Success(WriteStatus.QUEUED)
                        // The user no longer exists on the server: drop it locally together with any pending entry.
                        result.error == DataError.Remote.NOT_FOUND -> {
                            transactionRunner.inTransaction {
                                userDao.deleteById(user.id)
                                pendingSyncDao.deleteByUserId(user.id)
                            }
                            Result.Failure(result.error)
                        }
                        else -> {
                            transactionRunner.inTransaction {
                                userDao.upsert(previous)
                                restorePending(user.id, previousPending)
                            }
                            Result.Failure(result.error)
                        }
                    }
                }
            }
        }

    override suspend fun removeUserLocally(id: Long): Result<User, DataError.Local> =
        writeMutex.withLock {
            val entity = userDao.getById(id) ?: return@withLock Result.Failure(DataError.Local.NOT_FOUND)
            userDao.deleteById(id)
            awaitingFinalize += id
            Result.Success(entity.toUser())
        }

    override suspend fun restoreUser(user: User) {
        writeMutex.withLock {
            userDao.upsert(user.toUserEntity())
            awaitingFinalize -= user.id
        }
    }

    override suspend fun finalizeDelete(user: User): Result<WriteStatus, DataError> =
        writeMutex.withLock {
            val snapshot = user.toUserEntity()

            if (user.id < 0) {
                // Never-synced user: cancel its pending CREATE; the server never hears about it.
                transactionRunner.inTransaction {
                    userDao.deleteById(user.id)
                    pendingSyncDao.deleteByUserId(user.id)
                }
                awaitingFinalize -= user.id
                return@withLock Result.Success(WriteStatus.SYNCED)
            }

            // Outbox first: the pending DELETE commits before the request and before the Undo-window guard is lifted
            // (both under writeMutex, so replay can't run in between), so the delete is always protected by one of them.
            val previousPending = applyLocallyAndEnqueue(snapshot, PendingOperation.DELETE) { userDao.deleteById(user.id) }
            awaitingFinalize -= user.id

            val result = remote.delete(user.id)
            when {
                result is Result.Success || (result is Result.Failure && result.error == DataError.Remote.NOT_FOUND) -> {
                    // Already gone on the server counts as success; the queued DELETE (and anything older) is done.
                    pendingSyncDao.deleteByUserId(user.id)
                    Result.Success(WriteStatus.SYNCED)
                }
                result is Result.Failure && result.error.isRetryable -> Result.Success(WriteStatus.QUEUED)
                else -> {
                    transactionRunner.inTransaction {
                        userDao.upsert(snapshot)
                        restorePending(user.id, previousPending)
                    }
                    Result.Failure((result as Result.Failure).error)
                }
            }
        }

    override suspend fun syncPending(): Result<SyncReport, DataError.Remote> {
        val pass =
            syncStateMutex.withLock {
                inFlightSync?.takeIf { it.isActive }
                    ?: syncScope.async { replayQueue() }.also { inFlightSync = it }
            }
        return pass.await()
    }

    /**
     * Refresh = replay first, then replace the cache with the last page. The cache is replaced only when nothing is
     * left to sync: no queued entries (skipped Undo-window entries count) and no user inside its Undo window. In that
     * case refresh returns success without fetching, because the cache is still the correct local truth.
     */
    override suspend fun refresh(): EmptyResult<DataError> {
        val sync = syncPending()
        if (sync is Result.Failure) return Result.Failure(sync.error)

        return writeMutex.withLock {
            if (pendingSyncDao.count() > 0 || awaitingFinalize.isNotEmpty()) return@withLock Result.Success(Unit)

            when (val page = remote.fetchLastPage()) {
                is Result.Failure -> Result.Failure(page.error)
                is Result.Success -> {
                    replaceCacheIfChanged(page.data)
                    Result.Success(Unit)
                }
            }
        }
    }

    private suspend fun replayQueue(): Result<SyncReport, DataError.Remote> {
        var synced = 0
        var rejected = 0
        for ((user) in pendingSyncDao.getAllOrdered()) {
            val outcome =
                writeMutex.withLock {
                    // Re-read under the lock: a user write may have merged into or removed this entry meanwhile.
                    val entry = pendingSyncDao.getByUserId(user.id) ?: return@withLock ReplayOutcome.Gone
                    if (entry.user.id in awaitingFinalize) return@withLock ReplayOutcome.Skipped
                    replay(entry)
                }
            when (outcome) {
                ReplayOutcome.Synced -> synced++
                ReplayOutcome.Rejected -> rejected++
                ReplayOutcome.Skipped, ReplayOutcome.Gone -> Unit
                is ReplayOutcome.Retry -> return Result.Failure(outcome.error)
            }
        }
        return Result.Success(SyncReport(synced = synced, rejected = rejected))
    }

    private suspend fun replay(entry: PendingSyncEntity): ReplayOutcome {
        val userId = entry.user.id
        val result: Result<RemoteUser?, DataError.Remote> =
            when (entry.operation) {
                PendingOperation.CREATE -> remote.create(entry.user.toUserDetails())
                PendingOperation.UPDATE -> remote.update(userId, entry.user.toUserDetails())
                PendingOperation.DELETE ->
                    when (val deleted = remote.delete(userId)) {
                        is Result.Success -> Result.Success(null)
                        is Result.Failure ->
                            if (deleted.error == DataError.Remote.NOT_FOUND) Result.Success(null) else deleted
                    }
            }

        return when (result) {
            is Result.Success -> {
                val serverUser = result.data
                when (entry.operation) {
                    PendingOperation.CREATE if serverUser != null -> rekey(entry.user, serverUser)
                    PendingOperation.UPDATE if serverUser != null ->
                        transactionRunner.inTransaction {
                            userDao.getById(userId)?.let { userDao.upsert(serverUser.toUserEntity(it.createdAtEpochMillis)) }
                            pendingSyncDao.deleteByUserId(userId)
                        }

                    else -> pendingSyncDao.deleteByUserId(userId)
                }
                ReplayOutcome.Synced
            }
            is Result.Failure -> {
                if (result.error.isRetryable) return ReplayOutcome.Retry(result.error)
                transactionRunner.inTransaction {
                    pendingSyncDao.deleteByUserId(userId)
                    when (entry.operation) {
                        PendingOperation.CREATE -> userDao.deleteById(userId)
                        PendingOperation.DELETE -> userDao.upsert(entry.user)
                        PendingOperation.UPDATE ->
                            // NOT_FOUND: the user no longer exists on the server, so drop it locally. Any other
                            // rejection keeps the local edit; the server's last state isn't stored, so the next
                            // refresh corrects it.
                            if (result.error == DataError.Remote.NOT_FOUND) userDao.deleteById(userId) else Unit
                    }
                }
                ReplayOutcome.Rejected
            }
        }
    }

    /** Swaps a temp-id user for the server's user in one transaction and clears its pending CREATE. */
    private suspend fun rekey(
        temp: UserEntity,
        serverUser: RemoteUser,
    ) {
        transactionRunner.inTransaction {
            val createdAt = userDao.getById(temp.id)?.createdAtEpochMillis ?: temp.createdAtEpochMillis
            userDao.deleteById(temp.id)
            userDao.upsert(serverUser.toUserEntity(createdAt))
            pendingSyncDao.deleteByUserId(temp.id)
        }
    }

    /**
     * In one transaction: runs [applyLocally] and merges [operation] for [user] into the outbox. Returns the pending
     * entry as it was before, so a permanent failure can restore it exactly with [restorePending].
     */
    private suspend fun applyLocallyAndEnqueue(
        user: UserEntity,
        operation: PendingOperation,
        applyLocally: suspend () -> Unit,
    ): PendingSyncEntity? =
        transactionRunner.inTransaction {
            val previousPending = pendingSyncDao.getByUserId(user.id)
            applyLocally()
            enqueue(user, operation)
            previousPending
        }

    /** Puts the user's pending entry back to [previous], or removes it if there was none. Run inside a transaction. */
    private suspend fun restorePending(
        userId: Long,
        previous: PendingSyncEntity?,
    ) {
        if (previous == null) pendingSyncDao.deleteByUserId(userId) else pendingSyncDao.upsert(previous)
    }

    /** Merges a write into the user's pending entry (D2). Must run inside a transaction. */
    private suspend fun enqueue(
        user: UserEntity,
        operation: PendingOperation,
    ) {
        val existing = pendingSyncDao.getByUserId(user.id)
        val merged = PendingSyncMerger.merge(existing, user.toPendingSync(operation, queuedAtEpochMillis = nowMillis()))
        if (merged == null) pendingSyncDao.deleteByUserId(user.id) else pendingSyncDao.upsert(merged)
    }

    private suspend fun replaceCacheIfChanged(page: List<RemoteUser>) {
        val cached = userDao.getAll()
        val fetchedKeys = page.map { it.toUserEntity(createdAtEpochMillis = 0) }.toSet()
        val cachedKeys = cached.map { it.copy(createdAtEpochMillis = 0) }.toSet()
        if (fetchedKeys == cachedKeys) return

        val createdAtById = cached.associate { it.id to it.createdAtEpochMillis }
        val now = nowMillis()
        userDao.replaceAll(page.map { it.toUserEntity(createdAtById[it.id] ?: now) })
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

    private sealed interface ReplayOutcome {
        data object Synced : ReplayOutcome

        data object Rejected : ReplayOutcome

        data object Skipped : ReplayOutcome

        data object Gone : ReplayOutcome

        data class Retry(
            val error: DataError.Remote,
        ) : ReplayOutcome
    }
}
