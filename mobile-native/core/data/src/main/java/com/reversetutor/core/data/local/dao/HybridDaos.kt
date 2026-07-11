package com.reversetutor.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.reversetutor.core.data.local.entity.ContextSnapshotEntity
import com.reversetutor.core.data.local.entity.EntityTombstoneEntity
import com.reversetutor.core.data.local.entity.ModelBindingEntity
import com.reversetutor.core.data.local.entity.ProviderConnectionEntity
import com.reversetutor.core.data.local.entity.SearchDocumentEntity
import com.reversetutor.core.data.local.entity.StudyPlanTaskEntity
import com.reversetutor.core.data.local.entity.SyncConflictEntity
import com.reversetutor.core.data.local.entity.SyncCursorEntity
import com.reversetutor.core.data.local.entity.SyncOutboxEntity
import com.reversetutor.core.data.local.entity.TokenUsageRecordEntity
import com.reversetutor.core.data.local.entity.TurnRunEntity
import com.reversetutor.core.data.local.entity.WeeklySummaryEntity
import com.reversetutor.core.data.local.entity.WidgetLayoutPreferenceEntity

@Dao
interface ModelConnectionDao {
    @Upsert
    suspend fun upsertConnection(connection: ProviderConnectionEntity)

    @Upsert
    suspend fun upsertBinding(binding: ModelBindingEntity)

    @Query("SELECT * FROM provider_connections WHERE id = :id")
    suspend fun getConnection(id: String): ProviderConnectionEntity?

    @Query("SELECT * FROM model_bindings WHERE id = :id")
    suspend fun getBinding(id: String): ModelBindingEntity?

    @Query("SELECT * FROM provider_connections WHERE spaceId = :spaceId ORDER BY updatedAtEpochMillis DESC")
    suspend fun listConnections(spaceId: String): List<ProviderConnectionEntity>

    @Query("SELECT * FROM provider_connections ORDER BY updatedAtEpochMillis DESC")
    suspend fun listAllConnections(): List<ProviderConnectionEntity>

    @Query("SELECT * FROM model_bindings WHERE spaceId = :spaceId ORDER BY isDefault DESC, updatedAtEpochMillis DESC")
    suspend fun listBindings(spaceId: String): List<ModelBindingEntity>

    @Query("SELECT * FROM model_bindings WHERE connectionId = :connectionId ORDER BY isDefault DESC, updatedAtEpochMillis DESC")
    suspend fun listBindingsByConnection(connectionId: String): List<ModelBindingEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE modelBindingId IN (SELECT id FROM model_bindings WHERE connectionId = :connectionId)")
    suspend fun countSessionBindings(connectionId: String): Int

    @Query("SELECT COUNT(*) FROM turn_runs WHERE modelBindingId IN (SELECT id FROM model_bindings WHERE connectionId = :connectionId)")
    suspend fun countRunBindings(connectionId: String): Int

    @Query("DELETE FROM provider_connections WHERE id = :id")
    suspend fun deleteConnection(id: String): Int
}

@Dao
interface TurnRunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRun(run: TurnRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: ContextSnapshotEntity)

    @Query("SELECT * FROM turn_runs WHERE id = :id")
    suspend fun getById(id: String): TurnRunEntity?

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM turn_runs WHERE spaceId = :spaceId AND sessionId = :sessionId")
    suspend fun nextSequence(spaceId: String, sessionId: String): Long

    @Query("SELECT * FROM turn_runs WHERE turnId = :turnId ORDER BY attempt DESC, createdAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun findLatestByTurnId(turnId: String): TurnRunEntity?

    @Query("SELECT * FROM turn_runs WHERE parentTurnId = :parentTurnId AND state = 'Waiting' ORDER BY sequence ASC, attempt ASC")
    suspend fun findWaitingByParentTurnId(parentTurnId: String): List<TurnRunEntity>

    @Query("SELECT * FROM turn_runs WHERE sessionId = :sessionId ORDER BY sequence ASC, attempt ASC")
    suspend fun listBySession(sessionId: String): List<TurnRunEntity>

    @Query("SELECT * FROM turn_runs WHERE sessionId = :sessionId AND state IN ('Waiting', 'Running') ORDER BY sequence ASC, attempt ASC")
    suspend fun listActiveBySession(sessionId: String): List<TurnRunEntity>

    @Query("SELECT * FROM context_snapshots WHERE id = :id")
    suspend fun getSnapshot(id: String): ContextSnapshotEntity?

    @Query("DELETE FROM turn_runs WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int

    @Query("DELETE FROM context_snapshots WHERE sessionId = :sessionId")
    suspend fun deleteSnapshotsBySession(sessionId: String): Int
}

@Dao
interface LearningDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlanTask(task: StudyPlanTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeeklySummary(summary: WeeklySummaryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTokenUsage(record: TokenUsageRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWidgetPreference(preference: WidgetLayoutPreferenceEntity)

    @Query("SELECT * FROM study_plan_tasks WHERE spaceId = :spaceId ORDER BY updatedAtEpochMillis DESC")
    suspend fun listPlanTasks(spaceId: String): List<StudyPlanTaskEntity>

    @Query("SELECT * FROM weekly_summaries WHERE spaceId = :spaceId ORDER BY weekStartEpochMillis DESC")
    suspend fun listWeeklySummaries(spaceId: String): List<WeeklySummaryEntity>

    @Query(
        """
        SELECT * FROM weekly_summaries
        WHERE spaceId = :spaceId
          AND weekStartEpochMillis = :weekStartEpochMillis
          AND sourceRevision = :sourceRevision
          AND generatorVersion = :generatorVersion
        LIMIT 1
        """
    )
    suspend fun findWeeklySummary(
        spaceId: String,
        weekStartEpochMillis: Long,
        sourceRevision: Long,
        generatorVersion: String
    ): WeeklySummaryEntity?

    @Query("SELECT * FROM token_usage_records WHERE spaceId = :spaceId ORDER BY createdAtEpochMillis DESC")
    suspend fun listTokenUsage(spaceId: String): List<TokenUsageRecordEntity>

    @Query("SELECT * FROM widget_layout_preferences WHERE spaceId = :spaceId ORDER BY `order` ASC")
    suspend fun listWidgetPreferences(spaceId: String): List<WidgetLayoutPreferenceEntity>

    @Query("DELETE FROM study_plan_tasks WHERE sourceSessionId = :sessionId")
    suspend fun deletePlanTasksBySession(sessionId: String): Int
}

@Dao
interface SearchDocumentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: SearchDocumentEntity)

    @Query(
        """
        SELECT * FROM search_documents
        WHERE spaceId = :spaceId AND normalizedText LIKE '%' || :normalizedQuery || '%'
        ORDER BY updatedAtEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun search(spaceId: String, normalizedQuery: String, limit: Int): List<SearchDocumentEntity>

    @Query("DELETE FROM search_documents WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: String): Int
}

@Dao
interface SyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOutbox(envelope: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCursor(cursor: SyncCursorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConflict(conflict: SyncConflictEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTombstone(tombstone: EntityTombstoneEntity)

    @Query("SELECT * FROM sync_outbox WHERE status = 'Pending' AND nextAttemptAtEpochMillis <= :nowEpochMillis ORDER BY updatedAtEpochMillis ASC LIMIT :limit")
    suspend fun listReadyOutbox(nowEpochMillis: Long, limit: Int): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE id = :id")
    suspend fun getOutbox(id: String): SyncOutboxEntity?

    @Query(
        """
        UPDATE sync_outbox
        SET status = :status,
            retryCount = :retryCount,
            nextAttemptAtEpochMillis = :nextAttemptAtEpochMillis,
            lastError = :lastError
        WHERE id = :id
        """
    )
    suspend fun updateOutboxFailure(
        id: String,
        status: String,
        retryCount: Int,
        nextAttemptAtEpochMillis: Long,
        lastError: String
    ): Int

    @Query("SELECT * FROM sync_cursors WHERE spaceId = :spaceId AND entityType = :entityType LIMIT 1")
    suspend fun getCursor(spaceId: String, entityType: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_cursors WHERE entityType = :entityType ORDER BY updatedAtEpochMillis DESC LIMIT 1")
    suspend fun getLatestCursor(entityType: String): SyncCursorEntity?

    @Query("SELECT * FROM sync_conflicts WHERE spaceId = :spaceId AND state = 'Pending' ORDER BY createdAtEpochMillis ASC")
    suspend fun listPendingConflicts(spaceId: String): List<SyncConflictEntity>

    @Query("SELECT * FROM entity_tombstones WHERE entityType = :entityType AND entityId = :entityId LIMIT 1")
    suspend fun getTombstone(entityType: String, entityId: String): EntityTombstoneEntity?

    @Query("DELETE FROM sync_outbox WHERE id = :id")
    suspend fun deleteOutbox(id: String): Int
}
