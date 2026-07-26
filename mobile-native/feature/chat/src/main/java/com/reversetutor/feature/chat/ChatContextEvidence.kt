package com.reversetutor.feature.chat

import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.model.MemoryItem
import com.reversetutor.core.model.SourceParserStatus

internal const val MaxChatContextEvidence = 6
private const val MaxEvidenceBodyChars = 900

fun interface ChatSourceUsagePort {
    fun recordSourcesUsed(sessionId: String, sourceIds: Set<String>, usedAtEpochMillis: Long)

    companion object {
        val None = ChatSourceUsagePort { _, _, _ -> }
    }
}

internal suspend fun buildChatContextEvidence(
    userText: String,
    memoryRepository: MemoryRepository?,
    sourceRepository: SourceRepository?,
    sessionSnapshot: NewSessionConfiguration? = null,
    sessionId: String = "current"
): List<LlmContextEvidence> {
    val queryTerms = userText.queryTerms()
    val memoryEvidence = runCatching {
        memoryRepository?.snapshot()?.items.orEmpty()
            .mapNotNull { it.toContextEvidence(queryTerms) }
    }.getOrDefault(emptyList())
    val sourceEvidence = runCatching {
        sourceRepository?.listSourcesWithChunks().orEmpty()
            .filter { source ->
                sessionSnapshot == null || sessionSnapshot.sourceSelections.any {
                    it == source.source.id || it == source.source.title
                }
            }
            .mapNotNull { it.toContextEvidence(queryTerms) }
    }.getOrDefault(emptyList())
    val behaviorEvidence = sessionSnapshot?.toFutureBehaviorEvidence(sessionId)
    val resourceLimit = MaxChatContextEvidence - if (behaviorEvidence == null) 0 else 1
    val resources = (memoryEvidence + sourceEvidence)
        .sortedWith(compareByDescending<LlmContextEvidence> { it.relevanceScore(queryTerms) }.thenBy { it.title })
        .take(resourceLimit)
    return listOfNotNull(behaviorEvidence) + resources
}

internal suspend fun buildGenerationChatContextEvidence(
    userText: String,
    memoryRepository: MemoryRepository?,
    sourceRepository: SourceRepository?,
    sessionSnapshot: NewSessionConfiguration? = null,
    sessionId: String = "current",
    sourceUsagePort: ChatSourceUsagePort = ChatSourceUsagePort.None,
    usedAtEpochMillis: Long
): List<LlmContextEvidence> {
    val evidence = buildChatContextEvidence(
        userText = userText,
        memoryRepository = memoryRepository,
        sourceRepository = sourceRepository,
        sessionSnapshot = sessionSnapshot,
        sessionId = sessionId
    )
    val usedSourceIds = evidence.asSequence()
        .filter { it.kind == "Source" }
        .mapNotNull(LlmContextEvidence::sourceId)
        .toSet()
    if (usedSourceIds.isNotEmpty()) {
        sourceUsagePort.recordSourcesUsed(sessionId, usedSourceIds, usedAtEpochMillis)
    }
    return evidence
}

fun NewSessionConfiguration.toFutureBehaviorEvidence(sessionId: String): LlmContextEvidence =
    requireNotNull(LlmContextEvidence(
        id = "session-settings-$sessionId",
        title = "当前会话行为配置",
        kind = "SessionConfiguration",
        body = buildString {
            append("显示名=").append(learnerDisplayName).append('\n')
            append("角色=").append(learnerRole).append("；人格=").append(learnerProfile).append('\n')
            append("头像显示=").append(avatarVisible).append("；互动习惯=").append(dialogueStrategy).append('\n')
            append("目标=").append(goal).append("；截止=").append(deadline).append('\n')
            append("范围=").append(learningScope).append("；模块=").append(modules).append('\n')
            append("阶段里程碑=").append(stageMilestones).append("；周计划=").append(plan)
                .append("；当前状态=").append(currentState).append('\n')
            append("反馈强度=").append(feedbackIntensity)
                .append("；追问强度=").append(probingIntensity)
                .append("；脚手架强度=").append(scaffoldingIntensity).append('\n')
            append("纠错坚持度=").append(correctionPersistence)
                .append("；复习频率=").append(reviewFrequency)
                .append("；说话语气=").append(speakingTone).append('\n')
            append("世界树=").append(story).append('\n')
            append("自定义栏目=").append(effectiveCustomColumns().joinToString { "${it.name}=${it.content}" }).append('\n')
            append("快捷标签=").append(quickTags.entries.joinToString { entry ->
                "${entry.key}=${entry.value.values.joinToString { it.text }}"
            }).append('\n')
            append("Source IDs=").append(sourceSelections.joinToString())
        }
    ).normalized())

fun sourceOwnerIds(
    sourceId: String,
    managedTitle: String,
    sessionSnapshots: Map<String, NewSessionConfiguration?>,
    favorites: List<NewSessionFavorite>
): List<String> {
    val sessionOwners = sessionSnapshots.mapNotNull { (sessionId, snapshot) ->
        sessionId.takeIf { snapshot?.sourceSelections.orEmpty().any { it == sourceId || it == managedTitle } }
    }
    val favoriteOwners = favorites.mapNotNull { favorite ->
        "favorite:${favorite.id}".takeIf {
            favorite.configuration.sourceSelections.any { it == sourceId || it == managedTitle }
        }
    }
    return sessionOwners + favoriteOwners
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
