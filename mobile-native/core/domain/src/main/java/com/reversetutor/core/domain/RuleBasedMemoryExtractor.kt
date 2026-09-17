package com.reversetutor.core.domain

/**
 * V2-003: rule-based memory extractor, v1.
 *
 * Runs on a batch of messages leaving the sliding window (never per
 * sentence, never blocking the chat loop). Deterministic keyword rules
 * only; the cloud cheap-model extractor arrives later via BYOK.
 *
 * Values are normalized labels, not raw transcript: the semantic content
 * stays reachable through the provenance handle back to the turn.
 */

enum class ExtractionRole { USER, ASSISTANT }

/** Minimal message view the extractor reasons about. */
data class ExtractableMessage(
    val messageId: String,
    val role: ExtractionRole,
    val text: String,
    val occurredAtEpochMillis: Long,
    val hourOfDay: Int,
)

object RuleBasedMemoryExtractor {

    const val MAX_CANDIDATES_PER_BATCH: Int = 3

    private val GOAL_PATTERNS = listOf("我要", "我想", "目标是", "我的目标", "计划", "打算", "争取")
    private val PREFERENCE_PATTERNS = listOf("我喜欢", "我不喜欢", "我讨厌", "我习惯", "以后都", "别给我")
    private val LEARNING_PATTERNS = listOf("我懂了", "明白了", "原来是", "我错了", "算错了", "粗心", "搞不懂", "不会")
    private val REMINDER_KEYWORDS = listOf("睡觉", "休息", "熬夜", "几点了", "该睡")
    private val ACTIVITY_KEYWORDS = mapOf(
        "写代码" to "coding",
        "代码" to "coding",
        "刷题" to "practice",
        "学习" to "study",
        "打游戏" to "gaming",
        "游戏" to "gaming",
    )

    fun extract(batch: List<ExtractableMessage>): List<WindowLocalObservation> {
        val candidates = mutableListOf<WindowLocalObservation>()
        var reminderSeen = false
        for (message in batch) {
            if (message.role == ExtractionRole.ASSISTANT) {
                if (REMINDER_KEYWORDS.any { message.text.contains(it) }) reminderSeen = true
                continue
            }
            val category: WindowObservationCategory
            val sourceClass: String
            val confidence: Double
            val value: String
            when {
                GOAL_PATTERNS.any { message.text.contains(it) } -> {
                    category = WindowObservationCategory.GOAL_STATEMENT
                    sourceClass = MemoryObservationSourceClass.USER_STATEMENT
                    confidence = 0.9
                    value = "stated_goal"
                }
                PREFERENCE_PATTERNS.any { message.text.contains(it) } -> {
                    category = WindowObservationCategory.PREFERENCE_SIGNAL
                    sourceClass = MemoryObservationSourceClass.USER_STATEMENT
                    confidence = 0.85
                    value = "stated_preference"
                }
                reminderSeen -> {
                    category = WindowObservationCategory.REMINDER_FEEDBACK
                    sourceClass = MemoryObservationSourceClass.USER_STATEMENT
                    confidence = 0.95
                    value = "acknowledged_reminder"
                }
                LEARNING_PATTERNS.any { message.text.contains(it) } -> {
                    category = WindowObservationCategory.LEARNING_EVENT
                    sourceClass = MemoryObservationSourceClass.USER_STATEMENT
                    confidence = 0.8
                    value = "learning_moment"
                }
                else -> {
                    val lateNight = message.hourOfDay >= WindowMemoryPolicy.LATE_NIGHT_START_HOUR ||
                        message.hourOfDay < WindowMemoryPolicy.LATE_NIGHT_END_HOUR
                    val topic = ACTIVITY_KEYWORDS.entries.firstOrNull { message.text.contains(it.key) }?.value
                    if (!lateNight || topic == null) continue
                    category = WindowObservationCategory.ACTIVITY_CONTEXT
                    sourceClass = MemoryObservationSourceClass.BEHAVIOR_OBSERVATION
                    confidence = 0.6
                    value = "late_night_" + topic
                }
            }
            candidates += WindowLocalObservation(
                category = category,
                slotKey = category.name,
                value = value,
                sourceClass = sourceClass,
                confidence = confidence,
                salience = WindowMemoryPolicy.salienceOf(category, message.hourOfDay),
                occurredAtEpochMillis = message.occurredAtEpochMillis,
                provenanceHandle = "turn:" + message.messageId,
            )
        }
        return candidates
            .sortedWith(compareByDescending<WindowLocalObservation> { it.salience.ordinal }.thenByDescending { it.confidence })
            .take(MAX_CANDIDATES_PER_BATCH)
    }
}
