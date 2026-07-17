package com.reversetutor.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FigmaResponsiveDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun formalHomeWeeklyAndCustomSessionFlowRemainReachable() {
        waitForText("会话")
        composeRule.onNodeWithTag("formal-home-screen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("新建会话").assertIsDisplayed()

        composeRule.onNodeWithTag("formal-home-screen").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("formal-weekly-screen").assertIsDisplayed()

        composeRule.onNodeWithTag("formal-weekly-screen").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("formal-home-screen").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("新建会话").performClick()
        waitForText("选择产品方向")
        composeRule.onNodeWithText("开始设置  →").performClick()
        dismissActivityAnnouncementIfPresent()
        waitForText("学习预设")
        composeRule.onNodeWithText("自定义").performClick()
        waitForText("自定义世界树")
        composeRule.onNodeWithText("把你的世界写下来").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        waitForText("学习预设")
    }

    private fun dismissActivityAnnouncementIfPresent() {
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithText("稍后再说").fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText("学习预设").fetchSemanticsNodes().isNotEmpty()
        }
        val dismissNodes = composeRule.onAllNodesWithText("稍后再说")
        if (dismissNodes.fetchSemanticsNodes().isNotEmpty()) {
            dismissNodes.onFirst().performClick()
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
