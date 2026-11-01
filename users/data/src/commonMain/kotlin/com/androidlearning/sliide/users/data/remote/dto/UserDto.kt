package com.androidlearning.sliide.users.data.remote.dto

import kotlinx.serialization.Serializable

/** A GoRest user as it appears on the wire. `gender` and `status` are lowercase strings (`"female"`, `"active"`). */
@Serializable
data class UserDto(
    val id: Long,
    val name: String,
    val email: String,
    val gender: String,
    val status: String,
)

/** Body of `POST /users` and `PATCH /users/{id}`. */
@Serializable
data class UserRequestDto(
    val name: String,
    val email: String,
    val gender: String,
    val status: String,
)
