package com.androidlearning.sliide.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * iOS entry point for Koin, called once from `iOSApp.init()` (exported to Swift as `KoinIosKt.doInitKoinIos()`).
 * iOS has no `Application` object and Swift can't build a Kotlin `CoroutineScope`, so the application scope is created
 * here, exactly like `SliideApplication` does on Android. It is never cancelled: its lifetime is the process.
 */
fun initKoinIos() {
    initKoin(applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
}
