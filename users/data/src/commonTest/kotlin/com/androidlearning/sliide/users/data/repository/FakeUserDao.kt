package com.androidlearning.sliide.users.data.repository

import com.androidlearning.sliide.core.database.UserDao
import com.androidlearning.sliide.core.database.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeUserDao : UserDao {
    private val users = MutableStateFlow<Map<Long, UserEntity>>(emptyMap())

    override fun observeAll(): Flow<List<UserEntity>> = users.map { all -> all.values.sortedByDescending { it.createdAtEpochMillis } }

    override suspend fun upsert(user: UserEntity) {
        users.update { it + (user.id to user) }
    }

    override suspend fun deleteById(id: Long) {
        users.update { it - id }
    }
}
