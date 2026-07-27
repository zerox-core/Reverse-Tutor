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
    val generation: ChatGenerationUiState = ChatGenerationUiState.Idle,
    val learnerImageRef: String? = null,
    val avatarVisible: Boolean = true,
    val sources: List<ChatSourceUi> = emptyList(),
    val currentSessionSourceIds: Set<String> = emptySet(),
    val pendingDeletion: PendingChatMessageDeletion? = null,
    val pendingDeletionRetryRequired: Boolean = false
) {
    val generationStatusLabel: String?
        get() = generation.statusLabel
    val learnerAvatarReference: LearnerAvatarReference?
        get() = LearnerAvatarReference.parse(learnerImageRef)
    val reserveAvatarSpace: Boolean
        get() = avatarVisible

    companion object {
        fun from(
            sessionTitle: String,
            records: List<MessageRecord>,
            composer: ChatComposerState,
            generation: ChatGenerationUiState = ChatGenerationUiState.Idle,
            learnerName: String = "林澈",
            learnerStatus: String = "正在理解函数",
            contextPath: String = "基础语法 / 函数 / 参数与返回值",
            sessionSnapshot: NewSessionConfiguration? = null,
            sources: List<ChatSourceUi> = emptyList(),
            currentSessionSourceIds: Set<String> = emptySet(),
            rememberedMessageIds: Set<String> = emptySet(),
            pendingDeletion: PendingChatMessageDeletion? = null,
            pendingDeletionRetryRequired: Boolean = false
        ): ChatUiState {
            val resolvedName = sessionSnapshot?.learnerDisplayName?.takeIf(String::isNotBlank) ?: learnerName
            val resolvedStatus = sessionSnapshot?.learnerRole?.takeIf(String::isNotBlank) ?: learnerStatus
            return ChatUiState(
                sessionTitle = sessionTitle,
                learnerName = resolvedName,
                learnerStatus = resolvedStatus,
                contextPath = contextPath,
                messages = records
                    .sortedBy { it.message.createdAtEpochMillis }
                    .map { record ->
                        ChatTimelineItem(
                            id = record.message.id,
                            spaceId = record.message.spaceId,
                            role = record.message.role,
                            roleLabel = record.message.role.toChatLabel(resolvedName),
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
                            quoteLabel = record.quote?.let { "正在回复：${it.excerpt}" },
                            remembered = record.message.id in rememberedMessageIds
                        )
                    },
                composer = composer,
                generation = generation,
                learnerImageRef = sessionSnapshot?.learnerImageRef,
                avatarVisible = sessionSnapshot?.avatarVisible ?: true,
                sources = sources,
                currentSessionSourceIds = currentSessionSourceIds,
                pendingDeletion = pendingDeletion,
                pendingDeletionRetryRequired = pendingDeletionRetryRequired
            )
        }
    }
}

fun buildChatRouteUiState(
    sessionTitle: String,
    records: List<MessageRecord>,
    composer: ChatComposerState,
    generation: ChatGenerationUiState,
    learnerRoleFallback: String,
    sessionSnapshot: NewSessionConfiguration?,
    sources: List<ChatSourceUi> = emptyList(),
    currentSessionSourceIds: Set<String> = emptySet(),
    rememberedMessageIds: Set<String> = emptySet(),
    pendingDeletion: PendingChatMessageDeletion? = null,
    pendingDeletionRetryRequired: Boolean = false
): ChatUiState = ChatUiState.from(
    sessionTitle = sessionTitle,
    records = records,
    composer = composer,
    generation = generation,
    learnerStatus = learnerRoleFallback,
    sessionSnapshot = sessionSnapshot,
    sources = sources,
    currentSessionSourceIds = currentSessionSourceIds,
    rememberedMessageIds = rememberedMessageIds,
    pendingDeletion = pendingDeletion,
    pendingDeletionRetryRequired = pendingDeletionRetryRequired
)

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
    val quoteLabel: String?,
    val remembered: Boolean = false
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
    val imageDraft: ChatImageDraft? = null,
    val attachments: List<ChatDraftAttachment> = emptyList(),
    val isSending: Boolean = false,
    val sendFailure: String? = null,
    val notice: String? = null
) {
    val orderedAttachments: List<ChatDraftAttachment>
        get() = if (imageDraft == null) {
            attachments
        } else {
            attachments + imageDraft.toChatDraftAttachment()
        }
    val canSend: Boolean
        get() = !isSending &&
            (text.isNotBlank() || orderedAttachments.any { it.readiness == ChatAttachmentReadiness.Ready }) &&
            orderedAttachments.all { it.readiness == ChatAttachmentReadiness.Ready }

    fun toQuoteDraft(): MessageQuoteDraft? {
        if (!canSend) return null
        val target = quoteTarget ?: return null
        return MessageQuoteDraft(
            quotedMessageId = target.messageId,
            excerpt = target.excerpt
        ).normalized()
    }

    fun toAttachmentDrafts(): List<MessageAttachmentDraft> =
        orderedAttachments.map {
            MessageAttachmentDraft(
                name = it.name,
                mimeType = it.mimeType,
                uri = it.uri,
                sourceId = it.sourceId
            )
        }

    fun toPersistentDraft(clientRequestId: String): ChatComposerDraft = ChatComposerDraft(
        clientRequestId = clientRequestId,
        text = text,
        quote = quoteTarget,
        attachments = orderedAttachments
    )

    companion object {
        fun from(draft: ChatComposerDraft): ChatComposerState = ChatComposerState(
            text = draft.text,
            quoteTarget = draft.quote,
            attachments = draft.attachments
        )
    }
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

    fun toChatDraftAttachment(): ChatDraftAttachment = ChatDraftAttachment(
        id = "image-$requestId",
        kind = ChatAttachmentKind.Image,
        name = name,
        mimeType = mimeType,
        uri = uri,
        sourceId = sourceId,
        readiness = ChatAttachmentReadiness.Ready
    )
}

data class ChatQuoteTarget(
    val messageId: String,
    val excerpt: String,
    val sourceIdentity: String = "消息"
)

enum class ChatMessageAction(
    val label: String
) {
    Copy("复制"),
    Quote("引用回复"),
    Remember("记住这条"),
    LocateSource("定位关联资料"),
    Delete("删除消息")
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
