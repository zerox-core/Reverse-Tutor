package com.reversetutor.feature.chat

/** Renderer-neutral replay contract for one assistant reply. */
sealed interface RichReplyBlockContract {
    data class Heading(val level: Int, val text: String) : RichReplyBlockContract
    data class Paragraph(val text: String) : RichReplyBlockContract
    data class BulletList(val items: List<String>) : RichReplyBlockContract
    data class NumberedList(val items: List<String>) : RichReplyBlockContract
    data class CodeBlock(val language: String?, val code: String) : RichReplyBlockContract
    data class Callout(val kind: String, val text: String) : RichReplyBlockContract
    data class SimpleTable(val columns: List<String>, val rows: List<List<String>>) : RichReplyBlockContract
}

data class RichReplyEvidenceContract(val id: String)

data class RichReplyToolResultContract(
    val kind: String,
    val targetId: String? = null,
    val status: String = "completed"
)

data class SessionRichReplyContract(
    val assistantMessageId: String,
    val blocks: List<RichReplyBlockContract>,
    val evidence: List<RichReplyEvidenceContract>,
    val toolResults: List<RichReplyToolResultContract>
)

/** Feature boundary: no DAO, entity, Provider response, URL, or raw tool args. */
fun interface SessionRichReplyPort {
    suspend fun load(sessionId: String, assistantMessageId: String): SessionRichReplyContract?
}
