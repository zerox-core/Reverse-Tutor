package com.reversetutor.preview.shell

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.reversetutor.feature.chat.ChatAttachmentKind
import com.reversetutor.feature.chat.ChatAttachmentPolicy
import com.reversetutor.feature.chat.ChatAttachmentReadiness
import com.reversetutor.feature.chat.ChatAttachmentRejection
import com.reversetutor.feature.chat.ChatDraftAttachment
import com.reversetutor.feature.chat.ChatPermissionState
import java.io.File
import java.io.InputStream

data class ChatAttachmentPlatformInput(
    val id: String,
    val kind: ChatAttachmentKind,
    val name: String,
    val mimeType: String? = null,
    val uri: String? = null,
    val sourceId: String? = null,
    val sizeBytes: Long? = null,
    val error: String? = null
)

sealed interface ChatAttachmentInputResult {
    data class Ready(val attachment: ChatDraftAttachment) : ChatAttachmentInputResult
    data class Failed(val attachment: ChatDraftAttachment) : ChatAttachmentInputResult
    data class Rejected(val reason: ChatAttachmentRejection) : ChatAttachmentInputResult
}

sealed interface ChatAttachmentSizeResult {
    data class Valid(val sizeBytes: Long) : ChatAttachmentSizeResult
    data class Oversize(val actualSizeBytesAtLeast: Long) : ChatAttachmentSizeResult
    data class Failed(val message: String) : ChatAttachmentSizeResult
}

data class ChatAttachmentActivityResultState(
    val attachments: List<ChatDraftAttachment>,
    val notice: String?,
    val inputResult: ChatAttachmentInputResult
)

fun reduceChatAttachmentActivityResult(
    currentAttachments: List<ChatDraftAttachment>,
    input: ChatAttachmentPlatformInput,
    currentNotice: String? = null
): ChatAttachmentActivityResultState {
    val result = mapPickedChatAttachment(input)
    return when (result) {
        is ChatAttachmentInputResult.Ready -> ChatAttachmentActivityResultState(
            attachments = currentAttachments + result.attachment,
            notice = currentNotice,
            inputResult = result
        )
        is ChatAttachmentInputResult.Failed -> ChatAttachmentActivityResultState(
            attachments = currentAttachments + result.attachment,
            notice = currentNotice,
            inputResult = result
        )
        is ChatAttachmentInputResult.Rejected -> ChatAttachmentActivityResultState(
            attachments = currentAttachments,
            notice = when (result.reason) {
                ChatAttachmentRejection.TooMany -> "每条消息最多添加 9 个附件。"
                ChatAttachmentRejection.ImageTooLarge -> "单张图片不能超过 20 MB。"
            },
            inputResult = result
        )
    }
}

fun validateChatAttachmentSize(
    declaredLength: Long?,
    openStream: () -> InputStream?
): ChatAttachmentSizeResult {
    val limit = ChatAttachmentPolicy.MaxImageBytes
    if (declaredLength != null) {
        return if (declaredLength <= limit) {
            ChatAttachmentSizeResult.Valid(declaredLength)
        } else {
            ChatAttachmentSizeResult.Oversize(declaredLength)
        }
    }
    return runCatching {
        val stream = openStream() ?: error("无法读取图片")
        stream.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            val maximumRead = limit + 1L
            while (total < maximumRead) {
                val requested = minOf(buffer.size.toLong(), maximumRead - total).toInt()
                val count = input.read(buffer, 0, requested)
                if (count < 0) break
                if (count == 0) {
                    if (input.read() < 0) break
                    total += 1L
                } else {
                    total += count
                }
            }
            if (total > limit) {
                ChatAttachmentSizeResult.Oversize(total)
            } else {
                ChatAttachmentSizeResult.Valid(total)
            }
        }
    }.getOrElse { error ->
        ChatAttachmentSizeResult.Failed(error.message ?: "无法读取图片")
    }
}

fun mapPickedChatAttachment(input: ChatAttachmentPlatformInput): ChatAttachmentInputResult {
    if (input.kind in setOf(ChatAttachmentKind.Image, ChatAttachmentKind.Camera) &&
        input.sizeBytes != null && input.sizeBytes > ChatAttachmentPolicy.MaxImageBytes
    ) {
        return ChatAttachmentInputResult.Rejected(ChatAttachmentRejection.ImageTooLarge)
    }
    val failure = input.error?.takeIf(String::isNotBlank)
        ?: if (input.kind != ChatAttachmentKind.Source && input.uri.isNullOrBlank()) "无法读取附件" else null
    val attachment = ChatDraftAttachment(
        id = input.id,
        kind = input.kind,
        name = input.name,
        mimeType = input.mimeType,
        uri = input.uri,
        sourceId = input.sourceId,
        sizeBytes = input.sizeBytes,
        readiness = if (failure == null) {
            ChatAttachmentReadiness.Ready
        } else {
            ChatAttachmentReadiness.Failed(failure, retryable = true)
        }
    )
    return if (failure == null) {
        ChatAttachmentInputResult.Ready(attachment)
    } else {
        ChatAttachmentInputResult.Failed(attachment)
    }
}

fun mapCameraPermissionResult(
    granted: Boolean,
    shouldShowRationale: Boolean
): ChatPermissionState = when {
    granted -> ChatPermissionState.Granted
    shouldShowRationale -> ChatPermissionState.Denied
    else -> ChatPermissionState.PermanentlyDenied
}

fun Context.readChatAttachmentInput(
    uri: Uri,
    id: String,
    kind: ChatAttachmentKind = ChatAttachmentKind.Image
): ChatAttachmentPlatformInput = runCatching {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        ?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')
        ?: "selected-image"
    val declaredSize = contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        descriptor.length.takeIf { it >= 0L }
    }
    val sizeResult = if (kind in setOf(ChatAttachmentKind.Image, ChatAttachmentKind.Camera)) {
        validateChatAttachmentSize(declaredSize) { contentResolver.openInputStream(uri) }
    } else {
        ChatAttachmentSizeResult.Valid(declaredSize ?: 0L)
    }
    ChatAttachmentPlatformInput(
        id = id,
        kind = kind,
        name = name,
        mimeType = contentResolver.getType(uri),
        uri = uri.toString(),
        sizeBytes = when (sizeResult) {
            is ChatAttachmentSizeResult.Valid -> sizeResult.sizeBytes
            is ChatAttachmentSizeResult.Oversize -> sizeResult.actualSizeBytesAtLeast
            is ChatAttachmentSizeResult.Failed -> null
        },
        error = (sizeResult as? ChatAttachmentSizeResult.Failed)?.message
    )
}.getOrElse { error ->
    ChatAttachmentPlatformInput(
        id = id,
        kind = kind,
        name = uri.lastPathSegment?.substringAfterLast('/') ?: "selected-image",
        mimeType = contentResolver.getType(uri),
        uri = uri.toString(),
        error = error.message ?: "无法读取图片"
    )
}

fun Context.persistCameraAttachment(bitmap: Bitmap, id: String): ChatAttachmentPlatformInput = runCatching {
    val directory = File(filesDir, "chat-camera").apply { mkdirs() }
    val file = File(directory, "$id.jpg")
    file.outputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
    }
    ChatAttachmentPlatformInput(
        id = id,
        kind = ChatAttachmentKind.Camera,
        name = file.name,
        mimeType = "image/jpeg",
        uri = Uri.fromFile(file).toString(),
        sizeBytes = file.length()
    )
}.getOrElse { error ->
    ChatAttachmentPlatformInput(
        id = id,
        kind = ChatAttachmentKind.Camera,
        name = "camera.jpg",
        mimeType = "image/jpeg",
        error = error.message ?: "照片保存失败"
    )
}
