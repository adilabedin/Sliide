package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Indigo10 = Color(0xFF0E0F5C)
private val Indigo20 = Color(0xFF242692)
private val Indigo30 = Color(0xFF3B3FB0)
private val Indigo40 = Color(0xFF5257CC)
private val Indigo80 = Color(0xFFC0C1FF)
private val Indigo90 = Color(0xFFE1E0FF)

private val Teal10 = Color(0xFF002020)
private val Teal20 = Color(0xFF003737)
private val Teal30 = Color(0xFF004F4F)
private val Teal40 = Color(0xFF006A6A)
private val Teal80 = Color(0xFF4CDADA)
private val Teal90 = Color(0xFF6FF7F6)

private val Coral10 = Color(0xFF3A0B00)
private val Coral20 = Color(0xFF5E1700)
private val Coral30 = Color(0xFF852400)
private val Coral40 = Color(0xFFAD3300)
private val Coral80 = Color(0xFFFFB59C)
private val Coral90 = Color(0xFFFFDBD0)

private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

private val Neutral6 = Color(0xFF131318)
private val Neutral10 = Color(0xFF1B1B21)
private val Neutral12 = Color(0xFF1F1F25)
private val Neutral17 = Color(0xFF2A292F)
private val Neutral22 = Color(0xFF35343A)
private val Neutral90 = Color(0xFFE4E1E9)
private val Neutral94 = Color(0xFFEFEDF4)
private val Neutral96 = Color(0xFFF5F2FA)
private val Neutral98 = Color(0xFFFCF8FF)

private val NeutralVariant30 = Color(0xFF46464F)
private val NeutralVariant50 = Color(0xFF777680)
private val NeutralVariant60 = Color(0xFF918F9A)
private val NeutralVariant80 = Color(0xFFC7C5D0)
private val NeutralVariant90 = Color(0xFFE3E1EC)

internal val LightColorScheme =
    lightColorScheme(
        primary = Indigo40,
        onPrimary = Color.White,
        primaryContainer = Indigo90,
        onPrimaryContainer = Indigo10,
        secondary = Teal40,
        onSecondary = Color.White,
        secondaryContainer = Teal90,
        onSecondaryContainer = Teal10,
        tertiary = Coral40,
        onTertiary = Color.White,
        tertiaryContainer = Coral90,
        onTertiaryContainer = Coral10,
        error = Red40,
        onError = Color.White,
        errorContainer = Red90,
        onErrorContainer = Red10,
        background = Neutral98,
        onBackground = Neutral10,
        surface = Neutral98,
        onSurface = Neutral10,
        surfaceVariant = NeutralVariant90,
        onSurfaceVariant = NeutralVariant30,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Neutral96,
        surfaceContainer = Neutral94,
        surfaceContainerHigh = Neutral90,
        surfaceContainerHighest = NeutralVariant90,
        outline = NeutralVariant50,
        outlineVariant = NeutralVariant80,
        inverseSurface = Neutral17,
        inverseOnSurface = Neutral94,
        inversePrimary = Indigo80,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = Indigo80,
        onPrimary = Indigo20,
        primaryContainer = Indigo30,
        onPrimaryContainer = Indigo90,
        secondary = Teal80,
        onSecondary = Teal20,
        secondaryContainer = Teal30,
        onSecondaryContainer = Teal90,
        tertiary = Coral80,
        onTertiary = Coral20,
        tertiaryContainer = Coral30,
        onTertiaryContainer = Coral90,
        error = Red80,
        onError = Red20,
        errorContainer = Red30,
        onErrorContainer = Red90,
        background = Neutral6,
        onBackground = Neutral90,
        surface = Neutral6,
        onSurface = Neutral90,
        surfaceVariant = NeutralVariant30,
        onSurfaceVariant = NeutralVariant80,
        surfaceContainerLowest = Color(0xFF0E0E13),
        surfaceContainerLow = Neutral10,
        surfaceContainer = Neutral12,
        surfaceContainerHigh = Neutral17,
        surfaceContainerHighest = Neutral22,
        outline = NeutralVariant60,
        outlineVariant = NeutralVariant30,
        inverseSurface = Neutral90,
        inverseOnSurface = Neutral17,
        inversePrimary = Indigo40,
    )
