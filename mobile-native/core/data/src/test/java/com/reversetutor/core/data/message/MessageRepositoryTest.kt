package com.reversetutor.core.data.message

import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.entity.MessageAttachmentEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {
    @Test
    fun messagesAreListedChronologicallyAndBlankSendIsRejected() = runBlocking {
        val repository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        repository.saveMessage(message(id = "newer", text = "Second", createdAt = 20L))
        repository.saveMessage(message(id = "older", text = "First", createdAt = 10L))

        assertEquals(listOf("older", "newer"), repository.listMessages("session-1").map { it.id })

        val rejected = repository.sendUserMessage(
            sessionId = "session-1",
            text = "   ",
            nowEpochMillis = 30L,
            messageId = "blank"
        )

        assertNull(rejected)
        assertEquals(listOf("older", "newer"), repository.listMessages("session-1").map { it.id })
    }

    @Test
    fun sendUserMessagePersistsQuoteContextAndDeleteRemovesMessage() = runBlocking {
        val repository = MessageRepository(FakeMessageDao(), FakeMessageAttachmentDao(), FakeMessageQuoteDao())
        repository.saveMessage(message(id = "source", text = "Explain factoring", createdAt = 10L))

        val sent = repository.sendUserMessage(
            sessionId = "session-1",
            text = "  Turn this into a quiz  ",
            nowEpochMillis = 20L,
            messageId = "reply",
            quote = MessageQuoteDraft(
                quotedMessageId = "source",
                excerpt = "Explain factoring"
            )
        )

        assertEquals("reply", sent?.id)
        assertEquals("Turn this into a quiz", sent?.text)
        val records = repository.listMessageRecords("session-1")
        assertEquals(listOf("source", "reply"), records.map { it.message.id })
        assertEquals("source", records.last().quote?.quotedMessageId)
        assertEquals("Explain factoring", records.last().quote?.excerpt)

        assertTrue(repository.deleteMessage("reply"))
        assertFalse(repository.deleteMessage("missing"))
        assertEquals(listOf("source"), repository.listMessages("session-1").map { it.id })
    }

    @Test
    fun sendUserMessagePersistsImageAttachmentAndDeleteRemovesAttachment() = runBlocking {
        val attachmentDao = FakeMessageAttachmentDao()
        val repository = MessageRepository(FakeMessageDao(), attachmentDao, FakeMessageQuoteDao())

        val sent = repository.sendUserMessage(
            sessionId = "session-1",
            text = "   ",
            nowEpochMillis = 30L,
            messageId = "image-message",
            attachments = listOf(
                MessageAttachmentDraft(
                    name = "question.png",
                    mimeType = "image/png",
                    uri = "content://images/question.png",
                    sourceId = "source-question"
                )
            )
        )

        assertEquals("image-message", sent?.id)
        assertEquals("", sent?.text)
        val record = repository.listMessageRecords("session-1").single()
        assertEquals("question.png", record.attachments.single().name)
        assertEquals("image/png", record.attachments.single().mimeType)
        assertEquals("content://images/question.png", record.attachments.single().uri)
        assertEquals("source-question", record.attachments.single().sourceId)

        assertTrue(repository.deleteMessage("image-message"))
        assertTrue(attachmentDao.listByMessageId("image-message").isEmpty())
    }

    private fun message(
        id: String,
        text: String,
        createdAt: Long
    ): Message = Message(
        id = id,
        spaceId = "space-1",
        sessionId = "session-1",
        role = MessageRole.User,
        text = text,
        createdAtEpochMillis = createdAt
    )
}

private class FakeMessageDao : MessageDao {
    private val messages = linkedMapOf<String, MessageEntity>()

    override suspend fun insert(message: MessageEntity) {
        messages[message.id] = message
    }

    override suspend fun listBySession(sessionId: String): List<MessageEntity> =
        messages.values
            .filter { it.sessionId == sessionId }
            .sortedBy { it.createdAtEpochMillis }

    override suspend fun deleteById(id: String): Int =
        if (messages.remove(id) == null) 0 else 1
}

private class FakeMessageAttachmentDao : MessageAttachmentDao {
    private val attachments = linkedMapOf<String, MessageAttachmentEntity>()

    override suspend fun insert(attachment: MessageAttachmentEntity) {
        attachments[attachment.id] = attachment
    }

    override suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity> =
        attachments.values
            .filter { it.messageId == messageId }
            .sortedBy { it.name }

    override suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity> =
        attachments.values
            .filter { it.messageId in messageIds }
            .sortedWith(compareBy<MessageAttachmentEntity> { it.messageId }.thenBy { it.name })

    override suspend fun deleteByMessageId(messageId: String): Int {
        val matches = attachments.filterValues { it.messageId == messageId }.keys
        matches.forEach { attachments.remove(it) }
        return matches.size
    }
}

private class FakeMessageQuoteDao : MessageQuoteDao {
    private val quotes = linkedMapOf<String, MessageQuoteEntity>()

    override suspend fun insert(quote: MessageQuoteEntity) {
        quotes[quote.id] = quote
    }

    override suspend fun getByMessageId(messageId: String): MessageQuoteEntity? =
        quotes.values.firstOrNull { it.messageId == messageId }

    override suspend fun deleteByMessageId(messageId: String): Int {
        val matches = quotes.filterValues { it.messageId == messageId }.keys
        matches.forEach { quotes.remove(it) }
        return matches.size
    }
}
