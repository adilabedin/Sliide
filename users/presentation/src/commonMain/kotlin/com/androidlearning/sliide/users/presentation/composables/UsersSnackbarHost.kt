package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide.core.designsystem.component.UndoSnackbar
import com.androidlearning.sliide.core.designsystem.component.rememberCountdownFraction
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.core.designsystem.theme.extendedColors
import kotlin.time.Instant

/** Snackbar data for an Undo window. Shown indefinitely: the controller's timer, not the host, ends the window. */
internal class UndoSnackbarVisuals(
    override val message: String,
    override val actionLabel: String,
    val userId: Long,
    val deadline: Instant,
    val windowMillis: Long,
) : SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/**
 * The feed's snackbar host: [UndoSnackbarVisuals] render as the swipeable Undo snackbar with a countdown, everything
 * else as a plain message snackbar.
 */
@Composable
internal fun UsersSnackbarHost(
    hostState: SnackbarHostState,
    currentTime: () -> Instant,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data -> UsersSnackbar(data, currentTime) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersSnackbar(
    data: SnackbarData,
    currentTime: () -> Instant,
) {
    val visuals = data.visuals
    if (visuals is UndoSnackbarVisuals) {
        val remaining = (visuals.deadline - currentTime()).inWholeMilliseconds.coerceAtLeast(0)
        val fraction = rememberCountdownFraction(visuals.userId, remaining, visuals.windowMillis)
        val dismissState = rememberSwipeToDismissBoxState()
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {},
            onDismiss = { data.dismiss() },
        ) {
            UndoSnackbar(
                message = visuals.message,
                actionLabel = visuals.actionLabel,
                onAction = data::performAction,
                countdownFraction = fraction,
            )
        }
    } else {
        Snackbar(
            snackbarData = data,
            modifier = Modifier.padding(horizontal = SliideSpacing.l, vertical = SliideSpacing.s),
            shape = SliideShapes.large,
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.extendedColors.inverseAccent,
        )
    }
}

// region Previews

private val PREVIEW_NOW = Instant.fromEpochMilliseconds(1_800_000_000_000)

private class PreviewSnackbarData(
    override val visuals: SnackbarVisuals,
) : SnackbarData {
    override fun performAction() = Unit

    override fun dismiss() = Unit
}

private class MessageVisuals(
    override val message: String,
) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Short
}

private val undoVisuals =
    UndoSnackbarVisuals(
        message = "Jane Doe deleted",
        actionLabel = "Undo",
        userId = 7_401_001,
        deadline = Instant.fromEpochMilliseconds(PREVIEW_NOW.toEpochMilliseconds() + 3_000),
        windowMillis = 5_000,
    )

@Composable
private fun SnackbarSample(
    darkTheme: Boolean,
    visuals: SnackbarVisuals,
) {
    SliideTheme(darkTheme = darkTheme) {
        Box { UsersSnackbar(data = PreviewSnackbarData(visuals), currentTime = { PREVIEW_NOW }) }
    }
}

@Preview(widthDp = 360)
@Composable
private fun UsersSnackbarMessageLightPreview() {
    SnackbarSample(darkTheme = false, visuals = MessageVisuals("Saved offline, will sync when online"))
}

@Preview(widthDp = 360)
@Composable
private fun UsersSnackbarMessageDarkPreview() {
    SnackbarSample(darkTheme = true, visuals = MessageVisuals("Saved offline, will sync when online"))
}

@Preview(widthDp = 360)
@Composable
private fun UsersSnackbarUndoLightPreview() {
    SnackbarSample(darkTheme = false, visuals = undoVisuals)
}

@Preview(widthDp = 360)
@Composable
private fun UsersSnackbarUndoDarkPreview() {
    SnackbarSample(darkTheme = true, visuals = undoVisuals)
}

// endregion
