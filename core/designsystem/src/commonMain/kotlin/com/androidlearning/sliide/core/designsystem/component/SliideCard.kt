package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideMotion
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme

/**
 * The app's card surface: a tonal container separated from the background by a hairline, not a shadow. It scales down
 * slightly while pressed and supports tap and long-press. [selected] tints it and draws an accent outline (two-pane
 * selection).
 * The selected tint is neutral on purpose, so no avatar tone can blend into it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SliideCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) SliideMotion.PRESSED_SCALE else 1f,
        animationSpec = tween(SliideMotion.SHORT_MILLIS, easing = SliideMotion.Emphasized),
        label = "cardScale",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(SliideMotion.MEDIUM_MILLIS),
        label = "cardContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(SliideMotion.MEDIUM_MILLIS),
        label = "cardBorder",
    )
    Surface(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(SliideShapes.large)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClickLabel = onClickLabel,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ),
        shape = SliideShapes.large,
        color = containerColor,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, borderColor),
    ) {
        Column(modifier = Modifier.padding(SliideSpacing.l), content = content)
    }
}

@Composable
private fun CardSample(selected: Boolean) {
    SliideCard(onClick = {}, selected = selected, modifier = Modifier.fillMaxWidth().padding(SliideSpacing.l)) {
        Text("Jane Doe", style = MaterialTheme.typography.titleMedium)
        Text(
            "jane.doe@example.com",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview
@Composable
private fun SliideCardLightPreview() {
    SliideTheme(darkTheme = false) { Surface { CardSample(selected = false) } }
}

@Preview
@Composable
private fun SliideCardDarkPreview() {
    SliideTheme(darkTheme = true) { Surface { CardSample(selected = false) } }
}

@Preview
@Composable
private fun SliideCardSelectedLightPreview() {
    SliideTheme(darkTheme = false) { Surface { CardSample(selected = true) } }
}

@Preview
@Composable
private fun SliideCardSelectedDarkPreview() {
    SliideTheme(darkTheme = true) { Surface { CardSample(selected = true) } }
}
