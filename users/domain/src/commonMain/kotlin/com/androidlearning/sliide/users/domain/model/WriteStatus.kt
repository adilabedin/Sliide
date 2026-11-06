package com.androidlearning.sliide.users.domain.model

/** Outcome of an accepted user write. */
enum class WriteStatus {
    /** The API confirmed the write. */
    SYNCED,

    /**
     * The write failed in a way that may later succeed (offline, timeout, 5xx, 429, 401/403) and is queued for a
     * later sync.
     */
    QUEUED,
}
