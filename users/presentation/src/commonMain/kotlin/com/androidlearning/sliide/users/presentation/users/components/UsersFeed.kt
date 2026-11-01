package com.androidlearning.sliide.users.presentation.users.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.component.UserCardPlaceholder
import com.androidlearning.sliide.core.designsystem.theme.SliideSpacing
import com.androidlearning.sliide.users.presentation.users.UserUi

/** Number of shimmering placeholder cards shown while the first load runs (spec: user-feed). */
const val PLACEHOLDER_COUNT = 20
const val PLACEHOLDER_TEST_TAG = "user_card_placeholder"

/** Bottom padding leaves room for the floating add button over the last card. */
private val FeedPadding =
    PaddingValues(start = SliideSpacing.l, end = SliideSpacing.l, top = SliideSpacing.s, bottom = 96.dp)

/**
 * The feed as a single column ([columns] = 1, a `LazyColumn`) or a two-column `LazyVerticalGrid`. Rows are keyed by
 * user id and animate in, out and between positions.
 */
@Composable
fun UsersFeed(
    users: List<UserUi>,
    columns: Int,
    selectedUserId: Long?,
    listState: LazyListState,
    gridState: LazyGridState,
    onUserClick: (Long) -> Unit,
    onDeleteRequest: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (columns == 1) {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
            contentPadding = FeedPadding,
            verticalArrangement = Arrangement.spacedBy(SliideSpacing.m),
        ) {
            items(items = users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    selected = user.id == selectedUserId,
                    onClick = { onUserClick(user.id) },
                    onDeleteRequest = { onDeleteRequest(user.id) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = modifier.fillMaxSize(),
            contentPadding = FeedPadding,
            verticalArrangement = Arrangement.spacedBy(SliideSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(SliideSpacing.m),
        ) {
            items(items = users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    selected = user.id == selectedUserId,
                    onClick = { onUserClick(user.id) },
                    onDeleteRequest = { onDeleteRequest(user.id) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
        }
    }
}

/**
 * [PLACEHOLDER_COUNT] shimmering cards, composed lazily like real rows. Hidden from accessibility individually; the
 * whole list is announced once with [loadingDescription].
 */
@Composable
fun UsersFeedPlaceholders(
    columns: Int,
    loadingDescription: String,
    modifier: Modifier = Modifier,
) {
    val containerModifier = modifier.fillMaxSize().semantics { contentDescription = loadingDescription }
    val itemModifier = Modifier.fillMaxWidth().clearAndSetSemantics { testTag = PLACEHOLDER_TEST_TAG }
    if (columns == 1) {
        LazyColumn(
            modifier = containerModifier,
            contentPadding = FeedPadding,
            verticalArrangement = Arrangement.spacedBy(SliideSpacing.m),
        ) {
            items(count = PLACEHOLDER_COUNT) { UserCardPlaceholder(modifier = itemModifier) }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = containerModifier,
            contentPadding = FeedPadding,
            verticalArrangement = Arrangement.spacedBy(SliideSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(SliideSpacing.m),
        ) {
            items(count = PLACEHOLDER_COUNT) { UserCardPlaceholder(modifier = itemModifier) }
        }
    }
}
