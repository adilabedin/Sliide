package com.androidlearning.sliide.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class HttpClientExtTest {
    @Test
    fun statusCodesMapToRemoteErrors() =
        runTest {
            val expected =
                mapOf(
                    401 to DataError.Remote.UNAUTHORIZED,
                    403 to DataError.Remote.UNAUTHORIZED,
                    404 to DataError.Remote.NOT_FOUND,
                    408 to DataError.Remote.REQUEST_TIMEOUT,
                    422 to DataError.Remote.VALIDATION,
                    429 to DataError.Remote.TOO_MANY_REQUESTS,
                    500 to DataError.Remote.SERVER_ERROR,
                    503 to DataError.Remote.SERVER_ERROR,
                    418 to DataError.Remote.UNKNOWN,
                )
            expected.forEach { (code, error) ->
                val mock = MockClient { respondJson("""{"message":"x"}""", status = HttpStatusCode.fromValue(code)) }

                assertThat(mock.client.get<TestDto>("users"), "GET $code").isEqualTo(Result.Failure(error))
                assertThat(mock.client.post<TestDto, TestDto>("users", TestDto(1)), "POST $code").isEqualTo(Result.Failure(error))
                assertThat(mock.client.patch<TestDto, TestDto>("users/1", TestDto(1)), "PATCH $code").isEqualTo(Result.Failure(error))
                assertThat(mock.client.delete("users/1"), "DELETE $code").isEqualTo(Result.Failure(error))
            }
        }

    @Test
    fun successStatusesDecodeTheBody() =
        runTest {
            val ok = MockClient { respondJson("""{"id":1}""", status = HttpStatusCode.OK) }
            val created = MockClient { respondJson("""{"id":2}""", status = HttpStatusCode.Created) }

            assertThat((ok.client.get<TestDto>("users/1") as Result.Success).data.body).isEqualTo(TestDto(1))
            assertThat(created.client.post<TestDto, TestDto>("users", TestDto(0))).isEqualTo(Result.Success(TestDto(2)))
        }

    @Test
    fun getExposesResponseHeaders() =
        runTest {
            val mock = MockClient { respondJson("[]", extraHeaders = mapOf("X-Pagination-Pages" to "7")) }

            val result = mock.client.get<List<TestDto>>("users")

            assertThat((result as Result.Success).data.headers["X-Pagination-Pages"]).isEqualTo("7")
        }

    @Test
    fun deleteWithNoContentSucceeds() =
        runTest {
            val mock = MockClient { respondJson("", status = HttpStatusCode.NoContent) }

            assertThat(mock.client.delete("users/1")).isEqualTo(Result.Success(Unit))
        }

    @Test
    fun malformedBodyIsSerializationError() =
        runTest {
            val mock = MockClient { respondJson("""{"id":"not-a-number"}""") }

            assertThat(mock.client.get<TestDto>("users/1")).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
            assertThat(mock.client.post<TestDto, TestDto>("users", TestDto(1)))
                .isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
        }

    @Test
    fun ioExceptionIsNoInternet() =
        runTest {
            val mock = MockClient { throw IOException("Unable to resolve host") }

            assertThat(mock.client.get<TestDto>("users")).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(mock.client.delete("users/1")).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
        }

    @Test
    fun requestTimeoutIsRequestTimeout() =
        runTest {
            val mock = MockClient { request -> throw HttpRequestTimeoutException(request.urlString(), 20_000L) }

            assertThat(mock.client.get<TestDto>("users")).isEqualTo(Result.Failure(DataError.Remote.REQUEST_TIMEOUT))
        }

    @Test
    fun socketTimeoutIsRequestTimeoutDespiteBeingAnIOException() =
        runTest {
            val timeout = SocketTimeoutException("Socket timeout")
            assertThat(timeout).isInstanceOf(IOException::class)
            val mock = MockClient { throw timeout }

            assertThat(mock.client.get<TestDto>("users")).isEqualTo(Result.Failure(DataError.Remote.REQUEST_TIMEOUT))
        }

    @Test
    fun otherExceptionsAreUnknown() =
        runTest {
            val mock = MockClient { throw IllegalStateException("boom") }

            assertThat(mock.client.get<TestDto>("users")).isEqualTo(Result.Failure(DataError.Remote.UNKNOWN))
        }

    @Test
    fun cancellationPropagates() =
        runTest {
            val mock = MockClient { throw CancellationException("caller went away") }

            assertFailsWith<CancellationException> { mock.client.get<TestDto>("users") }
            assertFailsWith<CancellationException> { mock.client.delete("users/1") }
        }

    private fun HttpRequestData.urlString(): String = url.toString()
}
