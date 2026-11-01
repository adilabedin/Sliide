package com.androidlearning.sliide.users.domain.util

sealed interface DataError : Error {
    enum class Local : DataError {
        DISK_FULL,
        NOT_FOUND,
        UNKNOWN,
    }

    /** Failures of a GoRest API call, already classified by the remote data source. */
    enum class Remote : DataError {
        NO_INTERNET,
        REQUEST_TIMEOUT,
        SERVER_ERROR,

        /** HTTP 429: rate limited by GoRest. The Ktor remote data source maps 429 to this value; retried later. */
        TOO_MANY_REQUESTS,
        UNAUTHORIZED,
        NOT_FOUND,
        VALIDATION,
        SERIALIZATION,
        UNKNOWN,
    }
}

/**
 * True when retrying the same request later may succeed (no connectivity, timeout, 5xx, 429 rate limit).
 * Only retryable failures are queued for offline sync; every other failure is surfaced to the caller.
 */
val DataError.Remote.isRetryable: Boolean
    get() =
        when (this) {
            DataError.Remote.NO_INTERNET,
            DataError.Remote.REQUEST_TIMEOUT,
            DataError.Remote.SERVER_ERROR,
            DataError.Remote.TOO_MANY_REQUESTS,
            -> true

            DataError.Remote.UNAUTHORIZED,
            DataError.Remote.NOT_FOUND,
            DataError.Remote.VALIDATION,
            DataError.Remote.SERIALIZATION,
            DataError.Remote.UNKNOWN,
            -> false
        }
