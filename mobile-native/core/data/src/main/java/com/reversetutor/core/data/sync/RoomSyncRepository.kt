package com.reversetutor.core.data.sync

import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.model.SyncConflict
import com.reversetutor.core.model.SyncCursor
import com.reversetutor.core.model.SyncEnvelope

class RoomSyncRepository(
    private val database: ReverseTutorDatabase
) {
    suspend fun enqueue(envelope: SyncEnvelope) {
        database.syncDao().upsertOutbox(envelope.toEntity())
    }

    suspend fun listReady(nowEpochMillis: Long, limit: Int = 50): List<SyncEnvelope> =
        database.syncDao().listReadyOutbox(nowEpochMillis, limit).map { it.toDomain() }

    suspend fun removeOutbox(id: String): Boolean =
        database.syncDao().deleteOutbox(id) > 0

    suspend fun saveCursor(cursor: SyncCursor) {
        database.syncDao().upsertCursor(cursor.toEntity())
    }

    suspend fun getCursor(spaceId: String, entityType: String): SyncCursor? =
        database.syncDao().getCursor(spaceId, entityType)?.toDomain()

    suspend fun saveConflict(conflict: SyncConflict) {
        database.syncDao().upsertConflict(conflict.toEntity())
    }

    suspend fun listPendingConflicts(spaceId: String): List<SyncConflict> =
        database.syncDao().listPendingConflicts(spaceId).map { it.toDomain() }
}
