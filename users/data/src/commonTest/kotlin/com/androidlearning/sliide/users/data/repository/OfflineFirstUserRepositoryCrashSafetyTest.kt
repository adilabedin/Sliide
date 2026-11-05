package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Every write is queued only after its request fails, so a process killed mid-request (modelled by cancelling the
 * application scope, since operations outlive their caller) loses that write and the next refresh shows the server's
 * data (accepted). Temp ids are never reused within a process.
 */
class OfflineFirstUserRepositoryCrashSafetyTest {
    @Test
    fun processKilledDuringCreateLosesItAndRefreshRemovesTheTempUser() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.gate = CompletableDeferred() // never completed: the POST hangs

            val killedScope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val firstProcess = h.newRepository(applicationScope = killedScope)
            launch { firstProcess.createUser(details()) }
            runCurrent()
            killedScope.cancel()
            runCurrent()

            // Only a failed POST queues a create, so nothing is left to send after the restart.
            assertThat(h.userDao.getById(-1)?.name).isEqualTo("Jane Doe")
            assertThat(h.pendingSyncDao.entries).isEmpty()

            h.apiClient.gate = null
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(42)))
            h.newRepository().refresh()
            assertThat(h.userDao.users.map { it.id }).containsExactly(42L)
            assertThat(h.apiClient.requests.filterIsInstance<RemoteRequest.Create>()).hasSize(1)
        }

    @Test
    fun processKilledDuringUpdateLosesTheEditAndRefreshRestoresServerData() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.gate = CompletableDeferred() // never completed: the request hangs

            // Operations outlive their caller, so a kill is modelled by cancelling the application scope itself.
            val killedScope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val firstProcess = h.newRepository(applicationScope = killedScope)
            launch { firstProcess.updateUser(original.toUser().copy(name = "New")) }
            runCurrent()
            killedScope.cancel()
            runCurrent()

            // Only a failed request queues an edit, so the cut-off one is shown but never sent.
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            assertThat(h.pendingSyncDao.entries).isEmpty()

            h.apiClient.gate = null
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(42, name = "Old")))
            h.newRepository().refresh()
            assertThat(h.apiClient.requests.filterIsInstance<RemoteRequest.Update>()).hasSize(1)
            assertThat(h.userDao.getById(42)?.name).isEqualTo("Old")
        }

    @Test
    fun processKilledDuringDeleteLosesTheDeleteAndRefreshBringsTheUserBack() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            val killedScope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val firstProcess = h.newRepository(applicationScope = killedScope)
            h.apiClient.gate = CompletableDeferred()

            launch { firstProcess.deleteUser(user) }
            runCurrent()
            killedScope.cancel()
            runCurrent()

            // Only a failed request queues a delete, so the cut-off one leaves nothing to send.
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()

            h.apiClient.gate = null
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(42)))
            h.newRepository().refresh()
            assertThat(h.apiClient.requests.filterIsInstance<RemoteRequest.Delete>()).hasSize(1)
            assertThat(h.userDao.getById(42)?.id).isEqualTo(42L)
        }

    @Test
    fun updateRejectedAfterPriorQueuedUpdateDropsTheMergedEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "First"))
            h.apiClient.defaultError = null
            h.clock.advance(5.minutes)
            h.apiClient.updateResults += Result.Failure(emailTaken)

            val result = h.repository.updateUser(original.toUser().copy(name = "Second"))

            // The earlier queued edit was merged into this one, so it goes too; the next refresh restores server data.
            assertThat(result).isEqualTo(Result.Failure(emailTaken))
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.userDao.getById(42)?.name).isEqualTo("Second")
        }

    @Test
    fun deleteRejectedAfterPriorQueuedUpdateDropsTheMergedEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "Edited"))
            val edited = h.userDao.getById(42)!!
            h.apiClient.defaultError = null
            h.apiClient.deleteResults += Result.Failure(DataError.Validation(emptyList()))

            val result = h.repository.deleteUser(edited.toUser())

            assertThat(result).isEqualTo(Result.Failure(DataError.Validation(emptyList())))
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.userDao.getById(42)).isNull()
        }

    @Test
    fun deleteRetryableKeepsMergedDeleteAndReturnsQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            assertThat(h.repository.deleteUser(user)).isEqualTo(Result.Success(WriteStatus.QUEUED))
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.DELETE)
        }

    @Test
    fun tempIdsAreNotReusedAfterRekeyAndStaleReferenceCannotTouchNewUser() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details(name = "First", email = "first@example.com"))
            val stale = h.userDao.getById(-1)!!.toUser()
            h.apiClient.defaultError = null
            h.apiClient.createResults += Result.Success(remoteUser(7012))
            h.repository.syncPending()
            assertThat(h.userDao.getById(-1)).isNull()

            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details(name = "Second", email = "second@example.com"))
            val second = h.userDao.getById(-2)

            assertThat(second?.name).isEqualTo("Second")
            assertThat(
                h.repository.updateUser(stale.copy(name = "Hijack")),
            ).isEqualTo(Result.Failure(DataError.Local.NOT_FOUND))
            assertThat(h.userDao.getById(-2)).isEqualTo(second)
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .user,
            ).isEqualTo(second)
        }
}
