package com.androidlearning.sliide.users.data.repository

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.data.mapper.toUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** Spec user-write-errors: "Direct writes and replay decide the same way". */
class OfflineFirstUserRepositoryPolicyParityTest {
    /**
     * The decision a failed write reached, independent of the path: KEPT (still queued), DONE (a delete of a user already
     * gone: direct returns success, sync counts it synced) or REJECTED (direct returns the error, sync counts it rejected).
     */
    private enum class Outcome { KEPT, REJECTED, DONE }

    private val errors: List<DataError> =
        listOf(
            DataError.Remote.SERVER_ERROR,
            DataError.Remote.NO_INTERNET,
            DataError.Remote.UNAUTHORIZED,
            DataError.Remote.TOO_MANY_REQUESTS,
            DataError.Remote.UNKNOWN,
            emailTaken,
            DataError.Validation(emptyList()),
            DataError.Remote.NOT_FOUND,
        )

    @Test
    fun createDecidesTheSameWayInBothPaths() =
        runTest {
            errors.forEach { error ->
                assertThat(replayed(backgroundScope, PendingOperation.CREATE, error), "replay $error")
                    .isEqualTo(direct(backgroundScope, PendingOperation.CREATE, error))
            }
        }

    @Test
    fun updateDecidesTheSameWayInBothPaths() =
        runTest {
            errors.forEach { error ->
                assertThat(replayed(backgroundScope, PendingOperation.UPDATE, error), "replay $error")
                    .isEqualTo(direct(backgroundScope, PendingOperation.UPDATE, error))
            }
        }

    @Test
    fun deleteDecidesTheSameWayInBothPaths() =
        runTest {
            errors.forEach { error ->
                assertThat(replayed(backgroundScope, PendingOperation.DELETE, error), "replay $error")
                    .isEqualTo(direct(backgroundScope, PendingOperation.DELETE, error))
            }
        }

    @Test
    fun serverErrorIsKeptAndFieldErrorRejectedOnCreateInBothPaths() =
        runTest {
            listOf(
                DataError.Remote.SERVER_ERROR to Outcome.KEPT,
                emailTaken to Outcome.REJECTED,
            ).forEach { (error, expected) ->
                assertThat(direct(backgroundScope, PendingOperation.CREATE, error), "direct $error").isEqualTo(expected)
                assertThat(replayed(backgroundScope, PendingOperation.CREATE, error), "replay $error").isEqualTo(expected)
            }
        }

    private suspend fun direct(
        scope: CoroutineScope,
        operation: PendingOperation,
        error: DataError,
    ): Outcome {
        val h = RepositoryHarness(scope)
        val result =
            when (operation) {
                PendingOperation.CREATE -> {
                    h.apiClient.createResults += Result.Failure(error)
                    h.repository.createUser(details())
                }
                PendingOperation.UPDATE -> {
                    val user = h.seedUser(userEntity(42)).toUser()
                    h.apiClient.updateResults += Result.Failure(error)
                    h.repository.updateUser(user.copy(name = "Edited"))
                }
                PendingOperation.DELETE -> {
                    val user = h.seedUser(userEntity(42)).toUser()
                    h.apiClient.deleteResults += Result.Failure(error)
                    h.repository.deleteUser(user)
                }
            }
        return when {
            h.pendingSyncDao.entries.isNotEmpty() -> Outcome.KEPT
            result is Result.Success -> Outcome.DONE
            else -> Outcome.REJECTED
        }
    }

    private suspend fun replayed(
        scope: CoroutineScope,
        operation: PendingOperation,
        error: DataError,
    ): Outcome {
        val h = RepositoryHarness(scope)
        val id = if (operation == PendingOperation.CREATE) -1L else 42L
        val entity = userEntity(id)
        if (operation != PendingOperation.DELETE) h.seedUser(entity)
        h.seedPending(entity, operation)
        when (operation) {
            PendingOperation.CREATE -> h.apiClient.createResults += Result.Failure(error)
            PendingOperation.UPDATE -> h.apiClient.updateResults += Result.Failure(error)
            PendingOperation.DELETE -> h.apiClient.deleteResults += Result.Failure(error)
        }
        val report = (h.repository.syncPending() as? Result.Success)?.data
        return when {
            h.pendingSyncDao.entries.isNotEmpty() -> Outcome.KEPT
            report?.synced == 1 -> Outcome.DONE
            else -> Outcome.REJECTED
        }
    }
}
