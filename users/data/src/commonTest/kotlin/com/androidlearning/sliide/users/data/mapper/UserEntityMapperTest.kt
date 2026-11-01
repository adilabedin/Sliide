package com.androidlearning.sliide.users.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlin.test.Test
import kotlin.time.Instant

class UserEntityMapperTest {
    private val user =
        User(
            id = 7_401_001,
            name = "Jane Doe",
            email = "jane.doe@example.com",
            gender = Gender.FEMALE,
            status = UserStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(1_700_000_000_123),
        )

    @Test
    fun userMapsToEntityWithWireValues() {
        assertThat(user.toUserEntity()).isEqualTo(
            UserEntity(
                id = 7_401_001,
                name = "Jane Doe",
                email = "jane.doe@example.com",
                gender = "female",
                status = "active",
                createdAtEpochMillis = 1_700_000_000_123,
            ),
        )
    }

    @Test
    fun roundTripPreservesEveryGenderAndStatus() {
        Gender.entries.forEach { gender ->
            UserStatus.entries.forEach { status ->
                val original = user.copy(gender = gender, status = status)

                assertThat(original.toUserEntity().toUser()).isEqualTo(original)
            }
        }
    }

    @Test
    fun wireValuesAreCaseInsensitive() {
        assertThat("MALE".toGender()).isEqualTo(Gender.MALE)
        assertThat("Inactive".toUserStatus()).isEqualTo(UserStatus.INACTIVE)
    }
}
