package com.androidlearning.sliide.users.presentation.profile

import com.androidlearning.sliide.users.presentation.util.UiText

sealed interface ProfileEvent {
    /** Leave the profile; [message] (if any) is shown on the feed. */
    data class Close(
        val message: UiText? = null,
    ) : ProfileEvent

    /** A new user was saved: leave the profile and scroll the feed to the top. [message] is set if it was queued. */
    data class Created(
        val message: UiText? = null,
    ) : ProfileEvent

    /** Shown on the profile itself (e.g. saved offline, or a rejected save). */
    data class ShowMessage(
        val message: UiText,
    ) : ProfileEvent
}
