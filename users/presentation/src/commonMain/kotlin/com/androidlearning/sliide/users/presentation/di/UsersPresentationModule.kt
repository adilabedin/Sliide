package com.androidlearning.sliide.users.presentation.di

import com.androidlearning.sliide.users.presentation.delete.DeleteUndoController
import com.androidlearning.sliide.users.presentation.profile.ProfileViewModel
import com.androidlearning.sliide.users.presentation.users.UsersViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val usersPresentationModule =
    module {
        // App-wide on purpose: the Undo window must outlive every screen and ViewModel (design D3).
        single {
            DeleteUndoController(
                repository = get(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                clock = get(),
            )
        }
        viewModel {
            UsersViewModel(userRepository = get(), deleteUndoController = get(), clock = get(), savedStateHandle = get())
        }
        viewModelOf(::ProfileViewModel)
    }
