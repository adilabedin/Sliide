package com.androidlearning.sliide.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
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
