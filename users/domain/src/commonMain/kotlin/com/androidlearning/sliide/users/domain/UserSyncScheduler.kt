package com.androidlearning.sliide.users.domain

/**
 * Schedules background replays of the pending-sync queue (spec: background-sync). Scheduling only: the replay itself
 * is [UserRepository.syncPending]. The repository calls [requestSync] when it queues a write; [schedulePeriodicSync]
 * is the safety net for anything a per-user request missed.
 */
interface UserSyncScheduler {
    /**
     * Ensures exactly one periodic background replay is scheduled. Idempotent and safe to call on every launch: an
     * existing schedule is kept, so its interval does not restart and a run in progress is not cancelled.
     */
    fun schedulePeriodicSync()

    /**
     * Asks the OS to replay [userId]'s pending entry in the background as soon as the device is online. Fire-and-forget
     * and safe to call repeatedly: at most one sync stays pending per user, and a new request supersedes an older one.
     * A platform with no per-item background task may run a full replay instead (iOS).
     */
    fun requestSync(userId: Long)
}
