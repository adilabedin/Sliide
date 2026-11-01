package com.androidlearning.sliide.core.database

/**
 * Every database name in one place: the file, the tables and the columns referenced by queries. Entities and DAOs use
 * these instead of string literals, so a rename can't leave a query pointing at a column that no longer exists.
 */
object DatabaseConstants {
    const val FILE_NAME = "sliide.db"

    object Users {
        const val TABLE = "users"
        const val ID = "id"
        const val CREATED_AT = "createdAtEpochMillis"
    }

    object PendingSync {
        const val TABLE = "pending_sync"

        /** Prefix of the embedded `UserEntity` snapshot's columns. */
        const val USER_PREFIX = "user_"

        /** The snapshot's id column, which is also the primary key. */
        const val USER_ID = "$USER_PREFIX${Users.ID}"
        const val OPERATION = "operation"
        const val QUEUED_AT = "queued_at_epoch_millis"
    }
}
