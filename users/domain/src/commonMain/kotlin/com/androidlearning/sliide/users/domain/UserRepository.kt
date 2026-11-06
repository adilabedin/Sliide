package com.androidlearning.sliide.users.domain

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.flow.Flow

/**
 * Offline-first access to users. The local cache is the single source of truth: every write lands in it before
 * the network call. Failures that may later succeed (no connectivity, timeout, 5xx, 429, 401/403) are queued for a
 * later [syncPending]. A rejected write loses its pending entry and returns the typed [DataError]; only a rejected
 * create is also removed locally, and the next [refresh] corrects anything else.
 *
 * Lifetime: every suspending operation hands its work to an app-wide scope before its first suspension, and the caller
 * only awaits it. Once called, an operation runs to completion even if the caller is cancelled straight away (e.g. a
 * ViewModel cleared mid-request, or one calling from `onCleared` with an undispatched launch); cancelling only stops
 * the wait.
 */
interface UserRepository {
    /** Users from the local cache, newest first. Emits again whenever the cache changes. */
    fun observeUsers(): Flow<List<User>>

    /**
     * Inserts the user locally under a temporary negative id, then creates it on the server.
     * [WriteStatus.SYNCED]: re-keyed to the server id. [WriteStatus.QUEUED]: kept under the temp id and queued.
     * Failure (e.g. [DataError.Validation]): the local insert is rolled back.
     */
    suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError>

    /**
     * Writes the change locally, then updates it on the server. A user with a temporary id is never sent; the
     * change is merged into its pending create and returns [WriteStatus.QUEUED].
     * A failure that may later succeed queues the change ([WriteStatus.QUEUED], including
     * 401/403). Failure: the pending entry is dropped and the edit stays in the cache until the next [refresh],
     * except for [DataError.Remote.NOT_FOUND], where the user no longer exists on the server and is removed from the
     * cache. An uncached user fails with [DataError.Local.NOT_FOUND].
     */
    suspend fun updateUser(user: User): Result<WriteStatus, DataError>

    /**
     * Deletes the user: removes it from the cache, then sends DELETE for a server-known user (404 counts as success) or
     * queues it on a failure that may later succeed (including 401/403, as [WriteStatus.QUEUED]). A
     * temp-id user just has its row and pending create removed, with no network call; a temp id whose create already
     * synced in this process is deleted under its server id. [user] is a snapshot, so a user a refresh already removed
     * from the cache still gets its DELETE sent.
     * Failure (e.g. [DataError.Validation], GoRest refusing the delete): the pending entry is dropped and the user stays
     * removed until the next [refresh] brings it back.
     */
    suspend fun deleteUser(user: User): Result<WriteStatus, DataError>

    /**
     * Sends every pending entry once, oldest first, one at a time. An entry that fails in a way that may later succeed
     * stays queued for the next sync and the pass carries on; the first such error is returned.
     * A rejected entry is only deleted from the queue; the cached users are left as they are until the next [refresh]
     * replaces the list with the server's. Concurrent calls share one pass.
     */
    suspend fun syncPending(): EmptyResult<DataError.Remote>

    /**
     * Sends at most [userId]'s pending entry, with the same success and rejection rules as [syncPending],
     * and leaves every other entry untouched. With no entry for [userId] it sends nothing and succeeds. It may run
     * alongside a full pass; no entry is ever sent twice.
     */
    suspend fun syncPending(userId: Long): EmptyResult<DataError.Remote>

    /**
     * Runs [syncPending], again while entries queued in the meantime remain, then fetches the last page of `/users` and
     * replaces the cache when it differs. On a replay failure the cache is left untouched and the failure is returned.
     */
    suspend fun refresh(): EmptyResult<DataError>
}
