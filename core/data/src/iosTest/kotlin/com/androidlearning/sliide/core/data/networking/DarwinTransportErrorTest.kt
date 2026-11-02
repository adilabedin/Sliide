package com.androidlearning.sliide.core.data.networking

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.Result
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Real Darwin engine, no mock: a refused connection must map to `NO_INTERNET` (retryable), never `UNKNOWN`, or the
 * offline-first repository would discard offline creates on iOS. HTTPS so App Transport Security can't fail the request
 * for a different reason.
 */
class DarwinTransportErrorTest {
    @Test
    fun refusedConnectionIsNoInternet() =
        runTest {
            val client = HttpClientFactory.create(Darwin.create(), goRestJson(), baseUrl = REFUSED_URL, apiToken = "")

            assertThat(client.get<List<TestDto>>("users")).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
            assertThat(client.delete("users/1")).isEqualTo(Result.Failure(DataError.Remote.NO_INTERNET))
        }

    private companion object {
        const val REFUSED_URL = "https://127.0.0.1:1/"
    }
}
