package com.androidlearning.sliide.users.presentation.util

import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.FieldError
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

/**
 * One human-readable message per failure cause (spec: user-feed "Failure messages are specific"). Server field errors
 * are shown as GoRest sent them, prefixed with the field name ("Email has already been taken"), one per line; they are
 * English and untranslated by design (design D7).
 */
fun DataError.toUiText(): UiText =
    when (this) {
        is DataError.Validation ->
            if (fields.isEmpty()) {
                UiText.Resource(Res.string.error_validation)
            } else {
                UiText.Dynamic(fields.joinToString(separator = "\n") { it.toDisplayText() })
            }
        is DataError.Local -> UiText.Resource(Res.string.error_local)
        is DataError.Remote ->
            UiText.Resource(
                when (this) {
                    DataError.Remote.NO_INTERNET -> Res.string.error_no_internet
                    DataError.Remote.REQUEST_TIMEOUT -> Res.string.error_timeout
                    DataError.Remote.SERVER_ERROR -> Res.string.error_server
                    DataError.Remote.TOO_MANY_REQUESTS -> Res.string.error_too_many_requests
                    DataError.Remote.UNAUTHORIZED -> Res.string.error_unauthorized
                    DataError.Remote.NOT_FOUND -> Res.string.error_not_found
                    DataError.Remote.SERIALIZATION -> Res.string.error_serialization
                    DataError.Remote.UNKNOWN -> Res.string.error_unknown
                },
            )
    }

/** `email` / `has already been taken` → "Email has already been taken". */
fun FieldError.toDisplayText(): String = "${field.replaceFirstChar(Char::titlecase)} $message"
