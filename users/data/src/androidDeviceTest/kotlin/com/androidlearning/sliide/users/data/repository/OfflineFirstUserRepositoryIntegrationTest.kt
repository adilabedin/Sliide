package com.androidlearning.sliide.users.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.RoomDatabaseTransactionRunner
import com.androidlearning.sliide.core.database.SliideDatabase
import com.androidlearning.sliide.core.database.UserDao
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.users.data.fake.FakeUserRemoteDataSource
import com.androidlearning.sliide.users.data.fake.TestClock
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.SyncReport
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.domain.util.DataError
import com.androidlearning.sliide.users.domain.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Real [OfflineFirstUserRepository] + real file-backed Room database + real [RoomDatabaseTransactionRunner]; only the
 * network ([FakeUserRemoteDataSource]) and the clock are swapped. The database is file-backed so a test can close it
 * and reopen it with a fresh repository, simulating an app restart.
 */
class OfflineFirstUserRepositoryIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dbFile: File
    private lateinit var database: SliideDatabase
    private lateinit var syncScope: CoroutineScope
    private val remote = FakeUserRemoteDataSource()
    private val clock = TestClock()

    private val jane = UserDetails(name = "Jane Doe", email = "jane@example.com", gender = Gender.FEMALE, status = UserStatus.ACTIVE)

    @BeforeTest
    fun setUp() {
        dbFile = File(context.cacheDir, "offline-first-it-${System.nanoTime()}.db")
        syncScope = CoroutineScope(SupervisorJob())
        database = openDatabase()
    }

    @AfterTest
    fun tearDown() {
        syncScope.cancel()
        database.close()
        context.deleteDatabase(dbFile.absolutePath)
        listOf("", "-wal", "-shm", "-journal").forEach { File(dbFile.absolutePath + it).delete() }
    }

    @Test
    fun offlineCreateQueuesAndSurvivesDatabaseReopen() =
        runTest {
            remote.defaultError = DataError.Remote.NO_INTERNET

            val result = repository().createUser(jane)

            assertThat(result).isEqualTo(Result.Success(WriteStatus.QUEUED))
            reopenDatabase()

            val user = database.userDao.getAll().single()
            assertThat(user.id).isLessThan(0)
            assertThat(user.name).isEqualTo("Jane Doe")
            val pending = database.pendingSyncDao.getAllOrdered()
            assertThat(pending).hasSize(1)
            assertThat(pending.single().operation).isEqualTo(PendingOperation.CREATE)
            assertThat(pending.single().user).isEqualTo(user)
        }

    @Test
    fun syncAfterReopenRekeysToExactlyOneRowUnderServerId() =
        runTest {
            remote.defaultError = DataError.Remote.NO_INTERNET
            repository().createUser(jane)
            val tempId =
                database.userDao
                    .getAll()
                    .single()
                    .id
            reopenDatabase()

            remote.defaultError = null
            remote.nextServerId = 7012
            val report = repository().syncPending()

            assertThat(report).isInstanceOf(Result.Success::class)
            val users = database.userDao.getAll()
            assertThat(users.map { it.id }).containsExactly(7012L)
            assertThat(database.userDao.getById(tempId)).isEqualTo(null)
            assertThat(database.pendingSyncDao.count()).isEqualTo(0)
            assertThat(users.single().createdAtEpochMillis).isEqualTo(clock.now().toEpochMilliseconds())
        }

    @Test
    fun rejectedReplayDropsEntryAndTempUserAcrossReopen() =
        runTest {
            remote.defaultError = DataError.Remote.NO_INTERNET
            repository().createUser(jane)
            remote.defaultError = DataError.Remote.VALIDATION

            val report = repository().syncPending()
            reopenDatabase()

            assertThat(report).isEqualTo(Result.Success(SyncReport(synced = 0, rejected = 1)))
            assertThat(database.userDao.getAll()).isEmpty()
            assertThat(database.pendingSyncDao.count()).isEqualTo(0)
        }

    @Test
    fun failureInsideRekeyTransactionRollsBackEverything() =
        runTest {
            remote.defaultError = DataError.Remote.NO_INTERNET
            repository().createUser(jane)
            val before = database.userDao.getAll().single()
            remote.defaultError = null

            // The re-key deletes the temp row, then upserts the server user: failing that upsert must undo the delete.
            val failingDao = FailingUpsertUserDao(database.userDao)
            val result = runCatching { repository(userDao = failingDao).syncPending() }

            assertThat(result).isFailure()
            assertThat(database.userDao.getAll()).containsExactly(before)
            assertThat(database.pendingSyncDao.getByUserId(before.id)?.operation).isEqualTo(PendingOperation.CREATE)
        }

    private fun repository(userDao: UserDao = database.userDao) =
        OfflineFirstUserRepository(
            userDao = userDao,
            pendingSyncDao = database.pendingSyncDao,
            transactionRunner = RoomDatabaseTransactionRunner(database),
            remote = remote,
            clock = clock,
            syncScope = syncScope,
        )

    private fun openDatabase(): SliideDatabase =
        Room
            .databaseBuilder<SliideDatabase>(context, dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    private fun reopenDatabase() {
        database.close()
        database = openDatabase()
    }

    /** Delegates to the real DAO but throws when a server-id (positive) user is upserted. */
    private class FailingUpsertUserDao(
        private val delegate: UserDao,
    ) : UserDao by delegate {
        override suspend fun upsert(user: UserEntity) {
            if (user.id > 0) error("Simulated failure while writing server user ${user.id}")
            delegate.upsert(user)
        }
    }
}
