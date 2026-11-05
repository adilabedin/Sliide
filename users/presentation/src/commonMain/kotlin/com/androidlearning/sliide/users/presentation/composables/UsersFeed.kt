package com.androidlearning.sliide.users.presentation.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.component.UserCardPlaceholder
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.users.UserUi

/** Number of shimmering placeholder cards shown while the first load runs (spec: user-feed). */
const val PLACEHOLDER_COUNT = 20
const val PLACEHOLDER_TEST_TAG = "user_card_placeholder"

/** Bottom padding leaves room for the floating add button over the last card. */
private val FeedPadding =
    PaddingValues(start = SliideSpacing.l, end = SliideSpacing.l, top = SliideSpacing.s, bottom = 96.dp)

/**
 * The feed as a single `LazyColumn`. Rows are keyed by user id and animate in, out and between positions. Only visible
 * cards are composed.
 */
@Composable
internal fun UsersFeed(
    users: List<UserUi>,
    selectedUserId: Long?,
    listState: LazyListState,
    onUserClick: (Long) -> Unit,
    onDeleteRequest: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FeedLazyLayout(
        listState = listState,
        itemCount = users.size,
        key = { users[it].id },
        modifier = modifier,
    ) { index, itemModifier ->
        val user = users[index]
        UserCard(
            user = user,
            selected = user.id == selectedUserId,
            onClick = { onUserClick(user.id) },
            onDeleteRequest = { onDeleteRequest(user.id) },
            modifier = itemModifier.fillMaxWidth(),
        )
    }
}

/**
 * [PLACEHOLDER_COUNT] shimmering cards, composed lazily like real rows. Hidden from accessibility individually; the
 * whole list is announced once with [loadingDescription].
 */
@Composable
internal fun UsersFeedPlaceholders(
    loadingDescription: String,
    modifier: Modifier = Modifier,
) {
    FeedLazyLayout(
        listState = rememberLazyListState(),
        itemCount = PLACEHOLDER_COUNT,
        key = null,
        modifier = modifier.semantics { contentDescription = loadingDescription },
    ) { _, _ ->
        UserCardPlaceholder(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { testTag = PLACEHOLDER_TEST_TAG })
    }
}

/**
 * The one lazy container behind the feed and its placeholders: a `LazyColumn` with the shared padding and spacing. [item] receives the index and, when items are [key]ed, an
 * `animateItem()` modifier so keyed rows animate in, out and between positions.
 */
@Composable
private fun FeedLazyLayout(
    listState: LazyListState,
    itemCount: Int,
    key: ((Int) -> Any)?,
    modifier: Modifier = Modifier,
    item: @Composable (index: Int, itemModifier: Modifier) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = FeedPadding,
        verticalArrangement = Arrangement.spacedBy(SliideSpacing.m),
    ) {
        items(count = itemCount, key = key) { index ->
            item(index, if (key != null) Modifier.animateItem() else Modifier)
        }
    }
}

// region Previews

@Composable
private fun FeedSample(
    darkTheme: Boolean,
    loading: Boolean,
) {
    SliideTheme(darkTheme = darkTheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            if (loading) {
                UsersFeedPlaceholders(loadingDescription = "Loading users")
            } else {
                UsersFeed(
                    users = previewUsers,
                    selectedUserId = previewUsers[1].id,
                    listState = rememberLazyListState(),
                    onUserClick = {},
                    onDeleteRequest = {},
                )
            }
        }
    }
}

@Preview(widthDp = 320, heightDp = 640)
@Composable
private fun UsersFeedOneColumnLightPreview() {
    FeedSample(darkTheme = false, loading = false)
}

@Preview(widthDp = 320, heightDp = 640)
@Composable
private fun UsersFeedOneColumnDarkPreview() {
    FeedSample(darkTheme = true, loading = false)
}

@Preview(widthDp = 320, heightDp = 640)
@Composable
private fun UsersFeedPlaceholdersLightPreview() {
    FeedSample(darkTheme = false, loading = true)
}

// endregion
