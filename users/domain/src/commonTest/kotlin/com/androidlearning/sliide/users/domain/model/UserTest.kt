package com.androidlearning.sliide.users.domain.model

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.time.Instant

class UserTest {
    private fun user(id: Long) =
        User(
            id = id,
            name = "Jane Doe",
            email = "jane@example.com",
            gender = Gender.FEMALE,
            status = UserStatus.ACTIVE,
            createdAt = Instant.fromEpochMilliseconds(0),
        )

    @Test
    fun temporaryIdIsLocalOnly() {
        assertThat(user(-1).isLocalOnly).isTrue()
    }

    @Test
    fun serverIdIsNotLocalOnly() {
        assertThat(user(7_401_001).isLocalOnly).isFalse()
    }
}
