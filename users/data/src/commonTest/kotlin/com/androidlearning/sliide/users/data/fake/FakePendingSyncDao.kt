package com.androidlearning.sliide.users.data.fake

import com.androidlearning.sliide.core.database.PendingSyncDao
import com.androidlearning.sliide.core.database.PendingSyncEntity
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [PendingSyncDao]: at most one entry per user id, replay order by queuedAt then user id. */
class FakePendingSyncDao : PendingSyncDao {
    internal val table = MutableStateFlow<Map<Long, PendingSyncEntity>>(emptyMap())

    /** Current entries in replay order. Handy for assertions. */
    val entries: List<PendingSyncEntity>
        get() = table.value.values.sortedWith(compareBy<PendingSyncEntity> { it.queuedAtEpochMillis }.thenBy { it.user.id })

    override suspend fun getAllOrdered(): List<PendingSyncEntity> = entries

    override suspend fun getByUserId(userId: Long): PendingSyncEntity? = table.value[userId]

    override suspend fun upsert(entry: PendingSyncEntity) {
        table.value = table.value + (entry.user.id to entry)
    }

    override suspend fun deleteByUserId(userId: Long) {
        table.value = table.value - userId
    }

    override suspend fun count(): Int = table.value.size
}
