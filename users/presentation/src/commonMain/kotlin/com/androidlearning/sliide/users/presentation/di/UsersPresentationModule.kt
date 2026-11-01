package com.androidlearning.sliide.users.presentation.di

import com.androidlearning.sliide.users.presentation.users.UsersViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val usersPresentationModule =
    module {
        viewModelOf(::UsersViewModel)
    }
