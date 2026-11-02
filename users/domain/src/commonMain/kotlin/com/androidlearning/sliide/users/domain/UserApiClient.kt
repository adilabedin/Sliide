package com.androidlearning.sliide.users.domain

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails

/**
 * The GoRest API boundary. Reads fail with a [DataError.Remote]. Writes fail with a [DataError.Remote], or with a
 * [DataError.Validation] carrying GoRest's field errors when it answers 422.
 */
interface UserApiClient {
    /** Users on the last page of `/users`. */
    suspend fun fetchLastPage(): Result<List<RemoteUser>, DataError.Remote>

    /** `POST /users`. 422 → [DataError.Validation]. */
    suspend fun create(details: UserDetails): Result<RemoteUser, DataError>

    /** `PATCH /users/{id}`. 422 → [DataError.Validation]; 404 → [DataError.Remote.NOT_FOUND]. */
    suspend fun update(
        id: Long,
        details: UserDetails,
    ): Result<RemoteUser, DataError>

    /**
     * `DELETE /users/{id}`. 422 → [DataError.Validation], which for a delete means GoRest refused it;
     * 404 → [DataError.Remote.NOT_FOUND].
     */
    suspend fun delete(id: Long): EmptyResult<DataError>
}
