package com.androidlearning.sliide.users.presentation.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_saved_offline
import com.androidlearning.sliide.users.presentation.generated.resources.write_queued_auth_failed
import kotlin.test.Test

class WriteStatusUiTextTest {
    @Test
    fun aSyncedWriteNeedsNoMessage() {
        assertThat(WriteStatus.SYNCED.toQueuedMessage()).isNull()
    }

    @Test
    fun aQueuedWriteSaysItWasSavedOffline() {
        assertThat(WriteStatus.QUEUED.toQueuedMessage()).isEqualTo(UiText.Resource(Res.string.profile_saved_offline))
    }

    @Test
    fun aWriteQueuedAfterAnAuthFailureSaysItWillRetry() {
        assertThat(WriteStatus.QUEUED_AUTH_FAILED.toQueuedMessage())
            .isEqualTo(UiText.Resource(Res.string.write_queued_auth_failed))
    }
}
