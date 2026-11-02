package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.component.DestructiveButton
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_body
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_cancel
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_confirm
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_title
import org.jetbrains.compose.resources.stringResource

/** Readable maximum width of the dialog on tablets. */
private val DialogMaxWidth = 400.dp

/**
 * Confirmation before a delete, shared by the feed's long-press and the profile's Delete button (spec: user-deletion).
 * A close icon at the top start dismisses it, as do back and an outside tap; the single full-width [DestructiveButton]
 * confirms. Dismissing changes nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeleteUserDialog(
    userName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicAlertDialog(onDismissRequest = onDismiss, modifier = modifier.widthIn(max = DialogMaxWidth)) {
        DeleteUserDialogContent(userName = userName, onConfirm = onConfirm, onDismiss = onDismiss)
    }
}

/** The dialog's surface, split out so previews can render it without a window. */
@Composable
private fun DeleteUserDialogContent(
    userName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = SliideShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(SliideSpacing.m).padding(bottom = SliideSpacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                NavigationIconButton(
                    kind = NavigationIconKind.Close,
                    contentDescription = stringResource(Res.string.delete_dialog_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(modifier = Modifier.size(SliideSpacing.l))
            Column(
                modifier = Modifier.padding(horizontal = SliideSpacing.s),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SliideSpacing.s),
            ) {
                Text(
                    text = stringResource(Res.string.delete_dialog_title, userName),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(Res.string.delete_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.size(SliideSpacing.xl))
            DestructiveButton(
                text = stringResource(Res.string.delete_dialog_confirm),
                onClick = onConfirm,
                modifier = Modifier.padding(horizontal = SliideSpacing.s),
            )
        }
    }
}

// region Previews

@Composable
private fun DialogSample(
    darkTheme: Boolean,
    userName: String,
) {
    SliideTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.padding(SliideSpacing.l).widthIn(max = DialogMaxWidth)) {
            DeleteUserDialogContent(userName = userName, onConfirm = {}, onDismiss = {})
        }
    }
}

@Preview(widthDp = 360)
@Composable
private fun DeleteUserDialogLightPreview() {
    DialogSample(darkTheme = false, userName = "Jane Doe")
}

@Preview(widthDp = 360)
@Composable
private fun DeleteUserDialogDarkPreview() {
    DialogSample(darkTheme = true, userName = "Jane Doe")
}

@Preview(widthDp = 360)
@Composable
private fun DeleteUserDialogLongNameLightPreview() {
    DialogSample(darkTheme = false, userName = "Maximilian Alexander von Hohenberg-Lichtenstein")
}

@Preview(widthDp = 360)
@Composable
private fun DeleteUserDialogLongNameDarkPreview() {
    DialogSample(darkTheme = true, userName = "Maximilian Alexander von Hohenberg-Lichtenstein")
}

// endregion
