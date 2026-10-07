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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowMemoryRepositoryTest {

    private fun observation(
        value: String,
        category: WindowObservationCategory = WindowObservationCategory.GOAL_STATEMENT,
        slotKey: String = "goal",
        at: Long = 1_000L,
    ) = WindowLocalObservation(
        category = category,
        slotKey = slotKey,
        value = value,
        sourceClass = "USER_STATEMENT",
        confidence = 0.9,
        salience = SignalSalience.HIGH,
        occurredAtEpochMillis = at,
        provenanceHandle = "turn:m-$at",
    )

    @Test
    fun observationsRoundTripInChronologicalOrder() = runBlocking {
        val dao = FakeWindowMemoryDao()
        val repo = WindowMemoryRepository(dao)
        repo.appendObservations(
            "s-1",
            listOf(observation("先学导数", at = 2_000L), observation("再刷题", at = 1_000L)),
        )

        val stored = repo.listObservations("s-1")
        assertEquals(2, stored.size)
        assertEquals(1_000L, stored[0].occurredAtEpochMillis)
        assertEquals("再刷题", stored[0].value)
        assertEquals(2_000L, stored[1].occurredAtEpochMillis)
        assertEquals(SignalSalience.HIGH, stored[1].salience)
        assertEquals(0.9, stored[1].confidence, 0.0001)
        assertEquals("turn:m-2000", stored[1].provenanceHandle)
    }

    @Test
    fun observationsAreIsolatedPerSession() = runBlocking {
        val dao = FakeWindowMemoryDao()
        val repo = WindowMemoryRepository(dao)
        repo.appendObservations("s-1", listOf(observation("目标甲")))
        assertEquals(0, repo.listObservations("s-2").size)
    }

    @Test
    fun activeValueSaveLoadAndSlotIsolation() = runBlocking {
        val dao = FakeWindowMemoryDao()
        val repo = WindowMemoryRepository(dao)
        val value = WindowActiveValue(
            category = WindowObservationCategory.GOAL_STATEMENT,
            slotKey = "goal",
            value = "这周拿下导数",
            revision = 1L,
            weight = 0.9,
            sourceClass = "USER_STATEMENT",
            provenanceHandle = "turn:m-1",
            updatedAtEpochMillis = 5_000L,
        )
        repo.saveActiveValue("s-1", value)

        assertEquals(value, repo.loadActiveValue("s-1", WindowObservationCategory.GOAL_STATEMENT, "goal"))
        assertNull(repo.loadActiveValue("s-1", WindowObservationCategory.PREFERENCE_SIGNAL, "goal"))
        assertNull(repo.loadActiveValue("s-2", WindowObservationCategory.GOAL_STATEMENT, "goal"))
    }

    @Test
    fun activeValueUpsertReplacesSameSlot() = runBlocking {
        val dao = FakeWindowMemoryDao()
        val repo = WindowMemoryRepository(dao)
        fun slot(value: String, revision: Long) = WindowActiveValue(
            category = WindowObservationCategory.GOAL_STATEMENT,
            slotKey = "goal",
            value = value,
            revision = revision,
            weight = 0.5,
            sourceClass = "USER_STATEMENT",
            provenanceHandle = "turn:m-1",
            updatedAtEpochMillis = 1_000L,
        )
        repo.saveActiveValue("s-1", slot("旧目标", 1L))
        repo.saveActiveValue("s-1", slot("新目标", 2L))

        assertEquals("新目标", repo.loadActiveValue("s-1", WindowObservationCategory.GOAL_STATEMENT, "goal")!!.value)
        assertEquals(1, repo.listActiveValues("s-1").size)
    }

    @Test
    fun watermarkRoundTrip() = runBlocking {
        val dao = FakeWindowMemoryDao()
        val repo = WindowMemoryRepository(dao)
        assertNull(repo.loadWatermark("s-1"))
        repo.saveWatermark("s-1", WindowIntakeWatermark("m-9", 9_000L))
        assertEquals(WindowIntakeWatermark("m-9", 9_000L), repo.loadWatermark("s-1"))
    }

    @Test
    fun rollingSummaryRoundTripAndOverwrite() = runBlocking {
        val dao = FakeWindowMemoryDao()
        val repo = WindowMemoryRepository(dao)
        assertNull(repo.loadRollingSummary("s-1"))
        repo.saveRollingSummary("s-1", "摘要一", "m-3", 100L)
        repo.saveRollingSummary("s-1", "摘要二", "m-6", 200L)
        val record = repo.loadRollingSummary("s-1")!!
        assertEquals("摘要二", record.summary)
        assertEquals("m-6", record.coversUntilMessageId)
        assertEquals(200L, record.updatedAtEpochMillis)
    }

    private class FakeWindowMemoryDao : WindowMemoryDao {
        val observations = mutableListOf<WindowMemoryObservationEntity>()
        val activeValues = mutableMapOf<String, WindowMemoryActiveValueEntity>()
        val watermarks = mutableMapOf<String, WindowIntakeWatermarkEntity>()
        val summaries = mutableMapOf<String, WindowRollingSummaryEntity>()

        override suspend fun insertObservation(observation: WindowMemoryObservationEntity) {
            observations.add(observation)
        }

        override suspend fun listObservations(sessionId: String): List<WindowMemoryObservationEntity> =
            observations.filter { it.sessionId == sessionId }.sortedBy { it.occurredAtEpochMillis }

        override suspend fun upsertActiveValue(value: WindowMemoryActiveValueEntity) {
            activeValues["${value.sessionId}|${value.category}|${value.slotKey}"] = value
        }

        override suspend fun getActiveValue(
            sessionId: String,
            category: String,
            slotKey: String,
        ): WindowMemoryActiveValueEntity? = activeValues["$sessionId|$category|$slotKey"]

        override suspend fun listActiveValues(sessionId: String): List<WindowMemoryActiveValueEntity> =
            activeValues.values.filter { it.sessionId == sessionId }

        override suspend fun upsertWatermark(watermark: WindowIntakeWatermarkEntity) {
            watermarks[watermark.sessionId] = watermark
        }

        override suspend fun getWatermark(sessionId: String): WindowIntakeWatermarkEntity? = watermarks[sessionId]

        override suspend fun upsertRollingSummary(summary: WindowRollingSummaryEntity) {
            summaries[summary.sessionId] = summary
        }

        override suspend fun getRollingSummary(sessionId: String): WindowRollingSummaryEntity? = summaries[sessionId]
    }
}
