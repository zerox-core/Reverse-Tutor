package com.reversetutor.feature.chat

import com.reversetutor.core.data.message.MessageQuoteDraft
import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.data.message.MessageAttachmentDraft
import com.reversetutor.core.model.MessageRole

data class ChatUiState(
    val sessionTitle: String,
    val messages: List<ChatTimelineItem>,
    val composer: ChatComposerState,
    val generation: ChatGenerationUiState = ChatGenerationUiState.Idle
) {
    val generationStatusLabel: String?
        get() = generation.statusLabel

    companion object {
        fun from(
            sessionTitle: String,
            records: List<MessageRecord>,
            composer: ChatComposerState,
            generation: ChatGenerationUiState = ChatGenerationUiState.Idle
        ): ChatUiState =
            ChatUiState(
                sessionTitle = sessionTitle,
                messages = records
                    .sortedBy { it.message.createdAtEpochMillis }
                    .map { record ->
                        ChatTimelineItem(
                            id = record.message.id,
                            spaceId = record.message.spaceId,
                            role = record.message.role,
                            roleLabel = record.message.role.toChatLabel(),
                            text = record.message.text,
                            createdAtEpochMillis = record.message.createdAtEpochMillis,
                            attachmentLabels = record.attachments.map { attachment ->
                                attachment.toTimelineLabel()
                            },
                            quoteLabel = record.quote?.let { "Replying to: ${it.excerpt}" }
                        )
                    },
                composer = composer,
                generation = generation
            )
    }
}

sealed interface ChatGenerationUiState {
    val statusLabel: String?

    object Idle : ChatGenerationUiState {
        override val statusLabel: String? = null
    }

    object NoModel : ChatGenerationUiState {
        override val statusLabel: String = "No model configured"
    }

    object Pending : ChatGenerationUiState {
        override val statusLabel: String = "Generating reply..."
    }

    data class Failure(val message: String) : ChatGenerationUiState {
        override val statusLabel: String = "Provider failed: $message"
    }
}

data class ChatTimelineItem(
    val id: String,
    val spaceId: String,
    val role: MessageRole,
    val roleLabel: String,
    val text: String,
    val createdAtEpochMillis: Long,
    val attachmentLabels: List<String>,
    val quoteLabel: String?
)

data class ChatComposerState(
    val text: String,
    val quoteTarget: ChatQuoteTarget? = null,
    val imageDraft: ChatImageDraft? = null
) {
    val canSend: Boolean
        get() = text.trim().isNotEmpty() || imageDraft != null

    fun toQuoteDraft(): MessageQuoteDraft? {
        if (!canSend) return null
        val target = quoteTarget ?: return null
        return MessageQuoteDraft(
            quotedMessageId = target.messageId,
            excerpt = target.excerpt
        ).normalized()
    }

    fun toAttachmentDrafts(): List<MessageAttachmentDraft> =
        imageDraft?.toAttachmentDraft()?.let(::listOf).orEmpty()
}

data class ChatImageDraft(
    val requestId: Long,
    val name: String,
    val mimeType: String? = null,
    val uri: String? = null,
    val sourceId: String? = null
) {
    val displayLabel: String
        get() = buildString {
            append("Image: ")
            append(name.ifBlank { "attachment" })
            if (!mimeType.isNullOrBlank()) {
                append(" (")
                append(mimeType)
                append(")")
            }
        }

    fun toAttachmentDraft(): MessageAttachmentDraft =
        MessageAttachmentDraft(
            name = name,
            mimeType = mimeType,
            uri = uri,
            sourceId = sourceId
        )
}

data class ChatQuoteTarget(
    val messageId: String,
    val excerpt: String
)

enum class ChatMessageAction(
    val label: String,
    val deferred: Boolean
) {
    Quote("Quote", false),
    Note("Note", false),
    Regenerate("Regenerate", true),
    Delete("Delete", false)
}

private fun MessageRole.toChatLabel(): String =
    when (this) {
        MessageRole.User -> "You"
        MessageRole.Assistant -> "Assistant"
        MessageRole.System -> "System"
        MessageRole.Tool -> "Tool"
    }

private fun com.reversetutor.core.model.MessageAttachment.toTimelineLabel(): String =
    if (mimeType?.startsWith("image/") == true) {
        "Image: $name"
    } else {
        "Attachment: $name"
    }
