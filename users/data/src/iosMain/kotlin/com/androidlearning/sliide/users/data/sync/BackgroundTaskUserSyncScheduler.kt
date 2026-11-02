package com.androidlearning.sliide.users.data.sync

import com.androidlearning.sliide.users.domain.UserSyncScheduler
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Schedules the periodic replay with a `BGAppRefreshTask` (design D5). iOS decides when it actually runs; the request
 * only says "not before 30 minutes from now". The task id is listed in `iosApp/Info.plist`
 * (`BGTaskSchedulerPermittedIdentifiers`, with `UIBackgroundModes` → `fetch`).
 *
 * [schedulePeriodicSync] must first run before the app finishes launching (it does: `iOSApp.init()` → `initKoinIos()`
 * → `initKoin()`), because that is when iOS accepts launch-handler registrations.
 */
@OptIn(ExperimentalAtomicApi::class)
class BackgroundTaskUserSyncScheduler(
    backgroundUserSync: () -> BackgroundUserSync,
    connectivityCheck: ConnectivityCheck,
    applicationScope: CoroutineScope,
) : UserSyncScheduler {
    // Built on the first background task, not at construction: the repository depends on this scheduler, and
    // BackgroundUserSync depends on the repository, so resolving it eagerly would be a dependency cycle.
    private val runner by lazy {
        IosBackgroundSyncRunner(backgroundUserSync(), connectivityCheck, applicationScope, ::submitRequest)
    }

    override fun schedulePeriodicSync() {
        registerOnce()
        // Submitting again would replace the pending request and push its start back: keep an existing one.
        BGTaskScheduler.sharedScheduler.getPendingTaskRequestsWithCompletionHandler { requests ->
            val pending = requests.orEmpty().any { (it as? BGTaskRequest)?.identifier == TASK_IDENTIFIER }
            if (!pending) submitRequest()
        }
    }

    /**
     * iOS has no per-item "run when online" task and allows one pending request per identifier, so this only makes sure
     * a refresh request is pending; its run replays the whole queue, including [userId]'s entry (design D4).
     */
    override fun requestSync(userId: Long) = schedulePeriodicSync()

    private fun registerOnce() {
        // Registering the same identifier twice throws, so guard it for the whole process.
        if (!registered.compareAndSet(false, true)) return
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(TASK_IDENTIFIER, usingQueue = null) { task ->
            (task as? BGTask)?.let { runner.run(BgTaskHandle(it)) }
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun submitRequest() {
        val request =
            BGAppRefreshTaskRequest(identifier = TASK_IDENTIFIER).apply {
                earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(SYNC_INTERVAL_SECONDS)
            }
        memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            if (!BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error.ptr)) {
                // Expected on the Simulator and when Background App Refresh is off; never fatal.
                println("Background sync not scheduled: ${error.value?.localizedDescription}")
            }
        }
    }

    private class BgTaskHandle(
        private val task: BGTask,
    ) : SyncTaskHandle {
        override fun setExpirationHandler(handler: () -> Unit) {
            task.expirationHandler = handler
        }

        override fun complete(success: Boolean) {
            task.setTaskCompletedWithSuccess(success)
        }
    }

    companion object {
        const val TASK_IDENTIFIER = "com.androidlearning.sliide.sync-pending-users"
        private const val SYNC_INTERVAL_SECONDS = 30 * 60.0
        private val registered = AtomicBoolean(false)
    }
}
