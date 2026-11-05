package com.androidlearning.sliide.users.presentation.util

import com.androidlearning.sliide.users.domain.model.WriteStatus
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.profile_saved_offline
import com.androidlearning.sliide.users.presentation.generated.resources.write_queued_auth_failed

/** Why an accepted write was queued, or `null` when it reached the server. */
internal fun WriteStatus.toQueuedMessage(): UiText? =
    when (this) {
        WriteStatus.SYNCED -> null
        WriteStatus.QUEUED -> UiText.Resource(Res.string.profile_saved_offline)
        WriteStatus.QUEUED_AUTH_FAILED -> UiText.Resource(Res.string.write_queued_auth_failed)
    }
