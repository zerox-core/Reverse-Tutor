package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5ContextHubDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun contextHubIsReachableFromActiveChatAndBackReturnsToChat() {
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
        composeRule.onNodeWithText("Open context hub").performClick()
        waitForText("Context hub")

        composeRule.onNodeWithText("Session: Exam sprint").assertIsDisplayed()
        composeRule.onNodeWithText("Graph").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Anchors").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Notes").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Errors").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Session settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Graph").performScrollTo().performClick()
        waitForText("No graph nodes yet")
        composeRule.onNodeWithText("Native graph canvas").performScrollTo().assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        waitForText("No messages yet")
        composeRule.onNodeWithText("Open context hub").assertIsDisplayed()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
