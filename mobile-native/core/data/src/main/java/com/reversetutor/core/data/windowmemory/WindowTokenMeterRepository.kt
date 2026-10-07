package com.reversetutor.core.data.windowmemory

import com.reversetutor.core.data.local.dao.WindowMemoryTokenMeterDao
import com.reversetutor.core.data.local.entity.WindowMemoryTokenMeterEntity
import java.util.UUID

/** One window-memory metering event (kept-window size or injected context size). */
data class WindowTokenMeterRecord(
    val kind: String,
    val estimatedTokens: Int,
    val detail: String,
    val createdAtEpochMillis: Long,
)

/**
 * V2-006: window-memory token metering (schema 14 -> 15). Budget-tuning
 * telemetry for the memory subsystem itself; deliberately separate from
 * token_usage_records, which meters individual LLM calls per turn attempt.
 */
class WindowTokenMeterRepository(
    private val dao: WindowMemoryTokenMeterDao,
    private val meterIdFactory: () -> String = { UUID.randomUUID().toString() },
) {

    suspend fun recordTokenMeter(
        sessionId: String,
        kind: String,
        estimatedTokens: Int,
        detail: String,
        createdAtEpochMillis: Long,
    ) {
        dao.insertMeter(
            WindowMemoryTokenMeterEntity(
                id = meterIdFactory(),
                sessionId = sessionId,
                kind = kind,
                estimatedTokens = estimatedTokens,
                detail = detail,
                createdAtEpochMillis = createdAtEpochMillis,
            )
        )
    }

    suspend fun listTokenMeters(sessionId: String): List<WindowTokenMeterRecord> =
        dao.listMeters(sessionId).map {
            WindowTokenMeterRecord(
                kind = it.kind,
                estimatedTokens = it.estimatedTokens,
                detail = it.detail,
                createdAtEpochMillis = it.createdAtEpochMillis,
            )
        }
}
