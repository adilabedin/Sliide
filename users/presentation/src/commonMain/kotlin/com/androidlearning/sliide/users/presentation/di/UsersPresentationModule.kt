package com.androidlearning.sliide.users.presentation.di

import com.androidlearning.sliide.users.presentation.profile.ProfileViewModel
import com.androidlearning.sliide.users.presentation.users.UsersViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val usersPresentationModule =
    module {
        viewModel {
            UsersViewModel(
                userRepository = get(),
                clock = get(),
                savedStateHandle = get(),
            )
        }
        viewModelOf(::ProfileViewModel)
    }
