package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red slice for the window-memory sliding window + layering contracts.
 * Locked product decisions (2026-09-17, user sign-off):
 *  - window budget uses industry-average defaults, user-tunable later
 *  - rolling summary layer stays ON (details matter for a teaching app)
 *  - reminder feedback is a first-class high-weight signal
 */
class WindowMemoryContractTest {

    private fun msg(id: String, tokens: Int, at: Long): WindowMessageView =
        WindowMessageView(messageId = id, estimatedTokens = tokens, occurredAtEpochMillis = at)

    @Test
    fun `default window config uses industry average budget with summary enabled`() {
        val config = WindowMemoryConfig()
        assertEquals(6000, config.tokenBudget)
        assertEquals(60, config.messageCap)
        assertTrue(config.rollingSummaryEnabled)
    }

    @Test
    fun `eviction keeps newest messages within token budget`() {
        val config = WindowMemoryConfig(tokenBudget = 100, messageCap = 60)
        val messages = listOf(msg("m1", 60, 1L), msg("m2", 60, 2L), msg("m3", 30, 3L))
        val result = WindowMemoryPolicy.evict(messages, config)
        assertEquals(listOf("m2", "m3"), result.kept.map { it.messageId })
        assertEquals(listOf("m1"), result.evicted.map { it.messageId })
    }

    @Test
    fun `eviction enforces message cap even under token budget`() {
        val config = WindowMemoryConfig(tokenBudget = 100000, messageCap = 2)
        val messages = listOf(msg("m1", 1, 1L), msg("m2", 1, 2L), msg("m3", 1, 3L))
        val result = WindowMemoryPolicy.evict(messages, config)
        assertEquals(listOf("m2", "m3"), result.kept.map { it.messageId })
        assertEquals(listOf("m1"), result.evicted.map { it.messageId })
    }

    @Test
    fun `evicted batch is oldest first for downstream compression`() {
        val config = WindowMemoryConfig(tokenBudget = 10, messageCap = 60)
        val messages = listOf(msg("m1", 10, 1L), msg("m2", 10, 2L), msg("m3", 10, 3L))
        val result = WindowMemoryPolicy.evict(messages, config)
        assertEquals(listOf("m1", "m2"), result.evicted.map { it.messageId })
    }

    @Test
    fun `late night activity context has high salience`() {
        assertEquals(
            SignalSalience.HIGH,
            WindowMemoryPolicy.salienceOf(WindowObservationCategory.ACTIVITY_CONTEXT, hourOfDay = 3),
        )
    }

    @Test
    fun `afternoon activity context has normal salience`() {
        assertEquals(
            SignalSalience.NORMAL,
            WindowMemoryPolicy.salienceOf(WindowObservationCategory.ACTIVITY_CONTEXT, hourOfDay = 15),
        )
    }

    @Test
    fun `reminder feedback is always high salience regardless of hour`() {
        assertEquals(
            SignalSalience.HIGH,
            WindowMemoryPolicy.salienceOf(WindowObservationCategory.REMINDER_FEEDBACK, hourOfDay = 15),
        )
        assertEquals(
            SignalSalience.HIGH,
            WindowMemoryPolicy.salienceOf(WindowObservationCategory.REMINDER_FEEDBACK, hourOfDay = 3),
        )
    }

    @Test
    fun `goal statement is high salience at any hour`() {
        assertEquals(
            SignalSalience.HIGH,
            WindowMemoryPolicy.salienceOf(WindowObservationCategory.GOAL_STATEMENT, hourOfDay = 10),
        )
    }

    @Test
    fun `patterns aggregate occurrence count and active hours`() {
        val observations = listOf(
            CategorizedObservation(WindowObservationCategory.ACTIVITY_CONTEXT, occurredAtEpochMillis = 100L, hourOfDay = 2),
            CategorizedObservation(WindowObservationCategory.ACTIVITY_CONTEXT, occurredAtEpochMillis = 200L, hourOfDay = 3),
            CategorizedObservation(WindowObservationCategory.DAILY_LIFE, occurredAtEpochMillis = 300L, hourOfDay = 12),
        )
        val patterns = WindowMemoryPolicy.aggregatePatterns(observations)
        val activity = patterns.first { it.category == WindowObservationCategory.ACTIVITY_CONTEXT }
        assertEquals(2, activity.occurrenceCount)
        assertEquals(setOf(2, 3), activity.activeHours)
        assertEquals(100L, activity.firstSeenEpochMillis)
        assertEquals(200L, activity.lastSeenEpochMillis)
    }
}
