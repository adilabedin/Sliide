package com.androidlearning.sliide.users.domain.model

/** Result of one replay pass over the pending-sync queue. */
data class SyncReport(
    val synced: Int,
    val rejected: Int,
)
