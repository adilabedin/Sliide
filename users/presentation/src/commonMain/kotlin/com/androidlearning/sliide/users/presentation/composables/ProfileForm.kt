package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.component.SegmentedToggle
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_email_label
import com.androidlearning.sliide.users.presentation.generated.resources.profile_gender_female
import com.androidlearning.sliide.users.presentation.generated.resources.profile_gender_label
import com.androidlearning.sliide.users.presentation.generated.resources.profile_gender_male
import com.androidlearning.sliide.users.presentation.generated.resources.profile_name_label
import com.androidlearning.sliide.users.presentation.generated.resources.profile_status_active
import com.androidlearning.sliide.users.presentation.generated.resources.profile_status_inactive
import com.androidlearning.sliide.users.presentation.generated.resources.profile_status_label
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_invalid
import com.androidlearning.sliide.users.presentation.generated.resources.validation_name_required
import com.androidlearning.sliide.users.presentation.profile.ProfileForm
import com.androidlearning.sliide.users.presentation.util.UiText
import org.jetbrains.compose.resources.stringResource

/**
 * The one form used by every profile mode: name, email, gender and status. With [readOnly] the controls show values
 * without a cursor or toggle interaction (View mode and while saving).
 */
@Composable
internal fun ProfileFormCard(
    form: ProfileForm,
    nameError: UiText?,
    emailError: UiText?,
    readOnly: Boolean,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onGenderChange: (Gender) -> Unit,
    onStatusChange: (UserStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = SliideShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(SliideSpacing.l),
            verticalArrangement = Arrangement.spacedBy(SliideSpacing.l),
        ) {
            ProfileTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = stringResource(Res.string.profile_name_label),
                error = nameError,
                readOnly = readOnly,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            ProfileTextField(
                value = form.email,
                onValueChange = onEmailChange,
                label = stringResource(Res.string.profile_email_label),
                error = emailError,
                readOnly = readOnly,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            )
            LabeledToggle(label = stringResource(Res.string.profile_gender_label)) {
                val male = stringResource(Res.string.profile_gender_male)
                val female = stringResource(Res.string.profile_gender_female)
                SegmentedToggle(
                    first = Gender.MALE,
                    second = Gender.FEMALE,
                    selected = form.gender,
                    onSelect = onGenderChange,
                    label = { if (it == Gender.MALE) male else female },
                    readOnly = readOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LabeledToggle(label = stringResource(Res.string.profile_status_label)) {
                val active = stringResource(Res.string.profile_status_active)
                val inactive = stringResource(Res.string.profile_status_inactive)
                SegmentedToggle(
                    first = UserStatus.ACTIVE,
                    second = UserStatus.INACTIVE,
                    selected = form.status,
                    onSelect = onStatusChange,
                    label = { if (it == UserStatus.ACTIVE) active else inactive },
                    readOnly = readOnly,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A single-line outlined field whose focus colours stay neutral while [readOnly]. */
@Composable
internal fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: UiText?,
    readOnly: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
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
        modifier = modifier.fillMaxWidth(),
    )
}

/** A small caption above a control, such as a two-option toggle. */
@Composable
internal fun LabeledToggle(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(SliideSpacing.s)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

// region Previews

private val sampleForm = ProfileForm("Jane Doe", "jane.doe@example.com", Gender.FEMALE, UserStatus.ACTIVE)

@Composable
private fun FormSample(
    darkTheme: Boolean,
    form: ProfileForm = sampleForm,
    readOnly: Boolean,
    withErrors: Boolean = false,
) {
    SliideTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ProfileFormCard(
                form = form,
                nameError = if (withErrors) UiText.Resource(Res.string.validation_name_required) else null,
                emailError = if (withErrors) UiText.Resource(Res.string.validation_email_invalid) else null,
                readOnly = readOnly,
                onNameChange = {},
                onEmailChange = {},
                onGenderChange = {},
                onStatusChange = {},
                modifier = Modifier.padding(SliideSpacing.l),
            )
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun ProfileFormReadOnlyLightPreview() {
    FormSample(darkTheme = false, readOnly = true)
}

@Preview(widthDp = 360)
@Composable
private fun ProfileFormReadOnlyDarkPreview() {
    FormSample(darkTheme = true, readOnly = true)
}

@Preview(widthDp = 360)
@Composable
private fun ProfileFormEditableLightPreview() {
    FormSample(darkTheme = false, readOnly = false)
}

@Preview(widthDp = 360)
@Composable
private fun ProfileFormEditableDarkPreview() {
    FormSample(darkTheme = true, readOnly = false)
}

@Preview(widthDp = 360)
@Composable
private fun ProfileFormErrorsLightPreview() {
    FormSample(darkTheme = false, form = sampleForm.copy(name = "", email = "jane@example"), readOnly = false, withErrors = true)
}

@Preview(widthDp = 360)
@Composable
private fun ProfileFormErrorsDarkPreview() {
    FormSample(darkTheme = true, form = sampleForm.copy(name = "", email = "jane@example"), readOnly = false, withErrors = true)
}

// endregion
