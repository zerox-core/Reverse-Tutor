package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.domain.MessageContextPort

/** Uses the same fork-bound projection as the visible chat timeline. */
class TopologyAwareMessageContextPort(
    private val visibleHistoryReader: WindowVisibleHistoryReader
) : MessageContextPort {
    override suspend fun listRecentMessages(
        spaceId: String,
        sessionId: String,
        limit: Int
    ): List<ContextMessage> =
        visibleHistoryReader.recordsFor(sessionId)
            .filter { it.record.message.spaceId == spaceId }
            .takeLast(limit.coerceAtLeast(0))
            .map { record ->
                ContextMessage(
                    messageId = record.record.message.id,
                    role = record.record.message.role.name.lowercase(),
                    text = record.record.message.text,
                    timestampEpochMillis = record.record.message.createdAtEpochMillis
                )
            }
}
