package com.reversetutor.feature.reversetutor.score

import com.reversetutor.feature.reversetutor.core.PyCompat
import kotlin.math.max
import kotlin.math.min

/**
 * 评分引擎（规格源：spike app/tools/evaluation.py）：
 * evaluate_session_quality（四维质量分）与 detect_teaching_failure（卡滞/情绪检测）。
 */
object ScoreEngine {

    /** clamp 到 [lo, hi]（对齐 spike clamp）。 */
    fun clamp(v: Double, lo: Double, hi: Double): Double = max(lo, min(hi, v))

    /** understanding_level 解析（1-5，非法回退 default）。对齐 clamp_int：先 round 后 clamp。 */
    fun levelOf(v: Double?, default: Int = 1): Int =
        if (v == null || v.isNaN() || v.isInfinite()) default
        else clamp(PyCompat.round(v), 1.0, 5.0).toInt()

    data class QualityInput(
        val currentRound: Int,          // 会话轮次（0 时取 userMsgCount.max(1)）
        val userMsgCount: Int,          // transcript 中 user 消息数
        val toolDiversity: Int,         // 去重工具数
        val breakthroughs: Int,         // 顿悟事件数
        val struggles: Int,             // 挣扎事件数
        val understandingLevel: Int,    // 会话水平 1-5
        val toolsUsed: List<String> = emptyList()
    )

    data class QualityResult(
        val qualityScore: Double,
        val goalAchievement: Double,
        val studentEngagement: Double,
        val teachingEfficiency: Double,
        val conceptualDepth: Double,
        val highlights: List<String>,
        val missedOpportunities: List<String>
    )

    /** 四维会话质量（对齐 spike _evaluate_session_quality 的全部常数与文案）。 */
    fun evaluateSessionQuality(input: QualityInput): QualityResult {
        val rounds = if (input.currentRound > 0) input.currentRound else max(input.userMsgCount, 1)
        val bt = input.breakthroughs
        val sg = input.struggles
        val toolDiv = input.toolDiversity
        val level = input.understandingLevel

        val goalAchievement = clamp(0.3 + 0.18 * bt + (level - 1) * 0.1, 0.0, 1.0)
        val engagement = clamp(0.4 + 0.1 * min(rounds, 6) - 0.08 * sg, 0.0, 1.0)
        val efficiency = clamp(0.8 - max(0, rounds - 8) * 0.05 + 0.05 * min(toolDiv, 5), 0.0, 1.0)
        val depth = clamp(0.3 + level * 0.12 + 0.05 * min(toolDiv, 4), 0.0, 1.0)
        val quality = round2((goalAchievement + engagement + efficiency + depth) / 4)

        val highlights = mutableListOf<String>()
        if (bt > 0) highlights.add("发生了 $bt 次顿悟，核心概念被真正讲通")
        if (toolDiv >= 4) highlights.add("使用了 $toolDiv 种不同工具，策略路径多样")
        val missed = mutableListOf<String>()
        if (input.toolsUsed.none { it.contains("challenge") }) {
            missed.add("未测试迁移能力（可用 challenge_teacher_explanation 或变式问题）")
        }
        if (sg > 0) missed.add("有 $sg 次卡顿，可以更早给出脚手架")

        return QualityResult(
            qualityScore = quality,
            goalAchievement = round2(goalAchievement),
            studentEngagement = round2(engagement),
            teachingEfficiency = round2(efficiency),
            conceptualDepth = round2(depth),
            highlights = if (highlights.isEmpty()) listOf("完成了基本的教与学闭环") else highlights,
            missedOpportunities = missed
        )
    }

    /** 单条交互记录（卡滞检测输入）。 */
    data class InteractionItem(
        val understandingLevel: Int?,
        val emotion: String
    )

    data class FailureResult(
        val isFailing: Boolean,
        val failurePoints: List<String>,
        val probableCauses: List<String>,
        val correctiveActions: List<String>,
        val stallRounds: Int,
        val currentStrategy: String
    )

    /** 教学失败检测（对齐 spike _detect_teaching_failure）。 */
    fun detectTeachingFailure(
        log: List<InteractionItem>,
        currentStrategy: String = ""
    ): FailureResult {
        val levels = log.map { it.understandingLevel ?: 1 }
        var stall = 0
        // Python: for i in range(len(levels)-1, 0, -1) —— 从尾倒数、到 i=1 为止、遇提升即停
        for (i in levels.size - 1 downTo 1) {
            if (levels[i] <= levels[i - 1]) stall++ else break
        }
        val emotions = log.map { it.emotion }
        val recent = emotions.takeLast(2)
        val deteriorated = recent.contains("frustrated") || recent.contains("discouraged")
        val isFailing = stall >= 3 || (deteriorated && stall >= 2)

        val failurePoints = mutableListOf<String>()
        if (stall >= 3) failurePoints.add("连续 $stall 轮理解水平无提升")
        if (deteriorated) failurePoints.add("学生情绪在近期转为 frustrated/discouraged")
        if (failurePoints.isEmpty()) failurePoints.add("未见明显失败信号")

        return FailureResult(
            isFailing = isFailing,
            failurePoints = failurePoints,
            probableCauses = if (isFailing) listOf(
                "当前教学策略与该学生不匹配",
                "前置知识掌握不牢，推导链条断裂"
            ) else emptyList(),
            correctiveActions = if (isFailing) listOf("切换到几何/图像解释", "回到前置知识补缺")
            else listOf("保持当前策略并继续观察"),
            stallRounds = stall,
            currentStrategy = currentStrategy.ifEmpty { "未指定" }
        )
    }

    /** Python round(x, 2)：银行家舍入（HALF_EVEN），非 HALF_UP。 */
    private fun round2(v: Double): Double = PyCompat.round(v, 2)
}
