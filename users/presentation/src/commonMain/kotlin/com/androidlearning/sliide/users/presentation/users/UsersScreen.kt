package com.androidlearning.sliide.users.presentation.users

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide.core.designsystem.component.ErrorBanner
import com.androidlearning.sliide.core.designsystem.component.FullScreenMessage
import com.androidlearning.sliide.core.designsystem.component.SliideFab
import com.androidlearning.sliide.core.designsystem.layout.DeviceConfiguration
import com.androidlearning.sliide.core.designsystem.layout.currentDeviceConfiguration
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.composables.DeleteUserDialog
import com.androidlearning.sliide.users.presentation.composables.UndoSnackbarVisuals
import com.androidlearning.sliide.users.presentation.composables.UsersFeed
import com.androidlearning.sliide.users.presentation.composables.UsersFeedPlaceholders
import com.androidlearning.sliide.users.presentation.composables.UsersSnackbarHost
import com.androidlearning.sliide.users.presentation.composables.UsersTopAppBar
import com.androidlearning.sliide.users.presentation.composables.previewUsers
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.delete_snackbar_message
import com.androidlearning.sliide.users.presentation.generated.resources.delete_snackbar_undo
import com.androidlearning.sliide.users.presentation.generated.resources.error_no_internet
import com.androidlearning.sliide.users.presentation.generated.resources.error_no_internet_body
import com.androidlearning.sliide.users.presentation.generated.resources.users_add_user
import com.androidlearning.sliide.users.presentation.generated.resources.users_dismiss
import com.androidlearning.sliide.users.presentation.generated.resources.users_empty_body
import com.androidlearning.sliide.users.presentation.generated.resources.users_empty_title
import com.androidlearning.sliide.users.presentation.generated.resources.users_loading
import com.androidlearning.sliide.users.presentation.generated.resources.users_retry
import com.androidlearning.sliide.users.presentation.generated.resources.users_title
import com.androidlearning.sliide.users.presentation.util.ObserveAsEvents
import com.androidlearning.sliide.users.presentation.util.UiText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The feed's ViewModel-wired entry point. Navigation (opening a profile, the add button) is the caller's job; the
 * caller also owns [viewModel] so it can forward selection changes and messages from the profile pane.
 */
@Composable
fun UsersRoot(
    viewModel: UsersViewModel,
    onUserClick: (Long) -> Unit,
    onAddUserClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            UsersEvent.ScrollToTop ->
                scope.launch {
                    listState.animateScrollToItem(0)
                    gridState.animateScrollToItem(0)
                }
            is UsersEvent.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(event.message.resolve()) }
        }
    }

    UsersScreen(
        state = state,
        onAction = viewModel::onAction,
        onUserClick = onUserClick,
        onAddUserClick = onAddUserClick,
        snackbarHostState = snackbarHostState,
        listState = listState,
        gridState = gridState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    state: UsersState,
    onAction: (UsersAction) -> Unit,
    onUserClick: (Long) -> Unit,
    onAddUserClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    listState: LazyListState,
    gridState: LazyGridState,
    modifier: Modifier = Modifier,
    currentTime: () -> Instant = { Clock.System.now() },
) {
    val columns = if (currentDeviceConfiguration() == DeviceConfiguration.MEDIUM) 2 else 1
    // Pinned: the bar never moves or resizes; it only tints while the lazy feed scrolls beneath it (spec: user-feed).
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fabExpanded by remember(listState, gridState) {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemIndex == 0 }
    }

    UndoSnackbarEffect(state.pendingDelete, snackbarHostState, onAction)

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            UsersTopAppBar(
                title = stringResource(Res.string.users_title),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            SliideFab(
                text = stringResource(Res.string.users_add_user),
                onClick = onAddUserClick,
                expanded = fabExpanded,
            )
        },
        snackbarHost = { UsersSnackbarHost(snackbarHostState, currentTime) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (val content = state.content) {
            FeedContent.Loading ->
                UsersFeedPlaceholders(
                    columns = columns,
                    loadingDescription = stringResource(Res.string.users_loading),
                    modifier = contentModifier,
                )

            FeedContent.Empty ->
                FullScreenMessage(
                    icon = Icons.Filled.Person,
                    title = stringResource(Res.string.users_empty_title),
                    body = stringResource(Res.string.users_empty_body),
                    actionLabel = stringResource(Res.string.users_retry),
                    onAction = { onAction(UsersAction.OnRetry) },
                    modifier = contentModifier,
                )

            is FeedContent.Error ->
                FullScreenMessage(
                    icon = Icons.Filled.Warning,
                    title = content.message.asString(),
                    body = stringResource(Res.string.error_no_internet_body),
                    actionLabel = stringResource(Res.string.users_retry),
                    onAction = { onAction(UsersAction.OnRetry) },
                    modifier = contentModifier,
                )

            FeedContent.Users ->
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onAction(UsersAction.OnRefresh) },
                    modifier = contentModifier,
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                            visible = state.banner != null,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            val banner = remember(state.banner) { state.banner }
                            if (banner != null) {
                                ErrorBanner(
                                    message = banner.asString(),
                                    dismissLabel = stringResource(Res.string.users_dismiss),
                                    onDismiss = { onAction(UsersAction.OnDismissBanner) },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = SliideSpacing.l, vertical = SliideSpacing.xs),
                                )
                            }
                        }
                        UsersFeed(
                            users = state.users,
                            columns = columns,
                            selectedUserId = state.selectedUserId,
                            listState = listState,
                            gridState = gridState,
                            onUserClick = onUserClick,
                            onDeleteRequest = { onAction(UsersAction.OnDeleteRequest(it)) },
                        )
                    }
                }
        }
    }

    state.confirmDelete?.let { user ->
        DeleteUserDialog(
            userName = user.name,
            onConfirm = { onAction(UsersAction.OnDeleteConfirm) },
            onDismiss = { onAction(UsersAction.OnDeleteCancel) },
        )
    }
}

/**
 * Shows the Undo snackbar while [pendingDelete] is open (design D4). The effect is keyed by user id: when the window
 * closes elsewhere (timer, newer delete) the effect is cancelled, which removes the snackbar without sending a result.
 * Undo sends [UsersAction.OnUndoDelete]; a swipe-away sends [UsersAction.OnCommitDelete], both for the captured id.
 */
@Composable
private fun UndoSnackbarEffect(
    pendingDelete: PendingDeleteUi?,
    snackbarHostState: SnackbarHostState,
    onAction: (UsersAction) -> Unit,
) {
    val undoLabel = stringResource(Res.string.delete_snackbar_undo)
    val message = pendingDelete?.let { stringResource(Res.string.delete_snackbar_message, it.userName) }
    LaunchedEffect(pendingDelete?.userId) {
        if (pendingDelete == null || message == null) return@LaunchedEffect
        snackbarHostState.currentSnackbarData?.dismiss()
        val result =
            snackbarHostState.showSnackbar(
                UndoSnackbarVisuals(
                    message = message,
                    actionLabel = undoLabel,
                    userId = pendingDelete.userId,
                    deadline = pendingDelete.deadline,
                    windowMillis = pendingDelete.windowMillis,
                ),
            )
        when (result) {
            SnackbarResult.ActionPerformed -> onAction(UsersAction.OnUndoDelete(pendingDelete.userId))
            SnackbarResult.Dismissed -> onAction(UsersAction.OnCommitDelete(pendingDelete.userId))
        }
    }
}

// region Previews

@Composable
private fun UsersScreenPreview(
    state: UsersState,
    darkTheme: Boolean,
) {
    SliideTheme(darkTheme = darkTheme) {
        Box {
            UsersScreen(
                state = state,
                onAction = {},
                onUserClick = {},
                onAddUserClick = {},
                snackbarHostState = remember { SnackbarHostState() },
                listState = rememberLazyListState(),
                gridState = rememberLazyGridState(),
                currentTime = { PREVIEW_NOW },
            )
        }
    }
}

private val PREVIEW_NOW = Instant.fromEpochMilliseconds(1_800_000_000_000)

private val loadingState = UsersState(content = FeedContent.Loading)
private val emptyState = UsersState(content = FeedContent.Empty)
private val errorState =
    UsersState(
        content =
            FeedContent.Error(
                UiText.Resource(Res.string.error_no_internet),
            ),
    )
private val populatedState = UsersState(users = previewUsers.filterNot { it.isPendingSync }, content = FeedContent.Users)
private val pendingSyncState = UsersState(users = previewUsers, content = FeedContent.Users)
private val bannerState =
    populatedState.copy(
        banner = UiText.Resource(Res.string.error_no_internet),
    )
private val dialogState = populatedState.copy(confirmDelete = previewUsers[0])
private val snackbarState =
    populatedState.copy(
        users = previewUsers.drop(1).filterNot { it.isPendingSync },
        pendingDelete =
            PendingDeleteUi(
                userId = previewUsers[0].id,
                userName = previewUsers[0].name,
                deadline = Instant.fromEpochMilliseconds(PREVIEW_NOW.toEpochMilliseconds() + 3_000),
                windowMillis = 5_000,
            ),
    )

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenLoadingLightCompactPreview() {
    UsersScreenPreview(loadingState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenLoadingLightExpandedPreview() {
    UsersScreenPreview(loadingState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenLoadingDarkCompactPreview() {
    UsersScreenPreview(loadingState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenLoadingDarkExpandedPreview() {
    UsersScreenPreview(loadingState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenEmptyLightCompactPreview() {
    UsersScreenPreview(emptyState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenEmptyLightExpandedPreview() {
    UsersScreenPreview(emptyState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenEmptyDarkCompactPreview() {
    UsersScreenPreview(emptyState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenEmptyDarkExpandedPreview() {
    UsersScreenPreview(emptyState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenErrorLightCompactPreview() {
    UsersScreenPreview(errorState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenErrorLightExpandedPreview() {
    UsersScreenPreview(errorState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenErrorDarkCompactPreview() {
    UsersScreenPreview(errorState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenErrorDarkExpandedPreview() {
    UsersScreenPreview(errorState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenBannerLightCompactPreview() {
    UsersScreenPreview(bannerState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenBannerLightExpandedPreview() {
    UsersScreenPreview(bannerState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenBannerDarkCompactPreview() {
    UsersScreenPreview(bannerState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenBannerDarkExpandedPreview() {
    UsersScreenPreview(bannerState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenPopulatedLightCompactPreview() {
    UsersScreenPreview(populatedState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenPopulatedLightExpandedPreview() {
    UsersScreenPreview(populatedState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenPopulatedDarkCompactPreview() {
    UsersScreenPreview(populatedState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenPopulatedDarkExpandedPreview() {
    UsersScreenPreview(populatedState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenPendingSyncLightCompactPreview() {
    UsersScreenPreview(pendingSyncState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenPendingSyncLightExpandedPreview() {
    UsersScreenPreview(pendingSyncState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenPendingSyncDarkCompactPreview() {
    UsersScreenPreview(pendingSyncState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenPendingSyncDarkExpandedPreview() {
    UsersScreenPreview(pendingSyncState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenDialogLightCompactPreview() {
    UsersScreenPreview(dialogState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenDialogLightExpandedPreview() {
    UsersScreenPreview(dialogState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenDialogDarkCompactPreview() {
    UsersScreenPreview(dialogState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenDialogDarkExpandedPreview() {
    UsersScreenPreview(dialogState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenSnackbarLightCompactPreview() {
    UsersScreenPreview(snackbarState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenSnackbarLightExpandedPreview() {
    UsersScreenPreview(snackbarState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 720)
@Composable
private fun UsersScreenSnackbarDarkCompactPreview() {
    UsersScreenPreview(snackbarState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 720)
@Composable
private fun UsersScreenSnackbarDarkExpandedPreview() {
    UsersScreenPreview(snackbarState, darkTheme = true)
}

// endregion
