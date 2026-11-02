package com.androidlearning.sliide.users.presentation.users

import com.androidlearning.sliide.users.presentation.util.UiText

sealed interface UsersAction {
    /** Pull to refresh. */
    data object OnRefresh : UsersAction

    /** Retry on the full-screen error. */
    data object OnRetry : UsersAction

    data object OnDismissBanner : UsersAction

    /** Long-press or the "Delete" accessibility action on a card: asks for confirmation. */
    data class OnDeleteRequest(
        val userId: Long,
    ) : UsersAction

    data object OnDeleteConfirm : UsersAction

    data object OnDeleteCancel : UsersAction

    /** Undo pressed on the snackbar of [userId]'s window. */
    data class OnUndoDelete(
        val userId: Long,
    ) : UsersAction

    /** The snackbar of [userId]'s window was swiped away. */
    data class OnCommitDelete(
        val userId: Long,
    ) : UsersAction

    /**
     * The profile the user chose to open: a user id, `ProfileKey.CREATE`, or `null` when none is chosen. Drives the
     * card highlight and, on two-pane layouts, the default first-user selection.
     */
    data class OnSelectionChange(
        val key: Long?,
    ) : UsersAction

    /** Whether the feed and the detail pane are shown side by side (expanded width). */
    data class OnPaneLayoutChange(
        val isTwoPane: Boolean,
    ) : UsersAction

    /** A user was created from the profile: scroll to the top where it appears. */
    data object OnUserCreated : UsersAction

    /** A message from another pane (e.g. the profile) to show in this screen's snackbar host. */
    data class OnShowMessage(
        val message: UiText,
    ) : UsersAction
}
