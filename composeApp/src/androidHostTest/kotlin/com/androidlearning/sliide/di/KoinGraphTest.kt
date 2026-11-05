package com.androidlearning.sliide.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

class KoinGraphTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun appGraphResolves() {
        module { includes(appModules(TestScope())) }.verify(
            // SavedStateHandle is supplied by the ViewModel store at runtime (ProfileViewModel), WorkerParameters by
            // WorkManager when it creates UserSyncWorker, and the CoroutineScope that ApplicationScope wraps by the
            // application itself (appModules' parameter), which a constructor-based check can't see.
            extraTypes = listOf(Context::class, SavedStateHandle::class, WorkerParameters::class, CoroutineScope::class),
        )
    }
}
