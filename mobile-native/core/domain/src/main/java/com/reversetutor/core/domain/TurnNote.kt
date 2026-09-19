package com.reversetutor.core.domain

/**
 * Turn note (本轮便签) — expression-loop slice 2
 * (SPEC: docs/specs/expression-loop-llm-first.md §4.2 / §9 切片 2).
 *
 * A small, deterministic briefing the decision layer hands to the expression
 * LLM each turn: how dense the reply may be, what to focus on, and a one-line
 * learning status. It is produced by [TurnNoteAssembler] as a pure function of
 * bounded inputs — no I/O, no clock, no LLM — so the same turn always yields
 * the same note, and the note is audit-friendly (every density change lists
 * the pacing signals that caused it).
 *
 * The note travels to the provider as a pre-rendered prompt block
 * ([TurnNote.render]); downstream layers only position it at the tail of the
 * prompt per the cache-friendly layout (SPEC §4.8).
 */

/**
 * Reply density tier. The numeric budgets ride on the enum so they can never
 * disagree with the tier label.
 */
enum class DensityTier(
    val wireLabel: String,
    val maxChars: Int,
    val maxNewConcepts: Int,
    val questionBudget: Int
) {
    Low("低", 80, 0, 0),
    Medium("中", 160, 1, 1),
    High("高", 300, 2, 1)
}

/**
 * The deterministic per-turn note. Budget fields are derived from
 * [densityTier]; optional lines are blank-safe and omitted from [render].
 */
data class TurnNote(
    /** One-line "知识点 + 掌握度 + 上次卡点"; blank when nothing is known. */
    val learningStatus: String = "",
    /** The single knowledge point this turn should stay on; blank if unknown. */
    val suggestedFocus: String = "",
    val densityTier: DensityTier = DensityTier.Medium,
    /** Human-readable reasons for the tier decision, kept for audit. */
    val paceSignals: List<String> = emptyList(),
    /** Reserved for the slice-3 style reviewer; blank until wired. */
    val styleHint: String = ""
) {
    val maxChars: Int get() = densityTier.maxChars
    val maxNewConcepts: Int get() = densityTier.maxNewConcepts
    val questionBudget: Int get() = densityTier.questionBudget

    fun normalized(): TurnNote = copy(
        learningStatus = GuidedLearningContracts.boundText(
            learningStatus, TurnNoteAssembler.LEARNING_STATUS_MAX
        ),
        suggestedFocus = GuidedLearningContracts.boundText(
            suggestedFocus, GuidedLearningContracts.CONCEPT_KEY_MAX
        ),
        paceSignals = GuidedLearningContracts.boundedDistinctText(
            paceSignals, TurnNoteAssembler.PACE_SIGNALS_MAX, TurnNoteAssembler.PACE_SIGNAL_LEN
        ),
        styleHint = GuidedLearningContracts.boundText(styleHint, TurnNoteAssembler.STYLE_HINT_MAX)
    )

    /** Pre-rendered prompt block; optional lines are omitted when blank. */
    fun render(): String {
        val note = normalized()
        return buildString {
            append("本轮便签（内部节奏提示，别念出来、别解释）：")
            if (note.learningStatus.isNotBlank()) {
                append("\n- 学习状态：").append(note.learningStatus)
            }
            if (note.suggestedFocus.isNotBlank()) {
                append("\n- 建议聚焦：").append(note.suggestedFocus)
            }
            append("\n- 信息密度：").append(note.densityTier.wireLabel)
                .append("（回复 ≤").append(note.maxChars).append(" 字")
                .append("、最多 ").append(note.maxNewConcepts).append(" 个新概念")
                .append("、最多 ").append(note.questionBudget).append(" 个问题）")
            if (note.densityTier == DensityTier.Low) {
                append("；这一轮可以只是听，不推进也算好回复")
            }
            if (note.paceSignals.isNotEmpty()) {
                append("\n- 节奏信号：").append(note.paceSignals.joinToString("；"))
            }
            if (note.styleHint.isNotBlank()) {
                append("\n- 风格提示：").append(note.styleHint)
            }
        }
    }
}

/**
 * Bounded inputs to [TurnNoteAssembler.assemble].
 *
 * [recentUserTexts] are the previous user messages, newest first, EXCLUDING
 * the current [userText]; the assembler re-derives the per-message density
 * tier of the recent past from them (the same pure rule, minus the
 * high-streak cap) so no persistence is needed to detect a high-tier streak.
 */
data class TurnNoteInput(
    val userText: String = "",
    val recentUserTexts: List<String> = emptyList(),
    val turnPlan: TurnPlan? = null,
    val knowledgePoint: String = "",
    /** Mastery score 0..100 for the current concept, when the ledger has one. */
    val masteryScore: Float? = null,
    /** Most recent historical-error description, when one exists. */
    val lastStuckPoint: String = "",
    val userEmotion: String = UserEmotionWire.NEUTRAL
)

/**
 * Pure assembler for [TurnNote]. Rule order (first match wins, then caps):
 * 1. 情绪低落（情绪词 / 策略层情绪标记）→ 低档；
 * 2. 主动要求展开（展开/讲解/举例/考试类词）→ 高档；
 * 3. 消息很短（≤[SHORT_MESSAGE_CHARS] 字）→ 低档；
 * 4. 否则 → 中档；
 * 5. 高档封顶：此前连续 [HIGH_STREAK_LIMIT] 轮都是高档 → 回落中档。
 */
object TurnNoteAssembler {

    const val SHORT_MESSAGE_CHARS = 15
    const val HIGH_STREAK_LIMIT = 3
    const val LEARNING_STATUS_MAX = 160
    const val PACE_SIGNALS_MAX = 4
    const val PACE_SIGNAL_LEN = 24
    const val STYLE_HINT_MAX = 80

    /** 主动要求展开/深讲的信号词：升高档。 */
    private val EXPAND_KEYWORDS = listOf(
        "展开", "详细", "细讲", "讲解", "讲讲", "多说", "多讲", "考考我", "考试", "举个例子", "举例"
    )

    /** 情绪低落的信号词：直接低档。 */
    private val EMOTION_KEYWORDS = listOf(
        "烦", "累", "不想学", "焦虑", "崩", "难受", "没心情", "摆烂", "沮丧"
    )

    private val LOW_EMOTIONS = setOf(
        UserEmotionWire.TIRED, UserEmotionWire.FRUSTRATED, UserEmotionWire.REFUSING
    )

    fun assemble(input: TurnNoteInput): TurnNote {
        val userText = input.userText.trim()
        val signals = mutableListOf<String>()

        val emotionHit = EMOTION_KEYWORDS.any { userText.contains(it) } ||
            input.userEmotion.trim().lowercase() in LOW_EMOTIONS
        val expandHit = EXPAND_KEYWORDS.any { userText.contains(it) }

        var tier = when {
            emotionHit -> DensityTier.Low.also { signals += "情绪低落" }
            expandHit -> DensityTier.High.also { signals += "主动要求展开" }
            userText.isNotEmpty() && userText.length <= SHORT_MESSAGE_CHARS ->
                DensityTier.Low.also { signals += "消息很短" }
            else -> DensityTier.Medium
        }

        if (tier == DensityTier.High) {
            val streak = input.recentUserTexts.asSequence()
                .take(HIGH_STREAK_LIMIT)
                .map { tierForText(it) }
                .takeWhile { it == DensityTier.High }
                .count()
            if (streak >= HIGH_STREAK_LIMIT) {
                tier = DensityTier.Medium
                signals += "连续高档回落"
            }
        }

        return TurnNote(
            learningStatus = buildLearningStatus(input),
            suggestedFocus = buildSuggestedFocus(input),
            densityTier = tier,
            paceSignals = signals
        ).normalized()
    }

    /**
     * The per-message density tier of ONE user text, without the streak cap.
     * Used to reconstruct recent pacing history for streak detection.
     */
    fun tierForText(text: String): DensityTier {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return DensityTier.Medium
        if (EMOTION_KEYWORDS.any { trimmed.contains(it) }) return DensityTier.Low
        if (EXPAND_KEYWORDS.any { trimmed.contains(it) }) return DensityTier.High
        if (trimmed.length <= SHORT_MESSAGE_CHARS) return DensityTier.Low
        return DensityTier.Medium
    }

    private fun buildLearningStatus(input: TurnNoteInput): String {
        val knowledgePoint = input.knowledgePoint.trim()
            .ifEmpty { input.turnPlan?.normalized()?.conceptKey.orEmpty() }
            .takeIf { it.isNotBlank() && it != GuidedLearningContracts.UNKNOWN_CONCEPT }
            ?: return ""
        val status = StringBuilder("「").append(knowledgePoint).append("」")
        input.masteryScore?.let { score ->
            status.append("掌握约 ").append(score.coerceIn(0f, 100f).toInt()).append("%")
        }
        val stuck = input.lastStuckPoint.trim()
        if (stuck.isNotEmpty()) {
            status.append("，上次卡在：").append(stuck.take(60))
        }
        return status.toString()
    }

    private fun buildSuggestedFocus(input: TurnNoteInput): String {
        val conceptKey = input.turnPlan?.normalized()?.conceptKey.orEmpty()
        return conceptKey.takeIf { it.isNotBlank() && it != GuidedLearningContracts.UNKNOWN_CONCEPT }
            ?: input.knowledgePoint.trim()
    }
}
