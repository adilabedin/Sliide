package com.androidlearning.sliide.users.data.repository

import com.androidlearning.sliide.core.database.DatabaseTransactionRunner
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingSyncDao
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
import com.androidlearning.sliide.users.domain.UserSyncScheduler
import com.androidlearning.sliide.users.domain.model.RemoteUser
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
 * Sync requests (spec: background-sync): whenever a write leaves a pending entry, [syncScheduler] is asked to replay
 * that user's entry in the background, after the queuing transaction commits. Success, rejection and a CREATE + DELETE
 * that cancel out leave no entry and request nothing.
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
    private val syncScheduler: UserSyncScheduler,
) : UserRepository {
    private val writeMutex = Mutex()
    private val syncStateMutex = Mutex()
    private var inFlightSync: Deferred<EmptyResult<DataError.Remote>>? = null

    /**
     * Temp ids whose CREATE synced in this process, mapped to their server id, so a delete still holding the temp id
     * (e.g. issued after its Undo window) reaches the server user. Guarded by [writeMutex]; in memory, like
     * [lastIssuedTempId], because stale temp-id references don't survive process death.
     */
    private val rekeyedTempIds = mutableMapOf<Long, Long>()

    /**
     * Last temp id handed out by this process. [UserDao.nextTempId] only looks at current rows, so after `-1` is
     * re-keyed it would hand out `-1` again, and a stale UI still holding `User(-1)` could edit or delete the new
     * user. Issuing below this value keeps temp ids unique per process; stale references don't survive process death.
     * Guarded by [writeMutex].
     */
    private var lastIssuedTempId = 0L

    override fun observeUsers(): Flow<List<User>> = userDao.observeAll().map { entities -> entities.map { it.toUser() } }

    // Only a failed request queues a write. If the app dies mid-request the write is lost and the next
    // refresh shows the server's data (here: removes the temp user); accepted as too rare to cover.
    override suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError> =
        outlivingCaller {
            writeMutex.withLock {
                val tempId = minOf(userDao.nextTempId(), lastIssuedTempId - 1)
                lastIssuedTempId = tempId
                val tempEntity =
                    details.toUserEntity(id = tempId, createdAtEpochMillis = nowMillis())
                userDao.upsert(tempEntity)

                when (val result = apiClient.create(details)) {
                    is Result.Success -> {
                        rekey(tempEntity, result.data)
                        Result.Success(WriteStatus.SYNCED)
                    }

                    is Result.Failure ->
                        when (OutboxPolicy.decide(PendingOperation.CREATE, result.error)) {
                            OutboxDecision.KEEP -> {
                                pendingSyncDao.upsert(
                                    tempEntity.toPendingSync(
                                        PendingOperation.CREATE,
                                        queuedAtEpochMillis = nowMillis(),
                                    ),
                                )
                                syncScheduler.requestSync(tempEntity.id)
                                Result.Success(WriteStatus.QUEUED)
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
                val previous =
                    userDao.getById(user.id)
                        ?: return@withLock Result.Failure(DataError.Local.NOT_FOUND)

                // The row keeps its original createdAt so the feed order and relative time don't jump on edit.
                val updated =
                    user.toUserEntity().copy(createdAtEpochMillis = previous.createdAtEpochMillis)

                if (user.id < 0) {
                    // Never-synced user: no API call, the change folds into its pending CREATE (spec: offline-sync).
                    // A temp user with no pending CREATE (its POST was cut off by a killed process) is only edited locally.
                    val queued =
                        transactionRunner.inTransaction {
                            userDao.upsert(updated)

                            val hasPendingCreate = pendingSyncDao.getByUserId(user.id) != null

                            if (hasPendingCreate) enqueue(updated, PendingOperation.UPDATE)

                            hasPendingCreate
                        }

                    if (queued) syncScheduler.requestSync(user.id)

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
                                transactionRunner.inTransaction {
                                    enqueue(
                                        updated,
                                        PendingOperation.UPDATE,
                                    )
                                }
                                syncScheduler.requestSync(user.id)
                                Result.Success(WriteStatus.QUEUED)
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

    override suspend fun deleteUser(user: User): Result<WriteStatus, DataError> =
        outlivingCaller {
            writeMutex.withLock {
                // A temp id whose CREATE synced meanwhile (e.g. during the Undo window) is deleted under its server id.
                val target =
                    rekeyedTempIds[user.id]?.let { serverId -> user.copy(id = serverId) } ?: user

                if (target.id < 0) {
                    // Never-synced user: cancel its pending CREATE; the server never hears about it.
                    transactionRunner.inTransaction {
                        userDao.deleteById(target.id)
                        pendingSyncDao.deleteByUserId(target.id)
                    }
                    return@withLock Result.Success(WriteStatus.SYNCED)
                }

                // Server-known user: deleted locally, then on the server. Only a failed request queues the delete (see
                // [createUser]). writeMutex is held until the request finishes, so replay can't send an older entry
                // for this user meanwhile.
                userDao.deleteById(target.id)

                when (val result = apiClient.delete(target.id)) {
                    // Deleted: the queued DELETE (and anything older) is done.
                    is Result.Success -> {
                        pendingSyncDao.deleteByUserId(target.id)
                        Result.Success(WriteStatus.SYNCED)
                    }

                    is Result.Failure ->
                        when (OutboxPolicy.decide(PendingOperation.DELETE, result.error)) {
                            // Already gone on the server: done, as on success.
                            OutboxDecision.GONE -> {
                                pendingSyncDao.deleteByUserId(target.id)
                                Result.Success(WriteStatus.SYNCED)
                            }

                            OutboxDecision.KEEP -> {
                                transactionRunner.inTransaction {
                                    enqueue(target.toUserEntity(), PendingOperation.DELETE)
                                }
                                syncScheduler.requestSync(target.id)
                                Result.Success(WriteStatus.QUEUED)
                            }
                            // Not rolled back: any older pending entry goes, and the next refresh brings the user back.
                            OutboxDecision.REJECT -> {
                                pendingSyncDao.deleteByUserId(target.id)
                                Result.Failure(result.error)
                            }
                        }
                }
            }
        }

    /**
     * One pass sends every queued entry once, oldest first, one at a time (design D4). A failure [OutboxPolicy] keeps
     * leaves that entry queued for the next sync and the pass carries on; the first kept error is returned. Sync never
     * rolls the cached users back: the next [refresh] replaces the list with the server's.
     *
     * Already outlives its caller: the pass is started in [applicationScope] and callers only await it, so a cancelled
     * caller stops waiting while the pass (shared by every concurrent caller) runs on.
     */
    override suspend fun syncPending(): EmptyResult<DataError.Remote> {
        val pass =
            syncStateMutex.withLock {
                inFlightSync?.takeIf { it.isActive }
                    ?: applicationScope
                        .async {
                            pendingSyncDao
                                .getAllOrdered()
                                .map { retrySync(it.user.id) }
                                .firstOrNull { it is Result.Failure } ?: Result.Success(Unit)
                        }.also { inFlightSync = it }
            }
        return pass.await()
    }

    /**
     * Sends only [userId]'s entry (spec: offline-sync, single-user replay). No single-flight of its own: [retrySync]'s
     * lock and re-read already stop a full pass and this call from sending the same entry twice.
     */
    override suspend fun syncPending(userId: Long): EmptyResult<DataError.Remote> = outlivingCaller { retrySync(userId) }

    /**
     * Refresh = replay first, then replace the cache with the last page. A failed replay returns its error without
     * fetching. The page is only fetched with the queue empty, so it never overwrites a write the server hasn't seen;
     * an entry queued after the replay started (e.g. a joined background pass that read the queue earlier) is synced
     * by running refresh again first.
     */
    override suspend fun refresh(): EmptyResult<DataError> =
        outlivingCaller {
            val sync = syncPending()

            if (sync is Result.Failure) return@outlivingCaller Result.Failure(sync.error)

            val fetched: EmptyResult<DataError>? =
                writeMutex.withLock {
                    // Queued after this replay started: sync it before fetching. Null runs refresh again below.
                    if (pendingSyncDao.count() > 0) return@withLock null

                    when (val page = apiClient.fetchLastPage()) {
                        is Result.Failure -> Result.Failure(page.error)
                        is Result.Success -> {
                            // Replaced only when the page differs; users already cached keep their createdAt.
                            val cached = userDao.getAll()
                            val fetchedKeys =
                                page.data.map { it.toUserEntity(createdAtEpochMillis = 0) }.toSet()
                            val cachedKeys =
                                cached.map { it.copy(createdAtEpochMillis = 0) }.toSet()

                            if (fetchedKeys != cachedKeys) {
                                val createdAtById =
                                    cached.associate { it.id to it.createdAtEpochMillis }
                                val now = nowMillis()
                                userDao.replaceAll(
                                    page.data.map {
                                        it.toUserEntity(
                                            createdAtById[it.id] ?: now,
                                        )
                                    },
                                )
                            }
                            Result.Success(Unit)
                        }
                    }
                }

            // Outside writeMutex: refresh's replay takes it, and Mutex isn't reentrant.
            fetched ?: refresh()
        }

    /**
     * Runs [block] in [applicationScope] and awaits it (spec: operation-lifetime). Cancelling the caller cancels only
     * this await: the operation still completes its local writes, its request and any rollback or queueing. The scope's
     * `SupervisorJob` delivers a failure to the awaiting caller without cancelling sibling operations.
     */
    private suspend fun <T> outlivingCaller(block: suspend () -> T): T = applicationScope.async { block() }.await()

    /**
     * Sends [userId]'s pending entry, if any, under [writeMutex]. Shared by the full pass and the per-user sync, so both
     * apply exactly the same rules. The entry is re-read under the lock: a user write or another replay may have merged
     * into or removed it meanwhile, which is what keeps an entry from being sent twice.
     *
     * Fails only with the error of a write [OutboxPolicy] keeps queued. A sent, rejected or missing entry is done.
     */
    private suspend fun retrySync(userId: Long): EmptyResult<DataError.Remote> =
        writeMutex.withLock {
            // Already sent or merged away by someone else.
            val entry = pendingSyncDao.getByUserId(userId) ?: return@withLock Result.Success(Unit)
            val user = entry.user

            // A successful create or update returns the server's user; a delete returns null.
            val result: Result<RemoteUser?, DataError> =
                when (entry.operation) {
                    PendingOperation.CREATE -> apiClient.create(user.toUserDetails())
                    PendingOperation.UPDATE -> apiClient.update(user.id, user.toUserDetails())
                    PendingOperation.DELETE ->
                        apiClient
                            .delete(user.id)
                            .let { it as? Result.Failure ?: Result.Success(null) }
                }

            when (result) {
                // Sent: a created user is re-keyed to its server id, an updated one takes the server's data, and the
                // entry is cleared.
                is Result.Success -> {
                    val serverUser = result.data

                    when (entry.operation) {
                        PendingOperation.CREATE if serverUser != null ->
                            rekey(
                                user,
                                serverUser,
                            )

                        PendingOperation.UPDATE if serverUser != null ->
                            transactionRunner.inTransaction {
                                userDao
                                    .getById(user.id)
                                    ?.let { userDao.upsert(serverUser.toUserEntity(it.createdAtEpochMillis)) }
                                pendingSyncDao.deleteByUserId(user.id)
                            }

                        else -> pendingSyncDao.deleteByUserId(user.id)
                    }
                    Result.Success(Unit)
                }

                is Result.Failure ->
                    if (OutboxPolicy.decide(entry.operation, result.error) == OutboxDecision.KEEP) {
                        // Stays queued. KEEP is only decided for remote errors; the fallback keeps the type total.
                        Result.Failure(
                            result.error as? DataError.Remote ?: DataError.Remote.UNKNOWN,
                        )
                    } else {
                        // Rejected, or already gone on the server: the entry is dropped and the cache left to refresh.
                        pendingSyncDao.deleteByUserId(user.id)
                        Result.Success(Unit)
                    }
            }
        }

    /** Swaps a temp-id user for the server's user in one transaction and clears its pending CREATE. */
    private suspend fun rekey(
        temp: UserEntity,
        serverUser: RemoteUser,
    ) {
        transactionRunner.inTransaction {
            val createdAt =
                userDao.getById(temp.id)?.createdAtEpochMillis ?: temp.createdAtEpochMillis
            userDao.deleteById(temp.id)
            userDao.upsert(serverUser.toUserEntity(createdAt))
            pendingSyncDao.deleteByUserId(temp.id)
        }
        rekeyedTempIds[temp.id] = serverUser.id
    }

    /**
     * Merges a write into the user's pending entry (D2). Must run inside a transaction. Every caller leaves an entry: the
     * one merge that cancels (CREATE + DELETE) only happens for a temp id, which [deleteUser] handles before this.
     */
    private suspend fun enqueue(
        user: UserEntity,
        operation: PendingOperation,
    ) {
        val existing = pendingSyncDao.getByUserId(user.id)
        val merged =
            PendingSyncMerger.merge(
                existing,
                user.toPendingSync(operation, queuedAtEpochMillis = nowMillis()),
            )
        if (merged == null) pendingSyncDao.deleteByUserId(user.id) else pendingSyncDao.upsert(merged)
    }

    private fun nowMillis(): Long = clock.now().toEpochMilliseconds()
}
