package com.reversetutor.core.domain

import kotlin.math.max

/**
 * R88 章节切换卡片策略。
 *
 * 卡片触发是两段式的（用户 2026-09-25 拍板）：AI 学生先在上一轮以学生口吻
 * 提出切换（"老师，我们是不是该学电学了？"），用户认可（"对的，马上进入
 * 电学章节吧"）后才在聊天时间线里插入章节卡片。何时切换由 [LearningPathPolicy]
 * 的确定性路径游走决定，模型只负责表达，不拥有切换权。
 *
 * - [proposalFor]：由本轮 [TurnPlan] 的路径步态产出"待用户确认"提案；
 * - [isTransitionAffirmation]：纯文本确定性判断用户是否认可（不调模型）；
 * - [cardText] / [parseCardText]：卡片 System 消息的编解码。卡片文本是人类
 *   可读的一行字，即使意外漏进 LLM 上下文也无害；解析失败时 UI 回退为普通
 *   系统提示。
 */
object ChapterTransitionPolicy {

    /** 卡片 System 消息的可识别前缀。 */
    const val CARD_PREFIX = "✦ 章节更新｜"

    /** 认可文本上限；更长的大概率是正常教学内容而不是确认。 */
    private const val MAX_AFFIRM_LENGTH = 30

    /** 认可引导词：用户回复以这些词开头（可带标点/后缀）才算确认。 */
    private val AFFIRM_LEADS = setOf(
        "对", "对的", "好的", "好", "嗯", "嗯嗯", "可以", "行", "没问题", "ok", "okay",
        "没错", "正确", "同意", "开始吧", "继续吧", "好呀", "好嘞", "来吧"
    )

    /** 认可后缀里的否定/推迟信号：出现即不算确认（宁可漏卡，不可错卡）。 */
    private val NEGATIVE_HINTS = listOf("不", "别", "暂", "缓", "等", "改", "晚", "下次", "回头", "先学")

    /**
     * 配卡片的路径步态：推进 / 回退 / 完成。Start/Stay 是常规学习，
     * 不打断节奏。
     */
    fun proposableMoves(): Set<PathMove> = setOf(PathMove.Advance, PathMove.Regress, PathMove.Completed)

    /** 本轮计划是否值得一个"待用户确认"的章节提案。 */
    fun proposalFor(plan: TurnPlan, path: LearningPath): ChapterTransitionProposal? {
        val move = plan.pathMove ?: return null
        if (move !in proposableMoves()) return null
        if (plan.pathSize <= 0 || plan.pathSize > LearningPathPolicy.MAX_NODES) return null
        return when (move) {
            PathMove.Advance -> {
                if (plan.pathPosition < 0 || plan.pathPosition >= plan.pathSize) return null
                ChapterTransitionProposal(
                    move = move,
                    fromLabel = path.labelAt(plan.pathPosition - 1),
                    toLabel = path.labelAt(plan.pathPosition).ifEmpty { plan.pathLabel },
                    position = plan.pathPosition,
                    size = plan.pathSize
                ).normalized()
            }
            PathMove.Regress -> {
                if (plan.pathPosition < 0 || plan.pathPosition >= plan.pathSize) return null
                ChapterTransitionProposal(
                    move = move,
                    fromLabel = if (plan.pathPosition + 1 < plan.pathSize) {
                        path.labelAt(plan.pathPosition + 1)
                    } else {
                        ""
                    },
                    toLabel = path.labelAt(plan.pathPosition).ifEmpty { plan.pathLabel },
                    position = plan.pathPosition,
                    size = plan.pathSize
                ).normalized()
            }
            PathMove.Completed -> ChapterTransitionProposal(
                move = move,
                fromLabel = path.labelAt(plan.pathSize - 1),
                toLabel = "",
                position = plan.pathSize - 1,
                size = plan.pathSize
            ).normalized()
            else -> null
        }
    }

    /**
     * 纯文本判断用户是否在认可 AI 学生提出的章节切换（不调模型）。
     *
     * 判定口径（宁可漏判不误判）：长度受限、不含问号、以认可引导词开头、
     * 且引导词之后没有否定/推迟信号。"对的，马上进入电学章节吧" 命中；
     * "电学难吗？" / "先不学电学" / 长段教学讲解都不命中。误判的上界由
     * 调用方保证——只有确实存在待确认提案的那一轮才会走到这里。
     */
    fun isTransitionAffirmation(userText: String): Boolean {
        val text = userText.trim()
        if (text.isEmpty() || text.length > MAX_AFFIRM_LENGTH) return false
        if (text.contains('？') || text.contains('?')) return false
        val firstToken = text.split(Regex("[，。,.!！、\\s]+")).firstOrNull().orEmpty()
        if (firstToken.lowercase() !in AFFIRM_LEADS) return false
        val tail = text.removePrefix(firstToken)
            .trim('，', '。', ',', '.', '!', '！', '、', ' ')
        return NEGATIVE_HINTS.none { hint -> tail.contains(hint) }
    }

    /** 提案 → 卡片文本（System 消息正文）。 */
    fun cardText(proposal: ChapterTransitionProposal): String {
        val p = proposal.normalized()
        return CARD_PREFIX + listOf(
            p.move.name.lowercase(),
            p.fromLabel,
            p.toLabel,
            p.position.toString(),
            p.size.toString()
        ).joinToString("｜")
    }

    /** 卡片文本 → 提案；任何不合规形状都返回 null（UI 回退普通系统提示）。 */
    fun parseCardText(text: String): ChapterTransitionProposal? {
        if (!text.startsWith(CARD_PREFIX)) return null
        val parts = text.removePrefix(CARD_PREFIX).split('｜')
        if (parts.size != 5) return null
        // 编码侧写的是 move.name.lowercase()（如 advance）；枚举 valueOf 大小写
        // 敏感，必须按小写名匹配，不能 valueOf(uppercase)。
        val move = PathMove.values().firstOrNull { it.name.lowercase() == parts[0].trim().lowercase() }
            ?: return null
        if (move !in proposableMoves()) return null
        val position = parts[3].trim().toIntOrNull() ?: return null
        val size = parts[4].trim().toIntOrNull() ?: return null
        if (size <= 0 || size > LearningPathPolicy.MAX_NODES) return null
        if (position < 0 || position >= size) return null
        return ChapterTransitionProposal(
            move = move,
            fromLabel = parts[1],
            toLabel = parts[2],
            position = position,
            size = size
        ).normalized()
    }
}

/**
 * 一次待确认的章节切换提案（R88）。[position] 是目标节点下标（0 起）；
 * Completed 时 position 固定为最后一节，toLabel 留空。
 */
data class ChapterTransitionProposal(
    val move: PathMove = PathMove.Stay,
    val fromLabel: String = "",
    val toLabel: String = "",
    val position: Int = -1,
    val size: Int = 0
) {
    fun normalized(): ChapterTransitionProposal = ChapterTransitionProposal(
        move = move,
        fromLabel = cleanCardLabel(fromLabel),
        toLabel = cleanCardLabel(toLabel),
        position = if (size > 0) position.coerceIn(0, size - 1) else -1,
        size = max(0, size)
    )
}

private fun cleanCardLabel(label: String): String =
    label.replace(Regex("[｜\n\r]"), " ").trim().take(LearningPathPolicy.NODE_LABEL_MAX)