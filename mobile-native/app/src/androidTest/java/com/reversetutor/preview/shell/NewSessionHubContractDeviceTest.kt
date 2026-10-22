package com.reversetutor.preview.shell

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionCreatePort
import com.reversetutor.feature.chat.NewSessionCreateRequest
import com.reversetutor.feature.chat.NewSessionCreated
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.SessionListItem
import com.reversetutor.feature.chat.Task2B1NewSessionRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 2026-10-22 用户拍板（方案B）：新建会话不再有「自定义（六栏表单）」「草稿箱」。
 * 选预设 / 收藏 → 把那份配置直接交给对话式创建当开场素材。
 */
class NewSessionHubContractDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hubHasOnlyPresetAndFavoriteTabs() {
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
        // 六栏表单入口必须消失。
        composeRule.onAllNodesWithText("自定义").assertCountEquals(0)
        composeRule.onAllNodesWithText("基本资料").assertCountEquals(0)
        composeRule.onAllNodesWithText("创建并进入聊天").assertCountEquals(0)
    }

    @Test
    fun usePresetHandsConfigurationToConversationalCreation() {
        var seeded: NewSessionConfiguration? = null
        composeRule.setContent {
            ReverseTutorTheme {
                Task2B1NewSessionRoute(
                    createPort = NewSessionCreatePort { error("not used") },
                    persistence = FakeNewSessionPersistence(),
                    onCreated = {},
                    onStartCreation = { seeded = it }
                )
            }
        }

        composeRule.onNodeWithText("高三数学讲题冲刺").performClick()
        composeRule.onNodeWithText("使用此预设").assertIsDisplayed().performClick()
        composeRule.waitUntil(2_000L) { seeded != null }

        composeRule.runOnIdle {
            val configuration = checkNotNull(seeded)
            assertEquals("高三数学讲题冲刺", configuration.title)
            assertTrue(configuration.learnerRole.isNotBlank())
            assertTrue(configuration.openingMessage.isNotBlank())
        }
    }

    @Test
    fun createPortStillProducesNavigationItemForConversationalPath() {
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
                    onCreated = { navigatedSession = it },
                    onStartCreation = { configuration ->
                        // 模拟对话式创建走完一圈后落到 createPort。
                        val request = NewSessionCreateRequest(
                            attemptId = "seed",
                            draftId = "draft-seed",
                            snapshot = configuration
                        )
                        capturedRequest = request
                        navigatedSession = sessionItem(request)
                    }
                )
            }
        }

        composeRule.onNodeWithText("高三数学讲题冲刺").performClick()
        composeRule.onNodeWithText("使用此预设").performClick()
        composeRule.waitUntil(2_000L) { navigatedSession != null }

        composeRule.runOnIdle {
            assertNotNull(capturedRequest)
            assertTrue(checkNotNull(capturedRequest).snapshot.openingMessage.isNotBlank())
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
