package com.androidlearning.sliide.users.data.repository

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Refresh = sync first, then replace from the last page only when nothing is pending (spec user-cache). */
class OfflineFirstUserRepositoryRefreshTest {
    @Test
    fun refreshWithEmptyQueueReplacesCacheWithDifferentLastPage() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(1))
            h.seedUser(userEntity(2))
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(2), remoteUser(3)))

            val result = h.repository.refresh()

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(h.userDao.users.map { it.id }).containsExactlyInAnyOrder(2L, 3L)
        }

    @Test
    fun refreshKeepsCreatedAtOfExistingUsersAndStampsNewOnesWithNow() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(2, createdAt = 55))
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(2), remoteUser(3)))

            h.repository.refresh()

            assertThat(h.userDao.getById(2)?.createdAtEpochMillis).isEqualTo(55L)
            assertThat(h.userDao.getById(3)?.createdAtEpochMillis).isEqualTo(h.clock.now.toEpochMilliseconds())
        }

    @Test
    fun refreshWhileOfflineWithPendingWritesDoesNotFetchAndLeavesCacheUnchanged() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(1, name = "Edited"))
            h.seedPending(user, PendingOperation.UPDATE)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            val result = h.repository.refresh()

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(h.apiClient.requests.filterIsInstance<RemoteRequest.FetchLastPage>()).isEmpty()
            assertThat(h.userDao.users).containsExactly(user)
        }

    @Test
    fun refreshWithEmptyQueueButOfflineReturnsFetchError() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(1))
            h.apiClient.fetchLastPageResults += Result.Failure(DataError.Remote.NO_INTERNET)

            assertThat(h.repository.refresh()).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(h.userDao.users).containsExactly(user)
        }

    @Test
    fun unchangedLastPageDoesNotRewriteCacheOrEmit() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(1, createdAt = 10))
            h.seedUser(userEntity(2, createdAt = 20))
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(1), remoteUser(2)))

            h.repository.observeUsers().test {
                val before = awaitItem()
                h.repository.refresh()
                expectNoEvents()
                assertThat(before.map { it.id }).containsExactly(2L, 1L)
            }
            assertThat(h.userDao.getById(1)?.createdAtEpochMillis).isEqualTo(10L)
        }

    @Test
    fun syncedLocallyCreatedUserIsDroppedWhenNotOnLastPage() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(1))
            h.apiClient.createResults += Result.Success(remoteUser(7012))
            h.repository.createUser(details())
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(1)))

            h.repository.refresh()

            assertThat(h.userDao.getById(7012)).isNull()
            assertThat(h.userDao.users.map { it.id }).containsExactly(1L)
        }

    @Test
    fun refreshReplaysQueueBeforeFetching() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(userEntity(9), PendingOperation.DELETE)
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(1)))

            h.repository.refresh()

            assertThat(h.apiClient.requests).containsExactly(RemoteRequest.Delete(9), RemoteRequest.FetchLastPage)
            assertThat(h.userDao.users.map { it.id }).containsExactly(1L)
        }

    @Test
    fun refreshJoiningAPassThatMissedANewerWriteSyncsItThenFetches() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(1), remoteUser(2)))

            // A background pass reads [1] and holds user 1's request in flight.
            // runCurrent, not advanceUntilIdle: the pass runs in backgroundScope, which advanceUntilIdle doesn't wait for.
            val pass = async { h.repository.syncPending() }
            runCurrent()
            assertThat(h.apiClient.requests).hasSize(1)
            // User 2 is queued after that pass read its list.
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE)
            // The pull joins the running pass.
            val refresh = async { h.repository.refresh() }
            runCurrent()
            gate.complete(Unit)

            pass.await()
            assertThat(refresh.await()).isEqualTo(Result.Success(Unit))
            assertThat(h.apiClient.requests.map { it::class }).containsExactly(
                RemoteRequest.Update::class,
                RemoteRequest.Update::class,
                RemoteRequest.FetchLastPage::class,
            )
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }
}
