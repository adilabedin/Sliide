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
 * the network call, retryable network failures are queued for a later [syncPending], and permanent failures
 * roll the local write back and are returned to the caller.
 */
interface UserRepository {
    /** Users from the local cache, newest first. Emits again whenever the cache changes. */
    fun observeUsers(): Flow<List<User>>

    /**
     * Inserts the user locally under a temporary negative id, then creates it on the server.
     * [WriteStatus.SYNCED]: re-keyed to the server id. [WriteStatus.QUEUED]: kept under the temp id and queued.
     * Failure: a non-retryable error; the local insert is rolled back.
     */
    suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError>

    /**
     * Writes the change locally, then updates it on the server. A user with a temporary id is never sent; the
     * change is merged into its pending create and returns [WriteStatus.QUEUED].
     * Failure: a non-retryable error; the previous local data is restored, except for [DataError.Remote.NOT_FOUND],
     * where the user no longer exists on the server and is removed from the cache along with any pending entry.
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
     * queues it on a retryable failure; a temp-id user just cancels its pending create without any network call.
     * Failure: a non-retryable error; the user is restored locally.
     *
     * Ends the Undo window opened by [removeUserLocally]; call it instead of, never in addition to, [restoreUser].
     * Launch it in a scope that outlives the screen (an application-level scope, not `viewModelScope`): if it never
     * runs, replay keeps skipping this user and [refresh] stays blocked for the rest of the process.
     */
    suspend fun finalizeDelete(user: User): Result<WriteStatus, DataError>

    /**
     * Replays the pending queue oldest first, one entry at a time, stopping at the first retryable failure.
     * Permanently rejected entries are dropped from the queue with their local effect resolved (a rejected create
     * removes the temp user, an update whose user no longer exists removes it, a rejected delete restores the user)
     * and counted in [SyncReport.rejected]; nothing else is recorded. Concurrent calls share one pass.
     */
    suspend fun syncPending(): Result<SyncReport, DataError.Remote>

    /**
     * Runs [syncPending]; only if the queue is then empty, fetches the last page of `/users` and replaces the cache
     * when it differs. On a replay failure the cache is left untouched and the failure is returned.
     */
    suspend fun refresh(): EmptyResult<DataError>
}
