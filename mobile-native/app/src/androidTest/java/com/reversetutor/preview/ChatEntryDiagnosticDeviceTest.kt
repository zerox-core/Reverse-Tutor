package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.llm.LlmProfileInput
import com.reversetutor.core.model.LlmProviderKind
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RT-2026-034 discriminator. This class is run directly against the dedicated
 * emulator only; it never needs a real Provider or an API key.
 */
@RunWith(AndroidJUnit4::class)
class ChatEntryDiagnosticDeviceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetLocalState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
        composeRule.activityRule.scenario.recreate()
        waitForText("会话")
    }

    @Test
    fun noModelSessionCreationReachesChat() {
        clearAllLlmProfiles()
        createBuiltInStudySession()
        assertEventuallyInChat("no_model")
    }

    @Test
    fun localProfileSessionCreationReachesChat() {
        createActivePreviewProfile()
        composeRule.activityRule.scenario.recreate()
        waitForText("会话")
        createBuiltInStudySession()
        assertEventuallyInChat("local_fake_profile")
    }

    private fun createBuiltInStudySession() {
        composeRule.onNodeWithTag("formal-home-new-session").performClick()
        waitForText("学习模式")
        composeRule.onNodeWithText("学习模式").performClick()
        dismissAnnouncementIfPresent()
        waitForText("内置预设")
        composeRule.onNodeWithText("高三数学讲题冲刺").performClick()
        waitForText("使用此预设")
        composeRule.onNodeWithText("使用此预设").performClick()
        waitForText("创建并进入聊天")
        composeRule.onNodeWithText("创建并进入聊天").performClick()
    }

    private fun assertEventuallyInChat(branch: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithContentDescription("返回会话首页")
                .fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithContentDescription("发送")
                    .fetchSemanticsNodes().isNotEmpty()
        }
        val hasBack = composeRule.onAllNodesWithContentDescription("返回会话首页")
            .fetchSemanticsNodes().isNotEmpty()
        val hasSend = composeRule.onAllNodesWithContentDescription("发送")
            .fetchSemanticsNodes().isNotEmpty()
        val editableCount = composeRule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
        assertTrue(
            "branch=$branch did not reach Chat; back=$hasBack, send=$hasSend, editableCount=$editableCount",
            hasBack || hasSend
        )
    }

    private fun dismissAnnouncementIfPresent() {
        composeRule.waitUntil(2_000) {
            composeRule.onAllNodesWithText("稍后再说").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("内置预设").fetchSemanticsNodes().isNotEmpty()
        }
        val dismissNodes = composeRule.onAllNodesWithText("稍后再说")
        if (dismissNodes.fetchSemanticsNodes().isNotEmpty()) {
            dismissNodes.onFirst().performClick()
        }
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clearAllLlmProfiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            DataModule.llmProfileRepository(context).listProfiles().forEach { profile ->
                DataModule.llmProfileRepository(context).deleteProfile(profile.id)
            }
        }
    }

    private fun createActivePreviewProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val repository = DataModule.llmProfileRepository(context)
            repository.saveProfile(
                input = LlmProfileInput(
                    name = "DiagnosticProfile",
                    provider = LlmProviderKind.OpenAiCompatible,
                    model = "diagnostic-local",
                    baseUrl = "https://example.invalid/v1",
                    apiKey = ""
                ),
                nowEpochMillis = System.currentTimeMillis()
            )
            val profile = repository.listProfiles().first { it.name == "DiagnosticProfile" }
            repository.activateProfile(profile.id, System.currentTimeMillis())
        }
    }
}
