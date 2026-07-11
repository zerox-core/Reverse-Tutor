package com.reversetutor.core.data.learning

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.domain.LearningInsightRepository
import com.reversetutor.core.domain.StudyPlanRepository
import com.reversetutor.core.domain.TokenUsageRepository
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.SyncEnvelope
import com.reversetutor.core.model.TokenUsageRecord
import com.reversetutor.core.model.WeeklySummary
import com.reversetutor.core.model.WidgetLayoutPreference

class LearningRepositoryImpl(
    private val database: ReverseTutorDatabase
) : StudyPlanRepository, LearningInsightRepository, TokenUsageRepository {
    override suspend fun listTasks(spaceId: String): List<StudyPlanTask> =
        database.learningDao().listPlanTasks(spaceId).map { it.toDomain() }

    override suspend fun saveTask(task: StudyPlanTask): StudyPlanTask {
        database.learningDao().upsertPlanTask(task.toEntity())
        return task
    }

    override suspend fun findWeeklySummary(
        spaceId: String,
        weekStartEpochMillis: Long,
        sourceRevision: Long,
        generatorVersion: String
    ): WeeklySummary? =
        database.learningDao().findWeeklySummary(
            spaceId,
            weekStartEpochMillis,
            sourceRevision,
            generatorVersion
        )?.toDomain()

    override suspend fun saveWeeklySummary(summary: WeeklySummary): WeeklySummary {
        database.learningDao().upsertWeeklySummary(summary.toEntity())
        return summary
    }

    override suspend fun saveUsage(record: TokenUsageRecord): TokenUsageRecord {
        database.learningDao().insertTokenUsage(record.toEntity())
        return record
    }

    suspend fun savePlanTask(task: StudyPlanTask, outbox: SyncEnvelope? = null) {
        require(outbox == null || outbox.entityId == task.id)
        database.withTransaction {
            database.learningDao().upsertPlanTask(task.toEntity())
            outbox?.let { database.syncDao().upsertOutbox(it.toEntity()) }
        }
    }

    suspend fun listPlanTasks(spaceId: String): List<StudyPlanTask> =
        listTasks(spaceId)

    suspend fun listWeeklySummaries(spaceId: String): List<WeeklySummary> =
        database.learningDao().listWeeklySummaries(spaceId).map { it.toDomain() }

    suspend fun recordTokenUsage(record: TokenUsageRecord): Boolean =
        database.learningDao().insertTokenUsage(record.toEntity()) != -1L

    suspend fun listTokenUsage(spaceId: String): List<TokenUsageRecord> =
        database.learningDao().listTokenUsage(spaceId).map { it.toDomain() }

    suspend fun saveWidgetPreference(preference: WidgetLayoutPreference, outbox: SyncEnvelope? = null) {
        require(outbox == null || outbox.entityId == preference.widgetId)
        database.withTransaction {
            database.learningDao().upsertWidgetPreference(preference.toEntity())
            outbox?.let { database.syncDao().upsertOutbox(it.toEntity()) }
        }
    }

    suspend fun listWidgetPreferences(spaceId: String): List<WidgetLayoutPreference> =
        database.learningDao().listWidgetPreferences(spaceId).map { it.toDomain() }
}
