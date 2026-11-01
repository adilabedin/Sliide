package com.androidlearning.sliide.users.data.repository

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.domain.model.SyncReport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Replay of the outbox: spec offline-sync (order, outcomes, re-key, rejections, Undo guard). */
class OfflineFirstUserRepositorySyncTest {
    @Test
    fun emptyQueueReplaysNothing() =
        runTest {
            val h = RepositoryHarness(backgroundScope)

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 0)))
            assertThat(h.apiClient.requests).isEmpty()
        }

    @Test
    fun replaySendsEntriesOldestFirst() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(3)), PendingOperation.UPDATE, queuedAt = 300)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(userEntity(2), PendingOperation.DELETE, queuedAt = 200)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 3, rejected = 0)))
            assertThat(h.apiClient.requests).containsExactly(
                RemoteRequest.Update(1, details(name = "User 1", email = "user1@example.com")),
                RemoteRequest.Delete(2),
                RemoteRequest.Update(3, details(name = "User 3", email = "user3@example.com")),
            )
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun connectivityDropMidReplayStopsAndKeepsRemainingInOrder() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.seedPending(h.seedUser(userEntity(3)), PendingOperation.UPDATE, queuedAt = 300)
            h.apiClient.updateResults += Result.Success(remoteUser(1))
            h.apiClient.updateResults += Result.Failure(DataError.Remote.NO_INTERNET)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(2L, 3L)
            assertThat(h.apiClient.requests.size).isEqualTo(2)
        }

    @Test
    fun replayedDeleteWith404IsRemoved() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(userEntity(42), PendingOperation.DELETE)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.NOT_FOUND)

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 0)))
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun replayedCreateRekeysToServerIdThenDeleteTargetsServerId() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details())
            val tempCreatedAt = h.userDao.getById(-1)!!.createdAtEpochMillis
            h.apiClient.defaultError = null
            h.apiClient.createResults += Result.Success(remoteUser(7012, name = "Jane Doe", email = "jane@example.com"))

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 0)))
            assertThat(h.userDao.getById(-1)).isNull()
            assertThat(h.userDao.getById(7012)?.createdAtEpochMillis).isEqualTo(tempCreatedAt)
            assertThat(h.pendingSyncDao.entries).isEmpty()

            val synced = h.userDao.getById(7012)!!.toUser()
            h.repository.removeUserLocally(7012)
            h.repository.finalizeDelete(synced)

            assertThat(h.apiClient.requests.last()).isEqualTo(RemoteRequest.Delete(7012))
            assertThat(h.userDao.users).isEmpty()
        }

    @Test
    fun replayedUpdateAppliesServerDataAndClearsEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(42, name = "Local", createdAt = 7)), PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Success(remoteUser(42, name = "Server"))

            h.repository.syncPending()

            assertThat(h.userDao.getById(42)?.name).isEqualTo("Server")
            assertThat(h.userDao.getById(42)?.createdAtEpochMillis).isEqualTo(7L)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun replayedCreateRejectedRemovesTempUserDropsEntryAndContinues() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(-1, name = "Jane")), PendingOperation.CREATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.createResults += Result.Failure(DataError.Remote.VALIDATION)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 1)))
            assertThat(h.userDao.getById(-1)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.apiClient.requests.size).isEqualTo(2)
        }

    @Test
    fun replayedDeleteRejectedRestoresSnapshotAndDropsEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val snapshot = userEntity(42)
            h.seedPending(snapshot, PendingOperation.DELETE)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.UNAUTHORIZED)

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(h.userDao.getById(42)).isEqualTo(snapshot)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun replayedUpdateRejectedKeepsLocalDataAndDropsEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val edited = h.seedUser(userEntity(42, name = "Edited"))
            h.seedPending(edited, PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.VALIDATION)

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(h.userDao.getById(42)).isEqualTo(edited)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun replayedUpdateForUserThatNoLongerExistsRemovesUserAndEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(42, name = "Edited")), PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.NOT_FOUND)

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun rateLimitedReplayKeepsEntryAndStops() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.TOO_MANY_REQUESTS)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.TOO_MANY_REQUESTS))
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(1L, 2L)
            assertThat(h.apiClient.requests.size).isEqualTo(1)
        }

    @Test
    fun rejectedEntryIsGoneAfterRestart() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(-1, name = "Jane")), PendingOperation.CREATE)
            h.apiClient.createResults += Result.Failure(DataError.Remote.VALIDATION)
            h.repository.syncPending()

            val reopened = h.newRepository()

            assertThat(reopened.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 0)))
            assertThat(h.apiClient.requests.size).isEqualTo(1)
            reopened.observeUsers().test { assertThat(awaitItem()).isEmpty() }
        }

    @Test
    fun rejectionIsAtomicWithEntryRemovalAndRollback() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(-1)), PendingOperation.CREATE)
            h.apiClient.createResults += Result.Failure(DataError.Remote.VALIDATION)
            val transactionsBefore = h.transactionRunner.transactionCount

            h.repository.syncPending()

            // One transaction: entry delete + temp-user rollback.
            assertThat(h.transactionRunner.transactionCount - transactionsBefore).isEqualTo(1)
            assertThat(h.userDao.users).isEmpty()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun entryInsideUndoWindowIsSkippedButKept() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(42, name = "Edited")), PendingOperation.UPDATE)
            h.repository.removeUserLocally(42)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 0)))
            assertThat(h.apiClient.requests).isEmpty()
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .user.id,
            ).isEqualTo(42L)
        }

    @Test
    fun offlineCreatedUserRemovedDuringReplayThenFinalisedSendsNothing() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details())
            h.apiClient.defaultError = null
            val requestsBefore = h.apiClient.requests.size

            val removed = (h.repository.removeUserLocally(-1) as Result.Success).data
            h.repository.syncPending()
            h.repository.finalizeDelete(removed)

            assertThat(h.apiClient.requests.size).isEqualTo(requestsBefore)
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.userDao.users).isEmpty()
        }

    @Test
    fun undoneRemovalIsReplayedAgain() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val edited = h.seedUser(userEntity(42, name = "Edited"))
            h.seedPending(edited, PendingOperation.UPDATE)
            val removed = (h.repository.removeUserLocally(42) as Result.Success).data
            h.repository.restoreUser(removed)

            h.repository.syncPending()

            assertThat(h.apiClient.requests).containsExactly(RemoteRequest.Update(42, details(name = "Edited", email = edited.email)))
        }
}
