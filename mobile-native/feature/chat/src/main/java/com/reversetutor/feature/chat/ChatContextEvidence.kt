package com.reversetutor.feature.chat

import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.model.MemoryItem
import com.reversetutor.core.model.SourceParserStatus

internal const val MaxChatContextEvidence = 6
private const val MaxEvidenceBodyChars = 900

internal suspend fun buildChatContextEvidence(
    userText: String,
    memoryRepository: MemoryRepository?,
    sourceRepository: SourceRepository?
): List<LlmContextEvidence> {
    val queryTerms = userText.queryTerms()
    val memoryEvidence = runCatching {
        memoryRepository?.snapshot()?.items.orEmpty()
            .mapNotNull { it.toContextEvidence(queryTerms) }
    }.getOrDefault(emptyList())
    val sourceEvidence = runCatching {
        sourceRepository?.listSourcesWithChunks().orEmpty()
            .mapNotNull { it.toContextEvidence(queryTerms) }
    }.getOrDefault(emptyList())
    return (memoryEvidence + sourceEvidence)
        .sortedWith(compareByDescending<LlmContextEvidence> { it.relevanceScore(queryTerms) }.thenBy { it.title })
        .take(MaxChatContextEvidence)
}

private fun MemoryItem.toContextEvidence(queryTerms: Set<String>): LlmContextEvidence? {
    if (!matchesQuery(queryTerms, title, body)) return null
    return LlmContextEvidence(
        id = id,
        title = title,
        body = body.take(MaxEvidenceBodyChars),
        kind = kind.name,
        sourceMessageId = sourceMessageId,
        sourceId = sourceId
    ).normalized()
}

private fun SourceWithChunks.toContextEvidence(queryTerms: Set<String>): LlmContextEvidence? {
    if (source.parserStatus !in setOf(SourceParserStatus.FullyLocal, SourceParserStatus.PartiallyLocal)) return null
    if (chunks.isEmpty()) return null
    val body = chunks
        .filter { matchesQuery(queryTerms, source.title, it.text) }
        .ifEmpty { if (queryTerms.isEmpty()) chunks.take(1) else emptyList() }
        .take(2)
        .joinToString(separator = "\n") { it.text }
        .take(MaxEvidenceBodyChars)
    if (body.isBlank()) return null
    return LlmContextEvidence(
        id = source.id,
        title = source.title,
        body = body,
        kind = "Source",
        sourceId = source.id
    ).normalized()
}

private fun LlmContextEvidence.relevanceScore(queryTerms: Set<String>): Int =
    if (queryTerms.isEmpty()) {
        0
    } else {
        queryTerms.count { term ->
            title.contains(term, ignoreCase = true) || body.contains(term, ignoreCase = true)
        }
    }

private fun matchesQuery(
    queryTerms: Set<String>,
    title: String,
    body: String
): Boolean =
    queryTerms.isEmpty() || queryTerms.any { term ->
        title.contains(term, ignoreCase = true) || body.contains(term, ignoreCase = true)
    }

private fun String.queryTerms(): Set<String> =
    lowercase()
        .split(Regex("[^a-z0-9\\u4e00-\\u9fa5]+"))
        .map { it.trim() }
        .filter { it.length >= 2 }
        .take(12)
        .toSet()
