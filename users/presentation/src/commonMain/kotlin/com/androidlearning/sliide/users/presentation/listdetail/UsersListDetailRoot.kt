package com.androidlearning.sliide.users.presentation.listdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide.core.designsystem.component.FullScreenMessage
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_select_body
import com.androidlearning.sliide.users.presentation.generated.resources.profile_select_title
import com.androidlearning.sliide.users.presentation.profile.ProfileAction
import com.androidlearning.sliide.users.presentation.profile.ProfileKey
import com.androidlearning.sliide.users.presentation.profile.ProfileRoot
import com.androidlearning.sliide.users.presentation.profile.ProfileViewModel
import com.androidlearning.sliide.users.presentation.users.UsersAction
import com.androidlearning.sliide.users.presentation.users.UsersRoot
import com.androidlearning.sliide.users.presentation.users.UsersViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The users feature's entry point (design D1): the feed as the list pane and the reusable profile as the detail pane
 * of Material 3's adaptive list-detail scaffold. Compact and medium widths show one pane (the profile full screen, back
 * returns to the feed); expanded widths show both side by side. The navigator's content key is a user id or
 * [ProfileKey.CREATE], a primitive so the selection survives rotation, resizing and process death.
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

    val selectedKey = navigator.currentDestination?.contentKey
    val isTwoPane =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

    LaunchedEffect(selectedKey) {
        usersViewModel.onAction(UsersAction.OnSelectionChange(selectedKey?.takeIf { it != ProfileKey.CREATE }))
        // However the pane was closed (back, close icon, after save/delete), the next open starts clean.
        if (selectedKey == null) profileViewModel.onAction(ProfileAction.OnDismiss)
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
                if (selectedKey == null) {
                    SelectUserPlaceholder()
                } else {
                    ProfileRoot(
                        viewModel = profileViewModel,
                        profileKey = selectedKey,
                        showBackButton = !isTwoPane,
                        onClose = { message ->
                            closeProfile()
                            message?.let { usersViewModel.onAction(UsersAction.OnShowMessage(it)) }
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

/** The empty detail pane on two-pane layouts. */
@Composable
fun SelectUserPlaceholder(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Box {
            FullScreenMessage(
                icon = Icons.Filled.Person,
                title = stringResource(Res.string.profile_select_title),
                body = stringResource(Res.string.profile_select_body),
            )
        }
    }
}

@Preview(widthDp = 480, heightDp = 720)
@Composable
private fun SelectUserPlaceholderLightPreview() {
    SliideTheme(darkTheme = false) { SelectUserPlaceholder() }
}

@Preview(widthDp = 480, heightDp = 720)
@Composable
private fun SelectUserPlaceholderDarkPreview() {
    SliideTheme(darkTheme = true) { SelectUserPlaceholder() }
}
