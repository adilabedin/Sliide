package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Palette: deep ink neutrals, a midnight primary, one restrained champagne accent, and a refined red reserved for
// destructive actions. Light and dark share the same hues; every pairing used in the app is checked by ContrastTest.

// Ink neutrals (dark side)
private val Ink950 = Color(0xFF0A0B0E)
private val Ink900 = Color(0xFF0D0E12)
private val Ink850 = Color(0xFF16181D)
private val Ink800 = Color(0xFF1B1D23)
private val Ink750 = Color(0xFF22252C)
private val Ink700 = Color(0xFF2A2D35)
private val Ink650 = Color(0xFF262930)
private val Ink600 = Color(0xFF23262D)
private val InkText = Color(0xFF15171C)
private val InkSecondary = Color(0xFF555A64)

// Porcelain neutrals (light side)
private val Porcelain = Color(0xFFF6F6F3)
private val Porcelain200 = Color(0xFFEFEFEB)
private val Porcelain300 = Color(0xFFE9E9E4)
private val Hairline = Color(0xFFE3E3DD)
private val Mist = Color(0xFFECECEF)
private val MistSecondary = Color(0xFFA3A7B2)
private val MistInverse = Color(0xFFE6E6EA)

// Midnight primary
private val Midnight = Color(0xFF1E2A4A)
private val MidnightDeep = Color(0xFF0F1730)
private val MidnightContainer = Color(0xFFDDE2F1)
private val MidnightDark = Color(0xFF2A3762)
private val Periwinkle = Color(0xFFB8C4F0)
private val PeriwinkleOn = Color(0xFF14203F)

// Slate secondary
private val Slate = Color(0xFF4A5468)
private val SlateContainer = Color(0xFFE4E7EE)
private val SlateDeep = Color(0xFF1A2130)
private val SlateLight = Color(0xFFBCC3D3)
private val SlateOn = Color(0xFF262D3B)
private val SlateDarkContainer = Color(0xFF323949)
private val SlateDarkOnContainer = Color(0xFFDDE2EE)

// Champagne accent
private val Champagne = Color(0xFF8A6A2C)
private val ChampagneDeep = Color(0xFF7A5C22)
private val ChampagneLight = Color(0xFFD9BF86)
private val ChampagneContainer = Color(0xFFF3E7CC)
private val ChampagneOnContainer = Color(0xFF2E2208)
private val ChampagneOn = Color(0xFF3A2C0A)
private val ChampagneDarkContainer = Color(0xFF54431C)

// Refined red
private val Crimson = Color(0xFFB3261E)
private val CrimsonDark = Color(0xFFB02A22)
private val CrimsonContainer = Color(0xFFF9DEDC)
private val CrimsonOnContainer = Color(0xFF410E0B)
private val Coral = Color(0xFFFF9C94)
private val CoralOn = Color(0xFF5C0B07)
private val CrimsonDarkContainer = Color(0xFF8C1D18)
private val CoralContainerOn = Color(0xFFFFDAD6)

internal val LightColorScheme =
    lightColorScheme(
        primary = Midnight,
        onPrimary = Color.White,
        primaryContainer = MidnightContainer,
        onPrimaryContainer = MidnightDeep,
        secondary = Slate,
        onSecondary = Color.White,
        secondaryContainer = SlateContainer,
        onSecondaryContainer = SlateDeep,
        tertiary = Champagne,
        onTertiary = Color.White,
        tertiaryContainer = ChampagneContainer,
        onTertiaryContainer = ChampagneOnContainer,
        error = Crimson,
        onError = Color.White,
        errorContainer = CrimsonContainer,
        onErrorContainer = CrimsonOnContainer,
        background = Porcelain,
        onBackground = InkText,
        surface = Porcelain,
        onSurface = InkText,
        surfaceVariant = Hairline,
        onSurfaceVariant = InkSecondary,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White,
        surfaceContainer = Porcelain200,
        surfaceContainerHigh = Porcelain300,
        surfaceContainerHighest = Hairline,
        outline = Color(0xFF8A8E98),
        outlineVariant = Hairline,
        inverseSurface = Ink600,
        inverseOnSurface = Color(0xFFF1F1EE),
        inversePrimary = Periwinkle,
        scrim = Color.Black,
    )

internal val DarkColorScheme =
    darkColorScheme(
        primary = Periwinkle,
        onPrimary = PeriwinkleOn,
        primaryContainer = MidnightDark,
        onPrimaryContainer = MidnightContainer,
        secondary = SlateLight,
        onSecondary = SlateOn,
        secondaryContainer = SlateDarkContainer,
        onSecondaryContainer = SlateDarkOnContainer,
        tertiary = ChampagneLight,
        onTertiary = ChampagneOn,
        tertiaryContainer = ChampagneDarkContainer,
        onTertiaryContainer = ChampagneContainer,
        error = Coral,
        onError = CoralOn,
        errorContainer = CrimsonDarkContainer,
        onErrorContainer = CoralContainerOn,
        background = Ink900,
        onBackground = Mist,
        surface = Ink900,
        onSurface = Mist,
        surfaceVariant = Ink700,
        onSurfaceVariant = MistSecondary,
        surfaceContainerLowest = Ink950,
        surfaceContainerLow = Ink850,
        surfaceContainer = Ink800,
        surfaceContainerHigh = Ink750,
        surfaceContainerHighest = Ink700,
        outline = Color(0xFF7A7F8C),
        outlineVariant = Ink650,
        inverseSurface = MistInverse,
        inverseOnSurface = Ink800,
        inversePrimary = Midnight,
        scrim = Color.Black,
    )

/** One muted avatar tone: a tonal [container] behind the initials and the [content] colour of the initials. */
@Immutable
data class AvatarTone(
    val container: Color,
    val content: Color,
)

/**
 * Colours Material 3's scheme has no slot for. [destructive] is the filled red of destructive buttons (white label in
 * both modes); [inverseAccent] is the accent on [androidx.compose.material3.ColorScheme.inverseSurface] (the Undo action).
 */
@Immutable
data class SliideExtendedColors(
    val destructive: Color,
    val onDestructive: Color,
    val inverseAccent: Color,
    val positive: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,
    val toggleSelected: Color,
    val avatarTones: List<AvatarTone>,
)

internal val LightExtendedColors =
    SliideExtendedColors(
        destructive = Crimson,
        onDestructive = Color.White,
        inverseAccent = ChampagneLight,
        positive = Color(0xFF2E8B5E),
        shimmerBase = Porcelain200,
        shimmerHighlight = Color.White,
        toggleSelected = Color.White,
        avatarTones =
            listOf(
                AvatarTone(container = MidnightContainer, content = MidnightDeep),
                AvatarTone(container = ChampagneContainer, content = ChampagneOnContainer),
                AvatarTone(container = Color(0xFFDCEBE3), content = Color(0xFF0F3424)),
                AvatarTone(container = Color(0xFFEFDDE3), content = Color(0xFF3E1424)),
                AvatarTone(container = SlateContainer, content = SlateDeep),
                AvatarTone(container = Color(0xFFDDE9EE), content = Color(0xFF0E2F3B)),
            ),
    )

internal val DarkExtendedColors =
    SliideExtendedColors(
        destructive = CrimsonDark,
        onDestructive = Color.White,
        inverseAccent = ChampagneDeep,
        positive = Color(0xFF5BC795),
        shimmerBase = Ink800,
        shimmerHighlight = Ink700,
        toggleSelected = Color(0xFF353943),
        avatarTones =
            listOf(
                AvatarTone(container = MidnightDark, content = MidnightContainer),
                AvatarTone(container = ChampagneDarkContainer, content = ChampagneContainer),
                AvatarTone(container = Color(0xFF1F3D30), content = Color(0xFFCDE9DA)),
                AvatarTone(container = Color(0xFF45222F), content = Color(0xFFF2D6E0)),
                AvatarTone(container = SlateDarkContainer, content = SlateDarkOnContainer),
                AvatarTone(container = Color(0xFF1C3A46), content = Color(0xFFCFE6EF)),
            ),
    )
