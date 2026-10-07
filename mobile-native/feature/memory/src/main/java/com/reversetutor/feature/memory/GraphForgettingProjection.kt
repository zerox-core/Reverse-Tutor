package com.reversetutor.feature.memory

import com.reversetutor.core.domain.ForgettingCurve
import com.reversetutor.core.domain.ForgettingNodeStage
import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.domain.toForgettingEvents
import com.reversetutor.core.model.GraphNode

/**
 * R103 遗忘曲线接线（设计稿 docs/specs/forgetting-curve-design.md，2026-09-29 定稿）：
 * 把 learning_fact_receipts 事件流投影成图谱节点的 [BlackHoleForgetProfile]。
 *
 * - 联动键：receipt.knowledgePoint.trim() == node.label.trim()（与图谱节点
 *   「知识点即标签」的既有约定一致）；
 * - 阶段映射（[ForgettingCurve.stage] 的 D7 三阶段）：
 *   Protection → 冷却保护（elapsedDays 预扣，图谱重建时不重新满血）；
 *   Decaying → 已过保、forget 预扣 = 断链离散进度；
 *   Absorbed → initialForget = 1，建档即淡出（远期已遗忘，不走闪烁旅程）；
 *   NoEvidence → 不产出档案：节点回落到 physics 全局时长
 *   （生产 = Float.MAX_VALUE 不遗忘，R82「无证据不遗忘」语义保留）；
 * - 时长换算：S_last 单位是天，引擎单位是秒；保护期 = 1.0S，
 *   断链离散期 = (3.0S − 1.0S) = 2.0S。
 *
 * 纯函数、无 IO；now 由调用方传入（默认系统时钟），便于测试与回放。
 */
object GraphForgettingProjection {

    private const val SECONDS_PER_DAY = 86_400f

    /** 断链离散期跨度（× S_last）：AbsorbThreshold(3) − DecayThreshold(1)。 */
    private const val DECAY_SPAN_STABILITIES = ForgettingCurve.AbsorbThreshold - ForgettingCurve.DecayThreshold

    /**
     * 生成图谱节点的遗忘档案。
     *
     * @param nodes 图谱快照节点（GraphRepository.snapshot().nodes）
     * @param receipts 学习事实收据（LearningLedgerRepository.listLearningFacts）
     * @param nowEpochMillis 计算基准时刻
     * @return nodeId → 档案；只含有巩固事件且标签能对上的节点
     */
    fun profiles(
        nodes: List<GraphNode>,
        receipts: List<LearningFactReceipt>,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Map<String, BlackHoleForgetProfile> {
        val eventsByKnowledgePoint = receipts
            .groupBy { it.knowledgePoint.trim() }
            .mapValues { (_, rs) -> rs.toForgettingEvents() }
        val result = mutableMapOf<String, BlackHoleForgetProfile>()
        nodes.forEach { node ->
            val events = eventsByKnowledgePoint[node.label.trim()] ?: return@forEach
            when (val stage = ForgettingCurve.stage(events, nowEpochMillis)) {
                is ForgettingNodeStage.NoEvidence -> return@forEach
                is ForgettingNodeStage.Protection -> result[node.id] = BlackHoleForgetProfile(
                    protectionSeconds = stage.stabilityDays * SECONDS_PER_DAY,
                    forgettingFullSeconds = DECAY_SPAN_STABILITIES * stage.stabilityDays * SECONDS_PER_DAY,
                    elapsedProtectionSeconds = stage.elapsedDays * SECONDS_PER_DAY,
                    initialForget = 0f
                )
                is ForgettingNodeStage.Decaying -> result[node.id] = BlackHoleForgetProfile(
                    protectionSeconds = stage.stabilityDays * SECONDS_PER_DAY,
                    forgettingFullSeconds = DECAY_SPAN_STABILITIES * stage.stabilityDays * SECONDS_PER_DAY,
                    // 建档时已过保：elapsed 直接置满，forget 预扣 = 断链离散进度
                    elapsedProtectionSeconds = stage.stabilityDays * SECONDS_PER_DAY,
                    initialForget = stage.progress
                )
                ForgettingNodeStage.Absorbed -> {
                    val s = ForgettingCurve.stabilityDays(events) ?: return@forEach
                    result[node.id] = BlackHoleForgetProfile(
                        protectionSeconds = s * SECONDS_PER_DAY,
                        forgettingFullSeconds = DECAY_SPAN_STABILITIES * s * SECONDS_PER_DAY,
                        elapsedProtectionSeconds = 3f * s * SECONDS_PER_DAY,
                        initialForget = 1f
                    )
                }
            }
        }
        return result
    }
}
