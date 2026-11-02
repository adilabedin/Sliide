package com.androidlearning.sliide.users.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.androidlearning.sliide.users.domain.UserSyncScheduler
import java.util.concurrent.TimeUnit

/**
 * Schedules [UserSyncWorker] (spec: background-sync).
 * - [schedulePeriodicSync]: unique periodic work with `KEEP`, called on every launch, so an existing schedule survives.
 *   `REPLACE` would restart the period and could cancel a run in progress.
 * - [requestSync]: unique one-time work per user (`sync_user_<id>`) with `REPLACE`, so a write merged while that
 *   user's job is running still gets a run. Cancelling a running job only stops its wait: the send itself runs in the
 *   application scope and completes.
 * - Both are `CONNECTED` (the OS doesn't start the job while offline) and have no backoff: the worker never returns
 *   `retry()`.
 */
class WorkManagerUserSyncScheduler(
    private val context: Context,
) : UserSyncScheduler {
    override fun schedulePeriodicSync() {
        val request =
            PeriodicWorkRequestBuilder<UserSyncWorker>(SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(onlineOnly)
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun requestSync(userId: Long) {
        val request =
            OneTimeWorkRequestBuilder<UserSyncWorker>()
                .setConstraints(onlineOnly)
                .setInputData(workDataOf(UserSyncWorker.KEY_USER_ID to userId))
                .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(userWorkName(userId), ExistingWorkPolicy.REPLACE, request)
    }

    private val onlineOnly: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    companion object {
        const val UNIQUE_WORK_NAME = "sync_pending_users"

        fun userWorkName(userId: Long): String = "sync_user_$userId"

        const val SYNC_INTERVAL_MINUTES = 30L
    }
}
