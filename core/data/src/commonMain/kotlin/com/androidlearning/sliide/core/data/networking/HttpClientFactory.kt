package com.androidlearning.sliide.core.data.networking

import com.androidlearning.sliide.core.data.BuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builds the GoRest [HttpClient]. Status codes are never thrown (`expectSuccess = false`): the `HttpClient`
 * extensions in this package map them to [com.androidlearning.sliide.core.domain.util.DataError.Remote].
 *
 * Routes passed to the client must be relative with no leading slash (`"users"`), so they resolve under [baseUrl]'s
 * path (`/public/v2/`) instead of replacing it.
 */
object HttpClientFactory {
    const val REQUEST_TIMEOUT_MILLIS = 20_000L
    const val CONNECT_TIMEOUT_MILLIS = 10_000L
    const val SOCKET_TIMEOUT_MILLIS = 20_000L

    fun create(
        engine: HttpClientEngine,
        baseUrl: String = BuildKonfig.BASE_URL,
        apiToken: String = BuildKonfig.API_TOKEN,
    ): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
                socketTimeoutMillis = SOCKET_TIMEOUT_MILLIS
            }
            defaultRequest {
                url(baseUrl)
                header(HttpHeaders.Authorization, GoRestApi.BEARER_PREFIX + apiToken)
                accept(ContentType.Application.Json)
            }
        }
}
