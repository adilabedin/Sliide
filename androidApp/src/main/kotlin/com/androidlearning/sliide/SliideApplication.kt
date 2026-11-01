package com.androidlearning.sliide

import android.app.Application
import com.androidlearning.sliide.di.initKoin
import org.koin.android.ext.koin.androidContext

class SliideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@SliideApplication)
        }
    }
}
