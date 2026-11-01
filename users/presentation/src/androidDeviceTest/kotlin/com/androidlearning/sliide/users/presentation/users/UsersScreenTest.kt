package com.androidlearning.sliide.users.presentation.users

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.presentation.users.components.PLACEHOLDER_COUNT
import com.androidlearning.sliide.users.presentation.users.components.PLACEHOLDER_TEST_TAG
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/** Runs the stateless [UsersScreen] with fixed state; a tiny reducer applies the dialog actions like the ViewModel. */
class UsersScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val jane = UserUi(1, "Jane Doe", "jane.doe@example.com", "JD", true, RelativeTime.Minutes(5), false)
    private val rahul = UserUi(2, "Rahul Sharma", "rahul.sharma@example.in", "RS", false, RelativeTime.Hours(3), false)
    private val now = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private val actions = mutableListOf<UsersAction>()
    private val state = mutableStateOf(UsersState(users = listOf(jane, rahul), content = FeedContent.Users))

    private fun setContent() {
        composeRule.setContent {
            SliideTheme {
                UsersScreen(
                    state = state.value,
                    onAction = { action ->
                        actions += action
                        state.value =
                            when (action) {
                                is UsersAction.OnDeleteRequest ->
                                    state.value.copy(confirmDelete = state.value.users.first { it.id == action.userId })
                                UsersAction.OnDeleteCancel -> state.value.copy(confirmDelete = null)
                                else -> state.value
                            }
                    },
                    onUserClick = {},
                    onAddUserClick = {},
                    snackbarHostState = remember { SnackbarHostState() },
                    listState = rememberLazyListState(),
                    gridState = rememberLazyGridState(),
                    currentTime = { now },
                )
            }
        }
    }

    @Test
    fun longPressOpensDialogNamingTheUserAndTheUndoWindow() {
        setContent()

        composeRule.onNodeWithText("Jane Doe").performTouchInput { longClick() }

        assertEquals(UsersAction.OnDeleteRequest(jane.id), actions.single())
        composeRule.onNodeWithText("Delete Jane Doe?").assertIsDisplayed()
        composeRule.onNodeWithText("You will have 5 seconds to undo", substring = true).assertIsDisplayed()
    }

    @Test
    fun deleteAccessibilityActionOpensTheSameDialog() {
        setContent()

        val node = composeRule.onNodeWithText("Rahul Sharma").fetchSemanticsNode()
        val delete = node.config[SemanticsActions.CustomActions].single { it.label == "Delete" }
        composeRule.runOnIdle { delete.action() }

        assertEquals(UsersAction.OnDeleteRequest(rahul.id), actions.single())
        composeRule.onNodeWithText("Delete Rahul Sharma?").assertIsDisplayed()
    }

    @Test
    fun cancelClosesTheDialogAndChangesNothing() {
        setContent()
        composeRule.onNodeWithText("Jane Doe").performTouchInput { longClick() }

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onAllNodesWithText("Delete Jane Doe?").assertCountEquals(0)
        composeRule.onNodeWithText("Jane Doe").assertIsDisplayed()
        assertTrue(actions.none { it == UsersAction.OnDeleteConfirm })
        assertEquals(UsersAction.OnDeleteCancel, actions.last())
    }

    @Test
    fun loadingStateHasTwentyPlaceholders() {
        state.value = UsersState(content = FeedContent.Loading)
        setContent()

        // Lazy lists report an unknown row count, so prove the size by scrolling: index 19 exists, index 20 does not.
        val list = composeRule.onNodeWithContentDescription("Loading users")
        list.performScrollToIndex(PLACEHOLDER_COUNT - 1)
        composeRule.onAllNodesWithTag(PLACEHOLDER_TEST_TAG).onLast().assertExists()
        assertThrows(IllegalArgumentException::class.java) { list.performScrollToIndex(PLACEHOLDER_COUNT) }
    }

    @Test
    fun undoOnTheSnackbarDispatchesUndoForThatUser() {
        state.value =
            UsersState(
                users = listOf(rahul),
                content = FeedContent.Users,
                pendingDelete =
                    PendingDeleteUi(
                        userId = jane.id,
                        userName = jane.name,
                        deadline = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 5_000),
                        windowMillis = 5_000,
                    ),
            )
        setContent()

        composeRule.onNodeWithText("Jane Doe deleted").assertIsDisplayed()
        composeRule.onNodeWithText("Undo").performClick()

        composeRule.runOnIdle { assertEquals(UsersAction.OnUndoDelete(jane.id), actions.last()) }
    }
}
