package com.reversetutor.core.data.agent

/**
 * Small, deterministic codec for validated user-visible content. It deliberately
 * has no field for Provider output, retrieval body, URL, secret, or tool args.
 */
internal object AgentPayloadCodec {
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
