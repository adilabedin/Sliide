package com.androidlearning.sliide.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class PendingSyncDaoTest {
    private lateinit var database: SliideDatabase
    private lateinit var dao: PendingSyncDao

    @BeforeTest
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database =
            Room
                .inMemoryDatabaseBuilder<SliideDatabase>(context)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        dao = database.pendingSyncDao
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertReplacesTheEntryForTheSameUser() =
        runTest {
            dao.upsert(entry(jane, PendingOperation.UPDATE, queuedAt = 10))
            val replacement = entry(jane.copy(name = "Jane Smith"), PendingOperation.DELETE, queuedAt = 10)

            dao.upsert(replacement)

            assertThat(dao.getAllOrdered()).containsExactly(replacement)
            assertThat(dao.count()).isEqualTo(1)
        }

    @Test
    fun entriesAreOrderedByQueuedAtThenUserId() =
        runTest {
            val late = entry(jane.copy(id = 1), PendingOperation.UPDATE, queuedAt = 30)
            val earlyHighId = entry(jane.copy(id = 9), PendingOperation.UPDATE, queuedAt = 10)
            val earlyLowId = entry(jane.copy(id = 2), PendingOperation.DELETE, queuedAt = 10)
            dao.upsert(late)
            dao.upsert(earlyHighId)
            dao.upsert(earlyLowId)

            assertThat(dao.getAllOrdered()).containsExactly(earlyLowId, earlyHighId, late)
        }

    @Test
    fun snapshotRoundTrips() =
        runTest {
            val pending = entry(jane.copy(id = -1), PendingOperation.CREATE, queuedAt = 42)

            dao.upsert(pending)

            assertThat(dao.getByUserId(-1)).isEqualTo(pending)
        }

    @Test
    fun entrySurvivesWithoutAMatchingUsersRow() =
        runTest {
            database.userDao.upsert(jane)
            dao.upsert(entry(jane, PendingOperation.DELETE, queuedAt = 5))

            database.userDao.deleteById(jane.id)

            assertThat(database.userDao.getById(jane.id)).isNull()
            assertThat(dao.getByUserId(jane.id)?.operation).isEqualTo(PendingOperation.DELETE)
        }

    @Test
    fun deleteByUserIdRemovesTheEntry() =
        runTest {
            dao.upsert(entry(jane, PendingOperation.UPDATE, queuedAt = 5))

            dao.deleteByUserId(jane.id)

            assertThat(dao.getByUserId(jane.id)).isNull()
            assertThat(dao.count()).isEqualTo(0)
        }

    @Test
    fun transactionRunnerCommitsWritesAcrossTables() =
        runTest {
            val runner = RoomDatabaseTransactionRunner(database)

            runner.inTransaction {
                database.userDao.upsert(jane)
                dao.upsert(entry(jane, PendingOperation.UPDATE, queuedAt = 5))
            }

            assertThat(database.userDao.getById(jane.id)).isEqualTo(jane)
            assertThat(dao.count()).isEqualTo(1)
        }

    @Test
    fun transactionRunnerRollsBackEveryWriteWhenTheBlockThrows() =
        runTest {
            val runner = RoomDatabaseTransactionRunner(database)

            assertFailure {
                runner.inTransaction {
                    database.userDao.upsert(jane)
                    dao.upsert(entry(jane, PendingOperation.UPDATE, queuedAt = 5))
                    throw IllegalStateException("boom")
                }
            }.isInstanceOf(IllegalStateException::class)

            assertThat(database.userDao.getById(jane.id)).isNull()
            assertThat(dao.count()).isEqualTo(0)
        }

    private fun entry(
        user: UserEntity,
        operation: PendingOperation,
        queuedAt: Long,
    ) = PendingSyncEntity(user = user, operation = operation, queuedAtEpochMillis = queuedAt)

    private companion object {
        val jane =
            UserEntity(
                id = 7_401_001,
                name = "Jane Doe",
                email = "jane.doe@example.com",
                gender = "female",
                status = "active",
                createdAtEpochMillis = 1_700_000_000_000,
            )
    }
}
