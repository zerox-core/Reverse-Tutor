package com.reversetutor.feature.chat

import com.reversetutor.core.model.MessageRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

const val ChatMessageUndoWindowMillis: Long = 5_000L

data class PendingChatMessageDeletion(
    val sessionId: String,
    val messageId: String,
    val requestedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long
)

interface ChatPendingDeletionStore {
    fun load(sessionId: String): PendingChatMessageDeletion?
    fun loadAll(): List<PendingChatMessageDeletion>
    fun save(pending: PendingChatMessageDeletion)
    fun clear(sessionId: String)

    object None : ChatPendingDeletionStore {
        override fun load(sessionId: String): PendingChatMessageDeletion? = null
        override fun loadAll(): List<PendingChatMessageDeletion> = emptyList()
        override fun save(pending: PendingChatMessageDeletion) = Unit
        override fun clear(sessionId: String) = Unit
    }
}

fun interface ChatMessageDeletePort {
    suspend fun delete(pending: PendingChatMessageDeletion): ChatMessageDeleteResult

    companion object {
        val Unavailable = ChatMessageDeletePort { ChatMessageDeleteResult.RetryableFailure }
    }
}

enum class ChatMessageDeleteResult {
    Success,
    AlreadyAbsent,
    RetryableFailure
}

enum class ChatUndoDeletionResult {
    Undone,
    Expired,
    NothingPending
}

enum class ChatFinalizeDeletionResult {
    Success,
    AlreadyAbsent,
    RetryableFailure,
    NotDue,
    NothingPending
}

sealed interface ChatDeletionRequestResult {
    data class Accepted(val pending: PendingChatMessageDeletion) : ChatDeletionRequestResult
    data class AlreadyPending(val pending: PendingChatMessageDeletion) : ChatDeletionRequestResult
}

class ChatMessageDeletionCoordinator(
    private val store: ChatPendingDeletionStore,
    private val deletePort: ChatMessageDeletePort,
    private val undoWindowMillis: Long = ChatMessageUndoWindowMillis
) {
    fun request(
        sessionId: String,
        messageId: String,
        nowEpochMillis: Long
    ): ChatDeletionRequestResult {
        val current = store.loadAll().minByOrNull { it.requestedAtEpochMillis }
        if (current != null) {
            return if (current.sessionId == sessionId && current.messageId == messageId) {
                ChatDeletionRequestResult.Accepted(current)
            } else {
                ChatDeletionRequestResult.AlreadyPending(current)
            }
        }
        val pending = PendingChatMessageDeletion(
            sessionId = sessionId,
            messageId = messageId,
            requestedAtEpochMillis = nowEpochMillis,
            expiresAtEpochMillis = nowEpochMillis + undoWindowMillis
        )
        store.save(pending)
        return ChatDeletionRequestResult.Accepted(pending)
    }

    fun undo(sessionId: String, nowEpochMillis: Long): ChatUndoDeletionResult {
        val pending = store.load(sessionId) ?: return ChatUndoDeletionResult.NothingPending
        if (nowEpochMillis >= pending.expiresAtEpochMillis) return ChatUndoDeletionResult.Expired
        store.clear(sessionId)
        return ChatUndoDeletionResult.Undone
    }

    suspend fun finalize(sessionId: String, nowEpochMillis: Long): ChatFinalizeDeletionResult {
        val pending = store.load(sessionId) ?: return ChatFinalizeDeletionResult.NothingPending
        if (nowEpochMillis < pending.expiresAtEpochMillis) return ChatFinalizeDeletionResult.NotDue
        return try {
            when (deletePort.delete(pending)) {
                ChatMessageDeleteResult.Success -> {
                    store.clear(sessionId)
                    ChatFinalizeDeletionResult.Success
                }
                ChatMessageDeleteResult.AlreadyAbsent -> {
                    store.clear(sessionId)
                    ChatFinalizeDeletionResult.AlreadyAbsent
                }
                ChatMessageDeleteResult.RetryableFailure -> ChatFinalizeDeletionResult.RetryableFailure
            }
        } catch (_: Exception) {
            ChatFinalizeDeletionResult.RetryableFailure
        }
    }
}

data class ChatPendingSweepResult(
    val nextSweepAtEpochMillis: Long? = null,
    val failedMessageIds: List<String> = emptyList()
)

class ChatPendingDeletionSweeper(
    private val store: ChatPendingDeletionStore,
    private val deletePort: ChatMessageDeletePort
) {
    suspend fun sweep(nowEpochMillis: Long): ChatPendingSweepResult {
        val pending = store.loadAll().sortedBy { it.expiresAtEpochMillis }
        val failed = mutableListOf<String>()
        pending.filter { nowEpochMillis >= it.expiresAtEpochMillis }.forEach { item ->
            val result = ChatMessageDeletionCoordinator(store, deletePort)
                .finalize(item.sessionId, nowEpochMillis)
            if (result == ChatFinalizeDeletionResult.RetryableFailure) failed += item.messageId
        }
        val next = store.loadAll()
            .filter { it.messageId !in failed && it.expiresAtEpochMillis > nowEpochMillis }
            .minOfOrNull { it.expiresAtEpochMillis }
        return ChatPendingSweepResult(next, failed)
    }
}

enum class ChatDerivativeKind {
    Memory,
    Graph
}

data class ChatDerivative(
    val id: String,
    val label: String,
    val kind: ChatDerivativeKind
)

data class ChatDeleteImpact(
    val memories: List<ChatDerivative> = emptyList(),
    val graphItems: List<ChatDerivative> = emptyList(),
    val supportsAtomicDerivativeDeleteAndRestore: Boolean = false,
    val supportsRetainedDerivativeProvenanceMarker: Boolean = false
) {
    val hasDerivatives: Boolean
        get() = memories.isNotEmpty() || graphItems.isNotEmpty()
    val canDeleteDerivatives: Boolean
        get() = hasDerivatives && supportsAtomicDerivativeDeleteAndRestore
    val canDeleteMessageOnly: Boolean
        get() = !hasDerivatives || supportsRetainedDerivativeProvenanceMarker
    val deletionBoundary: String
        get() = when {
            !hasDerivatives -> "这条消息没有关联派生内容。"
            supportsRetainedDerivativeProvenanceMarker -> "关联内容将保留并标记原消息已删除。"
            else -> "后端未提供派生内容溯源标记，当前不能安全删除这条消息。"
        }
}

sealed interface ChatMemoryCategoryCapability {
    data object Supported : ChatMemoryCategoryCapability
    data class Unavailable(val reason: String) : ChatMemoryCategoryCapability
}

enum class ChatMemoryCategory(
    val label: String,
    val capability: ChatMemoryCategoryCapability
) {
    Identity("身份", ChatMemoryCategoryCapability.Unavailable("当前仓库没有身份记忆的精确写入能力。")),
    Fact("事实", ChatMemoryCategoryCapability.Unavailable("当前仓库没有事实记忆的精确写入能力。")),
    Preference("偏好", ChatMemoryCategoryCapability.Unavailable("当前仓库没有偏好记忆的精确写入能力。")),
    Goal("目标", ChatMemoryCategoryCapability.Unavailable("当前仓库没有目标记忆的精确写入能力。")),
    Plan("计划", ChatMemoryCategoryCapability.Unavailable("当前仓库没有计划记忆的精确写入能力。")),
    Constraint("约束", ChatMemoryCategoryCapability.Supported),
    FollowUp("待跟进", ChatMemoryCategoryCapability.Unavailable("当前仓库没有待跟进记忆的精确写入能力。"))
}

data class ChatMemoryDraft(
    val messageId: String,
    val spaceId: String,
    val text: String,
    val category: ChatMemoryCategory = ChatMemoryCategory.Constraint,
    val sourceId: String? = null
)

data class ChatDeleteConfirmation(
    val messageId: String,
    val spaceId: String,
    val messagePreview: String,
    val impact: ChatDeleteImpact
)

data class ChatRememberedMessageMetadata(
    val messageId: String,
    val category: ChatMemoryCategory,
    val rememberedAtEpochMillis: Long
)

interface ChatRememberedMessageStore {
    fun load(messageId: String): ChatRememberedMessageMetadata?
    fun loadRememberedMessageIds(): Set<String>
    fun save(metadata: ChatRememberedMessageMetadata)

    object None : ChatRememberedMessageStore {
        override fun load(messageId: String): ChatRememberedMessageMetadata? = null
        override fun loadRememberedMessageIds(): Set<String> = emptySet()
        override fun save(metadata: ChatRememberedMessageMetadata) = Unit
    }
}

sealed interface ChatMemoryCommitResult {
    object Saved : ChatMemoryCommitResult
    data class Unavailable(val message: String) : ChatMemoryCommitResult
    data class Failed(val message: String) : ChatMemoryCommitResult
}

interface ChatMessageActionPort {
    suspend fun loadDeleteImpact(messageId: String, spaceId: String): ChatDeleteImpact
    suspend fun remember(draft: ChatMemoryDraft): ChatMemoryCommitResult

    object Unavailable : ChatMessageActionPort {
        override suspend fun loadDeleteImpact(messageId: String, spaceId: String): ChatDeleteImpact =
            ChatDeleteImpact()

        override suspend fun remember(draft: ChatMemoryDraft): ChatMemoryCommitResult =
            ChatMemoryCommitResult.Unavailable("当前仓库不支持保存这类记忆。")
    }
}

enum class ChatClipboardResult {
    Copied,
    Unavailable,
    Failed
}

interface ChatClipboardPort {
    fun copyPlainText(text: String, attachmentLabel: String? = null): ChatClipboardResult

    object Unavailable : ChatClipboardPort {
        override fun copyPlainText(text: String, attachmentLabel: String?): ChatClipboardResult =
            ChatClipboardResult.Unavailable
    }
}

sealed interface ChatMediaResult {
    object Success : ChatMediaResult
    data class PermissionDenied(val message: String) : ChatMediaResult
    data class Failure(val message: String) : ChatMediaResult
}

interface ChatImageMediaPort {
    suspend fun save(uri: String, displayName: String, mimeType: String?): ChatMediaResult
    suspend fun share(uri: String, displayName: String, mimeType: String?): ChatMediaResult

    object Unavailable : ChatImageMediaPort {
        override suspend fun save(uri: String, displayName: String, mimeType: String?): ChatMediaResult =
            ChatMediaResult.Failure("当前设备不支持保存图片。")

        override suspend fun share(uri: String, displayName: String, mimeType: String?): ChatMediaResult =
            ChatMediaResult.Failure("当前设备不支持分享图片。")
    }
}

data class ChatSourceUi(
    val id: String,
    val displayName: String,
    val typeLabel: String,
    val stateLabel: String,
    val valid: Boolean
)

data class ChatSourceAttachmentPresentation(
    val sourceId: String?,
    val displayName: String,
    val typeLabel: String,
    val stateLabel: String,
    val valid: Boolean,
    val canReselectForCurrentSession: Boolean
)

data class ChatInvalidSourceReselectRequest(
    val sessionId: String,
    val sourceId: String,
    val originalDisplayName: String
)

fun ChatSourceAttachmentPresentation.toReselectRequest(sessionId: String): ChatInvalidSourceReselectRequest? {
    val invalidSourceId = sourceId ?: return null
    if (valid) return null
    return ChatInvalidSourceReselectRequest(sessionId, invalidSourceId, displayName)
}

fun resolveChatSourceAttachment(
    attachment: ChatAttachmentUi,
    sources: List<ChatSourceUi>
): ChatSourceAttachmentPresentation {
    val source = attachment.sourceId?.let { id -> sources.firstOrNull { it.id == id } }
    return if (source == null) {
        ChatSourceAttachmentPresentation(
            sourceId = attachment.sourceId,
            displayName = attachment.name,
            typeLabel = attachment.mimeType?.substringAfterLast('/')?.uppercase(Locale.ROOT) ?: "资料",
            stateLabel = "资料已失效",
            valid = false,
            canReselectForCurrentSession = true
        )
    } else {
        ChatSourceAttachmentPresentation(
            sourceId = source.id,
            displayName = source.displayName,
            typeLabel = source.typeLabel,
            stateLabel = source.stateLabel,
            valid = source.valid,
            canReselectForCurrentSession = !source.valid
        )
    }
}

data class ChatMessageMetadata(
    val exactTime: String,
    val deliveryLabel: String
)

fun chatMessageMetadata(item: ChatTimelineItem, timeZoneId: String = TimeZone.getDefault().id): ChatMessageMetadata {
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone(timeZoneId)
    }
    return ChatMessageMetadata(
        exactTime = format.format(Date(item.createdAtEpochMillis)),
        deliveryLabel = when (item.role) {
            MessageRole.User -> "已保存到本机"
            MessageRole.Assistant -> "已接收并保存"
            MessageRole.System,
            MessageRole.Tool -> "本机记录"
        }
    )
}

sealed interface ChatTimelineEntry {
    data class DateSeparator(val label: String) : ChatTimelineEntry
    data class Message(val item: ChatTimelineItem) : ChatTimelineEntry
}

fun buildChatTimelineEntries(
    messages: List<ChatTimelineItem>,
    timeZoneId: String = TimeZone.getDefault().id
): List<ChatTimelineEntry> {
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone(timeZoneId)
    }
    var previousDay: String? = null
    return buildList {
        messages.forEach { item ->
            val day = dayFormat.format(Date(item.createdAtEpochMillis))
            if (day != previousDay) {
                add(ChatTimelineEntry.DateSeparator(day))
                previousDay = day
            }
            add(ChatTimelineEntry.Message(item))
        }
    }
}

sealed interface ChatRichInline {
    data class Text(val source: String) : ChatRichInline
    data class Bold(val source: String) : ChatRichInline
    data class Code(val source: String) : ChatRichInline
    data class Formula(val source: String) : ChatRichInline
    data class Link(val label: String, val url: String) : ChatRichInline
}

sealed interface ChatRichBlock {
    data class Heading(val level: Int, val text: String) : ChatRichBlock
    data class Paragraph(val inlines: List<ChatRichInline>) : ChatRichBlock
    data class ListItem(val text: String, val ordered: Boolean, val index: Int?) : ChatRichBlock
    data class Quote(val text: String) : ChatRichBlock
    data class Code(val language: String?, val source: String) : ChatRichBlock
    data class Formula(val source: String) : ChatRichBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ChatRichBlock
    data class PlainText(val source: String) : ChatRichBlock
}

object ChatRichContentParser {
    fun parse(source: String): List<ChatRichBlock> = runCatching { parseStrict(source) }
        .getOrElse { listOf(ChatRichBlock.PlainText(source)) }

    private fun parseStrict(source: String): List<ChatRichBlock> {
        if (source.isBlank()) return listOf(ChatRichBlock.PlainText(source))
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').lines()
        val blocks = mutableListOf<ChatRichBlock>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.isBlank()) {
                index += 1
                continue
            }
            if (line.startsWith("```")) {
                val language = line.removePrefix("```").trim().ifBlank { null }
                val closing = (index + 1 until lines.size).firstOrNull { lines[it].trim() == "```" }
                    ?: return listOf(ChatRichBlock.PlainText(source))
                blocks += ChatRichBlock.Code(language, lines.subList(index + 1, closing).joinToString("\n"))
                index = closing + 1
                continue
            }
            if (line.trim().startsWith("$$")) {
                val trimmed = line.trim()
                if (trimmed.length >= 4 && trimmed.endsWith("$$")) {
                    blocks += ChatRichBlock.Formula(trimmed.removePrefix("$$").removeSuffix("$$").trim())
                    index += 1
                    continue
                }
                return listOf(ChatRichBlock.PlainText(source))
            }
            val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(line)
            if (heading != null) {
                blocks += ChatRichBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
                index += 1
                continue
            }
            if (line.startsWith("> ")) {
                blocks += ChatRichBlock.Quote(line.removePrefix("> "))
                index += 1
                continue
            }
            val unordered = Regex("^[-*+]\\s+(.+)$").matchEntire(line)
            if (unordered != null) {
                blocks += ChatRichBlock.ListItem(unordered.groupValues[1], ordered = false, index = null)
                index += 1
                continue
            }
            val ordered = Regex("^(\\d+)[.)]\\s+(.+)$").matchEntire(line)
            if (ordered != null) {
                blocks += ChatRichBlock.ListItem(
                    ordered.groupValues[2],
                    ordered = true,
                    index = ordered.groupValues[1].toIntOrNull()
                )
                index += 1
                continue
            }
            if (line.contains('|') && index + 1 < lines.size && isTableDivider(lines[index + 1])) {
                val headers = tableCells(line)
                val rows = mutableListOf<List<String>>()
                index += 2
                while (index < lines.size && lines[index].contains('|') && lines[index].isNotBlank()) {
                    rows += tableCells(lines[index])
                    index += 1
                }
                if (headers.isNotEmpty()) blocks += ChatRichBlock.Table(headers, rows)
                continue
            }
            val paragraph = mutableListOf(line)
            index += 1
            while (index < lines.size && lines[index].isNotBlank() && !startsBlock(lines, index)) {
                paragraph += lines[index]
                index += 1
            }
            blocks += ChatRichBlock.Paragraph(parseInlines(paragraph.joinToString("\n")))
        }
        return blocks.ifEmpty { listOf(ChatRichBlock.PlainText(source)) }
    }

    private fun startsBlock(lines: List<String>, index: Int): Boolean {
        val line = lines[index]
        return line.startsWith("```") || line.trim().startsWith("$$") || line.startsWith("> ") ||
            Regex("^(#{1,6})\\s+.+$").matches(line) || Regex("^[-*+]\\s+.+$").matches(line) ||
            Regex("^\\d+[.)]\\s+.+$").matches(line) ||
            (line.contains('|') && index + 1 < lines.size && isTableDivider(lines[index + 1]))
    }

    private fun isTableDivider(line: String): Boolean =
        tableCells(line).isNotEmpty() && tableCells(line).all { Regex(":?-{3,}:?").matches(it.replace(" ", "")) }

    private fun tableCells(line: String): List<String> = line.trim().trim('|').split('|').map(String::trim)

    internal fun parseInlines(source: String): List<ChatRichInline> {
        val pattern = Regex("`([^`]+)`|\\[([^]]+)]\\((https?://[^)]+)\\)|\\$([^$\\n]+)\\$|\\*\\*([^*\\n]+)\\*\\*")
        val result = mutableListOf<ChatRichInline>()
        var cursor = 0
        pattern.findAll(source).forEach { match ->
            if (match.range.first > cursor) result += ChatRichInline.Text(source.substring(cursor, match.range.first))
            result += when {
                match.groupValues[1].isNotEmpty() -> ChatRichInline.Code(match.groupValues[1])
                match.groupValues[2].isNotEmpty() -> ChatRichInline.Link(match.groupValues[2], match.groupValues[3])
                match.groupValues[4].isNotEmpty() -> ChatRichInline.Formula(match.groupValues[4])
                else -> ChatRichInline.Bold(match.groupValues[5])
            }
            cursor = match.range.last + 1
        }
        if (cursor < source.length) result += ChatRichInline.Text(source.substring(cursor))
        return result.ifEmpty { listOf(ChatRichInline.Text(source)) }
    }
}
