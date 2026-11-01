package com.androidlearning.sliide.users.presentation.util

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Text produced outside Compose (in a ViewModel) and resolved to a string only when rendered. */
sealed interface UiText {
    data class Dynamic(
        val value: String,
    ) : UiText

    data class Resource(
        val id: StringResource,
        val args: List<Any> = emptyList(),
    ) : UiText

    @Composable
    fun asString(): String =
        when (this) {
            is Dynamic -> value
            is Resource -> stringResource(id, *args.toTypedArray())
        }

    /** Resolves outside composition (e.g. right before showing a snackbar). */
    suspend fun resolve(): String =
        when (this) {
            is Dynamic -> value
            is Resource -> getString(id, *args.toTypedArray())
        }
}
