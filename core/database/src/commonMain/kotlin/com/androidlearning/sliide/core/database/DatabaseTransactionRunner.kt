package com.androidlearning.sliide.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/** Runs a block of DAO calls atomically: either every write in [inTransaction]'s block is kept, or none is. */
interface DatabaseTransactionRunner {
    suspend fun <R> inTransaction(block: suspend () -> R): R
}

/** [DatabaseTransactionRunner] backed by a Room immediate (write-locking) transaction. */
class RoomDatabaseTransactionRunner(
    private val database: SliideDatabase,
) : DatabaseTransactionRunner {
    override suspend fun <R> inTransaction(block: suspend () -> R): R =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { block() }
        }
}
