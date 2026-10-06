package com.reversetutor.feature.chat

/**
 * R102 创建链确认闸门（路线A · 2026-10-06 用户拍板；设计 docs/specs/agent-creation-confirmation-gate.md）。
 *
 * 铁规矩：模型每轮回包的 draft 字段只能进 Proposed；只有代码层判定到确认信号才升 Confirmed；
 * 流程推进（出孵化草案）只读 Confirmed 槽位——模型对状态转移没有任何投票权。
 */

/** 信息槽位。label 与 [AgentCreationFollowUpPlanner.Field] 对齐，供追问目标直接映射。 */
enum class CreationSlot(val label: String) {
    Goal("学习目标"),
    LearnerRole("学习者角色"),
    Persona("人物性格"),
    TeachingStyle("教学风格偏好"),
    Constraints("约束条件");

    companion object {
        fun fromLabel(label: String?): CreationSlot? = values().firstOrNull { it.label == label }
    }
}

enum class SlotStatus { Empty, Proposed, Confirmed }

/** 单个槽位：值 + 状态 + 落定轮次。 */
data class SlotEntry(
    val status: SlotStatus = SlotStatus.Empty,
    val value: String = "",
    val round: Int = 0
)

/** 槽位集合（不可变；所有写操作返回新实例，纯函数可单测）。 */
data class CreationSlots(
    val entries: Map<CreationSlot, SlotEntry> = emptyMap()
) {
    fun statusOf(slot: CreationSlot): SlotStatus = entries[slot]?.status ?: SlotStatus.Empty

    fun valueOf(slot: CreationSlot): String = entries[slot]?.value.orEmpty()

    /** 必填槽（Goal + LearnerRole）是否全部 Confirmed。 */
    fun requiredConfirmed(): Boolean =
        statusOf(CreationSlot.Goal) == SlotStatus.Confirmed &&
            statusOf(CreationSlot.LearnerRole) == SlotStatus.Confirmed

    /** 可选槽（人设 / 教学方式 / 约束）是否全部 Confirmed。 */
    fun optionalAllConfirmed(): Boolean =
        OPTIONAL.all { statusOf(it) == SlotStatus.Confirmed }

    /** 模型提案：只能写 Empty/Proposed；Confirmed 槽位模型无权覆盖（返回原样）。 */
    fun propose(slot: CreationSlot, value: String, round: Int): CreationSlots {
        val v = value.trim()
        if (v.isEmpty()) return this
        if (statusOf(slot) == SlotStatus.Confirmed) return this
        return copy(entries = entries + (slot to SlotEntry(SlotStatus.Proposed, v, round)))
    }

    /** 用户确认：value 空时取当前提案值；无任何值时返回原样。 */
    fun confirm(slot: CreationSlot, value: String = "", round: Int = 0): CreationSlots {
        val v = value.trim().ifEmpty { entries[slot]?.value.orEmpty() }
        if (v.isEmpty()) return this
        return copy(entries = entries + (slot to SlotEntry(SlotStatus.Confirmed, v, round)))
    }

    /** 修订打回：槽位清零，重新走「提案 → 确认」。 */
    fun reset(slot: CreationSlot): CreationSlots = copy(entries = entries + (slot to SlotEntry()))

    /** 收敛兜底：桌上所有 Proposed 提案自动升 Confirmed（用户止损，提案即默认值）。 */
    fun autoConfirmProposed(round: Int): CreationSlots = copy(
        entries = entries.mapValues { (_, entry) ->
            if (entry.status == SlotStatus.Proposed) {
                entry.copy(status = SlotStatus.Confirmed, round = round)
            } else {
                entry
            }
        }
    )

    /** 序列化形态：槽位名 → 条目（快照用）。 */
    fun toNamedEntries(): Map<String, SlotEntry> = entries.mapKeys { (slot, _) -> slot.name }

    companion object {
        private val OPTIONAL = listOf(CreationSlot.Persona, CreationSlot.TeachingStyle, CreationSlot.Constraints)

        fun fromNamedEntries(map: Map<String, SlotEntry>): CreationSlots = CreationSlots(
            map.mapNotNull { (name, entry) ->
                runCatching { CreationSlot.valueOf(name) }.getOrNull()?.let { it to entry }
            }.toMap()
        )

        /**
         * 旧快照迁移（R102 前没有槽位状态）：草案里已有的字段一律降为 Proposed——
         * 即使是旧数据也要求用户确认一次，AI 下一轮复述提案请用户表态。
         */
        fun migratedFrom(draft: NewSessionConfiguration): CreationSlots {
            var slots = CreationSlots()
            if (draft.goal.isNotBlank()) slots = slots.propose(CreationSlot.Goal, draft.goal, 0)
            if (draft.learnerRole.isNotBlank()) slots = slots.propose(CreationSlot.LearnerRole, draft.learnerRole, 0)
            if (draft.persona.isNotBlank()) slots = slots.propose(CreationSlot.Persona, draft.persona, 0)
            if (draft.dialogueStrategy.isNotBlank()) {
                slots = slots.propose(CreationSlot.TeachingStyle, draft.dialogueStrategy, 0)
            }
            val constraints = listOf(draft.plan, draft.learningScope, draft.stageMilestones)
                .filter { it.isNotBlank() && it != "未设置" }
                .joinToString("；")
            if (constraints.isNotBlank()) slots = slots.propose(CreationSlot.Constraints, constraints, 0)
            return slots
        }
    }
}

/**
 * R102 确认信号判定（全在代码层，模型无权判定）。
 * 针对「上一轮悬置提案」评估本轮用户文本，按优先级取：直接给值 > 显式确认 > 选项采纳；
 * 非正面回答（反问/疑问）一律拦截，悬置提案不得确认。
 */
object CreationConfirmationSignals {

    /** 整句短肯定（与 R100 INCUBATION_CONFIRM 同源词表，扩「对/是的/就这个」）。 */
    private val CONFIRM_SENTENCE = Regex(
        "^(对|对的|是的|是|嗯|恩|好|好的|好呀|可以|行|就这个|就这样|没错|确认|没问题|ok|okay)[！!。.~～ ，,呀啊]*$",
        RegexOption.IGNORE_CASE
    )

    /** 句首肯定 + 后续内容（「对，就学三阶」）；含转折（但是/不过/可是）不算。 */
    private val CONFIRM_PREFIX = Regex(
        "^(对|对的|是的|嗯|好|好的|可以|行|就这个|没错|没问题|ok|okay)[，,。.！!~～ ]",
        RegexOption.IGNORE_CASE
    )
    private val NEGATION = Regex("但是|不过|可是|然而|还是算")

    private val QUESTION_TAIL = Regex("""[？?]\s*$""")
    private val QUESTION_WORDS = Regex("什么|怎么|怎样|哪个|哪些|为什么|该不该|能不能|要不要|吗|呢|呀")

    fun isExplicitConfirm(text: String): Boolean = CONFIRM_SENTENCE.matches(text.trim())

    fun hasConfirmPrefix(text: String): Boolean {
        val t = text.trim()
        return CONFIRM_PREFIX.containsMatchIn(t) && !NEGATION.containsMatchIn(t)
    }

    /**
     * 非正面回答（事故场景：「我该学什么呀？」）：整句肯定除外；
     * 句尾问号，或短句含疑问词。判定时悬置提案一律不得确认。
     */
    fun isNonAnswer(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        if (isExplicitConfirm(t)) return false
        if (QUESTION_TAIL.containsMatchIn(t)) return true
        return t.length <= 30 && QUESTION_WORDS.containsMatchIn(t)
    }

    /**
     * 选项采纳 / 用户亲口给值：用户文本与提案值有实质重合
     * （一方包含另一方且长度 ≥2，或提案的任一 4 字连续片段命中用户文本）。
     */
    fun overlapsProposal(proposal: String, text: String): Boolean {
        val p = proposal.trim()
        val t = text.trim()
        if (p.isEmpty() || t.isEmpty()) return false
        if (p.length >= 2 && t.contains(p)) return true
        if (t.length >= 2 && p.contains(t)) return true
        val window = 4
        if (p.length >= window) {
            var i = 0
            while (i + window <= p.length) {
                if (t.contains(p.substring(i, i + window))) return true
                i++
            }
        }
        return false
    }

    /** 用户亲口给值判定：模型回包字段值与用户文本实质重合 → 该字段直接 Confirmed。 */
    fun userSourced(value: String, userText: String): Boolean = overlapsProposal(value, userText)
}
