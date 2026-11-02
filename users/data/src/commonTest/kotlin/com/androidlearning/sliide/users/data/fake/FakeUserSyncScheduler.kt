package com.androidlearning.sliide.users.data.fake

import com.androidlearning.sliide.users.domain.UserSyncScheduler

/** Records sync requests instead of scheduling anything, so tests can assert when the repository asks for a sync. */
class FakeUserSyncScheduler : UserSyncScheduler {
    private val requested = mutableListOf<Long>()

    /** User ids passed to [requestSync], in call order. */
    val requestedUserIds: List<Long> get() = requested.toList()

    var periodicScheduleCount = 0
        private set

    override fun schedulePeriodicSync() {
        periodicScheduleCount++
    }

    override fun requestSync(userId: Long) {
        requested += userId
    }
}
