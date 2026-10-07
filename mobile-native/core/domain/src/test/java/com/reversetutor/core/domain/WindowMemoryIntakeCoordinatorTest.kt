package com.reversetutor.core.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red slice V2-004: window-memory intake pipeline. Extraction happens only
 * when messages slide out of the sliding window (never per sentence), the
 * watermark guarantees each evicted batch is processed exactly once, and
 * observations evolve the window-level active values through the frozen
 * V2-002 policy. Rolling summary folds the evicted batch when enabled.
 */
class WindowMemoryIntakeCoordinatorTest {

    private fun <T> runBlockingTest(block: suspend () -> T): T = runBlocking { block() }

    private fun user(id: String, text: String, at: Long) =
        WindowIntakeMessage(messageId = id, role = ExtractionRole.USER, text = text, occurredAtEpochMillis = at)

    private fun assistant(id: String, text: String, at: Long) =
        WindowIntakeMessage(messageId = id, role = ExtractionRole.ASSISTANT, text = text, occurredAtEpochMillis = at)

    private fun casual(count: Int, startAt: Long, prefix: String = "c"): List<WindowIntakeMessage> =
        (0 until count).map { user(prefix + it + "-" + startAt, "嗯嗯好的", startAt + it) }

    private class FakeMessagePort(var messages: List<WindowIntakeMessage>) : WindowIntakeMessagePort {
        override suspend fun listMessages(sessionId: String): List<WindowIntakeMessage> = messages
    }

    private class FakeStore : WindowIntakeStore {
        val watermarks = mutableMapOf<String, WindowIntakeWatermark>()
        val observations = mutableListOf<WindowLocalObservation>()
        val activeValues = mutableMapOf<String, WindowActiveValue>()
        val summaries = mutableMapOf<String, String>()

        private fun key(sessionId: String, category: WindowObservationCategory, slotKey: String) =
            sessionId + "|" + category.name + "|" + slotKey

        override suspend fun loadWatermark(sessionId: String): WindowIntakeWatermark? = watermarks[sessionId]

        override suspend fun saveWatermark(sessionId: String, watermark: WindowIntakeWatermark) {
            watermarks[sessionId] = watermark
        }

        override suspend fun appendObservations(sessionId: String, observations: List<WindowLocalObservation>) {
            this.observations += observations
        }

        override suspend fun loadActiveValue(
            sessionId: String,
            category: WindowObservationCategory,            slotKey: String,
        ): WindowActiveValue? = activeValues[key(sessionId, category, slotKey)]

        override suspend fun saveActiveValue(sessionId: String, value: WindowActiveValue) {
            activeValues[key(sessionId, value.category, value.slotKey)] = value
        }

        override suspend fun loadRollingSummary(sessionId: String): String? = summaries[sessionId]

        override suspend fun saveRollingSummary(
            sessionId: String,
            summary: String,
            coversUntilMessageId: String,
            nowEpochMillis: Long,
        ) {
            summaries[sessionId] = summary
        }
    }

    private fun coordinator(
        port: FakeMessagePort,
        store: FakeStore,
        config: WindowMemoryConfig = WindowMemoryConfig(),
        foldSummary: (suspend (String, String?, List<WindowIntakeMessage>) -> String?)? = null,
    ) = WindowMemoryIntakeCoordinator(
        messagePort = port,
        store = store,
        config = config,
        hourOfDayAt = { 3 },
        foldSummary = foldSummary,
    )

    @Test
    fun `under window cap nothing is evicted and nothing is extracted`() {
        val port = FakeMessagePort(casual(5, 1000L))
        val store = FakeStore()
        val report = runBlockingTest { coordinator(port, store).onTurnCompleted("s1", 2000L) }
        assertEquals(5, report.windowKeptCount)
        assertEquals(0, report.evictedCount)
        assertEquals(0, report.observationsExtracted)
        assertTrue(store.observations.isEmpty())
        assertNull(store.watermarks["s1"])
    }

    @Test
    fun `over cap evicted goal message becomes active value and watermark advances`() {
        val messages = listOf(user("m0", "我要在这个月把圆锥曲线刷完", 1000L)) + casual(60, 1001L)
        val port = FakeMessagePort(messages)
        val store = FakeStore()
        val report = runBlockingTest { coordinator(port, store).onTurnCompleted("s1", 2000L) }
        assertEquals(1, report.evictedCount)
        assertEquals(1, report.newlyProcessedCount)
        assertEquals(1, report.observationsExtracted)
        assertEquals(1, report.createdCount)
        val active = store.activeValues.values.single()
        assertEquals(WindowObservationCategory.GOAL_STATEMENT, active.category)
        assertEquals("stated_goal", active.value)
        assertEquals("m0", store.watermarks["s1"]?.lastProcessedMessageId)
    }

    @Test
    fun `second run does not reprocess already handled evictions`() {
        val messages = listOf(user("m0", "我要在这个月把圆锥曲线刷完", 1000L)) + casual(60, 1001L)
        val port = FakeMessagePort(messages)
        val store = FakeStore()
        val c = coordinator(port, store)
        runBlockingTest { c.onTurnCompleted("s1", 2000L) }
        val second = runBlockingTest { c.onTurnCompleted("s1", 3000L) }
        assertEquals(0, second.newlyProcessedCount)
        assertEquals(0, second.observationsExtracted)
        assertEquals(1, store.observations.size)
    }

    @Test
    fun `repeated same goal reinforces existing active value`() {
        val messages = listOf(
            user("m0", "我要在这个月把圆锥曲线刷完", 1000L),
            user("m1", "我要在这个月把圆锥曲线刷完", 1001L),
        ) + casual(61, 1002L)
        val port = FakeMessagePort(messages)
        val store = FakeStore()
        val report = runBlockingTest { coordinator(port, store).onTurnCompleted("s1", 2000L) }
        assertEquals(3, report.newlyProcessedCount)
        assertEquals(1, report.createdCount)
        assertEquals(1, report.reinforcedCount)
        val active = store.activeValues.values.single()
        assertEquals(2L, active.revision)
        assertEquals(WindowActiveValuePolicy.MAX_WEIGHT, active.weight, 0.0001)
    }

    @Test
    fun `casual evicted batch advances watermark without observations`() {
        val port = FakeMessagePort(casual(61, 1000L))
        val store = FakeStore()
        val report = runBlockingTest { coordinator(port, store).onTurnCompleted("s1", 2000L) }
        assertEquals(1, report.evictedCount)
        assertEquals(1, report.newlyProcessedCount)
        assertEquals(0, report.observationsExtracted)
        assertEquals("c0-1000", store.watermarks["s1"]?.lastProcessedMessageId)
    }

    @Test
    fun `reminder feedback pair evicted together produces reminder active value`() {
        val messages = listOf(
            assistant("m0", "都凌晨三点了，快去睡觉吧", 1000L),
            user("m1", "解决完这个我就睡", 1001L),
        ) + casual(60, 1002L)
        val port = FakeMessagePort(messages)
        val store = FakeStore()
        val report = runBlockingTest { coordinator(port, store).onTurnCompleted("s1", 2000L) }
        assertEquals(2, report.newlyProcessedCount)
        assertEquals(1, report.observationsExtracted)
        val active = store.activeValues.values.single()
        assertEquals(WindowObservationCategory.REMINDER_FEEDBACK, active.category)
        assertTrue(active.weight >= 0.9)
        assertEquals("m1", store.watermarks["s1"]?.lastProcessedMessageId)
    }

    @Test
    fun `rolling summary folds each newly evicted batch with previous summary`() {
        val port = FakeMessagePort(casual(61, 1000L))
        val store = FakeStore()
        val foldInputs = mutableListOf<String?>()
        val fold: suspend (String, String?, List<WindowIntakeMessage>) -> String? = { _, current, evicted ->
            foldInputs += current
            "摘要覆盖" + evicted.last().messageId
        }
        val c = coordinator(port, store, foldSummary = fold)
        val first = runBlockingTest { c.onTurnCompleted("s1", 2000L) }
        assertTrue(first.summaryFolded)
        assertEquals(listOf(null), foldInputs)
        assertEquals("摘要覆盖c0-1000", store.summaries["s1"])

        port.messages = casual(62, 1000L)
        val second = runBlockingTest { c.onTurnCompleted("s1", 3000L) }
        assertTrue(second.summaryFolded)
        assertEquals(listOf(null, "摘要覆盖c0-1000"), foldInputs)
        assertEquals("摘要覆盖c1-1000", store.summaries["s1"])
    }

    @Test
    fun `summary fold skipped when disabled in config`() {
        val port = FakeMessagePort(casual(61, 1000L))
        val store = FakeStore()
        var foldCalled = false
        val fold: suspend (String, String?, List<WindowIntakeMessage>) -> String? = { _, _, _ ->
            foldCalled = true
            "x"
        }
        val c = coordinator(
            port,
            store,
            config = WindowMemoryConfig(rollingSummaryEnabled = false),
            foldSummary = fold,
        )
        val report = runBlockingTest { c.onTurnCompleted("s1", 2000L) }
        assertFalse(report.summaryFolded)
        assertFalse(foldCalled)
        assertNull(store.summaries["s1"])
    }

    @Test
    fun `token estimator counts cjk and ascii differently`() {
        assertEquals(4, WindowTokenEstimator.estimate("你好世界"))
        assertEquals(3, WindowTokenEstimator.estimate("hello world"))
        assertEquals(3, WindowTokenEstimator.estimate("你好ab"))
        assertEquals(1, WindowTokenEstimator.estimate(""))
    }
}
