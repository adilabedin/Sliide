package com.androidlearning.sliide.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PendingSyncDao {
    /** Every pending entry in replay order: first queued first, ties broken by user id. */
    @Query(
        "SELECT * FROM ${DatabaseConstants.PendingSync.TABLE} ORDER BY ${DatabaseConstants.PendingSync.QUEUED_AT} ASC, ${DatabaseConstants.PendingSync.USER_ID} ASC",
    )
    suspend fun getAllOrdered(): List<PendingSyncEntity>

    @Query("SELECT * FROM ${DatabaseConstants.PendingSync.TABLE} WHERE ${DatabaseConstants.PendingSync.USER_ID} = :userId")
    suspend fun getByUserId(userId: Long): PendingSyncEntity?

    /** Inserts the entry, or replaces the existing entry for the same user. */
    @Upsert
    suspend fun upsert(entry: PendingSyncEntity)

    @Query("DELETE FROM ${DatabaseConstants.PendingSync.TABLE} WHERE ${DatabaseConstants.PendingSync.USER_ID} = :userId")
    suspend fun deleteByUserId(userId: Long)

    @Query("SELECT COUNT(*) FROM ${DatabaseConstants.PendingSync.TABLE}")
    suspend fun count(): Int
}
