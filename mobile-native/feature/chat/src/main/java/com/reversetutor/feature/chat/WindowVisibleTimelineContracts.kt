package com.reversetutor.feature.chat

import com.reversetutor.core.model.MessageRole

/**
 * Read-only timeline seam for a topology-aware chat host. The feature module
 * receives display-safe values only; it never receives a Repository, DAO, or
 * Room entity.
 */
fun interface WindowVisibleTimelinePort {
    suspend fun load(sessionId: String): List<WindowVisibleTimelineEntry>
}

data class WindowVisibleTimelineEntry(
    val id: String,
    val spaceId: String,
    val role: MessageRole,
    val text: String,
    val createdAtEpochMillis: Long,
    val attachmentLabels: List<String>,
    val attachments: List<ChatAttachmentUi>,
    val quoteLabel: String?,
    val origin: WindowTimelineOrigin
)

enum class WindowTimelineOrigin {
    LOCAL,
    INHERITED_READ_ONLY
}
