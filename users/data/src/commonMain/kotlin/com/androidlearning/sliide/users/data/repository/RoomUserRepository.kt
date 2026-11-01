package com.androidlearning.sliide.users.data.repository

import com.androidlearning.sliide.core.database.UserDao
import com.androidlearning.sliide.users.data.mapper.toUser
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Reads users from the Room cache, which is the single source of truth for the UI. */
class RoomUserRepository(
    private val userDao: UserDao,
) : UserRepository {
    override fun observeUsers(): Flow<List<User>> = userDao.observeAll().map { entities -> entities.map { it.toUser() } }
}
