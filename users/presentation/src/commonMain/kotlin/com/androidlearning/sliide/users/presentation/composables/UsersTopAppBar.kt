package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme

/**
 * The single-line, fixed-height app bar shared by the users feed and the profile. It never collapses: with a
 * [TopAppBarDefaults.pinnedScrollBehavior] it only tints to `surfaceContainer` while content scrolls beneath it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsersTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = { Text(text = title) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    )
}

/** The two navigation glyphs the feature uses: a layout-direction-aware back arrow and a close (dismiss) cross. */
internal enum class NavigationIconKind(
    val icon: ImageVector,
) {
    Back(Icons.AutoMirrored.Filled.ArrowBack),
    Close(Icons.Filled.Close),
}

/** A back or close icon button with an accessible label; used by app bars and the delete dialog. */
@Composable
internal fun NavigationIconButton(
    kind: NavigationIconKind,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(imageVector = kind.icon, contentDescription = contentDescription)
    }
}

// region Previews

@Composable
private fun TopAppBarSample(
    darkTheme: Boolean,
    withNavigation: Boolean,
    withAction: Boolean,
) {
    SliideTheme(darkTheme = darkTheme) {
        UsersTopAppBar(
            title = if (withNavigation) "Profile" else "Users",
            navigationIcon = {
                if (withNavigation) NavigationIconButton(NavigationIconKind.Back, contentDescription = "Back", onClick = {})
            },
            actions = {
                if (withAction) {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Edit, contentDescription = "Edit profile") }
                }
            },
        )
    }
}

@Preview(widthDp = 320)
@Composable
private fun UsersTopAppBarTitleOnlyLightCompactPreview() {
    TopAppBarSample(darkTheme = false, withNavigation = false, withAction = false)
}

@Preview(widthDp = 840)
@Composable
private fun UsersTopAppBarTitleOnlyLightExpandedPreview() {
    TopAppBarSample(darkTheme = false, withNavigation = false, withAction = false)
}

@Preview(widthDp = 320)
@Composable
private fun UsersTopAppBarTitleOnlyDarkCompactPreview() {
    TopAppBarSample(darkTheme = true, withNavigation = false, withAction = false)
}

@Preview(widthDp = 840)
@Composable
private fun UsersTopAppBarTitleOnlyDarkExpandedPreview() {
    TopAppBarSample(darkTheme = true, withNavigation = false, withAction = false)
}

@Preview(widthDp = 320)
@Composable
private fun UsersTopAppBarNavigationAndActionLightCompactPreview() {
    TopAppBarSample(darkTheme = false, withNavigation = true, withAction = true)
}

@Preview(widthDp = 840)
@Composable
private fun UsersTopAppBarNavigationAndActionLightExpandedPreview() {
    TopAppBarSample(darkTheme = false, withNavigation = true, withAction = true)
}

@Preview(widthDp = 320)
@Composable
private fun UsersTopAppBarNavigationAndActionDarkCompactPreview() {
    TopAppBarSample(darkTheme = true, withNavigation = true, withAction = true)
}

@Preview(widthDp = 840)
@Composable
private fun UsersTopAppBarNavigationAndActionDarkExpandedPreview() {
    TopAppBarSample(darkTheme = true, withNavigation = true, withAction = true)
}

@Preview
@Composable
private fun NavigationIconButtonsLightPreview() {
    SliideTheme(darkTheme = false) {
        Surface {
            Row {
                NavigationIconButton(NavigationIconKind.Back, contentDescription = "Back", onClick = {})
                NavigationIconButton(NavigationIconKind.Close, contentDescription = "Cancel", onClick = {})
            }
        }
    }
}

@Preview
@Composable
private fun NavigationIconButtonsDarkPreview() {
    SliideTheme(darkTheme = true) {
        Surface {
            Row {
                NavigationIconButton(NavigationIconKind.Back, contentDescription = "Back", onClick = {})
                NavigationIconButton(NavigationIconKind.Close, contentDescription = "Cancel", onClick = {})
            }
        }
    }
}

// endregion
