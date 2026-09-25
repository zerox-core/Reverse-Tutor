package com.reversetutor.preview.wiring.session

import android.content.Context
import com.reversetutor.core.domain.ChapterTransitionProposal
import com.reversetutor.core.domain.PathMove

/**
 * R88：章节切换提案的会话级一次性暂存。
 *
 * 上一轮 AI 学生提出切换时存入提案，下一轮用户回复时消费（无论是否认可
 * 都清空——卡片是一次性仪式，不是持久状态）。
 */
interface ChapterTransitionProposalStore {
    fun load(sessionId: String): ChapterTransitionProposal?
    fun save(sessionId: String, proposal: ChapterTransitionProposal)
    fun clear(sessionId: String)
}

object NoOpChapterTransitionProposalStore : ChapterTransitionProposalStore {
    override fun load(sessionId: String): ChapterTransitionProposal? = null
    override fun save(sessionId: String, proposal: ChapterTransitionProposal) = Unit
    override fun clear(sessionId: String) = Unit
}

/**
 * SharedPreferences 实现：一行文本存提案，字段用 U+001F 分隔；
 * 解析失败视为无提案（卡片是装饰性的，坏了就静默降级）。
 */
class SharedPreferencesChapterTransitionProposalStore(context: Context) : ChapterTransitionProposalStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(sessionId: String): ChapterTransitionProposal? {
        val row = prefs.getString(key(sessionId), null) ?: return null
        val fields = row.split(SEP)
        if (fields.size != 5) return null
        val move = runCatching { PathMove.valueOf(fields[0]) }.getOrNull() ?: return null
        val position = fields[3].toIntOrNull() ?: return null
        val size = fields[4].toIntOrNull() ?: return null
        return ChapterTransitionProposal(
            move = move,
            fromLabel = fields[1],
            toLabel = fields[2],
            position = position,
            size = size
        ).normalized()
    }

    override fun save(sessionId: String, proposal: ChapterTransitionProposal) {
        val p = proposal.normalized()
        prefs.edit()
            .putString(
                key(sessionId),
                listOf(p.move.name, p.fromLabel, p.toLabel, p.position.toString(), p.size.toString())
                    .joinToString(SEP)
            )
            .apply()
    }

    override fun clear(sessionId: String) {
        prefs.edit().remove(key(sessionId)).apply()
    }

    private fun key(sessionId: String) = "chapter_transition_$sessionId"

    private companion object {
        const val PREFS_NAME = "chapter_transition_proposals"
        const val SEP = ""
    }
}