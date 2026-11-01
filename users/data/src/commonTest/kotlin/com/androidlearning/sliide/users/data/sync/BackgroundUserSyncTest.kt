package com.androidlearning.sliide.users.data.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.repository.RepositoryHarness
import com.androidlearning.sliide.users.data.repository.details
import com.androidlearning.sliide.users.data.repository.remoteUser
import com.androidlearning.sliide.users.data.repository.userEntity
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** One background run over the real repository (spec: background-sync). */
class BackgroundUserSyncTest {
    @Test
    fun emptyQueueSendsNoRequest() =
        runTest {
            val h = RepositoryHarness(backgroundScope)

            val outcome = BackgroundUserSync(h.repository).run()

            assertThat(outcome).isEqualTo(BackgroundSyncOutcome.Synced(SyncReport(synced = 0, rejected = 0)))
            assertThat(h.apiClient.requests).isEmpty()
        }

    @Test
    fun queuedUpdateIsSentOnceAndRemoved() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)

            val outcome = BackgroundUserSync(h.repository).run()

            assertThat(outcome).isEqualTo(BackgroundSyncOutcome.Synced(SyncReport(synced = 1, rejected = 0)))
            assertThat(h.apiClient.requests)
                .containsExactly(RemoteRequest.Update(1, details(name = "User 1", email = "user1@example.com")))
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun retryableFailureStopsAndKeepsEntryQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            val outcome = BackgroundUserSync(h.repository).run()

            assertThat(outcome).isEqualTo(BackgroundSyncOutcome.Stopped(DataError.Remote.SERVER_ERROR))
            assertThat(h.pendingSyncDao.entries).single().transform { it.user.id }.isEqualTo(1L)
        }

    @Test
    fun permanentRejectionIsDroppedAndRunContinues() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(userEntity(-1), PendingOperation.CREATE, queuedAt = 100)
            h.seedUser(userEntity(-1))
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.createResults += Result.Failure(DataError.Remote.VALIDATION)

            val outcome = BackgroundUserSync(h.repository).run()

            assertThat(outcome).isEqualTo(BackgroundSyncOutcome.Synced(SyncReport(synced = 1, rejected = 1)))
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.userDao.users.map { it.id }).containsExactly(2L)
        }

    @Test
    fun neverFetchesTheUsersPageOrReplacesTheCache() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(1))
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE)
            h.apiClient.defaultFetchLastPageResult = Result.Success(listOf(remoteUser(99)))

            BackgroundUserSync(h.repository).run()

            assertThat(h.apiClient.requests.none { it == RemoteRequest.FetchLastPage }).isTrue()
            assertThat(h.userDao.users.map { it.id }).containsExactlyInAnyOrder(1L, 2L)
        }

    @Test
    fun overlappingRefreshSendsEachEntryOnce() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate

            val background = async { BackgroundUserSync(h.repository).run() }
            val refresh = async { h.repository.refresh() }
            advanceUntilIdle()
            gate.complete(Unit)

            assertThat(background.await()).isEqualTo(BackgroundSyncOutcome.Synced(SyncReport(synced = 1, rejected = 0)))
            refresh.await()
            assertThat(h.apiClient.requests.filterIsInstance<RemoteRequest.Update>()).single()
        }

    @Test
    fun unexpectedExceptionBecomesStoppedUnknown() =
        runTest {
            val outcome = BackgroundUserSync(ThrowingSyncRepository(IllegalStateException("boom"))).run()

            assertThat(outcome).isEqualTo(BackgroundSyncOutcome.Stopped(DataError.Remote.UNKNOWN))
        }

    @Test
    fun cancellationPropagatesAndKeepsTheEntryQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)
            h.apiClient.gate = CompletableDeferred()
            var outcome: BackgroundSyncOutcome? = null

            val job = launch { outcome = BackgroundUserSync(h.repository).run() }
            advanceUntilIdle()
            job.cancel()
            job.join()

            assertThat(job.isCancelled).isTrue()
            assertThat(outcome).isNull()
            assertThat(h.pendingSyncDao.entries).single().transform { it.user.id }.isEqualTo(1L)
        }

    /** A repository whose replay throws; every other method is unused by [BackgroundUserSync]. */
    private class ThrowingSyncRepository(
        private val error: Throwable,
    ) : UserRepository {
        override suspend fun syncPending(): Result<SyncReport, DataError.Remote> = throw error

        override fun observeUsers(): Flow<List<User>> = error("unused")

        override suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError> = error("unused")

        override suspend fun updateUser(user: User): Result<WriteStatus, DataError> = error("unused")

        override suspend fun removeUserLocally(id: Long): Result<User, DataError.Local> = error("unused")

        override suspend fun restoreUser(user: User) = error("unused")

        override suspend fun finalizeDelete(user: User): Result<WriteStatus, DataError> = error("unused")

        override suspend fun refresh(): EmptyResult<DataError> = error("unused")
    }
}
