package com.androidlearning.sliide.users.data.repository

import com.androidlearning.sliide.core.database.DatabaseTransactionRunner
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingSyncDao
import com.androidlearning.sliide.core.database.PendingSyncEntity
import com.androidlearning.sliide.core.database.UserDao
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.mapper.toPendingSync
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.data.mapper.toUserDetails
import com.androidlearning.sliide.users.data.mapper.toUserEntity
import com.androidlearning.sliide.users.data.sync.OutboxDecision
import com.androidlearning.sliide.users.data.sync.OutboxPolicy
import com.androidlearning.sliide.users.data.sync.PendingSyncMerger
import com.androidlearning.sliide.users.domain.UserApiClient
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Offline-first [UserRepository]: Room is the single source of truth, every write lands there first, and writes
 * that fail in a way that may later succeed wait in the `pending_sync` outbox until [syncPending] replays them.
 *
 * Failure handling (design D4): [OutboxPolicy] alone decides, from the operation and the [DataError], whether a failed
 * write is kept, rejected or already gone. A rejected write only loses its pending entry; the next [refresh] replaces
 * the cache with the server's list. The one exception is a rejected direct create, whose temp user is removed at once
 * because the form stays open for a corrected resubmit.
 *
 * Concurrency (design D5/D7):
 * - [writeMutex] is held for each user write (local step plus one HTTP call) and for each replayed entry, never for
 *   the whole queue, so a user write waits for at most one in-flight replay request.
 * - [syncPending] is single-flight: the running pass is a [Deferred] started in [applicationScope], and concurrent
 *   callers await that same pass, so cancelling one caller never cancels the pass for the others.
 *
 * Lifetime (spec: operation-lifetime): every suspending operation runs in [applicationScope], the process-wide scope
 * the application creates, and the caller only awaits it. A ViewModel cleared mid-request therefore never leaves the
 * cache, the outbox and the server half-updated. [observeUsers] stays a cold flow bound to its collector. Tests inject
 * `backgroundScope`, or a child scope they cancel to simulate a process kill.
 */
class OfflineFirstUserRepository(
    private val userDao: UserDao,
    private val pendingSyncDao: PendingSyncDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val apiClient: UserApiClient,
    private val clock: Clock,
    private val applicationScope: CoroutineScope,
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
        outlivingCaller {
            writeMutex.withLock {
                val now = nowMillis()
                val tempId = minOf(userDao.nextTempId(), lastIssuedTempId - 1)
                lastIssuedTempId = tempId
                val tempEntity = details.toUserEntity(id = tempId, createdAtEpochMillis = now)
                userDao.upsert(tempEntity)
                // Only a failed request queues a write. If the app dies mid-request the write is lost and the next
                // refresh shows the server's data (here: removes the temp user); accepted as too rare to cover.
                when (val result = apiClient.create(details)) {
                    is Result.Success -> {
                        rekey(tempEntity, result.data)
                        Result.Success(WriteStatus.SYNCED)
                    }
                    is Result.Failure ->
                        when (OutboxPolicy.decide(PendingOperation.CREATE, result.error)) {
                            OutboxDecision.KEEP -> {
                                pendingSyncDao.upsert(tempEntity.toPendingSync(PendingOperation.CREATE, queuedAtEpochMillis = now))
                                Result.Success(queuedStatus(result.error))
                            }
                            // GONE is never decided for a create; like a rejection, the temp user goes.
                            OutboxDecision.REJECT, OutboxDecision.GONE -> {
                                userDao.deleteById(tempEntity.id)
                                Result.Failure(result.error)
                            }
                        }
                }
            }
        }

    override suspend fun updateUser(user: User): Result<WriteStatus, DataError> =
        outlivingCaller {
            writeMutex.withLock {
                val previous = userDao.getById(user.id) ?: return@withLock Result.Failure(DataError.Local.NOT_FOUND)
                // The row keeps its original createdAt so the feed order and relative time don't jump on edit.
                val updated = user.toUserEntity().copy(createdAtEpochMillis = previous.createdAtEpochMillis)

                if (user.id < 0) {
                    // Never-synced user: no API call, the change folds into its pending CREATE (spec: offline-sync).
                    // A temp user with no pending CREATE (its POST was cut off by a killed process) is only edited locally.
                    transactionRunner.inTransaction {
                        userDao.upsert(updated)
                        if (pendingSyncDao.getByUserId(user.id) != null) enqueue(updated, PendingOperation.UPDATE)
                    }
                    return@withLock Result.Success(WriteStatus.QUEUED)
                }

                // Only a failed request queues the edit (see [createUser]); the cache shows it straight away.
                userDao.upsert(updated)
                when (val result = apiClient.update(user.id, updated.toUserDetails())) {
                    is Result.Success -> {
                        transactionRunner.inTransaction {
                            userDao.upsert(result.data.toUserEntity(previous.createdAtEpochMillis))
                            // A successful direct write supersedes anything older still queued for this user.
                            pendingSyncDao.deleteByUserId(user.id)
                        }
                        Result.Success(WriteStatus.SYNCED)
                    }
                    is Result.Failure ->
                        when (OutboxPolicy.decide(PendingOperation.UPDATE, result.error)) {
                            OutboxDecision.KEEP -> {
                                transactionRunner.inTransaction { enqueue(updated, PendingOperation.UPDATE) }
                                Result.Success(queuedStatus(result.error))
                            }
                            // The user no longer exists on the server: drop it locally together with any pending entry.
                            OutboxDecision.GONE -> {
                                transactionRunner.inTransaction {
                                    userDao.deleteById(user.id)
                                    pendingSyncDao.deleteByUserId(user.id)
                                }
                                Result.Failure(DataError.Remote.NOT_FOUND)
                            }
                            // Not rolled back: any older pending entry goes, and the next refresh restores the server's data.
                            OutboxDecision.REJECT -> {
                                pendingSyncDao.deleteByUserId(user.id)
                                Result.Failure(result.error)
                            }
                        }
                }
            }
        }

    override suspend fun removeUserLocally(id: Long): Result<User, DataError.Local> =
        outlivingCaller {
            writeMutex.withLock {
                val entity = userDao.getById(id) ?: return@withLock Result.Failure(DataError.Local.NOT_FOUND)
                userDao.deleteById(id)
                awaitingFinalize += id
                Result.Success(entity.toUser())
            }
        }

    override suspend fun restoreUser(user: User) {
        outlivingCaller {
            writeMutex.withLock {
                userDao.upsert(user.toUserEntity())
                awaitingFinalize -= user.id
            }
        }
    }

    override suspend fun finalizeDelete(user: User): Result<WriteStatus, DataError> =
        outlivingCaller {
            writeMutex.withLock {
                if (user.id < 0) {
                    // Never-synced user: cancel its pending CREATE; the server never hears about it.
                    transactionRunner.inTransaction {
                        userDao.deleteById(user.id)
                        pendingSyncDao.deleteByUserId(user.id)
                    }
                    awaitingFinalize -= user.id
                    return@withLock Result.Success(WriteStatus.SYNCED)
                }

                // Only a failed request queues the delete (see [createUser]). The Undo-window guard can be lifted now:
                // writeMutex is held until the request finishes, so replay can't send an older entry for this user meanwhile.
                userDao.deleteById(user.id)
                awaitingFinalize -= user.id

                val decision =
                    when (val result = apiClient.delete(user.id)) {
                        is Result.Success -> null
                        is Result.Failure -> OutboxPolicy.decide(PendingOperation.DELETE, result.error) to result.error
                    }
                when (decision?.first) {
                    // Deleted, or already gone on the server: the queued DELETE (and anything older) is done.
                    null, OutboxDecision.GONE -> {
                        pendingSyncDao.deleteByUserId(user.id)
                        Result.Success(WriteStatus.SYNCED)
                    }
                    OutboxDecision.KEEP -> {
                        transactionRunner.inTransaction { enqueue(user.toUserEntity(), PendingOperation.DELETE) }
                        Result.Success(queuedStatus(decision.second))
                    }
                    // Not rolled back: any older pending entry goes, and the next refresh brings the user back.
                    OutboxDecision.REJECT -> {
                        pendingSyncDao.deleteByUserId(user.id)
                        Result.Failure(decision.second)
                    }
                }
            }
        }

    /**
     * Already outlives its caller: the pass is started in [applicationScope] and callers only await it, so a cancelled
     * caller stops waiting while the pass (shared by every concurrent caller) runs on.
     */
    override suspend fun syncPending(): Result<SyncReport, DataError.Remote> {
        val pass =
            syncStateMutex.withLock {
                inFlightSync?.takeIf { it.isActive }
                    ?: applicationScope.async { sendQueuedWrites() }.also { inFlightSync = it }
            }
        return pass.await()
    }

    /**
     * Refresh = replay first, then replace the cache with the last page. The cache is replaced only when nothing is
     * left to sync: no queued entries (skipped Undo-window entries count) and no user inside its Undo window. In that
     * case refresh returns success without fetching, because the cache is still the correct local truth.
     */
    override suspend fun refresh(): EmptyResult<DataError> =
        outlivingCaller {
            val sync = syncPending()
            if (sync is Result.Failure) return@outlivingCaller Result.Failure(sync.error)

            writeMutex.withLock {
                if (pendingSyncDao.count() > 0 || awaitingFinalize.isNotEmpty()) return@withLock Result.Success(Unit)

                when (val page = apiClient.fetchLastPage()) {
                    is Result.Failure -> Result.Failure(page.error)
                    is Result.Success -> {
                        replaceCacheIfChanged(page.data)
                        Result.Success(Unit)
                    }
                }
            }
        }

    /**
     * Runs [block] in [applicationScope] and awaits it (spec: operation-lifetime). Cancelling the caller cancels only
     * this await: the operation still completes its local writes, its request and any rollback or queueing. The scope's
     * `SupervisorJob` delivers a failure to the awaiting caller without cancelling sibling operations.
     */
    private suspend fun <T> outlivingCaller(block: suspend () -> T): T = applicationScope.async { block() }.await()

    /**
     * Sends every queued entry once, oldest first, one at a time under [writeMutex] (design D4). A success clears the
     * entry; a failure [OutboxPolicy] keeps leaves it queued for the next sync and the pass carries on; any other failure
     * only deletes the entry. Returns the first kept error, if any. Sync never rolls the cached users back: the next
     * [refresh] replaces the list with the server's.
     */
    private suspend fun sendQueuedWrites(): Result<SyncReport, DataError.Remote> {
        var synced = 0
        var rejected = 0
        var firstKeptError: DataError.Remote? = null
        for ((user) in pendingSyncDao.getAllOrdered()) {
            writeMutex.withLock {
                // Re-read under the lock: a user write may have merged into or removed this entry meanwhile.
                val entry = pendingSyncDao.getByUserId(user.id) ?: return@withLock
                // Inside its Undo window: left queued until it is finalised or undone.
                if (entry.user.id in awaitingFinalize) return@withLock
                when (val result = send(entry)) {
                    is Result.Success -> {
                        clearSynced(entry, result.data)
                        synced++
                    }
                    is Result.Failure -> {
                        val decision = OutboxPolicy.decide(entry.operation, result.error)
                        if (decision == OutboxDecision.KEEP) {
                            // KEEP is only decided for remote errors; the fallback keeps the type total.
                            if (firstKeptError == null) firstKeptError = result.error as? DataError.Remote ?: DataError.Remote.UNKNOWN
                            return@withLock
                        }
                        pendingSyncDao.deleteByUserId(entry.user.id)
                        // A delete of a user already gone on the server is done; everything else was rejected.
                        if (decision == OutboxDecision.GONE && entry.operation == PendingOperation.DELETE) synced++ else rejected++
                    }
                }
            }
        }
        return firstKeptError?.let { Result.Failure(it) } ?: Result.Success(SyncReport(synced = synced, rejected = rejected))
    }

    /** Sends one queued entry. A successful create or update returns the server's user; a delete returns null. */
    private suspend fun send(entry: PendingSyncEntity): Result<RemoteUser?, DataError> =
        when (entry.operation) {
            PendingOperation.CREATE -> apiClient.create(entry.user.toUserDetails())
            PendingOperation.UPDATE -> apiClient.update(entry.user.id, entry.user.toUserDetails())
            PendingOperation.DELETE -> apiClient.delete(entry.user.id).let { if (it is Result.Failure) it else Result.Success(null) }
        }

    /** Clears a sent entry; a created user is re-keyed to its server id and an updated one takes the server's data. */
    private suspend fun clearSynced(
        entry: PendingSyncEntity,
        serverUser: RemoteUser?,
    ) {
        val userId = entry.user.id
        when {
            entry.operation == PendingOperation.CREATE && serverUser != null -> rekey(entry.user, serverUser)
            entry.operation == PendingOperation.UPDATE && serverUser != null ->
                transactionRunner.inTransaction {
                    userDao.getById(userId)?.let { userDao.upsert(serverUser.toUserEntity(it.createdAtEpochMillis)) }
                    pendingSyncDao.deleteByUserId(userId)
                }
            else -> pendingSyncDao.deleteByUserId(userId)
        }
    }

    /** A kept write's status: an authentication failure is reported separately so the UI can say why (design D6). */
    private fun queuedStatus(error: DataError): WriteStatus =
        if (error == DataError.Remote.UNAUTHORIZED) WriteStatus.QUEUED_AUTH_FAILED else WriteStatus.QUEUED

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
}
