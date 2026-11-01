package com.androidlearning.sliide.users.presentation.users

import androidx.compose.runtime.Immutable
import com.androidlearning.sliide.users.domain.model.User

@Immutable
data class UserUi(
    val id: Long,
    val name: String,
    val email: String,
)

fun User.toUserUi(): UserUi = UserUi(id = id, name = name, email = email)
