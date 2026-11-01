package com.androidlearning.sliide.users.presentation.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RelativeTimeTest {
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private fun ago(duration: Duration) = relativeAddedTime(createdAt = now - duration, now = now)

    @Test
    fun underAMinuteIsJustNow() {
        assertThat(ago(0.seconds)).isEqualTo(RelativeTime.JustNow)
        assertThat(ago(59.seconds)).isEqualTo(RelativeTime.JustNow)
    }

    @Test
    fun futureTimestampIsJustNow() {
        assertThat(ago((-5).minutes)).isEqualTo(RelativeTime.JustNow)
    }

    @Test
    fun minuteBucketEdges() {
        assertThat(ago(60.seconds)).isEqualTo(RelativeTime.Minutes(1))
        assertThat(ago(5.minutes)).isEqualTo(RelativeTime.Minutes(5))
        assertThat(ago(59.minutes + 59.seconds)).isEqualTo(RelativeTime.Minutes(59))
    }

    @Test
    fun hourBucketEdges() {
        assertThat(ago(60.minutes)).isEqualTo(RelativeTime.Hours(1))
        assertThat(ago(3.hours)).isEqualTo(RelativeTime.Hours(3))
        assertThat(ago(23.hours + 59.minutes)).isEqualTo(RelativeTime.Hours(23))
    }

    @Test
    fun dayBucketEdges() {
        assertThat(ago(24.hours)).isEqualTo(RelativeTime.Days(1))
        assertThat(ago(2.days)).isEqualTo(RelativeTime.Days(2))
        assertThat(ago(29.days + 23.hours)).isEqualTo(RelativeTime.Days(29))
    }

    @Test
    fun monthBucketEdges() {
        assertThat(ago(30.days)).isEqualTo(RelativeTime.Months(1))
        assertThat(ago(330.days)).isEqualTo(RelativeTime.Months(11))
        assertThat(ago(359.days)).isEqualTo(RelativeTime.Months(11))
    }

    @Test
    fun yearBucketEdges() {
        assertThat(ago(360.days)).isEqualTo(RelativeTime.Years(1))
        assertThat(ago(365.days)).isEqualTo(RelativeTime.Years(1))
        assertThat(ago(730.days)).isEqualTo(RelativeTime.Years(2))
    }
}
