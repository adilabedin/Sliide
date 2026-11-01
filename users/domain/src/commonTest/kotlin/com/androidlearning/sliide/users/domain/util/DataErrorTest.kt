package com.androidlearning.sliide.users.domain.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class DataErrorTest {
    @Test
    fun onlyConnectivityTimeoutServerAndRateLimitErrorsAreRetryable() {
        val expected =
            mapOf(
                DataError.Remote.NO_INTERNET to true,
                DataError.Remote.REQUEST_TIMEOUT to true,
                DataError.Remote.SERVER_ERROR to true,
                DataError.Remote.TOO_MANY_REQUESTS to true,
                DataError.Remote.UNAUTHORIZED to false,
                DataError.Remote.NOT_FOUND to false,
                DataError.Remote.VALIDATION to false,
                DataError.Remote.SERIALIZATION to false,
                DataError.Remote.UNKNOWN to false,
            )

        assertThat(expected.keys).isEqualTo(DataError.Remote.entries.toSet())
        DataError.Remote.entries.forEach { error ->
            assertThat(error.isRetryable, name = error.name).isEqualTo(expected.getValue(error))
        }
    }
}
