package com.androidlearning.sliide.core.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity

/**
 * A user write waiting to reach the API. There is at most one row per user: the embedded snapshot's `user_id` column is
 * the primary key. There is no foreign key to `users`, because a pending DELETE outlives its `users` row.
 * [queuedAtEpochMillis] is the first time this user was queued and is kept when a later write merges into the row.
 */
@Entity(tableName = DatabaseConstants.PendingSync.TABLE, primaryKeys = [DatabaseConstants.PendingSync.USER_ID])
data class PendingSyncEntity(
    @Embedded(prefix = DatabaseConstants.PendingSync.USER_PREFIX) val user: UserEntity,
    @ColumnInfo(name = DatabaseConstants.PendingSync.OPERATION) val operation: PendingOperation,
    @ColumnInfo(name = DatabaseConstants.PendingSync.QUEUED_AT) val queuedAtEpochMillis: Long,
)
