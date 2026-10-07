package com.reversetutor.core.domain

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 遗忘曲线纯函数（设计稿：docs/specs/forgetting-curve-design.md，2026-09-29 定稿落地）。
 *
 * 模型：事件级指数衰减 R_i(t) = exp(-(t - t_i) / S_i)，节点遗忘是事件集体遗忘的涌现（D7）。
 *
 * - 初始稳定度 S_0 = 24 × eff² 天（eff = 证据分，见 [EvidenceEffectiveness]；
 *   partial 事件 eff × 0.75）；
 * - 复习链倍增：S = S_0 × G^k（G = 2，封顶 180 天）；成功复习（retrieval /
 *   delayed_retrieval / correction 且非 failed）计链，一次 failed 抵消两次成功
 *   （k_eff = max(0, k_success − 2 × k_lapse)）；
 * - 无状态：所有数值由 append-only 事件流现算（D2「strength 不手存」），不改旧事件、
 *   不加新表；抢救 = 追加新巩固事件自然重置（D7），无需特判；
 * - D7 三阶段（取节点当前稳定度 S_last，Δt = now − t_last）：
 *   Δt ≤ 1.0 S 冷却保护；1.0 S < Δt ≤ 3.0 S 断链离散
 *   （progress = (Δt − S) / (2S) ∈ (0, 1]）；Δt > 3.0 S 坠入核心。
 *
 * 纯 Kotlin、无 Android 依赖、无 IO；输入乱序时内部按时间戳排序，同一事件流
 * 永远得到同一结果。
 */

/** 参与遗忘计算的归一化学习事件（从 [LearningFactReceipt] 投影，只保留算法所需字段）。 */
data class ForgettingEvent(
    val evidenceType: String,
    val result: String,
    val occurredAtEpochMillis: Long
)

/** 节点（事件流聚合后）的遗忘阶段（D7 三阶段的函数口径）。 */
sealed interface ForgettingNodeStage {
    /** 无任何巩固事件（空流 / 只有未知证据或 failed）：不判遗忘，交由界面层保护策略。 */
    data object NoEvidence : ForgettingNodeStage

    /** 冷却保护期：Δt ≤ 1.0 × S_last。 */
    data class Protection(
        val stabilityDays: Float,
        val elapsedDays: Float
    ) : ForgettingNodeStage

    /** 断链离散期：1.0 S < Δt ≤ 3.0 S（闪烁呼吸 = 即将遗忘警告，可点按抢救）。 */
    data class Decaying(
        /** 遗忘进度 ∈ (0, 1]，1.0 = 坠入核心。 */
        val progress: Float,
        /** 该时刻最近巩固事件的保持率 e^(−Δt/S)。 */
        val retention: Float,
        val stabilityDays: Float
    ) : ForgettingNodeStage

    /** 坠入核心：Δt > 3.0 × S_last（投影消失，数据保留）。 */
    data object Absorbed : ForgettingNodeStage
}

/** 账本收据 → 遗忘事件（只保留 evidenceType / result / occurredAt，其余字段不参与）。 */
fun LearningFactReceipt.toForgettingEvent(): ForgettingEvent = ForgettingEvent(
    evidenceType = evidenceType,
    result = result,
    occurredAtEpochMillis = occurredAtEpochMillis
)

fun List<LearningFactReceipt>.toForgettingEvents(): List<ForgettingEvent> = map { it.toForgettingEvent() }

object ForgettingCurve {
    /** S_0 = InitialStabilityDays × eff^InitialStabilityExponent（天）。 */
    const val InitialStabilityDays = 24f
    const val InitialStabilityExponent = 2

    /** 复习链倍增系数 G。 */
    const val ReviewChainGain = 2f

    /** 稳定度封顶 S_max（天）。 */
    const val MaxStabilityDays = 180f

    /** 一次 failed 抵消的成功复习次数。 */
    const val LapsePenaltyFactor = 2

    /** 断链阈值（× S_last）。 */
    const val DecayThreshold = 1f

    /** 坠入核心阈值（× S_last）。 */
    const val AbsorbThreshold = 3f

    /** partial 事件的证据分折算系数。 */
    const val PartialEvidenceFactor = 0.75f

    const val MillisPerDay = 86_400_000L
    const val ResultPassed = "passed"
    const val ResultPartial = "partial"
    const val ResultFailed = "failed"

    /** 证据分 eff（与 MasteryLedgerProjection.EvidenceTargetScores 同源 / 100）。 */
    val EvidenceEffectiveness: Map<String, Float> = mapOf(
        "explanation" to 0.35f,
        "retrieval" to 0.55f,
        "transfer" to 0.72f,
        "delayed_retrieval" to 0.82f,
        "correction" to 0.90f
    )

    /** 计入复习链（倍增）的成功复习证据类型；explanation 是基线接触，不进链。 */
    val ReviewChainEvidenceTypes: Set<String> = setOf(
        "retrieval",
        "delayed_retrieval",
        "correction"
    )

    /**
     * 事件的有效证据分：passed → 表值；partial → 表值 × 0.75；
     * failed / 未知证据 / none → null（不构成巩固事件）。
     */
    fun effectiveness(evidenceType: String, result: String): Float? {
        val base = EvidenceEffectiveness[evidenceType] ?: return null
        return when (result) {
            ResultPassed -> base
            ResultPartial -> base * PartialEvidenceFactor
            else -> null
        }
    }

    /** 初始稳定度 S_0（天）：24 × eff²。 */
    fun initialStabilityDays(eff: Float): Float =
        InitialStabilityDays * eff.toDouble().pow(InitialStabilityExponent.toDouble()).toFloat()

    /** 最近一次巩固事件（时间戳最大）。 */
    fun latestConsolidatingEvent(events: List<ForgettingEvent>): ForgettingEvent? =
        events.filter { effectiveness(it.evidenceType, it.result) != null }
            .maxByOrNull { it.occurredAtEpochMillis }

    /**
     * 节点当前稳定度 S_last（天）：S_0(最近巩固事件) × G^k_eff，封顶 180 天。
     * 无巩固事件（空流 / 只有 failed 或未知证据）返回 null。
     */
    fun stabilityDays(events: List<ForgettingEvent>): Float? {
        val latest = latestConsolidatingEvent(events) ?: return null
        val eff = effectiveness(latest.evidenceType, latest.result) ?: return null
        val s0 = initialStabilityDays(eff)
        var kSuccess = 0
        var kLapse = 0
        events.forEach { event ->
            if (event.evidenceType !in EvidenceEffectiveness) return@forEach
            when (event.result) {
                ResultFailed -> kLapse++
                ResultPassed, ResultPartial ->
                    if (event.evidenceType in ReviewChainEvidenceTypes) kSuccess++
            }
        }
        val kEff = max(0, kSuccess - LapsePenaltyFactor * kLapse)
        return min(s0 * ReviewChainGain.pow(kEff.toFloat()), MaxStabilityDays)
    }

    /** D7 三阶段判定（纯函数）。now 早于最近事件时按 Δt=0 处理（时钟回拨保护）。 */
    fun stage(events: List<ForgettingEvent>, nowEpochMillis: Long): ForgettingNodeStage {
        val latest = latestConsolidatingEvent(events)
            ?: return ForgettingNodeStage.NoEvidence
        val s = stabilityDays(events) ?: return ForgettingNodeStage.NoEvidence
        val elapsedDays =
            (nowEpochMillis - latest.occurredAtEpochMillis).coerceAtLeast(0L).toDouble() / MillisPerDay
        return when {
            elapsedDays <= DecayThreshold * s ->
                ForgettingNodeStage.Protection(stabilityDays = s, elapsedDays = elapsedDays.toFloat())
            elapsedDays <= AbsorbThreshold * s -> ForgettingNodeStage.Decaying(
                progress = ((elapsedDays - DecayThreshold * s) /
                    ((AbsorbThreshold - DecayThreshold) * s)).toFloat(),
                retention = exp(-elapsedDays / s).toFloat(),
                stabilityDays = s
            )
            else -> ForgettingNodeStage.Absorbed
        }
    }

    /**
     * 视觉强度（§5，与状态机双轨：只做 LOD 与视觉权重，不参与遗忘状态机）：
     * strength = Σ_i eff_i × R_i(t)，R_i 用该事件发生时刻的链稳定度 S_i。
     * 无巩固事件返回 0。多条弱事件堆出的高强度**不会**推迟遗忘（遗忘只看 S_last）。
     */
    fun strength(events: List<ForgettingEvent>, nowEpochMillis: Long): Float {
        var kSuccess = 0
        var kLapse = 0
        var sum = 0f
        events.sortedBy { it.occurredAtEpochMillis }.forEach { event ->
            if (event.evidenceType in EvidenceEffectiveness) {
                when (event.result) {
                    ResultFailed -> kLapse++
                    ResultPassed, ResultPartial ->
                        if (event.evidenceType in ReviewChainEvidenceTypes) kSuccess++
                }
            }
            val eff = effectiveness(event.evidenceType, event.result) ?: return@forEach
            val kEff = max(0, kSuccess - LapsePenaltyFactor * kLapse)
            val s = min(initialStabilityDays(eff) * ReviewChainGain.pow(kEff.toFloat()), MaxStabilityDays)
            val elapsedDays = (nowEpochMillis - event.occurredAtEpochMillis)
                .coerceAtLeast(0L).toDouble() / MillisPerDay
            sum += eff * exp(-elapsedDays / s).toFloat()
        }
        return sum
    }
}
