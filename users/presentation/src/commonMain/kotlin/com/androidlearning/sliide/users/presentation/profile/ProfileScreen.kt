package com.androidlearning.sliide.users.presentation.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.androidlearning.sliide.core.designsystem.component.DestructiveButton
import com.androidlearning.sliide.core.designsystem.component.SegmentedToggle
import com.androidlearning.sliide.core.designsystem.component.UserAvatar
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.presentation.delete.DeleteUserDialog
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_back
import com.androidlearning.sliide.users.presentation.generated.resources.profile_cancel_edit
import com.androidlearning.sliide.users.presentation.generated.resources.profile_delete
import com.androidlearning.sliide.users.presentation.generated.resources.profile_edit
import com.androidlearning.sliide.users.presentation.generated.resources.profile_email_label
import com.androidlearning.sliide.users.presentation.generated.resources.profile_gender_female
import com.androidlearning.sliide.users.presentation.generated.resources.profile_gender_label
import com.androidlearning.sliide.users.presentation.generated.resources.profile_gender_male
import com.androidlearning.sliide.users.presentation.generated.resources.profile_name_label
import com.androidlearning.sliide.users.presentation.generated.resources.profile_save
import com.androidlearning.sliide.users.presentation.generated.resources.profile_saving
import com.androidlearning.sliide.users.presentation.generated.resources.profile_status_active
import com.androidlearning.sliide.users.presentation.generated.resources.profile_status_inactive
import com.androidlearning.sliide.users.presentation.generated.resources.profile_status_label
import com.androidlearning.sliide.users.presentation.generated.resources.profile_title
import com.androidlearning.sliide.users.presentation.generated.resources.profile_title_create
import com.androidlearning.sliide.users.presentation.generated.resources.users_pending_sync
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_invalid
import com.androidlearning.sliide.users.presentation.generated.resources.validation_name_required
import com.androidlearning.sliide.users.presentation.util.ObserveAsEvents
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.UiText
import com.androidlearning.sliide.users.presentation.util.asAddedText
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Readable maximum width of the form on wide panes (spec: adaptive-layout). */
private val FormMaxWidth = 560.dp

/**
 * The profile's ViewModel-wired entry point. [profileKey] is a user id or [ProfileKey.CREATE]; the caller owns
 * navigation: [onClose] leaves the profile (with a message for the feed), [onCreated] does the same after a create.
 */
@Composable
fun ProfileRoot(
    viewModel: ProfileViewModel,
    profileKey: Long,
    showBackButton: Boolean,
    onClose: (UiText?) -> Unit,
    onCreated: (UiText?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(profileKey) { viewModel.onAction(ProfileAction.OnOpen(profileKey)) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ProfileEvent.Close -> onClose(event.message)
            is ProfileEvent.Created -> onCreated(event.message)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    // Back while editing discards the draft instead of leaving the profile. CMP's BackHandler is deprecated in favour
    // of NavigationEventHandler, whose shared API is not published for this CMP version yet.
    @Suppress("DEPRECATION")
    BackHandler(enabled = state.mode == ProfileMode.EDIT) { onAction(ProfileAction.OnCancelEdit) }

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
                onAction = onAction,
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
                ProfileForm(state = state, onAction = onAction)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTopBar(
    mode: ProfileMode,
    isSaving: Boolean,
    canSave: Boolean,
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onAction: (ProfileAction) -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text =
                    stringResource(
                        if (mode == ProfileMode.CREATE) Res.string.profile_title_create else Res.string.profile_title,
                    ),
            )
        },
        navigationIcon = {
            when {
                mode == ProfileMode.EDIT ->
                    IconButton(onClick = { onAction(ProfileAction.OnCancelEdit) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(Res.string.profile_cancel_edit))
                    }
                showBackButton || mode == ProfileMode.CREATE ->
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector =
                                if (mode == ProfileMode.CREATE) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.profile_back),
                        )
                    }
            }
        },
        actions = {
            AnimatedContent(
                targetState = mode == ProfileMode.VIEW,
                transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.85f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.85f)) },
                label = "editSave",
            ) { isView ->
                if (isView) {
                    IconButton(onClick = { onAction(ProfileAction.OnEditClick) }) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(Res.string.profile_edit))
                    }
                } else {
                    SaveButton(isSaving = isSaving, enabled = canSave, onClick = { onAction(ProfileAction.OnSaveClick) })
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    )
}

@Composable
private fun SaveButton(
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val savingDescription = stringResource(Res.string.profile_saving)
    TextButton(onClick = onClick, enabled = enabled && !isSaving) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp).semantics { contentDescription = savingDescription },
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = stringResource(Res.string.profile_save),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ProfileHeaderSection(
    header: ProfileHeader,
    name: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SliideSpacing.s),
    ) {
        UserAvatar(initials = header.initials, toneSeed = header.userId, size = 88.dp)
        Spacer(modifier = Modifier.size(SliideSpacing.xs))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = header.addedAgo.asAddedText(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (header.isPendingSync) {
            Text(
                text = stringResource(Res.string.users_pending_sync),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.tertiaryContainer, SliideShapes.pill)
                        .padding(horizontal = SliideSpacing.s, vertical = SliideSpacing.xxs),
            )
        }
    }
}

@Composable
private fun ProfileForm(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
) {
    Surface(
        shape = SliideShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(SliideSpacing.l),
            verticalArrangement = Arrangement.spacedBy(SliideSpacing.l),
        ) {
            ProfileTextField(
                value = state.form.name,
                onValueChange = { onAction(ProfileAction.OnNameChange(it)) },
                label = stringResource(Res.string.profile_name_label),
                error = state.nameError,
                readOnly = !state.isEditable,
                keyboardOptions =
                    KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            ProfileTextField(
                value = state.form.email,
                onValueChange = { onAction(ProfileAction.OnEmailChange(it)) },
                label = stringResource(Res.string.profile_email_label),
                error = state.emailError,
                readOnly = !state.isEditable,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            )
            LabeledToggle(label = stringResource(Res.string.profile_gender_label)) {
                val male = stringResource(Res.string.profile_gender_male)
                val female = stringResource(Res.string.profile_gender_female)
                SegmentedToggle(
                    first = Gender.MALE,
                    second = Gender.FEMALE,
                    selected = state.form.gender,
                    onSelect = { onAction(ProfileAction.OnGenderChange(it)) },
                    label = { if (it == Gender.MALE) male else female },
                    readOnly = !state.isEditable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LabeledToggle(label = stringResource(Res.string.profile_status_label)) {
                val active = stringResource(Res.string.profile_status_active)
                val inactive = stringResource(Res.string.profile_status_inactive)
                SegmentedToggle(
                    first = UserStatus.ACTIVE,
                    second = UserStatus.INACTIVE,
                    selected = state.form.status,
                    onSelect = { onAction(ProfileAction.OnStatusChange(it)) },
                    label = { if (it == UserStatus.ACTIVE) active else inactive },
                    readOnly = !state.isEditable,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: UiText?,
    readOnly: Boolean,
    keyboardOptions: KeyboardOptions,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        readOnly = readOnly,
        isError = error != null,
        supportingText = error?.let { { Text(it.asString()) } },
        singleLine = true,
        keyboardOptions = keyboardOptions,
        shape = SliideShapes.medium,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (readOnly) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = if (readOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabeledToggle(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SliideSpacing.s)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
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
