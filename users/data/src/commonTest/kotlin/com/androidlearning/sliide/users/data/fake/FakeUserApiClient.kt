package com.androidlearning.sliide.users.data.fake

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserApiClient
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import kotlinx.coroutines.CompletableDeferred

/** One recorded call to [FakeUserApiClient]. */
sealed interface RemoteRequest {
    data object FetchLastPage : RemoteRequest

    data class Create(
        val details: UserDetails,
    ) : RemoteRequest

    data class Update(
        val id: Long,
        val details: UserDetails,
    ) : RemoteRequest

    data class Delete(
        val id: Long,
    ) : RemoteRequest
}

/**
 * Scripted [UserApiClient]. Each method pops the next result from its queue, falling back to its default when
 * the queue is empty. By default: fetch returns an empty page, create echoes the details under the next server id
 * (starting at [nextServerId]), update echoes the details, delete succeeds.
 *
 * Every call is recorded in [requests] *before* it waits on [gate], so a test can hold a request in flight and assert
 * that it was sent. Set [gate] to a [CompletableDeferred] and complete it to release the waiting request(s).
 */
class FakeUserApiClient : UserApiClient {
    private val recorded = mutableListOf<RemoteRequest>()
    val requests: List<RemoteRequest> get() = recorded.toList()

    val fetchLastPageResults = ArrayDeque<Result<List<RemoteUser>, DataError.Remote>>()
    val createResults = ArrayDeque<Result<RemoteUser, DataError>>()
    val updateResults = ArrayDeque<Result<RemoteUser, DataError>>()
    val deleteResults = ArrayDeque<EmptyResult<DataError>>()

    var defaultFetchLastPageResult: Result<List<RemoteUser>, DataError.Remote> = Result.Success(emptyList())

    /** When non-null, overrides the echo behaviour for every create/update/delete without a queued result. */
    var defaultError: DataError? = null

    var nextServerId = 1_000L

    /** When non-null, [create] throws it after recording the call (an unexpected crash inside an operation). */
    var createThrows: Throwable? = null

    var gate: CompletableDeferred<Unit>? = null

    /** The first [ungatedRequests] calls skip [gate], so a test can let some requests through and hold a later one. */
    var ungatedRequests = 0

    override suspend fun fetchLastPage(): Result<List<RemoteUser>, DataError.Remote> {
        record(RemoteRequest.FetchLastPage)
        return fetchLastPageResults.removeFirstOrNull() ?: defaultFetchLastPageResult
    }

    override suspend fun create(details: UserDetails): Result<RemoteUser, DataError> {
        record(RemoteRequest.Create(details))
        createThrows?.let { throw it }
        return createResults.removeFirstOrNull()
            ?: defaultError?.let { Result.Failure(it) }
            ?: Result.Success(RemoteUser(id = nextServerId++, details = details))
    }

    override suspend fun update(
        id: Long,
        details: UserDetails,
    ): Result<RemoteUser, DataError> {
        record(RemoteRequest.Update(id, details))
        return updateResults.removeFirstOrNull()
            ?: defaultError?.let { Result.Failure(it) }
            ?: Result.Success(RemoteUser(id = id, details = details))
    }

    override suspend fun delete(id: Long): EmptyResult<DataError> {
        record(RemoteRequest.Delete(id))
        return deleteResults.removeFirstOrNull()
            ?: defaultError?.let { Result.Failure(it) }
            ?: Result.Success(Unit)
    }

    private suspend fun record(request: RemoteRequest) {
        recorded += request
        if (recorded.size > ungatedRequests) gate?.await()
    }
}
