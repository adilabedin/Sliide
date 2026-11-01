package com.androidlearning.sliide.users.data.di

import com.androidlearning.sliide.users.data.repository.RoomUserRepository
import com.androidlearning.sliide.users.domain.UserRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val usersDataModule =
    module {
        singleOf(::RoomUserRepository) bind UserRepository::class
    }
