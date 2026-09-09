package com.reversetutor.preview.shell

import com.reversetutor.core.data.sources.SourceImportResult
import com.reversetutor.feature.chat.NewSessionConfiguration

/**
 * NEWMP-V1-006 Task 3: pure projection for the in-chat "从手机选择资料"
 * import flow (AppShell chatSourcePickerActive branch), extracted so JVM
 * tests can pin the session-binding contract without Compose.
 *
 * The mapper never returns local file paths or document bodies — only the
 * bound snapshot (with the new source id appended to the selection) and a
 * short, safe user-facing notice.
 */
internal sealed interface ChatSourcePickImportOutcome {
    data class Bound(
        val snapshot: NewSessionConfiguration,
        val notice: String
    ) : ChatSourcePickImportOutcome

    data class Rejected(val notice: String) : ChatSourcePickImportOutcome
}

internal fun mapChatSourcePickImport(
    imported: SourceImportResult,
    sessionId: String?,
    currentSnapshot: NewSessionConfiguration?
): ChatSourcePickImportOutcome? {
    val readableContent = !imported.source.extractedText.isNullOrBlank() ||
        imported.chunks.any { it.text.isNotBlank() }
    val usable = sessionId != null && imported.isUsable && readableContent
    if (!usable) {
        return ChatSourcePickImportOutcome.Rejected("资料未能解析为可用内容，请换一个文件。")
    }
    val snapshot = currentSnapshot
        ?: return ChatSourcePickImportOutcome.Rejected("资料未能加入当前会话，请重试。")
    return ChatSourcePickImportOutcome.Bound(
        snapshot = snapshot.copy(
            sourceSelections = (snapshot.sourceSelections + imported.source.id).distinct()
        ),
        notice = "资料已加入本会话，将用于后续回复。"
    )
}
