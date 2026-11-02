package com.androidlearning.sliide.users.domain.model

/** Outcome of an accepted user write. */
enum class WriteStatus {
    /** The API confirmed the write. */
    SYNCED,

    /** The API could not be reached or failed temporarily; the write is queued for a later sync. */
    QUEUED,

    /** The API refused the app's credentials (401/403); the write is queued and retried on a later sync. */
    QUEUED_AUTH_FAILED,
}

/** True when the write is waiting in the outbox, whatever the reason. */
val WriteStatus.isQueued: Boolean
    get() = this != WriteStatus.SYNCED
