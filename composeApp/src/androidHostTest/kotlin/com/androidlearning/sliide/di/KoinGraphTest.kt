package com.androidlearning.sliide.di

import android.content.Context
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
            extraTypes = listOf(Context::class),
        )
    }
}
