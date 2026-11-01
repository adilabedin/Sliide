package com.androidlearning.sliide.users.presentation.fake

import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlin.time.Instant

val TEST_NOW: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)

val jane =
    User(
        id = 1,
        name = "Jane Doe",
        email = "jane.doe@example.com",
        gender = Gender.FEMALE,
        status = UserStatus.ACTIVE,
        createdAt = Instant.fromEpochMilliseconds(TEST_NOW.toEpochMilliseconds() - 5 * 60_000),
    )

val rahul =
    User(
        id = 2,
        name = "Rahul Sharma",
        email = "rahul.sharma@example.in",
        gender = Gender.MALE,
        status = UserStatus.INACTIVE,
        createdAt = Instant.fromEpochMilliseconds(TEST_NOW.toEpochMilliseconds() - 3 * 3_600_000),
    )

val pendingAmelie =
    User(
        id = -1,
        name = "Amélie Laurent",
        email = "amelie.laurent@example.fr",
        gender = Gender.FEMALE,
        status = UserStatus.ACTIVE,
        createdAt = TEST_NOW,
    )
