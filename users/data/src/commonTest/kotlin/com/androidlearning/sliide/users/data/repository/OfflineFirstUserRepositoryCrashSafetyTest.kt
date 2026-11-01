package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
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
 * Outbox-first writes: a server-id update or delete is queued before its request, so a process killed mid-request
 * (modelled by cancelling the application scope, since operations outlive their caller) never loses the local change, and a permanent failure restores the previous outbox state exactly.
 * Also: temp ids are never reused within a process.
 */
class OfflineFirstUserRepositoryCrashSafetyTest {
    @Test
    fun processKilledDuringUpdateLeavesPendingUpdateWithNewData() =
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

            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.UPDATE)
            assertThat(entry.user.name).isEqualTo("New")

            // The queued edit protects the cache: after restart, refresh replays it instead of reverting it.
            h.apiClient.gate = null
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(42, name = "New")))
            h.newRepository().refresh()
            assertThat(
                h.apiClient.requests
                    .drop(1)
                    .first(),
            ).isEqualTo(RemoteRequest.Update(42, details(name = "New", email = original.email)))
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
        }

    @Test
    fun processKilledDuringFinalizeDeleteLeavesPendingDeleteThatReplays() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            val killedScope = CoroutineScope(backgroundScope.coroutineContext + Job())
            val firstProcess = h.newRepository(applicationScope = killedScope)
            firstProcess.removeUserLocally(42)
            h.apiClient.gate = CompletableDeferred()

            launch { firstProcess.finalizeDelete(user) }
            runCurrent()
            killedScope.cancel()
            runCurrent()

            assertThat(h.userDao.getById(42)).isNull()
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.DELETE)

            // The Undo guard was lifted together with the queued DELETE, so the next process's replay sends it.
            h.apiClient.gate = null
            h.newRepository().syncPending()
            assertThat(h.apiClient.requests).containsExactly(RemoteRequest.Delete(42), RemoteRequest.Delete(42))
            assertThat(h.pendingSyncDao.entries.size).isEqualTo(0)
        }

    @Test
    fun updateRejectedAfterPriorQueuedUpdateRestoresThatEntryExactly() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "First"))
            val priorEntry = h.pendingSyncDao.entries.single()
            val cachedAfterFirst = h.userDao.getById(42)
            h.apiClient.defaultError = null
            h.clock.advance(5.minutes)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.VALIDATION)

            val result = h.repository.updateUser(original.toUser().copy(name = "Second"))

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.VALIDATION))
            assertThat(h.pendingSyncDao.entries).containsExactly(priorEntry)
            assertThat(h.userDao.getById(42)).isEqualTo(cachedAfterFirst)
        }

    @Test
    fun finalizeDeleteRejectedAfterPriorQueuedUpdateRestoresThatEntryExactly() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "Edited"))
            val priorEntry = h.pendingSyncDao.entries.single()
            val edited = h.userDao.getById(42)!!
            h.apiClient.defaultError = null
            h.repository.removeUserLocally(42)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.UNAUTHORIZED)

            val result = h.repository.finalizeDelete(edited.toUser())

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
            assertThat(h.pendingSyncDao.entries).containsExactly(priorEntry)
            assertThat(h.userDao.getById(42)).isEqualTo(edited)
        }

    @Test
    fun finalizeDeleteRetryableKeepsMergedDeleteAndReturnsQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            assertThat(h.repository.finalizeDelete(user)).isEqualTo(Result.Success(WriteStatus.QUEUED))
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
            assertThat(h.repository.updateUser(stale.copy(name = "Hijack"))).isEqualTo(Result.Failure(DataError.Local.NOT_FOUND))
            assertThat(h.userDao.getById(-2)).isEqualTo(second)
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .user,
            ).isEqualTo(second)
        }
}
