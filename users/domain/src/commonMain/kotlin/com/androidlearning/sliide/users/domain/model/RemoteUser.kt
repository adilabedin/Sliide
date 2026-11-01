package com.androidlearning.sliide.users.domain.model

/** A user as returned by GoRest: a server-assigned [id] and no timestamp. */
data class RemoteUser(
    val id: Long,
    val details: UserDetails,
)
