package com.reversetutor.core.data.agent

import com.reversetutor.core.llm.LlmSourceCheckRule
import com.reversetutor.core.llm.LlmSourceGroundedCheckPlan

/**
 * Small, deterministic codec for validated user-visible content. It deliberately
 * has no field for Provider output, retrieval body, URL, secret, or tool args.
 */
internal object AgentPayloadCodec {
    fun encodeCheckPlan(plan: LlmSourceGroundedCheckPlan): String = buildString {
        val normalized = plan.normalized() ?: return ""
        append(normalized.id.escape()).append('\t')
            .append(normalized.sourceRevision.escape()).append('\t')
            .append(encodeList(normalized.sourceReferenceIds)).append('\t')
            .append(normalized.prompt.escape()).append('\t')
            .append(normalized.expectedAnswer.escape()).append('\t')
            .append(normalized.conceptKey.escape()).append('\t')
        append(encodeList(normalized.sourceRevisions.map { (handle, revision) -> "$handle=$revision" })).append('\t')
        when (val rule = normalized.rule) {

            is LlmSourceCheckRule.ExactText -> append("exact_text\t").append(rule.normalizedAnswer.escape())
            is LlmSourceCheckRule.NumericTolerance -> append("numeric_tolerance\t").append(rule.expected).append(',').append(rule.tolerance)
            is LlmSourceCheckRule.RequiredConcepts -> append("required_concepts\t").append(encodeList(rule.terms))
            is LlmSourceCheckRule.Rubric -> append("rubric\t").append(encodeList(rule.criteria))
        }
    }

    fun decodeCheckPlan(payload: String): LlmSourceGroundedCheckPlan? = runCatching {
        val parts = payload.split('\t')
        if (parts.size < 8) return null
        val rule = when (parts[6]) {
            "exact_text" -> LlmSourceCheckRule.ExactText(parts[7].unescape())
            "numeric_tolerance" -> parts[7].split(',').let { LlmSourceCheckRule.NumericTolerance(it[0].toDouble(), it[1].toDouble()) }
            "required_concepts" -> LlmSourceCheckRule.RequiredConcepts(decodeList(parts[7]))
            "rubric" -> LlmSourceCheckRule.Rubric(decodeList(parts[7]))
            else -> return null
        }
        val revisions = parts.getOrNull(8)?.let { token ->
            decodeList(token).mapNotNull { entry ->
                val split = entry.indexOf('=')
                if (split <= 0 || split == entry.length - 1) return null
                entry.substring(0, split) to entry.substring(split + 1)
            }.toMap()
        } ?: emptyMap()
        LlmSourceGroundedCheckPlan(
            id = parts[0].unescape(), sourceRevision = parts[1].unescape(), sourceReferenceIds = decodeList(parts[2]),
            sourceRevisions = revisions,
            prompt = parts[3].unescape(), expectedAnswer = parts[4].unescape(), conceptKey = parts[5].unescape(), rule = rule
        ).normalized()

    }.getOrNull()
    fun encodeBlocks(blocks: List<RichDocumentBlock>): String = blocks.joinToString("\n") { block ->
        when (block) {
            is RichDocumentBlock.Heading -> "heading\t${block.level}\t${block.text.escape()}"
            is RichDocumentBlock.Paragraph -> "paragraph\t${block.text.escape()}"
            is RichDocumentBlock.BulletList -> "bullet_list\t${encodeList(block.items)}"
            is RichDocumentBlock.NumberedList -> "numbered_list\t${encodeList(block.items)}"
            is RichDocumentBlock.CodeBlock -> "code\t${block.language.orEmpty().escape()}\t${block.code.escape()}"
            is RichDocumentBlock.Callout -> "callout\t${block.kind.escape()}\t${block.text.escape()}"
            is RichDocumentBlock.SimpleTable -> "table\t${encodeList(block.columns)}\t${block.rows.joinToString(";") { encodeList(it) }.escape()}"
        }
    }

    fun decodeBlocks(payload: String): List<RichDocumentBlock> = payload.lineSequence().mapNotNull { line ->
        val parts = line.split('\t')
        when (parts.firstOrNull()) {
            "heading" -> parts.getOrNull(1)?.toIntOrNull()?.let { level -> parts.getOrNull(2)?.unescape()?.let { RichDocumentBlock.Heading(level, it) } }
            "paragraph" -> parts.getOrNull(1)?.unescape()?.let(RichDocumentBlock::Paragraph)
            "bullet_list" -> parts.getOrNull(1)?.let { RichDocumentBlock.BulletList(decodeList(it)) }
            "numbered_list" -> parts.getOrNull(1)?.let { RichDocumentBlock.NumberedList(decodeList(it)) }
            "code" -> parts.getOrNull(2)?.unescape()?.let { RichDocumentBlock.CodeBlock(parts.getOrNull(1)?.unescape()?.ifEmpty { null }, it) }
            "callout" -> parts.getOrNull(2)?.unescape()?.let { RichDocumentBlock.Callout(parts.getOrNull(1)?.unescape().orEmpty(), it) }
            else -> null
        }
    }.take(24).toList()

    fun encodeList(values: List<String>): String = values.take(24).joinToString("|") { it.escape() }
    fun decodeList(payload: String): List<String> = if (payload.isBlank()) emptyList() else payload.split('|').map { it.unescape() }.filter { it.isNotBlank() }.take(24)

    fun decodeBlock(kind: String, payload: String): RichDocumentBlock? = when (kind) {
        "paragraph" -> RichDocumentBlock.Paragraph(payload)
        "heading" -> RichDocumentBlock.Heading(2, payload)
        "code" -> RichDocumentBlock.CodeBlock(null, payload)
        else -> null
    }

    private fun String.escape(): String = replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("|", "\\p")
    private fun String.unescape(): String = buildString {
        var escaping = false
        this@unescape.forEach { char ->
            if (escaping) {
                append(when (char) { 't' -> '\t'; 'n' -> '\n'; 'p' -> '|'; else -> char })
                escaping = false
            } else if (char == '\\') escaping = true else append(char)
        }
        if (escaping) append('\\')
    }
}
