package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The single app theme. Colours are defined in shared code (no Android dynamic colour) so Android
 * and iOS render identical values.
 */
@Composable
fun SliideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = SliideTypography,
        content = content,
    )
}
