package com.androidlearning.sliide.core.domain.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class ResultTest {
    @Test
    fun mapTransformsSuccessData() {
        val result: Result<Int, DataError> = Result.Success(2)

        assertThat(result.map { it * 10 }).isEqualTo(Result.Success(20))
    }

    @Test
    fun mapKeepsFailureWithoutCallingTransform() {
        val result: Result<Int, DataError> = Result.Failure(DataError.Local.DISK_FULL)
        var called = false

        val mapped = result.map { called = true }

        assertThat(mapped).isEqualTo(Result.Failure(DataError.Local.DISK_FULL))
        assertThat(called).isEqualTo(false)
    }

    @Test
    fun onSuccessRunsOnlyForSuccess() {
        var seen: Int? = null

        Result.Success(5).onSuccess { seen = it }
        assertThat(seen).isEqualTo(5)

        seen = null
        (Result.Failure(DataError.Local.UNKNOWN) as Result<Int, DataError>).onSuccess { seen = it }
        assertThat(seen).isNull()
    }

    @Test
    fun onFailureRunsOnlyForFailure() {
        var seen: DataError? = null

        Result.Failure(DataError.Local.NOT_FOUND).onFailure { seen = it }
        assertThat(seen).isEqualTo(DataError.Local.NOT_FOUND)

        seen = null
        (Result.Success(1) as Result<Int, DataError>).onFailure { seen = it }
        assertThat(seen).isNull()
    }

    @Test
    fun helpersReturnTheSameResultForChaining() {
        val result: Result<Int, DataError> = Result.Success(3)

        assertThat(result.onSuccess { }.onFailure { }).isEqualTo(result)
    }

    @Test
    fun asEmptyResultDropsSuccessDataAndKeepsFailure() {
        assertThat(Result.Success("saved").asEmptyResult<String, DataError>()).isEqualTo(Result.Success(Unit))
        assertThat(
            Result.Failure(DataError.Local.DISK_FULL).asEmptyResult<Nothing, DataError>(),
        ).isEqualTo(Result.Failure(DataError.Local.DISK_FULL))
    }
}
