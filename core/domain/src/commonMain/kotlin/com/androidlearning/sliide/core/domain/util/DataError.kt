package com.androidlearning.sliide.core.domain.util

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

        /** HTTP 429: rate limited by GoRest. The Ktor remote data source maps 429 to this value. */
        TOO_MANY_REQUESTS,

        /** HTTP 401 or 403: the server could not authenticate the app. */
        UNAUTHORIZED,
        NOT_FOUND,
        SERIALIZATION,
        UNKNOWN,
    }

    /**
     * HTTP 422: the server rejected the request body. [fields] are what GoRest sent, unchanged and in order; empty when
     * the body named no field or could not be read. On a delete, GoRest uses 422 to refuse it.
     */
    data class Validation(
        val fields: List<FieldError>,
    ) : DataError
}

/** One field the server rejected, e.g. `email` / `has already been taken`. The message is shown as sent. */
data class FieldError(
    val field: String,
    val message: String,
)
