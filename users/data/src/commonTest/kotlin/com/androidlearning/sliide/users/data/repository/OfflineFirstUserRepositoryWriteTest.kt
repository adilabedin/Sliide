package com.androidlearning.sliide.users.data.repository

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.single
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.fake.RemoteRequest
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** User-initiated writes: specs user-cache (create/update/delete) and offline-sync (queueing + merge rules). */
class OfflineFirstUserRepositoryWriteTest {
    // region user-cache: Cache is the single source of truth

    @Test
    fun cacheChangesAreObservedNewestFirst() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.repository.observeUsers().test {
                assertThat(awaitItem()).isEmpty()
                h.seedUser(userEntity(1, createdAt = 10))
                assertThat(awaitItem().map { it.id }).containsExactly(1L)
                h.seedUser(userEntity(2, createdAt = 20))
                assertThat(awaitItem().map { it.id }).containsExactly(2L, 1L)
                h.userDao.deleteById(2)
                assertThat(awaitItem().map { it.id }).containsExactly(1L)
            }
        }

    // endregion

    // region createUser

    @Test
    fun createOnlineStoresUserUnderServerIdAtTop() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(1, createdAt = 0))
            h.apiClient.createResults += Result.Success(remoteUser(7012, name = "Jane Doe", email = "jane@example.com"))

            val result = h.repository.createUser(details())

            assertThat(result).isEqualTo(Result.Success(WriteStatus.SYNCED))
            h.repository.observeUsers().test {
                val users = awaitItem()
                assertThat(users.map { it.id }).containsExactly(7012L, 1L)
                assertThat(users.first().name).isEqualTo("Jane Doe")
            }
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun createOfflineKeepsUserUnderTempIdAndQueuesCreate() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(1, createdAt = 0))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            val result = h.repository.createUser(details())

            assertThat(result).isEqualTo(Result.Success(WriteStatus.QUEUED))
            h.repository.observeUsers().test {
                assertThat(awaitItem().map { it.id }).containsExactly(-1L, 1L)
            }
            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.user.id).isEqualTo(-1L)
            assertThat(entry.operation).isEqualTo(PendingOperation.CREATE)
            assertThat(entry.user.name).isEqualTo("Jane Doe")
        }

    @Test
    fun twoOfflineCreatesGetDistinctNegativeIds() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            h.repository.createUser(details(name = "A", email = "a@example.com"))
            h.repository.createUser(details(name = "B", email = "b@example.com"))

            assertThat(
                h.userDao.users
                    .map { it.id }
                    .sorted(),
            ).containsExactly(-2L, -1L)
            assertThat(
                h.pendingSyncDao.entries
                    .map { it.user.id }
                    .sorted(),
            ).containsExactly(-2L, -1L)
        }

    @Test
    fun createOnServerErrorIsQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.createResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            assertThat(h.repository.createUser(details())).isEqualTo(Result.Success(WriteStatus.QUEUED))
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.CREATE)
        }

    @Test
    fun createOnTimeoutIsQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.createResults += Result.Failure(DataError.Remote.REQUEST_TIMEOUT)

            assertThat(h.repository.createUser(details())).isEqualTo(Result.Success(WriteStatus.QUEUED))
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.CREATE)
        }

    @Test
    fun createRejectedWithValidationErrorRollsBackAndIsNotQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.createResults += Result.Failure(emailTaken)

            val result = h.repository.createUser(details())

            assertThat(result).isEqualTo(Result.Failure(emailTaken))
            assertThat(h.userDao.users).isEmpty()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun createUnauthorisedIsQueuedAsAuthFailed() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.createResults += Result.Failure(DataError.Remote.UNAUTHORIZED)

            assertThat(h.repository.createUser(details())).isEqualTo(Result.Success(WriteStatus.QUEUED_AUTH_FAILED))
            assertThat(h.userDao.getById(-1)?.name).isEqualTo("Jane Doe")
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.CREATE)
        }

    @Test
    fun createAnswered404RollsBackAndIsNotQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.createResults += Result.Failure(DataError.Remote.NOT_FOUND)

            assertThat(h.repository.createUser(details())).isEqualTo(Result.Failure(DataError.Remote.NOT_FOUND))
            assertThat(h.userDao.users).isEmpty()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    // endregion

    // region updateUser

    @Test
    fun updateOnlineSyncsAndKeepsCreatedAt() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old", createdAt = 5))

            val result = h.repository.updateUser(original.toUser().copy(name = "New"))

            assertThat(result).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            assertThat(h.userDao.getById(42)?.createdAtEpochMillis).isEqualTo(5L)
            assertThat(h.apiClient.requests.single()).isEqualTo(RemoteRequest.Update(42, details(name = "New", email = original.email)))
        }

    @Test
    fun updateOfflineShowsNewNameAndQueuesUpdate() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            val result = h.repository.updateUser(original.toUser().copy(name = "New"))

            assertThat(result).isEqualTo(Result.Success(WriteStatus.QUEUED))
            h.repository.observeUsers().test {
                assertThat(awaitItem().single().name).isEqualTo("New")
            }
            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.UPDATE)
            assertThat(entry.user.name).isEqualTo("New")
        }

    @Test
    fun updateRejectedDropsEntryAndKeepsEditUntilRefresh() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.updateResults += Result.Failure(emailTaken)

            val result = h.repository.updateUser(original.toUser().copy(name = "New"))

            assertThat(result).isEqualTo(Result.Failure(emailTaken))
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            assertThat(h.pendingSyncDao.entries).isEmpty()

            h.apiClient.defaultFetchLastPageResult = Result.Success(listOf(remoteUser(42, name = "Old")))
            h.repository.refresh()
            assertThat(h.userDao.getById(42)?.name).isEqualTo("Old")
        }

    @Test
    fun updateOfUserThatNoLongerExistsOnServerRemovesItLocally() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.seedPending(original, PendingOperation.UPDATE)
            h.apiClient.updateResults += Result.Failure(DataError.Remote.NOT_FOUND)

            val result = h.repository.updateUser(original.toUser().copy(name = "New"))

            assertThat(result).isEqualTo(Result.Failure(DataError.Remote.NOT_FOUND))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun updateOfUnknownUserFailsWithNotFound() =
        runTest {
            val h = RepositoryHarness(backgroundScope)

            val result = h.repository.updateUser(userEntity(42).toUser())

            assertThat(result).isEqualTo(Result.Failure(DataError.Local.NOT_FOUND))
            assertThat(h.apiClient.requests).isEmpty()
        }

    @Test
    fun updateUnauthorisedKeepsEditAndIsQueuedAsAuthFailed() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.updateResults += Result.Failure(DataError.Remote.UNAUTHORIZED)

            val result = h.repository.updateUser(original.toUser().copy(name = "New"))

            assertThat(result).isEqualTo(Result.Success(WriteStatus.QUEUED_AUTH_FAILED))
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.UPDATE)
        }

    @Test
    fun updateOnServerUnavailableKeepsEditAndIsQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.updateResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            val result = h.repository.updateUser(original.toUser().copy(name = "New"))

            assertThat(result).isEqualTo(Result.Success(WriteStatus.QUEUED))
            assertThat(h.userDao.getById(42)?.name).isEqualTo("New")
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.UPDATE)
        }

    @Test
    fun updateThenUpdateOfflineKeepsOneUpdateWithLatestDataAndOriginalQueueTime() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            h.repository.updateUser(original.toUser().copy(name = "First"))
            val firstQueuedAt =
                h.pendingSyncDao.entries
                    .single()
                    .queuedAtEpochMillis
            h.clock.now = h.clock.now.plus(kotlin.time.Duration.parse("1m"))
            h.repository.updateUser(original.toUser().copy(name = "Second"))

            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.UPDATE)
            assertThat(entry.user.name).isEqualTo("Second")
            assertThat(entry.queuedAtEpochMillis).isEqualTo(firstQueuedAt)
        }

    @Test
    fun createThenUpdateOfflineKeepsOneCreateWithUpdatedData() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details(name = "Jane"))
            val temp = h.userDao.getById(-1)!!.toUser()

            val result = h.repository.updateUser(temp.copy(name = "Janet"))

            assertThat(result).isEqualTo(Result.Success(WriteStatus.QUEUED))
            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.CREATE)
            assertThat(entry.user.name).isEqualTo("Janet")
        }

    @Test
    fun editOfOfflineCreatedUserWhileOnlineSendsNoPut() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details(name = "Jane"))
            h.apiClient.defaultError = null
            val requestsBefore = h.apiClient.requests.size

            h.repository.updateUser(
                h.userDao
                    .getById(-1)!!
                    .toUser()
                    .copy(name = "Janet"),
            )

            assertThat(h.apiClient.requests.size).isEqualTo(requestsBefore)
            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.CREATE)
            assertThat(entry.user.name).isEqualTo("Janet")
        }

    @Test
    fun onlineEditAfterOfflineEditSupersedesPendingEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "Offline"))
            h.apiClient.defaultError = null

            val result = h.repository.updateUser(original.toUser().copy(name = "Online"))

            assertThat(result).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(h.pendingSyncDao.entries).isEmpty()
            val requestsBefore = h.apiClient.requests.size
            h.repository.syncPending()
            assertThat(h.apiClient.requests.size).isEqualTo(requestsBefore)
        }

    // endregion

    // region delete (local remove, restore, finalize)

    @Test
    fun undoRestoresIdenticalUserWithoutAnyRequest() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42))

            val removed = h.repository.removeUserLocally(42)
            assertThat(h.userDao.getById(42)).isNull()
            h.repository.restoreUser((removed as Result.Success).data)

            assertThat(h.userDao.getById(42)).isEqualTo(original)
            assertThat(h.apiClient.requests).isEmpty()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun removeOfUnknownUserFailsWithNotFound() =
        runTest {
            val h = RepositoryHarness(backgroundScope)

            assertThat(h.repository.removeUserLocally(42)).isEqualTo(Result.Failure(DataError.Local.NOT_FOUND))
        }

    @Test
    fun finalizeDeleteOnlineSendsDelete() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)

            val result = h.repository.finalizeDelete(user)

            assertThat(result).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(h.apiClient.requests).containsExactly(RemoteRequest.Delete(42))
            assertThat(h.userDao.getById(42)).isNull()
        }

    @Test
    fun finalizeDeleteWith404CountsAsSuccess() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.NOT_FOUND)

            assertThat(h.repository.finalizeDelete(user)).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.userDao.getById(42)).isNull()
        }

    @Test
    fun finalizeDeleteOfflineQueuesDelete() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET

            val result = h.repository.finalizeDelete(user)

            assertThat(result).isEqualTo(Result.Success(WriteStatus.QUEUED))
            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.DELETE)
            assertThat(entry.user).isEqualTo(userEntity(42))
        }

    @Test
    fun finalizeDeleteRefusedDropsEntryAndRefreshBringsTheUserBack() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42))
            h.repository.removeUserLocally(42)
            h.apiClient.deleteResults += Result.Failure(DataError.Validation(emptyList()))

            val result = h.repository.finalizeDelete(original.toUser())

            assertThat(result).isEqualTo(Result.Failure(DataError.Validation(emptyList())))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()

            h.apiClient.defaultFetchLastPageResult = Result.Success(listOf(remoteUser(42)))
            h.repository.refresh()
            assertThat(h.userDao.getById(42)?.id).isEqualTo(42L)
        }

    @Test
    fun finalizeDeleteUnauthorisedKeepsUserRemovedAndIsQueuedAsAuthFailed() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.UNAUTHORIZED)

            assertThat(h.repository.finalizeDelete(user)).isEqualTo(Result.Success(WriteStatus.QUEUED_AUTH_FAILED))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.DELETE)
        }

    @Test
    fun finalizeDeleteOnServerUnavailableIsQueued() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val user = h.seedUser(userEntity(42)).toUser()
            h.repository.removeUserLocally(42)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.SERVER_ERROR)

            assertThat(h.repository.finalizeDelete(user)).isEqualTo(Result.Success(WriteStatus.QUEUED))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(
                h.pendingSyncDao.entries
                    .single()
                    .operation,
            ).isEqualTo(PendingOperation.DELETE)
        }

    @Test
    fun finalizeDeleteWithUnexpectedStatusDropsEntryOnly() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42))
            h.repository.removeUserLocally(42)
            h.apiClient.deleteResults += Result.Failure(DataError.Remote.UNKNOWN)

            assertThat(h.repository.finalizeDelete(original.toUser()))
                .isEqualTo(Result.Failure(DataError.Remote.UNKNOWN))
            assertThat(h.userDao.getById(42)).isNull()
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun updateThenDeleteOfflineLeavesOnlyDelete() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42, name = "Old"))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "Edited"))
            val edited = h.userDao.getById(42)!!.toUser()

            h.repository.removeUserLocally(42)
            h.repository.finalizeDelete(edited)

            val entry = h.pendingSyncDao.entries.single()
            assertThat(entry.operation).isEqualTo(PendingOperation.DELETE)
        }

    @Test
    fun onlineDeleteAfterOfflineEditSupersedesPendingEntry() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            val original = h.seedUser(userEntity(42))
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.updateUser(original.toUser().copy(name = "Edited"))
            h.apiClient.defaultError = null

            h.repository.removeUserLocally(42)
            val result = h.repository.finalizeDelete(h.userDao.getById(42)?.toUser() ?: original.toUser())

            assertThat(result).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(h.pendingSyncDao.entries).isEmpty()
        }

    @Test
    fun createThenDeleteOfOfflineUserCancelsBothWithoutAnyRequest() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details())
            h.apiClient.defaultError = null
            val requestsBefore = h.apiClient.requests.size

            val removed = (h.repository.removeUserLocally(-1) as Result.Success).data
            val result = h.repository.finalizeDelete(removed)

            assertThat(result).isEqualTo(Result.Success(WriteStatus.SYNCED))
            assertThat(h.pendingSyncDao.entries).isEmpty()
            assertThat(h.userDao.users).isEmpty()
            h.repository.syncPending()
            assertThat(h.apiClient.requests.size).isEqualTo(requestsBefore)
        }

    @Test
    fun deleteNeverFinalisedIsNotSentAndRefreshMayShowUserAgain() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.seedUser(userEntity(42))
            h.repository.removeUserLocally(42)

            // App terminates: a fresh instance over the same tables has no Undo-window state.
            val restarted = h.newRepository()
            h.apiClient.fetchLastPageResults += Result.Success(listOf(remoteUser(42)))
            val result = restarted.refresh()

            assertThat(result).isEqualTo(Result.Success(Unit))
            assertThat(h.apiClient.requests.filterIsInstance<RemoteRequest.Delete>()).isEmpty()
            assertThat(h.userDao.getById(42)).isNotNull()
        }

    // endregion

    // region restart

    @Test
    fun queuedEntrySurvivesRestart() =
        runTest {
            val h = RepositoryHarness(backgroundScope)
            h.apiClient.defaultError = DataError.Remote.NO_INTERNET
            h.repository.createUser(details())
            val before = h.pendingSyncDao.entries

            val restarted = h.newRepository()
            h.apiClient.defaultError = null
            val report = restarted.syncPending()

            assertThat(before.single().operation).isEqualTo(PendingOperation.CREATE)
            assertThat(report).isEqualTo(
                Result.Success(
                    com.androidlearning.sliide.users.domain.model
                        .SyncReport(synced = 1, rejected = 0),
                ),
            )
            assertThat(h.apiClient.requests.last()).isEqualTo(RemoteRequest.Create(details()))
        }

    // endregion
}
