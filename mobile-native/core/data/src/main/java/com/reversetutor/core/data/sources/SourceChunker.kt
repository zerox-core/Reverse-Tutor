package com.reversetutor.core.data.sources

/**
 * 资料切片器（V1-019 知识锚点第一期）。
 *
 * 规则：
 * - 先按空行切段落；段落 ≤ [TargetChunkChars] 直接成片。
 * - 超长段落按句子边界（。！？；…：!?;）切句，逐步装进缓冲区，装满一片封一片。
 * - 同一段落内相邻片段带 [OverlapChars] 重叠，保证句子被边界切断时上下文不丢。
 * - 末尾碎片（< [MinTailChunkChars]）并回前一片，避免产生无意义小片。
 * - 单句超长（> [TargetChunkChars]）时按字符硬切，保证任何字符都不丢失。
 */
internal object SourceChunker {

    internal const val TargetChunkChars = 500
    internal const val OverlapChars = 50
    internal const val MinTailChunkChars = 80

    private val ParagraphBoundary = Regex("\n{2,}")
    private val SentenceBoundary = Regex("(?<=[。！？；…：!?;])")

    fun chunk(text: String): List<String> {
        val paragraphs = text
            .split(ParagraphBoundary)
            .map { it.trim('\n') }
            .filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) {
            return listOf(text).filter { it.isNotBlank() }.ifEmpty { listOf(text) }
        }
        return paragraphs.flatMap(::chunkParagraph)
    }

    private fun chunkParagraph(paragraph: String): List<String> {
        if (paragraph.length <= TargetChunkChars) {
            return listOf(paragraph)
        }
        val chunks = mutableListOf<String>()
        val buffer = StringBuilder()
        for (sentence in splitSentences(paragraph)) {
            val pieces = if (sentence.length > TargetChunkChars) {
                sentence.chunked(TargetChunkChars)
            } else {
                listOf(sentence)
            }
            for (piece in pieces) {
                if (buffer.isNotEmpty() && buffer.length + piece.length > TargetChunkChars) {
                    closeChunk(chunks, buffer)
                }
                buffer.append(piece)
            }
        }
        if (buffer.isNotBlank()) {
            closeChunk(chunks, buffer)
        }
        if (chunks.size >= 2 && chunks.last().length < MinTailChunkChars) {
            val tail = chunks.removeAt(chunks.size - 1)
            chunks[chunks.size - 1] = chunks.last() + tail
        }
        return chunks
    }

    private fun closeChunk(chunks: MutableList<String>, buffer: StringBuilder) {
        val chunk = buffer.toString().trim()
        buffer.setLength(0)
        if (chunk.isNotEmpty()) {
            chunks.add(chunk)
            val overlapStart = (chunk.length - OverlapChars).coerceAtLeast(0)
            buffer.append(chunk.substring(overlapStart))
        }
    }

    internal fun splitSentences(text: String): List<String> =
        text.split(SentenceBoundary)
            .map { it.trim('\n', ' ', '\t') }
            .filter { it.isNotEmpty() }
}
