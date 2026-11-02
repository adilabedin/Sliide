package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_save
import com.androidlearning.sliide.users.presentation.generated.resources.profile_saving
import org.jetbrains.compose.resources.stringResource

/** Minimum height of the Save button: clearly larger than the app bar's icon buttons (spec: user-profile). */
internal val SaveButtonMinHeight = 48.dp

/**
 * The profile's prominent, filled Save action for the app bar. While [isSaving] a progress indicator replaces the
 * label; the label stays laid out (invisible) underneath so the button keeps its width.
 */
@Composable
internal fun SaveButton(
    isSaving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.profile_save)
    val savingDescription = stringResource(Res.string.profile_saving)
    Button(
        onClick = onClick,
        enabled = enabled && !isSaving,
        modifier = modifier.padding(end = SliideSpacing.s).heightIn(min = SaveButtonMinHeight),
        shape = SliideShapes.medium,
        contentPadding = PaddingValues(horizontal = SliideSpacing.l, vertical = SliideSpacing.s),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier =
                    if (isSaving) {
                        Modifier.alpha(0f).clearAndSetSemantics {}
                    } else {
                        Modifier
                    },
            )
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).semantics { contentDescription = savingDescription },
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            }
        }
    }
}

// region Previews

@Composable
private fun SaveButtonSample(
    darkTheme: Boolean,
    isSaving: Boolean,
    enabled: Boolean,
) {
    SliideTheme(darkTheme = darkTheme) {
        Surface {
            Box(modifier = Modifier.padding(SliideSpacing.l)) {
                SaveButton(isSaving = isSaving, enabled = enabled, onClick = {})
            }
        }
    }
}

@Preview
@Composable
private fun SaveButtonEnabledLightPreview() {
    SaveButtonSample(darkTheme = false, isSaving = false, enabled = true)
}

@Preview
@Composable
private fun SaveButtonEnabledDarkPreview() {
    SaveButtonSample(darkTheme = true, isSaving = false, enabled = true)
}

@Preview
@Composable
private fun SaveButtonDisabledLightPreview() {
    SaveButtonSample(darkTheme = false, isSaving = false, enabled = false)
}

@Preview
@Composable
private fun SaveButtonDisabledDarkPreview() {
    SaveButtonSample(darkTheme = true, isSaving = false, enabled = false)
}

@Preview
@Composable
private fun SaveButtonSavingLightPreview() {
    SaveButtonSample(darkTheme = false, isSaving = true, enabled = true)
}

@Preview
@Composable
private fun SaveButtonSavingDarkPreview() {
    SaveButtonSample(darkTheme = true, isSaving = true, enabled = true)
}

// endregion
