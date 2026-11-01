package com.androidlearning.sliide.users.data.di

import org.koin.core.module.Module

/**
 * Binds `UserSyncScheduler` and the background run for this platform. A genuine platform split (design D1): Android
 * schedules with WorkManager periodic work, iOS with `BGTaskScheduler` app-refresh tasks, two unrelated OS facilities.
 */
expect val platformUserSyncModule: Module
