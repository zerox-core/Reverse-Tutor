package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.feature.memory.ContextHubRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5ContextHubDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetLocalData() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
    }

    @After
    fun cleanUpLocalData() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
    }

    @Test
    fun contextHubShowsSessionEvidenceSectionsAndReturnsToChat() {
        var chatOpened = false
        composeRule.setContent {
            ReverseTutorTheme {
                ContextHubRoute(
                    memoryRepository = DataModule.memoryRepository(context),
                    graphRepository = DataModule.graphRepository(context),
                    sessionId = "session-context",
                    sessionTitle = "函数训练",
                    onOpenChat = { chatOpened = true },
                    onOpenSources = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithText("学习脉络").assertIsDisplayed()
        composeRule.onNodeWithText("当前会话：函数训练").assertIsDisplayed()
        listOf("概览", "图谱", "锚点", "随笔", "错因", "设置").forEach { label ->
            assertTrue(
                composeRule.onAllNodesWithText(label, useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            )
        }

        composeRule.onAllNodesWithText("图谱")
            .filterToOne(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
            )
            .performScrollTo()
            .performClick()
        assertTrue(
            composeRule.onAllNodesWithText("当前会话信息过少，再多聊会天吧")
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
        composeRule.onNodeWithText("返回聊天").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(chatOpened) }
    }
}
