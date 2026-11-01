package com.androidlearning.sliide.users.presentation.util

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.error_local
import com.androidlearning.sliide.users.presentation.generated.resources.error_no_internet
import com.androidlearning.sliide.users.presentation.generated.resources.error_too_many_requests
import com.androidlearning.sliide.users.presentation.generated.resources.error_unauthorized
import kotlin.test.Test

class DataErrorUiTextTest {
    @Test
    fun everyRemoteErrorHasItsOwnMessage() {
        val messages =
            DataError.Remote.entries
                .map { it.toUiText() }
                .toSet()
        assertThat(messages).hasSize(DataError.Remote.entries.size)
    }

    @Test
    fun specificMessages() {
        assertThat(DataError.Remote.NO_INTERNET.toUiText()).isEqualTo(UiText.Resource(Res.string.error_no_internet))
        assertThat(DataError.Remote.TOO_MANY_REQUESTS.toUiText())
            .isEqualTo(UiText.Resource(Res.string.error_too_many_requests))
        assertThat(DataError.Remote.UNAUTHORIZED.toUiText()).isEqualTo(UiText.Resource(Res.string.error_unauthorized))
    }

    @Test
    fun localErrorsShareTheGenericMessage() {
        DataError.Local.entries.forEach { error ->
            assertThat(error.toUiText()).isEqualTo(UiText.Resource(Res.string.error_local))
        }
    }
}
