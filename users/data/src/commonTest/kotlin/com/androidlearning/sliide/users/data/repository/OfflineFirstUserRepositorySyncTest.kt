package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Replay of the outbox: spec offline-sync (order, outcomes, re-key, rejections). */
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
    fun connectivityDropKeepsThatEntryAndStillSendsTheRest() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.seedPending(h.seedUser(userEntity(3)), PendingOperation.UPDATE, queuedAt = 300)
            h.apiClient.updateResults += Result.Success(remoteUser(1))
            h.apiClient.updateResults += Result.Failure(DataError.Remote.NO_INTERNET)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(2L)
            assertThat(h.apiClient.requests.size).isEqualTo(3)
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
            h.repository.deleteUser(synced)

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
    fun replayedCreateRejectedDropsEntryLeavesCacheAndContinues() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val temp = h.seedUser(userEntity(-1, name = "Jane"))
            h.seedPending(temp, PendingOperation.CREATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.createResults += Result.Failure(emailTaken)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 1)))
            assertThat(h.userDao.getById(-1)).isEqualTo(temp)
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.apiClient.requests.size).isEqualTo(2)
        }

    @Test
    fun replayedDeleteRefusedDropsEntryAndLeavesCacheToRefresh() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(userEntity(42), PendingOperation.DELETE)
            h.apiClient.deleteResults += Result.Failure(DataError.Validation(emptyList()))

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun refreshAfterRejectedSyncReplacesTheCacheWithTheServersList() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(-1, name = "Jane")), PendingOperation.CREATE, queuedAt = 100)
            h.seedPending(userEntity(42), PendingOperation.DELETE, queuedAt = 200)
            h.apiClient.createResults += Result.Failure(emailTaken)
            h.apiClient.deleteResults += Result.Failure(DataError.Validation(emptyList()))
            h.apiClient.defaultFetchLastPageResult = Result.Success(listOf(remoteUser(42)))

            assertThat(h.repository.refresh()).isEqualTo(Result.Success(Unit))

            // The rejected temp user is gone and the user whose delete was refused is back.
            assertThat(h.userDao.users.map { it.id }).containsExactly(42L)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun replayedUpdateRejectedKeepsLocalDataAndDropsEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val edited = h.seedUser(userEntity(42, name = "Edited"))
            h.seedPending(edited, PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(emailTaken)

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(h.userDao.getById(42)).isEqualTo(edited)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun replayedUpdateForUserThatNoLongerExistsDropsEntryAndLeavesCacheToRefresh() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val edited = h.seedUser(userEntity(42, name = "Edited"))
            h.seedPending(edited, PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.NOT_FOUND)

            assertThat(h.repository.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(h.userDao.getById(42)).isEqualTo(edited)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun rateLimitedEntryIsKeptAndTheRestAreStillSent() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(1)), PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.TOO_MANY_REQUESTS)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.TOO_MANY_REQUESTS))
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(1L)
            assertThat(h.apiClient.requests.size).isEqualTo(2)
        }

    @Test
    fun unauthorisedEntryIsKeptWithItsLocalDataAndTheRestAreStillSent() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val edited = h.seedUser(userEntity(1, name = "Edited"))
            h.seedPending(edited, PendingOperation.UPDATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.UNAUTHORIZED)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.UNAUTHORIZED))
            assertThat(h.pendingSyncDao.entries.map { it.user.id }).containsExactly(1L)
            assertThat(h.userDao.getById(1)).isEqualTo(edited)
            assertThat(h.apiClient.requests.size).isEqualTo(2)
        }

    @Test
    fun replayedCreateWithUnexpectedStatusIsDroppedAndReplayContinues() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(-1, name = "Jane")), PendingOperation.CREATE, queuedAt = 100)
            h.seedPending(h.seedUser(userEntity(2)), PendingOperation.UPDATE, queuedAt = 200)
            h.apiClient.createResults += Result.Failure(DataError.Remote.UNKNOWN)

            val result = h.repository.syncPending()

            assertThat(result).isEqualTo(Result.Success(SyncReport(synced = 1, rejected = 1)))
            assertThat(h.userDao.getById(-1)?.name).isEqualTo("Jane")
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.apiClient.requests.size).isEqualTo(2)
        }

    @Test
    fun rejectedEntryIsGoneAfterRestart() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedPending(h.seedUser(userEntity(-1, name = "Jane")), PendingOperation.CREATE)
            h.apiClient.createResults += Result.Failure(emailTaken)
            h.repository.syncPending()

            val reopened = h.newRepository()

            assertThat(reopened.syncPending()).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 0)))
            assertThat(h.apiClient.requests.size).isEqualTo(1)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun rejectionOnlyDeletesTheEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val temp = h.seedUser(userEntity(-1))
            h.seedPending(temp, PendingOperation.CREATE)
            h.apiClient.createResults += Result.Failure(emailTaken)

            h.repository.syncPending()

            assertThat(h.userDao.users).containsExactly(temp)
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun deleteUnderTempIdAfterCreateSyncedDeletesTheServerUser() =
        runTest {
            // Spec offline-sync: "CREATE syncs during the Undo window".
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details())
            val hidden = h.userDao.getById(-1)!!.toUser()
            h.apiClient.defaultError = null
            h.apiClient.createResults += Result.Success(remoteUser(7012, name = "Jane Doe", email = "jane@example.com"))
            h.repository.syncPending()

            val result = h.repository.deleteUser(hidden)

            assertThat(result).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(h.apiClient.requests.last()).isEqualTo(RemoteRequest.Delete(7012))
            assertThat(h.userDao.users).isEmpty()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }
}
