package com.androidlearning.sliide.core.data.networking

import com.androidlearning.sliide.core.data.BuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json

/**
 * The JSON rules for GoRest bodies: unknown fields are ignored (GoRest may add some) and `null`s are not written.
 * Koin binds one instance; tests build the client with it too, so they decode exactly like the app.
 */
fun goRestJson(): Json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

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

    /**
     * The client's injected [Json], registered on every client this factory builds. Ktor's `ContentNegotiation` keeps
     * its own copy private, so code that must decode a raw body itself (the size-capped 422 parser) reads it from here
     * instead of building a second `Json`.
     */
    val JsonKey: AttributeKey<Json> = AttributeKey("GoRestJson")

    /**
     * Builds the client over [engine], decoding every body (successes and GoRest's 422 field errors) with [json]. The
     * caller provides [json] (Koin binds [goRestJson]), so the decoding rules live in one injected instance.
     */
    fun create(
        engine: HttpClientEngine,
        json: Json,
        baseUrl: String = BuildKonfig.BASE_URL,
        apiToken: String = BuildKonfig.API_TOKEN,
    ): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) {
                json(json)
            }
            install(HttpTimeout) { applyGoRestTimeouts() }
            defaultRequest {
                url(baseUrl)
                // No explicit Accept: ContentNegotiation already sends `Accept: application/json` on every request.
                header(HttpHeaders.Authorization, GoRestApi.BEARER_PREFIX + apiToken)
            }
        }.also { it.attributes.put(JsonKey, json) }
}

/** GoRest's timeouts (20 s per request, 10 s to connect, 20 s socket). Separate so a test can pin the values. */
internal fun HttpTimeoutConfig.applyGoRestTimeouts() {
    requestTimeoutMillis = HttpClientFactory.REQUEST_TIMEOUT_MILLIS
    connectTimeoutMillis = HttpClientFactory.CONNECT_TIMEOUT_MILLIS
    socketTimeoutMillis = HttpClientFactory.SOCKET_TIMEOUT_MILLIS
}
