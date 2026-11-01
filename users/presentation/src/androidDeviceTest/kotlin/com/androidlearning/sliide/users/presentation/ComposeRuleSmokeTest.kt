package com.androidlearning.sliide.users.presentation

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import org.junit.Rule
import org.junit.Test

/** Proves the device-test setup can host Compose content in this module. */
class ComposeRuleSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun composeContentIsDisplayed() {
        composeRule.setContent { SliideTheme { Text("Hello") } }
        composeRule.onNodeWithText("Hello").assertIsDisplayed()
    }
}
