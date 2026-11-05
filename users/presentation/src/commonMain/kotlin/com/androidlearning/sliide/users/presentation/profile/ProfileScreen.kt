package com.androidlearning.sliide.users.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.androidlearning.sliide.core.designsystem.component.DestructiveButton
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.composables.DeleteUserDialog
import com.androidlearning.sliide.users.presentation.composables.ProfileFormCard
import com.androidlearning.sliide.users.presentation.composables.ProfileHeaderSection
import com.androidlearning.sliide.users.presentation.composables.ProfileTopBar
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_delete
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_invalid
import com.androidlearning.sliide.users.presentation.generated.resources.validation_name_required
import com.androidlearning.sliide.users.presentation.util.ObserveAsEvents
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.UiText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Readable maximum width of the form on wide panes (spec: adaptive-layout). */
private val FormMaxWidth = 560.dp

/**
 * The profile's ViewModel-wired entry point. [profileKey] is a user id (the detail pane) or [ProfileKey.CREATE] (the
 * add-user screen); the caller owns navigation: [onClose] leaves the profile (with a message for the feed),
 * [onCreated] leaves the add-user screen with how the new user was saved, and [onDeleteConfirmed] hands a confirmed
 * delete to the feed, which owns the Undo window.
 */
@Composable
fun ProfileRoot(
    viewModel: ProfileViewModel,
    profileKey: Long,
    showBackButton: Boolean,
    onClose: (UiText?) -> Unit,
    onCreated: (WriteStatus) -> Unit,
    onDeleteConfirmed: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profileKey) { viewModel.onAction(ProfileAction.OnOpen(profileKey)) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProfileEvent.Close -> onClose(event.message)
            is ProfileEvent.Created -> onCreated(event.status)
            is ProfileEvent.DeleteConfirmed -> onDeleteConfirmed(event.userId)
            is ProfileEvent.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(event.message.resolve()) }
        }
    }

    ProfileScreen(
        state = state,
        onAction = viewModel::onAction,
        showBackButton = showBackButton,
        onBackClick = { onClose(null) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Back while editing discards the draft instead of leaving the profile.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.mode == ProfileMode.EDIT,
        onBackCompleted = { onAction(ProfileAction.OnCancelEdit) },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ProfileTopBar(
                mode = state.mode,
                isSaving = state.isSaving,
                canSave = state.canSave,
                showBackButton = showBackButton,
                onBackClick = onBackClick,
                onCancelEditClick = { onAction(ProfileAction.OnCancelEdit) },
                onEditClick = { onAction(ProfileAction.OnEditClick) },
                onSaveClick = { onAction(ProfileAction.OnSaveClick) },
            )
        },
        bottomBar = {
            if (state.mode != ProfileMode.CREATE) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                                    .padding(horizontal = SliideSpacing.l, vertical = SliideSpacing.m),
                            contentAlignment = Alignment.Center,
                        ) {
                            DestructiveButton(
                                text = stringResource(Res.string.profile_delete),
                                onClick = { onAction(ProfileAction.OnDeleteClick) },
                                enabled = !state.isSaving,
                                modifier = Modifier.widthIn(max = FormMaxWidth),
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .widthIn(max = FormMaxWidth)
                        .fillMaxWidth()
                        .padding(horizontal = SliideSpacing.l, vertical = SliideSpacing.l),
                verticalArrangement = Arrangement.spacedBy(SliideSpacing.xl),
            ) {
                state.header?.let { header -> ProfileHeaderSection(header = header, name = state.form.name) }
                ProfileFormCard(
                    form = state.form,
                    nameError = state.nameError,
                    emailError = state.emailError,
                    readOnly = !state.isEditable,
                    onNameChange = { onAction(ProfileAction.OnNameChange(it)) },
                    onEmailChange = { onAction(ProfileAction.OnEmailChange(it)) },
                    onGenderChange = { onAction(ProfileAction.OnGenderChange(it)) },
                    onStatusChange = { onAction(ProfileAction.OnStatusChange(it)) },
                )
            }
        }
    }

    if (state.showDeleteDialog) {
        DeleteUserDialog(
            userName = state.form.name,
            onConfirm = { onAction(ProfileAction.OnDeleteConfirm) },
            onDismiss = { onAction(ProfileAction.OnDeleteCancel) },
        )
    }
}

// region Previews

@Composable
private fun ProfileScreenPreview(
    state: ProfileState,
    darkTheme: Boolean,
) {
    SliideTheme(darkTheme = darkTheme) {
        ProfileScreen(
            state = state,
            onAction = {},
            showBackButton = true,
            onBackClick = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

private val previewHeader = ProfileHeader(userId = 7_401_001, initials = "JD", addedAgo = RelativeTime.Minutes(5), isPendingSync = false)
private val previewForm = ProfileForm("Jane Doe", "jane.doe@example.com", Gender.FEMALE, UserStatus.ACTIVE)
private val viewState = ProfileState(mode = ProfileMode.VIEW, header = previewHeader, form = previewForm)
private val editValidState = viewState.copy(mode = ProfileMode.EDIT, form = previewForm.copy(name = "Jane Smith"), canSave = true)
private val editInvalidState =
    viewState.copy(
        mode = ProfileMode.EDIT,
        form = previewForm.copy(name = "", email = "jane@example"),
        nameError = UiText.Resource(Res.string.validation_name_required),
        emailError = UiText.Resource(Res.string.validation_email_invalid),
    )
private val savingState = editValidState.copy(isSaving = true, canSave = false)
private val createPristineState = ProfileState(mode = ProfileMode.CREATE)
private val createErrorsState =
    createPristineState.copy(
        form = ProfileForm(name = "", email = "ada@"),
        nameError = UiText.Resource(Res.string.validation_name_required),
        emailError = UiText.Resource(Res.string.validation_email_invalid),
    )
private val deleteDialogState = viewState.copy(showDeleteDialog = true)

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenViewLightCompactPreview() {
    ProfileScreenPreview(viewState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenViewLightExpandedPreview() {
    ProfileScreenPreview(viewState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenViewDarkCompactPreview() {
    ProfileScreenPreview(viewState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenViewDarkExpandedPreview() {
    ProfileScreenPreview(viewState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenEditValidLightCompactPreview() {
    ProfileScreenPreview(editValidState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenEditValidLightExpandedPreview() {
    ProfileScreenPreview(editValidState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenEditValidDarkCompactPreview() {
    ProfileScreenPreview(editValidState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenEditValidDarkExpandedPreview() {
    ProfileScreenPreview(editValidState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenEditInvalidLightCompactPreview() {
    ProfileScreenPreview(editInvalidState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenEditInvalidLightExpandedPreview() {
    ProfileScreenPreview(editInvalidState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenEditInvalidDarkCompactPreview() {
    ProfileScreenPreview(editInvalidState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenEditInvalidDarkExpandedPreview() {
    ProfileScreenPreview(editInvalidState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenSavingLightCompactPreview() {
    ProfileScreenPreview(savingState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenSavingLightExpandedPreview() {
    ProfileScreenPreview(savingState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenSavingDarkCompactPreview() {
    ProfileScreenPreview(savingState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenSavingDarkExpandedPreview() {
    ProfileScreenPreview(savingState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenCreatePristineLightCompactPreview() {
    ProfileScreenPreview(createPristineState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenCreatePristineLightExpandedPreview() {
    ProfileScreenPreview(createPristineState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenCreatePristineDarkCompactPreview() {
    ProfileScreenPreview(createPristineState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenCreatePristineDarkExpandedPreview() {
    ProfileScreenPreview(createPristineState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenCreateErrorsLightCompactPreview() {
    ProfileScreenPreview(createErrorsState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenCreateErrorsLightExpandedPreview() {
    ProfileScreenPreview(createErrorsState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenCreateErrorsDarkCompactPreview() {
    ProfileScreenPreview(createErrorsState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenCreateErrorsDarkExpandedPreview() {
    ProfileScreenPreview(createErrorsState, darkTheme = true)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenDeleteDialogLightCompactPreview() {
    ProfileScreenPreview(deleteDialogState, darkTheme = false)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenDeleteDialogLightExpandedPreview() {
    ProfileScreenPreview(deleteDialogState, darkTheme = false)
}

@Preview(widthDp = 320, heightDp = 800)
@Composable
private fun ProfileScreenDeleteDialogDarkCompactPreview() {
    ProfileScreenPreview(deleteDialogState, darkTheme = true)
}

@Preview(widthDp = 840, heightDp = 800)
@Composable
private fun ProfileScreenDeleteDialogDarkExpandedPreview() {
    ProfileScreenPreview(deleteDialogState, darkTheme = true)
}

// endregion
