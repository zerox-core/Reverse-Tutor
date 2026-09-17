package com.reversetutor.core.data.windowmemory

import com.reversetutor.core.data.local.dao.WindowMemoryDao
import com.reversetutor.core.data.local.entity.WindowIntakeWatermarkEntity
import com.reversetutor.core.data.local.entity.WindowMemoryActiveValueEntity
import com.reversetutor.core.data.local.entity.WindowMemoryObservationEntity
import com.reversetutor.core.data.local.entity.WindowRollingSummaryEntity
import com.reversetutor.core.domain.SignalSalience
import com.reversetutor.core.domain.WindowActiveValue
import com.reversetutor.core.domain.WindowIntakeWatermark
import com.reversetutor.core.domain.WindowLocalObservation
import com.reversetutor.core.domain.WindowObservationCategory
import java.util.UUID

/**
 * V2-004: window-memory persistence. Exposes only `core:domain` contracts;
 * entity mapping stays private to this file.
 *
 * Layers covered (see docs/NEWMP-V2-memory-decisions.md):
 *  - Layer 1 observations: append-only, session-scoped, chronological.
 *  - Layer 3 active values: one row per (session, category, slot).
 *  - Intake watermark: exactly-once batch processing across app restarts.
 *  - Rolling summary: folded text of evicted raw turns (Layer 0 compression).
 *
 * Layer 2 patterns are read-time aggregates over observations
 * ([com.reversetutor.core.domain.WindowMemoryPolicy.aggregatePatterns]) and
 * need no table of their own.
 */

/** Stored rolling summary row, exposed without the entity type. */
data class WindowRollingSummaryRecord(
    val summary: String,
    val coversUntilMessageId: String,
    val updatedAtEpochMillis: Long,
)

class WindowMemoryRepository(
    private val dao: WindowMemoryDao,
    private val observationIdFactory: () -> String = { "wmo-" + UUID.randomUUID().toString() },
) {

    suspend fun appendObservations(sessionId: String, observations: List<WindowLocalObservation>) {
        observations.forEach { observation -> dao.insertObservation(observation.toEntity(sessionId)) }
    }

    suspend fun listObservations(sessionId: String): List<WindowLocalObservation> =
        dao.listObservations(sessionId).map { it.toDomain() }

    suspend fun loadActiveValue(
        sessionId: String,
        category: WindowObservationCategory,
        slotKey: String,
    ): WindowActiveValue? = dao.getActiveValue(sessionId, category.name, slotKey)?.toDomain()

    suspend fun saveActiveValue(sessionId: String, value: WindowActiveValue) {
        dao.upsertActiveValue(value.toEntity(sessionId))
    }

    suspend fun listActiveValues(sessionId: String): List<WindowActiveValue> =
        dao.listActiveValues(sessionId).map { it.toDomain() }

    suspend fun loadWatermark(sessionId: String): WindowIntakeWatermark? =
        dao.getWatermark(sessionId)?.toDomain()

    suspend fun saveWatermark(sessionId: String, watermark: WindowIntakeWatermark) {
        dao.upsertWatermark(watermark.toEntity(sessionId))
    }

    suspend fun loadRollingSummary(sessionId: String): WindowRollingSummaryRecord? =
        dao.getRollingSummary(sessionId)?.let {
            WindowRollingSummaryRecord(
                summary = it.summary,
                coversUntilMessageId = it.coversUntilMessageId,
                updatedAtEpochMillis = it.updatedAtEpochMillis,
            )
        }

    suspend fun saveRollingSummary(
        sessionId: String,
        summary: String,
        coversUntilMessageId: String,
        nowEpochMillis: Long,
    ) {
        dao.upsertRollingSummary(
            WindowRollingSummaryEntity(
                sessionId = sessionId,
                summary = summary,
                coversUntilMessageId = coversUntilMessageId,
                updatedAtEpochMillis = nowEpochMillis,
            )
        )
    }

    private fun WindowLocalObservation.toEntity(sessionId: String) = WindowMemoryObservationEntity(
        id = observationIdFactory(),
        sessionId = sessionId,
        category = category.name,
        slotKey = slotKey,
        value = value,
        sourceClass = sourceClass,
        confidence = confidence,
        salience = salience.name,
        occurredAtEpochMillis = occurredAtEpochMillis,
        provenanceHandle = provenanceHandle,
    )

    private fun WindowMemoryObservationEntity.toDomain() = WindowLocalObservation(
        category = WindowObservationCategory.valueOf(category),
        slotKey = slotKey,
        value = value,
        sourceClass = sourceClass,
        confidence = confidence,
        salience = SignalSalience.valueOf(salience),
        occurredAtEpochMillis = occurredAtEpochMillis,
        provenanceHandle = provenanceHandle,
    )

    private fun WindowActiveValue.toEntity(sessionId: String) = WindowMemoryActiveValueEntity(
        sessionId = sessionId,
        category = category.name,
        slotKey = slotKey,
        value = value,
        revision = revision,
        weight = weight,
        sourceClass = sourceClass,
        provenanceHandle = provenanceHandle,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun WindowMemoryActiveValueEntity.toDomain() = WindowActiveValue(
        category = WindowObservationCategory.valueOf(category),
        slotKey = slotKey,
        value = value,
        revision = revision,
        weight = weight,
        sourceClass = sourceClass,
        provenanceHandle = provenanceHandle,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )

    private fun WindowIntakeWatermark.toEntity(sessionId: String) = WindowIntakeWatermarkEntity(
        sessionId = sessionId,
        lastProcessedMessageId = lastProcessedMessageId,
        lastProcessedEpochMillis = lastProcessedEpochMillis,
    )

    private fun WindowIntakeWatermarkEntity.toDomain() = WindowIntakeWatermark(
        lastProcessedMessageId = lastProcessedMessageId,
        lastProcessedEpochMillis = lastProcessedEpochMillis,
    )
}
