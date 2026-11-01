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
) {
    /**
     * True while the user exists only in the local cache: it was created offline (or its create is still queued) and
     * holds a temporary negative id until the server confirms it and the row is re-keyed to the server id.
     */
    val isLocalOnly: Boolean
        get() = id < 0
}

enum class Gender {
    MALE,
    FEMALE,
}

enum class UserStatus {
    ACTIVE,
    INACTIVE,
}
