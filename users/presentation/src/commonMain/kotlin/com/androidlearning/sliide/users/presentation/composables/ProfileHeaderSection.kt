package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.component.UserAvatar
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.users_pending_sync
import com.androidlearning.sliide.users.presentation.profile.ProfileHeader
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.asAddedText
import org.jetbrains.compose.resources.stringResource

/** The profile's avatar, name, added time and, for unsynced users, the "Pending sync" chip. */
@Composable
internal fun ProfileHeaderSection(
    header: ProfileHeader,
    name: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SliideSpacing.s),
    ) {
        UserAvatar(initials = header.initials, toneSeed = header.userId, size = 88.dp)
        Spacer(modifier = Modifier.size(SliideSpacing.xs))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = header.addedAgo.asAddedText(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (header.isPendingSync) {
            Text(
                text = stringResource(Res.string.users_pending_sync),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier =
                    Modifier
                        .background(MaterialTheme.colorScheme.tertiaryContainer, SliideShapes.pill)
                        .padding(horizontal = SliideSpacing.s, vertical = SliideSpacing.xxs),
            )
        }
    }
}

// region Previews

private val syncedHeader = ProfileHeader(userId = 7_401_001, initials = "JD", addedAgo = RelativeTime.Minutes(5), isPendingSync = false)
private val pendingHeader = ProfileHeader(userId = -1, initials = "AL", addedAgo = RelativeTime.JustNow, isPendingSync = true)

@Composable
private fun HeaderSample(
    darkTheme: Boolean,
    header: ProfileHeader,
    name: String,
) {
    SliideTheme(darkTheme = darkTheme) {
        Surface { ProfileHeaderSection(header = header, name = name, modifier = Modifier.padding(SliideSpacing.l)) }
    }
}

@Preview
@Composable
private fun ProfileHeaderSyncedLightPreview() {
    HeaderSample(darkTheme = false, header = syncedHeader, name = "Jane Doe")
}

@Preview
@Composable
private fun ProfileHeaderSyncedDarkPreview() {
    HeaderSample(darkTheme = true, header = syncedHeader, name = "Jane Doe")
}

@Preview
@Composable
private fun ProfileHeaderPendingSyncLightPreview() {
    HeaderSample(darkTheme = false, header = pendingHeader, name = "Amélie Laurent")
}

@Preview
@Composable
private fun ProfileHeaderPendingSyncDarkPreview() {
    HeaderSample(darkTheme = true, header = pendingHeader, name = "Amélie Laurent")
}

// endregion
