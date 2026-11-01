package com.androidlearning.sliide.users.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.androidlearning.sliide.users.domain.UserSyncScheduler
import java.util.concurrent.TimeUnit

/**
 * Schedules [UserSyncWorker] as unique periodic work (design D3).
 * - `KEEP`: called on every launch, so an existing schedule must survive. `REPLACE` would restart the period and could
 *   cancel a run in progress.
 * - `CONNECTED`: the OS doesn't start the job while offline.
 * - No backoff: the worker never returns `retry()`.
 */
class WorkManagerUserSyncScheduler(
    private val context: Context,
) : UserSyncScheduler {
    override fun schedulePeriodicSync() {
        val request =
            PeriodicWorkRequestBuilder<UserSyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "sync_pending_users"
        const val SYNC_INTERVAL_MINUTES = 30L
    }
}
