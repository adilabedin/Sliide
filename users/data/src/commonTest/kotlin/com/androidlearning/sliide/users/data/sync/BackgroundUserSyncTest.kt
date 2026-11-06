package com.androidlearning.sliide.users.data.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.repository.RepositoryHarness
import com.androidlearning.sliide.users.data.repository.details
import com.androidlearning.sliide.users.data.repository.emailTaken
import com.androidlearning.sliide.users.data.repository.remoteUser
import com.androidlearning.sliide.users.data.repository.userEntity
import com.androidlearning.sliide.users.domain.UserRepository
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

            BackgroundUserSync(h.repository).run()
            assertThat(h.apiClient.requests).isEmpty()
        }

    @Test
    fun queuedUpdateIsSentOnceAndRemoved() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)

            BackgroundUserSync(h.repository).run()
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

            BackgroundUserSync(h.repository).run()
            assertThat(h.pendingSyncDao.entries).single().transform { it.user.id }.isEqualTo(1L)
        }

    @Test
    fun permanentRejectionIsDroppedAndRunContinues() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(userEntity(-1), PendingOperation.CREATE, queuedAt = 100)
            h.seedUser(userEntity(-1))
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.createResults += Result.Failure(emailTaken)

            BackgroundUserSync(h.repository).run()
            assertThat(h.pendingSyncDao.entries).isEmpty()
            // The cache is left to the next foreground refresh; a background run never replaces it.
            assertThat(h.userDao.users.map { it.id }).containsExactlyInAnyOrder(-1L, 2L)
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

            background.await()
            refresh.await()
            assertThat(h.apiClient.requests.filterIsInstance<RemoteRequest.Update>()).single()
        }

    @Test
    fun perUserRunSendsOnlyThatUsersEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(41)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(42)), PendingOperation.UPDATE, queuedAt = 200)

            BackgroundUserSync(h.repository).run(userId = 42)
            assertThat(
                h.apiClient.requests
                    .filterIsInstance<RemoteRequest.Update>()
                    .map { it.id },
            ).containsExactly(42L)
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(41L)
        }

    @Test
    fun perUserRunWithARetryableFailureStopsAndKeepsTheEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(42)), PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            BackgroundUserSync(h.repository).run(userId = 42)
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(42L)
        }

    @Test
    fun perUserRunSwallowsAnUnexpectedException() =
        runTest {
            // A plain Exception, not an IllegalStateException: any non-cancellation failure must be caught.
            // Returning at all is the assertion: a throw would fail the test.
            BackgroundUserSync(ThrowingSyncRepository(Exception("boom"))).run(userId = 42)
        }

    @Test
    fun runSwallowsAnUnexpectedException() =
        runTest {
            // Returning at all is the assertion: a throw would fail the test.
            BackgroundUserSync(ThrowingSyncRepository(IllegalStateException("boom"))).run()
        }

    @Test
    fun cancellationPropagatesAndKeepsTheEntryQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)
            h.apiClient.gate = CompletableDeferred()
            var finished = false

            val job =
                launch {
                    BackgroundUserSync(h.repository).run()
                    finished = true
                }
            advanceUntilIdle()
            job.cancel()
            job.join()

            assertThat(job.isCancelled).isTrue()
            assertThat(finished).isFalse()
            assertThat(h.pendingSyncDao.entries).single().transform { it.user.id }.isEqualTo(1L)
        }

    /** A repository whose replay throws; every other method is unused by [BackgroundUserSync]. */
    private class ThrowingSyncRepository(
        private val error: Throwable,
    ) : UserRepository {
        override suspend fun syncPending(): EmptyResult<DataError.Remote> = throw error

        override suspend fun syncPending(userId: Long): EmptyResult<DataError.Remote> = throw error

        override fun observeUsers(): Flow<List<User>> = error("unused")

        override suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError> = error("unused")

        override suspend fun updateUser(user: User): Result<WriteStatus, DataError> = error("unused")

        override suspend fun deleteUser(user: User): Result<WriteStatus, DataError> = error("unused")

        override suspend fun refresh(): EmptyResult<DataError> = error("unused")
    }
}
