package com.androidlearning.sliide.users.domain.model

/** Outcome of an accepted user write. */
enum class WriteStatus {
    /** The API confirmed the write. */
    SYNCED,

    /** The API was unreachable for a retryable reason; the write is queued for a later sync. */
    QUEUED,
}
