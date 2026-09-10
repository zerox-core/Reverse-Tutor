package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-006 Task 3: JVM contracts for the in-chat attachment action sheet.
 * The sheet must expose the phone-document entry ("文件") so the
 * learner can import local material without leaving the conversation, and
 * entry subtitles must stay limited to safe user-facing guidance.
 */
class ChatAttachmentSheetContractsTest {

    @Test
    fun attachmentSheetExposesPhoneDocumentEntry() {
        val entries = chatAttachmentSheetActionSpecs()

        assertEquals(listOf("相册", "文件"), entries.map { it.label })
        val documentEntry = entries.single { it.label == "文件" }
        assertNull(documentEntry.subtitle)
    }

    @Test
    fun attachmentSheetEntriesNeverLeakPathsOrInternalIdentifiers() {
        val entries = chatAttachmentSheetActionSpecs()

        entries.forEach { spec ->
            assertTrue("label must be non-blank: $spec", spec.label.isNotBlank())
            assertTrue(
                "labels/subtitles must not expose file paths or content:// URIs: $spec",
                !spec.label.contains('/') && (spec.subtitle?.contains("content://") != true)
            )
        }
    }

    @Test
    fun cameraPermissionSubtitleMapsOnlyDeniedStates() {
        assertEquals("相机权限被拒绝，可重新授权", chatCameraPermissionSubtitle(ChatPermissionState.Denied))
        assertEquals("相机权限已关闭，前往系统设置", chatCameraPermissionSubtitle(ChatPermissionState.PermanentlyDenied))
        assertNull(chatCameraPermissionSubtitle(ChatPermissionState.Granted))
        assertNull(chatCameraPermissionSubtitle(ChatPermissionState.Requestable))
    }
}
