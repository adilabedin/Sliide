package com.androidlearning.sliide.users.domain.model

/** The editable fields of a user: the input for create and update. */
data class UserDetails(
    val name: String,
    val email: String,
    val gender: Gender,
    val status: UserStatus,
)
