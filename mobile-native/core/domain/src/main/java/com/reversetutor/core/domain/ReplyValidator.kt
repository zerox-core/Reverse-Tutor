package com.reversetutor.core.domain

/**
 * Reply validator (回复校验器) — expression-loop slice 3
 * (SPEC: docs/specs/expression-loop-llm-first.md §4.3 / §9 切片 3).
 *
 * Pure, deterministic checks over the reply the expression LLM is streaming
 * (or has produced). Two severity classes:
 *
 * - **Red lines** (红线): the reply must never reach the user. The streaming
 *   watchdog aborts the current stream on the first hit, the turn is retried
 *   once with a strong-constraint directive, and a second hit falls back to
 *   [TEMPLATE_FALLBACK_REPLY].
 * - **Style flags** (风格项): recorded only. They are serialized into the
 *   turn's trajectory and rendered back into the NEXT turn's note as a style
 *   hint — never used to withdraw or replace content already shown.
 *
 * All matchers are deliberately phrase/structure based and conservative:
 * a false red line costs one regeneration; a false style flag only nudges
 * the next note.
 */
object ReplyValidator {

    /** Hard violations; the first hit aborts the stream. */
    enum class RedLine(val wireLabel: String) {
        LeakedPlan("泄露教学策略/计划"),
        LectureTone("讲课腔"),
        ClaimedHuman("自称人类"),
        OffProtocol("协议外内容")
    }

    /** Soft violations; recorded for the next turn's style hint only. */
    enum class StyleFlag(val wireLabel: String, val hint: String) {
        TeacherAddress("「老师」称呼频繁", "别叫「老师」，直接说话"),
        OpenerPattern("总起句模式", "别用「好的/当然」这类总起句开头，直接进内容"),
        ArrowStructure("箭头/分步结构", "别用箭头、编号或分步，像聊天一样说"),
        OverDensity("超密度预算", "上轮话太多了，这轮收短一点"),
        MultiQuestion("问题数超过 1 个", "最多问一个问题")
    }

    data class Verdict(
        val redLine: RedLine? = null,
        val styleFlags: List<StyleFlag> = emptyList()
    ) {
        val hasRedLine: Boolean get() = redLine != null
    }

    /** Last-resort user-visible reply when the retry still hits a red line. */
    const val TEMPLATE_FALLBACK_REPLY =
        "唔……我刚把自己绕进去了。你刚才说的那个点，再跟我讲一遍呗？这次我好好听。"

    /** Preview notice shown while the retry is being generated (SPEC §3.1). */
    const val RETRY_PREVIEW_NOTICE = "小K重新组织了一下语言"

    // --- red-line matchers -------------------------------------------------

    private val LEAKED_PLAN_MARKERS = listOf(
        "教学策略", "教学计划", "本轮计划", "教学契约", "表达契约",
        "系统提示", "内部提示", "本轮便签", "便签", "提示词",
        "TurnNote", "turnNote", "actionType", "sessionPolicy",
        "掌握度", "难度系数", "节奏信号"
    )

    private val LECTURE_TONE_MARKERS = listOf(
        "我来给你讲", "让我来教", "我来教你", "下面我来讲", "我来给你解释",
        "今天我们来学习", "我们来学习", "我们来复习", "正确答案是", "标准答案",
        "标准解法", "解法如下", "这道题应该这样做", "听我给你讲"
    )

    private val CLAIMED_HUMAN_MARKERS = listOf(
        "我是真人", "我是人类", "我是个真人", "我是真实的人",
        "我不是AI", "我不是 AI", "我不是机器人", "不是人工智能"
    )

    private val OFF_PROTOCOL_MARKERS = listOf(
        "\"blocks\"", "\"version\":\"v1\"", "evidenceReferenceIds",
        "\"toolCalls\"", "\"outcome\":{"
    )

    /**
     * The first red line in [text], checked in a stable order. Safe to call
     * on a partially streamed prefix: every matcher only needs the text
     * produced so far.
     */
    fun findFirstRedLine(text: String): RedLine? {
        if (text.isBlank()) return null
        return when {
            LEAKED_PLAN_MARKERS.any { text.contains(it) } -> RedLine.LeakedPlan
            CLAIMED_HUMAN_MARKERS.any { text.contains(it) } -> RedLine.ClaimedHuman
            OFF_PROTOCOL_MARKERS.any { text.contains(it) } -> RedLine.OffProtocol
            LECTURE_TONE_MARKERS.any { text.contains(it) } -> RedLine.LectureTone
            else -> null
        }
    }

    // --- style matchers ----------------------------------------------------

    private val OPENER_MARKERS = listOf("好的", "好哒", "好嘞", "当然", "没问题", "嗯嗯", "嗯，")

    private val NUMBERED_STEP = Regex(
        "(?m)^\\s*(\\d+\\s*[.、)]|第[一二三四五六七八九十]+步|[Ss]tep\\s*\\d+)"
    )

    /** Style flags for a COMPLETE reply; [tier] supplies the density budget. */
    fun styleFlagsFor(text: String, tier: DensityTier): List<StyleFlag> {
        if (text.isBlank()) return emptyList()
        val flags = mutableListOf<StyleFlag>()
        if (countOccurrences(text, "老师") >= 2) flags += StyleFlag.TeacherAddress
        val trimmed = text.trimStart()
        if (OPENER_MARKERS.any { trimmed.startsWith(it) }) flags += StyleFlag.OpenerPattern
        if (text.contains("→") || text.contains("->") || NUMBERED_STEP.containsMatchIn(text) ||
            (text.contains("首先") && text.contains("其次"))
        ) {
            flags += StyleFlag.ArrowStructure
        }
        if (text.length > tier.maxChars) flags += StyleFlag.OverDensity
        val questions = countOccurrences(text, "？") + countOccurrences(text, "?")
        if (questions > tier.questionBudget) flags += StyleFlag.MultiQuestion
        return flags
    }

    /** Full verdict for a complete reply. */
    fun inspect(text: String, tier: DensityTier): Verdict =
        Verdict(redLine = findFirstRedLine(text), styleFlags = styleFlagsFor(text, tier))

    // --- payloads & hints --------------------------------------------------

    /** Stable serialization for the trajectory row (enum names). */
    fun payloadForStyleFlags(flags: List<StyleFlag>): String =
        flags.distinct().joinToString(",") { it.name }

    fun payloadForRedLine(redLine: RedLine?): String = redLine?.name.orEmpty()

    /**
     * Renders a stored style-flag payload back into the one-line style hint
     * for the NEXT turn's note. Blank payload → blank hint.
     */
    fun styleHintForPayload(payload: String?): String {
        if (payload.isNullOrBlank()) return ""
        return payload.split(',')
            .mapNotNull { name -> StyleFlag.values().firstOrNull { it.name == name.trim() } }
            .map { it.hint }
            .distinct()
            .joinToString("；")
            .take(TurnNoteAssembler.STYLE_HINT_MAX)
    }

    /**
     * The strong-constraint directive appended to the prompt tail when a turn
     * is retried after a red-line abort (SPEC §3.1: 重说一次).
     */
    fun retryDirectiveFor(redLine: RedLine): String {
        val specific = when (redLine) {
            RedLine.LeakedPlan -> "不许提任何教学策略、计划、便签、掌握度或内部字段"
            RedLine.LectureTone -> "不许以老师身份讲课，不许给完整解法或标准答案"
            RedLine.ClaimedHuman -> "不许自称人类或否认自己是 AI"
            RedLine.OffProtocol -> "不许输出任何 JSON、协议字段或结构化标记"
        }
        return "重说要求（最高优先级，覆盖上面所有冲突要求）：你上一条回复违反了表达契约（" +
            redLine.wireLabel + "），那条没有发出去。这一次必须用聊天语气重新回应对方的最后一条消息；" +
            specific + "；直接像同学一样说话，别解释发生了什么。"
    }

    private fun countOccurrences(text: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var index = 0
        while (true) {
            index = text.indexOf(needle, index)
            if (index < 0) return count
            count += 1
            index += needle.length
        }
    }
}
