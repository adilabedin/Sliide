package com.androidlearning.sliide.users.data.mapper

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingSyncEntity
import com.androidlearning.sliide.core.database.UserEntity
import com.androidlearning.sliide.users.domain.model.Gender
import com.androidlearning.sliide.users.domain.model.RemoteUser
import com.androidlearning.sliide.users.domain.model.UserDetails
import com.androidlearning.sliide.users.domain.model.UserStatus
import kotlin.test.Test

class SyncMappersTest {
    private val details =
        UserDetails(name = "Jane Doe", email = "jane@example.com", gender = Gender.FEMALE, status = UserStatus.INACTIVE)

    private val entity =
        UserEntity(
            id = 7012,
            name = "Jane Doe",
            email = "jane@example.com",
            gender = "female",
            status = "inactive",
            createdAtEpochMillis = 1_700_000_000_000,
        )

    @Test
    fun detailsMapToEntityWithGivenIdAndTimestamp() {
        assertThat(details.toUserEntity(id = 7012, createdAtEpochMillis = 1_700_000_000_000)).isEqualTo(entity)
    }

    @Test
    fun remoteUserKeepsTheCreatedAtPassedIn() {
        val existingCreatedAt = 1_600_000_000_000
        val mapped = RemoteUser(id = 7012, details = details).toUserEntity(createdAtEpochMillis = existingCreatedAt)
        assertThat(mapped).isEqualTo(entity.copy(createdAtEpochMillis = existingCreatedAt))
    }

    @Test
    fun entityMapsBackToDetails() {
        assertThat(entity.toUserDetails()).isEqualTo(details)
    }

    @Test
    fun entityMapsToPendingSyncEntry() {
        assertThat(entity.toPendingSync(PendingOperation.UPDATE, queuedAtEpochMillis = 5)).isEqualTo(
            PendingSyncEntity(user = entity, operation = PendingOperation.UPDATE, queuedAtEpochMillis = 5),
        )
    }
}
