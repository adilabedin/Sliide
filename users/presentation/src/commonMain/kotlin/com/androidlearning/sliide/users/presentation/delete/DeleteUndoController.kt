package com.androidlearning.sliide.users.presentation.delete

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.WriteStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** A user removed locally whose Undo window ends at [deadline]. */
data class PendingDelete(
    val user: User,
    val deadline: Instant,
)

/** One-off outcomes of a delete the screen should tell the user about. */
sealed interface DeleteMessage {
    /** The delete was rejected and the user restored; [error] says why (a [DataError.Validation] means GoRest refused it). */
    data class FinalizeFailed(
        val userName: String,
        val error: DataError,
    ) : DeleteMessage

    /** The server could not authenticate the app; the user stays removed and the delete is queued for a later sync. */
    data class QueuedAuthFailed(
        val userName: String,
    ) : DeleteMessage
}

/**
 * Owns the Undo window of a delete (spec: user-deletion). Every [request] that removes a user ends in exactly one
 * [UserRepository.restoreUser] (via [undo]) or [UserRepository.finalizeDelete] (window expiry, [commitNow], or a newer
 * [request]), as the repository contract requires.
 *
 * It is an app-wide singleton running in its own long-lived [scope] (design D3), so the countdown keeps running when the
 * users screen leaves composition, the app is backgrounded, or the ViewModels are cleared. All transitions are
 * serialised by [mutex]; every window is resolved through [takeWindow], so a late timer, a duplicate Undo or a stale
 * swipe for an older user finds no matching window and does nothing.
 */
class DeleteUndoController(
    private val repository: UserRepository,
    private val scope: CoroutineScope,
    private val clock: Clock,
    val window: Duration = DEFAULT_WINDOW,
) {
    private val mutex = Mutex()
    private var timer: Job? = null

    private val _pending = MutableStateFlow<PendingDelete?>(null)
    val pending: StateFlow<PendingDelete?> = _pending.asStateFlow()

    private val _messages = Channel<DeleteMessage>(Channel.BUFFERED)
    val messages: Flow<DeleteMessage> = _messages.receiveAsFlow()

    /** Removes the user locally and opens a new Undo window; an open window for another user is finalised first. */
    fun request(userId: Long) {
        scope.launch {
            mutex.withLock {
                _pending.value?.let { previous -> finalize(takeWindow(previous.user.id) ?: previous) }
                when (val removed = repository.removeUserLocally(userId)) {
                    is Result.Failure -> Unit // Already gone (e.g. removed by a refresh): nothing to undo or finalise.
                    is Result.Success -> {
                        _pending.value = PendingDelete(user = removed.data, deadline = clock.now() + window)
                        timer =
                            scope.launch {
                                delay(window)
                                commitNow(userId)
                            }
                    }
                }
            }
        }
    }

    /** Undo: restores the user if [userId] is the open window. */
    fun undo(userId: Long) {
        scope.launch {
            mutex.withLock {
                val window = takeWindow(userId) ?: return@withLock
                repository.restoreUser(window.user)
            }
        }
    }

    /** Ends the window now and finalises the delete (snackbar swiped away, or the timer fired). */
    fun commitNow(userId: Long) {
        scope.launch {
            mutex.withLock {
                val window = takeWindow(userId) ?: return@withLock
                finalize(window)
            }
        }
    }

    /** Closes the open window if it belongs to [userId]. Must be called under [mutex]. */
    private fun takeWindow(userId: Long): PendingDelete? {
        val open = _pending.value?.takeIf { it.user.id == userId } ?: return null
        _pending.value = null
        timer?.cancel()
        timer = null
        return open
    }

    /** Sends the finalise without holding [mutex], so a slow network call never delays the next delete or Undo. */
    private fun finalize(window: PendingDelete) {
        scope.launch {
            val message =
                when (val result = repository.finalizeDelete(window.user)) {
                    is Result.Failure -> DeleteMessage.FinalizeFailed(userName = window.user.name, error = result.error)
                    is Result.Success ->
                        when (result.data) {
                            // A delete queued because the device is offline needs no message (spec: user-deletion).
                            WriteStatus.SYNCED, WriteStatus.QUEUED -> null
                            WriteStatus.QUEUED_AUTH_FAILED -> DeleteMessage.QueuedAuthFailed(userName = window.user.name)
                        }
                }
            message?.let { _messages.send(it) }
        }
    }

    companion object {
        val DEFAULT_WINDOW: Duration = 5.seconds
    }
}
