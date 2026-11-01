package com.androidlearning.sliide.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    /** All cached users, newest first. Emits again whenever the table changes. */
    @Query("SELECT * FROM users ORDER BY createdAtEpochMillis DESC")
    fun observeAll(): Flow<List<UserEntity>>

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long)
}
