package com.reversetutor.feature.chat

/**
 * 一句话命令创建（2026-10-15 用户反馈修复 · 规格逐条照做）。
 *
 * 用户在「AI 创建草案」页说/打一句自然语言命令（例「我想学高中物理的电磁感应」）→
 * 直接变成创建草案所需字段 → 直接创建并进入会话窗口；信息不足只补问**一个**最关键字段，
 * 用户回答后直接创建，不做多轮盘问。
 *
 * 本对象只承载快车道的**确定性兜底**（不进 LLM）：把命令轮 / 单问回答轮的产物补成可创建草案，
 * 保证「用户回答后一定能创建」不依赖模型自觉。创建本身仍走既有 performCreate() 路径。
 */
object AgentCreationOneLineCommand {

    /** 学习者角色兜底（与收敛兜底同口径；仅当命令与回答都没给基础信息时使用）。 */
    const val DEFAULT_LEARNER_ROLE = "基础未说明，按零基础起步"

    /** 单问没有命中任何槽位时的最小兜底问句（真正问不出来时问目标）。 */
    val FALLBACK_QUESTION: String = AgentCreationFollowUpPlanner.Field.Goal.fallbackQuestion

    /** 命令原文兜底成目标时的截断长度。 */
    private const val GOAL_MAX_CHARS = 40

    /**
     * 单问字段 × 用户回答：模型没把回答判定成「亲口给值」时，用**用户原话**落定被问字段，
     * 保证用户答了就不算白答（回答本身就是要补的那个字段值）。
     */
    fun applyCommandAnswer(
        draft: NewSessionConfiguration,
        field: AgentCreationFollowUpPlanner.Field,
        answer: String
    ): NewSessionConfiguration {
        val text = answer.trim()
        if (text.isEmpty()) return draft
        return when (field) {
            AgentCreationFollowUpPlanner.Field.Goal ->
                if (draft.goal.isBlank()) draft.copy(goal = text) else draft
            AgentCreationFollowUpPlanner.Field.LearnerRole ->
                if (draft.learnerRole.isBlank()) draft.copy(learnerRole = text) else draft
            AgentCreationFollowUpPlanner.Field.Persona ->
                if (draft.persona.isBlank()) draft.copy(persona = text) else draft
            AgentCreationFollowUpPlanner.Field.TeachingStyle ->
                if (draft.dialogueStrategy.isBlank()) draft.copy(dialogueStrategy = text) else draft
            AgentCreationFollowUpPlanner.Field.Constraints ->
                if (draft.plan.isBlank()) draft.copy(plan = text) else draft
            // 会话名称是展示字段：不作为单问目标，回答也不覆盖草案。
            AgentCreationFollowUpPlanner.Field.Title -> draft
        }
    }

    /**
     * 补齐创建必填（title / learnerRole）：缺就按确定性兜底补上，
     * 保证 [NewSessionConfiguration.validationErrors] 为空、可以立刻创建。
     * 目标永不默认成别的说法——真没目标时用命令原文（命令本身就是目标声明）。
     */
    fun finalizeForCreate(
        draft: NewSessionConfiguration,
        commandText: String = ""
    ): NewSessionConfiguration {
        val command = commandText.trim().take(GOAL_MAX_CHARS)
        var next = draft
        if (next.goal.isBlank() && command.isNotBlank()) next = next.copy(goal = command)
        if (next.title.isBlank()) {
            next = next.copy(title = AgentCreationUnderstanding.proposeTitle(next.goal.ifBlank { command }))
        }
        if (next.learnerRole.isBlank()) next = next.copy(learnerRole = DEFAULT_LEARNER_ROLE)
        return next
    }

    /** 快车道是否已有足够信息直接创建（创建必填齐全）。 */
    fun readyToCreate(draft: NewSessionConfiguration): Boolean =
        draft.validationErrors().isEmpty()
}
