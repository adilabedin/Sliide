package com.androidlearning.sliide.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideShapes
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme

/**
 * A slim, dismissible banner above content that is still shown (e.g. "No internet connection" over cached users).
 * Announced politely by screen readers when it appears.
 */
@Composable
fun ErrorBanner(
    message: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = SliideShapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = SliideSpacing.l, top = SliideSpacing.xs, bottom = SliideSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(SliideSpacing.m))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = dismissLabel)
            }
        }
    }
}

/**
 * A centred full-area message: an icon in a tonal circle, a [title], a [body] and an optional action. Used for empty
 * states, full-screen errors and the "select a user" detail placeholder.
 */
@Composable
fun FullScreenMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = Icons.Filled.Refresh,
) {
    Box(modifier = modifier.fillMaxSize().padding(SliideSpacing.xxl), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SliideSpacing.m),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.size(SliideSpacing.xs))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.size(SliideSpacing.xs))
                FilledTonalButton(onClick = onAction, shape = SliideShapes.medium) {
                    if (actionIcon != null) {
                        Icon(imageVector = actionIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(SliideSpacing.s))
                    }
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun BannerSample() {
    Surface {
        ErrorBanner(
            message = "No internet connection",
            dismissLabel = "Dismiss",
            onDismiss = {},
            modifier = Modifier.fillMaxWidth().padding(SliideSpacing.l),
        )
    }
}

@Composable
private fun ErrorMessageSample() {
    Surface {
        FullScreenMessage(
            icon = Icons.Filled.Warning,
            title = "No internet connection",
            body = "Check your connection and try again.",
            actionLabel = "Retry",
            onAction = {},
        )
    }
}

@Composable
private fun EmptyMessageSample() {
    Surface {
        FullScreenMessage(
            icon = Icons.Filled.Person,
            title = "No users yet",
            body = "Tap Add user to create the first one.",
        )
    }
}

@Preview
@Composable
private fun ErrorBannerLightPreview() {
    SliideTheme(darkTheme = false) { BannerSample() }
}

@Preview
@Composable
private fun ErrorBannerDarkPreview() {
    SliideTheme(darkTheme = true) { BannerSample() }
}

@Preview(heightDp = 480)
@Composable
private fun FullScreenMessageErrorLightPreview() {
    SliideTheme(darkTheme = false) { ErrorMessageSample() }
}

@Preview(heightDp = 480)
@Composable
private fun FullScreenMessageErrorDarkPreview() {
    SliideTheme(darkTheme = true) { ErrorMessageSample() }
}

@Preview(heightDp = 480)
@Composable
private fun FullScreenMessageEmptyLightPreview() {
    SliideTheme(darkTheme = false) { EmptyMessageSample() }
}

@Preview(heightDp = 480)
@Composable
private fun FullScreenMessageEmptyDarkPreview() {
    SliideTheme(darkTheme = true) { EmptyMessageSample() }
}
