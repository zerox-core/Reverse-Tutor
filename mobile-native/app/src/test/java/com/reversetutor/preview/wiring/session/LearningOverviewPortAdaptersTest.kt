package com.reversetutor.preview.wiring.session

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
