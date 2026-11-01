package com.androidlearning.sliide.users.data.mapper

import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlin.time.Instant

fun UserEntity.toUser(): User =
    User(
        id = id,
        name = name,
        email = email,
        gender = gender.toGender(),
        status = status.toUserStatus(),
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMillis),
    )

fun User.toUserEntity(): UserEntity =
    UserEntity(
        id = id,
        name = name,
        email = email,
        gender = gender.wireValue,
        status = status.wireValue,
        createdAtEpochMillis = createdAt.toEpochMilliseconds(),
    )

/** GoRest wire values: lowercase strings, e.g. `"female"`, `"active"`. */
internal val Gender.wireValue: String get() = name.lowercase()

internal val UserStatus.wireValue: String get() = name.lowercase()

internal fun String.toGender(): Gender = Gender.entries.first { it.wireValue == lowercase() }

internal fun String.toUserStatus(): UserStatus = UserStatus.entries.first { it.wireValue == lowercase() }
