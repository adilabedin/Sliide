package com.androidlearning.sliide

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * Launches the real app: proves Koin started in SliideApplication, the list-detail root and both ViewModels resolved,
 * and that the first load leaves the shimmer (into users, the empty state or an error, depending on the network).
 */
class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun usersScreenIsShownOnLaunch() {
        // The list pane animates in, so wait for it to settle before asserting what is on screen.
        composeRule.waitUntil(timeoutMillis = 5_000) { isDisplayed("Add user") }
        composeRule.onNodeWithText("Users").assertIsDisplayed()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(hasContentDescription("Loading users")).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun isDisplayed(text: String): Boolean =
        // The FAB clears its children's semantics, so its label is only visible in the unmerged tree.
        runCatching { composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed() }.isSuccess
}
