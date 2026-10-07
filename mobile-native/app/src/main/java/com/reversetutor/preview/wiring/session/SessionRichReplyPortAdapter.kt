package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.agent.AssistantReplyArtifactRepository
import com.reversetutor.core.data.agent.RichDocumentBlock
import com.reversetutor.feature.chat.RichReplyBlockContract
import com.reversetutor.feature.chat.RichReplyEvidenceContract
import com.reversetutor.feature.chat.RichReplyToolResultContract
import com.reversetutor.feature.chat.SessionRichReplyContract
import com.reversetutor.feature.chat.SessionRichReplyPort

/** App-side adapter publishing only the feature's renderer-neutral contract. */
class SessionRichReplyPortAdapter(
    private val artifacts: AssistantReplyArtifactRepository
) : SessionRichReplyPort {
    override suspend fun load(sessionId: String, assistantMessageId: String): SessionRichReplyContract? =
        artifacts.read(sessionId, assistantMessageId)?.let { artifact ->
            SessionRichReplyContract(
                assistantMessageId = artifact.assistantMessageId,
                blocks = artifact.blocks.map(RichDocumentBlock::toContract),
                evidence = artifact.evidenceReferenceIds.map(::RichReplyEvidenceContract),
                toolResults = artifact.toolResultCodes.map(::toolResultContract)
            )
        }
}

private fun RichDocumentBlock.toContract(): RichReplyBlockContract = when (this) {
    is RichDocumentBlock.Heading -> RichReplyBlockContract.Heading(level, text)
    is RichDocumentBlock.Paragraph -> RichReplyBlockContract.Paragraph(text)
    is RichDocumentBlock.BulletList -> RichReplyBlockContract.BulletList(items)
    is RichDocumentBlock.NumberedList -> RichReplyBlockContract.NumberedList(items)
    is RichDocumentBlock.CodeBlock -> RichReplyBlockContract.CodeBlock(language, code)
    is RichDocumentBlock.Callout -> RichReplyBlockContract.Callout(kind, text)
    is RichDocumentBlock.SimpleTable -> RichReplyBlockContract.SimpleTable(columns, rows)
}

private fun toolResultContract(value: String): RichReplyToolResultContract {
    val parts = value.split(':')
    return when (parts.firstOrNull()) {
        "document" -> RichReplyToolResultContract("document", parts.getOrNull(1))
        "table" -> RichReplyToolResultContract("table", parts.getOrNull(1))
        "reference" -> RichReplyToolResultContract("reference", parts.getOrNull(1))
        "rejected" -> RichReplyToolResultContract("none", status = "rejected")
        else -> RichReplyToolResultContract("none", status = "rejected")
    }
}
