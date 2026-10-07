package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-006: relevance-ranked, budget-bounded window-memory injection.
 * Never injects everything - the lorebook rule (locked with the user):
 * always-on categories first, query relevance second, weight third, and the
 * token budget truncates the tail.
 */
class WindowMemoryContextSelectorTest {

    private fun value(
        category: WindowObservationCategory,
        weight: Double,
        at: Long = 1L,
    ) = WindowActiveValue(
        category = category,
        slotKey = category.name,
        value = "v-" + category.name,
        revision = 1L,
        weight = weight,
        sourceClass = "USER_STATEMENT",
        provenanceHandle = "turn:m1",
        updatedAtEpochMillis = at,
    )

    private fun pattern(category: WindowObservationCategory, count: Int) = ObservationPattern(
        category = category,
        occurrenceCount = count,
        firstSeenEpochMillis = 1L,
        lastSeenEpochMillis = 2L,
        activeHours = setOf(2),
    )

    @Test
    fun alwaysOnCategoriesRankAboveHigherWeightOthers() {
        val block = WindowMemoryContextSelector.select(
            activeValues = listOf(
                value(WindowObservationCategory.ACTIVITY_CONTEXT, weight = 1.0),
                value(WindowObservationCategory.REMINDER_FEEDBACK, weight = 0.3),
            ),
            patterns = emptyList(),
            rollingSummary = null,
            queryText = "随便聊聊",
        )

        assertTrue(block.text.contains("提醒反馈"))
        assertTrue(block.text.indexOf("提醒反馈") < block.text.indexOf("行为场景"))
        assertEquals(2, block.includedValueCount)
    }

    @Test
    fun queryRelevanceBoostsMatchingCategory() {
        val block = WindowMemoryContextSelector.select(
            activeValues = listOf(
                value(WindowObservationCategory.ACTIVITY_CONTEXT, weight = 1.0),
                value(WindowObservationCategory.PREFERENCE_SIGNAL, weight = 0.4),
            ),
            patterns = emptyList(),
            rollingSummary = null,
            queryText = "我说过我喜欢什么吗",
        )

        assertTrue(block.text.indexOf("学习偏好") < block.text.indexOf("行为场景"))
    }

    @Test
    fun zeroBudgetProducesEmptyBlock() {
        val block = WindowMemoryContextSelector.select(
            activeValues = listOf(value(WindowObservationCategory.GOAL_STATEMENT, weight = 1.0)),
            patterns = listOf(pattern(WindowObservationCategory.ACTIVITY_CONTEXT, 3)),
            rollingSummary = "一些摘要",
            queryText = "",
            tokenBudget = 0,
        )

        assertEquals("", block.text)
        assertEquals(0, block.includedValueCount)
        assertEquals(0, block.includedPatternCount)
        assertFalse(block.summaryIncluded)
    }

    @Test
    fun budgetTruncatesTailLines() {
        val many = (1..8).map {
            value(WindowObservationCategory.LEARNING_EVENT, weight = 1.0 - it * 0.01, at = it.toLong())
        }
        val full = WindowMemoryContextSelector.select(
            activeValues = many,
            patterns = emptyList(),
            rollingSummary = null,
            queryText = "",
        )
        val tight = WindowMemoryContextSelector.select(
            activeValues = many,
            patterns = emptyList(),
            rollingSummary = null,
            queryText = "",
            tokenBudget = 30,
        )

        assertTrue(full.includedValueCount > tight.includedValueCount)
        assertTrue(tight.includedValueCount >= 1)
        assertTrue(tight.estimatedTokens == WindowTokenEstimator.estimate(tight.text))
    }

    @Test
    fun patternsAndSummaryIncludedWhenBudgetAllows() {
        val block = WindowMemoryContextSelector.select(
            activeValues = emptyList(),
            patterns = listOf(pattern(WindowObservationCategory.ACTIVITY_CONTEXT, 3)),
            rollingSummary = "早些时候聊了函数基础。",
            queryText = "",
        )

        assertEquals(1, block.includedPatternCount)
        assertTrue(block.summaryIncluded)
        assertTrue(block.text.contains("早些时候聊了函数基础。"))
        assertTrue(block.text.contains("3 次"))
    }

    @Test
    fun emptyInputsReturnEmptyBlock() {
        val block = WindowMemoryContextSelector.select(
            activeValues = emptyList(),
            patterns = emptyList(),
            rollingSummary = null,
            queryText = "查一下",
        )

        assertEquals("", block.text)
        assertEquals(0, block.estimatedTokens)
    }
}
