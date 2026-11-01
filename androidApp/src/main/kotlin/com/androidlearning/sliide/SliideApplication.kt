package com.androidlearning.sliide

import android.app.Application
import com.androidlearning.sliide.di.initKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext

class SliideApplication : Application() {
    /**
     * Process-wide scope for work that must outlive any screen (repository writes, sync, refresh). Owned here so its
     * lifetime is the application's; never cancelled. `SupervisorJob`: one failed operation doesn't cancel the others.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        initKoin(applicationScope) {
            androidContext(this@SliideApplication)
        }
    }
}
