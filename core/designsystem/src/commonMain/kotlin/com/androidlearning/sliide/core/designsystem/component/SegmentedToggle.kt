package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideMotion
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.core.designsystem.theme.extendedColors

/**
 * A two-option toggle: exactly one of [first] and [second] is selected, and a pill slides to the selected one.
 * With [readOnly] the toggle shows its value but ignores taps (the profile's View mode). Exposed to accessibility as a
 * radio group.
 */
@Composable
fun <T> SegmentedToggle(
    first: T,
    second: T,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val isSecond = selected == second
    Surface(
        modifier = modifier.height(48.dp),
        shape = SliideShapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        BoxWithConstraints(modifier = Modifier.padding(SliideSpacing.xs)) {
            val segmentWidth = maxWidth / 2
            val pillOffset by animateDpAsState(
                targetValue = if (isSecond) segmentWidth else 0.dp,
                animationSpec = tween(SliideMotion.MEDIUM_MILLIS, easing = SliideMotion.Emphasized),
                label = "togglePill",
            )
            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(pillOffset.roundToPx(), 0) }
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .shadow(if (readOnly) 0.dp else 1.dp, SliideShapes.small)
                        .background(
                            if (readOnly) {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            } else {
                                MaterialTheme.extendedColors.toggleSelected
                            },
                            SliideShapes.small,
                        ),
            )
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight().selectableGroup()) {
                listOf(first, second).forEach { option ->
                    val isSelected = option == selected
                    val textColor by animateColorAsState(
                        targetValue =
                            if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        animationSpec = tween(SliideMotion.MEDIUM_MILLIS),
                        label = "toggleText",
                    )
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(
                                    selected = isSelected,
                                    enabled = !readOnly,
                                    role = Role.RadioButton,
                                    onClick = { if (!isSelected) onSelect(option) },
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label(option),
                            color = textColor,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSample(
    selected: String,
    readOnly: Boolean,
) {
    Surface {
        Column(modifier = Modifier.padding(SliideSpacing.l)) {
            SegmentedToggle(
                first = "Male",
                second = "Female",
                selected = selected,
                onSelect = {},
                label = { it },
                readOnly = readOnly,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SegmentedToggleFirstLightPreview() {
    SliideTheme(darkTheme = false) { ToggleSample(selected = "Male", readOnly = false) }
}

@Preview
@Composable
private fun SegmentedToggleSecondLightPreview() {
    SliideTheme(darkTheme = false) { ToggleSample(selected = "Female", readOnly = false) }
}

@Preview
@Composable
private fun SegmentedToggleReadOnlyLightPreview() {
    SliideTheme(darkTheme = false) { ToggleSample(selected = "Male", readOnly = true) }
}

@Preview
@Composable
private fun SegmentedToggleFirstDarkPreview() {
    SliideTheme(darkTheme = true) { ToggleSample(selected = "Male", readOnly = false) }
}

@Preview
@Composable
private fun SegmentedToggleSecondDarkPreview() {
    SliideTheme(darkTheme = true) { ToggleSample(selected = "Female", readOnly = false) }
}

@Preview
@Composable
private fun SegmentedToggleReadOnlyDarkPreview() {
    SliideTheme(darkTheme = true) { ToggleSample(selected = "Male", readOnly = true) }
}
