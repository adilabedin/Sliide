package com.androidlearning.sliide.core.data.networking

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/** A decoded response body together with the response headers (for example GoRest's `X-Pagination-*`). */
data class NetworkResponse<T>(
    val body: T,
    val headers: Headers,
)

/** GET [route] (relative, no leading slash). Returns the decoded body and the response headers. */
suspend inline fun <reified T> HttpClient.get(
    route: String,
    queryParameters: Map<String, Any?> = emptyMap(),
): Result<NetworkResponse<T>, DataError.Remote> =
    safeCall {
        request(route) {
            method = HttpMethod.Get
            queryParameters.forEach { (key, value) -> value?.let { parameter(key, it) } }
        }.toResult { NetworkResponse(body = it.body<T>(), headers = it.headers) }
    }

/** POST [body] as JSON to [route] and decode the response as [T]. */
suspend inline fun <reified B, reified T> HttpClient.post(
    route: String,
    body: B,
): Result<T, DataError.Remote> = sendJson(HttpMethod.Post, route, body)

/** PATCH [body] as JSON to [route] and decode the response as [T]. */
suspend inline fun <reified B, reified T> HttpClient.patch(
    route: String,
    body: B,
): Result<T, DataError.Remote> = sendJson(HttpMethod.Patch, route, body)

/** DELETE [route]. Never decodes a body, so `204 No Content` succeeds. */
suspend fun HttpClient.delete(route: String): EmptyResult<DataError.Remote> =
    safeCall {
        request(route) { method = HttpMethod.Delete }.toResult { }
    }

@PublishedApi
internal suspend inline fun <reified B, reified T> HttpClient.sendJson(
    method: HttpMethod,
    route: String,
    body: B,
): Result<T, DataError.Remote> =
    safeCall {
        request(route) {
            this.method = method
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toResult { it.body<T>() }
    }

/**
 * Runs [execute], which sends the request and decodes the body, and maps any exception it throws to a
 * [DataError.Remote]. Decoding happens inside, so a malformed body becomes `SERIALIZATION` instead of escaping.
 * Cancellation is rethrown, never turned into a failure.
 */
@PublishedApi
internal suspend inline fun <T> safeCall(execute: () -> Result<T, DataError.Remote>): Result<T, DataError.Remote> =
    try {
        execute()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Failure(e.toRemoteError())
    }

@PublishedApi
internal suspend inline fun <T> HttpResponse.toResult(onSuccess: (HttpResponse) -> T): Result<T, DataError.Remote> =
    if (status.isSuccess()) Result.Success(onSuccess(this)) else Result.Failure(status.toRemoteError())

/** Maps a non-2xx status to the error the offline-first repository relies on (see `DataError.Remote.isRetryable`). */
fun HttpStatusCode.toRemoteError(): DataError.Remote =
    when (value) {
        401, 403 -> DataError.Remote.UNAUTHORIZED
        404 -> DataError.Remote.NOT_FOUND
        408 -> DataError.Remote.REQUEST_TIMEOUT
        422 -> DataError.Remote.VALIDATION
        429 -> DataError.Remote.TOO_MANY_REQUESTS
        in 500..599 -> DataError.Remote.SERVER_ERROR
        else -> DataError.Remote.UNKNOWN
    }

/**
 * Maps a transport exception to a [DataError.Remote]. Order matters: Ktor's timeout exceptions are `IOException`s, so
 * they are matched before the generic connectivity case. Every other `IOException` (OkHttp's `UnknownHostException` /
 * `ConnectException`, Darwin's `DarwinHttpRequestException`) is a failure to reach the server, so offline writes are
 * queued as retryable instead of being discarded.
 */
fun Throwable.toRemoteError(): DataError.Remote =
    when (this) {
        is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException ->
            DataError.Remote.REQUEST_TIMEOUT
        is IOException -> DataError.Remote.NO_INTERNET
        is SerializationException, is ContentConvertException, is NoTransformationFoundException ->
            DataError.Remote.SERIALIZATION
        else -> DataError.Remote.UNKNOWN
    }
