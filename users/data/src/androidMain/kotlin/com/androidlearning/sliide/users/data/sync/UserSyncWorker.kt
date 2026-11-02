package com.androidlearning.sliide.users.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

/** WorkManager's result type, aliased so it is never confused with the domain `Result`. */
typealias WorkerResult = ListenableWorker.Result

/**
 * The background replay on Android (spec: background-sync). With a [KEY_USER_ID] input it replays only that user's
 * entry (a per-user sync); without one it replays the whole queue (the periodic job). Thin by design:
 * [BackgroundUserSync] does the work. Always reports success, even when the replay stopped: background runs never retry or back off (design D4); the
 * queue waits for the next periodic run or the next foreground refresh.
 */
class UserSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val backgroundUserSync: BackgroundUserSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): WorkerResult {
        val userId = inputData.getLong(KEY_USER_ID, NO_USER).takeIf { it != NO_USER }
        backgroundUserSync.run(userId)
        return WorkerResult.success()
    }

    companion object {
        const val KEY_USER_ID = "user_id"

        // Real and temporary user ids are never 0: server ids are positive and temporary ids negative.
        private const val NO_USER = 0L
    }
}
