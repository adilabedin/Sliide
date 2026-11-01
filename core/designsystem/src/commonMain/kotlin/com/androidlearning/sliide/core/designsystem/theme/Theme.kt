package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalSliideExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/**
 * The single app theme. Colours are defined in shared code (no Android dynamic colour) so Android
 * and iOS render identical values.
 */
@Composable
fun SliideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSliideExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = SliideTypography,
            shapes = SliideMaterialShapes,
            content = content,
        )
    }
}

/** Colours outside Material 3's scheme (destructive red, avatar tones, shimmer) for the current theme. */
val MaterialTheme.extendedColors: SliideExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSliideExtendedColors.current
