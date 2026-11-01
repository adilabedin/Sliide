package com.androidlearning.sliide.users.presentation.users

import androidx.compose.runtime.Immutable
import com.androidlearning.sliide.users.domain.model.User
import com.androidlearning.sliide.users.domain.model.UserStatus
import com.androidlearning.sliide.users.presentation.util.RelativeTime
import com.androidlearning.sliide.users.presentation.util.relativeAddedTime
import kotlin.time.Instant

/** A feed card: display-ready fields only. [addedAgo] is bucketed in shared code and pluralised by the UI. */
@Immutable
data class UserUi(
    val id: Long,
    val name: String,
    val email: String,
    val initials: String,
    val isActive: Boolean,
    val addedAgo: RelativeTime,
    val isPendingSync: Boolean,
)

fun User.toUserUi(now: Instant): UserUi =
    UserUi(
        id = id,
        name = name,
        email = email,
        initials = initialsOf(name),
        isActive = status == UserStatus.ACTIVE,
        addedAgo = relativeAddedTime(createdAt = createdAt, now = now),
        isPendingSync = isLocalOnly,
    )

/** Up to two initials from the first and last words of [name]; "?" when it has no letters. */
internal fun initialsOf(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { word -> word.any { it.isLetterOrDigit() } }
    if (words.isEmpty()) return "?"
    val first = words.first().first { it.isLetterOrDigit() }
    val last = if (words.size > 1) words.last().first { it.isLetterOrDigit() } else null
    return listOfNotNull(first, last).joinToString("").uppercase()
}
