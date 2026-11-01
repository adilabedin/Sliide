package com.androidlearning.sliide.users.data.remote

import com.androidlearning.sliide.core.data.networking.GoRestApi
import com.androidlearning.sliide.core.data.networking.delete
import com.androidlearning.sliide.core.data.networking.get
import com.androidlearning.sliide.core.data.networking.patch
import com.androidlearning.sliide.core.data.networking.post
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.mapper.toRemoteUserOrNull
import com.androidlearning.sliide.users.data.mapper.toRemoteUsersOrNull
import com.androidlearning.sliide.users.data.mapper.toUserRequestDto
import com.androidlearning.sliide.users.data.remote.dto.UserDto
import com.androidlearning.sliide.users.data.remote.dto.UserRequestDto
import com.androidlearning.sliide.users.domain.UserApiClient
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import io.ktor.client.HttpClient

/** [UserApiClient] over the GoRest `/users` endpoints (spec: user-remote-api). API strings live in [GoRestApi]. */
class KtorUserApiClient(
    private val httpClient: HttpClient,
) : UserApiClient {
    /**
     * Two dependent requests in one operation: page 1 to read the page count from `X-Pagination-Pages`, then the last
     * page itself. They run strictly in sequence (the second needs the first's header), and any failure returns
     * immediately, so both fail together with one error and no partial list. With a single page the first response is
     * reused. An empty last page (it vanished between the two requests) is a valid, empty result.
     */
    override suspend fun fetchLastPage(): Result<List<RemoteUser>, DataError.Remote> {
        val first =
            when (val result = httpClient.get<List<UserDto>>(GoRestApi.USERS, pageQuery(GoRestApi.FIRST_PAGE))) {
                is Result.Failure -> return result
                is Result.Success -> result.data
            }
        val pageCount =
            first.headers[GoRestApi.Headers.PAGE_COUNT]?.trim()?.toIntOrNull()
                ?: return Result.Failure(DataError.Remote.SERIALIZATION)
        if (pageCount <= GoRestApi.FIRST_PAGE) return first.body.toRemoteUsers()

        return when (val last = httpClient.get<List<UserDto>>(GoRestApi.USERS, pageQuery(pageCount))) {
            is Result.Failure -> last
            is Result.Success -> last.data.body.toRemoteUsers()
        }
    }

    override suspend fun create(details: UserDetails): Result<RemoteUser, DataError.Remote> =
        httpClient.post<UserRequestDto, UserDto>(GoRestApi.USERS, details.toUserRequestDto()).toRemoteUser()

    override suspend fun update(
        id: Long,
        details: UserDetails,
    ): Result<RemoteUser, DataError.Remote> =
        httpClient.patch<UserRequestDto, UserDto>(GoRestApi.user(id), details.toUserRequestDto()).toRemoteUser()

    override suspend fun delete(id: Long): EmptyResult<DataError.Remote> = httpClient.delete(GoRestApi.user(id))

    private fun pageQuery(page: Int): Map<String, Any> =
        mapOf(GoRestApi.Query.PAGE to page, GoRestApi.Query.PER_PAGE to GoRestApi.PAGE_SIZE)

    private fun List<UserDto>.toRemoteUsers(): Result<List<RemoteUser>, DataError.Remote> =
        toRemoteUsersOrNull()?.let { Result.Success(it) } ?: Result.Failure(DataError.Remote.SERIALIZATION)

    private fun Result<UserDto, DataError.Remote>.toRemoteUser(): Result<RemoteUser, DataError.Remote> =
        when (this) {
            is Result.Failure -> this
            is Result.Success ->
                data.toRemoteUserOrNull()?.let { Result.Success(it) } ?: Result.Failure(DataError.Remote.SERIALIZATION)
        }
}
