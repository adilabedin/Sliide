package com.androidlearning.sliide.users.data.sync

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.androidlearning.sliide.core.database.PendingOperation
import com.androidlearning.sliide.core.database.PendingOperation.CREATE
import com.androidlearning.sliide.core.database.PendingOperation.DELETE
import com.androidlearning.sliide.core.database.PendingOperation.UPDATE
import com.androidlearning.sliide.core.domain.util.DataError
import com.androidlearning.sliide.core.domain.util.FieldError
import com.androidlearning.sliide.users.data.sync.OutboxDecision.GONE
import com.androidlearning.sliide.users.data.sync.OutboxDecision.KEEP
import com.androidlearning.sliide.users.data.sync.OutboxDecision.REJECT
import kotlin.test.Test

/** Every (operation × error) cell of design D4's table. */
class OutboxPolicyTest {
    /** Expected decision per error, as `CREATE / UPDATE / DELETE`. */
    private fun row(
        create: OutboxDecision,
        update: OutboxDecision,
        delete: OutboxDecision,
    ) = mapOf(CREATE to create, UPDATE to update, DELETE to delete)

    private val keep = row(KEEP, KEEP, KEEP)
    private val reject = row(REJECT, REJECT, REJECT)
    private val notFound = row(REJECT, GONE, GONE)

    private val remoteTable: Map<DataError.Remote, Map<PendingOperation, OutboxDecision>> =
        mapOf(
            DataError.Remote.NO_INTERNET to keep,
            DataError.Remote.REQUEST_TIMEOUT to keep,
            DataError.Remote.SERVER_ERROR to keep,
            DataError.Remote.TOO_MANY_REQUESTS to keep,
            DataError.Remote.UNAUTHORIZED to keep,
            DataError.Remote.NOT_FOUND to notFound,
            DataError.Remote.SERIALIZATION to reject,
            DataError.Remote.UNKNOWN to reject,
        )

    private val localTable: Map<DataError.Local, Map<PendingOperation, OutboxDecision>> =
        mapOf(
            DataError.Local.DISK_FULL to reject,
            DataError.Local.NOT_FOUND to reject,
            DataError.Local.UNKNOWN to reject,
        )

    @Test
    fun everyRemoteErrorIsClassified() {
        assertThat(remoteTable.keys).isEqualTo(DataError.Remote.entries.toSet())
        remoteTable.forEach { (error, expected) -> assertRow(error, expected) }
    }

    @Test
    fun everyLocalErrorIsRejected() {
        assertThat(localTable.keys).isEqualTo(DataError.Local.entries.toSet())
        localTable.forEach { (error, expected) -> assertRow(error, expected) }
    }

    @Test
    fun validationIsRejectedWithOrWithoutFields() {
        assertRow(DataError.Validation(listOf(FieldError("email", "has already been taken"))), reject)
        // On a delete this is GoRest refusing it (design D3).
        assertRow(DataError.Validation(emptyList()), reject)
    }

    private fun assertRow(
        error: DataError,
        expected: Map<PendingOperation, OutboxDecision>,
    ) {
        assertThat(expected.keys).isEqualTo(PendingOperation.entries.toSet())
        expected.forEach { (operation, decision) ->
            assertThat(OutboxPolicy.decide(operation, error), "$operation $error").isEqualTo(decision)
        }
    }
}
