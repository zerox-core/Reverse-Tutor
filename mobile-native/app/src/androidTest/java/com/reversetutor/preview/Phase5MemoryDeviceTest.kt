package com.reversetutor.preview

import android.content.Context
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5MemoryDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chatNoteActionCreatesMemoryNoteVisibleInContextHub() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
        composeRule.activityRule.scenario.recreate()

        waitForText("Sessions")
        composeRule.onNodeWithText("New session").performClick()
        composeRule.onNodeWithText("Exam sprint").performClick()
        composeRule.onNodeWithText("Create").performClick()
        composeRule.onNodeWithText("OK", useUnmergedTree = true).performClick()
        waitForText("Exam sprint")

        composeRule.onAllNodesWithText("Open").onLast().performClick()
        waitForText("No messages yet")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextInput("Remember this mistake")
        composeRule.onNodeWithText("Send").performClick()
        waitForText("Remember this mistake")

        composeRule.onNodeWithText("Note").performClick()
        waitForText("Note saved to Context hub.")
        composeRule.onNodeWithText("OK", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Open context hub").performClick()
        waitForText("Notes: 1")

        composeRule.onNodeWithText("Session-linked memory evidence").assertIsDisplayed()
        composeRule.onNodeWithText("Notes: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Open errors: 0").assertIsDisplayed()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
