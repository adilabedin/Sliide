package com.androidlearning.sliide.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UserDaoTest {
    private lateinit var database: SliideDatabase
    private lateinit var dao: UserDao

    @BeforeTest
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database =
            Room
                .inMemoryDatabaseBuilder<SliideDatabase>(context)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        dao = database.userDao
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertedUserIsObserved() =
        runTest {
            dao.observeAll().test {
                assertThat(awaitItem()).isEmpty()

                dao.upsert(jane)

                assertThat(awaitItem()).containsExactly(jane)
            }
        }

    @Test
    fun deletedUserDisappears() =
        runTest {
            dao.upsert(jane)
            dao.observeAll().test {
                assertThat(awaitItem()).containsExactly(jane)

                dao.deleteById(jane.id)

                assertThat(awaitItem()).isEmpty()
            }
        }

    @Test
    fun usersAreOrderedNewestFirst() =
        runTest {
            dao.upsert(jane)
            dao.upsert(john)

            dao.observeAll().test {
                assertThat(awaitItem()).containsExactly(john, jane)
            }
        }

    @Test
    fun upsertReplacesExistingUser() =
        runTest {
            dao.upsert(jane)
            val renamed = jane.copy(name = "Jane Smith")

            dao.upsert(renamed)

            dao.observeAll().test {
                assertThat(awaitItem()).containsExactly(renamed)
            }
        }

    @Test
    fun usersWithEqualTimestampsAreOrderedByIdDescending() =
        runTest {
            val sameTime = john.copy(createdAtEpochMillis = jane.createdAtEpochMillis)
            dao.upsert(jane)
            dao.upsert(sameTime)

            dao.observeAll().test {
                assertThat(awaitItem()).containsExactly(sameTime, jane)
            }
        }

    @Test
    fun replaceAllSwapsTheWholeTable() =
        runTest {
            dao.upsert(jane)
            dao.upsert(john)
            val newcomer = jane.copy(id = 7_401_003, email = "new@example.com")

            dao.replaceAll(listOf(newcomer))

            assertThat(dao.getAll()).containsExactlyInAnyOrder(newcomer)
        }

    @Test
    fun getByIdReturnsUserOrNull() =
        runTest {
            dao.upsert(jane)

            assertThat(dao.getById(jane.id)).isEqualTo(jane)
            assertThat(dao.getById(-99)).isEqualTo(null)
        }

    @Test
    fun nextTempIdIsMinusOneOnEmptyDatabase() =
        runTest {
            assertThat(dao.nextTempId()).isEqualTo(-1L)
        }

    @Test
    fun nextTempIdIsMinusOneWhenOnlyServerIdsExist() =
        runTest {
            dao.upsert(jane)

            assertThat(dao.nextTempId()).isEqualTo(-1L)
        }

    @Test
    fun nextTempIdStaysBelowTheMinimumAcrossBothTables() =
        runTest {
            dao.upsert(jane.copy(id = -3))
            database.pendingSyncDao.upsert(
                PendingSyncEntity(
                    user = jane.copy(id = -7),
                    operation = PendingOperation.CREATE,
                    queuedAtEpochMillis = 1L,
                ),
            )

            assertThat(dao.nextTempId()).isEqualTo(-8L)
        }

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
        val john =
            UserEntity(
                id = 7_401_002,
                name = "John Roe",
                email = "john.roe@example.com",
                gender = "male",
                status = "inactive",
                createdAtEpochMillis = 1_700_000_060_000,
            )
    }
}
