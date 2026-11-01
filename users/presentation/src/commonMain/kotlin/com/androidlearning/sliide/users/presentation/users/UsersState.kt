package com.androidlearning.sliide.users.presentation.users

import androidx.compose.runtime.Immutable

@Immutable
data class UsersState(
    val users: List<UserUi> = emptyList(),
    val isLoading: Boolean = true,
)
