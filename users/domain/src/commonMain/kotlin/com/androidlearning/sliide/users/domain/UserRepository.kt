package com.androidlearning.sliide.users.domain

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to users. The local cache is the single source of truth: every write lands in it before
 * the network call. Failures that may later succeed (no connectivity, timeout, 5xx, 429, 401/403) are queued for a
 * later [syncPending]. A rejected write loses its pending entry and returns the typed [DataError]; only a rejected
 * create is also removed locally, and the next [refresh] corrects anything else.
 */
interface UserRepository {
    /** Users from the local cache, newest first. Emits again whenever the cache changes. */
    fun observeUsers(): Flow<List<User>>

    /**
     * Inserts the user locally under a temporary negative id, then creates it on the server.
     * [WriteStatus.SYNCED]: re-keyed to the server id. [WriteStatus.QUEUED] / [WriteStatus.QUEUED_AUTH_FAILED]: kept
     * under the temp id and queued. Failure (e.g. [DataError.Validation]): the local insert is rolled back.
     */
    suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError>

    /**
     * Writes the change locally, then updates it on the server. A user with a temporary id is never sent; the
     * change is merged into its pending create and returns [WriteStatus.QUEUED].
     * A failure that may later succeed queues the change ([WriteStatus.QUEUED], or [WriteStatus.QUEUED_AUTH_FAILED]
     * for 401/403). Failure: the pending entry is dropped and the edit stays in the cache until the next [refresh],
     * except for [DataError.Remote.NOT_FOUND], where the user no longer exists on the server and is removed from the
     * cache. An uncached user fails with [DataError.Local.NOT_FOUND].
     */
    suspend fun updateUser(user: User): Result<WriteStatus, DataError>

    /**
     * Removes the user from the cache only (no network call) and returns its snapshot for Undo. Replay skips the
     * user until [restoreUser] or [finalizeDelete] is called. Fails with [DataError.Local.NOT_FOUND] if absent.
     *
     * Contract: every successful call MUST be followed by exactly one [restoreUser] (Undo) or [finalizeDelete]
     * (Snackbar dismissed). Until then, replay skips this user's pending entry and [refresh] will not replace the
     * cache, for the lifetime of the process.
     */
    suspend fun removeUserLocally(id: Long): Result<User, DataError.Local>

    /**
     * Undo: puts a locally removed user back exactly as it was. No network call. Ends the Undo window opened by
     * [removeUserLocally]; call it instead of, never in addition to, [finalizeDelete].
     */
    suspend fun restoreUser(user: User)

    /**
     * Commits a delete after the Undo window. Sends DELETE for a server-known user (404 counts as success) or
     * queues it on a failure that may later succeed (including 401/403, as [WriteStatus.QUEUED_AUTH_FAILED]); a
     * temp-id user just cancels its pending create without any network call.
     * Failure (e.g. [DataError.Validation], GoRest refusing the delete): the pending entry is dropped and the user stays
     * removed until the next [refresh] brings it back.
     *
     * Ends the Undo window opened by [removeUserLocally]; call it instead of, never in addition to, [restoreUser].
     * Launch it in a scope that outlives the screen (an application-level scope, not `viewModelScope`): if it never
     * runs, replay keeps skipping this user and [refresh] stays blocked for the rest of the process.
     */
    suspend fun finalizeDelete(user: User): Result<WriteStatus, DataError>

    /**
     * Sends every pending entry once, oldest first, one at a time. An entry that fails in a way that may later succeed
     * stays queued for the next sync and the pass carries on; the first such error is returned.
     * A rejected entry is only deleted from the queue and counted in [SyncReport.rejected]; the cached users are left
     * as they are until the next [refresh] replaces the list with the server's. Concurrent calls share one pass.
     */
    suspend fun syncPending(): Result<SyncReport, DataError.Remote>

    /**
     * Sends at most [userId]'s pending entry, with the same success, rejection and Undo-window rules as [syncPending],
     * and leaves every other entry untouched. With no entry for [userId] it sends nothing and reports zero. It may run
     * alongside a full pass; no entry is ever sent twice.
     */
    suspend fun syncPending(userId: Long): Result<SyncReport, DataError.Remote>

    /**
     * Runs [syncPending]; only if the queue is then empty, fetches the last page of `/users` and replaces the cache
     * when it differs. On a replay failure the cache is left untouched and the failure is returned.
     */
    suspend fun refresh(): EmptyResult<DataError>
}
