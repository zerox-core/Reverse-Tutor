package com.reversetutor.core.domain

/**
 * Home learning overview contracts — a separate read model for the home
 * side-panel. This model does NOT reuse the conversation-assistant contract
 * and does NOT require the UI to know Repository details.
 *
 * All data is aggregated from existing read models (learning, error, memory,
 * graph, study plan, token usage). No weekly summary is generated
 * synchronously in a chat turn.
 */

// ---------------------------------------------------------------------------
// Scope
// ---------------------------------------------------------------------------

/**
 * @param spaceId the space to query.
 * @param sessionIds null = all sessions in the space; non-null = only those listed.
 */
data class LearningOverviewScope(
    val spaceId: String,
    val sessionIds: List<String>? = null
)

// ---------------------------------------------------------------------------
// Sub-contracts
// ---------------------------------------------------------------------------

data class LearningProgressContract(
    val totalKnowledgePoints: Int = 0,
    val masteredCount: Int = 0,
    val masteryRate: Float = 0f,
    val weeklyChange: Float = 0f
)

data class TodayPlanTask(
    val id: String,
    val title: String,
    val status: String,
    val knowledgePoint: String? = null
)

data class TodayPlanContract(
    val tasks: List<TodayPlanTask> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0
) {
    val isEmpty: Boolean get() = tasks.isEmpty()
}

data class LearningThreadContract(
    val id: String,
    val title: String,
    val knowledgePoint: String,
    val progress: Float,
    val updatedAtEpochMillis: Long
)

data class WeakPointContract(
    val id: String,
    val knowledgePoint: String,
    val errorCount: Int,
    val lastErrorEpochMillis: Long,
    val severity: Float
)

data class TokenUsageOverviewContract(
    val totalTokens: Long = 0,
    val estimatedTokens: Long = 0,
    val isEstimated: Boolean = false,
    val period: String = "weekly"
)

// ---------------------------------------------------------------------------
// Main contract
// ---------------------------------------------------------------------------

data class LearningOverviewContract(
    val scope: LearningOverviewScope,
    val generatedAtEpochMillis: Long,
    val activeSessionCount: Int,
    val progress: LearningProgressContract,
    val todayPlan: TodayPlanContract,
    val weeklyMainline: List<LearningThreadContract>,
    val weakPoints: List<WeakPointContract>,
    val tokenUsage: TokenUsageOverviewContract,
    val warnings: List<ContextWarning> = emptyList(),
    val isNoData: Boolean = false
)

// ---------------------------------------------------------------------------
// Non-frozen overview ports
// ---------------------------------------------------------------------------

interface LearningOverviewSessionPort {
    suspend fun countActiveSessions(spaceId: String, sessionIds: List<String>?): Int
}

interface LearningOverviewProgressPort {
    suspend fun getProgress(spaceId: String, sessionIds: List<String>?): LearningProgressContract
}

interface LearningOverviewPlanPort {
    suspend fun listTodayPlan(spaceId: String): List<TodayPlanTask>
}

interface LearningOverviewThreadPort {
    suspend fun listWeeklyMainline(spaceId: String, sessionIds: List<String>?, limit: Int): List<LearningThreadContract>
}

interface LearningOverviewWeakPointPort {
    suspend fun listWeakPoints(spaceId: String, sessionIds: List<String>?, limit: Int): List<WeakPointContract>
}

interface LearningOverviewTokenPort {
    suspend fun aggregateTokenUsage(spaceId: String, sessionIds: List<String>?): TokenUsageOverviewContract
}
