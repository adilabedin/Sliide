package com.androidlearning.sliide.users.data.sync

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.repository.RepositoryHarness
import com.androidlearning.sliide.users.data.repository.userEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * One iOS background refresh run (design D6, spec: background-sync). The runner launches into `backgroundScope` (the
 * stand-in application scope), which `advanceUntilIdle` does not drain, so these tests step with `runCurrent`.
 */
class IosBackgroundSyncRunnerTest {
    private class FakeTaskHandle(
        private val events: MutableList<String>,
    ) : SyncTaskHandle {
        var expirationHandler: (() -> Unit)? = null
        val completions = mutableListOf<Boolean>()

        override fun setExpirationHandler(handler: () -> Unit) {
            expirationHandler = handler
        }

        override fun complete(success: Boolean) {
            completions += success
            events += "complete($success)"
        }
    }

    private class Fixture(
        scope: CoroutineScope,
        online: Boolean = true,
    ) {
        val events = mutableListOf<String>()
        val harness = RepositoryHarness(scope)
        val task = FakeTaskHandle(events)
        val runner =
            IosBackgroundSyncRunner(
                backgroundUserSync = BackgroundUserSync(harness.repository),
                connectivityCheck = { online },
                scope = scope,
                submitNextRequest = { events += "submit" },
            )
    }

    @Test
    fun offlineCompletesWithoutSyncing() =
        runTest {
            val f = Fixture(backgroundScope, online = false)
            f.harness.seedPending(f.harness.seedUser(userEntity(1)), PendingOperation.UPDATE)

            f.runner.run(f.task)
            runCurrent()

            assertThat(f.harness.apiClient.requests).isEmpty()
            assertThat(f.harness.pendingSyncDao.entries).single()
            assertThat(f.events).containsExactly("submit", "complete(true)")
        }

    @Test
    fun onlineSyncsOnceThenCompletes() =
        runTest {
            val f = Fixture(backgroundScope)
            f.harness.seedPending(f.harness.seedUser(userEntity(1)), PendingOperation.UPDATE)

            f.runner.run(f.task)
            runCurrent()

            assertThat(
                f.harness.apiClient.requests
                    .filterIsInstance<RemoteRequest.Update>(),
            ).single()
            assertThat(f.harness.pendingSyncDao.entries).isEmpty()
            assertThat(f.task.completions).containsExactly(true)
        }

    @Test
    fun resubmitsBeforeSyncing() =
        runTest {
            val f = Fixture(backgroundScope)
            f.harness.seedPending(f.harness.seedUser(userEntity(1)), PendingOperation.UPDATE)
            f.harness.apiClient.gate = CompletableDeferred()

            f.runner.run(f.task)

            assertThat(f.events).containsExactly("submit")
            assertThat(f.harness.apiClient.requests).isEmpty()
        }

    @Test
    fun expiryMidSyncCancelsAndCompletesFalseOnce() =
        runTest {
            val f = Fixture(backgroundScope)
            f.harness.seedPending(f.harness.seedUser(userEntity(1)), PendingOperation.UPDATE, queuedAt = 100)
            f.harness.seedPending(f.harness.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            f.harness.apiClient.ungatedRequests = 1
            f.harness.apiClient.gate = CompletableDeferred()

            f.runner.run(f.task)
            runCurrent()
            f.task.expirationHandler!!.invoke()
            runCurrent()

            assertThat(f.task.completions).containsExactly(false)
            // The first entry was sent and cleared; the in-flight one stays queued for the next run.
            assertThat(
                f.harness.pendingSyncDao.entries
                    .map { it.user.id },
            ).containsExactly(2L)
        }

    @Test
    fun resultAfterExpiryDoesNotCompleteAgain() =
        runTest {
            val f = Fixture(backgroundScope)
            f.harness.seedPending(f.harness.seedUser(userEntity(1)), PendingOperation.UPDATE)
            val gate = CompletableDeferred<Unit>()
            f.harness.apiClient.gate = gate

            f.runner.run(f.task)
            runCurrent()
            f.task.expirationHandler!!.invoke()
            gate.complete(Unit)
            runCurrent()

            assertThat(f.task.completions.size).isEqualTo(1)
            assertThat(f.task.completions).containsExactly(false)
        }
}
