package com.androidlearning.sliide.users.data.sync

import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.domain.util.DataError

/** What happens to a write whose API call failed (design D4). The repository decides how to apply it per path. */
enum class OutboxDecision {
    /** May succeed later: the write stays queued. */
    KEEP,

    /** Will never succeed as sent: the write leaves the queue and its local effect is rolled back. */
    REJECT,

    /** The user is already gone on the server: a delete is done, an update removes the user locally. */
    GONE,
}

/**
 * The single decision table for failed user writes, shared by direct writes and replay so the two paths can never
 * disagree (design D4, spec: user-write-errors). It only decides; the repository applies the decision. Every `when` is
 * exhaustive with no `else`, so a new error type or enum value fails to compile until it is classified here.
 */
object OutboxPolicy {
    fun decide(
        operation: PendingOperation,
        error: DataError,
    ): OutboxDecision =
        when (error) {
            is DataError.Remote -> decideRemote(operation, error)
            // 422: a rejected body, or on a delete GoRest refusing it. Sending it again cannot succeed.
            is DataError.Validation -> OutboxDecision.REJECT
            is DataError.Local -> OutboxDecision.REJECT
        }

    private fun decideRemote(
        operation: PendingOperation,
        error: DataError.Remote,
    ): OutboxDecision =
        when (error) {
            DataError.Remote.NO_INTERNET,
            DataError.Remote.REQUEST_TIMEOUT,
            DataError.Remote.SERVER_ERROR,
            DataError.Remote.TOO_MANY_REQUESTS,
            DataError.Remote.UNAUTHORIZED,
            -> OutboxDecision.KEEP

            DataError.Remote.NOT_FOUND ->
                when (operation) {
                    PendingOperation.CREATE -> OutboxDecision.REJECT
                    PendingOperation.UPDATE, PendingOperation.DELETE -> OutboxDecision.GONE
                }

            DataError.Remote.SERIALIZATION,
            DataError.Remote.UNKNOWN,
            -> OutboxDecision.REJECT
        }
}
