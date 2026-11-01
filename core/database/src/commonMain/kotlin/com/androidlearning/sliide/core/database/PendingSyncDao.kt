package com.androidlearning.sliide.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PendingSyncDao {
    /** Every pending entry in replay order: first queued first, ties broken by user id. */
    @Query("SELECT * FROM pending_sync ORDER BY queued_at_epoch_millis ASC, user_id ASC")
    suspend fun getAllOrdered(): List<PendingSyncEntity>

    @Query("SELECT * FROM pending_sync WHERE user_id = :userId")
    suspend fun getByUserId(userId: Long): PendingSyncEntity?

    /** Inserts the entry, or replaces the existing entry for the same user. */
    @Upsert
    suspend fun upsert(entry: PendingSyncEntity)

    @Query("DELETE FROM pending_sync WHERE user_id = :userId")
    suspend fun deleteByUserId(userId: Long)

    @Query("SELECT COUNT(*) FROM pending_sync")
    suspend fun count(): Int
}
