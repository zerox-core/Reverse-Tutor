package com.reversetutor.feature.chat

import com.reversetutor.core.data.message.MessageAttachmentDraft
import com.reversetutor.core.data.message.MessageQuoteDraft
import com.reversetutor.core.data.message.MessageRecord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException

enum class ChatAttachmentKind {
    Image,
    Source,
    Camera
}

sealed interface ChatAttachmentReadiness {
    val isFailure: Boolean
        get() = this is Failed

    object Preparing : ChatAttachmentReadiness
    object Ready : ChatAttachmentReadiness
    data class Failed(val message: String, val retryable: Boolean) : ChatAttachmentReadiness
}

data class ChatDraftAttachment(
    val id: String,
    val kind: ChatAttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val uri: String? = null,
    val sourceId: String? = null,
    val sizeBytes: Long? = null,
    val readiness: ChatAttachmentReadiness = ChatAttachmentReadiness.Preparing
) {
    val displayType: String
        get() = when (kind) {
            ChatAttachmentKind.Image -> "图片"
            ChatAttachmentKind.Source -> "资料"
            ChatAttachmentKind.Camera -> "拍照"
        }
}

data class ChatComposerDraft(
    val clientRequestId: String = "chat-${UUID.randomUUID()}",
    val text: String = "",
    val quote: ChatQuoteTarget? = null,
    val attachments: List<ChatDraftAttachment> = emptyList()
) {
    val canSend: Boolean
        get() {
            val hasContent = text.isNotBlank() || attachments.any {
                it.readiness == ChatAttachmentReadiness.Ready
            }
            return hasContent && attachments.all {
                it.readiness == ChatAttachmentReadiness.Ready
            }
        }
}

enum class ChatAttachmentRejection {
    TooMany,
    ImageTooLarge
}

sealed interface ChatAttachmentMutation {
    val draft: ChatComposerDraft

    data class Accepted(override val draft: ChatComposerDraft) : ChatAttachmentMutation
    data class Rejected(
        override val draft: ChatComposerDraft,
        val reason: ChatAttachmentRejection
    ) : ChatAttachmentMutation
}

object ChatAttachmentPolicy {
    const val MaxAttachments = 9
    const val MaxImageBytes = 20L * 1024L * 1024L

    fun add(
        draft: ChatComposerDraft,
        attachment: ChatDraftAttachment
    ): ChatAttachmentMutation {
        if (draft.attachments.size >= MaxAttachments) {
            return ChatAttachmentMutation.Rejected(draft, ChatAttachmentRejection.TooMany)
        }
        if (attachment.kind in setOf(ChatAttachmentKind.Image, ChatAttachmentKind.Camera) &&
            attachment.sizeBytes != null && attachment.sizeBytes > MaxImageBytes
        ) {
            return ChatAttachmentMutation.Rejected(draft, ChatAttachmentRejection.ImageTooLarge)
        }
        return ChatAttachmentMutation.Accepted(
            draft.copy(attachments = draft.attachments + attachment)
        )
    }

    fun remove(draft: ChatComposerDraft, attachmentId: String): ChatComposerDraft =
        draft.copy(attachments = draft.attachments.filterNot { it.id == attachmentId })

    fun move(draft: ChatComposerDraft, fromIndex: Int, toIndex: Int): ChatComposerDraft {
        if (fromIndex !in draft.attachments.indices || toIndex !in draft.attachments.indices || fromIndex == toIndex) {
            return draft
        }
        val reordered = draft.attachments.toMutableList()
        val item = reordered.removeAt(fromIndex)
        reordered.add(toIndex, item)
        return draft.copy(attachments = reordered)
    }

    fun retry(draft: ChatComposerDraft, attachmentId: String): ChatComposerDraft =
        draft.updateAttachment(attachmentId) { it.copy(readiness = ChatAttachmentReadiness.Preparing) }

    fun markReady(draft: ChatComposerDraft, attachmentId: String): ChatComposerDraft =
        draft.updateAttachment(attachmentId) { it.copy(readiness = ChatAttachmentReadiness.Ready) }

    fun markFailed(
        draft: ChatComposerDraft,
        attachmentId: String,
        message: String,
        retryable: Boolean = true
    ): ChatComposerDraft = draft.updateAttachment(attachmentId) {
        it.copy(readiness = ChatAttachmentReadiness.Failed(message, retryable))
    }

    private fun ChatComposerDraft.updateAttachment(
        attachmentId: String,
        transform: (ChatDraftAttachment) -> ChatDraftAttachment
    ): ChatComposerDraft = copy(
        attachments = attachments.map { if (it.id == attachmentId) transform(it) else it }
    )
}

object ChatDraftCodec {
    private const val Version = 2
    private const val MaxStringBytes = 2 * 1024 * 1024

    fun encode(draft: ChatComposerDraft): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(Version)
            data.writeString(draft.clientRequestId)
            data.writeString(draft.text)
            data.writeNullable(draft.quote?.messageId)
            data.writeNullable(draft.quote?.excerpt)
            data.writeNullable(draft.quote?.sourceIdentity)
            data.writeInt(draft.attachments.size)
            draft.attachments.forEach { attachment ->
                data.writeString(attachment.id)
                data.writeString(attachment.kind.name)
                data.writeString(attachment.name)
                data.writeNullable(attachment.mimeType)
                data.writeNullable(attachment.uri)
                data.writeNullable(attachment.sourceId)
                data.writeLong(attachment.sizeBytes ?: -1L)
                when (val readiness = attachment.readiness) {
                    ChatAttachmentReadiness.Preparing -> data.writeByte(0)
                    ChatAttachmentReadiness.Ready -> data.writeByte(1)
                    is ChatAttachmentReadiness.Failed -> {
                        data.writeByte(2)
                        data.writeString(readiness.message)
                        data.writeBoolean(readiness.retryable)
                    }
                }
            }
        }
        return output.toByteArray().toHexString()
    }

    fun decode(encoded: String): ChatComposerDraft? = runCatching {
        val bytes = encoded.hexToByteArray()
        DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            val version = data.readInt()
            require(version in 1..Version)
            val requestId = data.readString()
            val text = data.readString()
            val quoteMessageId = data.readNullable()
            val quoteExcerpt = data.readNullable()
            val quoteSourceIdentity = if (version >= 2) data.readNullable() else null
            val count = data.readInt()
            require(count in 0..ChatAttachmentPolicy.MaxAttachments)
            val attachments = List(count) {
                val id = data.readString()
                val kind = ChatAttachmentKind.valueOf(data.readString())
                val name = data.readString()
                val mimeType = data.readNullable()
                val uri = data.readNullable()
                val sourceId = data.readNullable()
                val size = data.readLong().takeIf { it >= 0L }
                val readiness = when (data.readByte().toInt()) {
                    0 -> ChatAttachmentReadiness.Failed(
                        message = "附件准备在应用重启后中断，请重试。",
                        retryable = true
                    )
                    1 -> ChatAttachmentReadiness.Ready
                    2 -> ChatAttachmentReadiness.Failed(data.readString(), data.readBoolean())
                    else -> error("Unknown attachment readiness")
                }
                ChatDraftAttachment(id, kind, name, mimeType, uri, sourceId, size, readiness)
            }
            ChatComposerDraft(
                clientRequestId = requestId,
                text = text,
                quote = if (quoteMessageId != null && quoteExcerpt != null) {
                    ChatQuoteTarget(quoteMessageId, quoteExcerpt, quoteSourceIdentity ?: "消息")
                } else {
                    null
                },
                attachments = attachments
            )
        }
    }.getOrNull()

    private fun DataOutputStream.writeNullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullable(): String? =
        if (readBoolean()) readString() else null

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MaxStringBytes)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MaxStringBytes)
        return ByteArray(size).also(::readFully).toString(Charsets.UTF_8)
    }

    private fun ByteArray.toHexString(): String {
        val digits = "0123456789abcdef"
        return buildString(size * 2) {
            this@toHexString.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(digits[value ushr 4])
                append(digits[value and 0x0f])
            }
        }
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0)
        return ByteArray(length / 2) { index ->
            val high = this[index * 2].digitToInt(16)
            val low = this[index * 2 + 1].digitToInt(16)
            ((high shl 4) or low).toByte()
        }
    }
}

interface ChatDraftStore {
    fun load(sessionId: String): ChatComposerDraft?
    fun save(sessionId: String, draft: ChatComposerDraft)
    fun clear(sessionId: String)

    object None : ChatDraftStore {
        override fun load(sessionId: String): ChatComposerDraft? = null
        override fun save(sessionId: String, draft: ChatComposerDraft) = Unit
        override fun clear(sessionId: String) = Unit
    }
}

fun interface ChatSendPort {
    suspend fun submit(draft: ChatComposerDraft): ChatSendSubmission
}

data class ChatRepositorySendRequest(
    val sessionId: String,
    val messageId: String,
    val text: String,
    val quote: MessageQuoteDraft?,
    val attachments: List<MessageAttachmentDraft>,
    val nowEpochMillis: Long
)

fun interface ChatRepositoryMessageSubmitter {
    suspend fun submit(request: ChatRepositorySendRequest): Boolean
}

class ChatRepositorySendAdapter(
    private val sessionId: String,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val submitter: ChatRepositoryMessageSubmitter
) : ChatSendPort {
    override suspend fun submit(draft: ChatComposerDraft): ChatSendSubmission {
        val request = ChatRepositorySendRequest(
            sessionId = sessionId,
            messageId = draft.clientRequestId,
            text = draft.text,
            quote = draft.quote?.let {
                MessageQuoteDraft(it.messageId, it.excerpt).normalized()
            },
            attachments = draft.attachments.map {
                MessageAttachmentDraft(
                    name = it.name,
                    mimeType = it.mimeType,
                    uri = it.uri,
                    sourceId = it.sourceId
                )
            },
            nowEpochMillis = nowEpochMillis()
        )
        val submitted = try {
            submitter.submit(request)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
        return if (submitted) {
            ChatSendSubmission.Success(request.messageId)
        } else {
            ChatSendSubmission.Failure("发送失败，请重试。", retryable = true)
        }
    }
}

sealed interface ChatSendSubmission {
    data class Success(val messageId: String) : ChatSendSubmission
    data class Failure(val message: String, val retryable: Boolean) : ChatSendSubmission
}

sealed interface ChatSendAttempt {
    data class Sent(val messageId: String) : ChatSendAttempt
    data class Failed(val message: String, val retryable: Boolean) : ChatSendAttempt
    object DuplicateBlocked : ChatSendAttempt
}

class ChatSendCoordinator(
    private val draftStore: ChatDraftStore,
    private val attachmentOrderStore: ChatAttachmentOrderStore = ChatAttachmentOrderStore.None,
    private val sendPort: ChatSendPort
) {
    constructor(draftStore: ChatDraftStore, sendPort: ChatSendPort) : this(
        draftStore,
        ChatAttachmentOrderStore.None,
        sendPort
    )

    private val activeSessions = mutableSetOf<String>()

    suspend fun send(sessionId: String, draft: ChatComposerDraft): ChatSendAttempt {
        if (!draft.canSend) return ChatSendAttempt.Failed("消息尚未准备好", retryable = false)
        val acquired = synchronized(activeSessions) { activeSessions.add(sessionId) }
        if (!acquired) return ChatSendAttempt.DuplicateBlocked
        draftStore.save(sessionId, draft)
        return try {
            when (val result = sendPort.submit(draft)) {
                is ChatSendSubmission.Success -> {
                    if (draft.attachments.isNotEmpty()) {
                        runCatching {
                            attachmentOrderStore.save(
                                result.messageId,
                                draft.attachments.indices.map { index ->
                                    "attachment-${result.messageId}-$index"
                                }
                            )
                        }
                    }
                    draftStore.clear(sessionId)
                    ChatSendAttempt.Sent(result.messageId)
                }
                is ChatSendSubmission.Failure -> {
                    draftStore.save(sessionId, draft)
                    ChatSendAttempt.Failed(result.message, result.retryable)
                }
            }
        } finally {
            synchronized(activeSessions) { activeSessions.remove(sessionId) }
        }
    }
}

interface ChatAttachmentOrderStore {
    fun load(messageId: String): List<String>?
    fun save(messageId: String, attachmentIds: List<String>)

    object None : ChatAttachmentOrderStore {
        override fun load(messageId: String): List<String>? = null
        override fun save(messageId: String, attachmentIds: List<String>) = Unit
    }
}

fun applyStoredAttachmentOrder(
    records: List<MessageRecord>,
    orderStore: ChatAttachmentOrderStore
): List<MessageRecord> = records.map { record ->
    if (record.attachments.size < 2) return@map record
    val storedOrder = orderStore.load(record.message.id)
    val storedIndex = storedOrder.orEmpty().withIndex().associate { it.value to it.index }
    val fallbackBase = storedOrder?.size ?: 0
    val ordered = record.attachments.sortedWith(
        compareBy(
            { attachment ->
                storedIndex[attachment.id]?.toLong()
                    ?: fallbackBase.toLong() + attachment.fallbackOrderIndex().toLong()
            },
            { attachment -> attachment.id },
            { attachment -> attachment.name }
        )
    )
    record.copy(attachments = ordered)
}

private fun com.reversetutor.core.model.MessageAttachment.fallbackOrderIndex(): Int =
    id.substringAfterLast('-', missingDelimiterValue = "")
        .toIntOrNull()
        ?: Int.MAX_VALUE

enum class ChatPermissionState {
    Requestable,
    Denied,
    PermanentlyDenied,
    Granted
}

enum class ChatPermissionAction {
    RequestPermission,
    OpenSettings,
    LaunchCamera,
    None
}

object ChatCameraPermissionMachine {
    fun afterResult(granted: Boolean, mayAskAgain: Boolean): ChatPermissionState = when {
        granted -> ChatPermissionState.Granted
        mayAskAgain -> ChatPermissionState.Denied
        else -> ChatPermissionState.PermanentlyDenied
    }

    fun actionFor(state: ChatPermissionState): ChatPermissionAction = when (state) {
        ChatPermissionState.Requestable,
        ChatPermissionState.Denied -> ChatPermissionAction.RequestPermission
        ChatPermissionState.PermanentlyDenied -> ChatPermissionAction.OpenSettings
        ChatPermissionState.Granted -> ChatPermissionAction.LaunchCamera
    }
}
