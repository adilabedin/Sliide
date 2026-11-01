package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.core.designsystem.theme.extendedColors
import kotlin.math.absoluteValue

/** Whether the avatar shows a status dot, and which one. */
enum class AvatarStatus {
    NONE,
    ACTIVE,
    INACTIVE,
}

/**
 * Initials on one of the theme's muted tones. The tone is picked deterministically from [toneSeed], so a user keeps
 * the same colour across launches. Decorative: the name next to it carries the meaning, so it has no semantics.
 */
@Composable
fun UserAvatar(
    initials: String,
    toneSeed: Long,
    modifier: Modifier = Modifier,
    status: AvatarStatus = AvatarStatus.NONE,
    size: Dp = 44.dp,
) {
    val tones = MaterialTheme.extendedColors.avatarTones
    val tone = tones[(toneSeed % tones.size).toInt().absoluteValue]
    Box(modifier = modifier.size(size).clearAndSetSemantics {}) {
        Box(
            modifier =
                Modifier
                    .size(size)
                    .background(tone.container, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                color = tone.content,
                style =
                    if (size >= 64.dp) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (status != AvatarStatus.NONE) {
            val dotSize = size / 4
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(dotSize)
                        .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape)
                        .padding(2.dp)
                        .background(
                            color =
                                if (status == AvatarStatus.ACTIVE) {
                                    MaterialTheme.extendedColors.positive
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            shape = CircleShape,
                        ),
            )
        }
    }
}

@Composable
private fun AvatarRow() {
    Surface {
        Row(
            modifier = Modifier.padding(SliideSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(SliideSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            (0L..5L).forEach { seed -> UserAvatar(initials = "JD", toneSeed = seed) }
        }
    }
}

@Composable
private fun AvatarStatuses() {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.padding(SliideSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(SliideSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UserAvatar(initials = "JD", toneSeed = 1, status = AvatarStatus.ACTIVE)
            UserAvatar(initials = "RS", toneSeed = 2, status = AvatarStatus.INACTIVE)
            UserAvatar(initials = "AL", toneSeed = 3, status = AvatarStatus.ACTIVE, size = 88.dp)
        }
    }
}

@Preview
@Composable
private fun UserAvatarTonesLightPreview() {
    SliideTheme(darkTheme = false) { AvatarRow() }
}

@Preview
@Composable
private fun UserAvatarTonesDarkPreview() {
    SliideTheme(darkTheme = true) { AvatarRow() }
}

@Preview
@Composable
private fun UserAvatarStatusLightPreview() {
    SliideTheme(darkTheme = false) { AvatarStatuses() }
}

@Preview
@Composable
private fun UserAvatarStatusDarkPreview() {
    SliideTheme(darkTheme = true) { AvatarStatuses() }
}
