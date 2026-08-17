package com.reversetutor.preview

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase4ImportExportDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun phase4ImportModesExportAndWipeWorkOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.reversetutor.preview", context.packageName)
        resetLocalData(context)

        launchImportPreviewFromShareIntent()
        verifyChineseImportPreviewModes()
        completeImportAndVerifyOverview()
        verifyImportedSessionData(context)
    }

    private fun resetLocalData(context: Context) {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
        composeRule.activityRule.scenario.recreate()
        dismissActivityAnnouncementIfPresent()
        waitForText("会话")
    }

    private fun launchImportPreviewFromShareIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_SEND)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TEXT, sessionExportJson)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        waitForText("导入预览")
    }

    private fun verifyChineseImportPreviewModes() {
        waitForText("校验通过")
        assertDisplayedAfterScroll("shared-import.json")
        assertDisplayedAfterScroll("预计写入 2 条记录")

        composeRule.onNodeWithText("追加").performClick()
        assertDisplayedAfterScroll("当前本地空间")

        composeRule.onNodeWithText("新空间").performClick()
        assertDisplayedAfterScroll("新导入空间")

        composeRule.onNodeWithText("覆盖").performClick()
        assertDisplayedAfterScroll("当前本地空间（将替换）")

        composeRule.onNodeWithText("追加").performClick()
        composeRule.onNodeWithText("开始导入").performClick()
    }

    private fun completeImportAndVerifyOverview() {
        waitForText("导入与导出")
        assertDisplayedAfterScroll("最近记录")
        assertDisplayedAfterScroll("导入 shared-import.json")
        assertDisplayedAfterScroll("2 条写入 · 0 条提醒")
    }

    private fun verifyImportedSessionData(context: Context) {
        composeRule.waitUntil(5_000) {
            runBlocking {
                DataModule.sessionRepository(context).getSession("p4-session-1") != null
            }
        }

        runBlocking {
            val sessionRepository = DataModule.sessionRepository(context)
            val messageRepository = DataModule.messageRepository(context)

            val importedSession = sessionRepository.getSession("p4-session-1")
            assertNotNull(importedSession)
            assertEquals("P4-import-fixture", importedSession?.title)
            assertEquals(
                listOf("Hello-from-P4-fixture"),
                messageRepository.listMessages("p4-session-1").map { it.text }
            )
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 10_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissActivityAnnouncementIfPresent() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("稍后再说").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("会话").fetchSemanticsNodes().isNotEmpty()
        }
        val dismissNodes = composeRule.onAllNodesWithText("稍后再说")
        if (dismissNodes.fetchSemanticsNodes().isNotEmpty()) {
            dismissNodes.onFirst().performClick()
        }
    }

    private fun assertDisplayedAfterScroll(text: String) {
        val node = composeRule.onAllNodesWithText(text).onLast()
        runCatching { node.performScrollTo() }
        node.assertIsDisplayed()
    }

    private companion object {
        const val sessionExportJson =
            """{"schema":"reverse_tutor_session_export_v1","version":1,"type":"session_export","created_at":"2026-07-01T00:00:00Z","session":{"id":"p4-session-1","title":"P4-import-fixture"},"messages":[{"id":"p4-message-1","role":"user","text":"Hello-from-P4-fixture"}]}"""
    }
}
