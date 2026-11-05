package com.androidlearning.sliide

import android.app.Application
import com.androidlearning.sliide.di.initKoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory

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
            // Initialises WorkManager with Koin's WorkerFactory before initKoin schedules the background sync.
            workManagerFactory()
        }
    }
}
