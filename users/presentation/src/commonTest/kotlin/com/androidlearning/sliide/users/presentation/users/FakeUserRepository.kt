package com.androidlearning.sliide.users.presentation.users

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Emits nothing until [emit] is called, so tests can observe the initial loading state. */
class FakeUserRepository : UserRepository {
    private val users = MutableSharedFlow<List<User>>(replay = 1)

    var writeResult: Result<WriteStatus, DataError> = Result.Success(WriteStatus.SYNCED)
    var syncResult: Result<SyncReport, DataError.Remote> = Result.Success(SyncReport(synced = 0, rejected = 0))
    var refreshResult: EmptyResult<DataError> = Result.Success(Unit)
    var removeResult: Result<User, DataError.Local> = Result.Failure(DataError.Local.NOT_FOUND)

    val restoredUsers = mutableListOf<User>()

    suspend fun emit(value: List<User>) = users.emit(value)

    override fun observeUsers(): Flow<List<User>> = users

    override suspend fun createUser(details: UserDetails): Result<WriteStatus, DataError> = writeResult

    override suspend fun updateUser(user: User): Result<WriteStatus, DataError> = writeResult

    override suspend fun removeUserLocally(id: Long): Result<User, DataError.Local> = removeResult

    override suspend fun restoreUser(user: User) {
        restoredUsers += user
    }

    override suspend fun finalizeDelete(user: User): Result<WriteStatus, DataError> = writeResult

    override suspend fun syncPending(): Result<SyncReport, DataError.Remote> = syncResult

    override suspend fun refresh(): EmptyResult<DataError> = refreshResult
}
