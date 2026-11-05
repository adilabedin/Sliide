package com.androidlearning.sliide.di

import com.androidlearning.sliide.core.data.di.ApplicationScope
import com.androidlearning.sliide.core.data.di.coreDataModule
import com.androidlearning.sliide.core.data.di.platformHttpEngineModule
import com.androidlearning.sliide.core.database.di.databaseModule
import com.androidlearning.sliide.core.database.di.platformDatabaseModule
import com.androidlearning.sliide.users.data.di.platformUserSyncModule
import com.androidlearning.sliide.users.data.di.usersDataModule
import com.androidlearning.sliide.users.domain.UserSyncScheduler
import com.androidlearning.sliide.users.presentation.di.usersPresentationModule
import kotlinx.coroutines.CoroutineScope
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Every module of the app. [applicationScope] is created and owned by the application (`SliideApplication` on Android,
 * [initKoinIos] on iOS) and bound under [ApplicationScope], so repository work outlives any screen.
 */
fun appModules(applicationScope: CoroutineScope): List<Module> =
    listOf(
        module { single(ApplicationScope) { applicationScope } },
        platformDatabaseModule,
        databaseModule,
        platformHttpEngineModule,
        coreDataModule,
        usersDataModule,
        platformUserSyncModule,
        usersPresentationModule,
    )

/**
 * Starts Koin once per process with the application's [applicationScope], then makes sure the periodic background
 * replay of the pending-sync queue is scheduled (spec: background-sync; idempotent, so safe on every launch). Called
 * from `SliideApplication` on Android (with `androidContext` and `workManagerFactory`, so WorkManager is ready here) and
 * through [initKoinIos] on iOS (from `iOSApp.init()`, before launch finishes, as `BGTaskScheduler` registration requires).
 */
fun initKoin(
    applicationScope: CoroutineScope,
    config: KoinAppDeclaration? = null,
) {
    val koin =
        startKoin {
            config?.invoke(this)
            modules(appModules(applicationScope))
        }.koin

    koin.get<UserSyncScheduler>().schedulePeriodicSync()
}
