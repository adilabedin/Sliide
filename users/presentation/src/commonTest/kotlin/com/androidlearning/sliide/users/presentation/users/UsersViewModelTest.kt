package com.androidlearning.sliide.users.presentation.users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.cash.turbine.Event
import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.androidlearning.sliide.core.domain.util.ApplicationScope
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.fake.FakeUserRepository
import com.androidlearning.sliide.users.presentation.fake.SchedulerClock
import com.androidlearning.sliide.users.presentation.fake.TEST_NOW
import com.androidlearning.sliide.users.presentation.fake.jane
import com.androidlearning.sliide.users.presentation.fake.pendingAmelie
import com.androidlearning.sliide.users.presentation.fake.rahul
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.delete_failed
import com.androidlearning.sliide.users.presentation.generated.resources.delete_refused
import com.androidlearning.sliide.users.presentation.generated.resources.error_no_internet
import com.androidlearning.sliide.users.presentation.generated.resources.write_queued_auth_failed
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {
    private val repository = FakeUserRepository()
    private val noInternet = UiText.Resource(Res.string.error_no_internet)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Unconfined like Main, so deletes sent in it run at once; tied to the test so nothing outlives it. */
    private fun TestScope.applicationScope() =
        ApplicationScope(CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    private fun TestScope.viewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): UsersViewModel =
        UsersViewModel(
            userRepository = repository,
            applicationScope = applicationScope(),
            clock = SchedulerClock(testScheduler),
            savedStateHandle = savedStateHandle,
        )

    /** Long-press → confirm on [userId]'s card. */
    private fun UsersViewModel.confirmDelete(userId: Long) {
        onAction(UsersAction.OnDeleteRequest(userId))
        onAction(UsersAction.OnDeleteConfirm)
    }

    @Test
    fun startsLoadingAndRefreshesOnce() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                assertThat(awaitItem().content).isEqualTo(FeedContent.Loading)
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.state.test { cancelAndIgnoreRemainingEvents() }

            assertThat(repository.refreshCount).isEqualTo(1)
        }

    @Test
    fun cachedUsersBecomeCards() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))

                val state = expectMostRecentItem()
                assertThat(state.content).isEqualTo(FeedContent.Users)
                assertThat(state.users).containsExactly(
                    UserUi(1, "Jane Doe", "jane.doe@example.com", "JD", true, RelativeTime.Minutes(5), false),
                    UserUi(2, "Rahul Sharma", "rahul.sharma@example.in", "RS", false, RelativeTime.Hours(3), false),
                )
            }
        }

    @Test
    fun emptyCacheStaysLoadingWhileTheFirstRefreshRuns() =
        runTest {
            repository.refreshGate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(emptyList())
                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Loading)

                repository.refreshGate!!.complete(Unit)
                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Empty)
            }
        }

    @Test
    fun retryFromTheEmptyStateShowsTheShimmerThenTheUsers() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(emptyList())
                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Empty)

                repository.refreshGate = CompletableDeferred()
                viewModel.onAction(UsersAction.OnRetry)
                val retrying = expectMostRecentItem()
                assertThat(retrying.content).isEqualTo(FeedContent.Loading)
                assertThat(retrying.isRefreshing).isFalse()
                assertThat(repository.refreshCount).isEqualTo(2)

                repository.emit(listOf(jane))
                repository.refreshGate!!.complete(Unit)
                val done = expectMostRecentItem()
                assertThat(done.content).isEqualTo(FeedContent.Users)
                assertThat(done.isRefreshing).isFalse()
            }
        }

    @Test
    fun retryFromTheEmptyStateReturnsToEmptyWhenStillNoUsers() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(emptyList())
                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Empty)

                repository.refreshGate = CompletableDeferred()
                viewModel.onAction(UsersAction.OnRetry)
                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Loading)

                repository.refreshGate!!.complete(Unit)
                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Empty)
            }
        }

    @Test
    fun emptyCacheWithFailedRefreshShowsFullScreenError() =
        runTest {
            repository.refreshResult = Result.Failure(DataError.Remote.NO_INTERNET)
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(emptyList())

                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Error(noInternet))
            }
        }

    @Test
    fun retryReplacesTheErrorWithUsers() =
        runTest {
            repository.refreshResult = Result.Failure(DataError.Remote.NO_INTERNET)
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(emptyList())
                repository.refreshResult = Result.Success(Unit)

                viewModel.onAction(UsersAction.OnRetry)
                repository.emit(listOf(jane))

                val state = expectMostRecentItem()
                assertThat(state.content).isEqualTo(FeedContent.Users)
                assertThat(repository.refreshCount).isEqualTo(2)
            }
        }

    @Test
    fun failedRefreshWithCacheShowsBannerUntilASuccessfulRefresh() =
        runTest {
            repository.refreshResult = Result.Failure(DataError.Remote.NO_INTERNET)
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                val failed = expectMostRecentItem()
                assertThat(failed.content).isEqualTo(FeedContent.Users)
                assertThat(failed.banner).isEqualTo(noInternet)

                repository.refreshResult = Result.Success(Unit)
                viewModel.onAction(UsersAction.OnRefresh)

                assertThat(expectMostRecentItem().banner).isNull()
            }
        }

    @Test
    fun dismissedBannerStaysHidden() =
        runTest {
            repository.refreshResult = Result.Failure(DataError.Remote.NO_INTERNET)
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                viewModel.onAction(UsersAction.OnDismissBanner)

                assertThat(expectMostRecentItem().banner).isNull()
            }
        }

    @Test
    fun pullDuringTheLaunchRefreshShowsTheIndicatorWithoutASecondRefresh() =
        runTest {
            repository.refreshGate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                assertThat(expectMostRecentItem().isRefreshing).isFalse()

                viewModel.onAction(UsersAction.OnRefresh)
                viewModel.onAction(UsersAction.OnRefresh)
                assertThat(expectMostRecentItem().isRefreshing).isTrue()
                assertThat(repository.refreshCount).isEqualTo(1)

                repository.refreshGate!!.complete(Unit)
                assertThat(expectMostRecentItem().isRefreshing).isFalse()
            }
        }

    @Test
    fun launchRefreshWithCachedUsersShowsNoIndicator() =
        runTest {
            repository.refreshGate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))

                val state = expectMostRecentItem()
                assertThat(state.content).isEqualTo(FeedContent.Users)
                assertThat(state.isRefreshing).isFalse()
                assertThat(repository.refreshCount).isEqualTo(1)

                // Finishing changes nothing visible, so the state flow emits no new item.
                repository.refreshGate!!.complete(Unit)
                assertThat(viewModel.state.value.isRefreshing).isFalse()
            }
        }

    @Test
    fun usersArrivingDuringTheFirstLaunchRefreshShowNoIndicator() =
        runTest {
            repository.refreshGate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(emptyList())
                assertThat(expectMostRecentItem().content).isEqualTo(FeedContent.Loading)

                // The refresh writes users into the cache before it returns.
                repository.emit(listOf(jane))
                val state = expectMostRecentItem()
                assertThat(state.content).isEqualTo(FeedContent.Users)
                assertThat(state.isRefreshing).isFalse()
            }
        }

    @Test
    fun pullWhenIdleShowsTheIndicatorUntilTheRefreshEnds() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                assertThat(expectMostRecentItem().isRefreshing).isFalse()

                repository.refreshGate = CompletableDeferred()
                viewModel.onAction(UsersAction.OnRefresh)
                assertThat(expectMostRecentItem().isRefreshing).isTrue()
                assertThat(repository.refreshCount).isEqualTo(2)

                repository.refreshGate!!.complete(Unit)
                assertThat(expectMostRecentItem().isRefreshing).isFalse()
            }
        }

    @Test
    fun relativeTimeUpdatesAsTimePasses() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                assertThat(expectMostRecentItem().users.single().addedAgo).isEqualTo(RelativeTime.Minutes(5))

                advanceTimeBy(1.minutes)
                runCurrent()

                assertThat(expectMostRecentItem().users.single().addedAgo).isEqualTo(RelativeTime.Minutes(6))
            }
        }

    @Test
    fun localOnlyUserIsMarkedPendingSync() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(pendingAmelie, jane))

                val users = expectMostRecentItem().users
                assertThat(users.first().isPendingSync).isTrue()
                assertThat(users.first().addedAgo).isEqualTo(RelativeTime.JustNow)
                assertThat(users.last().isPendingSync).isFalse()
            }
        }

    @Test
    fun confirmingADeleteHidesTheUserWithoutTouchingTheRepository() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))

                viewModel.onAction(UsersAction.OnDeleteRequest(jane.id))
                assertThat(expectMostRecentItem().confirmDelete?.name).isEqualTo("Jane Doe")

                viewModel.onAction(UsersAction.OnDeleteConfirm)
                runCurrent()

                val state = expectMostRecentItem()
                assertThat(state.confirmDelete).isNull()
                assertThat(state.users.map { it.id }).containsExactly(rahul.id)
                assertThat(state.pendingDelete).isEqualTo(
                    PendingDeleteUi(jane.id, "Jane Doe", TEST_NOW + 5.seconds, windowMillis = 5_000),
                )
                assertThat(repository.deletedUsers).isEmpty()
            }
        }

    @Test
    fun cancellingTheDialogChangesNothing() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))

                viewModel.onAction(UsersAction.OnDeleteRequest(jane.id))
                viewModel.onAction(UsersAction.OnDeleteCancel)
                advanceTimeBy(10.seconds)

                val state = expectMostRecentItem()
                assertThat(state.confirmDelete).isNull()
                assertThat(state.pendingDelete).isNull()
                assertThat(state.users.map { it.id }).containsExactly(jane.id)
                assertThat(repository.deletedUsers).isEmpty()
            }
        }

    @Test
    fun undoWithinTheWindowShowsTheUserAgainAndNeverDeletes() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))
                viewModel.confirmDelete(jane.id)
                advanceTimeBy(2.seconds)

                viewModel.onAction(UsersAction.OnUndoDelete)
                advanceTimeBy(10.seconds)

                val state = expectMostRecentItem()
                assertThat(state.pendingDelete).isNull()
                assertThat(state.users.map { it.id }).containsExactly(jane.id, rahul.id)
                assertThat(repository.deletedUsers).isEmpty()
            }
        }

    @Test
    fun theWindowEndingDeletesTheUserOnce() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))
                viewModel.confirmDelete(jane.id)

                advanceTimeBy(4.9.seconds)
                assertThat(repository.deletedUsers).isEmpty()

                advanceTimeBy(10.seconds)
                val state = expectMostRecentItem()
                assertThat(repository.deletedUsers).containsExactly(jane)
                assertThat(state.pendingDelete).isNull()
                assertThat(state.users.map { it.id }).containsExactly(rahul.id)
            }
        }

    @Test
    fun theUserStaysHiddenWhileItsDeleteRuns() =
        runTest {
            repository.deleteGate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))
                viewModel.confirmDelete(jane.id)
                viewModel.onAction(UsersAction.OnCommitDelete)
                runCurrent()

                // The window is closed but the cache still has Jane: the card must not flash back meanwhile.
                val state = expectMostRecentItem()
                assertThat(state.pendingDelete).isNull()
                assertThat(state.users.map { it.id }).containsExactly(rahul.id)

                repository.deleteGate!!.complete(Unit)
                runCurrent()
                // Now gone from the cache instead; the state is unchanged, so nothing new is emitted.
                assertThat(
                    viewModel.state.value.users
                        .map { it.id },
                ).containsExactly(rahul.id)
                assertThat(repository.deletedUsers).containsExactly(jane)
            }
        }

    @Test
    fun swipingTheSnackbarDeletesAtOnce() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                viewModel.confirmDelete(jane.id)
                advanceTimeBy(1.seconds)

                viewModel.onAction(UsersAction.OnCommitDelete)
                runCurrent()
                assertThat(repository.deletedUsers).containsExactly(jane)
                assertThat(expectMostRecentItem().pendingDelete).isNull()

                advanceTimeBy(10.seconds)
                assertThat(repository.deletedUsers).containsExactly(jane)
            }
        }

    @Test
    fun aSecondDeleteFinalisesTheFirstAtOnce() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))
                viewModel.confirmDelete(jane.id)
                advanceTimeBy(2.seconds)

                viewModel.confirmDelete(rahul.id)
                runCurrent()

                assertThat(repository.deletedUsers).containsExactly(jane)
                assertThat(expectMostRecentItem().pendingDelete).isEqualTo(
                    PendingDeleteUi(rahul.id, "Rahul Sharma", TEST_NOW + 7.seconds, windowMillis = 5_000),
                )
                advanceTimeBy(4.seconds)
                assertThat(repository.deletedUsers).containsExactly(jane)
                advanceTimeBy(2.seconds)
                assertThat(repository.deletedUsers).containsExactly(jane, rahul)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aRefreshDuringTheWindowKeepsTheUserHiddenAndUndoable() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))
                viewModel.confirmDelete(jane.id)

                viewModel.onAction(UsersAction.OnRefresh)
                repository.emit(listOf(jane, rahul))
                runCurrent()
                assertThat(expectMostRecentItem().users.map { it.id }).containsExactly(rahul.id)

                viewModel.onAction(UsersAction.OnUndoDelete)
                runCurrent()
                assertThat(expectMostRecentItem().users.map { it.id }).containsExactly(jane.id, rahul.id)
            }
        }

    @Test
    fun clearingTheViewModelSendsADeleteStillWaitingOnUndo() =
        runTest {
            // Spec user-deletion: "App closed during the window".
            val store = ViewModelStore()
            val viewModel =
                ViewModelProvider.create(store, viewModelFactory { initializer { viewModel() } })[UsersViewModel::class]
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))
                viewModel.confirmDelete(jane.id)
                runCurrent()
                assertThat(repository.deletedUsers).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }

            store.clear()
            runCurrent()

            assertThat(repository.deletedUsers).containsExactly(jane)
        }

    @Test
    fun theProfileHandsOverADelete() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane, rahul))

                viewModel.onAction(UsersAction.OnConfirmDelete(jane.id))
                runCurrent()

                val state = expectMostRecentItem()
                assertThat(state.users.map { it.id }).containsExactly(rahul.id)
                assertThat(state.pendingDelete?.userId).isEqualTo(jane.id)
                assertThat(repository.deletedUsers).isEmpty()
            }
        }

    @Test
    fun deletingTheChosenUserClearsTheSelection() =
        runTest {
            val savedStateHandle = SavedStateHandle()
            val viewModel = viewModel(savedStateHandle)
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))
                viewModel.onAction(UsersAction.OnSelectionChange(jane.id))
                assertThat(expectMostRecentItem().selectedUserId).isEqualTo(jane.id)

                viewModel.confirmDelete(jane.id)
                runCurrent()

                val state = expectMostRecentItem()
                assertThat(state.selectedUserId).isEqualTo(rahul.id)
                assertThat(state.defaultSelectedUserId).isEqualTo(rahul.id)
                assertThat(savedStateHandle.get<Long>("users_chosen_user")).isNull()
            }
        }

    @Test
    fun refusedDeleteSaysTheServerRefused() =
        runTest {
            repository.deleteResult = Result.Failure(DataError.Validation(emptyList()))
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                viewModel.events.test {
                    viewModel.confirmDelete(jane.id)
                    advanceTimeBy(6.seconds)

                    assertThat(awaitItem()).isEqualTo(
                        UsersEvent.ShowMessage(UiText.Resource(Res.string.delete_refused, listOf("Jane Doe"))),
                    )
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deleteWithUnexpectedStatusSaysTheUserCouldNotBeDeleted() =
        runTest {
            repository.deleteResult = Result.Failure(DataError.Remote.UNKNOWN)
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                viewModel.events.test {
                    viewModel.confirmDelete(jane.id)
                    advanceTimeBy(6.seconds)

                    assertThat(awaitItem()).isEqualTo(
                        UsersEvent.ShowMessage(UiText.Resource(Res.string.delete_failed, listOf("Jane Doe"))),
                    )
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun unauthorisedDeleteSaysItWillRetry() =
        runTest {
            repository.deleteResult = Result.Success(WriteStatus.QUEUED_AUTH_FAILED)
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                viewModel.events.test {
                    viewModel.confirmDelete(jane.id)
                    advanceTimeBy(6.seconds)

                    assertThat(awaitItem()).isEqualTo(
                        UsersEvent.ShowMessage(UiText.Resource(Res.string.write_queued_auth_failed)),
                    )
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun queuedOfflineDeleteShowsNoMessage() =
        runTest {
            repository.deleteResult = Result.Success(WriteStatus.QUEUED)
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                viewModel.events.test {
                    viewModel.confirmDelete(jane.id)
                    advanceTimeBy(6.seconds)

                    expectNoEvents()
                }
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun createdUserScrollsTheFeedToTheTop() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                repository.emit(listOf(jane))
                // The create lands in the cache before the add-user screen reports it.
                repository.emit(listOf(rahul, jane))

                viewModel.onAction(UsersAction.OnUserCreated)
                assertThat(expectMostRecentItem().scrollToTopUserId).isEqualTo(rahul.id)

                viewModel.onAction(UsersAction.OnScrolledToTop)
                assertThat(expectMostRecentItem().scrollToTopUserId).isNull()
            }
        }

    @Test
    fun selectionIsReflectedInState() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnSelectionChange(jane.id))
                assertThat(expectMostRecentItem().selectedUserId).isEqualTo(jane.id)
            }
        }

    @Test
    fun twoPaneWithNothingChosenSelectsTheFirstUser() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))

                val state = expectMostRecentItem()
                assertThat(state.defaultSelectedUserId).isEqualTo(jane.id)
                assertThat(state.selectedUserId).isEqualTo(jane.id)
            }
        }

    @Test
    fun singlePaneHasNoDefaultSelection() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = false))
                repository.emit(listOf(jane, rahul))

                val state = expectMostRecentItem()
                assertThat(state.defaultSelectedUserId).isNull()
                assertThat(state.selectedUserId).isNull()
            }
        }

    @Test
    fun chosenUserOverridesTheDefault() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))

                viewModel.onAction(UsersAction.OnSelectionChange(rahul.id))

                val state = expectMostRecentItem()
                assertThat(state.defaultSelectedUserId).isNull()
                assertThat(state.selectedUserId).isEqualTo(rahul.id)
            }
        }

    @Test
    fun closingTheChosenProfileFallsBackToTheDefault() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))
                viewModel.onAction(UsersAction.OnSelectionChange(rahul.id))

                viewModel.onAction(UsersAction.OnSelectionChange(null))

                val state = expectMostRecentItem()
                assertThat(state.defaultSelectedUserId).isEqualTo(jane.id)
                assertThat(state.selectedUserId).isEqualTo(jane.id)
            }
        }

    @Test
    fun aChosenUserThatDisappearsStaysChosenUntilTheProfileClosesIt() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))
                viewModel.onAction(UsersAction.OnSelectionChange(rahul.id))

                repository.emit(listOf(jane))
                val whileOpen = expectMostRecentItem()
                assertThat(whileOpen.selectedUserId).isEqualTo(rahul.id)
                assertThat(whileOpen.defaultSelectedUserId).isNull()

                viewModel.onAction(UsersAction.OnSelectionChange(null))
                assertThat(expectMostRecentItem().selectedUserId).isEqualTo(jane.id)
            }
        }

    @Test
    fun aFeedThatStoppedCollectingComesBackWithItsUsersAndDefault() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))
                assertThat(expectMostRecentItem().selectedUserId).isEqualTo(jane.id)
            }
            // Away long enough for the shared state to stop, e.g. while the add-user screen is open; the cache then takes
            // a moment to answer the new subscription, as Room does.
            advanceTimeBy(10.seconds)
            repository.observeDelay = 1.seconds

            viewModel.state.test {
                advanceTimeBy(2.seconds)
                val states =
                    listOf(awaitItem()) +
                        cancelAndConsumeRemainingEvents().filterIsInstance<Event.Item<UsersState>>().map { it.value }
                states.forEach { state ->
                    assertThat(state.content).isEqualTo(FeedContent.Users)
                    assertThat(state.selectedUserId).isEqualTo(jane.id)
                }
            }
        }

    @Test
    fun theChosenUserSurvivesProcessDeath() =
        runTest {
            val savedStateHandle = SavedStateHandle()
            val before = viewModel(savedStateHandle)
            before.onAction(UsersAction.OnSelectionChange(rahul.id))

            val restored = viewModel(savedStateHandle)

            assertThat(restored.state.value.selectedUserId).isEqualTo(rahul.id)
            restored.state.test {
                repository.emit(listOf(jane, rahul))
                assertThat(expectMostRecentItem().selectedUserId).isEqualTo(rahul.id)
            }
        }

    @Test
    fun defaultFollowsTheFirstCardWhenTheFirstUserIsDeleted() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))
                assertThat(expectMostRecentItem().defaultSelectedUserId).isEqualTo(jane.id)

                viewModel.confirmDelete(jane.id)
                runCurrent()

                val state = expectMostRecentItem()
                assertThat(state.defaultSelectedUserId).isEqualTo(rahul.id)
                assertThat(state.selectedUserId).isEqualTo(rahul.id)
            }
        }

    @Test
    fun defaultFollowsTheFirstCardWhenAUserIsCreatedAtTheTop() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(listOf(jane, rahul))

                repository.emit(listOf(pendingAmelie, jane, rahul))

                val state = expectMostRecentItem()
                assertThat(state.defaultSelectedUserId).isEqualTo(pendingAmelie.id)
                assertThat(state.selectedUserId).isEqualTo(pendingAmelie.id)
            }
        }

    @Test
    fun loadingHasNoDefaultSelection() =
        runTest {
            repository.refreshGate = CompletableDeferred()
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))

                val state = expectMostRecentItem()
                assertThat(state.content).isEqualTo(FeedContent.Loading)
                assertThat(state.defaultSelectedUserId).isNull()
                assertThat(state.selectedUserId).isNull()
            }
        }

    @Test
    fun emptyFeedHasNoDefaultSelection() =
        runTest {
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(emptyList())

                val state = expectMostRecentItem()
                assertThat(state.content).isEqualTo(FeedContent.Empty)
                assertThat(state.defaultSelectedUserId).isNull()
                assertThat(state.selectedUserId).isNull()
            }
        }

    @Test
    fun fullScreenErrorHasNoDefaultSelection() =
        runTest {
            repository.refreshResult = Result.Failure(DataError.Remote.NO_INTERNET)
            val viewModel = viewModel()
            viewModel.state.test {
                viewModel.onAction(UsersAction.OnPaneLayoutChange(isTwoPane = true))
                repository.emit(emptyList())

                val state = expectMostRecentItem()
                assertThat(state.content).isEqualTo(FeedContent.Error(noInternet))
                assertThat(state.defaultSelectedUserId).isNull()
                assertThat(state.selectedUserId).isNull()
            }
        }

    @Test
    fun initialsAreDerivedFromTheName() {
        assertThat(initialsOf("Jane Doe")).isEqualTo("JD")
        assertThat(initialsOf("  amélie   de la laurent ")).isEqualTo("AL")
        assertThat(initialsOf("Cher")).isEqualTo("C")
        assertThat(initialsOf("   ")).isEqualTo("?")
    }
}
