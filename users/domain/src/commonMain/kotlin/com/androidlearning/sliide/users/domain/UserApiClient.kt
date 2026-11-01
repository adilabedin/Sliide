package com.androidlearning.sliide.users.domain

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails

/** The GoRest API boundary. Implementations map transport failures onto [DataError.Remote]. */
interface UserApiClient {
    /** Users on the last page of `/users`. */
    suspend fun fetchLastPage(): Result<List<RemoteUser>, DataError.Remote>

    suspend fun create(details: UserDetails): Result<RemoteUser, DataError.Remote>

    suspend fun update(
        id: Long,
        details: UserDetails,
    ): Result<RemoteUser, DataError.Remote>

    suspend fun delete(id: Long): EmptyResult<DataError.Remote>
}
