package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.core.designsystem.theme.extendedColors

/**
 * The Undo snackbar: an ink pill with [message], a champagne [actionLabel] button, and a hairline along the bottom
 * that empties as the Undo window runs out. [countdownFraction] is read in the draw phase only (1 = full, 0 = expired).
 */
@Composable
fun UndoSnackbar(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    countdownFraction: () -> Float,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.16f)
    val progressColor = MaterialTheme.extendedColors.inverseAccent
    Surface(
        modifier = modifier.padding(horizontal = SliideSpacing.l, vertical = SliideSpacing.s),
        shape = SliideShapes.large,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = SliideSpacing.l, end = SliideSpacing.s, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = onAction) {
                    Text(text = actionLabel, color = progressColor, style = MaterialTheme.typography.labelLarge)
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(SliideShapes.pill)
                        .drawBehind {
                            drawRect(trackColor)
                            drawRect(
                                color = progressColor,
                                size = Size(size.width * countdownFraction().coerceIn(0f, 1f), size.height),
                            )
                        },
            )
        }
    }
}

/**
 * A countdown that starts at [remainingMillis] / [totalMillis] and animates linearly to 0 over [remainingMillis].
 * Restarts when [key] changes (a new Undo window), so a rotation resumes from the time actually left.
 */
@Composable
fun rememberCountdownFraction(
    key: Any,
    remainingMillis: Long,
    totalMillis: Long,
): () -> Float {
    val start = (remainingMillis.toFloat() / totalMillis.coerceAtLeast(1)).coerceIn(0f, 1f)
    val animatable = remember(key) { Animatable(start) }
    LaunchedEffect(key) {
        animatable.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = remainingMillis.coerceAtLeast(0).toInt(), easing = LinearEasing),
        )
    }
    return { animatable.value }
}

@Composable
private fun UndoSnackbarSample(fraction: Float) {
    Surface(color = Color.Transparent) {
        UndoSnackbar(
            message = "Jane Doe deleted",
            actionLabel = "Undo",
            onAction = {},
            countdownFraction = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun UndoSnackbarFullLightPreview() {
    SliideTheme(darkTheme = false) { UndoSnackbarSample(fraction = 1f) }
}

@Preview
@Composable
private fun UndoSnackbarHalfLightPreview() {
    SliideTheme(darkTheme = false) { UndoSnackbarSample(fraction = 0.5f) }
}

@Preview
@Composable
private fun UndoSnackbarFullDarkPreview() {
    SliideTheme(darkTheme = true) { UndoSnackbarSample(fraction = 1f) }
}

@Preview
@Composable
private fun UndoSnackbarHalfDarkPreview() {
    SliideTheme(darkTheme = true) { UndoSnackbarSample(fraction = 0.5f) }
}
