package com.androidlearning.sliide.core.data.networking

import kotlinx.serialization.Serializable

/** One entry of a GoRest 422 body: `[{"field": "email", "message": "has already been taken"}]`. */
@Serializable
internal data class GoRestFieldErrorDto(
    val field: String,
    val message: String,
)
