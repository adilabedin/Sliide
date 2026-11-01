package com.androidlearning.sliide.users.presentation.users

import com.androidlearning.sliide.users.presentation.util.UiText

sealed interface UsersEvent {
    data object ScrollToTop : UsersEvent

    data class ShowMessage(
        val message: UiText,
    ) : UsersEvent
}
