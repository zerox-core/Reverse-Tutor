package com.reversetutor.feature.chat

import com.reversetutor.core.data.message.MessageQuoteDraft
import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.data.message.MessageAttachmentDraft
import com.reversetutor.core.model.MessageRole

data class ChatUiState(
    val sessionTitle: String,
    val learnerName: String,
    val learnerStatus: String,
    val contextPath: String,
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
            generation: ChatGenerationUiState = ChatGenerationUiState.Idle,
            learnerName: String = "林澈",
            learnerStatus: String = "正在理解函数",
            contextPath: String = "基础语法 / 函数 / 参数与返回值"
        ): ChatUiState =
            ChatUiState(
                sessionTitle = sessionTitle,
                learnerName = learnerName,
                learnerStatus = learnerStatus,
                contextPath = contextPath,
                messages = records
                    .sortedBy { it.message.createdAtEpochMillis }
                    .map { record ->
                        ChatTimelineItem(
                            id = record.message.id,
                            spaceId = record.message.spaceId,
                            role = record.message.role,
                            roleLabel = record.message.role.toChatLabel(learnerName),
                            text = record.message.text,
                            createdAtEpochMillis = record.message.createdAtEpochMillis,
                            attachmentLabels = record.attachments.map { attachment ->
                                attachment.toTimelineLabel()
                            },
                            attachments = record.attachments.map { attachment ->
                                ChatAttachmentUi(
                                    name = attachment.name,
                                    mimeType = attachment.mimeType,
                                    uri = attachment.uri,
                                    sourceId = attachment.sourceId
                                )
                            },
                            quoteLabel = record.quote?.let { "正在回复：${it.excerpt}" }
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
        override val statusLabel: String = "未配置模型"
    }

    object Pending : ChatGenerationUiState {
        override val statusLabel: String = "正在生成回复..."
    }

    data class Failure(val message: String) : ChatGenerationUiState {
        override val statusLabel: String = "生成失败：$message"
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
    val attachments: List<ChatAttachmentUi>,
    val quoteLabel: String?
)

data class ChatAttachmentUi(
    val name: String,
    val mimeType: String?,
    val uri: String?,
    val sourceId: String?
) {
    val isImage: Boolean
        get() = mimeType?.startsWith("image/") == true
}

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
            append("图片：")
            append(name.ifBlank { "附件" })
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
    Quote("引用", false),
    Note("记为随笔", false),
    Regenerate("重新生成", true),
    Delete("删除", false)
}

private fun MessageRole.toChatLabel(learnerName: String): String =
    when (this) {
        MessageRole.User -> "我"
        MessageRole.Assistant -> learnerName
        MessageRole.System -> "学习记录"
        MessageRole.Tool -> "资料"
    }

private fun com.reversetutor.core.model.MessageAttachment.toTimelineLabel(): String =
    if (mimeType?.startsWith("image/") == true) {
        "图片：$name"
    } else {
        "附件：$name"
    }
