package com.androidlearning.sliide.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    /** All cached users, newest first (ties broken by id, highest first). Emits again whenever the table changes. */
    @Query("SELECT * FROM users ORDER BY createdAtEpochMillis DESC, id DESC")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM users")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(users: List<UserEntity>)

    /** Atomically replaces the whole table with [users]. */
    @Transaction
    suspend fun replaceAll(users: List<UserEntity>) {
        deleteAll()
        insertAll(users)
    }

    /**
     * The next temporary id for a user created locally: one below the smallest id in `users` and `pending_sync`,
     * and never above -1. Server ids are positive, so a temporary id can never clash with one.
     */
    @Query(
        """
        SELECT MIN(-1, COALESCE(MIN(id), 0) - 1) FROM (
            SELECT id FROM users
            UNION ALL
            SELECT user_id AS id FROM pending_sync
        )
        """,
    )
    suspend fun nextTempId(): Long
}
