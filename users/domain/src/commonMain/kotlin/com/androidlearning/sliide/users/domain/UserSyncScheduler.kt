package com.androidlearning.sliide.users.domain

/**
 * Schedules the periodic background replay of the pending-sync queue (spec: background-sync). Scheduling only: the
 * replay itself is [UserRepository.syncPending].
 */
interface UserSyncScheduler {
    /**
     * Ensures exactly one periodic background replay is scheduled. Idempotent and safe to call on every launch: an
     * existing schedule is kept, so its interval does not restart and a run in progress is not cancelled.
     */
    fun schedulePeriodicSync()
}
