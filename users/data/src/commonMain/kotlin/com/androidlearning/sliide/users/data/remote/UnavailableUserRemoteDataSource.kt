package com.androidlearning.sliide.users.data.remote

import com.androidlearning.sliide.users.domain.UserRemoteDataSource
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.util.DataError
import com.androidlearning.sliide.users.domain.util.EmptyResult
import com.androidlearning.sliide.users.domain.util.Result

/**
 * Stand-in network boundary until the Ktor GoRest client lands (design D9). Every call reports no internet, so the
 * app runs fully offline: writes stay in the cache and are queued for a later sync. Replace the Koin binding when the
 * real data source exists.
 */
class UnavailableUserRemoteDataSource : UserRemoteDataSource {
    override suspend fun fetchLastPage(): Result<List<RemoteUser>, DataError.Remote> = noInternet()

    override suspend fun create(details: UserDetails): Result<RemoteUser, DataError.Remote> = noInternet()

    override suspend fun update(
        id: Long,
        details: UserDetails,
    ): Result<RemoteUser, DataError.Remote> = noInternet()

    override suspend fun delete(id: Long): EmptyResult<DataError.Remote> = noInternet()

    private fun noInternet() = Result.Failure(DataError.Remote.NO_INTERNET)
}
