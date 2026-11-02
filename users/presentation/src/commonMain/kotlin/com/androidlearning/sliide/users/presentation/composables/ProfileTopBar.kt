package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_back
import com.androidlearning.sliide.users.presentation.generated.resources.profile_cancel_edit
import com.androidlearning.sliide.users.presentation.generated.resources.profile_edit
import com.androidlearning.sliide.users.presentation.generated.resources.profile_title
import com.androidlearning.sliide.users.presentation.generated.resources.profile_title_create
import com.androidlearning.sliide.users.presentation.profile.ProfileMode
import org.jetbrains.compose.resources.stringResource

/**
 * The profile app bar (spec: user-profile). Navigation icon by mode: Edit shows a close (cancel edit); Create always
 * shows a back arrow, because the page slides in as a navigation step; View shows a back arrow only when
 * [showBackButton] (full screen). The end slot animates between the Edit icon and the filled [SaveButton].
 */
@Composable
internal fun ProfileTopBar(
    mode: ProfileMode,
    isSaving: Boolean,
    canSave: Boolean,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onCancelEditClick: () -> Unit,
    onEditClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UsersTopAppBar(
        title = stringResource(if (mode == ProfileMode.CREATE) Res.string.profile_title_create else Res.string.profile_title),
        modifier = modifier,
        navigationIcon = {
            when {
                mode == ProfileMode.EDIT ->
                    NavigationIconButton(
                        kind = NavigationIconKind.Close,
                        contentDescription = stringResource(Res.string.profile_cancel_edit),
                        onClick = onCancelEditClick,
                    )
                mode == ProfileMode.CREATE || showBackButton ->
                    NavigationIconButton(
                        kind = NavigationIconKind.Back,
                        contentDescription = stringResource(Res.string.profile_back),
                        onClick = onBackClick,
                    )
            }
        },
        actions = {
            AnimatedContent(
                targetState = mode == ProfileMode.VIEW,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.85f)) },
                contentAlignment = Alignment.CenterEnd,
                label = "editSave",
            ) { isView ->
                if (isView) {
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.profile_edit))
                    }
                } else {
                    SaveButton(isSaving = isSaving, enabled = canSave, onClick = onSaveClick)
                }
            }
        },
    )
}

// region Previews

@Composable
private fun ProfileTopBarSample(
    darkTheme: Boolean,
    mode: ProfileMode,
    isSaving: Boolean = false,
    canSave: Boolean = false,
    showBackButton: Boolean = true,
) {
    SliideTheme(darkTheme = darkTheme) {
        ProfileTopBar(
            mode = mode,
            isSaving = isSaving,
            canSave = canSave,
            showBackButton = showBackButton,
            onBackClick = {},
            onCancelEditClick = {},
            onEditClick = {},
            onSaveClick = {},
        )
    }
}

@Preview(widthDp = 320)
@Composable
private fun ProfileTopBarViewLightCompactPreview() {
    ProfileTopBarSample(darkTheme = false, mode = ProfileMode.VIEW)
}

@Preview(widthDp = 840)
@Composable
private fun ProfileTopBarViewTwoPaneLightExpandedPreview() {
    ProfileTopBarSample(darkTheme = false, mode = ProfileMode.VIEW, showBackButton = false)
}

@Preview(widthDp = 320)
@Composable
private fun ProfileTopBarViewDarkCompactPreview() {
    ProfileTopBarSample(darkTheme = true, mode = ProfileMode.VIEW)
}

@Preview(widthDp = 320)
@Composable
private fun ProfileTopBarEditLightCompactPreview() {
    ProfileTopBarSample(darkTheme = false, mode = ProfileMode.EDIT, canSave = true)
}

@Preview(widthDp = 320)
@Composable
private fun ProfileTopBarEditDarkCompactPreview() {
    ProfileTopBarSample(darkTheme = true, mode = ProfileMode.EDIT, canSave = true)
}

@Preview(widthDp = 320)
@Composable
private fun ProfileTopBarSavingLightCompactPreview() {
    ProfileTopBarSample(darkTheme = false, mode = ProfileMode.EDIT, isSaving = true)
}

@Preview(widthDp = 320)
@Composable
private fun ProfileTopBarCreateLightCompactPreview() {
    ProfileTopBarSample(darkTheme = false, mode = ProfileMode.CREATE)
}

@Preview(widthDp = 840)
@Composable
private fun ProfileTopBarCreateTwoPaneLightExpandedPreview() {
    ProfileTopBarSample(darkTheme = false, mode = ProfileMode.CREATE, showBackButton = false)
}

@Preview(widthDp = 320)
@Composable
private fun ProfileTopBarCreateDarkCompactPreview() {
    ProfileTopBarSample(darkTheme = true, mode = ProfileMode.CREATE, canSave = true)
}

// endregion
