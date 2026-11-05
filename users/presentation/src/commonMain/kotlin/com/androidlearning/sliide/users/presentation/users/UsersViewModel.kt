package com.androidlearning.sliide.users.presentation.users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide.core.domain.util.ApplicationScope
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.delete_failed
import com.androidlearning.sliide.users.presentation.generated.resources.delete_refused
import com.androidlearning.sliide.users.presentation.generated.resources.write_queued_auth_failed
import com.androidlearning.sliide.users.presentation.util.UiText
import com.androidlearning.sliide.users.presentation.util.toUiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The users feed (design D5). The cache is the only source of users: refresh results reach the screen solely through
 * [UserRepository.observeUsers]. State is one `combine` of the cache, a minute ticker (relative times), the Undo
 * window and the refresh/UI flags.
 *
 * It owns the Undo window of every delete, from the feed or handed over by the profile (spec: user-deletion): a
 * confirmed user is only hidden, and [UserRepository.deleteUser] runs once [undoWindow] passes without Undo. The cache
 * is not touched until then, so a refresh during the window can't clash with it. The delete runs in [applicationScope],
 * and [onCleared] sends one still waiting, so it goes through even if the feed is closed.
 *
 * It also owns which profile is open (spec: adaptive-layout): the user the person chose, kept in [savedStateHandle] so
 * it survives process death, or, beside the list on two-pane layouts, the first user by default.
 */
class UsersViewModel(
    private val userRepository: UserRepository,
    private val applicationScope: ApplicationScope,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
    private val tick: Duration = 1.minutes,
    private val undoWindow: Duration = DEFAULT_UNDO_WINDOW,
) : ViewModel() {
    private var hasStarted = false
    private var refreshJob: Job? = null

    /** The open Undo window, if any; [undoTimer] finishes it when [undoWindow] has passed. */
    private val pendingDelete = MutableStateFlow<PendingDelete?>(null)
    private var undoTimer: Job? = null

    /** Users deleted but still in the cache: during the Undo window and while their delete runs. */
    private val hiddenIds = MutableStateFlow<Set<Long>>(emptySet())

    private val refresh = MutableStateFlow(RefreshState())
    private val local = MutableStateFlow(LocalState(chosenUserId = savedStateHandle[KEY_CHOSEN_USER]))

    private val _events = Channel<UsersEvent>(Channel.BUFFERED)
    val events: Flow<UsersEvent> = _events.receiveAsFlow()

    // The last list the cache gave, so a feed that stopped collecting (e.g. while the add-user screen is open) comes
    // back showing it rather than the loading state, and keeps its default selection.
    private var lastCachedUsers: List<User>? = null

    private val cachedUsers: Flow<List<User>?> =
        userRepository
            .observeUsers()
            .onEach { lastCachedUsers = it }
            .map<List<User>, List<User>?> { it }
            .onStart { emit(lastCachedUsers) }

    private val ticker: Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(tick)
            }
        }

    val state: StateFlow<UsersState> =
        combine(
            cachedUsers,
            ticker,
            combine(pendingDelete, hiddenIds, ::Pair),
            refresh,
            local,
        ) { users, now, (pending, hidden), refresh, local ->
            buildState(users, now, pending, hidden, refresh, local)
        }.onStart {
            if (!hasStarted) {
                hasStarted = true
                startRefresh(RefreshTrigger.Launch)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // A profile restored after process death is open from the first frame, not slid in once the cache loads.
            initialValue = UsersState(selectedUserId = local.value.chosenUserId),
        )

    fun onAction(action: UsersAction) {
        when (action) {
            UsersAction.OnRefresh -> startRefresh(RefreshTrigger.Pull)
            UsersAction.OnRetry -> startRefresh(RefreshTrigger.Retry)
            UsersAction.OnDismissBanner -> refresh.update { it.copy(bannerDismissed = true) }
            is UsersAction.OnDeleteRequest -> local.update { it.copy(confirmDeleteId = action.userId) }
            UsersAction.OnDeleteCancel -> local.update { it.copy(confirmDeleteId = null) }
            UsersAction.OnDeleteConfirm -> {
                val id = local.value.confirmDeleteId ?: return
                local.update { it.copy(confirmDeleteId = null) }
                confirmDelete(id)
            }

            is UsersAction.OnConfirmDelete -> confirmDelete(action.userId)
            UsersAction.OnUndoDelete -> undoDelete()
            UsersAction.OnCommitDelete -> finishDelete()
            is UsersAction.OnSelectionChange -> {
                savedStateHandle[KEY_CHOSEN_USER] = action.userId
                local.update { it.copy(chosenUserId = action.userId) }
            }

            is UsersAction.OnPaneLayoutChange -> local.update { it.copy(isTwoPane = action.isTwoPane) }
            UsersAction.OnUserCreated ->
                viewModelScope.launch {
                    // The create is already in the cache, which may be ahead of this feed's own (restarting) collection.
                    val topUserId =
                        userRepository
                            .observeUsers()
                            .first()
                            .firstOrNull()
                            ?.id
                    local.update { it.copy(scrollToTopUserId = topUserId) }
                }

            UsersAction.OnScrolledToTop -> local.update { it.copy(scrollToTopUserId = null) }
            is UsersAction.OnShowMessage -> send(UsersEvent.ShowMessage(action.message))
        }
    }

    /**
     * Starts a refresh unless one is running (spec: "Pull during a refresh"). A pull during a running refresh only marks
     * it as user-requested, so the pull indicator shows until it ends (design D2).
     */
    private fun startRefresh(trigger: RefreshTrigger) {
        if (refreshJob?.isActive == true) {
            if (trigger == RefreshTrigger.Pull) refresh.update { it.copy(running = RefreshTrigger.Pull) }
            return
        }
        refresh.update { it.copy(running = trigger) }
        refreshJob =
            viewModelScope.launch {
                val result = userRepository.refresh()
                refresh.update {
                    when (result) {
                        is Result.Success -> RefreshState(running = null, lastError = null)
                        is Result.Failure -> RefreshState(running = null, lastError = result.error)
                    }
                }
            }
    }

    /**
     * Hides [userId] and opens its Undo window. Only one Undo is offered at a time, so a delete still waiting on its Undo
     * is sent now. If the user is the open profile, the selection is cleared so the detail pane moves on at once.
     */
    private fun confirmDelete(userId: Long) {
        val user = lastCachedUsers?.firstOrNull { it.id == userId } ?: return
        finishDelete()
        hiddenIds.update { it + userId }
        if (local.value.chosenUserId == userId) {
            savedStateHandle[KEY_CHOSEN_USER] = null
            local.update { it.copy(chosenUserId = null) }
        }
        pendingDelete.value = PendingDelete(user = user, deadline = clock.now() + undoWindow)
        undoTimer =
            viewModelScope.launch {
                delay(undoWindow)
                finishDelete()
            }
    }

    private fun undoDelete() {
        val pending = pendingDelete.value ?: return
        undoTimer?.cancel()
        pendingDelete.value = null
        hiddenIds.update { it - pending.user.id }
    }

    /**
     * Sends the delete still waiting on its Undo, if any. It runs in [applicationScope] because the user confirmed it,
     * so it must go through even if the feed closes first. The user stays hidden until the cache has dropped it.
     */
    private fun finishDelete() {
        val user = pendingDelete.value?.user ?: return
        undoTimer?.cancel()
        pendingDelete.value = null
        applicationScope.launch {
            val result = userRepository.deleteUser(user)
            hiddenIds.update { it - user.id }
            result.toDeleteMessage(user)?.let { _events.trySend(UsersEvent.ShowMessage(it)) }
        }
    }

    private fun Result<WriteStatus, DataError>.toDeleteMessage(user: User): UiText? =
        when (this) {
            is Result.Failure -> {
                val text = if (error is DataError.Validation) Res.string.delete_refused else Res.string.delete_failed
                UiText.Resource(text, listOf(user.name))
            }

            is Result.Success ->
                when (data) {
                    // A delete queued because the device is offline needs no message (spec: user-deletion).
                    WriteStatus.SYNCED, WriteStatus.QUEUED -> null
                    WriteStatus.QUEUED_AUTH_FAILED -> UiText.Resource(Res.string.write_queued_auth_failed)
                }
        }

    /** The Undo snackbar goes with the feed, so a delete still waiting on it is final now. */
    override fun onCleared() {
        finishDelete()
    }

    private fun send(event: UsersEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun buildState(
        users: List<User>?,
        now: Instant,
        pending: PendingDelete?,
        hiddenIds: Set<Long>,
        refresh: RefreshState,
        local: LocalState,
    ): UsersState {
        val cards = users.orEmpty().filterNot { it.id in hiddenIds }.map { it.toUserUi(now) }
        val content =
            when {
                users == null -> FeedContent.Loading
                cards.isNotEmpty() -> FeedContent.Users
                refresh.running != null -> FeedContent.Loading
                refresh.lastError != null -> FeedContent.Error(refresh.lastError.toUiText())
                else -> FeedContent.Empty
            }
        // A chosen user stays chosen until the profile closes it (OnSelectionChange(null)), even if it vanished from the
        // cache: the profile reports why ("no longer available"), and a delete from the profile clears it before Undo.
        val chosenUserId = local.chosenUserId
        val defaultSelectedUserId =
            cards.firstOrNull()?.id?.takeIf { local.isTwoPane && chosenUserId == null && content == FeedContent.Users }
        val selectedUserId = chosenUserId ?: defaultSelectedUserId
        return UsersState(
            users = cards,
            content = content,
            // Only a pull shows the pull indicator: the launch refresh and Retry are covered by the shimmer or the cached
            // cards (spec: "Pull to refresh").
            isRefreshing = refresh.running == RefreshTrigger.Pull && cards.isNotEmpty(),
            banner =
                refresh.lastError
                    ?.takeIf { cards.isNotEmpty() && !refresh.bannerDismissed }
                    ?.toUiText(),
            pendingDelete =
                pending?.let {
                    PendingDeleteUi(
                        userId = it.user.id,
                        userName = it.user.name,
                        deadline = it.deadline,
                        windowMillis = undoWindow.inWholeMilliseconds,
                    )
                },
            confirmDelete = local.confirmDeleteId?.let { id -> cards.firstOrNull { it.id == id } },
            selectedUserId = selectedUserId,
            defaultSelectedUserId = defaultSelectedUserId,
            scrollToTopUserId = local.scrollToTopUserId,
        )
    }

    /** A user hidden for its Undo window, which ends at [deadline]. */
    private data class PendingDelete(
        val user: User,
        val deadline: Instant,
    )

    /** What started the running refresh; only [Pull] shows the pull indicator (design D1). */
    private enum class RefreshTrigger { Launch, Pull, Retry }

    /** [running] is the trigger of the refresh in flight, or `null` when idle. */
    private data class RefreshState(
        val running: RefreshTrigger? = null,
        val lastError: DataError? = null,
        val bannerDismissed: Boolean = false,
    )

    private data class LocalState(
        val confirmDeleteId: Long? = null,
        val chosenUserId: Long? = null,
        val isTwoPane: Boolean = false,
        val scrollToTopUserId: Long? = null,
    )

    companion object {
        val DEFAULT_UNDO_WINDOW: Duration = 5.seconds
        private const val KEY_CHOSEN_USER = "users_chosen_user"
    }
}
