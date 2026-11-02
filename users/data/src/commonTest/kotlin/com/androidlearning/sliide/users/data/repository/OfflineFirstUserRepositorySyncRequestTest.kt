package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.mapper.toUser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * When the repository asks for a background sync: spec background-sync, "A queued write requests its own background
 * sync". A request is made exactly when a write leaves a pending entry, for that entry's user.
 */
class OfflineFirstUserRepositorySyncRequestTest {
    // region writes that queue → a sync is requested

    @Test
    fun offlineCreateRequestsASyncForItsTempId() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            h.repository.createUser(details())

            assertThat(h.syncScheduler.requestedUserIds).containsExactly(-1L)
        }

    @Test
    fun offlineUpdateRequestsASync() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.apiClient.defaultError = DataError.Remote.SERVER_ERROR

            h.repository.updateUser(user.copy(name = "New"))

            assertThat(h.syncScheduler.requestedUserIds).containsExactly(42L)
        }

    @Test
    fun offlineFinalisedDeleteRequestsASync() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            h.repository.finalizeDelete(user)

            assertThat(h.syncScheduler.requestedUserIds).containsExactly(42L)
        }

    @Test
    fun editOfAnOfflineCreatedUserRequestsAnotherSyncForIt() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details())
            h.apiClient.defaultError = null
            val temp = h.userDao.getById(-1)!!.toUser()

            h.repository.updateUser(temp.copy(name = "Edited"))

            assertThat(h.syncScheduler.requestedUserIds).containsExactly(-1L, -1L)
        }

    // endregion

    // region writes that leave no entry → nothing is requested

    @Test
    fun onlineWritesRequestNothing() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()

            h.repository.createUser(details())
            h.repository.updateUser(user.copy(name = "New"))
            h.repository.removeUserLocally(42)
            h.repository.finalizeDelete(user)

            assertThat(h.syncScheduler.requestedUserIds).isEmpty()
        }

    @Test
    fun rejectedWritesRequestNothing() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.apiClient.defaultError = emailTaken

            h.repository.createUser(details())
            h.repository.updateUser(user.copy(name = "New"))
            h.repository.removeUserLocally(42)
            h.repository.finalizeDelete(user)

            assertThat(h.syncScheduler.requestedUserIds).isEmpty()
        }

    @Test
    fun writesForAUserAlreadyGoneOnTheServerRequestNothing() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val updated = h.seedUser(userEntity(41)).toUser()
            val deleted = h.seedUser(userEntity(42)).toUser()
            h.apiClient.updateResults += Result.Failure(DataError.Remote.NOT_FOUND)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.NOT_FOUND)

            h.repository.updateUser(updated.copy(name = "New"))
            h.repository.removeUserLocally(42)
            h.repository.finalizeDelete(deleted)

            assertThat(h.syncScheduler.requestedUserIds).isEmpty()
        }

    @Test
    fun deleteThatCancelsAnOfflineCreateRequestsNothingMore() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details())
            val temp = (h.repository.removeUserLocally(-1) as Result.Success).data

            h.repository.finalizeDelete(temp)

            // Only the create's own request: the cancelled pair leaves nothing to sync.
            assertThat(h.syncScheduler.requestedUserIds).containsExactly(-1L)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun editOfATempUserWithNoPendingCreateRequestsNothing() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            // A temp user whose create was lost (process killed mid-request): edited locally only.
            val temp = h.seedUser(userEntity(-1)).toUser()

            h.repository.updateUser(temp.copy(name = "Edited"))

            assertThat(h.syncScheduler.requestedUserIds).isEmpty()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    // endregion
}
