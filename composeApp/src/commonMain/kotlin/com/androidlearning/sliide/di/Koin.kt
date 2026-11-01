package com.androidlearning.sliide.di

import com.androidlearning.sliide.core.database.di.databaseModule
import com.androidlearning.sliide.core.database.di.platformDatabaseModule
import com.androidlearning.sliide.users.data.di.usersDataModule
import com.androidlearning.sliide.users.presentation.di.usersPresentationModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration

val appModules: List<Module> =
    listOf(
        platformDatabaseModule,
        databaseModule,
        usersDataModule,
        usersPresentationModule,
    )

/**
 * Starts Koin once per process. Called from `SliideApplication` on Android (with `androidContext`)
 * and from `iOSApp.init()` on iOS (exported to Swift as `KoinKt.doInitKoin(config:)`).
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(appModules)
    }
}
