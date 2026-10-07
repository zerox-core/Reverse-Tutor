package com.reversetutor.feature.chat

/**
 * R96 思考预算决策器：按轮决定创建流程是否请求模型思考（enable_thinking）。
 *
 * 背景（2026-09-28 实测）：开思考时长轮总时延 5.2s→32.3s（推理阶段 ~23s 真实
 * 生成），但创建契约（每轮单 JSON 对象、多约束一致性）是思考受益型任务；短答复
 * 承接轮思考几乎无收益。兼顾策略：
 * - 资料 / 复杒输入 → 开（质量敏感，且思考期有「思考中」气泡可见）；
 * - 短答复承接澄清 → 关（秒级响应，正文爆发 ~4.5s 可接受）。
 *
 * 纯函数、无 Android 依赖，规则保持可解释、可单测。
 */
object ThinkingBudgetDecider {

    data class Decision(val enabled: Boolean, val reason: String)

    /** 多约束信号词：出现 2 个及以上视为需要一致性自检的复杒轮。 */
    private val constraintMarkers = listOf(
        "并且", "同时", "还要", "另外", "不要", "不能", "必须", "除了", "以及", "但别"
    )

    /** 长输入阈值：超过视为多信息整合轮。 */
    private const val LONG_INPUT_CHARS = 120

    /** 短答复阈值：不超过 12 字且无约束信号词才视为承接澄清的简单轮
     * （澄清型答复如「高三的」多在 8 字内；12 字以上的请求大多是实质性输入，不敢关）。 */
    private const val SHORT_ANSWER_CHARS = 12

    fun decide(
        userText: String,
        hasDocAnalysis: Boolean,
        isFirstTurn: Boolean
    ): Decision {
        val text = userText.trim()
        val markerHits = constraintMarkers.count { text.contains(it) }
        return when {
            hasDocAnalysis -> Decision(true, "携带资料文档，需一致性抽取")
            isFirstTurn -> Decision(true, "首轮从零建立人设契约")
            markerHits >= 2 -> Decision(true, "多约束输入（信号词×$markerHits），需自检")
            text.length >= LONG_INPUT_CHARS -> Decision(true, "长输入（${text.length}字），多信息整合")
            text.length <= SHORT_ANSWER_CHARS && markerHits == 0 ->
                Decision(false, "短答复承接澄清（${text.length}字），思考无收益")
            else -> Decision(true, "默认保质量")
        }
    }
}
