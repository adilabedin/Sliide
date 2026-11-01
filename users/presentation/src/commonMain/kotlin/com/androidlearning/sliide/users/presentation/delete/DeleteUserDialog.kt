package com.androidlearning.sliide.users.presentation.delete

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_body
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_cancel
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_confirm
import com.androidlearning.sliide.users.presentation.generated.resources.delete_dialog_title
import org.jetbrains.compose.resources.stringResource

/** Confirmation before a delete, shared by the feed's long-press and the profile's Delete button. */
@Composable
fun DeleteUserDialog(
    userName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = SliideShapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        icon = { Icon(imageVector = Icons.Filled.Delete, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(text = stringResource(Res.string.delete_dialog_title, userName)) },
        text = {
            Text(
                text = stringResource(Res.string.delete_dialog_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(Res.string.delete_dialog_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(Res.string.delete_dialog_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun DeleteUserDialogLightPreview() {
    SliideTheme(darkTheme = false) { DeleteUserDialog(userName = "Jane Doe", onConfirm = {}, onDismiss = {}) }
}

@Preview
@Composable
private fun DeleteUserDialogDarkPreview() {
    SliideTheme(darkTheme = true) { DeleteUserDialog(userName = "Jane Doe", onConfirm = {}, onDismiss = {}) }
}

@Preview
@Composable
private fun DeleteUserDialogLongNameLightPreview() {
    SliideTheme(darkTheme = false) {
        DeleteUserDialog(userName = "Maximilian Alexander von Hohenberg-Lichtenstein", onConfirm = {}, onDismiss = {})
    }
}
