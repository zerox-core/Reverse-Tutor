package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-006 Task 3: JVM contracts for the in-chat attachment action sheet.
 * The sheet must expose the phone-document entry ("从手机选择资料") so the
 * learner can import local material without leaving the conversation, and
 * entry subtitles must stay limited to safe user-facing guidance.
 */
class ChatAttachmentSheetContractsTest {

    @Test
    fun attachmentSheetExposesPhoneSourceEntry() {
        val entries = chatAttachmentSheetActionSpecs(cameraSubtitle = null)

        assertEquals(
            listOf("选择图片", "选择应用内资料", "从手机选择资料", "拍照", "查看本会话资料"),
            entries.map { it.label }
        )
        val phoneEntry = entries.single { it.label == "从手机选择资料" }
        assertNull(phoneEntry.subtitle)
    }

    @Test
    fun attachmentSheetEntriesNeverLeakPathsOrInternalIdentifiers() {
        val entries = chatAttachmentSheetActionSpecs(cameraSubtitle = null)

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

    @Test
    fun cameraSubtitleIsCarriedOnlyByTheTakePhotoEntry() {
        val entries = chatAttachmentSheetActionSpecs(cameraSubtitle = "相机权限已关闭，前往系统设置")

        val withSubtitle = entries.filter { it.subtitle != null }
        assertEquals(listOf("拍照"), withSubtitle.map { it.label })
        assertEquals("相机权限已关闭，前往系统设置", withSubtitle.single().subtitle)
    }
}
