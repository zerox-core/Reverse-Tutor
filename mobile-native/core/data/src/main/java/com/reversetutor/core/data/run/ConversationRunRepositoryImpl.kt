package com.reversetutor.core.data.run

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.domain.ConversationRunRepository
import com.reversetutor.core.model.ContextSnapshot
import com.reversetutor.core.model.TurnRun

class ConversationRunRepositoryImpl(
    private val database: ReverseTutorDatabase
) : ConversationRunRepository {
    override suspend fun nextSequence(spaceId: String, sessionId: String): Long =
        database.turnRunDao().nextSequence(spaceId, sessionId)

    override suspend fun saveRun(run: TurnRun): TurnRun {
        database.turnRunDao().upsertRun(run.toEntity())
        return run
    }

    override suspend fun saveContextSnapshot(snapshot: ContextSnapshot): ContextSnapshot {
        database.turnRunDao().upsertSnapshot(snapshot.toEntity())
        return snapshot
    }

    override suspend fun findRun(runId: String): TurnRun? =
        database.turnRunDao().getById(runId)?.toDomain()

    override suspend fun findLatestRun(turnId: String): TurnRun? =
        database.turnRunDao().findLatestByTurnId(turnId)?.toDomain()

    override suspend fun findWaitingRuns(parentTurnId: String): List<TurnRun> =
        database.turnRunDao().findWaitingByParentTurnId(parentTurnId).map { it.toDomain() }

    override suspend fun isSessionDeleted(sessionId: String): Boolean =
        database.syncDao().getTombstone(SessionEntityType, sessionId) != null ||
            database.sessionDao().getById(sessionId) == null

    suspend fun save(run: TurnRun, snapshot: ContextSnapshot? = null) {
        require(snapshot == null || snapshot.id == run.contextSnapshotId)
        database.withTransaction {
            snapshot?.let { database.turnRunDao().upsertSnapshot(it.toEntity()) }
            database.turnRunDao().upsertRun(run.toEntity())
        }
    }

    suspend fun get(id: String): TurnRun? =
        findRun(id)

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
