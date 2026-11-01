package com.androidlearning.sliide.users.data.sync

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_get_global_queue
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * One-shot connectivity read with `NWPathMonitor`. Needed on iOS only: unlike WorkManager, a `BGAppRefreshTaskRequest`
 * can't require a network (design D6). A path that doesn't report within 2 seconds counts as offline.
 */
@OptIn(ExperimentalAtomicApi::class)
class NwPathConnectivityCheck : ConnectivityCheck {
    override suspend fun isOnline(): Boolean =
        withTimeoutOrNull(TIMEOUT) {
            suspendCancellableCoroutine { continuation ->
                val monitor = nw_path_monitor_create()
                val resumed = AtomicBoolean(false)
                nw_path_monitor_set_update_handler(monitor) { path ->
                    nw_path_monitor_cancel(monitor)
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(nw_path_get_status(path) == nw_path_status_satisfied)
                    }
                }
                nw_path_monitor_set_queue(monitor, dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u))
                continuation.invokeOnCancellation { nw_path_monitor_cancel(monitor) }
                nw_path_monitor_start(monitor)
            }
        } ?: false

    private companion object {
        val TIMEOUT = 2.seconds
    }
}
