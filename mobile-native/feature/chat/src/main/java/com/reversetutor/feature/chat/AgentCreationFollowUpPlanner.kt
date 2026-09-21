package com.reversetutor.feature.chat

/**
 * 每轮策略快照（设计方案 v3 · 第十章 10.1 第 2 步「装配上下文」）。
 * 协调器在调用网关前生成，随 converse 传给网关装配进 P2' 提示词。
 */
data class AgentCreationTurnStrategy(
    /** 当前确定性了解度 u_det（10.2 权重表）。 */
    val deterministicUnderstanding: Int = 0,
    /** 本轮之前已完成的用户轮数。 */
    val rounds: Int = 0,
    /** 各字段已问次数（字段 label → 次数），供「2 次未答给默认值提案」判断。 */
    val askedCounts: Map<String, Int> = emptyMap(),
    /** 本轮指定追问字段 label；null = 本轮不允许追问。 */
    val targetFollowUpField: String? = null,
    /** goal 已填而 title 为空：本轮必须在 draft.title 给提案（突破 60 封顶的前提）。 */
    val mustProposeTitle: Boolean = false,
    /** 收敛模式：不追问，缺项给默认值提案，assistantNote 邀请创建。 */
    val converge: Boolean = false,
    /** 已有已分析文档：requestDocument 一律 false。 */
    val documentAvailable: Boolean = false
)

/**
 * 追问策略（10.3）：优先级队列 + 防死循环。
 * 确定性逻辑全在客户端——优先级、同字段 2 次降级、8 轮软上限、
 * 「别问了」立即收敛；LLM 只按策略快照的指令生成措辞，客户端最后把关。
 */
class AgentCreationFollowUpPlanner {

    /** 追问优先级队列（10.3）：goal > learnerRole > title > teachingStyle > constraints。 */
    enum class Field(val label: String, val fallbackQuestion: String) {
        Goal("学习目标", "你想达到什么目标？比如补上某个薄弱块、冲刺一次考试，或者单纯把概念讲透。"),
        LearnerRole("学习者角色", "你现在的基础怎么样？学到哪一块了？"),
        Title("会话名称", "给这个会话起个名字吧？不想起的话，我就用提案的名字了。"),
        TeachingStyle("教学风格偏好", "你希望我怎么学？比如多追问你几个为什么，还是先听你完整讲完再提问。"),
        Constraints("约束条件", "有没有什么禁区或约束？比如不想被打断、只准用教材里的方法，没有就说没有。")
    }

    private val askCounts = mutableMapOf<Field, Int>()

    /** 已完成的用户轮数。 */
    var rounds: Int = 0
        private set

    private var converged: Boolean = false

    /** 本轮前的策略快照：该问什么 / 是否收敛 / 是否必须给 title 提案。 */
    fun strategyFor(
        draft: NewSessionConfiguration,
        detScore: Int,
        documentAvailable: Boolean
    ): AgentCreationTurnStrategy = AgentCreationTurnStrategy(
        deterministicUnderstanding = detScore,
        rounds = rounds,
        askedCounts = askCounts.mapKeys { (field, _) -> field.label },
        targetFollowUpField = if (shouldConverge()) null else nextField(draft)?.label,
        mustProposeTitle = draft.goal.isNotBlank() && draft.title.isBlank(),
        converge = shouldConverge(),
        documentAvailable = documentAvailable
    )

    /** 记录一轮完成；askedFieldLabel 为本轮实际追问的字段（null = 本轮没追问）。 */
    fun recordRound(askedFieldLabel: String?) {
        rounds++
        val field = fieldByLabel(askedFieldLabel) ?: return
        askCounts[field] = (askCounts[field] ?: 0) + 1
    }

    fun fieldByLabel(label: String?): Field? =
        Field.values().firstOrNull { it.label == label }

    /** 下一个该追问的字段：优先级队列中「未填且未问满 2 次」的第一项。 */
    fun nextField(draft: NewSessionConfiguration): Field? =
        PRIORITY.firstOrNull { !isFilled(it, draft) && (askCounts[it] ?: 0) < MAX_ASKS_PER_FIELD }

    /** 用户明确说「别问了 / 直接生成」→ 立即收敛。 */
    fun forceConverge() {
        converged = true
    }

    /** 收敛条件：用户要求 / 到 8 轮软上限。 */
    fun shouldConverge(): Boolean = converged || rounds >= SOFT_ROUND_CAP

    private fun isFilled(field: Field, draft: NewSessionConfiguration): Boolean = when (field) {
        Field.Goal -> draft.goal.isNotBlank()
        Field.LearnerRole -> draft.learnerRole.isNotBlank()
        Field.Title -> draft.title.isNotBlank()
        Field.TeachingStyle -> AgentCreationUnderstanding.hasTeachingStyle(draft)
        Field.Constraints -> AgentCreationUnderstanding.hasConstraints(draft)
    }

    companion object {
        const val SOFT_ROUND_CAP = 8
        const val MAX_ASKS_PER_FIELD = 2

        private val PRIORITY = listOf(
            Field.Goal,
            Field.LearnerRole,
            Field.Title,
            Field.TeachingStyle,
            Field.Constraints
        )

        private val STOP_ASKING = Regex("别问|直接生成|直接创建|不用再问|就这样吧")

        /** 用户是否明确要求停止追问（立即收敛信号）。 */
        fun isStopAsking(userText: String): Boolean = STOP_ASKING.containsMatchIn(userText)
    }
}
