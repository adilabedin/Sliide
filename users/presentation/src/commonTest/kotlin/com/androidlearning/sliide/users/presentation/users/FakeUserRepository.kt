package com.androidlearning.sliide.users.presentation.users

import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Emits nothing until [emit] is called, so tests can observe the initial loading state. */
class FakeUserRepository : UserRepository {
    private val users = MutableSharedFlow<List<User>>(replay = 1)

    suspend fun emit(value: List<User>) = users.emit(value)

    override fun observeUsers(): Flow<List<User>> = users
}
