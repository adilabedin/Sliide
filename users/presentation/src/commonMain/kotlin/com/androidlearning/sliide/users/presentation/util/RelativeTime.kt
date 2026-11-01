package com.androidlearning.sliide.users.presentation.util

import kotlin.time.Duration
import kotlin.time.Instant

/** How long ago something happened, bucketed for display ("Added 5 minutes ago"). Pure: no strings, no clock. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime

    data class Minutes(
        val count: Int,
    ) : RelativeTime

    data class Hours(
        val count: Int,
    ) : RelativeTime

    data class Days(
        val count: Int,
    ) : RelativeTime

    data class Months(
        val count: Int,
    ) : RelativeTime

    data class Years(
        val count: Int,
    ) : RelativeTime
}

private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_YEAR = 365
private const val MONTHS_PER_YEAR = 12

/**
 * Buckets the time between [createdAt] and [now]: under a minute (or in the future) is [RelativeTime.JustNow], then
 * whole minutes, hours, days (under 30), months of 30 days (under 12) and years of 365 days (at least 1).
 */
fun relativeAddedTime(
    createdAt: Instant,
    now: Instant,
): RelativeTime {
    val elapsed: Duration = now - createdAt
    val days = elapsed.inWholeDays
    return when {
        elapsed.inWholeMinutes < 1 -> RelativeTime.JustNow
        elapsed.inWholeHours < 1 -> RelativeTime.Minutes(elapsed.inWholeMinutes.toInt())
        days < 1 -> RelativeTime.Hours(elapsed.inWholeHours.toInt())
        days < DAYS_PER_MONTH -> RelativeTime.Days(days.toInt())
        days / DAYS_PER_MONTH < MONTHS_PER_YEAR -> RelativeTime.Months((days / DAYS_PER_MONTH).toInt())
        else -> RelativeTime.Years((days / DAYS_PER_YEAR).toInt().coerceAtLeast(1))
    }
}
