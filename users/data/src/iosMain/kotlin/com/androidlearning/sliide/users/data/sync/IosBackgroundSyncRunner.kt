package com.androidlearning.sliide.users.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** The parts of a `BGTask` the runner needs, so tests can drive it without the OS (design D6). */
interface SyncTaskHandle {
    fun setExpirationHandler(handler: () -> Unit)

    fun complete(success: Boolean)
}

/** Whether the device currently has a usable network path. */
fun interface ConnectivityCheck {
    suspend fun isOnline(): Boolean
}

/**
 * Runs one iOS background refresh task (design D6, spec: background-sync):
 * 1. Re-submits the next request first, so the schedule continues even if this run expires or the process dies.
 * 2. Offline: completes straight away without touching the queue.
 * 3. Online: runs [BackgroundUserSync] in [scope] and completes. Every outcome is a success (no retry, design D4).
 *
 * Expiration cancels the job and completes with `false`. Each entry the replay already sent was cleared in its own
 * transaction, so the next run resumes with what is left. [SyncTaskHandle.complete] is called exactly once.
 */
@OptIn(ExperimentalAtomicApi::class)
class IosBackgroundSyncRunner(
    private val backgroundUserSync: BackgroundUserSync,
    private val connectivityCheck: ConnectivityCheck,
    private val scope: CoroutineScope,
    private val submitNextRequest: () -> Unit,
) {
    fun run(task: SyncTaskHandle) {
        submitNextRequest()
        val completed = AtomicBoolean(false)
        val completeOnce = { success: Boolean -> if (completed.compareAndSet(false, true)) task.complete(success) }
        // The run's own Job, so the expiration handler can cancel it whether or not the work has started yet.
        val runJob = Job(scope.coroutineContext[Job])
        task.setExpirationHandler {
            runJob.cancel()
            completeOnce(false)
        }
        scope.launch(runJob) {
            if (connectivityCheck.isOnline()) backgroundUserSync.run()
            completeOnce(true)
        }
        // Completes once the launched work finishes, so the Job never outlives the run.
        runJob.complete()
    }
}
