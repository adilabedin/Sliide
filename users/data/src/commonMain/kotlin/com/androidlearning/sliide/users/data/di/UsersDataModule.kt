package com.androidlearning.sliide.users.data.di

import com.androidlearning.sliide.users.data.remote.UnavailableUserRemoteDataSource
import com.androidlearning.sliide.users.data.repository.OfflineFirstUserRepository
import com.androidlearning.sliide.users.domain.UserRemoteDataSource
import com.androidlearning.sliide.users.domain.UserRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val usersDataModule =
    module {
        single<Clock> { Clock.System }
        // Stub until the Ktor client exists (design D9): every call reports no internet, so writes are queued.
        singleOf(::UnavailableUserRemoteDataSource) bind UserRemoteDataSource::class
        // Single instance on purpose: its write lock and single-flight sync must be shared by the UI and background sync.
        single<UserRepository> {
            OfflineFirstUserRepository(
                userDao = get(),
                pendingSyncDao = get(),
                transactionRunner = get(),
                remote = get(),
                clock = get(),
            )
        }
    }
