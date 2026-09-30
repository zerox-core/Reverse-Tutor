package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.DailySummaryAiState
import com.reversetutor.core.domain.DailySummaryContract
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.model.DailySummary
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.TokenUsageRecord
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LearningOverviewDailySummaryPortAdapter] tests: 确定性统计折叠、
 * 掌握度跨越计数、会话过滤、AI 总结段状态映射、无接缝默认契约。
 * 仓库传 null、走 lambda 接缝——不依赖 Room。
 */
class DailySummaryPortAdapterTest {
    private val dayMillis = 24L * 60L * 60L * 1_000L
    private val now = 8L * dayMillis + 12L * 60L * 60L * 1_000L
    private val today = 8L * dayMillis

    @Test
    fun statsFoldCountsEvidencePlansAndTokensForToday() = runBlocking {
        val adapter = adapter(
            facts = listOf(
                fact("kp-a", occurredAt = today),
                fact("kp-b", evidenceType = "correction", occurredAt = today + 1_000L),
                fact("kp-b", evidenceType = "correction", occurredAt = today + 2_000L, result = "failed"),
                fact("kp-old", occurredAt = 1L * dayMillis)
            ),
            planTasks = listOf(
                planTask("t-done", completedAt = today),
                planTask("t-yesterday", completedAt = 7L * dayMillis),
                planTask("t-open", completedAt = null)
            ),
            tokenUsage = listOf(
                usage(30L, createdAt = today),
                usage(50L, createdAt = today + 10L),
                usage(999L, createdAt = 7L * dayMillis)
            )
        )

        val contract = adapter.getDailySummary("space-1", sessionIds = null)

        assertEquals(3, contract.evidenceCount)
        assertEquals(2, contract.passedCount)
        // recency desc then distinct: kp-b (today+2000, today+1000), kp-a (today)
        assertEquals(listOf("kp-b", "kp-a"), contract.knowledgePoints)
        assertEquals(1, contract.planCompletedCount)
        assertEquals(80L, contract.totalTokens)
        assertEquals(DailySummaryAiState.None, contract.aiState)
        assertNull(contract.aiText)
        assertTrue(contract.hasActivity)
    }

    @Test
    fun masteryCrossingWithinTodayCountsAsNewlyMastered() = runBlocking {
        val yesterday = 7L * dayMillis
        val adapter = adapter(
            facts = listOf(
                fact("kp-x", evidenceType = "explanation", occurredAt = yesterday),
                fact("kp-x", evidenceType = "retrieval", occurredAt = yesterday + 1L),
                fact("kp-x", evidenceType = "correction", occurredAt = yesterday + 2L),
                fact("kp-x", evidenceType = "transfer", occurredAt = yesterday + 3L),
                fact("kp-x", evidenceType = "delayed_retrieval", occurredAt = yesterday + 4L),
                fact("kp-x", evidenceType = "correction", occurredAt = today)
            )
        )

        val contract = adapter.getDailySummary("space-1", sessionIds = null)

        // 截至昨天 69.923 (<70)，今天一条 correction 拉到 76.95 (>=70)
        assertEquals(1, contract.masteredTodayCount)
    }

    @Test
    fun sessionScopeFiltersTodayEvidence() = runBlocking {
        val adapter = adapter(
            facts = listOf(
                fact("kp-a", occurredAt = today, windowId = "session-a"),
                fact("kp-b", occurredAt = today, windowId = "session-b"),
                fact("kp-c", occurredAt = today + 1_000L, windowId = "session-a")
            )
        )

        val contract = adapter.getDailySummary("space-1", sessionIds = listOf("session-a"))

        assertEquals(2, contract.evidenceCount)
        assertEquals(listOf("kp-c", "kp-a"), contract.knowledgePoints)
    }

    @Test
    fun storedSummarySurfacesAsReadyWithText() = runBlocking {
        val stored = DailySummary(
            id = "s1",
            spaceId = "space-1",
            dayStartEpochMillis = today,
            sourceRevision = 3L,
            generatorVersion = DailySummaryGenerator.GeneratorVersion,
            summary = "你今天复习了极限与导数",
            generatedAtEpochMillis = now - 60L
        )
        val adapter = adapter(
            facts = listOf(fact("kp-a", occurredAt = today)),
            findStored = { _, _ -> stored }
        )

        val contract = adapter.getDailySummary("space-1", sessionIds = null)

        assertEquals("你今天复习了极限与导数", contract.aiText)
        assertEquals(DailySummaryAiState.Ready, contract.aiState)
        assertEquals(now - 60L, contract.aiGeneratedAtEpochMillis)
    }

    @Test
    fun inFlightAndFailedStatesMapThroughSharedState() = runBlocking {
        val key = dailySummaryKey("space-1", today)
        val state = DailySummaryGenerationState()
        state.markInFlight(key)

        val generating = adapter(
            facts = listOf(fact("kp-a", occurredAt = today)),
            state = state
        )
        assertEquals(
            DailySummaryAiState.Generating,
            generating.getDailySummary("space-1", sessionIds = null).aiState
        )

        state.clearInFlight(key)
        state.markFailed(key)
        val failed = adapter(
            facts = listOf(fact("kp-a", occurredAt = today)),
            state = state
        )
        val failedContract = failed.getDailySummary("space-1", sessionIds = null)
        assertEquals(DailySummaryAiState.Failed, failedContract.aiState)
        assertNull(failedContract.aiText)
    }

    @Test
    fun withoutFactsSeamReturnsHonestDefaultContract() = runBlocking {
        val contract = LearningOverviewDailySummaryPortAdapter(
            nowEpochMillis = { now },
            timeZone = TimeZone.getTimeZone("UTC")
        ).getDailySummary("space-1", sessionIds = null)

        assertEquals(DailySummaryContract(), contract)
    }

    // --- helpers -----------------------------------------------------------

    private fun adapter(
        facts: List<LearningFactReceipt> = emptyList(),
        planTasks: List<StudyPlanTask> = emptyList(),
        tokenUsage: List<TokenUsageRecord> = emptyList(),
        findStored: (suspend (String, Long) -> DailySummary?)? = null,
        state: DailySummaryGenerationState? = null
    ): LearningOverviewDailySummaryPortAdapter = LearningOverviewDailySummaryPortAdapter(
        listLearningFacts = { facts },
        listPlanTasks = { planTasks },
        listTokenUsage = { tokenUsage },
        findStoredSummary = findStored,
        generationState = state,
        nowEpochMillis = { now },
        timeZone = TimeZone.getTimeZone("UTC")
    )

    private fun fact(
        knowledgePoint: String,
        evidenceType: String = "explanation",
        occurredAt: Long,
        windowId: String = "session-a",
        result: String = "passed"
    ): LearningFactReceipt = LearningFactReceipt(
        knowledgePoint = knowledgePoint,
        evidenceType = evidenceType,
        result = result,
        confidence = 1f,
        sourceWindowId = windowId,
        sourceTurnId = "turn-$knowledgePoint-$occurredAt-$evidenceType-$result",
        occurredAtEpochMillis = occurredAt
    )

    private fun planTask(id: String, completedAt: Long?): StudyPlanTask = StudyPlanTask(
        id = id,
        spaceId = "space-1",
        title = "task-$id",
        state = if (completedAt != null) StudyPlanTaskState.Completed else StudyPlanTaskState.Planned,
        completedAtEpochMillis = completedAt
    )

    private fun usage(totalTokens: Long, createdAt: Long): TokenUsageRecord = TokenUsageRecord(
        id = "u-$totalTokens-$createdAt",
        spaceId = "space-1",
        turnId = "turn-$totalTokens-$createdAt",
        attempt = 1,
        totalTokens = totalTokens,
        estimated = false,
        createdAtEpochMillis = createdAt
    )
}
