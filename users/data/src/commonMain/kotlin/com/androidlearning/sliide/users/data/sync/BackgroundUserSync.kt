package com.androidlearning.sliide.users.data.sync

import com.androidlearning.sliide.users.domain.UserRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * One background run, shared by the Android worker and the iOS refresh task (design D2). It replays pending writes
 * and nothing else: it never calls [UserRepository.refresh], so no row vanishes from a list the user is looking at.
 * Every run counts as finished, whatever happened (design D4, no retry): a kept write waits for the next run.
 */
class BackgroundUserSync(
    private val repository: UserRepository,
) {
    /** Replays only [userId]'s entry when given (a per-user sync), otherwise the whole queue. Never throws. */
    suspend fun run(userId: Long? = null) {
        try {
            if (userId == null) {
                repository.syncPending()
            } else {
                repository.syncPending(userId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A bug must never crash a background process; the queue is left for the next run.
        }
    }
}
