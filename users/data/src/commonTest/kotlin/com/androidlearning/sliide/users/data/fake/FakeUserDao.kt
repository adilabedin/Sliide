package com.androidlearning.sliide.users.data.fake

import com.androidlearning.sliide.core.database.UserDao
import com.androidlearning.sliide.core.database.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * In-memory [UserDao]. The table is a [MutableStateFlow] of id → entity, so writes that leave the content unchanged
 * (for example [replaceAll] with identical users) do not emit. [nextTempId] also considers [pendingSyncDao]'s ids,
 * matching the real query over `users ∪ pending_sync`.
 */
class FakeUserDao(
    private val pendingSyncDao: FakePendingSyncDao? = null,
) : UserDao {
    internal val table = MutableStateFlow<Map<Long, UserEntity>>(emptyMap())

    /** Current rows, unordered. Handy for assertions. */
    val users: List<UserEntity> get() = table.value.values.toList()

    override fun observeAll(): Flow<List<UserEntity>> =
        table
            .map { all -> all.values.sortedWith(compareByDescending<UserEntity> { it.createdAtEpochMillis }.thenByDescending { it.id }) }
            .distinctUntilChanged()

    override suspend fun getAll(): List<UserEntity> = table.value.values.toList()

    override suspend fun getById(id: Long): UserEntity? = table.value[id]

    override suspend fun upsert(user: UserEntity) {
        table.value = table.value + (user.id to user)
    }

    override suspend fun deleteById(id: Long) {
        table.value = table.value - id
    }

    override suspend fun deleteAll() {
        table.value = emptyMap()
    }

    override suspend fun insertAll(users: List<UserEntity>) {
        val current = table.value
        users.forEach { require(it.id !in current) { "UNIQUE constraint failed: users.id (${it.id})" } }
        table.value = current + users.associateBy { it.id }
    }

    /** Swaps the table in one assignment, so observers never see the intermediate empty state. */
    override suspend fun replaceAll(users: List<UserEntity>) {
        table.value = users.associateBy { it.id }
    }

    override suspend fun nextTempId(): Long {
        val ids = table.value.keys + pendingSyncDao?.entries.orEmpty().map { it.user.id }
        val min = ids.minOrNull() ?: 0L
        return minOf(-1L, min - 1L)
    }
}
