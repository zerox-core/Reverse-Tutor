package com.reversetutor.preview.shell

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.reversetutor.feature.chat.Task2B1NewSessionRoute
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionCreatePort
import com.reversetutor.feature.chat.NewSessionCreateRequest
import com.reversetutor.feature.chat.NewSessionCreated
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.SessionListItem
import com.reversetutor.preview.theme.ReverseTutorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewSessionHubContractDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun builtInCardOpensDetailAndUseAdvancesToSixSectionCustomRoot() {
        composeRule.setContent {
            ReverseTutorTheme {
                Task2B1NewSessionRoute(
                    createPort = NewSessionCreatePort { error("not used") },
                    persistence = FakeNewSessionPersistence(),
                    onCreated = {}
                )
            }
        }

        composeRule.onNodeWithText("内置预设").assertIsDisplayed()
        composeRule.onNodeWithText("收藏").assertIsDisplayed()
        composeRule.onNodeWithText("自定义").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("草稿箱").assertCountEquals(0)

        composeRule.onNodeWithText("高三数学讲题冲刺").performClick()
        composeRule.onNodeWithText("使用此预设").assertIsDisplayed().performClick()

        listOf("基本资料", "目标与计划", "对话策略", "世界树", "资料", "自定义栏目").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("草稿箱").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("新建空白草稿").assertIsDisplayed()
    }

    @Test
    fun validCustomCreateReturnsNavigationItemWithLearnerAndOpeningMessage() {
        var capturedRequest: NewSessionCreateRequest? = null
        var navigatedSession: SessionListItem? = null
        composeRule.setContent {
            ReverseTutorTheme {
                Task2B1NewSessionRoute(
                    createPort = NewSessionCreatePort { request ->
                        capturedRequest = request
                        NewSessionCreated(
                            session = sessionItem(request),
                            learnerRole = request.snapshot.learnerRole,
                            openingMessage = request.snapshot.openingMessage
                        )
                    },
                    persistence = FakeNewSessionPersistence(),
                    onCreated = { navigatedSession = it }
                )
            }
        }

        composeRule.onNodeWithText("自定义").performClick()
        composeRule.onNodeWithText("基本资料").performClick()
        composeRule.onNodeWithTag("new-session-title").performTextReplacement("函数讲解")
        composeRule.onNodeWithTag("new-session-learner-role").performTextReplacement("会追问的学习者")
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.onNodeWithText("创建并进入聊天").performClick()
        composeRule.waitUntil(2_000L) { navigatedSession != null }

        composeRule.runOnIdle {
            val request = checkNotNull(capturedRequest)
            assertTrue(request.snapshot.openingMessage.isNotBlank())
            assertEquals("会追问的学习者", request.snapshot.learnerRole)
            assertEquals(sessionItem(request).id, navigatedSession?.id)
        }
    }

    private fun sessionItem(request: NewSessionCreateRequest) = SessionListItem(
        id = "session-${request.attemptId}",
        title = request.snapshot.title,
        updatedAtEpochMillis = 1L,
        pinned = false,
        statusLabel = request.snapshot.openingMessage,
        unreadCount = 0,
        avatarLabel = request.snapshot.title.take(1),
        learnerRole = request.snapshot.learnerRole
    )
}

private class FakeNewSessionPersistence : NewSessionPersistence {
    private var drafts = emptyList<NewSessionDraftRecord>()
    private var favorites = emptyList<NewSessionFavorite>()
    private val sessions = mutableMapOf<String, NewSessionConfiguration>()

    override fun loadDrafts(): List<NewSessionDraftRecord> = drafts
    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) { this.drafts = drafts }
    override fun loadFavorites(): List<NewSessionFavorite> = favorites
    override fun replaceFavorites(favorites: List<NewSessionFavorite>) { this.favorites = favorites }
    override fun promoteDraft(draftId: String, sessionId: String, snapshot: NewSessionConfiguration) {
        drafts = drafts.filterNot { it.id == draftId }
        sessions[sessionId] = snapshot
    }
    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? = sessions[sessionId]
}
