package com.androidlearning.sliide.users.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

/** WorkManager's result type, aliased so it is never confused with the domain `Result`. */
typealias WorkerResult = ListenableWorker.Result

/**
 * The periodic background replay on Android (spec: background-sync). Thin by design: [BackgroundUserSync] does the
 * work. Always reports success, even when the replay stopped: background runs never retry or back off (design D4); the
 * queue waits for the next periodic run or the next foreground refresh.
 */
class UserSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val backgroundUserSync: BackgroundUserSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): WorkerResult {
        backgroundUserSync.run()
        return WorkerResult.success()
    }
}
