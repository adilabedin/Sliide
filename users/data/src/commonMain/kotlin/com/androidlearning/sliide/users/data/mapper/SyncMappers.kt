package com.androidlearning.sliide.users.data.mapper

import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingSyncEntity
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails

/** Builds a cache row for [details] under [id]; used for offline creates (temp id) and local updates. */
fun UserDetails.toUserEntity(
    id: Long,
    createdAtEpochMillis: Long,
): UserEntity =
    UserEntity(
        id = id,
        name = name,
        email = email,
        gender = gender.wireValue,
        status = status.wireValue,
        createdAtEpochMillis = createdAtEpochMillis,
    )

/**
 * Builds a cache row for a server user. GoRest returns no timestamp, so the caller passes [createdAtEpochMillis]:
 * the existing row's value to keep it, or "now" for a user seen for the first time.
 */
fun RemoteUser.toUserEntity(createdAtEpochMillis: Long): UserEntity = details.toUserEntity(id, createdAtEpochMillis)

fun UserEntity.toUserDetails(): UserDetails =
    UserDetails(
        name = name,
        email = email,
        gender = gender.toGender(),
        status = status.toUserStatus(),
    )

fun UserEntity.toPendingSync(
    operation: PendingOperation,
    queuedAtEpochMillis: Long,
): PendingSyncEntity =
    PendingSyncEntity(
        user = this,
        operation = operation,
        queuedAtEpochMillis = queuedAtEpochMillis,
    )
