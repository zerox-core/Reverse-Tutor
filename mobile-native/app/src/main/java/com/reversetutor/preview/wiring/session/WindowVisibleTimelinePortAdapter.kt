package com.reversetutor.preview.wiring.session

import com.reversetutor.feature.chat.ChatAttachmentUi
import com.reversetutor.feature.chat.WindowTimelineOrigin
import com.reversetutor.feature.chat.WindowVisibleTimelineEntry
import com.reversetutor.feature.chat.WindowVisibleTimelinePort

/** Maps the app-owned record projection to feature-chat's safe timeline Port. */
class WindowVisibleTimelinePortAdapter(
    private val visibleHistoryReader: WindowVisibleHistoryReader
) : WindowVisibleTimelinePort {
    override suspend fun load(sessionId: String): List<WindowVisibleTimelineEntry> =
        visibleHistoryReader.recordsFor(sessionId).map { visible ->
            val record = visible.record
            WindowVisibleTimelineEntry(
                id = record.message.id,
                spaceId = record.message.spaceId,
                role = record.message.role,
                text = record.message.text,
                createdAtEpochMillis = record.message.createdAtEpochMillis,
                attachmentLabels = record.attachments.map { attachment ->
                    if (attachment.mimeType?.startsWith("image/") == true) "图片：${attachment.name}"
                    else "附件：${attachment.name}"
                },
                attachments = record.attachments.map { attachment ->
                    ChatAttachmentUi(
                        name = attachment.name,
                        mimeType = attachment.mimeType,
                        uri = attachment.uri,
                        sourceId = attachment.sourceId
                    )
                },
                quoteLabel = record.quote?.let { "正在回复：${it.excerpt}" },
                origin = if (visible.inherited) {
                    WindowTimelineOrigin.INHERITED_READ_ONLY
                } else {
                    WindowTimelineOrigin.LOCAL
                }
            )
        }
}
