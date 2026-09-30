package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.learning.LearningRepositoryImpl
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.LearningOverviewPlanPort
import com.reversetutor.core.domain.LearningOverviewProgressPort
import com.reversetutor.core.domain.LearningOverviewSessionPort
import com.reversetutor.core.domain.LearningOverviewThreadPort
import com.reversetutor.core.domain.LearningOverviewTokenPort
import com.reversetutor.core.domain.DailySummaryAiState
import com.reversetutor.core.domain.DailySummaryContract
import com.reversetutor.core.domain.LearningOverviewDailySummaryPort
import com.reversetutor.core.domain.LearningOverviewWeakPointPort
import com.reversetutor.core.domain.LearningProgressContract
import com.reversetutor.core.domain.LearningThreadContract
import com.reversetutor.core.domain.MasteryLedgerProjection
import com.reversetutor.core.domain.TodayPlanTask
import com.reversetutor.core.domain.TokenUsageOverviewContract
import com.reversetutor.core.domain.WeakPointContract
import com.reversetutor.core.model.DailySummary
import com.reversetutor.core.model.ErrorLog
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.StudyPlanTaskState
import com.reversetutor.core.model.TokenUsageRecord
import java.util.Calendar
import java.util.TimeZone

/**
 * Adapts existing repository read capabilities to the non-frozen home learning
 * overview ports. The coordinator's bounded read turns empty/default data into
 * an explicit `NoData` state, never a failure.
 *
 * Step-2 panel real-data wiring (V1 plan 五步顺序第 2 步):
 * - [LearningOverviewProgressPortAdapter] and [LearningOverviewThreadPortAdapter]
 *   aggregate the append-only learning ledger through the deterministic
 *   [MasteryLedgerProjection] fold when the `listLearningFacts` seam is
 *   provided; without the seam they keep returning the honest empty/default
 *   contract (backward compatible with call sites that build no ledger).
 * - [LearningOverviewWeakPointPortAdapter] normalizes severity against the
 *   largest unresolved-error group, so the UI tier thresholds actually
 *   separate 薄弱/需加强/关注 instead of marking every weak point HIGH.
 *
 * No adapter fabricates data: an empty result always means the underlying
 * ledger/error log is empty or the read seam was not provided
 * (see tasks/native-p2-007-api-fact-map.md §6 for the frozen-layer map).
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
    private val learningRepository: LearningRepositoryImpl? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val listLearningFacts: (suspend (String) -> List<LearningFactReceipt>)? = null,
    private val timeZone: TimeZone = TimeZone.getDefault()
) : LearningOverviewProgressPort {
    override suspend fun getProgress(
        spaceId: String,
        sessionIds: List<String>?
    ): LearningProgressContract {
        val readFacts = listLearningFacts ?: return LearningProgressContract()
        val facts = readFacts(spaceId).filterByScope(sessionIds)
        val current = aggregateMastery(facts) ?: return LearningProgressContract()
        val weekStart = weekStartMillis(nowEpochMillis(), timeZone)
        val baseline = aggregateMastery(facts.filter { it.occurredAtEpochMillis < weekStart })
        return LearningProgressContract(
            totalKnowledgePoints = current.total,
            masteredCount = current.mastered,
            masteryRate = current.rate,
            // Baseline = the same deterministic fold over receipts that
            // predate this week. No baseline history => every mastered point
            // was gained this week, so the change equals the current rate.
            weeklyChange = current.rate - (baseline?.rate ?: 0f)
        )
    }

    private fun aggregateMastery(facts: List<LearningFactReceipt>): MasteryAggregate? {
        if (facts.isEmpty()) return null
        val snapshots = MasteryLedgerProjection(UnboundedSnapshots).project(facts)
        if (snapshots.isEmpty()) return null
        val mastered = snapshots.count { it.score >= MasteredScoreThreshold }
        return MasteryAggregate(
            mastered = mastered,
            total = snapshots.size,
            rate = mastered.toFloat() / snapshots.size
        )
    }

    private data class MasteryAggregate(val mastered: Int, val total: Int, val rate: Float)

    private companion object {
        /** At/above the variant_handling band boundary counts as 掌握. */
        const val MasteredScoreThreshold = 70f
    }
}

class LearningOverviewThreadPortAdapter(
    private val learningRepository: LearningRepositoryImpl? = null,
    private val listLearningFacts: (suspend (String) -> List<LearningFactReceipt>)? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault()
) : LearningOverviewThreadPort {
    override suspend fun listWeeklyMainline(
        spaceId: String,
        sessionIds: List<String>?,
        limit: Int
    ): List<LearningThreadContract> {
        val readFacts = listLearningFacts ?: return emptyList()
        val facts = readFacts(spaceId).filterByScope(sessionIds)
        if (facts.isEmpty()) return emptyList()
        val now = nowEpochMillis()
        val weekStart = weekStartMillis(now, timeZone)
        val weeklyFacts = facts.filter { it.occurredAtEpochMillis in weekStart..now }
        if (weeklyFacts.isEmpty()) return emptyList()
        // Progress is the FULL-ledger mastery score (0..100 mapped to the
        // 0..1 fraction the UI's fillMaxWidth consumes), never a
        // window-local score — a KP touched this week still carries its
        // whole history.
        val scoreByKnowledgePoint = MasteryLedgerProjection(UnboundedSnapshots)
            .project(facts)
            .associateBy { it.knowledgePoint }
        return weeklyFacts
            .groupBy { it.knowledgePoint }
            .mapNotNull { (knowledgePoint, group) ->
                val snapshot = scoreByKnowledgePoint[knowledgePoint]
                    ?: return@mapNotNull null
                LearningThreadContract(
                    id = knowledgePoint,
                    title = knowledgePoint,
                    knowledgePoint = knowledgePoint,
                    progress = (snapshot.score / MasteryLedgerProjection.MaxScore).coerceIn(0f, 1f),
                    updatedAtEpochMillis = group.maxOf { it.occurredAtEpochMillis }
                )
            }
            .sortedWith(
                compareByDescending<LearningThreadContract> { it.updatedAtEpochMillis }
                    .thenBy { it.id }
            )
            .take(limit)
    }
}

class LearningOverviewWeakPointPortAdapter(
    private val memoryRepository: MemoryRepository? = null,
    private val listErrors: (suspend (String) -> List<ErrorLog>)? = memoryRepository?.let { repo ->
        { spaceId -> repo.snapshot(spaceId).errors }
    }
) : LearningOverviewWeakPointPort {
    override suspend fun listWeakPoints(
        spaceId: String,
        sessionIds: List<String>?,
        limit: Int
    ): List<WeakPointContract> {
        val readErrors = listErrors ?: return emptyList()
        val errors = readErrors(spaceId).filterNot { it.resolved }
        if (errors.isEmpty()) return emptyList()
        val grouped = errors.groupBy { it.code ?: it.title }
        // Severity is RELATIVE: the largest unresolved group anchors 1.0, so
        // the UI tier thresholds (>=0.7 HIGH / >=0.4 MEDIUM) actually spread
        // groups across tiers. The error log is space-scoped; session scope
        // is not recorded on error rows.
        val maxCount = grouped.values.maxOf { it.size }.toFloat()
        return grouped
            .map { (keyPoint, groupErrors) ->
                WeakPointContract(
                    id = keyPoint,
                    knowledgePoint = keyPoint,
                    errorCount = groupErrors.size,
                    lastErrorEpochMillis = groupErrors.maxOf { it.createdAtEpochMillis },
                    severity = if (maxCount <= 0f) 0f else groupErrors.size / maxCount
                )
            }
            .sortedWith(
                compareByDescending<WeakPointContract> { it.errorCount }.thenBy { it.id }
            )
            .take(limit)
    }
}

/**
 * 每日总结读适配器：统计部分永远由确定性折叠得出；AI 总结段只读当日
 * 落库结果（仓库优先、接缝兜底），配合 [DailySummaryGenerationState]
 * 呈现 生成中/失败 状态。不造数：无台账接缝时返回全默认契约。
 */
class LearningOverviewDailySummaryPortAdapter(
    private val learningRepository: LearningRepositoryImpl? = null,
    private val listLearningFacts: (suspend (String) -> List<LearningFactReceipt>)? = null,
    private val listPlanTasks: (suspend (String) -> List<StudyPlanTask>)? = null,
    private val listTokenUsage: (suspend (String) -> List<TokenUsageRecord>)? = null,
    private val findStoredSummary: (suspend (String, Long) -> DailySummary?)? = null,
    private val generationState: DailySummaryGenerationState? = null,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault()
) : LearningOverviewDailySummaryPort {
    override suspend fun getDailySummary(
        spaceId: String,
        sessionIds: List<String>?
    ): DailySummaryContract {
        val readFacts = listLearningFacts ?: return DailySummaryContract()
        val now = nowEpochMillis()
        val dayStart = dayStartMillis(now, timeZone)
        val planTasks = learningRepository?.listTasks(spaceId)
            ?: listPlanTasks?.invoke(spaceId)
            ?: emptyList()
        val tokenUsage = learningRepository?.listTokenUsage(spaceId)
            ?: listTokenUsage?.invoke(spaceId)
            ?: emptyList()
        val stats = computeDailyActivityStats(
            facts = readFacts(spaceId),
            planTasks = planTasks,
            tokenUsage = tokenUsage,
            sessionIds = sessionIds,
            dayStart = dayStart,
            now = now
        )
        val stored = learningRepository?.findLatestDailySummary(
            spaceId,
            dayStart,
            DailySummaryGenerator.GeneratorVersion
        ) ?: findStoredSummary?.invoke(spaceId, dayStart)
        val key = dailySummaryKey(spaceId, dayStart)
        val aiText = stored?.summary?.takeIf { it.isNotBlank() }
        val aiState = when {
            generationState?.isInFlight(key) == true -> DailySummaryAiState.Generating
            aiText != null -> DailySummaryAiState.Ready
            generationState?.isFailed(key) == true -> DailySummaryAiState.Failed
            else -> DailySummaryAiState.None
        }
        return DailySummaryContract(
            dayStartEpochMillis = dayStart,
            evidenceCount = stats.evidenceCount,
            passedCount = stats.passedCount,
            knowledgePoints = stats.knowledgePoints,
            masteredTodayCount = stats.masteredTodayCount,
            planCompletedCount = stats.planCompletedCount,
            totalTokens = stats.totalTokens,
            aiText = aiText,
            aiState = aiState,
            aiGeneratedAtEpochMillis = stored?.generatedAtEpochMillis ?: 0L
        )
    }
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

// ---------------------------------------------------------------------------
// Ledger-read helpers shared by the progress / thread adapters
// ---------------------------------------------------------------------------

/** No cap: overview aggregation folds every knowledge point in the ledger. */
private const val UnboundedSnapshots = Int.MAX_VALUE

/**
 * Local-midnight week start: `now - 6 days` zeroed to 00:00 — the same
 * "本周" window the token adapter uses.
 */
private fun weekStartMillis(nowEpochMillis: Long, timeZone: TimeZone): Long =
    Calendar.getInstance(timeZone).apply {
        timeInMillis = nowEpochMillis
        add(Calendar.DAY_OF_YEAR, -6)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * Session-scope filter for ledger receipts: receipts are written with
 * sourceWindowId set to the session window id (the guided-learning pipeline
 * passes sessionId as windowId), so a non-null scope maps directly onto
 * receipt provenance; null keeps the whole space.
 */
private fun List<LearningFactReceipt>.filterByScope(
    sessionIds: List<String>?
): List<LearningFactReceipt> =
    sessionIds?.let { ids -> filter { it.sourceWindowId in ids } } ?: this
