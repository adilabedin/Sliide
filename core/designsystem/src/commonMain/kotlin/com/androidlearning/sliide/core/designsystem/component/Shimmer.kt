package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideMotion
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.core.designsystem.theme.extendedColors

/**
 * Paints [shape] with a slow, low-contrast highlight sweeping left to right every [SliideMotion.SHIMMER_MILLIS].
 * The animated progress is only read in the draw phase, so the sweep never recomposes.
 */
fun Modifier.shimmer(shape: Shape = SliideShapes.small): Modifier =
    composed {
        val base = MaterialTheme.extendedColors.shimmerBase
        val highlight = MaterialTheme.extendedColors.shimmerHighlight
        val transition = rememberInfiniteTransition(label = "shimmer")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(SliideMotion.SHIMMER_MILLIS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "shimmerProgress",
        )
        drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val band = size.width.coerceAtLeast(1f)
            onDrawBehind {
                val start = -band + progress * (size.width + band) * 2f
                drawOutline(
                    outline = outline,
                    brush =
                        Brush.linearGradient(
                            colors = listOf(base, highlight, base),
                            start = Offset(start, 0f),
                            end = Offset(start + band, size.height),
                        ),
                )
            }
        }
    }

/**
 * A placeholder shaped like a user card (avatar, name and email lines, time line), shimmering while the feed loads.
 * Purely visual: callers hide it from accessibility and announce loading once.
 */
@Composable
fun UserCardPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = SliideShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(SliideSpacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(44.dp).shimmer(CircleShape))
            Spacer(modifier = Modifier.size(SliideSpacing.m))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SliideSpacing.s),
            ) {
                Box(modifier = Modifier.fillMaxWidth(0.55f).height(14.dp).shimmer())
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp).shimmer())
                Box(modifier = Modifier.fillMaxWidth(0.35f).height(10.dp).shimmer())
            }
        }
    }
}

@Composable
private fun PlaceholderSample() {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(SliideSpacing.l),
        verticalArrangement = Arrangement.spacedBy(SliideSpacing.m),
    ) {
        repeat(3) { UserCardPlaceholder(modifier = Modifier.fillMaxWidth()) }
    }
}

@Preview
@Composable
private fun UserCardPlaceholderLightPreview() {
    SliideTheme(darkTheme = false) { PlaceholderSample() }
}

@Preview
@Composable
private fun UserCardPlaceholderDarkPreview() {
    SliideTheme(darkTheme = true) { PlaceholderSample() }
}
