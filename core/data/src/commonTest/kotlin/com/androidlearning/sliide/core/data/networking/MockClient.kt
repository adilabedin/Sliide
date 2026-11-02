package com.androidlearning.sliide.core.data.networking

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val TEST_BASE_URL = "https://test.example/public/v2/"
const val TEST_TOKEN = "test-token"

/** A real [HttpClientFactory] client over a [MockEngine]; [engine] exposes the recorded requests. */
class MockClient(
    json: Json = goRestJson(),
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val engine = MockEngine(handler)
    val client: HttpClient = HttpClientFactory.create(engine, json, baseUrl = TEST_BASE_URL, apiToken = TEST_TOKEN)

    val requests: List<HttpRequestData> get() = engine.requestHistory
}

fun MockRequestHandleScope.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    extraHeaders: Map<String, String> = emptyMap(),
): HttpResponseData =
    respond(
        content = json,
        status = status,
        headers =
            headersOf(
                *(extraHeaders.mapValues { listOf(it.value) } + (HttpHeaders.ContentType to listOf("application/json")))
                    .toList()
                    .toTypedArray(),
            ),
    )

@Serializable
data class TestDto(
    val id: Long,
)
