package com.androidlearning.sliide.users.data.di

import com.androidlearning.sliide.core.data.coroutines.ApplicationScope
import com.androidlearning.sliide.users.data.sync.BackgroundTaskUserSyncScheduler
import com.androidlearning.sliide.users.data.sync.ConnectivityCheck
import com.androidlearning.sliide.users.data.sync.NwPathConnectivityCheck
import com.androidlearning.sliide.users.domain.UserSyncScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformUserSyncModule: Module =
    module {
        single<ConnectivityCheck> { NwPathConnectivityCheck() }
        single<UserSyncScheduler> {
            BackgroundTaskUserSyncScheduler(
                // Lazy: BackgroundUserSync → repository → this scheduler would otherwise be a cycle.
                backgroundUserSync = { get() },
                connectivityCheck = get(),
                applicationScope = get<ApplicationScope>(),
            )
        }
    }
