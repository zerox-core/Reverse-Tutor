package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.llm.SessionSummaryOutcome
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.MasteryLedgerProjection
import com.reversetutor.core.model.DailySummary
import com.reversetutor.core.model.StudyPlanTask
import com.reversetutor.core.model.TokenUsageRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * V1 方向三「每日总结」：懒生成协调器与共享状态。
 *
 * 用户拍板的两条决策（2026-09-30）：
 * 1. 混合路线——确定性统计永远在，AI 人话总结后台异步生成，失败只显示统计；
 * 2. 打开面板懒生成——当天没有有效总结就后台生成，下次刷新可见。
 *
 * 生成绝不发生在聊天轮次内（契约 KDoc 禁止），由面板打开触发、走独立
 * 后台 scope。空数据日不烧模型额度；当日数据比上次生成明显变多
 * （≥ [DailySummaryGenerator.RegenEvidenceThreshold] 条新证据）才允许重生成。
 */

/** 面板打开触发懒生成后的决策结果，测试与排错用。 */
enum class DailySummaryRequestResult {
    Started,
    AlreadyFresh,
    AlreadyInFlight,
    SkippedNoActivity,
    FailedRecently,
    NoSession
}

/** 生成状态在读写两个适配器之间共享（进程内、不持久化）。 */
class DailySummaryGenerationState {
    private val lock = Any()
    private val inFlightKeys = mutableSetOf<String>()
    private val failedKeys = mutableSetOf<String>()
    private val mutableUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** 每次后台生成结束（成功或失败）发一条，面板据此静默刷新。 */
    val updates: SharedFlow<Unit> = mutableUpdates

    fun isInFlight(key: String): Boolean = synchronized(lock) { key in inFlightKeys }

    fun isFailed(key: String): Boolean = synchronized(lock) { key in failedKeys }

    fun markInFlight(key: String) = synchronized(lock) { inFlightKeys += key }

    fun clearInFlight(key: String) = synchronized(lock) { inFlightKeys -= key }

    fun markFailed(key: String) = synchronized(lock) { failedKeys += key }

    fun bump() {
        mutableUpdates.tryEmit(Unit)
    }
}

/** 确定性日统计——每日总结卡的底座，也是 AI 提示词的事实来源。 */
data class DailyActivityStats(
    val dayStartEpochMillis: Long,
    val evidenceCount: Int,
    val passedCount: Int,
    val knowledgePoints: List<String>,
    val masteredTodayCount: Int,
    val planCompletedCount: Int,
    val totalTokens: Long
)

/** At/above the variant_handling band boundary counts as 掌握（与 ProgressPortAdapter 同值）. */
private const val MasteredScoreThreshold = 70f

/** 当地时区当天 00:00。 */
internal fun dayStartMillis(nowEpochMillis: Long, timeZone: TimeZone): Long {
    val calendar = java.util.Calendar.getInstance(timeZone)
    calendar.timeInMillis = nowEpochMillis
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

internal fun dailySummaryKey(spaceId: String, dayStartEpochMillis: Long): String =
    "$spaceId|$dayStartEpochMillis"

/**
 * 从已存在的只读数据（台账 / 计划 / token）确定性折叠出当日统计。
 * 纯函数、不造数：没有台账接缝时输入为空，统计全为 0。
 */
internal fun computeDailyActivityStats(
    facts: List<LearningFactReceipt>,
    planTasks: List<StudyPlanTask>,
    tokenUsage: List<TokenUsageRecord>,
    sessionIds: List<String>?,
    dayStart: Long,
    now: Long
): DailyActivityStats {
    val scopedFacts = sessionIds
        ?.let { ids -> facts.filter { it.sourceWindowId in ids } }
        ?: facts
    val todayFacts = scopedFacts.filter { it.occurredAtEpochMillis in dayStart..now }
    val knowledgePoints = todayFacts
        .sortedByDescending { it.occurredAtEpochMillis }
        .map { it.knowledgePoint }
        .distinct()
    val projection = MasteryLedgerProjection(Int.MAX_VALUE)
    val beforeScores = projection
        .project(scopedFacts.filter { it.occurredAtEpochMillis < dayStart })
        .associate { it.knowledgePoint to it.score }
    val afterSnapshots = projection.project(scopedFacts.filter { it.occurredAtEpochMillis <= now })
    val masteredTodayCount = afterSnapshots.count { snapshot ->
        (beforeScores[snapshot.knowledgePoint] ?: 0f) < MasteredScoreThreshold &&
            snapshot.score >= MasteredScoreThreshold
    }
    val planCompletedCount = planTasks.count { task ->
        val completedAt = task.completedAtEpochMillis
        completedAt != null && completedAt in dayStart..now
    }
    val totalTokens = tokenUsage
        .filter { it.createdAtEpochMillis in dayStart..now }
        .sumOf { it.totalTokens.coerceAtLeast(0L) }
    return DailyActivityStats(
        dayStartEpochMillis = dayStart,
        evidenceCount = todayFacts.size,
        passedCount = todayFacts.count { it.result.equals("passed", ignoreCase = true) },
        knowledgePoints = knowledgePoints,
        masteredTodayCount = masteredTodayCount,
        planCompletedCount = planCompletedCount,
        totalTokens = totalTokens
    )
}

/** AI 人话总结的提示词：只喂事实，口吻温暖克制不施压（与教学人格隔离）。 */
internal fun buildDailySummaryPrompt(
    stats: DailyActivityStats,
    timeZone: TimeZone
): String = buildString {
    val dateLabel = SimpleDateFormat("yyyy-MM-dd", Locale.SIMPLIFIED_CHINESE)
        .apply { this.timeZone = timeZone }
        .format(Date(stats.dayStartEpochMillis))
    append("你是一名学习陪伴助手。请根据以下今日学习数据，为用户写一段 2-4 句的每日学习总结。")
    append("口吻温暖克制、像朋友一起回顾今天，不施压、不说教；不要列表，不要标题，直接输出总结正文。")
    append("\n\n")
    append("日期：").append(dateLabel).append('\n')
    append("今日涉及知识点（").append(stats.knowledgePoints.size).append(" 个）：")
    append(stats.knowledgePoints.take(8).joinToString("、")).append('\n')
    append("练习证据：").append(stats.evidenceCount)
    append(" 条，其中通过 ").append(stats.passedCount).append(" 条").append('\n')
    append("今日新掌握知识点：").append(stats.masteredTodayCount).append(" 个").append('\n')
    append("完成计划任务：").append(stats.planCompletedCount).append(" 项").append('\n')
    append("今日 token 消耗：约 ")
    append(String.format(Locale.SIMPLIFIED_CHINESE, "%.1fk", stats.totalTokens / 1000f))
}

/**
 * 懒生成器：决定「今天要不要生成」并后台执行生成 + 落库。
 *
 * 失败不落库、不破坏已有总结；进程内记一次失败后不再反复烧额度，
 * 应用重启后允许重试（失败状态不持久化是有意为之）。
 */
class DailySummaryGenerator(
    private val generateSummary: suspend (sessionId: String, promptText: String) -> SessionSummaryOutcome,
    private val findStored: suspend (spaceId: String, dayStartEpochMillis: Long) -> DailySummary?,
    private val saveStored: suspend (DailySummary) -> Unit,
    private val listLearningFacts: suspend (spaceId: String) -> List<LearningFactReceipt>,
    private val listPlanTasks: suspend (spaceId: String) -> List<StudyPlanTask>,
    private val listTokenUsage: suspend (spaceId: String) -> List<TokenUsageRecord>,
    private val pickSessionId: suspend (spaceId: String) -> String?,
    private val state: DailySummaryGenerationState,
    private val generationScope: CoroutineScope,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val timeZone: TimeZone = TimeZone.getDefault()
) {
    companion object {
        const val GeneratorVersion = "daily-v1"

        /** 当日新增证据达到该数量才允许重生成，控制模型额度消耗。 */
        const val RegenEvidenceThreshold = 10L

        /** 落库总结的长度上限。 */
        const val MaxSummaryChars = 1200

        private const val DayMillis = 24L * 60L * 60L * 1000L
    }

    /** 决策 + 后台启动。suspend 只用于决策所需的本地读，LLM 调用全程在后台。 */
    suspend fun requestGeneration(spaceId: String): DailySummaryRequestResult {
        val now = nowEpochMillis()
        val dayStart = dayStartMillis(now, timeZone)
        val key = dailySummaryKey(spaceId, dayStart)
        if (state.isInFlight(key)) return DailySummaryRequestResult.AlreadyInFlight

        val facts = listLearningFacts(spaceId)
        val todayEvidenceCount = facts.count { it.occurredAtEpochMillis in dayStart..now }
        if (todayEvidenceCount == 0) return DailySummaryRequestResult.SkippedNoActivity

        val stored = findStored(spaceId, dayStart)
        if (stored != null && !stored.stale &&
            todayEvidenceCount < stored.sourceRevision + RegenEvidenceThreshold
        ) return DailySummaryRequestResult.AlreadyFresh

        if (stored == null && state.isFailed(key)) return DailySummaryRequestResult.FailedRecently

        val sessionId = pickSessionId(spaceId) ?: return DailySummaryRequestResult.NoSession

        state.markInFlight(key)
        generationScope.launch {
            try {
                val stats = computeDailyActivityStats(
                    facts = facts,
                    planTasks = listPlanTasks(spaceId),
                    tokenUsage = listTokenUsage(spaceId),
                    sessionIds = null,
                    dayStart = dayStart,
                    now = now
                )
                when (val outcome = generateSummary(sessionId, buildDailySummaryPrompt(stats, timeZone))) {
                    is SessionSummaryOutcome.Generated -> saveStored(
                        DailySummary(
                            id = "daily-$spaceId-$dayStart-$GeneratorVersion",
                            spaceId = spaceId,
                            dayStartEpochMillis = dayStart,
                            dayEndEpochMillis = dayStart + DayMillis - 1L,
                            sourceRevision = todayEvidenceCount.toLong(),
                            generatorVersion = GeneratorVersion,
                            summary = outcome.summaryText.take(MaxSummaryChars),
                            generatedAtEpochMillis = nowEpochMillis(),
                            stale = false
                        )
                    )
                    else -> state.markFailed(key)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                state.markFailed(key)
            } finally {
                state.clearInFlight(key)
                state.bump()
            }
        }
        return DailySummaryRequestResult.Started
    }
}
