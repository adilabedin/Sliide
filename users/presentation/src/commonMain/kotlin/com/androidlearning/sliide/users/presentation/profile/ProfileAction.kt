package com.androidlearning.sliide.users.presentation.profile

import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.UserStatus

sealed interface ProfileAction {
    /** The pane now shows [key]: a user id, or [ProfileKey.CREATE]. */
    data class OnOpen(
        val key: Long,
    ) : ProfileAction

    /** The pane was closed (back, close icon, or another navigation): forget the target and any draft. */
    data object OnDismiss : ProfileAction

    data object OnEditClick : ProfileAction

    /** Close icon or back while editing: discard the draft and return to View mode. */
    data object OnCancelEdit : ProfileAction

    data class OnNameChange(
        val name: String,
    ) : ProfileAction

    data class OnEmailChange(
        val email: String,
    ) : ProfileAction

    data class OnGenderChange(
        val gender: Gender,
    ) : ProfileAction

    data class OnStatusChange(
        val status: UserStatus,
    ) : ProfileAction

    data object OnSaveClick : ProfileAction

    data object OnDeleteClick : ProfileAction

    data object OnDeleteConfirm : ProfileAction

    data object OnDeleteCancel : ProfileAction
}
