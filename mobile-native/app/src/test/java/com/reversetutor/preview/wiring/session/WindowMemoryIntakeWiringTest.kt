package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.local.dao.WindowMemoryDao
import com.reversetutor.core.data.local.entity.WindowIntakeWatermarkEntity
import com.reversetutor.core.data.local.entity.WindowMemoryActiveValueEntity
import com.reversetutor.core.data.local.entity.WindowMemoryObservationEntity
import com.reversetutor.core.data.local.entity.WindowRollingSummaryEntity
import com.reversetutor.core.data.llm.SessionSummaryOutcome
import com.reversetutor.core.data.windowmemory.WindowMemoryRepository
import com.reversetutor.core.domain.ExtractionRole
import com.reversetutor.core.domain.WindowIntakeMessage
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-004: intake wiring tests (locked decision #9 - batch extraction off the
 * chat loop, failures never surface to the turn path).
 */
class WindowMemoryIntakeWiringTest {

    private class FakeWindowMemoryDao : WindowMemoryDao {
        val observations = mutableListOf<WindowMemoryObservationEntity>()
        val activeValues = mutableMapOf<String, WindowMemoryActiveValueEntity>()
        val watermarks = mutableMapOf<String, WindowIntakeWatermarkEntity>()
        val summaries = mutableMapOf<String, WindowRollingSummaryEntity>()

        override suspend fun insertObservation(observation: WindowMemoryObservationEntity) {
            observations += observation
        }

        override suspend fun listObservations(sessionId: String): List<WindowMemoryObservationEntity> =
            observations.filter { it.sessionId == sessionId }.sortedBy { it.occurredAtEpochMillis }

        override suspend fun upsertActiveValue(value: WindowMemoryActiveValueEntity) {
            activeValues[value.sessionId + "|" + value.category + "|" + value.slotKey] = value
        }

        override suspend fun getActiveValue(
            sessionId: String,
            category: String,
            slotKey: String,
        ): WindowMemoryActiveValueEntity? = activeValues[sessionId + "|" + category + "|" + slotKey]

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

    private fun message(id: String, role: MessageRole, text: String, at: Long) = Message(
        id = id,
        spaceId = "space",
        sessionId = "ses",
        role = role,
        text = text,
        createdAtEpochMillis = at,
    )

    @Test
    fun messagePortMapsStoredMessagesIntoIntakeView() = runBlocking {
        val stored = listOf(
            message("m1", MessageRole.User, "我要学数学", 1L),
            message("m2", MessageRole.Assistant, "好的", 2L),
            message("m3", MessageRole.System, "系统提示", 3L),
        )
        val port = WindowIntakeMessagePortAdapter { stored }

        val listed = port.listMessages("ses")

        assertEquals(3, listed.size)
        assertEquals("m1", listed[0].messageId)
        assertEquals(ExtractionRole.USER, listed[0].role)
        assertEquals(ExtractionRole.ASSISTANT, listed[1].role)
        assertEquals(ExtractionRole.ASSISTANT, listed[2].role)
        assertEquals(2L, listed[1].occurredAtEpochMillis)
    }

    @Test
    fun dispatcherRunsRunnerOffCallerAndSwallowsRunnerFailure() = runBlocking {
        val calls = mutableListOf<String>()
        val completed = CompletableDeferred<Unit>()
        val runner = WindowIntakeRunner { sessionId, _ ->
            if (sessionId == "boom") throw IllegalStateException("intake failed")
            calls += sessionId
            completed.complete(Unit)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val dispatcher = WindowIntakeDispatcher(runner, scope, nowEpochMillis = { 7L })

            dispatcher.dispatch("boom")
            dispatcher.dispatch("ses-ok")

            withTimeout(5000) { completed.await() }
            assertEquals(listOf("ses-ok"), calls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun storeAdapterExposesRollingSummaryTextAndDelegatesWatermark() = runBlocking {
        val repository = WindowMemoryRepository(FakeWindowMemoryDao())
        repository.saveRollingSummary("ses", "旧摘要", "m9", 5L)
        val store = WindowIntakeStoreAdapter(repository)

        assertEquals("旧摘要", store.loadRollingSummary("ses"))
        assertNull(store.loadWatermark("ses"))
    }

    @Test
    fun foldSummaryPassesBatchIntoPromptAndMapsGeneratedText() = runBlocking {
        var capturedSessionId: String? = null
        var capturedPrompt: String? = null
        val fold = windowIntakeFoldSummary { sessionId, prompt ->
            capturedSessionId = sessionId
            capturedPrompt = prompt
            SessionSummaryOutcome.Generated("合并后的摘要")
        }
        val batch = listOf(
            WindowIntakeMessage("m1", ExtractionRole.USER, "我要学数学", 1L),
            WindowIntakeMessage("m2", ExtractionRole.ASSISTANT, "好的", 2L),
        )

        val folded = fold("ses", "旧摘要", batch)

        assertEquals("合并后的摘要", folded)
        assertEquals("ses", capturedSessionId)
        assertNotNull(capturedPrompt)
        assertTrue(capturedPrompt!!.contains("旧摘要"))
        assertTrue(capturedPrompt!!.contains("我要学数学"))
        assertTrue(capturedPrompt!!.contains("好的"))
    }

    @Test
    fun foldSummaryReturnsNullWhenGenerationFails() = runBlocking {
        val fold = windowIntakeFoldSummary { _, _ ->
            SessionSummaryOutcome.ProviderFailed("llm_provider_timeout")
        }
        val batch = listOf(WindowIntakeMessage("m1", ExtractionRole.USER, "测试", 1L))

        assertNull(fold("ses", null, batch))
    }
}
