package com.androidlearning.sliide.users.presentation.fake

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlin.time.Duration

/**
 * In-memory [UserRepository] for presentation tests.
 *
 * - The cache emits nothing until [emit] is called, so tests can observe the initial loading state.
 * - [removeUserLocally] / [restoreUser] edit the emitted list like the real repository. A failed [finalizeDelete]
 *   leaves the user removed, as the real repository does until the next refresh.
 * - [observeDelay] holds back each new subscriber's first list, as Room's query does, so a resubscribing feed can be
 *   seen before the cache answers.
 * - Every method records its calls; results are scripted through the `*Result` vars; a non-null `*Gate` suspends the
 *   call until the test completes it.
 */
class FakeUserRepository : UserRepository {
    private val users = MutableSharedFlow<List<User>>(replay = 1)
    private var current: List<User> = emptyList()

    var createResult: Result<WriteStatus, DataError> = Result.Success(WriteStatus.SYNCED)
    var updateResult: Result<WriteStatus, DataError> = Result.Success(WriteStatus.SYNCED)
    var finalizeResult: Result<WriteStatus, DataError> = Result.Success(WriteStatus.SYNCED)
    var refreshResult: EmptyResult<DataError> = Result.Success(Unit)
    var syncResult: Result<SyncReport, DataError.Remote> = Result.Success(SyncReport(synced = 0, rejected = 0))

    var observeDelay: Duration = Duration.ZERO

    var refreshGate: CompletableDeferred<Unit>? = null
    var saveGate: CompletableDeferred<Unit>? = null
    var finalizeGate: CompletableDeferred<Unit>? = null

    val createdDetails = mutableListOf<UserDetails>()
    val updatedUsers = mutableListOf<User>()
    val removedIds = mutableListOf<Long>()
    val restoredUsers = mutableListOf<User>()
    val finalizedUsers = mutableListOf<User>()
    var refreshCount = 0
        private set

    suspend fun emit(value: List<User>) {
        current = value
        users.emit(value)
    }

    override fun observeUsers(): Flow<List<User>> = users.onStart { if (observeDelay > Duration.ZERO) delay(observeDelay) }

    override suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError> {
        createdDetails += details
        saveGate?.await()
        return createResult
    }

    override suspend fun updateUser(user: User): Result<WriteStatus, DataError> {
        updatedUsers += user
        saveGate?.await()
        return updateResult
    }

    override suspend fun removeUserLocally(id: Long): Result<User, DataError.Local> {
        removedIds += id
        val user = current.firstOrNull { it.id == id } ?: return Result.Failure(DataError.Local.NOT_FOUND)
        emit(current - user)
        return Result.Success(user)
    }

    override suspend fun restoreUser(user: User) {
        restoredUsers += user
        emit((current + user).sortedByDescending { it.createdAt })
    }

    override suspend fun finalizeDelete(user: User): Result<WriteStatus, DataError> {
        finalizedUsers += user
        finalizeGate?.await()
        return finalizeResult
    }

    override suspend fun syncPending(): Result<SyncReport, DataError.Remote> = syncResult

    override suspend fun syncPending(userId: Long): Result<SyncReport, DataError.Remote> = syncResult

    override suspend fun refresh(): EmptyResult<DataError> {
        refreshCount++
        refreshGate?.await()
        return refreshResult
    }
}
