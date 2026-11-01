package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radii shared by every component: 8 / 14 / 20 / 28 dp. */
object SliideShapes {
    val small = RoundedCornerShape(8.dp)
    val medium = RoundedCornerShape(14.dp)
    val large = RoundedCornerShape(20.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/** Spacing scale used for paddings and gaps. */
object SliideSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Shared animation durations and easing so motion feels consistent across components. */
object SliideMotion {
    const val SHORT_MILLIS = 120
    const val MEDIUM_MILLIS = 240
    const val SHIMMER_MILLIS = 1_200
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Scale a pressed card shrinks to. */
    const val PRESSED_SCALE = 0.98f
}

internal val SliideMaterialShapes =
    Shapes(
        extraSmall = SliideShapes.small,
        small = SliideShapes.small,
        medium = SliideShapes.medium,
        large = SliideShapes.large,
        extraLarge = SliideShapes.extraLarge,
    )
