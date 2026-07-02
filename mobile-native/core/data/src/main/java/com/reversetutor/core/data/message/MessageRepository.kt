package com.reversetutor.core.data.message

import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.core.model.MessageQuote
import com.reversetutor.core.model.MessageRole
import java.util.UUID

class MessageRepository(
    private val messageDao: MessageDao,
    private val messageAttachmentDao: MessageAttachmentDao,
    private val messageQuoteDao: MessageQuoteDao
) {
    suspend fun saveMessage(message: Message) {
        messageDao.insert(message.toEntity())
    }

    suspend fun listMessages(sessionId: String): List<Message> =
        messageDao.listBySession(sessionId).map { it.toDomain() }

    suspend fun listMessageRecords(sessionId: String): List<MessageRecord> {
        val messages = messageDao.listBySession(sessionId)
        val attachmentsByMessageId = if (messages.isEmpty()) {
            emptyMap()
        } else {
            messageAttachmentDao.listByMessageIds(messages.map { it.id })
                .map { it.toDomain() }
                .groupBy { it.messageId }
        }
        return messages.map { entity ->
            MessageRecord(
                message = entity.toDomain(),
                attachments = attachmentsByMessageId[entity.id].orEmpty(),
                quote = messageQuoteDao.getByMessageId(entity.id)?.toDomain()
            )
        }
    }

    suspend fun sendUserMessage(
        sessionId: String,
        text: String,
        nowEpochMillis: Long,
        messageId: String = "message-${UUID.randomUUID()}",
        quote: MessageQuoteDraft? = null,
        attachments: List<MessageAttachmentDraft> = emptyList()
    ): Message? {
        val normalizedText = text.trim()
        val normalizedAttachments = attachments.mapNotNull { it.normalized() }
        if (normalizedText.isEmpty() && normalizedAttachments.isEmpty()) return null

        val spaceId = messageDao.listBySession(sessionId)
            .firstOrNull()
            ?.spaceId
            ?: SessionRepository.defaultSpaceId
        val message = Message(
            id = messageId,
            spaceId = spaceId,
            sessionId = sessionId,
            role = MessageRole.User,
            text = normalizedText,
            createdAtEpochMillis = nowEpochMillis
        )
        messageDao.insert(message.toEntity())
        normalizedAttachments.forEachIndexed { index, attachment ->
            messageAttachmentDao.insert(
                attachment.toAttachment(
                    spaceId = spaceId,
                    messageId = messageId,
                    index = index
                ).toEntity()
            )
        }

        val normalizedQuote = quote?.normalized()
        if (normalizedQuote != null) {
            messageQuoteDao.insert(
                MessageQuote(
                    id = "quote-$messageId",
                    spaceId = spaceId,
                    messageId = messageId,
                    quotedMessageId = normalizedQuote.quotedMessageId,
                    excerpt = normalizedQuote.excerpt
                ).toEntity()
            )
        }

        return message
    }

    suspend fun deleteMessage(id: String): Boolean {
        val deleted = messageDao.deleteById(id) > 0
        if (deleted) {
            messageAttachmentDao.deleteByMessageId(id)
            messageQuoteDao.deleteByMessageId(id)
        }
        return deleted
    }
}

data class MessageRecord(
    val message: Message,
    val attachments: List<MessageAttachment> = emptyList(),
    val quote: MessageQuote?
)

data class MessageAttachmentDraft(
    val name: String,
    val mimeType: String? = null,
    val uri: String? = null,
    val sourceId: String? = null
) {
    fun normalized(): MessageAttachmentDraft? {
        val normalizedName = name.trim()
        val normalizedMimeType = mimeType?.trim()?.ifEmpty { null }
        val normalizedUri = uri?.trim()?.ifEmpty { null }
        val normalizedSourceId = sourceId?.trim()?.ifEmpty { null }
        if (normalizedName.isEmpty() && normalizedUri == null && normalizedSourceId == null) return null
        return copy(
            name = normalizedName.ifEmpty { "Image attachment" },
            mimeType = normalizedMimeType,
            uri = normalizedUri,
            sourceId = normalizedSourceId
        )
    }

    fun toAttachment(
        spaceId: String,
        messageId: String,
        index: Int
    ): MessageAttachment =
        normalized()?.let { normalized ->
            MessageAttachment(
                id = "attachment-$messageId-$index",
                spaceId = spaceId,
                messageId = messageId,
                name = normalized.name,
                mimeType = normalized.mimeType,
                uri = normalized.uri,
                sourceId = normalized.sourceId
            )
        } ?: error("Cannot materialize a blank attachment draft")
}

data class MessageQuoteDraft(
    val quotedMessageId: String,
    val excerpt: String
) {
    fun normalized(): MessageQuoteDraft? {
        val normalizedMessageId = quotedMessageId.trim()
        val normalizedExcerpt = excerpt.trim()
        if (normalizedMessageId.isEmpty() || normalizedExcerpt.isEmpty()) return null
        return copy(
            quotedMessageId = normalizedMessageId,
            excerpt = normalizedExcerpt
        )
    }
}
