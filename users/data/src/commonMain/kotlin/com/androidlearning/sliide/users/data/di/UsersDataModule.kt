package com.androidlearning.sliide.users.data.di

import com.androidlearning.sliide.core.data.di.ApplicationScope
import com.androidlearning.sliide.users.data.remote.KtorUserApiClient
import com.androidlearning.sliide.users.data.repository.OfflineFirstUserRepository
import com.androidlearning.sliide.users.data.sync.BackgroundUserSync
import com.androidlearning.sliide.users.domain.UserApiClient
import com.androidlearning.sliide.users.domain.UserRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

val usersDataModule =
    module {
        single<Clock> { Clock.System }
        singleOf(::KtorUserApiClient) bind UserApiClient::class
        // Single instance on purpose: its write lock and single-flight sync must be shared by the UI and background sync.
        single<UserRepository> {
            OfflineFirstUserRepository(
                userDao = get(),
                pendingSyncDao = get(),
                transactionRunner = get(),
                apiClient = get(),
                clock = get(),
                applicationScope = get(ApplicationScope),
            )
        }
        // One background run; shared by the Android worker and the iOS refresh task (design D2).
        factoryOf(::BackgroundUserSync)
    }
