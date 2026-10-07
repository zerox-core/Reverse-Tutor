package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.local.dao.WindowMemoryDao
import com.reversetutor.core.data.local.dao.WindowMemoryTokenMeterDao
import com.reversetutor.core.data.local.entity.WindowIntakeWatermarkEntity
import com.reversetutor.core.data.local.entity.WindowMemoryActiveValueEntity
import com.reversetutor.core.data.local.entity.WindowMemoryObservationEntity
import com.reversetutor.core.data.local.entity.WindowMemoryTokenMeterEntity
import com.reversetutor.core.data.local.entity.WindowRollingSummaryEntity
import com.reversetutor.core.data.windowmemory.WindowMemoryRepository
import com.reversetutor.core.data.windowmemory.WindowTokenMeterRepository
import com.reversetutor.core.domain.SignalSalience
import com.reversetutor.core.domain.WindowActiveValue
import com.reversetutor.core.domain.WindowObservationCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-006: the context port adapter turns stored window memory into the
 * injection block and meters every injection.
 */
class WindowMemoryContextWiringTest {

    private class FakeDao : WindowMemoryDao {
        val activeValues = mutableMapOf<String, WindowMemoryActiveValueEntity>()
        val observations = mutableListOf<WindowMemoryObservationEntity>()
        val summaries = mutableMapOf<String, WindowRollingSummaryEntity>()

        override suspend fun insertObservation(observation: WindowMemoryObservationEntity) {
            observations += observation
        }

        override suspend fun listObservations(sessionId: String): List<WindowMemoryObservationEntity> =
            observations.filter { it.sessionId == sessionId }

        override suspend fun upsertActiveValue(value: WindowMemoryActiveValueEntity) {
            activeValues[value.sessionId + value.category + value.slotKey] = value
        }

        override suspend fun getActiveValue(
            sessionId: String,
            category: String,
            slotKey: String,
        ): WindowMemoryActiveValueEntity? = activeValues[sessionId + category + slotKey]

        override suspend fun listActiveValues(sessionId: String): List<WindowMemoryActiveValueEntity> =
            activeValues.values.filter { it.sessionId == sessionId }

        override suspend fun upsertWatermark(watermark: WindowIntakeWatermarkEntity) {}

        override suspend fun getWatermark(sessionId: String): WindowIntakeWatermarkEntity? = null

        override suspend fun upsertRollingSummary(summary: WindowRollingSummaryEntity) {
            summaries[summary.sessionId] = summary
        }

        override suspend fun getRollingSummary(sessionId: String): WindowRollingSummaryEntity? =
            summaries[sessionId]
    }

    private class FakeMeterDao : WindowMemoryTokenMeterDao {
        val rows = mutableListOf<WindowMemoryTokenMeterEntity>()

        override suspend fun insertMeter(meter: WindowMemoryTokenMeterEntity) {
            rows += meter
        }

        override suspend fun listMeters(sessionId: String): List<WindowMemoryTokenMeterEntity> =
            rows.filter { it.sessionId == sessionId }
    }

    private fun adapter(
        repository: WindowMemoryRepository,
        meterRepository: WindowTokenMeterRepository,
    ) = WindowMemoryContextPortAdapter(
        repository = repository,
        meterRepository = meterRepository,
        hourOfDayAt = { 2 },
        nowEpochMillis = { 99L },
    )

    @Test
    fun adapterBuildsInjectionBlockFromStoredMemory() = runBlocking {
        val repository = WindowMemoryRepository(FakeDao())
        repository.saveActiveValue(
            "ses",
            WindowActiveValue(
                category = WindowObservationCategory.GOAL_STATEMENT,
                slotKey = "GOAL_STATEMENT",
                value = "stated_goal",
                revision = 1L,
                weight = 0.9,
                sourceClass = "USER_STATEMENT",
                provenanceHandle = "turn:m1",
                updatedAtEpochMillis = 1L,
            ),
        )
        repository.saveRollingSummary("ses", "早些时候聊了函数。", "m9", 5L)
        val meterRepository = WindowTokenMeterRepository(FakeMeterDao())

        val text = adapter(repository, meterRepository)
            .loadWindowMemoryContext("space", "ses", "目标")

        assertTrue(text.contains("【窗口记忆】"))
        assertTrue(text.contains("学习目标"))
        assertTrue(text.contains("早些时候聊了函数。"))
    }

    @Test
    fun adapterMetersEveryNonEmptyInjection() = runBlocking {
        val repository = WindowMemoryRepository(FakeDao())
        repository.saveActiveValue(
            "ses",
            WindowActiveValue(
                category = WindowObservationCategory.GOAL_STATEMENT,
                slotKey = "GOAL_STATEMENT",
                value = "stated_goal",
                revision = 1L,
                weight = 0.9,
                sourceClass = "USER_STATEMENT",
                provenanceHandle = "turn:m1",
                updatedAtEpochMillis = 1L,
            ),
        )
        val meterDao = FakeMeterDao()
        val meterRepository = WindowTokenMeterRepository(meterDao)

        adapter(repository, meterRepository).loadWindowMemoryContext("space", "ses", "")

        assertEquals(1, meterDao.rows.size)
        assertEquals("injection", meterDao.rows[0].kind)
        assertEquals(99L, meterDao.rows[0].createdAtEpochMillis)
        assertTrue(meterDao.rows[0].estimatedTokens > 0)
    }

    @Test
    fun emptySessionYieldsBlankAndNoMetering() = runBlocking {
        val repository = WindowMemoryRepository(FakeDao())
        val meterDao = FakeMeterDao()
        val meterRepository = WindowTokenMeterRepository(meterDao)

        val text = adapter(repository, meterRepository).loadWindowMemoryContext("space", "ses", "")

        assertEquals("", text)
        assertTrue(meterDao.rows.isEmpty())
    }

    @Test
    fun observationsAggregateIntoPatternLines() = runBlocking {
        val dao = FakeDao()
        val repository = WindowMemoryRepository(dao)
        dao.observations += WindowMemoryObservationEntity(
            id = "o1",
            sessionId = "ses",
            category = "ACTIVITY_CONTEXT",
            slotKey = "ACTIVITY_CONTEXT",
            value = "late_night_coding",
            sourceClass = "BEHAVIOR_OBSERVATION",
            confidence = 0.6,
            salience = SignalSalience.HIGH.name,
            occurredAtEpochMillis = 1L,
            provenanceHandle = "turn:m1",
        )
        val meterRepository = WindowTokenMeterRepository(FakeMeterDao())

        val text = adapter(repository, meterRepository).loadWindowMemoryContext("space", "ses", "")

        assertTrue(text.contains("模式"))
        assertTrue(text.contains("1 次"))
    }
}
