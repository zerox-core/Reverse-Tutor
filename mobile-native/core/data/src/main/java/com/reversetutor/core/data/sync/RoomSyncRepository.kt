package com.reversetutor.core.data.sync

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.domain.SyncRepository
import com.reversetutor.core.model.SyncConflict
import com.reversetutor.core.model.SyncCursor
import com.reversetutor.core.model.SyncEnvelope

class RoomSyncRepository(
    private val database: ReverseTutorDatabase,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : SyncRepository {
    override suspend fun pendingEnvelopes(limit: Int): List<SyncEnvelope> =
        database.syncDao().listReadyOutbox(nowEpochMillis(), limit).map { it.toDomain() }

    override suspend fun markSucceeded(envelopeId: String, remoteRevision: Long) {
        database.withTransaction {
            val envelope = database.syncDao().getOutbox(envelopeId) ?: return@withTransaction
            val existing = database.syncDao().getCursor(envelope.spaceId, envelope.entityType)
            database.syncDao().upsertCursor(
                SyncCursor(
                    id = existing?.id ?: cursorId(envelope.spaceId, envelope.entityType),
                    spaceId = envelope.spaceId,
                    entityType = envelope.entityType,
                    cursor = existing?.cursor,
                    revision = maxOf(existing?.revision ?: 0L, remoteRevision),
                    updatedAtEpochMillis = nowEpochMillis()
                ).toEntity()
            )
            database.syncDao().deleteOutbox(envelopeId)
        }
    }

    override suspend fun markFailed(envelopeId: String, error: String, retryable: Boolean) {
        val stored = database.syncDao().getOutbox(envelopeId) ?: return
        val retryCount = stored.retryCount + 1
        val nextAttemptAt = if (retryable) {
            nowEpochMillis() + retryDelayMillis(retryCount)
        } else {
            stored.nextAttemptAtEpochMillis
        }
        database.syncDao().updateOutboxFailure(
            id = envelopeId,
            status = if (retryable) PendingStatus else FailedStatus,
            retryCount = retryCount,
            nextAttemptAtEpochMillis = nextAttemptAt,
            lastError = error
        )
    }

    override suspend fun readCursor(spaceId: String, entityType: String): SyncCursor? =
        database.syncDao().getCursor(spaceId, entityType)?.toDomain()

    override suspend fun saveCursor(cursor: SyncCursor): SyncCursor {
        database.syncDao().upsertCursor(cursor.toEntity())
        return cursor
    }

    suspend fun enqueue(envelope: SyncEnvelope) {
        database.syncDao().upsertOutbox(envelope.toEntity())
    }

    suspend fun listReady(nowEpochMillis: Long, limit: Int = 50): List<SyncEnvelope> =
        database.syncDao().listReadyOutbox(nowEpochMillis, limit).map { it.toDomain() }

    suspend fun removeOutbox(id: String): Boolean =
        database.syncDao().deleteOutbox(id) > 0

    suspend fun getCursor(spaceId: String, entityType: String): SyncCursor? =
        database.syncDao().getCursor(spaceId, entityType)?.toDomain()

    suspend fun saveConflict(conflict: SyncConflict) {
        database.syncDao().upsertConflict(conflict.toEntity())
    }

    suspend fun listPendingConflicts(spaceId: String): List<SyncConflict> =
        database.syncDao().listPendingConflicts(spaceId).map { it.toDomain() }

    private fun retryDelayMillis(retryCount: Int): Long {
        val exponent = (retryCount - 1).coerceIn(0, MaxRetryExponent)
        return (BaseRetryDelayMillis * (1L shl exponent)).coerceAtMost(MaxRetryDelayMillis)
    }

    private fun cursorId(spaceId: String, entityType: String): String =
        "cursor:$spaceId:$entityType"

    private companion object {
        const val PendingStatus = "Pending"
        const val FailedStatus = "Failed"
        const val BaseRetryDelayMillis = 30_000L
        const val MaxRetryDelayMillis = 6L * 60L * 60L * 1_000L
        const val MaxRetryExponent = 10
    }
}
