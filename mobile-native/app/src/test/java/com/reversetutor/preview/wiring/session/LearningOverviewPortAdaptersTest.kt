package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.LearningProgressContract
import com.reversetutor.core.model.ErrorLog
import com.reversetutor.core.model.TokenUsageRecord
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningOverviewPortAdaptersTest {
    private val dayMillis = 24L * 60L * 60L * 1_000L
    private val now = 8L * dayMillis + 12L * 60L * 60L * 1_000L

    // -------------------------------------------------------------------
    // Progress: ledger-backed mastery aggregation
    // -------------------------------------------------------------------

    @Test
    fun progressAggregatesMasteryFromLedgerAndComparesToLastWeekBaseline() = runBlocking {
        val adapter = progressAdapter(
            facts = listOf(
                // kp-a mastered LAST week: four passed corrections -> 73.94
                fact("kp-a", evidenceType = "correction", occurredAt = 1L * dayMillis),
                fact("kp-a", evidenceType = "correction", occurredAt = 1L * dayMillis),
                fact("kp-a", evidenceType = "correction", occurredAt = 1L * dayMillis),
                fact("kp-a", evidenceType = "correction", occurredAt = 1L * dayMillis),
                // kp-b mastered THIS week: four passed corrections -> 73.94
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis),
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis),
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis),
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis),
                // kp-c fresh entry this week: one explanation -> 12.25
                fact("kp-c", occurredAt = 8L * dayMillis)
            )
        )

        val progress = adapter.getProgress("space-1", sessionIds = null)

        assertEquals(3, progress.totalKnowledgePoints)
        assertEquals(2, progress.masteredCount)
        assertEquals(0.6667f, progress.masteryRate, 0.001f)
        // Baseline (only kp-a, mastered) rate was 1.0 -> change is negative.
        assertEquals(-0.3333f, progress.weeklyChange, 0.001f)
    }

    @Test
    fun progressFiltersReceiptsBySessionScope() = runBlocking {
        val adapter = progressAdapter(
            facts = listOf(
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-a"),
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-a"),
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-a"),
                fact("kp-b", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-a"),
                fact("kp-z", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-b"),
                fact("kp-z", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-b"),
                fact("kp-z", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-b"),
                fact("kp-z", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-b")
            )
        )

        val progress = adapter.getProgress("space-1", sessionIds = listOf("session-a"))

        assertEquals(1, progress.totalKnowledgePoints)
        assertEquals(1, progress.masteredCount)
        assertEquals(1f, progress.masteryRate, 0.0001f)
        // No baseline history before this week => change equals current rate.
        assertEquals(1f, progress.weeklyChange, 0.0001f)
    }

    @Test
    fun progressWithoutLedgerSeamKeepsHonestDefaultContract() = runBlocking {
        val progress = LearningOverviewProgressPortAdapter()
            .getProgress("space-1", sessionIds = null)

        assertEquals(LearningProgressContract(), progress)
    }

    // -------------------------------------------------------------------
    // Weekly mainline: ledger-backed threads
    // -------------------------------------------------------------------

    @Test
    fun weeklyMainlineListsThisWeekKnowledgePointsWithFullLedgerProgress() = runBlocking {
        val adapter = threadAdapter(
            facts = listOf(
                fact("kp-x", occurredAt = 8L * dayMillis),
                fact("kp-y", evidenceType = "correction", occurredAt = 7L * dayMillis),
                fact("kp-old", occurredAt = 1L * dayMillis)
            )
        )

        val threads = adapter.listWeeklyMainline("space-1", sessionIds = null, limit = 5)

        assertEquals(listOf("kp-x", "kp-y"), threads.map { it.id })
        // kp-x: one passed explanation -> 12.25 / 100
        assertEquals(0.1225f, threads[0].progress, 0.0001f)
        // kp-y: one passed correction -> 31.5 / 100
        assertEquals(0.315f, threads[1].progress, 0.0001f)
        assertEquals(8L * dayMillis, threads[0].updatedAtEpochMillis)
        assertEquals("kp-x", threads[0].title)
        assertEquals("kp-x", threads[0].knowledgePoint)
    }

    @Test
    fun weeklyMainlineFiltersBySessionScopeAndAppliesLimit() = runBlocking {
        val adapter = threadAdapter(
            facts = listOf(
                fact("kp-x", occurredAt = 8L * dayMillis, windowId = "session-a"),
                fact("kp-y", evidenceType = "correction", occurredAt = 8L * dayMillis, windowId = "session-b")
            )
        )

        val scoped = adapter.listWeeklyMainline(
            "space-1", sessionIds = listOf("session-a"), limit = 5
        )
        assertEquals(listOf("kp-x"), scoped.map { it.id })

        // Same updatedAt -> stable tie-break by id; limit keeps the first.
        val limited = adapter.listWeeklyMainline("space-1", sessionIds = null, limit = 1)
        assertEquals(listOf("kp-x"), limited.map { it.id })
    }

    @Test
    fun weeklyMainlineReturnsEmptyWhenAllReceiptsPredateThisWeek() = runBlocking {
        val adapter = threadAdapter(
            facts = listOf(fact("kp-old", occurredAt = 1L * dayMillis))
        )

        val threads = adapter.listWeeklyMainline("space-1", sessionIds = null, limit = 3)

        assertTrue(threads.isEmpty())
    }

    @Test
    fun weeklyMainlineWithoutLedgerSeamReturnsEmptyList() = runBlocking {
        val threads = LearningOverviewThreadPortAdapter()
            .listWeeklyMainline("space-1", sessionIds = null, limit = 3)

        assertTrue(threads.isEmpty())
    }

    // -------------------------------------------------------------------
    // Weak points: severity normalization
    // -------------------------------------------------------------------

    @Test
    fun weakPointsNormalizeSeverityAgainstTheLargestGroup() = runBlocking {
        val adapter = weakPointAdapter(
            errors = listOf(
                error("e1", code = "导数链式法则", at = 8L * dayMillis),
                error("e2", code = "导数链式法则", at = 7L * dayMillis),
                error("e3", code = "换元积分", at = 7L * dayMillis),
                error("e4", code = "已解决的旧错误", at = 8L * dayMillis, resolved = true)
            )
        )

        val weakPoints = adapter.listWeakPoints("space-1", sessionIds = null, limit = 5)

        assertEquals(listOf("导数链式法则", "换元积分"), weakPoints.map { it.id })
        assertEquals(2, weakPoints[0].errorCount)
        assertEquals(1f, weakPoints[0].severity, 0.0001f)
        assertEquals(0.5f, weakPoints[1].severity, 0.0001f)
        assertEquals(8L * dayMillis, weakPoints[0].lastErrorEpochMillis)
    }

    @Test
    fun weakPointsGroupByTitleWhenCodeIsNull() = runBlocking {
        val adapter = weakPointAdapter(
            errors = listOf(
                error("e1", code = null, title = "泰勒展开", at = 8L * dayMillis),
                error("e2", code = null, title = "泰勒展开", at = 7L * dayMillis)
            )
        )

        val weakPoints = adapter.listWeakPoints("space-1", sessionIds = null, limit = 5)

        assertEquals(listOf("泰勒展开"), weakPoints.map { it.id })
        assertEquals(2, weakPoints[0].errorCount)
        assertEquals(1f, weakPoints[0].severity, 0.0001f)
    }

    @Test
    fun weakPointsWithoutSeamReturnEmptyList() = runBlocking {
        val weakPoints = LearningOverviewWeakPointPortAdapter()
            .listWeakPoints("space-1", sessionIds = null, limit = 3)

        assertTrue(weakPoints.isEmpty())
    }

    // -------------------------------------------------------------------
    // Token usage (unchanged adapter, existing coverage)
    // -------------------------------------------------------------------

    @Test
    fun tokenUsageAggregatesThisWeekAndTracksEstimatedRecords() = runBlocking {
        val adapter = tokenAdapter(
            records = listOf(
                usage("current-exact", "turn-a", 80L, estimated = false, createdAt = 8L * dayMillis),
                usage("current-estimated", "turn-b", 20L, estimated = true, createdAt = 2L * dayMillis),
                usage("before-window", "turn-c", 999L, estimated = true, createdAt = dayMillis),
                usage("negative", "turn-d", -5L, estimated = true, createdAt = 7L * dayMillis)
            )
        )

        val overview = adapter.aggregateTokenUsage("space-1", sessionIds = null)

        assertEquals(100L, overview.totalTokens)
        assertEquals(20L, overview.estimatedTokens)
        assertTrue(overview.isEstimated)
        assertEquals("weekly", overview.period)
    }

    @Test
    fun tokenUsageFiltersByResolvedSessionWhenScopeIsSelected() = runBlocking {
        val adapter = tokenAdapter(
            records = listOf(
                usage("included", "turn-a", 80L, createdAt = 8L * dayMillis),
                usage("excluded", "turn-b", 20L, estimated = true, createdAt = 8L * dayMillis),
                usage("unresolved", "turn-c", 40L, createdAt = 8L * dayMillis)
            ),
            turnToSession = mapOf("turn-a" to "session-a", "turn-b" to "session-b")
        )

        val overview = adapter.aggregateTokenUsage("space-1", sessionIds = listOf("session-a"))

        assertEquals(80L, overview.totalTokens)
        assertEquals(0L, overview.estimatedTokens)
        assertFalse(overview.isEstimated)
    }

    @Test
    fun tokenUsageReturnsHonestEmptyOverviewWhenNoRecordMatches() = runBlocking {
        val adapter = tokenAdapter(records = emptyList())

        val overview = adapter.aggregateTokenUsage("space-1", sessionIds = emptyList())

        assertEquals(0L, overview.totalTokens)
        assertEquals(0L, overview.estimatedTokens)
        assertFalse(overview.isEstimated)
        assertEquals("weekly", overview.period)
    }

    // -------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------

    private fun progressAdapter(facts: List<LearningFactReceipt>): LearningOverviewProgressPortAdapter =
        LearningOverviewProgressPortAdapter(
            listLearningFacts = { spaceId ->
                assertEquals("space-1", spaceId)
                facts
            },
            nowEpochMillis = { now },
            timeZone = TimeZone.getTimeZone("UTC")
        )

    private fun threadAdapter(facts: List<LearningFactReceipt>): LearningOverviewThreadPortAdapter =
        LearningOverviewThreadPortAdapter(
            listLearningFacts = { spaceId ->
                assertEquals("space-1", spaceId)
                facts
            },
            nowEpochMillis = { now },
            timeZone = TimeZone.getTimeZone("UTC")
        )

    private fun weakPointAdapter(errors: List<ErrorLog>): LearningOverviewWeakPointPortAdapter =
        LearningOverviewWeakPointPortAdapter(
            listErrors = { spaceId ->
                assertEquals("space-1", spaceId)
                errors
            }
        )

    private fun tokenAdapter(
        records: List<TokenUsageRecord>,
        turnToSession: Map<String, String> = emptyMap()
    ): LearningOverviewTokenPortAdapter = LearningOverviewTokenPortAdapter(
        listTokenUsage = { spaceId ->
            assertEquals("space-1", spaceId)
            records
        },
        sessionIdForTurn = turnToSession::get,
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
        sourceTurnId = "turn-$knowledgePoint-$occurredAt-$evidenceType",
        occurredAtEpochMillis = occurredAt
    )

    private fun error(
        id: String,
        code: String?,
        title: String = "fallback-title",
        at: Long,
        resolved: Boolean = false
    ): ErrorLog = ErrorLog(
        id = id,
        spaceId = "space-1",
        title = title,
        detail = "",
        createdAtEpochMillis = at,
        resolved = resolved,
        code = code
    )

    private fun usage(
        id: String,
        turnId: String,
        totalTokens: Long,
        estimated: Boolean = false,
        createdAt: Long
    ): TokenUsageRecord = TokenUsageRecord(
        id = id,
        spaceId = "space-1",
        turnId = turnId,
        attempt = 1,
        totalTokens = totalTokens,
        estimated = estimated,
        createdAtEpochMillis = createdAt
    )
}
