package com.androidlearning.sliide.core.data.di

import com.androidlearning.sliide.core.data.networking.HttpClientFactory
import com.androidlearning.sliide.core.data.networking.goRestJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Binds the platform [HttpClientEngine]: OkHttp on Android, Darwin on iOS. Platform-specific because each OS has its
 * own native HTTP stack (design D4).
 */
expect val platformHttpEngineModule: Module

/** The shared GoRest [HttpClient], built identically on every platform from the platform engine. */
val coreDataModule =
    module {
        single<Json> { goRestJson() }
        single<HttpClient> {
            HttpClientFactory.create(
                engine = get<HttpClientEngine>(),
                json = get<Json>(),
            )
        }
    }
