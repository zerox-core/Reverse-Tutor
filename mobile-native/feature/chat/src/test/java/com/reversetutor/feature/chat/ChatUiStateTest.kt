package com.reversetutor.feature.chat

import com.reversetutor.core.data.message.MessageQuoteDraft
import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.core.model.MessageQuote
import com.reversetutor.core.model.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatUiStateTest {
    @Test
    fun timelineItemsRenderRolesAndQuoteContext() {
        val state = ChatUiState.from(
            sessionTitle = "Algebra",
            records = listOf(
                MessageRecord(
                    message = message(
                        id = "assistant-1",
                        role = MessageRole.Assistant,
                        text = "Factor x^2 - 4",
                        createdAt = 10L
                    ),
                    quote = null
                ),
                MessageRecord(
                    message = message(
                        id = "user-1",
                        role = MessageRole.User,
                        text = "Make it harder",
                        createdAt = 20L
                    ),
                    attachments = listOf(
                        MessageAttachment(
                            id = "attachment-user-1-0",
                            spaceId = "space-1",
                            messageId = "user-1",
                            name = "question.png",
                            mimeType = "image/png",
                            uri = "content://images/question.png",
                            sourceId = "source-question"
                        )
                    ),
                    quote = MessageQuote(
                        id = "quote-user-1",
                        spaceId = "space-1",
                        messageId = "user-1",
                        quotedMessageId = "assistant-1",
                        excerpt = "Factor x^2 - 4"
                    )
                )
            ),
            composer = ChatComposerState(text = "")
        )

        assertEquals("Algebra", state.sessionTitle)
        assertEquals("林澈", state.learnerName)
        assertEquals("正在理解函数", state.learnerStatus)
        assertEquals("基础语法 / 函数 / 参数与返回值", state.contextPath)
        assertEquals(listOf("林澈", "我"), state.messages.map { it.roleLabel })
        assertEquals("正在回复：Factor x^2 - 4", state.messages.last().quoteLabel)
        assertEquals(listOf("图片：question.png"), state.messages.last().attachmentLabels)
        assertEquals("content://images/question.png", state.messages.last().attachments.single().uri)
        assertTrue(state.messages.last().attachments.single().isImage)
        assertEquals(listOf("space-1", "space-1"), state.messages.map { it.spaceId })
    }

    @Test
    fun composerRejectsBlankTextAndBuildsQuoteDraft() {
        val blank = ChatComposerState(
            text = "   ",
            quoteTarget = ChatQuoteTarget(
                messageId = "assistant-1",
                excerpt = "A long explanation"
            )
        )

        assertFalse(blank.canSend)
        assertNull(blank.toQuoteDraft())

        val ready = blank.copy(text = "  turn into quiz  ")

        assertTrue(ready.canSend)
        assertEquals(
            MessageQuoteDraft(
                quotedMessageId = "assistant-1",
                excerpt = "A long explanation"
            ),
            ready.toQuoteDraft()
        )

        val imageOnly = ChatComposerState(
            text = "   ",
            imageDraft = ChatImageDraft(
                requestId = 1L,
                name = "question.png",
                mimeType = "image/png",
                uri = "content://images/question.png",
                sourceId = "source-question"
            )
        )

        assertTrue(imageOnly.canSend)
        assertEquals("question.png", imageOnly.toAttachmentDrafts().single().name)
    }

    @Test
    fun actionModelMatchesTask3BLongPressContract() {
        assertEquals(
            listOf("复制", "引用回复", "记住这条", "定位关联资料", "删除消息"),
            ChatMessageAction.entries.map { it.label }
        )
    }

    @Test
    fun generationStateSummarizesNoModelPendingAndFailureStates() {
        assertEquals("未配置模型", ChatGenerationUiState.NoModel.statusLabel)
        assertEquals("正在生成回复...", ChatGenerationUiState.Pending.statusLabel)
        assertEquals(
            "生成失败：Timeout",
            ChatGenerationUiState.Failure("Timeout").statusLabel
        )

        val state = ChatUiState.from(
            sessionTitle = "Algebra",
            records = emptyList(),
            composer = ChatComposerState(text = ""),
            generation = ChatGenerationUiState.NoModel
        )

        assertEquals(ChatGenerationUiState.NoModel, state.generation)
        assertEquals("未配置模型", state.generationStatusLabel)
    }

    @Test
    fun productionUiStateConsumesCurrentSessionLearnerIdentityAndAvatarLayout() {
        val snapshot = NewSessionConfiguration(
            learnerRole = "会追问的学生",
            learnerDisplayName = "小概",
            learnerImageRef = "content://avatar/current",
            avatarVisible = false
        )

        val state = buildChatRouteUiState(
            sessionTitle = "概率论",
            records = listOf(MessageRecord(message("assistant-1", MessageRole.Assistant, "为什么？", 1L), quote = null)),
            composer = ChatComposerState(""),
            generation = ChatGenerationUiState.Idle,
            learnerRoleFallback = "fallback",
            sessionSnapshot = snapshot
        )

        assertEquals("小概", state.learnerName)
        assertEquals("content://avatar/current", state.learnerImageRef)
        assertFalse(state.avatarVisible)
        assertFalse(state.reserveAvatarSpace)
        assertEquals("小概", state.messages.single().roleLabel)
    }

    @Test
    fun avatarReferenceParserAcceptsContentUrisAndRejectsBareResourceNumbers() {
        val contentState = buildChatRouteUiState(
            sessionTitle = "会话",
            records = emptyList(),
            composer = ChatComposerState(""),
            generation = ChatGenerationUiState.Idle,
            learnerRoleFallback = "fallback",
            sessionSnapshot = NewSessionConfiguration(learnerImageRef = "content://avatar/current")
        )
        val bareNumberState = contentState.copy(learnerImageRef = "2131230890")

        assertEquals(
            LearnerAvatarReference.ContentUri("content://avatar/current"),
            contentState.learnerAvatarReference
        )
        assertNull(bareNumberState.learnerAvatarReference)
    }

    private fun message(
        id: String,
        role: MessageRole,
        text: String,
        createdAt: Long
    ): Message = Message(
        id = id,
        spaceId = "space-1",
        sessionId = "session-1",
        role = role,
        text = text,
        createdAtEpochMillis = createdAt
    )
}
