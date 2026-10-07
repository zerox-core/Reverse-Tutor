package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.llm.SessionSummaryOutcome
import com.reversetutor.core.domain.SessionDigestContextPort
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole

/**
 * NEWMP-V1-017: immutable stored summary snapshot for one session.
 *
 * [summarizedUntilMessageId] is the id of the last chat message covered by
 * [summaryText]; everything strictly after it is still replayed verbatim.
 * [summarizedCount] mirrors the legacy engine metadata: the number of
 * messages compressed into this record.
 */
data class SessionSummaryRecord(
    val summaryText: String,
    val summarizedUntilMessageId: String,
    val summarizedCount: Int
)

/**
 * NEWMP-V1-017: persistence for session summaries. Extends
 * [SessionDigestContextPort] so one store both persists the digest and feeds
 * it into the conversation-context read model.
 */
interface SessionSummaryStore : SessionDigestContextPort {
    fun load(sessionId: String): SessionSummaryRecord?
    fun save(sessionId: String, record: SessionSummaryRecord)
    fun clear(sessionId: String)
}

/**
 * NEWMP-V1-017: old-parity early-history compressor (legacy engine
 * `maybe_summarize`).
 *
 * Trigger semantics match the legacy engine: once the number of user +
 * assistant messages AFTER the last summary cutoff reaches
 * [SummaryThreshold], everything except the most recent [KeepRecent]
 * messages is compressed into a 6-12 bullet digest. The previous digest is
 * merged into the prompt ("此前已有摘要，请整合、不要丢失老要点") so old
 * points survive re-summarization.
 *
 * Failures never throw and never destroy the previous record — the next
 * turn retries.
 */
class SessionSummarizer(
    private val generateSummary: suspend (sessionId: String, promptText: String) -> SessionSummaryOutcome,
    private val listMessages: suspend (sessionId: String) -> List<Message>,
    private val store: SessionSummaryStore
) {
    companion object {
        /** Legacy engine SUMMARY_THRESHOLD: chat messages before compression. */
        const val SummaryThreshold = 30

        /** Legacy engine SUMMARY_KEEP_RECENT: messages kept verbatim. */
        const val KeepRecent = 12

        /** Hard cap on the stored digest size. */
        const val MaxSummaryChars = 4000

        /** Per-message excerpt cap inside the summarizer prompt. */
        const val PerMessageExcerptChars = 800

        /** Legacy engine SUMMARY_SYSTEM, adapted to direct bullet output. */
        val SummaryInstruction = """
            你是一个教学对话摘要器。请把以下反转家教对话压缩为 6-12 条要点，严格保留以下信息：
              1. 用户表现出的强项、弱项知识点
              2. 用户中途提出的偏好 / 诉求 / 约定
              3. 用户反复的错误模式或迷思
              4. 用户的情绪趋势（是否曾拒绝 / 被说服过）
              5. 当前正在讨论的主题
            直接输出指向用户的要点列表（markdown bullet），不要输出 JSON，不要添加额外说明。
        """.trimIndent()
    }

    /** Runs the threshold check + compression. True when a new digest was stored. */
    suspend fun maybeSummarize(sessionId: String): Boolean = try {
        summarizeInternal(sessionId)
    } catch (_: Exception) {
        // Old parity: summary problems never break the turn.
        false
    }

    private suspend fun summarizeInternal(sessionId: String): Boolean {
        val chatMessages = listMessages(sessionId)
            .filter { it.role == MessageRole.User || it.role == MessageRole.Assistant }
        if (chatMessages.isEmpty()) return false

        val previous = store.load(sessionId)
        val cutoffIndex = chatMessages.indexOfFirst { it.id == previous?.summarizedUntilMessageId }
        // A missing cutoff message (deleted or branched history) falls back
        // to compressing the whole chat prefix again, merging the old digest.
        val startIndex = if (previous != null && cutoffIndex >= 0) cutoffIndex + 1 else 0
        val unsummarized = chatMessages.subList(startIndex, chatMessages.size)
        if (unsummarized.size < SummaryThreshold) return false
        if (unsummarized.size <= KeepRecent) return false

        val compressCount = unsummarized.size - KeepRecent
        val toCompress = unsummarized.take(compressCount)
        val conversation = toCompress.joinToString(separator = "\n") { message ->
            val speaker = if (message.role == MessageRole.User) "user" else "assistant"
            speaker + ": " + message.text.take(PerMessageExcerptChars)
        }
        val prompt = buildString {
            append(SummaryInstruction)
            append("\n\n")
            if (previous != null && previous.summaryText.isNotBlank()) {
                append("此前已有摘要（请整合、不要丢失老要点）：\n")
                append(previous.summaryText.take(MaxSummaryChars))
                append("\n\n新增对话：\n")
            } else {
                append("对话记录：\n")
            }
            append(conversation)
        }

        return when (val outcome = generateSummary(sessionId, prompt)) {
            is SessionSummaryOutcome.Generated -> {
                store.save(
                    sessionId,
                    SessionSummaryRecord(
                        summaryText = outcome.summaryText.take(MaxSummaryChars),
                        summarizedUntilMessageId = toCompress.last().id,
                        summarizedCount = toCompress.size
                    )
                )
                true
            }
            else -> false
        }
    }
}
