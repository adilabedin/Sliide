package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Spec operation-lifetime: every repository operation runs in the application scope, so cancelling its caller (a
 * ViewModel being cleared) cancels only the wait, never the operation. Each test holds the request in flight with the
 * fake's gate, cancels the caller, then releases the request and checks the operation still completed.
 */
class OfflineFirstUserRepositoryLifetimeTest {
    @Test
    fun cancelledCreateCallerStillReKeysToTheServerId() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate

            val caller = launch { h.repository.createUser(details()) }
            runCurrent()
            caller.cancel()
            gate.complete(Unit)
            runCurrent()

            assertThat(caller.isCancelled).isTrue()
            assertThat(h.userDao.getAll().map { it.id }).containsExactly(1_000L)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun cancelledUpdateCallerStillAppliesTheServerUpdate() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate

            val caller = launch { h.repository.updateUser(original.toUser().copy(name = "New")) }
            runCurrent()
            caller.cancel()
            gate.complete(Unit)
            runCurrent()

            assertThat(h.apiClient.requests).containsExactly(RemoteRequest.Update(42, details(name = "New", email = original.email)))
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun cancelledDeleteCallerStillDeletesAndClearsTheEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate

            val caller = launch { h.repository.deleteUser(user) }
            runCurrent()
            caller.cancel()
            gate.complete(Unit)
            runCurrent()

            assertThat(h.apiClient.requests).containsExactly(RemoteRequest.Delete(42))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun cancelledRefreshCallerStillReplacesTheCache() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(1))
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(2)))
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate

            val caller = launch { h.repository.refresh() }
            runCurrent()
            caller.cancel()
            gate.complete(Unit)
            runCurrent()

            assertThat(h.userDao.getAll().map { it.id }).containsExactly(2L)
        }

    @Test
    fun callerThatWaitsReceivesTheResult() =
        runTest {
            val h = RepositoryHarness(backgroundScope)

            assertThat(h.repository.createUser(details())).isEqualTo(Result.Success(WriteStatus.SYNCED))
        }

    @Test
    fun failingOperationReachesItsCallerWithoutCancellingOthers() =
        runTest {
            // Mirrors production: the application scope has a SupervisorJob.
            val appScope = supervisedChildScope()
            val h = RepositoryHarness(appScope)
            val target = h.seedUser(userEntity(42, name = "Old")).toUser()
            val gate = CompletableDeferred<Unit>()
            h.apiClient.gate = gate
            h.apiClient.createThrows = IllegalStateException("crash inside create")

            val update = async { h.repository.updateUser(target.copy(name = "New")) }
            runCurrent() // holds the write lock with its request in flight
            // runCatching so the caller-side failure is observed here instead of failing the test scope itself.
            val failing = async { runCatching { h.repository.createUser(details()) } }
            runCurrent()
            gate.complete(Unit)

            assertThat(update.await()).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(failing.await().exceptionOrNull()).isNotNull().isInstanceOf(IllegalStateException::class)
            assertThat(appScope.isActive).isTrue()

            h.apiClient.createThrows = null
            h.apiClient.gate = null
            assertThat(h.repository.createUser(details(email = "next@example.com"))).isEqualTo(Result.Success(WriteStatus.SYNCED))
        }

    private fun TestScope.supervisedChildScope(): CoroutineScope =
        CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
}
