package com.androidlearning.sliide.users.presentation.fake

import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlin.time.Clock
import kotlin.time.Instant

/** A clock driven by the test scheduler's virtual time, starting at [TEST_NOW]. */
class SchedulerClock(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant = TEST_NOW,
) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(start.toEpochMilliseconds() + scheduler.currentTime)
}
