package com.androidlearning.sliide.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity

/**
 * A user write waiting to reach the API. There is at most one row per user: the embedded snapshot's `user_id` column is
 * the primary key. There is no foreign key to `users`, because a pending DELETE outlives its `users` row.
 * [queuedAtEpochMillis] is the first time this user was queued and is kept when a later write merges into the row.
 */
@Entity(tableName = "pending_sync", primaryKeys = ["user_id"])
data class PendingSyncEntity(
    @Embedded(prefix = "user_") val user: UserEntity,
    @ColumnInfo(name = "operation") val operation: PendingOperation,
    @ColumnInfo(name = "queued_at_epoch_millis") val queuedAtEpochMillis: Long,
)
