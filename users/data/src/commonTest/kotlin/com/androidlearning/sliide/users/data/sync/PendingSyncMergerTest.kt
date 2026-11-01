package com.androidlearning.sliide.users.data.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingOperation.CREATE
import com.androidlearning.sliide.core.database.PendingOperation.DELETE
import com.androidlearning.sliide.core.database.PendingOperation.UPDATE
import com.androidlearning.sliide.core.database.PendingSyncEntity
import com.androidlearning.sliide.core.database.UserEntity
import kotlin.test.Test

class PendingSyncMergerTest {
    private val original = user(name = "Jane Doe")
    private val edited = user(name = "Jane Smith")

    private fun user(
        id: Long = 42,
        name: String,
    ) = UserEntity(
        id = id,
        name = name,
        email = "jane@example.com",
        gender = "female",
        status = "active",
        createdAtEpochMillis = 1_000,
    )

    private fun entry(
        operation: PendingOperation,
        user: UserEntity,
        queuedAt: Long,
    ) = PendingSyncEntity(user = user, operation = operation, queuedAtEpochMillis = queuedAt)

    @Test
    fun noExistingUpdateIsQueuedAsIs() {
        val incoming = entry(UPDATE, edited, queuedAt = 200)
        assertThat(PendingSyncMerger.merge(null, incoming)).isEqualTo(incoming)
    }

    @Test
    fun noExistingDeleteIsQueuedAsIs() {
        val incoming = entry(DELETE, edited, queuedAt = 200)
        assertThat(PendingSyncMerger.merge(null, incoming)).isEqualTo(incoming)
    }

    @Test
    fun noExistingCreateIsQueuedAsIs() {
        val incoming = entry(CREATE, user(id = -1, name = "New"), queuedAt = 200)
        assertThat(PendingSyncMerger.merge(null, incoming)).isEqualTo(incoming)
    }

    @Test
    fun createThenUpdateStaysCreateWithNewSnapshotAndOriginalQueueTime() {
        val merged = PendingSyncMerger.merge(entry(CREATE, original, 100), entry(UPDATE, edited, 200))
        assertThat(merged).isEqualTo(entry(CREATE, edited, 100))
    }

    @Test
    fun createThenDeleteCancelsBoth() {
        val merged = PendingSyncMerger.merge(entry(CREATE, original, 100), entry(DELETE, original, 200))
        assertThat(merged).isNull()
    }

    @Test
    fun updateThenUpdateKeepsLatestSnapshotAndOriginalQueueTime() {
        val merged = PendingSyncMerger.merge(entry(UPDATE, original, 100), entry(UPDATE, edited, 200))
        assertThat(merged).isEqualTo(entry(UPDATE, edited, 100))
    }

    @Test
    fun updateThenDeleteBecomesDeleteWithOriginalQueueTime() {
        val merged = PendingSyncMerger.merge(entry(UPDATE, original, 100), entry(DELETE, edited, 200))
        assertThat(merged).isEqualTo(entry(DELETE, edited, 100))
    }
}
