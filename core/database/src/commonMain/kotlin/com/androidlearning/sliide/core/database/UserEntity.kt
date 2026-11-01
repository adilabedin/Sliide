package com.androidlearning.sliide.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached GoRest user. [gender] and [status] are stored as their wire strings; [createdAtEpochMillis]
 * is set locally because GoRest returns no timestamp.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val email: String,
    val gender: String,
    val status: String,
    val createdAtEpochMillis: Long,
)
