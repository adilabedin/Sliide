package com.androidlearning.sliide.users.data.di

import com.androidlearning.sliide.users.data.sync.UserSyncWorker
import com.androidlearning.sliide.users.data.sync.WorkManagerUserSyncScheduler
import com.androidlearning.sliide.users.domain.UserSyncScheduler
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformUserSyncModule: Module =
    module {
        workerOf(::UserSyncWorker)
        single<UserSyncScheduler> { WorkManagerUserSyncScheduler(androidContext()) }
    }
