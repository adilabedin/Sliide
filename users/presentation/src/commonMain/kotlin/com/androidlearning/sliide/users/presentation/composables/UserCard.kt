package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.androidlearning.sliide.core.designsystem.component.AvatarStatus
import com.androidlearning.sliide.core.designsystem.component.SliideCard
import com.androidlearning.sliide.core.designsystem.component.UserAvatar
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.users_delete_action
import com.androidlearning.sliide.users.presentation.generated.resources.users_open_profile
import com.androidlearning.sliide.users.presentation.generated.resources.users_pending_sync
import com.androidlearning.sliide.users.presentation.users.UserUi
import com.androidlearning.sliide.users.presentation.util.asAddedText
import org.jetbrains.compose.resources.stringResource

/**
 * One user in the feed: avatar, name, email, relative added time and, for users not yet on the server, a "Pending
 * sync" chip. Tap opens the profile; long-press (with haptics) or the "Delete" accessibility action asks to delete.
 */
@Composable
internal fun UserCard(
    user: UserUi,
    selected: Boolean,
    onClick: () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val deleteLabel = stringResource(Res.string.users_delete_action)
    SliideCard(
        onClick = onClick,
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onDeleteRequest()
        },
        selected = selected,
        onClickLabel = stringResource(Res.string.users_open_profile),
        onLongClickLabel = deleteLabel,
        modifier =
            modifier.semantics {
                // Only the highlighted card says so; unselected cards stay silent rather than "not selected".
                if (selected) this.selected = true
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            onDeleteRequest()
                            true
                        },
                    )
            },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(
                initials = user.initials,
                toneSeed = user.id,
                status = if (user.isActive) AvatarStatus.ACTIVE else AvatarStatus.INACTIVE,
            )
            Spacer(modifier = Modifier.size(SliideSpacing.m))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SliideSpacing.xxs)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = SliideSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SliideSpacing.s),
                ) {
                    Text(
                        text = user.addedAgo.asAddedText(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    if (user.isPendingSync) PendingSyncChip()
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun PendingSyncChip(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.users_pending_sync),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 1,
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.tertiaryContainer, SliideShapes.pill)
                .padding(horizontal = SliideSpacing.s, vertical = SliideSpacing.xxs),
    )
}

@Composable
private fun CardPreview(
    user: UserUi,
    selected: Boolean = false,
) {
    Surface {
        UserCard(
            user = user,
            selected = selected,
            onClick = {},
            onDeleteRequest = {},
            modifier = Modifier.fillMaxWidth().padding(SliideSpacing.l),
        )
    }
}

@Preview
@Composable
private fun UserCardLightPreview() {
    SliideTheme(darkTheme = false) { CardPreview(previewUsers[0]) }
}

@Preview
@Composable
private fun UserCardDarkPreview() {
    SliideTheme(darkTheme = true) { CardPreview(previewUsers[0]) }
}

@Preview
@Composable
private fun UserCardPendingSyncLightPreview() {
    SliideTheme(darkTheme = false) { CardPreview(previewUsers[2]) }
}

@Preview
@Composable
private fun UserCardPendingSyncDarkPreview() {
    SliideTheme(darkTheme = true) { CardPreview(previewUsers[2]) }
}

@Preview
@Composable
private fun UserCardSelectedLightPreview() {
    SliideTheme(darkTheme = false) { CardPreview(previewUsers[1], selected = true) }
}

@Preview
@Composable
private fun UserCardSelectedDarkPreview() {
    SliideTheme(darkTheme = true) { CardPreview(previewUsers[1], selected = true) }
}

@Preview(widthDp = 320)
@Composable
private fun UserCardLongTextCompactPreview() {
    SliideTheme(darkTheme = false) { CardPreview(previewUsers[3]) }
}
