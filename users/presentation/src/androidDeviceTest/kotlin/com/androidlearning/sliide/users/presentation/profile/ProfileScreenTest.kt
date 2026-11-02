package com.androidlearning.sliide.users.presentation.profile

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.androidlearning.sliide.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_invalid
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Runs the stateless [ProfileScreen]; a small reducer mirrors the ViewModel for mode and toggle changes. */
class ProfileScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val header = ProfileHeader(userId = 1, initials = "JD", addedAgo = RelativeTime.Minutes(5), isPendingSync = false)
    private val form = ProfileForm("Jane Doe", "jane.doe@example.com", Gender.FEMALE, UserStatus.ACTIVE)
    private val actions = mutableListOf<ProfileAction>()
    private val state = mutableStateOf(ProfileState(mode = ProfileMode.VIEW, header = header, form = form))

    private var showBackButton = true
    private var backClicks = 0

    private val nameFieldMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Jane Doe"))

    private fun setContent() {
        composeRule.setContent {
            SliideTheme {
                ProfileScreen(
                    state = state.value,
                    onAction = { action ->
                        actions += action
                        val current = state.value
                        state.value =
                            when (action) {
                                ProfileAction.OnEditClick -> current.copy(mode = ProfileMode.EDIT)
                                ProfileAction.OnCancelEdit -> current.copy(mode = ProfileMode.VIEW, form = form)
                                is ProfileAction.OnGenderChange -> current.copy(form = current.form.copy(gender = action.gender))
                                else -> current
                            }
                    },
                    showBackButton = showBackButton,
                    onBackClick = { backClicks++ },
                    snackbarHostState = remember { SnackbarHostState() },
                )
            }
        }
    }

    @Test
    fun editIconTurnsIntoSave() {
        setContent()
        composeRule.onAllNodesWithText("Save").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Edit profile").performClick()

        composeRule.onNodeWithText("Save").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Edit profile").assertCountEquals(0)
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
    }

    @Test
    fun viewModeFieldsAreReadOnly() {
        setContent()

        val nameField = composeRule.onNode(nameFieldMatcher)

        nameField.performClick()

        // Read-only: no SetText action means neither typing nor the IME can change the value.
        nameField.assert(SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, false))
        nameField.assert(hasSetTextAction().not())
        assertTrue(actions.none { it is ProfileAction.OnNameChange })
    }

    @Test
    fun genderOptionsAreMutuallyExclusive() {
        setContent()
        composeRule.onNodeWithContentDescription("Edit profile").performClick()
        composeRule.onNodeWithText("Female").assertIsSelected()

        composeRule.onNodeWithText("Male").performClick()

        composeRule.onNodeWithText("Male").assertIsSelected()
        composeRule.onNodeWithText("Female").assertIsNotSelected()
    }

    @Test
    fun saveIsDisabledWhileTheFormIsInvalid() {
        state.value =
            ProfileState(
                mode = ProfileMode.EDIT,
                header = header,
                form = form.copy(email = "jane@example"),
                emailError = UiText.Resource(Res.string.validation_email_invalid),
                canSave = false,
            )
        setContent()

        composeRule.onNodeWithText("Save").assertIsNotEnabled()
        composeRule.onNodeWithText("Enter a valid email address").assertIsDisplayed()
    }

    @Test
    fun deleteButtonIsShownForAUserButNotInCreateMode() {
        setContent()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()

        state.value = ProfileState(mode = ProfileMode.CREATE)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Delete").assertCountEquals(0)
        composeRule.onNodeWithText("New user").assertIsDisplayed()
    }

    @Test
    fun createModeShowsBackArrowNotClose() {
        // Two-pane layout: View mode would have no navigation icon, but Create always shows a back arrow.
        showBackButton = false
        state.value = ProfileState(mode = ProfileMode.CREATE)
        setContent()

        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Cancel editing").assertCountEquals(0)
    }

    @Test
    fun backArrowInCreateLeavesWithoutSaving() {
        state.value = ProfileState(mode = ProfileMode.CREATE, form = ProfileForm(name = "Ada"))
        setContent()

        composeRule.onNodeWithContentDescription("Back").performClick()

        composeRule.runOnIdle {
            assertEquals(1, backClicks)
            assertTrue(actions.none { it == ProfileAction.OnSaveClick })
        }
    }

    @Test
    fun saveButtonIsAtLeast48dpTall() {
        state.value = ProfileState(mode = ProfileMode.CREATE)
        setContent()

        composeRule.onNodeWithText("Save").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    }
}
