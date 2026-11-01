package com.androidlearning.sliide.core.database

/** The write a [PendingSyncEntity] stands for. Room stores it as TEXT by name. */
enum class PendingOperation {
    CREATE,
    UPDATE,
    DELETE,
}
