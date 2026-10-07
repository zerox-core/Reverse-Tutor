package com.reversetutor.feature.chat

import kotlin.math.roundToInt

/**
 * 创建会话了解程度算法（设计方案 v3 · 第十章 10.2）。
 *
 * 双源混合：确定性字段覆盖分 u_det + LLM 自评 u_llm 按 0.5/0.5 融合；
 * 必填（title/learnerRole）缺失封顶 60；展示值单调不减（回退会打击信任）。
 * 全部为纯函数，可单测，不依赖任何 LLM 行为。
 */
object AgentCreationUnderstanding {

    const val CAP_REQUIRED_MISSING = 60
    const val CAP_FULL = 100

    private const val DEFAULT_OPENING = "准备好后，请开始讲给我听吧。"
    private const val UNSET = "未设置"

    /** 教学风格 / 偏好是否已谈（10.2 权重表 teachingStyle 行口径）。 */
    fun hasTeachingStyle(draft: NewSessionConfiguration): Boolean =
        draft.dialogueStrategy.isNotBlank() ||
            draft.feedbackIntensity != 3 ||
            draft.probingIntensity != 3 ||
            draft.scaffoldingIntensity != 3

    /** 约束 / 禁区是否已谈（10.2 权重表 constraints 行口径）。 */
    fun hasConstraints(draft: NewSessionConfiguration): Boolean =
        draft.plan.isNotBlank() ||
            draft.stageMilestones != UNSET ||
            draft.learningScope != UNSET

    /** 人物性格是否已成型（R84 链路中间环：目标 → 人物性格 → 教学方式）。 */
    fun hasPersona(draft: NewSessionConfiguration): Boolean = draft.persona.isNotBlank()

    /**
     * u_det：字段覆盖加权和（满分 115，钳到 100）。
     * goal 20 / learnerRole 20 / title 15 / persona 15 / teachingStyle 15 /
     * constraints 10 / learnerProfile 5 / openingMessage-story 5 / 文档 +10。
     */
    fun deterministicScore(
        draft: NewSessionConfiguration,
        hasAnalyzedDocument: Boolean
    ): Int {
        var score = 0
        if (draft.goal.isNotBlank()) score += 20
        if (draft.learnerRole.isNotBlank()) score += 20
        if (draft.title.isNotBlank()) score += 15
        if (draft.persona.isNotBlank()) score += 15
        if (draft.learnerProfile.isNotBlank()) score += 5
        if (hasTeachingStyle(draft)) score += 15
        if (hasConstraints(draft)) score += 10
        if (draft.story.isNotBlank() || draft.openingMessage != DEFAULT_OPENING) score += 5
        if (hasAnalyzedDocument) score += 10
        return score.coerceAtMost(CAP_FULL)
    }

    /**
     * 融合：u_raw = round(0.5 × u_llm + 0.5 × u_det)；
     * 必填缺失封顶 60；与上一轮取大者保证单调不减。
     * llmScore 为 null（LLM 没给分）时退化为纯确定值。
     */
    fun fuse(
        llmScore: Int?,
        detScore: Int,
        previousFused: Int,
        requiredFieldsReady: Boolean
    ): Int {
        val cap = if (requiredFieldsReady) CAP_FULL else CAP_REQUIRED_MISSING
        val llm = (llmScore ?: detScore).coerceIn(0, CAP_FULL)
        val raw = (0.5f * llm + 0.5f * detScore).roundToInt().coerceIn(0, cap)
        return maxOf(previousFused, raw).coerceAtMost(cap)
    }

    /**
     * 兜底标题提案（10.3 收敛 / 10.4 title 提案的客户端兜底）：
     * LLM 该给没给时，用 goal 首句裁 14 字合成，避免一直卡 60 封顶。
     */
    fun proposeTitle(goal: String): String {
        val clean = goal.replace(Regex("\\s+"), " ").trim()
        val head = clean.split('，', '。', '；', ',', '.', ';')
            .firstOrNull().orEmpty().ifBlank { clean }
        return (head.take(14).ifBlank { "新会话" }) + " · 讲学练"
    }
}
