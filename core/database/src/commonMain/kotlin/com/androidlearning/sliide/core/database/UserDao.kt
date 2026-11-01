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
    @Query(
        "SELECT * FROM ${DatabaseConstants.Users.TABLE} ORDER BY ${DatabaseConstants.Users.CREATED_AT} DESC, ${DatabaseConstants.Users.ID} DESC",
    )
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT * FROM ${DatabaseConstants.Users.TABLE}")
    suspend fun getAll(): List<UserEntity>

    @Query("SELECT * FROM ${DatabaseConstants.Users.TABLE} WHERE ${DatabaseConstants.Users.ID} = :id")
    suspend fun getById(id: Long): UserEntity?

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM ${DatabaseConstants.Users.TABLE} WHERE ${DatabaseConstants.Users.ID} = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ${DatabaseConstants.Users.TABLE}")
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
            SELECT ${DatabaseConstants.Users.ID} AS id FROM ${DatabaseConstants.Users.TABLE}
            UNION ALL
            SELECT ${DatabaseConstants.PendingSync.USER_ID} AS id FROM ${DatabaseConstants.PendingSync.TABLE}
        )
        """,
    )
    suspend fun nextTempId(): Long
}
