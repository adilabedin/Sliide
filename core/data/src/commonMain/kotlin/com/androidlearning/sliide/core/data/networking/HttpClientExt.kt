package com.androidlearning.sliide.core.data.networking

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.EmptyResult
import com.androidlearning.sliide.core.domain.util.FieldError
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
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.ktor.utils.io.readRemaining
import kotlinx.io.IOException
import kotlinx.io.readString
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

/**
 * POST [body] as JSON to [route] and decode the response as [T]. A 422 is returned as [DataError.Validation] with
 * GoRest's field errors; every other failure is a [DataError.Remote].
 */
suspend inline fun <reified B, reified T> HttpClient.post(
    route: String,
    body: B,
): Result<T, DataError> = sendJson(HttpMethod.Post, route, body)

/** PATCH [body] as JSON to [route] and decode the response as [T]. Failures as for [post]. */
suspend inline fun <reified B, reified T> HttpClient.patch(
    route: String,
    body: B,
): Result<T, DataError> = sendJson(HttpMethod.Patch, route, body)

/** DELETE [route]. Never decodes a success body, so `204 No Content` succeeds. Failures as for [post]. */
suspend fun HttpClient.delete(route: String): EmptyResult<DataError> =
    safeWriteCall {
        request(route) { method = HttpMethod.Delete }.toWriteResult { }
    }

@PublishedApi
internal suspend inline fun <reified B, reified T> HttpClient.sendJson(
    method: HttpMethod,
    route: String,
    body: B,
): Result<T, DataError> =
    safeWriteCall {
        request(route) {
            this.method = method
            contentType(ContentType.Application.Json)
            setBody(body)
        }.toWriteResult { it.body<T>() }
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

/** [safeCall] for writes, whose failures may also be a [DataError.Validation]. */
@PublishedApi
internal suspend inline fun <T> safeWriteCall(execute: () -> Result<T, DataError>): Result<T, DataError> =
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

@PublishedApi
internal suspend inline fun <T> HttpResponse.toWriteResult(onSuccess: (HttpResponse) -> T): Result<T, DataError> =
    when {
        status.isSuccess() -> Result.Success(onSuccess(this))
        status == HttpStatusCode.UnprocessableEntity -> Result.Failure(readValidationError())
        else -> Result.Failure(status.toRemoteError())
    }

/** Upper bound on how much of a 422 body is read; GoRest's field-error lists are far smaller. */
private const val MAX_ERROR_BODY_BYTES = 16 * 1024L

/**
 * Parses GoRest's 422 body (`[{"field": "email", "message": "has already been taken"}]`) into a
 * [DataError.Validation], keeping fields and messages unchanged and in order. Never throws (except cancellation): a body
 * that is empty, not JSON, not a list of field errors, or fails to read gives a validation failure with no fields.
 */
@PublishedApi
internal suspend fun HttpResponse.readValidationError(): DataError.Validation {
    val fields =
        try {
            val text = bodyAsChannel().readRemaining(MAX_ERROR_BODY_BYTES).readString()
            if (text.isBlank()) {
                emptyList()
            } else {
                HttpClientFactory.json
                    .decodeFromString<List<GoRestFieldErrorDto>>(text)
                    .map { FieldError(field = it.field, message = it.message) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    return DataError.Validation(fields)
}

/** Maps a non-2xx status to a [DataError.Remote]. A 422 on a write is parsed into [DataError.Validation] instead. */
fun HttpStatusCode.toRemoteError(): DataError.Remote =
    when (value) {
        401, 403 -> DataError.Remote.UNAUTHORIZED
        404 -> DataError.Remote.NOT_FOUND
        408 -> DataError.Remote.REQUEST_TIMEOUT
        429 -> DataError.Remote.TOO_MANY_REQUESTS
        in 500..599 -> DataError.Remote.SERVER_ERROR
        else -> DataError.Remote.UNKNOWN
    }

/**
 * Maps a transport exception to a [DataError.Remote]. Order matters: Ktor's timeout exceptions are `IOException`s, so
 * they are matched before the generic connectivity case. Every other `IOException` (OkHttp's `UnknownHostException` /
 * `ConnectException`, Darwin's `DarwinHttpRequestException`) is a failure to reach the server, so offline writes are
 * kept for a later sync instead of being discarded.
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
