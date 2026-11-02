package com.androidlearning.sliide.users.presentation.listdetail

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide.users.presentation.composables.SelectUserPlaceholder
import com.androidlearning.sliide.users.presentation.profile.ProfileAction
import com.androidlearning.sliide.users.presentation.profile.ProfileKey
import com.androidlearning.sliide.users.presentation.profile.ProfileMode
import com.androidlearning.sliide.users.presentation.profile.ProfileRoot
import com.androidlearning.sliide.users.presentation.profile.ProfileViewModel
import com.androidlearning.sliide.users.presentation.users.UsersAction
import com.androidlearning.sliide.users.presentation.users.UsersRoot
import com.androidlearning.sliide.users.presentation.users.UsersViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * The users feature's entry point (design D1): the feed as the list pane and the reusable profile as the detail pane
 * of Material 3's adaptive list-detail scaffold. Compact and medium widths show one pane (the profile full screen, back
 * returns to the feed); expanded widths show both side by side. The navigator's content key is a user id or
 * [ProfileKey.CREATE], a primitive so the selection survives rotation, resizing and process death.
 *
 * On two-pane layouts with no chosen profile, the detail pane shows the feed's first user (the ViewModel's
 * `defaultSelectedUserId`) without navigating, so the default never opens full screen when the window shrinks and back
 * is not intercepted for it. Entering Edit mode on the default promotes it to a real destination so the draft survives
 * a resize (spec: adaptive-layout).
 *
 * Back is handled here because the "navigable" scaffold variant is Android-only; the profile's own Edit-mode back
 * handler is composed deeper, so it takes precedence while editing.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun UsersListDetailRoot(modifier: Modifier = Modifier) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>()
    val scope = rememberCoroutineScope()
    val usersViewModel: UsersViewModel = koinViewModel()
    val profileViewModel: ProfileViewModel = koinViewModel()
    val usersState by usersViewModel.state.collectAsStateWithLifecycle()
    // The profile's mode together with the user it is showing: promotion must only react to an edit of the default user
    // itself, not to a stale Edit mode left over from another profile while the navigator is mid-switch.
    val profileFocus by remember { profileViewModel.state.map { it.mode to it.header?.userId } }
        .collectAsStateWithLifecycle(initialValue = ProfileMode.VIEW to null)

    val selectedKey = navigator.currentDestination?.contentKey
    val isTwoPane =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
    val detailKey = selectedKey ?: usersState.defaultSelectedUserId.takeIf { isTwoPane }
    val isDefaultSelection = selectedKey == null && detailKey != null
    // ProfileRoot collects its events with the callbacks captured at first composition; read the latest value.
    val showingDefault by rememberUpdatedState(isDefaultSelection)

    LaunchedEffect(selectedKey) { usersViewModel.onAction(UsersAction.OnSelectionChange(selectedKey)) }
    LaunchedEffect(isTwoPane) { usersViewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane)) }
    LaunchedEffect(detailKey) {
        // However the pane was emptied (back, close icon, after save/delete), the next open starts clean.
        if (detailKey == null) profileViewModel.onAction(ProfileAction.OnDismiss)
    }
    LaunchedEffect(isDefaultSelection, detailKey, profileFocus) {
        // Promote the default to a real destination once it is edited, so the draft survives a shrink to one pane.
        val (mode, shownUserId) = profileFocus
        if (isDefaultSelection && detailKey != null && mode == ProfileMode.EDIT && shownUserId == detailKey) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, detailKey)
        }
    }

    @Suppress("DEPRECATION")
    BackHandler(enabled = navigator.canNavigateBack(BackNavigationBehavior.PopUntilContentChange)) {
        scope.launch { navigator.navigateBack(BackNavigationBehavior.PopUntilContentChange) }
    }

    fun closeProfile() {
        scope.launch { navigator.navigateBack(BackNavigationBehavior.PopUntilContentChange) }
    }

    // The scaffold leaves a gap between panes; paint it with the theme instead of the bare window background.
    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        listPane = {
            AnimatedPane {
                UsersRoot(
                    viewModel = usersViewModel,
                    onUserClick = { id -> scope.openDetail(navigator, id) },
                    onAddUserClick = { scope.openDetail(navigator, ProfileKey.CREATE) },
                )
            }
        },
        detailPane = {
            AnimatedPane {
                if (detailKey == null) {
                    SelectUserPlaceholder()
                } else {
                    ProfileRoot(
                        viewModel = profileViewModel,
                        profileKey = detailKey,
                        showBackButton = !isTwoPane,
                        onClose = { message ->
                            // A default selection whose user vanished just re-targets to the new first user, silently
                            // (spec: user-profile, "Default selection disappears"); there is nothing to navigate back.
                            if (!showingDefault) {
                                closeProfile()
                                message?.let { usersViewModel.onAction(UsersAction.OnShowMessage(it)) }
                            }
                        },
                        onCreated = { message ->
                            closeProfile()
                            usersViewModel.onAction(UsersAction.OnUserCreated)
                            message?.let { usersViewModel.onAction(UsersAction.OnShowMessage(it)) }
                        },
                    )
                }
            }
        },
    )
}

/** Shows [key] in the detail pane, replacing (not stacking on) a profile that is already open. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private fun CoroutineScope.openDetail(
    navigator: ThreePaneScaffoldNavigator<Long>,
    key: Long,
) {
    launch {
        if (navigator.currentDestination?.contentKey == key) return@launch
        if (navigator.currentDestination?.contentKey != null) {
            navigator.navigateBack(BackNavigationBehavior.PopUntilContentChange)
        }
        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key)
    }
}
