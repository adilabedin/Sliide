package com.androidlearning.sliide.users.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.androidlearning.sliide.users.domain.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class UsersViewModel(
    private val userRepository: UserRepository,
) : ViewModel() {
    private var hasStartedObserving = false

    private val _state = MutableStateFlow(UsersState())
    val state: StateFlow<UsersState> =
        _state
            .onStart {
                if (!hasStartedObserving) {
                    hasStartedObserving = true
                    observeUsers()
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UsersState(),
            )

    @Suppress("UNUSED_PARAMETER")
    fun onAction(action: UsersAction) {
        // UsersAction has no cases yet; add an exhaustive `when` with the first action.
    }

    private fun observeUsers() {
        userRepository
            .observeUsers()
            .onEach { users ->
                _state.update { it.copy(users = users.map { user -> user.toUserUi() }, isLoading = false) }
            }.launchIn(viewModelScope)
    }
}
