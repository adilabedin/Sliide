package com.androidlearning.sliide

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Launches the real app: proves Koin started in SliideApplication and UsersViewModel resolved. */
class AppLaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun usersScreenIsShownOnLaunch() {
        composeRule.onNodeWithText("Users").assertIsDisplayed()

        // The fresh install has an empty cache, so loading must finish into the empty state.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText("No users yet")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No users yet").assertIsDisplayed()
    }
}
