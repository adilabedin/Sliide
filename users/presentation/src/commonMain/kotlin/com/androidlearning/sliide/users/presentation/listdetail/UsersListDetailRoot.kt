package com.androidlearning.sliide.users.presentation.listdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide.core.designsystem.layout.SliideAdaptiveListDetailLayout
import com.androidlearning.sliide.core.designsystem.layout.currentDeviceConfiguration
import com.androidlearning.sliide.core.designsystem.layout.showsListDetailOnePaneAtATime
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.composables.SelectUserPlaceholder
import com.androidlearning.sliide.users.presentation.profile.ProfileAction
import com.androidlearning.sliide.users.presentation.profile.ProfileMode
import com.androidlearning.sliide.users.presentation.profile.ProfileRoot
import com.androidlearning.sliide.users.presentation.profile.ProfileViewModel
import com.androidlearning.sliide.users.presentation.users.UsersAction
import com.androidlearning.sliide.users.presentation.users.UsersRoot
import com.androidlearning.sliide.users.presentation.users.UsersViewModel
import com.androidlearning.sliide.users.presentation.util.toQueuedMessage
import kotlinx.coroutines.flow.map
import org.koin.compose.viewmodel.koinViewModel

/**
 * The users feed and the open profile, as one navigation destination (spec: adaptive-layout). A phone held upright
 * shows one at a time; wider windows show both side by side (see [SliideAdaptiveListDetailLayout]). Which profile is
 * open lives in [UsersViewModel], not in navigation, so turning the device keeps it, and so does process death.
 *
 * On two-pane layouts with no chosen profile, the detail pane shows the feed's first user (the ViewModel's
 * `defaultSelectedUserId`) without choosing it, so the default never opens full screen when the window shrinks and back
 * is not intercepted for it. Entering Edit mode on the default chooses it, so the draft survives a resize.
 *
 * Adding a user is a separate destination; [onAddUserClick] goes there, and [createdStatus] is how its last save
 * went, announced here once and then cleared through [onCreatedStatusShown].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun UsersListDetailRoot(
    onAddUserClick: () -> Unit,
    createdStatus: WriteStatus?,
    onCreatedStatusShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usersViewModel: UsersViewModel = koinViewModel()
    val profileViewModel: ProfileViewModel = koinViewModel()
    val usersState by usersViewModel.state.collectAsStateWithLifecycle()
    // The profile's mode together with the user it is showing: promotion must only react to an edit of the default user
    // itself, not to a stale Edit mode left over from another profile while the pane is mid-switch.
    val profileFocus by remember { profileViewModel.state.map { it.mode to it.header?.userId } }
        .collectAsStateWithLifecycle(initialValue = ProfileMode.VIEW to null)

    val configuration = currentDeviceConfiguration()
    val isOnePaneAtATime = configuration.showsListDetailOnePaneAtATime
    val openedUserId = usersState.selectedUserId
    val isDefaultSelection = usersState.defaultSelectedUserId != null

    // ProfileRoot collects its events with the callbacks captured at first composition; read the latest value.
    val showingDefault by rememberUpdatedState(isDefaultSelection)

    fun closeProfile() = usersViewModel.onAction(UsersAction.OnSelectionChange(null))

    // Read when the profile pane leaves composition, to tell a close from leaving for another destination.
    val latestOpenedUserId by rememberUpdatedState(openedUserId)

    LaunchedEffect(isOnePaneAtATime) { usersViewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = !isOnePaneAtATime)) }

    LaunchedEffect(isDefaultSelection, openedUserId, profileFocus) {
        // Choose the default once it is edited, so the draft survives a shrink to one pane.
        val (mode, shownUserId) = profileFocus
        if (isDefaultSelection && mode == ProfileMode.EDIT && shownUserId == openedUserId) {
            usersViewModel.onAction(UsersAction.OnSelectionChange(openedUserId))
        }
    }

    LaunchedEffect(createdStatus) {
        if (createdStatus == null) return@LaunchedEffect
        usersViewModel.onAction(UsersAction.OnUserCreated)
        createdStatus.toQueuedMessage()?.let { usersViewModel.onAction(UsersAction.OnShowMessage(it)) }
        onCreatedStatusShown()
    }

    // On a phone held upright the profile covers the feed, so back returns to it. The profile's own Edit-mode back
    // handler is composed deeper, so it takes precedence while editing.
    @Suppress("DEPRECATION")
    BackHandler(enabled = isOnePaneAtATime && openedUserId != null) { closeProfile() }

    SliideAdaptiveListDetailLayout(
        selectedItem = openedUserId,
        configuration = configuration,
        modifier = modifier,
        list = {
            UsersRoot(
                viewModel = usersViewModel,
                onUserClick = { id -> usersViewModel.onAction(UsersAction.OnSelectionChange(id)) },
                onAddUserClick = onAddUserClick,
            )
        },
        detail = { userId ->
            // However the profile was closed (back, its back arrow, after a delete), the next open starts clean. It is
            // reset once the pane has left the screen, so it doesn't empty itself while sliding out; leaving for the
            // add-user screen disposes it too, with the profile still open, and keeps it.
            DisposableEffect(Unit) {
                onDispose { if (latestOpenedUserId == null) profileViewModel.onAction(ProfileAction.OnDismiss) }
            }
            ProfileRoot(
                viewModel = profileViewModel,
                profileKey = userId,
                showBackButton = isOnePaneAtATime,
                onClose = { message ->
                    // A default selection whose user vanished just re-targets to the new first user, silently
                    // (spec: user-profile, "Default selection disappears"); there is nothing to close.
                    if (!showingDefault) {
                        closeProfile()
                        message?.let { usersViewModel.onAction(UsersAction.OnShowMessage(it)) }
                    }
                },
                // The detail pane only shows existing users; creating one is the add-user destination.
                onCreated = {},
            )
        },
        emptyDetail = { SelectUserPlaceholder() },
    )
}
