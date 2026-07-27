package com.reversetutor.preview.shell

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.memory.AnchorInput
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.memory.MemorySnapshot
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.graph.GraphSnapshot
import com.reversetutor.feature.chat.ChatClipboardPort
import com.reversetutor.feature.chat.ChatClipboardResult
import com.reversetutor.feature.chat.ChatDeleteImpact
import com.reversetutor.feature.chat.ChatDerivative
import com.reversetutor.feature.chat.ChatDerivativeKind
import com.reversetutor.feature.chat.ChatImageMediaPort
import com.reversetutor.feature.chat.ChatMediaResult
import com.reversetutor.feature.chat.ChatMemoryCategory
import com.reversetutor.feature.chat.ChatMemoryCategoryCapability
import com.reversetutor.feature.chat.ChatMemoryCommitResult
import com.reversetutor.feature.chat.ChatMemoryDraft
import com.reversetutor.feature.chat.ChatMessageActionPort
import com.reversetutor.feature.chat.ChatMessageDeletePort
import com.reversetutor.feature.chat.ChatMessageDeleteResult
import com.reversetutor.feature.chat.PendingChatMessageDeletion
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidChatClipboardPort(
    private val writer: (label: String, text: String) -> Unit
) : ChatClipboardPort {
    constructor(context: Context) : this(
        writer = { label, text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    )

    override fun copyPlainText(text: String, attachmentLabel: String?): ChatClipboardResult = try {
        writer("消息", text)
        ChatClipboardResult.Copied
    } catch (_: Exception) {
        ChatClipboardResult.Failed
    }
}

data class ChatImageMediaStoreSpec(
    val displayName: String,
    val mimeType: String,
    val relativePath: String?
)

fun buildChatImageMediaStoreSpec(
    displayName: String,
    mimeType: String?,
    sdkInt: Int = Build.VERSION.SDK_INT
): ChatImageMediaStoreSpec = ChatImageMediaStoreSpec(
    displayName = displayName.ifBlank { "chat-image-${System.currentTimeMillis()}.jpg" },
    mimeType = mimeType?.takeIf(String::isNotBlank) ?: "image/jpeg",
    relativePath = if (sdkInt >= Build.VERSION_CODES.Q) {
        "${Environment.DIRECTORY_PICTURES}/Reverse Tutor"
    } else {
        null
    }
)

data class ChatImageShareSpec(
    val action: String,
    val uriText: String,
    val mimeType: String,
    val grantReadPermission: Boolean
)

fun buildChatImageShareSpec(uriText: String, mimeType: String?): ChatImageShareSpec =
    ChatImageShareSpec(
        action = Intent.ACTION_SEND,
        uriText = uriText,
        mimeType = mimeType?.takeIf(String::isNotBlank) ?: "image/*",
        grantReadPermission = true
    )

class AndroidChatImageMediaPort(private val context: Context) : ChatImageMediaPort {
    override suspend fun save(uri: String, displayName: String, mimeType: String?): ChatMediaResult =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext ChatMediaResult.PermissionDenied("需要存储权限才能保存图片。")
            }
            val spec = buildChatImageMediaStoreSpec(displayName, mimeType)
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, spec.displayName)
                put(MediaStore.Images.Media.MIME_TYPE, spec.mimeType)
                spec.relativePath?.let { put(MediaStore.Images.Media.RELATIVE_PATH, it) }
            }
            val destination = try {
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            } catch (error: SecurityException) {
                return@withContext ChatMediaResult.PermissionDenied(error.message ?: "没有保存图片的权限。")
            } catch (error: Exception) {
                return@withContext ChatMediaResult.Failure(error.message ?: "无法创建图片文件。")
            } ?: return@withContext ChatMediaResult.Failure("无法创建图片文件。")

            try {
                openChatImageInput(context, Uri.parse(uri)).use { input ->
                    context.contentResolver.openOutputStream(destination)?.use { output -> input.copyTo(output) }
                        ?: error("无法写入图片")
                }
                ChatMediaResult.Success
            } catch (error: SecurityException) {
                context.contentResolver.delete(destination, null, null)
                ChatMediaResult.PermissionDenied(error.message ?: "没有读取或保存图片的权限。")
            } catch (error: Exception) {
                context.contentResolver.delete(destination, null, null)
                ChatMediaResult.Failure(error.message ?: "保存图片失败。")
            }
        }

    override suspend fun share(uri: String, displayName: String, mimeType: String?): ChatMediaResult = try {
        val parsed = Uri.parse(uri)
        val shareUri = if (parsed.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.chat-files",
                File(requireNotNull(parsed.path))
            )
        } else {
            parsed
        }
        val spec = buildChatImageShareSpec(shareUri.toString(), mimeType)
        val intent = Intent(spec.action).apply {
            type = spec.mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享图片").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        ChatMediaResult.Success
    } catch (error: SecurityException) {
        ChatMediaResult.PermissionDenied(error.message ?: "没有分享这张图片的权限。")
    } catch (error: Exception) {
        ChatMediaResult.Failure(error.message ?: "无法分享图片。")
    }
}

private fun openChatImageInput(context: Context, uri: Uri) =
    if (uri.scheme == "file") {
        FileInputStream(File(requireNotNull(uri.path)))
    } else {
        context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
    }

data class RepositoryChatMessageActionCapabilities(
    val canDeleteSingleMessage: Boolean,
    val memoryCategories: Map<ChatMemoryCategory, ChatMemoryCategoryCapability>,
    val canCreateSourceAssociatedConstraint: Boolean,
    val canAtomicallyDeleteAndRestoreDerivatives: Boolean
) {
    companion object {
        val current = RepositoryChatMessageActionCapabilities(
            canDeleteSingleMessage = true,
            memoryCategories = ChatMemoryCategory.entries.associateWith(ChatMemoryCategory::capability),
            canCreateSourceAssociatedConstraint = true,
            canAtomicallyDeleteAndRestoreDerivatives = false
        )
    }
}

class RepositoryChatMessageActionPort internal constructor(
    private val backend: ChatMessageActionRepositoryBackend,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : ChatMessageActionPort {
    constructor(
        memoryRepository: MemoryRepository,
        graphRepository: GraphRepository,
        nowEpochMillis: () -> Long = System::currentTimeMillis
    ) : this(RealChatMessageActionRepositoryBackend(memoryRepository, graphRepository), nowEpochMillis)

    override suspend fun loadDeleteImpact(messageId: String, spaceId: String): ChatDeleteImpact {
        val memory = backend.memorySnapshot(spaceId).items.filter { it.sourceMessageId == messageId }
        val memoryIds = memory.mapTo(hashSetOf()) { it.id }
        val graph = backend.graphSnapshot(spaceId)
        val graphItems = buildList {
            graph.nodes.filter { it.sourceMemoryId in memoryIds }.forEach {
                add(ChatDerivative(it.id, "图谱节点：${it.label}", ChatDerivativeKind.Graph))
            }
            graph.edges.filter { it.sourceMemoryId in memoryIds }.forEach {
                add(ChatDerivative(it.id, "图谱关系：${it.relation}", ChatDerivativeKind.Graph))
            }
        }
        return ChatDeleteImpact(
            memories = memory.map { ChatDerivative(it.id, "记忆：${it.title}", ChatDerivativeKind.Memory) },
            graphItems = graphItems,
            supportsAtomicDerivativeDeleteAndRestore = false
        )
    }

    override suspend fun remember(draft: ChatMemoryDraft): ChatMemoryCommitResult {
        if (draft.text.isBlank()) return ChatMemoryCommitResult.Failed("记忆内容不能为空。")
        val unavailable = draft.category.capability as? ChatMemoryCategoryCapability.Unavailable
        if (unavailable != null) {
            return ChatMemoryCommitResult.Unavailable(unavailable.reason)
        }
        return try {
            when (draft.category) {
                ChatMemoryCategory.Constraint -> {
                    val saved = backend.createConstraint(
                        spaceId = draft.spaceId,
                        input = AnchorInput(
                            title = draft.text.lineSequence().first().take(40),
                            body = draft.text,
                            sourceMessageId = draft.messageId,
                            sourceId = draft.sourceId
                        ),
                        nowEpochMillis = nowEpochMillis()
                    )
                    if (!saved) ChatMemoryCommitResult.Failed("保存记忆失败，请重试。") else ChatMemoryCommitResult.Saved
                }
                else -> ChatMemoryCommitResult.Unavailable(
                    (draft.category.capability as ChatMemoryCategoryCapability.Unavailable).reason
                )
            }
        } catch (error: Exception) {
            ChatMemoryCommitResult.Failed(error.message ?: "保存记忆失败，请重试。")
        }
    }
}

internal interface ChatMessageActionRepositoryBackend {
    suspend fun memorySnapshot(spaceId: String): MemorySnapshot
    suspend fun graphSnapshot(spaceId: String): GraphSnapshot
    suspend fun createConstraint(spaceId: String, input: AnchorInput, nowEpochMillis: Long): Boolean
}

private class RealChatMessageActionRepositoryBackend(
    private val memoryRepository: MemoryRepository,
    private val graphRepository: GraphRepository
) : ChatMessageActionRepositoryBackend {
    override suspend fun memorySnapshot(spaceId: String): MemorySnapshot = memoryRepository.snapshot(spaceId)

    override suspend fun graphSnapshot(spaceId: String): GraphSnapshot = graphRepository.snapshot(spaceId)

    override suspend fun createConstraint(
        spaceId: String,
        input: AnchorInput,
        nowEpochMillis: Long
    ): Boolean =
        memoryRepository.createAnchor(
            input = input,
            nowEpochMillis = nowEpochMillis,
            spaceId = spaceId
        ) != null
}

internal interface ChatMessageDeleteRepositoryBackend {
    suspend fun deleteMessage(messageId: String): Boolean
    suspend fun messageExists(sessionId: String, messageId: String): Boolean
}

private class RealChatMessageDeleteRepositoryBackend(
    private val messageRepository: MessageRepository
) : ChatMessageDeleteRepositoryBackend {
    override suspend fun deleteMessage(messageId: String): Boolean = messageRepository.deleteMessage(messageId)

    override suspend fun messageExists(sessionId: String, messageId: String): Boolean =
        messageRepository.listMessages(sessionId).any { it.id == messageId }
}

class RepositoryChatMessageDeletePort internal constructor(
    private val backend: ChatMessageDeleteRepositoryBackend
) : ChatMessageDeletePort {
    constructor(messageRepository: MessageRepository) : this(RealChatMessageDeleteRepositoryBackend(messageRepository))

    override suspend fun delete(pending: PendingChatMessageDeletion): ChatMessageDeleteResult {
        val deleted = runCatching { backend.deleteMessage(pending.messageId) }.getOrNull()
        if (deleted == true) return ChatMessageDeleteResult.Success
        return try {
            if (backend.messageExists(pending.sessionId, pending.messageId)) {
                ChatMessageDeleteResult.RetryableFailure
            } else {
                ChatMessageDeleteResult.AlreadyAbsent
            }
        } catch (_: Exception) {
            ChatMessageDeleteResult.RetryableFailure
        }
    }
}
