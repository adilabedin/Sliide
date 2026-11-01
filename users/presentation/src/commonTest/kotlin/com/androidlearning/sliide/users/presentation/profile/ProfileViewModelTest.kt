package com.androidlearning.sliide.users.presentation.profile

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.delete.DeleteUndoController
import com.androidlearning.sliide.users.presentation.fake.FakeUserRepository
import com.androidlearning.sliide.users.presentation.fake.SchedulerClock
import com.androidlearning.sliide.users.presentation.fake.jane
import com.androidlearning.sliide.users.presentation.fake.pendingAmelie
import com.androidlearning.sliide.users.presentation.fake.rahul
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.error_unauthorized
import com.androidlearning.sliide.users.presentation.generated.resources.profile_no_longer_available
import com.androidlearning.sliide.users.presentation.generated.resources.profile_saved_offline
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_in_use
import com.androidlearning.sliide.users.presentation.generated.resources.validation_email_invalid
import com.androidlearning.sliide.users.presentation.generated.resources.validation_name_required
import com.androidlearning.sliide.users.presentation.generated.resources.validation_name_too_short
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val repository = FakeUserRepository()
    private val savedStateHandle = SavedStateHandle()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.viewModel(handle: SavedStateHandle = savedStateHandle): ProfileViewModel {
        repository.emit(listOf(jane, rahul, pendingAmelie))
        val clock = SchedulerClock(testScheduler)
        val controller = DeleteUndoController(repository = repository, scope = backgroundScope, clock = clock)
        return ProfileViewModel(repository, controller, clock, handle)
    }

    private suspend fun TestScope.editingJane(): ProfileViewModel =
        viewModel().apply {
            onAction(ProfileAction.OnOpen(jane.id))
            onAction(ProfileAction.OnEditClick)
        }

    private fun text(resource: org.jetbrains.compose.resources.StringResource) = UiText.Resource(resource)

    @Test
    fun openingAUserShowsItInViewMode() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(ProfileAction.OnOpen(jane.id))

            val state = viewModel.state.value
            assertThat(state.mode).isEqualTo(ProfileMode.VIEW)
            assertThat(state.form).isEqualTo(ProfileForm("Jane Doe", "jane.doe@example.com", Gender.FEMALE, UserStatus.ACTIVE))
            assertThat(state.header).isEqualTo(ProfileHeader(jane.id, "JD", RelativeTime.Minutes(5), isPendingSync = false))
            assertThat(state.isEditable).isFalse()
            assertThat(state.canSave).isFalse()
        }

    @Test
    fun viewModeIgnoresEdits() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(jane.id))

            viewModel.onAction(ProfileAction.OnNameChange("Someone else"))
            viewModel.onAction(ProfileAction.OnGenderChange(Gender.MALE))

            assertThat(viewModel.state.value.form.name).isEqualTo("Jane Doe")
            assertThat(viewModel.state.value.form.gender).isEqualTo(Gender.FEMALE)
        }

    @Test
    fun editWithoutChangesCannotSave() =
        runTest {
            val viewModel = editingJane()

            assertThat(viewModel.state.value.mode).isEqualTo(ProfileMode.EDIT)
            assertThat(viewModel.state.value.isEditable).isTrue()
            assertThat(viewModel.state.value.canSave).isFalse()
        }

    @Test
    fun whitespaceOnlyChangeIsNotAChange() =
        runTest {
            val viewModel = editingJane()

            viewModel.onAction(ProfileAction.OnNameChange("Jane Doe  "))

            assertThat(viewModel.state.value.canSave).isFalse()
        }

    @Test
    fun validChangeEnablesSave() =
        runTest {
            val viewModel = editingJane()

            viewModel.onAction(ProfileAction.OnEmailChange("jane@work.example.com"))

            assertThat(viewModel.state.value.canSave).isTrue()
        }

    @Test
    fun genderToggleIsAChange() =
        runTest {
            val viewModel = editingJane()

            viewModel.onAction(ProfileAction.OnGenderChange(Gender.MALE))

            assertThat(viewModel.state.value.form.gender).isEqualTo(Gender.MALE)
            assertThat(viewModel.state.value.canSave).isTrue()
        }

    @Test
    fun clearedNameShowsRequiredImmediately() =
        runTest {
            val viewModel = editingJane()

            viewModel.onAction(ProfileAction.OnNameChange(""))

            assertThat(viewModel.state.value.nameError).isEqualTo(text(Res.string.validation_name_required))
            assertThat(viewModel.state.value.canSave).isFalse()
        }

    @Test
    fun invalidEmailShowsErrorAndCorrectingItClearsIt() =
        runTest {
            val viewModel = editingJane()

            viewModel.onAction(ProfileAction.OnEmailChange("jane@example"))
            assertThat(viewModel.state.value.emailError).isEqualTo(text(Res.string.validation_email_invalid))

            viewModel.onAction(ProfileAction.OnEmailChange("jane@example.com"))
            assertThat(viewModel.state.value.emailError).isNull()
        }

    @Test
    fun pristineCreateFormHasDefaultsAndNoErrors() =
        runTest {
            val viewModel = viewModel()

            viewModel.onAction(ProfileAction.OnOpen(ProfileKey.CREATE))

            val state = viewModel.state.value
            assertThat(state.mode).isEqualTo(ProfileMode.CREATE)
            assertThat(state.form).isEqualTo(ProfileForm("", "", Gender.MALE, UserStatus.ACTIVE))
            assertThat(state.nameError).isNull()
            assertThat(state.emailError).isNull()
            assertThat(state.canSave).isFalse()
            assertThat(state.header).isNull()
        }

    @Test
    fun createShowsErrorsOnlyForTouchedFields() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(ProfileKey.CREATE))

            viewModel.onAction(ProfileAction.OnNameChange("J"))

            assertThat(viewModel.state.value.nameError).isEqualTo(text(Res.string.validation_name_too_short))
            assertThat(viewModel.state.value.emailError).isNull()
            assertThat(viewModel.state.value.canSave).isFalse()
        }

    @Test
    fun onlineEditSaveReturnsToViewWithTrimmedValues() =
        runTest {
            val viewModel = editingJane()
            viewModel.onAction(ProfileAction.OnNameChange("  Jane Smith "))

            viewModel.onAction(ProfileAction.OnSaveClick)

            assertThat(repository.updatedUsers.single().name).isEqualTo("Jane Smith")
            assertThat(viewModel.state.value.mode).isEqualTo(ProfileMode.VIEW)
            assertThat(viewModel.state.value.form.name).isEqualTo("Jane Smith")
            assertThat(viewModel.state.value.isSaving).isFalse()
        }

    @Test
    fun offlineEditSaveReturnsToViewAndSaysSavedOffline() =
        runTest {
            repository.updateResult = Result.Success(WriteStatus.QUEUED)
            val viewModel = editingJane()
            viewModel.events.test {
                viewModel.onAction(ProfileAction.OnNameChange("Jane Smith"))
                viewModel.onAction(ProfileAction.OnSaveClick)

                assertThat(awaitItem()).isEqualTo(ProfileEvent.ShowMessage(text(Res.string.profile_saved_offline)))
                assertThat(viewModel.state.value.mode).isEqualTo(ProfileMode.VIEW)
            }
        }

    @Test
    fun serverValidationRejectionKeepsInputAndFlagsTheEmail() =
        runTest {
            repository.updateResult = Result.Failure(DataError.Remote.VALIDATION)
            val viewModel = editingJane()
            viewModel.onAction(ProfileAction.OnEmailChange("taken@example.com"))

            viewModel.onAction(ProfileAction.OnSaveClick)

            val state = viewModel.state.value
            assertThat(state.mode).isEqualTo(ProfileMode.EDIT)
            assertThat(state.form.email).isEqualTo("taken@example.com")
            assertThat(state.emailError).isEqualTo(text(Res.string.validation_email_in_use))
            assertThat(state.canSave).isFalse()

            viewModel.onAction(ProfileAction.OnEmailChange("free@example.com"))
            assertThat(viewModel.state.value.emailError).isNull()
            assertThat(viewModel.state.value.canSave).isTrue()
        }

    @Test
    fun unauthorisedSaveKeepsEditingAndShowsMessage() =
        runTest {
            repository.updateResult = Result.Failure(DataError.Remote.UNAUTHORIZED)
            val viewModel = editingJane()
            viewModel.events.test {
                viewModel.onAction(ProfileAction.OnNameChange("Jane Smith"))
                viewModel.onAction(ProfileAction.OnSaveClick)

                assertThat(awaitItem()).isEqualTo(ProfileEvent.ShowMessage(text(Res.string.error_unauthorized)))
                assertThat(viewModel.state.value.mode).isEqualTo(ProfileMode.EDIT)
                assertThat(viewModel.state.value.form.name).isEqualTo("Jane Smith")
                assertThat(viewModel.state.value.canSave).isTrue()
            }
        }

    @Test
    fun formIsReadOnlyWhileSaving() =
        runTest {
            repository.saveGate = CompletableDeferred()
            val viewModel = editingJane()
            viewModel.onAction(ProfileAction.OnNameChange("Jane Smith"))

            viewModel.onAction(ProfileAction.OnSaveClick)
            viewModel.onAction(ProfileAction.OnNameChange("Typed while saving"))

            val state = viewModel.state.value
            assertThat(state.isSaving).isTrue()
            assertThat(state.isEditable).isFalse()
            assertThat(state.canSave).isFalse()
            assertThat(state.form.name).isEqualTo("Jane Smith")

            repository.saveGate!!.complete(Unit)
            assertThat(viewModel.state.value.isSaving).isFalse()
        }

    @Test
    fun cancelEditDiscardsTheDraft() =
        runTest {
            val viewModel = editingJane()
            viewModel.onAction(ProfileAction.OnNameChange("Changed"))

            viewModel.onAction(ProfileAction.OnCancelEdit)

            assertThat(viewModel.state.value.mode).isEqualTo(ProfileMode.VIEW)
            assertThat(viewModel.state.value.form.name).isEqualTo("Jane Doe")
            assertThat(repository.updatedUsers).containsExactly()
        }

    @Test
    fun onlineCreateClosesWithoutMessage() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(ProfileKey.CREATE))
            viewModel.events.test {
                viewModel.onAction(ProfileAction.OnNameChange(" Ada Lovelace "))
                viewModel.onAction(ProfileAction.OnEmailChange("ada@example.com "))
                viewModel.onAction(ProfileAction.OnGenderChange(Gender.FEMALE))
                viewModel.onAction(ProfileAction.OnSaveClick)

                assertThat(awaitItem()).isEqualTo(ProfileEvent.Created(message = null))
            }
            assertThat(repository.createdDetails).containsExactly(
                UserDetails("Ada Lovelace", "ada@example.com", Gender.FEMALE, UserStatus.ACTIVE),
            )
        }

    @Test
    fun offlineCreateClosesSayingSavedOffline() =
        runTest {
            repository.createResult = Result.Success(WriteStatus.QUEUED)
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(ProfileKey.CREATE))
            viewModel.events.test {
                viewModel.onAction(ProfileAction.OnNameChange("Ada Lovelace"))
                viewModel.onAction(ProfileAction.OnEmailChange("ada@example.com"))
                viewModel.onAction(ProfileAction.OnSaveClick)

                assertThat(awaitItem()).isEqualTo(ProfileEvent.Created(text(Res.string.profile_saved_offline)))
            }
        }

    @Test
    fun deleteAsksForConfirmationThenClosesAndOpensTheUndoWindow() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(jane.id))
            viewModel.events.test {
                viewModel.onAction(ProfileAction.OnDeleteClick)
                assertThat(viewModel.state.value.showDeleteDialog).isTrue()

                viewModel.onAction(ProfileAction.OnDeleteConfirm)
                runCurrent()

                assertThat(awaitItem()).isEqualTo(ProfileEvent.Close(message = null))
                assertThat(repository.removedIds).containsExactly(jane.id)
                expectNoEvents()
            }
        }

    @Test
    fun cancellingDeleteKeepsTheProfile() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(jane.id))

            viewModel.onAction(ProfileAction.OnDeleteClick)
            viewModel.onAction(ProfileAction.OnDeleteCancel)

            assertThat(viewModel.state.value.showDeleteDialog).isFalse()
            assertThat(repository.removedIds).containsExactly()
        }

    @Test
    fun userRemovedByRefreshClosesWithMessage() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(jane.id))
            viewModel.events.test {
                repository.emit(listOf(rahul))

                assertThat(awaitItem()).isEqualTo(ProfileEvent.Close(text(Res.string.profile_no_longer_available)))
            }
        }

    @Test
    fun pendingUserThatGetsItsServerIdClosesSilently() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(pendingAmelie.id))
            assertThat(
                viewModel.state.value.header
                    ?.isPendingSync,
            ).isEqualTo(true)
            viewModel.events.test {
                repository.emit(listOf(pendingAmelie.copy(id = 7_401_999), jane))

                assertThat(awaitItem()).isEqualTo(ProfileEvent.Close(message = null))
            }
        }

    @Test
    fun editDraftIsRestoredAfterProcessDeath() =
        runTest {
            val first = editingJane()
            first.onAction(ProfileAction.OnNameChange("Jane Draft"))

            val restored = viewModel(handle = savedStateHandle)

            val state = restored.state.value
            assertThat(state.mode).isEqualTo(ProfileMode.EDIT)
            assertThat(state.form.name).isEqualTo("Jane Draft")
            assertThat(state.header?.initials).isEqualTo("JD")
            assertThat(state.canSave).isTrue()
        }

    @Test
    fun cacheUpdatesDoNotOverwriteAnEditDraft() =
        runTest {
            val viewModel = editingJane()
            viewModel.onAction(ProfileAction.OnNameChange("Jane Draft"))

            repository.emit(listOf(jane.copy(email = "new@example.com"), rahul))

            assertThat(viewModel.state.value.form.name).isEqualTo("Jane Draft")
        }

    @Test
    fun dismissedCreateReopensBlank() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(ProfileKey.CREATE))
            viewModel.onAction(ProfileAction.OnNameChange("Ada"))

            viewModel.onAction(ProfileAction.OnDismiss)
            viewModel.onAction(ProfileAction.OnOpen(ProfileKey.CREATE))

            assertThat(viewModel.state.value.form).isEqualTo(ProfileForm())
            assertThat(viewModel.state.value.nameError).isNull()
        }

    @Test
    fun dismissedProfileStopsFollowingTheUser() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(jane.id))
            viewModel.events.test {
                viewModel.onAction(ProfileAction.OnDismiss)
                repository.emit(listOf(rahul))

                expectNoEvents()
            }
        }

    @Test
    fun cacheUpdatesRefreshViewMode() =
        runTest {
            val viewModel = viewModel()
            viewModel.onAction(ProfileAction.OnOpen(jane.id))

            repository.emit(listOf(jane.copy(email = "new@example.com"), rahul))

            assertThat(viewModel.state.value.form.email).isEqualTo("new@example.com")
        }
}
