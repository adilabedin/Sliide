package com.androidlearning.sliide.users.data.repository

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Instant

class RoomUserRepositoryTest {
    private val dao = FakeUserDao()
    private val repository = RoomUserRepository(dao)

    @Test
    fun emitsMappedUsersAndReEmitsOnChange() =
        runTest {
            repository.observeUsers().test {
                assertThat(awaitItem()).isEmpty()

                dao.upsert(janeEntity)
                assertThat(awaitItem()).containsExactly(jane)

                dao.deleteById(janeEntity.id)
                assertThat(awaitItem()).isEmpty()
            }
        }

    private companion object {
        val janeEntity =
            UserEntity(
                id = 7_401_001,
                name = "Jane Doe",
                email = "jane.doe@example.com",
                gender = "female",
                status = "active",
                createdAtEpochMillis = 1_700_000_000_000,
            )
        val jane =
            User(
                id = 7_401_001,
                name = "Jane Doe",
                email = "jane.doe@example.com",
                gender = Gender.FEMALE,
                status = UserStatus.ACTIVE,
                createdAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
            )
    }
}
