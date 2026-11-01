package com.androidlearning.sliide.users.data.fake

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A [Clock] whose time only moves when a test sets or [advance]s it. */
class TestClock(
    var now: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000),
) : Clock {
    override fun now(): Instant = now

    fun advance(duration: Duration) {
        now += duration
    }
}
