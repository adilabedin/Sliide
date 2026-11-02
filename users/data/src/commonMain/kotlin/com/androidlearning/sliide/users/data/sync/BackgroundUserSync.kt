package com.androidlearning.sliide.users.data.sync

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.SyncReport
import kotlin.coroutines.cancellation.CancellationException

/** What one background run did. Informational only: every outcome counts as a finished run (design D4, no retry). */
sealed interface BackgroundSyncOutcome {
    data class Synced(
        val report: SyncReport,
    ) : BackgroundSyncOutcome

    data class Stopped(
        val error: DataError.Remote,
    ) : BackgroundSyncOutcome
}

/**
 * One background run, shared by the Android worker and the iOS refresh task (design D2). It replays pending writes
 * and nothing else: it never calls [UserRepository.refresh], so no row vanishes from a list the user is looking at.
 */
class BackgroundUserSync(
    private val repository: UserRepository,
) {
    /** Replays only [userId]'s entry when given (a per-user sync), otherwise the whole queue. */
    suspend fun run(userId: Long? = null): BackgroundSyncOutcome =
        try {
            val result = if (userId == null) repository.syncPending() else repository.syncPending(userId)
            when (result) {
                is Result.Success -> BackgroundSyncOutcome.Synced(result.data)
                is Result.Failure -> BackgroundSyncOutcome.Stopped(result.error)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A bug must never crash a background process; the queue is left for the next run.
            BackgroundSyncOutcome.Stopped(DataError.Remote.UNKNOWN)
        }
}
