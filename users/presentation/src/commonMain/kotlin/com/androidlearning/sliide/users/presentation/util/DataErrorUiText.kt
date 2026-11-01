package com.androidlearning.sliide.users.presentation.util

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.error_local
import com.androidlearning.sliide.users.presentation.generated.resources.error_no_internet
import com.androidlearning.sliide.users.presentation.generated.resources.error_not_found
import com.androidlearning.sliide.users.presentation.generated.resources.error_serialization
import com.androidlearning.sliide.users.presentation.generated.resources.error_server
import com.androidlearning.sliide.users.presentation.generated.resources.error_timeout
import com.androidlearning.sliide.users.presentation.generated.resources.error_too_many_requests
import com.androidlearning.sliide.users.presentation.generated.resources.error_unauthorized
import com.androidlearning.sliide.users.presentation.generated.resources.error_unknown
import com.androidlearning.sliide.users.presentation.generated.resources.error_validation

/** One human-readable message per failure cause (spec: user-feed "Failure messages are specific"). */
fun DataError.toUiText(): UiText =
    UiText.Resource(
        when (this) {
            DataError.Remote.NO_INTERNET -> Res.string.error_no_internet
            DataError.Remote.REQUEST_TIMEOUT -> Res.string.error_timeout
            DataError.Remote.SERVER_ERROR -> Res.string.error_server
            DataError.Remote.TOO_MANY_REQUESTS -> Res.string.error_too_many_requests
            DataError.Remote.UNAUTHORIZED -> Res.string.error_unauthorized
            DataError.Remote.NOT_FOUND -> Res.string.error_not_found
            DataError.Remote.VALIDATION -> Res.string.error_validation
            DataError.Remote.SERIALIZATION -> Res.string.error_serialization
            DataError.Remote.UNKNOWN -> Res.string.error_unknown
            is DataError.Local -> Res.string.error_local
        },
    )
