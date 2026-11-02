package com.androidlearning.sliide.users.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.UserRepository
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.presentation.delete.DeleteMessage
import com.androidlearning.sliide.users.presentation.delete.DeleteUndoController
import com.androidlearning.sliide.users.presentation.delete.PendingDelete
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
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
import kotlin.time.Instant

/**
 * The users feed (design D5). The cache is the only source of users: refresh results reach the screen solely through
 * [UserRepository.observeUsers]. State is one `combine` of the cache, a minute ticker (relative times), the Undo
 * window and the refresh/UI flags.
 */
class UsersViewModel(
    private val userRepository: UserRepository,
    private val deleteUndoController: DeleteUndoController,
    private val clock: Clock,
    private val tick: Duration = 1.minutes,
) : ViewModel() {
    private var hasStarted = false
    private var refreshJob: Job? = null

    private val refresh = MutableStateFlow(RefreshState())
    private val local = MutableStateFlow(LocalState())

    private val _events = Channel<UsersEvent>(Channel.BUFFERED)
    val events: Flow<UsersEvent> = _events.receiveAsFlow()

    private val cachedUsers: Flow<List<User>?> =
        userRepository
            .observeUsers()
            .map<List<User>, List<User>?> { it }
            .onStart { emit(null) }

    private val ticker: Flow<Instant> =
        flow {
            while (true) {
                emit(clock.now())
                delay(tick)
            }
        }

    val state: StateFlow<UsersState> =
        combine(cachedUsers, ticker, deleteUndoController.pending, refresh, local) { users, now, pending, refresh, local ->
            buildState(users, now, pending, refresh, local)
        }.onStart {
            if (!hasStarted) {
                hasStarted = true
                startRefresh()
                observeDeleteMessages()
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UsersState(),
        )

    fun onAction(action: UsersAction) {
        when (action) {
            UsersAction.OnRefresh, UsersAction.OnRetry -> startRefresh()
            UsersAction.OnDismissBanner -> refresh.update { it.copy(bannerDismissed = true) }
            is UsersAction.OnDeleteRequest -> local.update { it.copy(confirmDeleteId = action.userId) }
            UsersAction.OnDeleteCancel -> local.update { it.copy(confirmDeleteId = null) }
            UsersAction.OnDeleteConfirm -> {
                val id = local.value.confirmDeleteId ?: return
                local.update { it.copy(confirmDeleteId = null) }
                deleteUndoController.request(id)
            }
            is UsersAction.OnUndoDelete -> deleteUndoController.undo(action.userId)
            is UsersAction.OnCommitDelete -> deleteUndoController.commitNow(action.userId)
            is UsersAction.OnSelectionChange -> local.update { it.copy(selectedUserId = action.userId) }
            UsersAction.OnUserCreated -> send(UsersEvent.ScrollToTop)
            is UsersAction.OnShowMessage -> send(UsersEvent.ShowMessage(action.message))
        }
    }

    /** Starts a refresh unless one is running (spec: "Pull during a refresh"). */
    private fun startRefresh() {
        if (refreshJob?.isActive == true) return
        refresh.update { it.copy(isRunning = true) }
        refreshJob =
            viewModelScope.launch {
                val result = userRepository.refresh()
                refresh.update {
                    when (result) {
                        is Result.Success -> RefreshState(isRunning = false, lastError = null)
                        is Result.Failure -> RefreshState(isRunning = false, lastError = result.error)
                    }
                }
            }
    }

    private fun observeDeleteMessages() {
        deleteUndoController.messages
            .onEach { message ->
                when (message) {
                    is DeleteMessage.FinalizeFailed -> {
                        val text = if (message.error is DataError.Validation) Res.string.delete_refused else Res.string.delete_failed
                        send(UsersEvent.ShowMessage(UiText.Resource(text, listOf(message.userName))))
                    }
                    is DeleteMessage.QueuedAuthFailed ->
                        send(UsersEvent.ShowMessage(UiText.Resource(Res.string.write_queued_auth_failed)))
                }
            }.launchIn(viewModelScope)
    }

    private fun send(event: UsersEvent) {
        viewModelScope.launch { _events.send(event) }
    }

    private fun buildState(
        users: List<User>?,
        now: Instant,
        pending: PendingDelete?,
        refresh: RefreshState,
        local: LocalState,
    ): UsersState {
        val cards = users.orEmpty().map { it.toUserUi(now) }
        val content =
            when {
                users == null -> FeedContent.Loading
                cards.isNotEmpty() -> FeedContent.Users
                refresh.isRunning -> FeedContent.Loading
                refresh.lastError != null -> FeedContent.Error(refresh.lastError.toUiText())
                else -> FeedContent.Empty
            }
        return UsersState(
            users = cards,
            content = content,
            isRefreshing = refresh.isRunning && cards.isNotEmpty(),
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
                        windowMillis = deleteUndoController.window.inWholeMilliseconds,
                    )
                },
            confirmDelete = local.confirmDeleteId?.let { id -> cards.firstOrNull { it.id == id } },
            selectedUserId = local.selectedUserId,
        )
    }

    private data class RefreshState(
        val isRunning: Boolean = false,
        val lastError: DataError? = null,
        val bannerDismissed: Boolean = false,
    )

    private data class LocalState(
        val confirmDeleteId: Long? = null,
        val selectedUserId: Long? = null,
    )
}
