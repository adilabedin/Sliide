package com.androidlearning.sliide.users.data.sync

import com.androidlearning.sliide.core.database.PendingOperation.CREATE
import com.androidlearning.sliide.core.database.PendingOperation.DELETE
import com.androidlearning.sliide.core.database.PendingSyncEntity

/**
 * Merges a new write into a user's existing pending entry, so the queue holds at most one entry per user.
 *
 * | existing \ incoming | UPDATE                    | DELETE                      |
 * |---------------------|---------------------------|-----------------------------|
 * | none                | UPDATE                    | DELETE                      |
 * | CREATE              | CREATE, new snapshot      | `null` (cancel both)        |
 * | UPDATE              | UPDATE, new snapshot      | DELETE                      |
 *
 * A merged entry keeps the existing `queuedAtEpochMillis`, so the user keeps their place in the replay order.
 * No write can follow a pending DELETE (the user is already gone locally), and an incoming CREATE always has a fresh
 * temp id, so those combinations never reach this function.
 */
object PendingSyncMerger {
    /** Returns the entry to upsert, or `null` when the existing entry must be deleted. */
    fun merge(
        existing: PendingSyncEntity?,
        incoming: PendingSyncEntity,
    ): PendingSyncEntity? =
        when {
            existing == null -> incoming
            existing.operation == CREATE && incoming.operation == DELETE -> null // never reached the server
            existing.operation == CREATE -> existing.copy(user = incoming.user) // still a CREATE, new data
            else -> incoming.copy(queuedAtEpochMillis = existing.queuedAtEpochMillis) // latest write wins
        }
}
