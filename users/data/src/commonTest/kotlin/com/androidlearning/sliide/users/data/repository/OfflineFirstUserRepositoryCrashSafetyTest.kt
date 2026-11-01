package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.domain.util.DataError
import com.androidlearning.sliide.users.domain.util.Result
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Outbox-first writes: a server-id update or delete is queued before its request, so a caller cancelled (or a process
 * killed) mid-request never loses the local change, and a permanent failure restores the previous outbox state exactly.
 * Also: temp ids are never reused within a process.
 */
class OfflineFirstUserRepositoryCrashSafetyTest {
    @Test
    fun cancelledUpdateLeavesPendingUpdateWithNewData() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.remote.gate = CompletableDeferred() // never completed: the request hangs

            val caller = launch { h.repository.updateUser(original.toUser().copy(name = "New")) }
            runCurrent()
            caller.cancel()
            runCurrent()

            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.UPDATE)
            assertThat(entry.user.name).isEqualTo("New")

            // The queued edit protects the cache: refresh replays it instead of reverting it.
            h.remote.gate = null
            h.remote.fetchLastPageResults += Result.Success(listOf(remoteUser(42, name = "New")))
            h.repository.refresh()
            assertThat(
                h.remote.requests
                    .drop(1)
                    .first(),
            ).isEqualTo(RemoteRequest.Update(42, details(name = "New", email = original.email)))
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
        }

    @Test
    fun cancelledFinalizeDeleteLeavesPendingDeleteThatReplays() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)
            h.remote.gate = CompletableDeferred()

            val caller = launch { h.repository.finalizeDelete(user) }
            runCurrent()
            caller.cancel()
            runCurrent()

            assertThat(h.userDao.getById(42)).isNull()
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.DELETE)

            // The Undo guard was lifted together with the queued DELETE, so the next replay sends it.
            h.remote.gate = null
            h.repository.syncPending()
            assertThat(h.remote.requests).containsExactly(RemoteRequest.Delete(42), RemoteRequest.Delete(42))
            assertThat(h.pendingSyncDao.entries.size).isEqualTo(0)
        }

    @Test
    fun updateRejectedAfterPriorQueuedUpdateRestoresThatEntryExactly() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.remote.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "First"))
            val priorEntry = h.pendingSyncDao.entries.single()
            val cachedAfterFirst = h.userDao.getById(42)
            h.remote.defaultError = null
            h.clock.advance(5.minutes)
            h.remote.updateResults += Result.Failure(DataError.Remote.VALIDATION)

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
            h.remote.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "Edited"))
            val priorEntry = h.pendingSyncDao.entries.single()
            val edited = h.userDao.getById(42)!!
            h.remote.defaultError = null
            h.repository.removeUserLocally(42)
            h.remote.deleteResults += Result.Failure(DataError.Remote.UNAUTHORIZED)

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
            h.remote.deleteResults += Result.Failure(DataError.Remote.SERVER_ERROR)

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
            h.remote.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details(name = "First", email = "first@example.com"))
            val stale = h.userDao.getById(-1)!!.toUser()
            h.remote.defaultError = null
            h.remote.createResults += Result.Success(remoteUser(7012))
            h.repository.syncPending()
            assertThat(h.userDao.getById(-1)).isNull()

            h.remote.defaultError = DataError.Remote.NO_INTERNET
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
