package com.androidlearning.sliide.users.domain.model

import kotlin.time.Instant

/** A GoRest user. [createdAt] is recorded locally because the API returns no timestamp. */
data class User(
    val id: Long,
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
    val createdAt: Instant,
)

enum class Gender {
    MALE,
    FEMALE,
}

enum class UserStatus {
    ACTIVE,
    INACTIVE,
}
