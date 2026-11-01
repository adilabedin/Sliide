package com.androidlearning.sliide.users.domain

import com.androidlearning.sliide.users.domain.model.User
import kotlinx.coroutines.flow.Flow

interface UserRepository {
    /** Users from the local cache, newest first. Emits again whenever the cache changes. */
    fun observeUsers(): Flow<List<User>>
}
