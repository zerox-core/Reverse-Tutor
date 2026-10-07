package com.reversetutor.preview.shell

import com.reversetutor.feature.chat.ChatAttachmentKind
import com.reversetutor.feature.chat.ChatAttachmentReadiness
import com.reversetutor.feature.chat.ChatAttachmentRejection
import com.reversetutor.feature.chat.ChatComposerDraft
import com.reversetutor.feature.chat.ChatDraftAttachment
import com.reversetutor.feature.chat.ChatPermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream

class ChatAttachmentPlatformAdaptersTest {
    @Test
    fun pickedImageMapsSizeAndFailureWithoutFalseReadyState() {
        val accepted = mapPickedChatAttachment(
            ChatAttachmentPlatformInput(
                id = "image-1",
                kind = ChatAttachmentKind.Image,
                name = "one.png",
                mimeType = "image/png",
                uri = "content://one",
                sizeBytes = 123L
            )
        )
        assertTrue(accepted is ChatAttachmentInputResult.Ready)

        val oversized = mapPickedChatAttachment(
            ChatAttachmentPlatformInput(
                id = "image-2",
                kind = ChatAttachmentKind.Image,
                name = "large.png",
                mimeType = "image/png",
                uri = "content://large",
                sizeBytes = 20L * 1024L * 1024L + 1L
            )
        )
        assertEquals(
            ChatAttachmentRejection.ImageTooLarge,
            (oversized as ChatAttachmentInputResult.Rejected).reason
        )

        val failed = mapPickedChatAttachment(
            ChatAttachmentPlatformInput(
                id = "image-3",
                kind = ChatAttachmentKind.Image,
                name = "broken.png",
                mimeType = "image/png",
                uri = null,
                sizeBytes = null,
                error = "无法读取图片"
            )
        )
        assertTrue(failed is ChatAttachmentInputResult.Failed)
        assertTrue((failed as ChatAttachmentInputResult.Failed).attachment.readiness.isFailure)
    }

    @Test
    fun permissionMapperDistinguishesRetryFromSettings() {
        assertEquals(
            ChatPermissionState.Denied,
            mapCameraPermissionResult(granted = false, shouldShowRationale = true)
        )
        assertEquals(
            ChatPermissionState.PermanentlyDenied,
            mapCameraPermissionResult(granted = false, shouldShowRationale = false)
        )
    }

    @Test
    fun unknownProviderLengthUsesBoundedStreamAndNeverReturnsReadyOnOversizeOrError() {
        val limit = com.reversetutor.feature.chat.ChatAttachmentPolicy.MaxImageBytes
        val exact = validateChatAttachmentSize(null) { CountingInputStream(limit) }
        assertEquals(ChatAttachmentSizeResult.Valid(limit), exact)

        val over = CountingInputStream(limit + 1L)
        val overResult = validateChatAttachmentSize(null) { over }
        assertEquals(ChatAttachmentSizeResult.Oversize(limit + 1L), overResult)
        assertTrue(over.bytesServed <= limit + 1L)

        val failed = validateChatAttachmentSize(null) {
            object : InputStream() {
                override fun read(): Int = throw IOException("provider failure")
            }
        }
        assertTrue(failed is ChatAttachmentSizeResult.Failed)
    }

    @Test
    fun unknownOversizeIsRejectedByAppShellMapperWithoutChangingDraftAttachments() {
        val limit = com.reversetutor.feature.chat.ChatAttachmentPolicy.MaxImageBytes
        val sizeResult = validateChatAttachmentSize(null) { CountingInputStream(limit + 1L) }
        assertEquals(ChatAttachmentSizeResult.Oversize(limit + 1L), sizeResult)
        val draft = ChatComposerDraft(
            text = "still sendable",
            attachments = List(8) { index ->
                ChatDraftAttachment(
                    id = "source-$index",
                    kind = ChatAttachmentKind.Source,
                    name = "existing-$index.pdf",
                    sourceId = "source-$index",
                    readiness = ChatAttachmentReadiness.Ready
                )
            }
        )

        val mapped = reduceChatAttachmentActivityResult(
            currentAttachments = draft.attachments,
            input = ChatAttachmentPlatformInput(
                id = "unknown-large",
                kind = ChatAttachmentKind.Image,
                name = "large.png",
                mimeType = "image/png",
                uri = "content://large",
                sizeBytes = (sizeResult as ChatAttachmentSizeResult.Oversize).actualSizeBytesAtLeast
            )
        )

        val unchanged = draft.copy(attachments = mapped.attachments)
        assertEquals(draft, unchanged)
        assertEquals(8, unchanged.attachments.size)
        assertTrue(unchanged.canSend)
        assertEquals("单张图片不能超过 20 MB。", mapped.notice)
        assertEquals(
            ChatAttachmentRejection.ImageTooLarge,
            (mapped.inputResult as ChatAttachmentInputResult.Rejected).reason
        )
    }
}

private class CountingInputStream(private val total: Long) : InputStream() {
    var bytesServed: Long = 0
        private set

    override fun read(): Int {
        if (bytesServed >= total) return -1
        bytesServed += 1
        return 0
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesServed >= total) return -1
        val count = minOf(length.toLong(), total - bytesServed).toInt()
        bytesServed += count
        return count
    }
}
