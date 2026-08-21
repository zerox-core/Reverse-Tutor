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
        val activeSessionCount = safeRead("session", 0, warnings) {
            sessionPort.countActiveSessions(spaceId, sessionIds)
        }

        // Progress
        val progress = safeRead("progress", LearningProgressContract(), warnings) {
            progressPort.getProgress(spaceId, sessionIds)
        }

        // Today's plan
        val todayTasks = safeRead("plan", emptyList<TodayPlanTask>(), warnings) {
            planPort.listTodayPlan(spaceId)
        }
        val todayPlan = TodayPlanContract(
            tasks = todayTasks,
            completedCount = todayTasks.count { it.status.equals("done", ignoreCase = true) },
            totalCount = todayTasks.size
        )

        // Weekly mainline (sorted by updatedAt descending, then id)
        val weeklyMainline = safeRead("mainline", emptyList<LearningThreadContract>(), warnings) {
            threadPort.listWeeklyMainline(spaceId, sessionIds, mainlineLimit)
                .sortedWith(compareByDescending<LearningThreadContract> { it.updatedAtEpochMillis }
                    .thenBy { it.id })
        }

        // Weak points (sorted by severity descending, then errorCount, then id)
        val weakPoints = safeRead("weakPoints", emptyList<WeakPointContract>(), warnings) {
            weakPointPort.listWeakPoints(spaceId, sessionIds, weakPointLimit)
                .sortedWith(
                    compareByDescending<WeakPointContract> { it.severity }
                        .thenByDescending { it.errorCount }
                        .thenBy { it.id }
                )
        }

        // Token usage
        val tokenUsage = safeRead("tokenUsage", TokenUsageOverviewContract(), warnings) {
            tokenPort.aggregateTokenUsage(spaceId, sessionIds)
        }

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

    private suspend fun <T> safeRead(
        source: String,
        default: T,
        warnings: MutableList<ContextWarning>,
        block: suspend () -> T
    ): T = try {
        block()
    } catch (_: Exception) {
        warnings += ContextWarning(source, "source_unavailable")
        default
    }
}
