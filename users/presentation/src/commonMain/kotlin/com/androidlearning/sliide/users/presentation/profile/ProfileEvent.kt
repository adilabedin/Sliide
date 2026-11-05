package com.androidlearning.sliide.users.presentation.profile

import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.util.UiText

sealed interface ProfileEvent {
    /** Leave the profile; [message] (if any) is shown on the feed. */
    data class Close(
        val message: UiText? = null,
    ) : ProfileEvent

    /**
     * A new user was saved: leave the add-user screen and scroll the feed to the top. [status] says whether it reached
     * the server or was queued; it is a primitive so it can travel back to the feed through navigation.
     */
    data class Created(
        val status: WriteStatus,
    ) : ProfileEvent

    /** Shown on the profile itself (e.g. saved offline, or a rejected save). */
    data class ShowMessage(
        val message: UiText,
    ) : ProfileEvent
}
