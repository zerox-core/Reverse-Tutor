package com.reversetutor.preview.shell

import com.reversetutor.core.data.sources.SourceImportResult
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.TutorSession
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.ChatInvalidSourceReselectRequest
import com.reversetutor.feature.chat.SessionSource
import com.reversetutor.feature.chat.SourceReadState
import com.reversetutor.feature.chat.sourceOwnerIds

internal sealed interface SessionSourceImportOutcome {
    data class Usable(val picked: PickedSessionSource) : SessionSourceImportOutcome
    data class Rejected(val message: String, val replacingSourceId: String?) : SessionSourceImportOutcome
}

internal fun prepareInvalidChatSourceReplacement(
    sources: List<SessionSource>,
    request: ChatInvalidSourceReselectRequest,
    nowEpochMillis: Long
): List<SessionSource> {
    if (sources.any { it.id == request.sourceId }) return sources
    return sources + SessionSource(
        id = request.sourceId,
        displayName = request.originalDisplayName,
        managedName = request.originalDisplayName,
        typeLabel = request.originalDisplayName.substringAfterLast('.', "资料").uppercase(),
        readState = SourceReadState.Invalid,
        currentSessionReferenced = true,
        referenceOwnerIds = listOf(request.sessionId),
        lastUsedAtEpochMillis = nowEpochMillis,
        preview = "资料已失效，请重新选择文件。",
        bytesRetained = false
    )
}

internal fun mapSessionSettingsImport(
    result: SourceImportResult,
    currentSessionId: String,
    replacingSourceId: String?,
    lastUsedAtEpochMillis: Long
): SessionSourceImportOutcome {
    val readableContent = !result.source.extractedText.isNullOrBlank() || result.chunks.any { it.text.isNotBlank() }
    val rejection = when {
        result.errors.isNotEmpty() -> result.errors.joinToString("；")
        !result.isUsable -> "当前解析状态为 ${result.source.parserStatus.name}，该文件暂不能用于会话资料。"
        !readableContent -> "文件没有可用内容，未替换当前资料。"
        else -> null
    }
    if (rejection != null) {
        return SessionSourceImportOutcome.Rejected("$rejection 请重试。", replacingSourceId)
    }
    return SessionSourceImportOutcome.Usable(
        PickedSessionSource(
            source = result.toUsableSessionSource(currentSessionId, lastUsedAtEpochMillis),
            replacingSourceId = replacingSourceId
        )
    )
}

internal fun buildSessionSettingsSourceCatalog(
    currentSessionId: String,
    sessions: List<TutorSession>,
    sessionSnapshots: Map<String, NewSessionConfiguration?>,
    favorites: List<NewSessionFavorite>,
    sources: List<SourceWithChunks>,
    lastUsedAt: Map<String, Long>
): List<SessionSource> {
    val liveSnapshots = sessions.associate { session -> session.id to sessionSnapshots[session.id] }
    return sources.map { sourceWithChunks ->
        val source = sourceWithChunks.source
        sourceWithChunks.toCatalogSessionSource(
            currentSessionId = currentSessionId,
            referenceOwnerIds = sourceOwnerIds(source.id, source.title, liveSnapshots, favorites),
            lastUsedAtEpochMillis = lastUsedAt[source.id] ?: source.createdAtEpochMillis
        )
    }
}

private fun SourceWithChunks.toCatalogSessionSource(
    currentSessionId: String,
    referenceOwnerIds: List<String>,
    lastUsedAtEpochMillis: Long
): SessionSource = SessionSource(
    id = source.id,
    displayName = source.title,
    managedName = source.title,
    typeLabel = source.type.name,
    readState = source.parserStatus.toSessionSettingsReadState(),
    currentSessionReferenced = currentSessionId in referenceOwnerIds,
    referenceOwnerIds = referenceOwnerIds.distinct(),
    lastUsedAtEpochMillis = lastUsedAtEpochMillis,
    preview = source.extractedText?.take(1_200)
        ?: chunks.joinToString("\n") { it.text }.take(1_200),
    bytesRetained = true
)

private fun SourceImportResult.toUsableSessionSource(
    currentSessionId: String,
    lastUsedAtEpochMillis: Long
): SessionSource = SessionSource(
    id = source.id,
    displayName = source.title,
    managedName = source.title,
    typeLabel = source.type.name,
    readState = SourceReadState.Ready,
    currentSessionReferenced = true,
    referenceOwnerIds = listOf(currentSessionId),
    lastUsedAtEpochMillis = lastUsedAtEpochMillis,
    preview = source.extractedText?.take(1_200)
        ?: chunks.joinToString("\n") { it.text }.take(1_200),
    bytesRetained = true
)

private fun SourceParserStatus.toSessionSettingsReadState(): SourceReadState = when (this) {
    SourceParserStatus.FullyLocal,
    SourceParserStatus.PartiallyLocal -> SourceReadState.Ready
    SourceParserStatus.FutureAssisted -> SourceReadState.Processing
    SourceParserStatus.Unsupported,
    SourceParserStatus.Failed -> SourceReadState.Invalid
}
