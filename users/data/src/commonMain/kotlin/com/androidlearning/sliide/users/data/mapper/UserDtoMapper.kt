package com.androidlearning.sliide.users.data.mapper

import com.androidlearning.sliide.users.data.remote.dto.UserDto
import com.androidlearning.sliide.users.data.remote.dto.UserRequestDto
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus

fun UserDetails.toUserRequestDto(): UserRequestDto =
    UserRequestDto(
        name = name,
        email = email,
        gender = gender.wireValue,
        status = status.wireValue,
    )

/** Maps a wire user to the domain, or `null` when its gender or status is not a value GoRest documents. */
fun UserDto.toRemoteUserOrNull(): RemoteUser? {
    val gender = gender.toGenderOrNull() ?: return null
    val status = status.toUserStatusOrNull() ?: return null
    return RemoteUser(
        id = id,
        details = UserDetails(name = name, email = email, gender = gender, status = status),
    )
}

/** All-or-nothing: `null` if any user has an unknown gender or status. */
fun List<UserDto>.toRemoteUsersOrNull(): List<RemoteUser>? = map { it.toRemoteUserOrNull() ?: return null }

internal fun String.toGenderOrNull(): Gender? = Gender.entries.firstOrNull { it.wireValue == this }

internal fun String.toUserStatusOrNull(): UserStatus? = UserStatus.entries.firstOrNull { it.wireValue == this }
