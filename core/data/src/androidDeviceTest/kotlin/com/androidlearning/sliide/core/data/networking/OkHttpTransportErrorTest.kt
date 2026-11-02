package com.androidlearning.sliide.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Real OkHttp engine on a device, no mock: a refused connection must map to `NO_INTERNET` (retryable), never `UNKNOWN`.
 * HTTPS so the cleartext network-security policy can't fail the request for a different reason. Also proves the
 * merged `INTERNET` permission: without it the socket fails with a `SecurityException` and this maps to `UNKNOWN`.
 */
class OkHttpTransportErrorTest {
    @Test
    fun refusedConnectionIsNoInternet() =
        runTest {
            val client = HttpClientFactory.create(OkHttp.create(), goRestJson(), baseUrl = REFUSED_URL, apiToken = "")

            assertThat(client.get<List<TestDto>>("users")).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(client.delete("users/1")).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
        }

    private companion object {
        const val REFUSED_URL = "https://127.0.0.1:1/"
    }
}
