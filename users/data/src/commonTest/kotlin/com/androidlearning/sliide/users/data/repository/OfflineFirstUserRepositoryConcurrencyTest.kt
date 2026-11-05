package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Per-entry locking, single-flight replay and resumable replay (spec offline-sync). */
class OfflineFirstUserRepositoryConcurrencyTest {
    @Test
    fun overlappingReplaysShareOnePassAndSendEachEntryOnce() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate

            val first = async { h.repository.syncPending() }
            runCurrent()
            val second = async { h.repository.syncPending() }
            runCurrent()
            gate.complete(Unit)

            val expected = Result.Success(Unit)
            assertThat(first.await()).isEqualTo(expected)
            assertThat(second.await()).isEqualTo(expected)
            assertThat(h.apiClient.requests.map { (it as RemoteRequest.Update).id }).containsExactly(1L, 2L)
        }

    @Test
    fun userWriteDuringLongReplayRunsAfterCurrentRequestBeforeRemainingEntries() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            (1L..10L).forEach { id -> h.seedPending(h.seedUser(userEntity(id)), PendingOperation.UPDATE, queuedAt = id) }
            val firstRequest = CompletableDeferred<Unit>()
            h.apiClient.gate = firstRequest

            val replay = async { h.repository.syncPending() }
            runCurrent() // replay holds the write lock with request #1 in flight
            val create = async { h.repository.createUser(details()) }
            runCurrent() // create waits for the lock
            h.apiClient.gate = null
            firstRequest.complete(Unit)

            replay.await()
            create.await()
            val order = h.apiClient.requests
            assertThat(order[0]).isEqualTo(RemoteRequest.Update(1, details(name = "User 1", email = "user1@example.com")))
            assertThat(order[1]).isEqualTo(RemoteRequest.Create(details()))
            assertThat(order.drop(2).map { (it as RemoteRequest.Update).id }).containsExactly(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
        }

    @Test
    fun cancellingOneCallerDoesNotCancelTheSharedPass() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE)
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate

            val cancelled = launch { h.repository.syncPending() }
            runCurrent()
            val survivor = async { h.repository.syncPending() }
            runCurrent()
            cancelled.cancel()
            gate.complete(Unit)

            assertThat(survivor.await()).isEqualTo(Result.Success(Unit))
        }

    @Test
    fun interruptedReplayResumesWithoutResending() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            (1L..5L).forEach { id -> h.seedPending(h.seedUser(userEntity(id)), PendingOperation.UPDATE, queuedAt = id) }

            // First process: entries 1 and 2 succeed, then the OS kills the task while request 3 is in flight.
            val killedScope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val firstProcess = h.newRepository(applicationScope = killedScope)
            h.apiClient.ungatedRequests = 2
            h.apiClient.gate = CompletableDeferred()
            val pass = killedScope.async { firstProcess.syncPending() }
            runCurrent()
            assertThat(h.apiClient.requests.size).isEqualTo(3)
            killedScope.cancel()
            runCurrent()

            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(3L, 4L, 5L)

            // Next process resumes from entry 3.
            h.apiClient.gate = null
            h.apiClient.ungatedRequests = 0
            val sentBefore = h.apiClient.requests.size
            h.newRepository().syncPending()
            val resent =
                h.apiClient.requests
                    .drop(sentBefore)
                    .map { (it as RemoteRequest.Update).id }
            assertThat(resent).containsExactly(3L, 4L, 5L)
            assertThat(pass.isCancelled).isEqualTo(true)
        }
}
