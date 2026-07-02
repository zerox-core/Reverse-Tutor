package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.llm.LlmProfileInput
import com.reversetutor.core.model.LlmProviderKind
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2CoreLoopDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun phase2CoreLoopPersistsNoModelAndMockReplyAfterRestart() {
        waitForText("Sessions")
        composeRule.onNodeWithText("New session").performClick()
        composeRule.onNodeWithText("Exam sprint").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("OK", useUnmergedTree = true).performClick()
        waitForText("Exam sprint")

        composeRule.onAllNodesWithText("Open").onLast().performClick()
        composeRule.onNodeWithText("No messages yet").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("P2-006-no-model")
        composeRule.onNodeWithText("Send").performClick()
        waitForText("P2-006-no-model")
        composeRule.onNodeWithText("You").assertIsDisplayed()
        composeRule.onNodeWithText("No model configured").assertIsDisplayed()
        composeRule.onAllNodesWithText("Mock generation ready").assertCountEquals(0)

        createActivePreviewProfile()
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("P2-006-mock-generation")
        composeRule.onNodeWithText("Send").performClick()
        waitForText("Mock generation ready")
        composeRule.onNodeWithText("P2-006-mock-generation").assertIsDisplayed()
        composeRule.onNodeWithText("Assistant").assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()
        waitForText("Sessions")
        waitForText("Exam sprint")
        composeRule.onAllNodesWithText("Open").onLast().performClick()
        waitForText("P2-006-no-model")
        composeRule.onNodeWithText("P2-006-mock-generation").assertIsDisplayed()
        composeRule.onNodeWithText("Mock generation ready").assertIsDisplayed()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun createActivePreviewProfile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            val repository = DataModule.llmProfileRepository(context)
            repository.saveProfile(
                input = LlmProfileInput(
                    name = "P2Profile",
                    provider = LlmProviderKind.OpenAiCompatible,
                    model = "gpt-4o-mini",
                    baseUrl = "https://api.openai.com/v1",
                    apiKey = ""
                ),
                nowEpochMillis = System.currentTimeMillis()
            )
            val profile = repository.listProfiles().first { it.name == "P2Profile" }
            repository.activateProfile(
                profileId = profile.id,
                nowEpochMillis = System.currentTimeMillis()
            )
        }
    }
}
