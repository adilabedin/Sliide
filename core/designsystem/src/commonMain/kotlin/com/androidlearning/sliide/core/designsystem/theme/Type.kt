package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

/** Material 3 type scale with slightly heavier titles for list headers and names. */
internal val SliideTypography =
    Base.copy(
        titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
