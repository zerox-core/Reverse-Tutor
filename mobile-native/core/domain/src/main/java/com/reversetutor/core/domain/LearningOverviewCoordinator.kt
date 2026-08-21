package com.reversetutor.core.domain

/**
 * Home learning overview coordinator — stable aggregation for the home
 * side-panel's today/week/weakness/token data.
 *
 * Reads through non-frozen ports backed by existing repositories/adapters.
 * Filters by [LearningOverviewScope.spaceId] and requested session scope.
 * Sorts deterministically, caps each list, and returns explicit empty states.
 *
 * Does NOT generate weekly summaries synchronously (that is a separate task).
 * Does NOT call LLM, Android APIs, or modify Repository signatures.
 */
class LearningOverviewCoordinator(
    private val sessionPort: LearningOverviewSessionPort,
    private val progressPort: LearningOverviewProgressPort,
    private val planPort: LearningOverviewPlanPort,
    private val threadPort: LearningOverviewThreadPort,
    private val weakPointPort: LearningOverviewWeakPointPort,
    private val tokenPort: LearningOverviewTokenPort,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val mainlineLimit: Int = 5,
    private val weakPointLimit: Int = 5
) {

    suspend fun generate(scope: LearningOverviewScope): LearningOverviewContract {
        val warnings = mutableListOf<ContextWarning>()
        val spaceId = scope.spaceId
        val sessionIds = scope.sessionIds

        // Active session count
        val activeSessionCount = safeRead("session", 0) {
            sessionPort.countActiveSessions(spaceId, sessionIds)
        }.also { if (it == 0) warnings.add(ContextWarning("session", "no active sessions")) }

        // Progress
        val progress = safeRead("progress", LearningProgressContract()) {
            progressPort.getProgress(spaceId, sessionIds)
        }.also { if (it.totalKnowledgePoints == 0) warnings.add(ContextWarning("progress", "no progress data")) }

        // Today's plan
        val todayTasks = safeRead("plan", emptyList<TodayPlanTask>()) {
            planPort.listTodayPlan(spaceId)
        }
        val todayPlan = TodayPlanContract(
            tasks = todayTasks,
            completedCount = todayTasks.count { it.status.equals("done", ignoreCase = true) },
            totalCount = todayTasks.size
        ).also { if (it.isEmpty) warnings.add(ContextWarning("plan", "no today plan")) }

        // Weekly mainline (sorted by updatedAt descending, then id)
        val weeklyMainline = safeRead("mainline", emptyList<LearningThreadContract>()) {
            threadPort.listWeeklyMainline(spaceId, sessionIds, mainlineLimit)
                .sortedWith(compareByDescending<LearningThreadContract> { it.updatedAtEpochMillis }
                    .thenBy { it.id })
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("mainline", "no weekly mainline")) }

        // Weak points (sorted by severity descending, then errorCount, then id)
        val weakPoints = safeRead("weakPoints", emptyList<WeakPointContract>()) {
            weakPointPort.listWeakPoints(spaceId, sessionIds, weakPointLimit)
                .sortedWith(
                    compareByDescending<WeakPointContract> { it.severity }
                        .thenByDescending { it.errorCount }
                        .thenBy { it.id }
                )
        }.also { if (it.isEmpty()) warnings.add(ContextWarning("weakPoints", "no weak points")) }

        // Token usage
        val tokenUsage = safeRead("tokenUsage", TokenUsageOverviewContract()) {
            tokenPort.aggregateTokenUsage(spaceId, sessionIds)
        }.also { if (it.totalTokens == 0L) warnings.add(ContextWarning("tokenUsage", "no token usage")) }

        // No-data detection: all sources returned empty
        val isNoData = activeSessionCount == 0 &&
            progress.totalKnowledgePoints == 0 &&
            todayPlan.isEmpty &&
            weeklyMainline.isEmpty() &&
            weakPoints.isEmpty() &&
            tokenUsage.totalTokens == 0L

        return LearningOverviewContract(
            scope = scope,
            generatedAtEpochMillis = nowEpochMillis(),
            activeSessionCount = activeSessionCount,
            progress = progress,
            todayPlan = todayPlan,
            weeklyMainline = weeklyMainline,
            weakPoints = weakPoints,
            tokenUsage = tokenUsage,
            warnings = warnings,
            isNoData = isNoData
        )
    }

    private suspend fun <T> safeRead(source: String, default: T, block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        default
    }
}
