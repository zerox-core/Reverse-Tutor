package com.reversetutor.core.domain

/**
 * V2-006: window-memory injection selector.
 *
 * Builds the bounded context block injected alongside a turn. Lorebook rule
 * (locked with the user): never inject everything - rank, then truncate by
 * token budget. Ranking: always-on categories (reminder feedback, stated
 * goals) first, query-keyword relevance second, weight third, recency last.
 *
 * Pure and Android-free: callers supply estimates and inputs; the selector
 * only decides.
 */

/** The bounded, ready-to-inject window-memory block. */
data class WindowMemoryContextBlock(
    val text: String,
    val includedValueCount: Int,
    val includedPatternCount: Int,
    val summaryIncluded: Boolean,
    val estimatedTokens: Int,
)

object WindowMemoryContextSelector {

    /** Injection budget - deliberately far below the raw sliding window. */
    const val DEFAULT_INJECTION_TOKEN_BUDGET: Int = 600

    private const val HEADER = "【窗口记忆】"

    private val CATEGORY_LABELS = mapOf(
        WindowObservationCategory.GOAL_STATEMENT to "学习目标",
        WindowObservationCategory.PREFERENCE_SIGNAL to "学习偏好",
        WindowObservationCategory.REMINDER_FEEDBACK to "提醒反馈",
        WindowObservationCategory.LEARNING_EVENT to "学习事件",
        WindowObservationCategory.ACTIVITY_CONTEXT to "行为场景",
        WindowObservationCategory.DAILY_LIFE to "日常",
    )

    private val CATEGORY_KEYWORDS = mapOf(
        WindowObservationCategory.GOAL_STATEMENT to listOf("目标", "计划", "打算"),
        WindowObservationCategory.PREFERENCE_SIGNAL to listOf("喜欢", "偏好", "习惯"),
        WindowObservationCategory.REMINDER_FEEDBACK to listOf("提醒", "睡觉", "休息"),
        WindowObservationCategory.LEARNING_EVENT to listOf("懂", "错", "学会", "题"),
        WindowObservationCategory.ACTIVITY_CONTEXT to listOf("代码", "学习", "游戏", "刷题"),
        WindowObservationCategory.DAILY_LIFE to listOf("吃", "睡", "饭"),
    )

    fun select(
        activeValues: List<WindowActiveValue>,
        patterns: List<ObservationPattern>,
        rollingSummary: String?,
        queryText: String,
        tokenBudget: Int = DEFAULT_INJECTION_TOKEN_BUDGET,
        estimateTokens: (String) -> Int = WindowTokenEstimator::estimate,
    ): WindowMemoryContextBlock {
        val lines = mutableListOf<String>()
        var tokens = 0
        var valueCount = 0
        var patternCount = 0
        var summaryIncluded = false

        val ranked = activeValues.sortedWith(
            compareByDescending<WindowActiveValue> { alwaysOnWeight(it.category) }
                .thenByDescending { queryRelevance(it.category, queryText) }
                .thenByDescending { it.weight }
                .thenByDescending { it.updatedAtEpochMillis }
        )
        for (value in ranked) {
            val line = "- " + label(value.category) + "：" + value.value
            val cost = estimateTokens(line)
            if (tokens + cost > tokenBudget) break
            lines += line
            tokens += cost
            valueCount++
        }

        for (pattern in patterns.sortedByDescending { it.occurrenceCount }) {
            val line = "- " + label(pattern.category) + "模式：共 " + pattern.occurrenceCount + " 次"
            val cost = estimateTokens(line)
            if (tokens + cost > tokenBudget) break
            lines += line
            tokens += cost
            patternCount++
        }

        val summary = rollingSummary?.takeIf { it.isNotBlank() }
        if (summary != null) {
            val line = "历史摘要：" + summary
            val cost = estimateTokens(line)
            if (tokens + cost <= tokenBudget) {
                lines += line
                tokens += cost
                summaryIncluded = true
            }
        }

        if (lines.isEmpty()) {
            return WindowMemoryContextBlock("", 0, 0, summaryIncluded = false, estimatedTokens = 0)
        }
        val text = HEADER + "\n" + lines.joinToString("\n")
        return WindowMemoryContextBlock(
            text = text,
            includedValueCount = valueCount,
            includedPatternCount = patternCount,
            summaryIncluded = summaryIncluded,
            estimatedTokens = estimateTokens(text),
        )
    }

    private fun label(category: WindowObservationCategory): String =
        CATEGORY_LABELS[category] ?: category.name

    /** Locked decision: reminder feedback and stated goals are always high. */
    private fun alwaysOnWeight(category: WindowObservationCategory): Int =
        if (category == WindowObservationCategory.REMINDER_FEEDBACK ||
            category == WindowObservationCategory.GOAL_STATEMENT
        ) 1 else 0

    private fun queryRelevance(category: WindowObservationCategory, queryText: String): Int {
        if (queryText.isBlank()) return 0
        val keywords = CATEGORY_KEYWORDS[category] ?: return 0
        return if (keywords.any { queryText.contains(it) }) 1 else 0
    }
}
