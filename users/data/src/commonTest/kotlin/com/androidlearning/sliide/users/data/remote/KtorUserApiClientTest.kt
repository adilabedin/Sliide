package com.androidlearning.sliide.users.data.remote

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.data.networking.HttpClientFactory
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test

class KtorUserApiClientTest {
    private val jane = UserDetails(name = "Jane Doe", email = "jane@example.com", gender = Gender.FEMALE, status = UserStatus.ACTIVE)

    @Test
    fun fetchesPageOneThenTheLastPageInOrder() =
        runTest {
            val api =
                apiWith { request ->
                    when (request.page()) {
                        "1" -> respondUsers(userJson(1), pages = "412")
                        "412" -> respondUsers(userJson(8240), userJson(8241), pages = "412")
                        else -> error("unexpected page ${request.page()}")
                    }
                }

            val result = api.client.fetchLastPage()

            assertThat(result.ids()).containsExactly(8240L, 8241L)
            assertThat(api.requests.map { it.url.encodedPathAndQuery })
                .containsExactly("/public/v2/users?page=1&per_page=20", "/public/v2/users?page=412&per_page=20")
        }

    @Test
    fun singlePageMakesOneRequestAndReturnsItsUsers() =
        runTest {
            val api = apiWith { respondUsers(userJson(5), userJson(6), pages = "1") }

            assertThat(api.client.fetchLastPage().ids()).containsExactly(5L, 6L)
            assertThat(api.requests.size).isEqualTo(1)
        }

    @Test
    fun emptyCollectionReturnsEmptyList() =
        runTest {
            listOf("0", "1").forEach { pages ->
                val api = apiWith { respondUsers(pages = pages) }

                assertThat(api.client.fetchLastPage(), "pages=$pages").isEqualTo(Result.Success(emptyList()))
                assertThat(api.requests.size, "pages=$pages").isEqualTo(1)
            }
        }

    @Test
    fun firstRequestFailingSkipsTheSecond() =
        runTest {
            val api = apiWith { throw IOException("offline") }

            assertThat(api.client.fetchLastPage()).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(api.requests.size).isEqualTo(1)
        }

    @Test
    fun secondRequestFailingFailsTheWholeFetch() =
        runTest {
            val api =
                apiWith { request ->
                    if (request.page() == "1") {
                        respondUsers(userJson(1), pages = "5")
                    } else {
                        respond("", HttpStatusCode.ServiceUnavailable)
                    }
                }

            assertThat(api.client.fetchLastPage()).isEqualTo(Result.Failure(DataError.Remote.SERVER_ERROR))
            assertThat(api.requests.size).isEqualTo(2)
        }

    @Test
    fun missingPageCountHeaderIsSerializationError() =
        runTest {
            val api = apiWith { respondUsers(userJson(1), pages = null) }

            assertThat(api.client.fetchLastPage()).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
            assertThat(api.requests.size).isEqualTo(1)
        }

    @Test
    fun nonNumericPageCountHeaderIsSerializationError() =
        runTest {
            val api = apiWith { respondUsers(userJson(1), pages = "many") }

            assertThat(api.client.fetchLastPage()).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
            assertThat(api.requests.size).isEqualTo(1)
        }

    @Test
    fun vanishedLastPageIsAnEmptySuccessWithNoThirdRequest() =
        runTest {
            val api =
                apiWith { request ->
                    if (request.page() == "1") respondUsers(userJson(1), pages = "5") else respondUsers(pages = "4")
                }

            assertThat(api.client.fetchLastPage()).isEqualTo(Result.Success(emptyList()))
            assertThat(api.requests.size).isEqualTo(2)
        }

    @Test
    fun unknownStatusInPageIsSerializationError() =
        runTest {
            val api = apiWith { respondUsers(userJson(1), userJson(2, status = "pending"), pages = "1") }

            assertThat(api.client.fetchLastPage()).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
        }

    @Test
    fun createPostsLowercaseBodyAndReturnsServerUser() =
        runTest {
            val api = apiWith { respondJson(userJson(7001), HttpStatusCode.Created) }

            val result = api.client.create(jane)

            assertThat(result).isEqualTo(Result.Success(RemoteUser(id = 7001, details = jane)))
            val request = api.requests.single()
            assertThat(request.method).isEqualTo(HttpMethod.Post)
            assertThat(request.url.encodedPath).isEqualTo("/public/v2/users")
            assertThat((request.body as TextContent).text)
                .isEqualTo("""{"name":"Jane Doe","email":"jane@example.com","gender":"female","status":"active"}""")
        }

    @Test
    fun createWithTakenEmailIsValidationError() =
        runTest {
            val api =
                apiWith { respondJson("""[{"field":"email","message":"has already been taken"}]""", HttpStatusCode.UnprocessableEntity) }

            assertThat(api.client.create(jane)).isEqualTo(Result.Failure(DataError.Remote.VALIDATION))
        }

    @Test
    fun createResponseWithUnknownStatusIsSerializationError() =
        runTest {
            val api = apiWith { respondJson(userJson(7001, status = "pending"), HttpStatusCode.Created) }

            assertThat(api.client.create(jane)).isEqualTo(Result.Failure(DataError.Remote.SERIALIZATION))
        }

    @Test
    fun updatePatchesTheUserById() =
        runTest {
            val api = apiWith { respondJson(userJson(42)) }

            val result = api.client.update(42, jane)

            assertThat(result).isEqualTo(Result.Success(RemoteUser(id = 42, details = jane)))
            val request = api.requests.single()
            assertThat(request.method).isEqualTo(HttpMethod.Patch)
            assertThat(request.url.encodedPath).isEqualTo("/public/v2/users/42")
        }

    @Test
    fun deleteWithNoContentSucceeds() =
        runTest {
            val api = apiWith { respond("", HttpStatusCode.NoContent) }

            assertThat(api.client.delete(42)).isEqualTo(Result.Success(Unit))
            val request = api.requests.single()
            assertThat(request.method).isEqualTo(HttpMethod.Delete)
            assertThat(request.url.encodedPath).isEqualTo("/public/v2/users/42")
        }

    @Test
    fun deletingMissingUserIsNotFound() =
        runTest {
            val api = apiWith { respondJson("""{"message":"Resource not found"}""", HttpStatusCode.NotFound) }

            assertThat(api.client.delete(42)).isEqualTo(Result.Failure(DataError.Remote.NOT_FOUND))
        }

    @Test
    fun noUsersRequestedBeforeFetch() =
        runTest {
            val api = apiWith { respondUsers(pages = "1") }

            assertThat(api.requests).isEmpty()
        }

    private class Api(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) {
        private val sent = mutableListOf<HttpRequestData>()

        /** Recorded before the handler runs, so requests whose handler throws (transport failures) are counted too. */
        val requests: List<HttpRequestData> get() = sent.toList()

        private val engine =
            MockEngine { request ->
                sent += request
                handler(request)
            }
        val client = KtorUserApiClient(HttpClientFactory.create(engine, baseUrl = "https://gorest.test/public/v2/", apiToken = "t"))
    }

    private fun apiWith(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) = Api(handler)

    private fun HttpRequestData.page(): String? = url.parameters["page"]

    private fun Result<List<RemoteUser>, DataError.Remote>.ids(): List<Long> = (this as Result.Success).data.map { it.id }

    private fun userJson(
        id: Long,
        status: String = "active",
    ): String = """{"id":$id,"name":"Jane Doe","email":"jane@example.com","gender":"female","status":"$status"}"""

    private fun MockRequestHandleScope.respondUsers(
        vararg users: String,
        pages: String?,
    ): HttpResponseData =
        respond(
            content = users.joinToString(prefix = "[", postfix = "]"),
            status = HttpStatusCode.OK,
            headers =
                if (pages == null) {
                    headersOf(HttpHeaders.ContentType, "application/json")
                } else {
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), "X-Pagination-Pages" to listOf(pages))
                },
        )

    private fun MockRequestHandleScope.respondJson(
        json: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpResponseData = respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))
}
