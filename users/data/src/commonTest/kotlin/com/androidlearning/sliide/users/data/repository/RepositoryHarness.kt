package com.androidlearning.sliide.users.data.repository

import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingSyncEntity
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.FieldError
import com.androidlearning.sliide.users.data.fake.FakeDatabaseTransactionRunner
import com.androidlearning.sliide.users.data.fake.FakePendingSyncDao
import com.androidlearning.sliide.users.data.fake.FakeUserApiClient
import com.androidlearning.sliide.users.data.fake.FakeUserDao
import com.androidlearning.sliide.users.data.fake.FakeUserSyncScheduler
import com.androidlearning.sliide.users.data.fake.TestClock
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlinx.coroutines.CoroutineScope

/**
 * Real [OfflineFirstUserRepository] over in-memory fakes. [newRepository] builds a second instance over the same
 * tables, which is how tests simulate an app restart (in-memory state such as the Undo-window set is lost).
 */
class RepositoryHarness(
    private val scope: CoroutineScope,
) {
    val pendingSyncDao = FakePendingSyncDao()
    val userDao = FakeUserDao(pendingSyncDao)
    val transactionRunner = FakeDatabaseTransactionRunner(userDao, pendingSyncDao)
    val apiClient = FakeUserApiClient()
    val clock = TestClock()
    val syncScheduler = FakeUserSyncScheduler()
    val repository = newRepository()

    fun newRepository(applicationScope: CoroutineScope = scope): OfflineFirstUserRepository =
        OfflineFirstUserRepository(
            userDao = userDao,
            pendingSyncDao = pendingSyncDao,
            transactionRunner = transactionRunner,
            apiClient = apiClient,
            clock = clock,
            applicationScope = applicationScope,
            syncScheduler = syncScheduler,
        )

    suspend fun seedUser(entity: UserEntity): UserEntity = entity.also { userDao.upsert(it) }

    suspend fun seedPending(
        entity: UserEntity,
        operation: PendingOperation,
        queuedAt: Long = entity.createdAtEpochMillis,
    ): PendingSyncEntity =
        PendingSyncEntity(user = entity, operation = operation, queuedAtEpochMillis = queuedAt).also { pendingSyncDao.upsert(it) }
}

fun details(
    name: String = "Jane Doe",
    email: String = "jane@example.com",
): UserDetails = UserDetails(name = name, email = email, gender = Gender.FEMALE, status = UserStatus.ACTIVE)

fun userEntity(
    id: Long,
    name: String = "User $id",
    email: String = "user$id@example.com",
    createdAt: Long = 1_000L + id,
): UserEntity =
    UserEntity(
        id = id,
        name = name,
        email = email,
        gender = "female",
        status = "active",
        createdAtEpochMillis = createdAt,
    )

fun remoteUser(
    id: Long,
    name: String = "User $id",
    email: String = "user$id@example.com",
): RemoteUser = RemoteUser(id = id, details = details(name = name, email = email))

/** The 422 GoRest returns for a taken email. */
val emailTaken: DataError = DataError.Validation(listOf(FieldError("email", "has already been taken")))
