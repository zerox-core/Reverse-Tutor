package com.reversetutor.core.data.run

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.model.ContextSnapshot
import com.reversetutor.core.model.TurnRun

class ConversationRunRepositoryImpl(
    private val database: ReverseTutorDatabase
) {
    suspend fun save(run: TurnRun, snapshot: ContextSnapshot? = null) {
        require(snapshot == null || snapshot.id == run.contextSnapshotId)
        database.withTransaction {
            snapshot?.let { database.turnRunDao().upsertSnapshot(it.toEntity()) }
            database.turnRunDao().upsertRun(run.toEntity())
        }
    }

    suspend fun get(id: String): TurnRun? =
        database.turnRunDao().getById(id)?.toDomain()

    suspend fun listBySession(sessionId: String): List<TurnRun> =
        database.turnRunDao().listBySession(sessionId).map { it.toDomain() }

    suspend fun listActiveBySession(sessionId: String): List<TurnRun> =
        database.turnRunDao().listActiveBySession(sessionId).map { it.toDomain() }

    suspend fun getSnapshot(id: String): ContextSnapshot? =
        database.turnRunDao().getSnapshot(id)?.toDomain()

    suspend fun isWritableAttempt(runId: String): Boolean =
        database.withTransaction {
            val run = database.turnRunDao().getById(runId) ?: return@withTransaction false
            val session = database.sessionDao().getById(run.sessionId) ?: return@withTransaction false
            if (session.archived || database.syncDao().getTombstone(SessionEntityType, run.sessionId) != null) {
                return@withTransaction false
            }
            database.turnRunDao().listBySession(run.sessionId)
                .filter { it.turnId == run.turnId }
                .maxOfOrNull { it.attempt } == run.attempt
        }

    private companion object {
        const val SessionEntityType = "session"
    }
}
