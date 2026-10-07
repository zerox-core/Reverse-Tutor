package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.llm.SessionSummaryOutcome
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.model.DailySummary
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.TokenUsageRecord
import java.util.TimeZone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DailySummaryGenerator] decision matrix + background persistence.
 * 全部走假模型与内存接缝，不烧真实模型额度（V1 铁律：测试不烧真钱）。
 */
class DailySummaryGeneratorTest {
    private val dayMillis = 24L * 60L * 60L * 1_000L
    private val now = 8L * dayMillis + 12L * 60L * 60L * 1_000L
    private val today = 8L * dayMillis

    @Test
    fun emptyDaySkipsGenerationWithoutTouchingModel() = runBlocking {
        var modelCalls = 0
        var saves = 0
        val generator = generator(
            facts = listOf(fact("kp-old", occurredAt = 1L * dayMillis)),
            generateSummary = { _, _ ->
                modelCalls++
                SessionSummaryOutcome.Generated("x")
            },
            saveStored = { saves++ }
        )

        assertEquals(DailySummaryRequestResult.SkippedNoActivity, generator.requestGeneration("space-1"))
        assertEquals(0, modelCalls)
        assertEquals(0, saves)
    }

    @Test
    fun freshStoredSummaryShortCircuitsBeforeModel() = runBlocking {
        var modelCalls = 0
        val generator = generator(
            facts = listOf(
                fact("kp-a", occurredAt = today),
                fact("kp-b", occurredAt = today)
            ),
            generateSummary = { _, _ ->
                modelCalls++
                SessionSummaryOutcome.Generated("x")
            },
            findStoredResult = stored(sourceRevision = 2L, summary = "已生成")
        )

        assertEquals(DailySummaryRequestResult.AlreadyFresh, generator.requestGeneration("space-1"))
        assertEquals(0, modelCalls)
    }

    @Test
    fun belowRegenThresholdIsAlreadyFresh() = runBlocking {
        val generator = generator(
            facts = List(11) { fact("kp-$it", occurredAt = today) },
            findStoredResult = stored(sourceRevision = 2L)
        )

        assertEquals(DailySummaryRequestResult.AlreadyFresh, generator.requestGeneration("space-1"))
    }

    @Test
    fun regeneratesAndSavesWhenEvidenceGrowsBeyondThreshold() = runBlocking {
        val saved = mutableListOf<DailySummary>()
        val generator = generator(
            facts = List(12) { fact("kp-$it", occurredAt = today) },
            findStoredResult = stored(sourceRevision = 2L),
            generateSummary = { _, prompt ->
                assertTrue(prompt.contains("12"))
                SessionSummaryOutcome.Generated("你今天把 12 条证据都过了一遍。")
            },
            saveStored = { saved.add(it) }
        )

        assertEquals(DailySummaryRequestResult.Started, generator.requestGeneration("space-1"))
        assertEquals(1, saved.size)
        val summary = saved[0]
        assertEquals("daily-space-1-$today-${DailySummaryGenerator.GeneratorVersion}", summary.id)
        assertEquals("space-1", summary.spaceId)
        assertEquals(today, summary.dayStartEpochMillis)
        assertEquals(today + dayMillis - 1L, summary.dayEndEpochMillis)
        assertEquals(12L, summary.sourceRevision)
        assertEquals("你今天把 12 条证据都过了一遍。", summary.summary)
        assertEquals(now, summary.generatedAtEpochMillis)
        assertFalse(summary.stale)
    }

    @Test
    fun providerFailureMarksFailedAndBlocksRetryInMemory() = runBlocking {
        var modelCalls = 0
        val state = DailySummaryGenerationState()
        val generator = generator(
            facts = listOf(fact("kp-a", occurredAt = today)),
            generateSummary = { _, _ ->
                modelCalls++
                SessionSummaryOutcome.ProviderFailed("llm_provider_timeout")
            },
            state = state
        )

        assertEquals(DailySummaryRequestResult.Started, generator.requestGeneration("space-1"))
        assertTrue(state.isFailed(dailySummaryKey("space-1", today)))
        assertEquals(DailySummaryRequestResult.FailedRecently, generator.requestGeneration("space-1"))
        assertEquals(1, modelCalls)
    }

    @Test
    fun pipelineExceptionMarksFailedWithoutSaving() = runBlocking {
        var saves = 0
        val state = DailySummaryGenerationState()
        val generator = generator(
            facts = listOf(fact("kp-a", occurredAt = today)),
            planTasks = { throw RuntimeException("plan read error") },
            saveStored = { saves++ },
            state = state
        )

        assertEquals(DailySummaryRequestResult.Started, generator.requestGeneration("space-1"))
        assertTrue(state.isFailed(dailySummaryKey("space-1", today)))
        assertEquals(0, saves)
    }

    @Test
    fun noSessionReturnsNoSession() = runBlocking {
        val generator = generator(
            facts = listOf(fact("kp-a", occurredAt = today)),
            sessionId = null
        )

        assertEquals(DailySummaryRequestResult.NoSession, generator.requestGeneration("space-1"))
    }

    @Test
    fun secondRequestWhileInFlightReturnsAlreadyInFlight() = runBlocking {
        val state = DailySummaryGenerationState()
        val gate = CompletableDeferred<Unit>()
        var saves = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val generator = generator(
                facts = listOf(fact("kp-a", occurredAt = today)),
                generateSummary = { _, _ ->
                    gate.await()
                    SessionSummaryOutcome.Generated("ok")
                },
                saveStored = { saves++ },
                state = state,
                scope = scope
            )

            assertEquals(DailySummaryRequestResult.Started, generator.requestGeneration("space-1"))
            assertTrue(state.isInFlight(dailySummaryKey("space-1", today)))
            assertEquals(DailySummaryRequestResult.AlreadyInFlight, generator.requestGeneration("space-1"))

            gate.complete(Unit)
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            assertEquals(1, saves)
            assertFalse(state.isInFlight(dailySummaryKey("space-1", today)))
        } finally {
            scope.cancel()
        }
    }

    // --- helpers -----------------------------------------------------------

    private fun generator(
        facts: List<LearningFactReceipt> = emptyList(),
        generateSummary: suspend (String, String) -> SessionSummaryOutcome = { _, _ ->
            SessionSummaryOutcome.Generated("今天的总结文本")
        },
        findStoredResult: DailySummary? = null,
        saveStored: suspend (DailySummary) -> Unit = {},
        planTasks: (suspend (String) -> List<StudyPlanTask>)? = null,
        tokenUsage: List<TokenUsageRecord> = emptyList(),
        sessionId: String? = "session-a",
        state: DailySummaryGenerationState = DailySummaryGenerationState(),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    ): DailySummaryGenerator = DailySummaryGenerator(
        generateSummary = generateSummary,
        findStored = { _, _ -> findStoredResult },
        saveStored = saveStored,
        listLearningFacts = { facts },
        listPlanTasks = planTasks ?: { emptyList() },
        listTokenUsage = { tokenUsage },
        pickSessionId = { sessionId },
        state = state,
        generationScope = scope,
        nowEpochMillis = { now },
        timeZone = TimeZone.getTimeZone("UTC")
    )

    private fun stored(
        sourceRevision: Long,
        summary: String = ""
    ): DailySummary = DailySummary(
        id = "s1",
        spaceId = "space-1",
        dayStartEpochMillis = today,
        sourceRevision = sourceRevision,
        generatorVersion = DailySummaryGenerator.GeneratorVersion,
        summary = summary,
        stale = false
    )

    private fun fact(
        knowledgePoint: String,
        evidenceType: String = "explanation",
        occurredAt: Long
    ): LearningFactReceipt = LearningFactReceipt(
        knowledgePoint = knowledgePoint,
        evidenceType = evidenceType,
        result = "passed",
        confidence = 1f,
        sourceWindowId = "session-a",
        sourceTurnId = "turn-$knowledgePoint-$occurredAt-$evidenceType",
        occurredAtEpochMillis = occurredAt
    )
}
