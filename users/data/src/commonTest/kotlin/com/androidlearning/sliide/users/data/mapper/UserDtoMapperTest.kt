package com.androidlearning.sliide.users.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide.users.data.remote.dto.UserDto
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test

class UserDtoMapperTest {
    @Test
    fun everyGenderAndStatusRoundTrips() {
        Gender.entries.forEach { gender ->
            UserStatus.entries.forEach { status ->
                val details = UserDetails(name = "Jane Doe", email = "jane@example.com", gender = gender, status = status)
                val request = details.toUserRequestDto()
                val echoed = UserDto(id = 42, name = request.name, email = request.email, gender = request.gender, status = request.status)

                assertThat(echoed.toRemoteUserOrNull(), "$gender/$status").isEqualTo(RemoteUser(id = 42, details = details))
            }
        }
    }

    @Test
    fun requestUsesLowercaseWireValues() {
        val details = UserDetails(name = "Jane Doe", email = "jane@example.com", gender = Gender.FEMALE, status = UserStatus.ACTIVE)

        val json = Json.encodeToString(details.toUserRequestDto())

        assertThat(json).isEqualTo("""{"name":"Jane Doe","email":"jane@example.com","gender":"female","status":"active"}""")
    }

    @Test
    fun maleInactiveMapsFromWire() {
        val dto = UserDto(id = 7, name = "John", email = "john@example.com", gender = "male", status = "inactive")

        assertThat(dto.toRemoteUserOrNull()?.details)
            .isEqualTo(UserDetails(name = "John", email = "john@example.com", gender = Gender.MALE, status = UserStatus.INACTIVE))
    }

    @Test
    fun unknownStatusMapsToNull() {
        assertThat(UserDto(id = 1, name = "a", email = "a@b.c", gender = "female", status = "pending").toRemoteUserOrNull()).isNull()
    }

    @Test
    fun unknownGenderMapsToNull() {
        assertThat(UserDto(id = 1, name = "a", email = "a@b.c", gender = "other", status = "active").toRemoteUserOrNull()).isNull()
    }

    @Test
    fun uppercaseWireValueIsNotDocumentedAndMapsToNull() {
        assertThat(UserDto(id = 1, name = "a", email = "a@b.c", gender = "FEMALE", status = "active").toRemoteUserOrNull()).isNull()
    }

    @Test
    fun oneUnknownValueFailsTheWholeList() {
        val good = UserDto(id = 1, name = "a", email = "a@b.c", gender = "female", status = "active")
        val bad = good.copy(id = 2, status = "pending")

        assertThat(listOf(good, bad).toRemoteUsersOrNull()).isNull()
        assertThat(listOf(good).toRemoteUsersOrNull()?.map { it.id }).isEqualTo(listOf(1L))
        assertThat(emptyList<UserDto>().toRemoteUsersOrNull()).isEqualTo(emptyList())
    }
}
