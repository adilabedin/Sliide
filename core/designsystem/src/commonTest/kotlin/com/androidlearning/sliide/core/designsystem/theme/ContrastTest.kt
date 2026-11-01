package com.androidlearning.sliide.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.fail

/** WCAG 2.1 AA for every foreground/background pairing the app renders, in light and dark. */
class ContrastTest {
    private data class Pair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
    )

    private fun pairs(
        scheme: ColorScheme,
        extended: SliideExtendedColors,
    ): List<Pair> {
        val card = scheme.surfaceContainerLow
        val text =
            listOf(
                Pair("onSurface / surface", scheme.onSurface, scheme.surface, TEXT),
                Pair("onSurface / card", scheme.onSurface, card, TEXT),
                Pair("onSurfaceVariant / surface", scheme.onSurfaceVariant, scheme.surface, TEXT),
                Pair("onSurfaceVariant / card", scheme.onSurfaceVariant, card, TEXT),
                Pair("primary / surface", scheme.primary, scheme.surface, TEXT),
                Pair("primary / card", scheme.primary, card, TEXT),
                Pair("onPrimary / primary", scheme.onPrimary, scheme.primary, TEXT),
                Pair("onPrimaryContainer / primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, TEXT),
                Pair("onSecondaryContainer / secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, TEXT),
                Pair("onTertiaryContainer / tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, TEXT),
                Pair("onSurface / surfaceContainerHigh", scheme.onSurface, scheme.surfaceContainerHigh, TEXT),
                Pair("error / surface", scheme.error, scheme.surface, TEXT),
                Pair("error / card", scheme.error, card, TEXT),
                Pair("onErrorContainer / errorContainer", scheme.onErrorContainer, scheme.errorContainer, TEXT),
                Pair("inverseOnSurface / inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface, TEXT),
                Pair("inverseAccent / inverseSurface", extended.inverseAccent, scheme.inverseSurface, TEXT),
                Pair("onDestructive / destructive", extended.onDestructive, extended.destructive, TEXT),
                Pair("onSurface / toggleSelected", scheme.onSurface, extended.toggleSelected, TEXT),
            )
        val avatars =
            extended.avatarTones.mapIndexed { index, tone ->
                Pair("avatar[$index]", tone.content, tone.container, TEXT)
            }
        val graphics =
            listOf(
                Pair("outline / card", scheme.outline, card, GRAPHIC),
                Pair("outline / surface", scheme.outline, scheme.surface, GRAPHIC),
                Pair("positive / card", extended.positive, card, GRAPHIC),
                Pair("tertiary / card", scheme.tertiary, card, GRAPHIC),
                Pair("primary / surfaceContainerHigh", scheme.primary, scheme.surfaceContainerHigh, GRAPHIC),
            )
        return text + avatars + graphics
    }

    @Test
    fun lightThemeMeetsAa() = assertAll("light", pairs(LightColorScheme, LightExtendedColors))

    @Test
    fun darkThemeMeetsAa() = assertAll("dark", pairs(DarkColorScheme, DarkExtendedColors))

    @Test
    fun contrastRatioMatchesKnownValues() {
        val blackOnWhite = contrast(Color.Black, Color.White)
        if (blackOnWhite !in 20.9..21.1) fail("black/white should be 21:1, was $blackOnWhite")
    }

    private fun assertAll(
        mode: String,
        pairs: List<Pair>,
    ) {
        val failures =
            pairs.mapNotNull { pair ->
                val ratio = contrast(pair.foreground, pair.background)
                if (ratio < pair.minimum) "${pair.name}: ${round2(ratio)} < ${pair.minimum}" else null
            }
        if (failures.isNotEmpty()) fail("$mode theme contrast failures:\n" + failures.joinToString("\n"))
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100) / 100

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0
    }
}
