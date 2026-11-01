package com.androidlearning.sliide.users.data.fake

import com.androidlearning.sliide.core.database.DatabaseTransactionRunner

/**
 * Runs the block directly. If it throws, every fake table is restored to its state before the block, mimicking a
 * Room rollback. [transactionCount] counts completed and failed transactions.
 */
class FakeDatabaseTransactionRunner(
    private val userDao: FakeUserDao,
    private val pendingSyncDao: FakePendingSyncDao,
) : DatabaseTransactionRunner {
    var transactionCount = 0
        private set

    override suspend fun <R> inTransaction(block: suspend () -> R): R {
        transactionCount++
        val users = userDao.table.value
        val pending = pendingSyncDao.table.value
        return try {
            block()
        } catch (e: Throwable) {
            userDao.table.value = users
            pendingSyncDao.table.value = pending
            throw e
        }
    }
}
