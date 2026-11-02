package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.domain.model.SyncReport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Replaying one user's entry on its own: spec offline-sync, "Replay can target a single user". */
class OfflineFirstUserRepositorySingleUserSyncTest {
    @Test
    fun sendsOnlyThatUsersEntryAndLeavesTheOthersQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(41)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(42, name = "Edited")), PendingOperation.UPDATE, queuedAt = 200)

            val result = h.repository.syncPending(userId = 42)

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 0)))
            assertThat(h.apiClient.requests).containsExactly(
                RemoteRequest.Update(42, details(name = "Edited", email = "user42@example.com")),
            )
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(41L)
        }

    @Test
    fun rejectedCreateIsCountedDroppedAndLeavesTheCacheForRefresh() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val temp = h.seedUser(userEntity(-1, name = "Jane"))
            h.seedPending(temp, PendingOperation.CREATE)
            h.apiClient.createResults += Result.Failure(emailTaken)

            val result = h.repository.syncPending(userId = -1)

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.userDao.getById(-1)).isEqualTo(temp)
        }

    @Test
    fun retryableFailureKeepsTheEntryUnchanged() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val entry = h.seedPending(h.seedUser(userEntity(42)), PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            val result = h.repository.syncPending(userId = 42)

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.SERVER_ERROR))
            assertThat(h.pendingSyncDao.entries).containsExactly(entry)
        }

    @Test
    fun replayedDeleteOfUserAlreadyGoneCountsAsSynced() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(userEntity(42), PendingOperation.DELETE)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.NOT_FOUND)

            val result = h.repository.syncPending(userId = 42)

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 0)))
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun userInsideTheUndoWindowIsSkippedAndStaysQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val entry = h.seedPending(h.seedUser(userEntity(42)), PendingOperation.UPDATE)
            h.repository.removeUserLocally(42)

            val result = h.repository.syncPending(userId = 42)

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 0)))
            assertThat(h.apiClient.requests).isEmpty()
            assertThat(h.pendingSyncDao.entries).containsExactly(entry)
        }

    @Test
    fun userWithNoPendingEntrySendsNothing() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(41)), PendingOperation.UPDATE)

            val result = h.repository.syncPending(userId = 42)

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 0)))
            assertThat(h.apiClient.requests).isEmpty()
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(41L)
        }

    @Test
    fun entrySentBySingleUserReplayDuringAFullReplayIsNotSentAgain() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(41)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(42)), PendingOperation.UPDATE, queuedAt = 200)
            // Hold the full pass on its first request, so the single-user replay runs before it reaches user 42.
            h.apiClient.gate = CompletableDeferred()

            val fullPass = async { h.repository.syncPending() }
            advanceUntilIdle()
            val singleUser = async { h.repository.syncPending(userId = 42) }
            advanceUntilIdle()
            h.apiClient.gate?.complete(Unit)

            assertThat(fullPass.await()).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 0)))
            assertThat(singleUser.await()).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 0)))
            assertThat(
                h.apiClient.requests
                    .filterIsInstance<RemoteRequest.Update>()
                    .map { it.id },
            ).containsExactly(41L, 42L)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun slowFailingEntryForOneUserDoesNotStopAnotherUsersSync() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(41)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(42)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.REQUEST_TIMEOUT)
            // User 41's request hangs until released, then times out.
            h.apiClient.gate = CompletableDeferred()

            val slow = async { h.repository.syncPending(userId = 41) }
            advanceUntilIdle()
            val other = async { h.repository.syncPending(userId = 42) }
            advanceUntilIdle()
            h.apiClient.gate?.complete(Unit)

            assertThat(slow.await()).isEqualTo(Result.Failure(DataError.Remote.REQUEST_TIMEOUT))
            assertThat(other.await()).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 0)))
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(41L)
        }

    @Test
    fun syncReplacedWhileSendingStillCompletesAndSendsTheEntryOnce() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(42)), PendingOperation.UPDATE)
            h.apiClient.gate = CompletableDeferred()

            // What WorkManager's REPLACE does to a running per-user job: its caller is cancelled mid-request.
            val replaced = launch { h.repository.syncPending(userId = 42) }
            advanceUntilIdle()
            replaced.cancel()
            val replacement = async { h.repository.syncPending(userId = 42) }
            advanceUntilIdle()
            h.apiClient.gate?.complete(Unit)

            assertThat(replacement.await()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 0)))
            assertThat(
                h.apiClient.requests
                    .filterIsInstance<RemoteRequest.Update>()
                    .map { it.id },
            ).containsExactly(42L)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }
}
