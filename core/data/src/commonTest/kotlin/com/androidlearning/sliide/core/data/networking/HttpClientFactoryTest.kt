package com.androidlearning.sliide.core.data.networking

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.each
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide.core.domain.util.Result
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class HttpClientFactoryTest {
    @Test
    fun everyMethodCarriesBearerTokenAndAcceptsJson() =
        runTest {
            val mock = MockClient { respondJson("""{"id":1}""") }

            mock.client.get<TestDto>("users")
            mock.client.post<TestDto, TestDto>("users", TestDto(1))
            mock.client.patch<TestDto, TestDto>("users/1", TestDto(1))
            mock.client.delete("users/1")

            assertThat(mock.requests.map { it.method })
                .containsExactly(HttpMethod.Get, HttpMethod.Post, HttpMethod.Patch, HttpMethod.Delete)
            assertThat(mock.requests.map { it.headers[HttpHeaders.Authorization] }).each { it.isEqualTo("Bearer $TEST_TOKEN") }
            assertThat(mock.requests.map { it.headers[HttpHeaders.Accept] }).each { it.isEqualTo("application/json") }
        }

    @Test
    fun postAndPatchSendJsonBodies() =
        runTest {
            val mock = MockClient { respondJson("""{"id":1}""") }

            mock.client.post<TestDto, TestDto>("users", TestDto(7))
            mock.client.patch<TestDto, TestDto>("users/7", TestDto(7))

            assertThat(mock.requests.map { it.body.contentType?.withoutParameters() })
                .each { it.isEqualTo(ContentType.Application.Json) }
        }

    @Test
    fun bodilessRequestsSendNoContentType() =
        runTest {
            val mock = MockClient { respondJson("""{"id":1}""", status = HttpStatusCode.OK) }

            mock.client.get<TestDto>("users")
            mock.client.delete("users/1")

            assertThat(mock.requests.map { it.body.contentType }).each { it.isNull() }
        }

    @Test
    fun relativeRouteKeepsTheBasePath() =
        runTest {
            val mock = MockClient { respondJson("[]") }

            mock.client.get<List<TestDto>>("users", mapOf("page" to 3, "per_page" to 20, "skipped" to null))

            assertThat(
                mock.requests
                    .single()
                    .url
                    .toString(),
            ).isEqualTo("https://test.example/public/v2/users?page=3&per_page=20")
        }

    @Test
    fun unknownResponseFieldsAreIgnored() =
        runTest {
            val mock = MockClient { respondJson("""{"id":5,"unexpected":"field","nested":{"a":1}}""") }

            val result = mock.client.get<TestDto>("users/5")

            assertThat((result as Result.Success).data.body).isEqualTo(TestDto(5))
        }
}
