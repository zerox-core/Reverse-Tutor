package com.reversetutor.core.model

data class StudyPlanTask(
    val id: String,
    val spaceId: String,
    val title: String,
    val detail: String? = null,
    val state: StudyPlanTaskState = StudyPlanTaskState.Proposed,
    val dueAtEpochMillis: Long? = null,
    val completedAtEpochMillis: Long? = null,
    val sourceSessionId: String? = null,
    val sourceMessageId: String? = null,
    val revision: Long = 0L,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = createdAtEpochMillis
)

enum class StudyPlanTaskState {
    Proposed,
    Planned,
    InProgress,
    Completed,
    Cancelled
}

data class WeeklySummary(
    val id: String,
    val spaceId: String,
    val weekStartEpochMillis: Long,
    val weekEndEpochMillis: Long = 0L,
    val sourceRevision: Long = 0L,
    val generatorVersion: String = "",
    val summary: String = "",
    val generatedAtEpochMillis: Long = 0L,
    val stale: Boolean = false
)

data class TokenUsageRecord(
    val id: String,
    val spaceId: String,
    val turnId: String,
    val attempt: Int,
    val modelBindingId: String? = null,
    val providerUsageId: String? = null,
    val inputTokens: Long = 0L,
    val outputTokens: Long = 0L,
    val cachedTokens: Long = 0L,
    val reasoningTokens: Long = 0L,
    val totalTokens: Long = inputTokens + outputTokens,
    val estimated: Boolean = true,
    val createdAtEpochMillis: Long = 0L
)
