package com.androidlearning.sliide.users.presentation.users

import androidx.compose.runtime.Immutable
import com.androidlearning.sliide.users.presentation.util.UiText
import kotlin.time.Instant

/** What the feed area shows (spec: user-feed). */
@Immutable
sealed interface FeedContent {
    /** The cache has not emitted yet, or it is empty while the first load runs: shimmer placeholders. */
    data object Loading : FeedContent

    data object Users : FeedContent

    data object Empty : FeedContent

    /** The cache is empty and the last refresh failed. */
    data class Error(
        val message: UiText,
    ) : FeedContent
}

/** The open Undo window, as the snackbar needs it. */
@Immutable
data class PendingDeleteUi(
    val userId: Long,
    val userName: String,
    val deadline: Instant,
    val windowMillis: Long,
)

@Immutable
data class UsersState(
    val users: List<UserUi> = emptyList(),
    val content: FeedContent = FeedContent.Loading,
    val isRefreshing: Boolean = false,
    val banner: UiText? = null,
    val pendingDelete: PendingDeleteUi? = null,
    val confirmDelete: UserUi? = null,
    val selectedUserId: Long? = null,
)
