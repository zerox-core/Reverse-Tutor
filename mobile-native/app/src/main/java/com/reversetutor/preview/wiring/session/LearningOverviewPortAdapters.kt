package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.learning.LearningRepositoryImpl
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.LearningOverviewPlanPort
import com.reversetutor.core.domain.LearningOverviewProgressPort
import com.reversetutor.core.domain.LearningOverviewSessionPort
import com.reversetutor.core.domain.LearningOverviewThreadPort
import com.reversetutor.core.domain.LearningOverviewTokenPort
import com.reversetutor.core.domain.LearningOverviewWeakPointPort
import com.reversetutor.core.domain.LearningProgressContract
import com.reversetutor.core.domain.LearningThreadContract
import com.reversetutor.core.domain.TodayPlanTask
import com.reversetutor.core.domain.TokenUsageOverviewContract
import com.reversetutor.core.domain.WeakPointContract
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.TokenUsageRecord
import java.util.Calendar
import java.util.TimeZone

/**
 * Adapts existing repository read capabilities to the non-frozen home learning
 * overview ports. The coordinator's bounded read turns empty/default data into
 * an explicit `NoData` state, never a failure.
 *
 * Capability gaps (see tasks/native-p2-007-api-fact-map.md §6): the frozen
 * layer currently exposes no weekly-mainline read model, no mastery progress
 * aggregation, and no token-usage read/aggregate API. The [thread], [progress]
 * and [token] adapters therefore return empty/default values rather than
 * fabricating data; faithful aggregation is a follow-up capability.
 */

class LearningOverviewSessionPortAdapter(
    private val sessionRepository: SessionRepository
) : LearningOverviewSessionPort {
    override suspend fun countActiveSessions(
        spaceId: String,
        sessionIds: List<String>?
    ): Int {
        val sessions = sessionRepository.listSessions(spaceId)
        val scoped = sessionIds
            ?.let { ids -> sessions.filter { it.id in ids } }
            ?: sessions
        return scoped.count { !it.archived }
    }
}

class LearningOverviewPlanPortAdapter(
    private val learningRepository: LearningRepositoryImpl
) : LearningOverviewPlanPort {
    override suspend fun listTodayPlan(spaceId: String): List<TodayPlanTask> =
        learningRepository.listTasks(spaceId)
            .filter { it.state != StudyPlanTaskState.Cancelled }
            .map { TodayPlanTask(it.id, it.title, it.state.name, it.sourceSessionId) }
}

class LearningOverviewProgressPortAdapter(
    private val learningRepository: LearningRepositoryImpl,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : LearningOverviewProgressPort {
    override suspend fun getProgress(
        spaceId: String,
        sessionIds: List<String>?
    ): LearningProgressContract = LearningProgressContract()
}

class LearningOverviewThreadPortAdapter(
    private val learningRepository: LearningRepositoryImpl
) : LearningOverviewThreadPort {
    override suspend fun listWeeklyMainline(
        spaceId: String,
        sessionIds: List<String>?,
        limit: Int
    ): List<LearningThreadContract> = emptyList()
}

class LearningOverviewWeakPointPortAdapter(
    private val memoryRepository: MemoryRepository
) : LearningOverviewWeakPointPort {
    override suspend fun listWeakPoints(
        spaceId: String,
        sessionIds: List<String>?,
        limit: Int
    ): List<WeakPointContract> =
        memoryRepository.snapshot(spaceId).errors
            .filterNot { it.resolved }
            .groupBy { it.code ?: it.title }
            .map { (keyPoint, errors) ->
                WeakPointContract(
                    id = keyPoint,
                    knowledgePoint = keyPoint,
                    errorCount = errors.size,
                    lastErrorEpochMillis = errors.maxOf { it.createdAtEpochMillis },
                    severity = errors.size.coerceAtLeast(1).toFloat() / errors.size.coerceAtLeast(1)
                )
            }
            .sortedByDescending { it.errorCount }
            .take(limit)
}

class LearningOverviewTokenPortAdapter(
    private val listTokenUsage: suspend (String) -> List<TokenUsageRecord>,
    private val sessionIdForTurn: suspend (String) -> String?,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault()
) : LearningOverviewTokenPort {
    override suspend fun aggregateTokenUsage(
        spaceId: String,
        sessionIds: List<String>?
    ): TokenUsageOverviewContract {
        val now = nowEpochMillis()
        val weekStart = Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val records = buildList {
            listTokenUsage(spaceId).forEach { record ->
                if (record.createdAtEpochMillis !in weekStart..now) return@forEach
                if (sessionIds == null || sessionIdForTurn(record.turnId) in sessionIds) {
                    add(record)
                }
            }
        }
        return TokenUsageOverviewContract(
            totalTokens = records.sumNonNegative(TokenUsageRecord::totalTokens),
            estimatedTokens = records.filter(TokenUsageRecord::estimated)
                .sumNonNegative(TokenUsageRecord::totalTokens),
            isEstimated = records.any(TokenUsageRecord::estimated),
            period = "weekly"
        )
    }
}

private fun List<TokenUsageRecord>.sumNonNegative(
    selector: (TokenUsageRecord) -> Long
): Long = fold(0L) { total, record ->
    val value = selector(record).coerceAtLeast(0L)
    if (Long.MAX_VALUE - total < value) Long.MAX_VALUE else total + value
}
