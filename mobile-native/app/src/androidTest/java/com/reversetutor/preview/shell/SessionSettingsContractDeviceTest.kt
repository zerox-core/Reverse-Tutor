package com.reversetutor.preview.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.reversetutor.feature.chat.InMemorySessionSettingsStore
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.SessionSettingsCoordinator
import com.reversetutor.feature.chat.SessionSettingsDocument
import com.reversetutor.feature.chat.SessionSettingsScreen
import com.reversetutor.feature.chat.ChatScreen
import com.reversetutor.feature.chat.ChatUiState
import com.reversetutor.feature.chat.ChatComposerState
import com.reversetutor.feature.chat.SessionHomePort
import com.reversetutor.feature.chat.SessionListItem
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.SessionSource
import com.reversetutor.feature.chat.SourceReadState
import com.reversetutor.feature.chat.TagLibraryPersistence
import com.reversetutor.feature.chat.TagLibrarySnapshot
import com.reversetutor.preview.theme.ReverseTutorTheme
import com.reversetutor.preview.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SessionSettingsContractDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun groupedIndexBackBoundaryAndUnavailableDeleteAreRealComposeContracts() {
        var leftSettings = 0
        val coordinator = SessionSettingsCoordinator(
            sessionId = "session-a",
            initial = SessionSettingsDocument.fromSnapshot(
                NewSessionConfiguration(title = "概率论", learnerRole = "会追问的学生")
            ),
            initialSources = listOf(
                SessionSource(
                    id = "source-a",
                    displayName = "概率论讲义",
                    managedName = "概率论讲义",
                    typeLabel = "PDF",
                    readState = SourceReadState.Ready,
                    currentSessionReferenced = true,
                    referenceOwnerIds = listOf("session-a", "session-b"),
                    lastUsedAtEpochMillis = 42L,
                    preview = "条件概率",
                    bytesRetained = true
                )
            ),
            store = InMemorySessionSettingsStore()
        )
        composeRule.setContent {
            ReverseTutorTheme {
                SessionSettingsScreen(
                    coordinator = coordinator,
                    tagLibraryPersistence = EmptyTagLibraryPersistence,
                    onBack = { leftSettings += 1 }
                )
            }
        }

        listOf("基本资料", "学习目标与计划", "对话策略", "资料管理", "世界树配置", "危险操作").forEach {
            composeRule.onNodeWithText(it).assertIsDisplayed()
        }

        composeRule.onNodeWithText("世界树配置").performClick()
        composeRule.onNodeWithTag("session-settings-story").performTextReplacement("临时世界树")
        composeRule.runOnIdle { assertEquals("", coordinator.state.applied.snapshot.story) }
        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.runOnIdle {
            assertEquals("临时世界树", coordinator.state.applied.snapshot.story)
            assertEquals(0, leftSettings)
        }
        composeRule.onNodeWithText("会话设置").assertIsDisplayed()

        composeRule.onNodeWithText("资料管理").performClick()
        composeRule.onNodeWithText("概率论讲义").performClick()
        composeRule.onNodeWithText("删除资料文件").performClick()
        composeRule.onNodeWithText("当前版本暂不支持删除资料文件；引用、文件内容与读取状态都会保留。")
            .assertIsDisplayed()
    }

    @Test
    fun productionRouteHandlesSystemBackAsTextBoundaryThenLeavesSettings() {
        val persistence = DeviceNewSessionPersistence().apply {
            sessions["session-a"] = NewSessionConfiguration(title = "概率论", learnerRole = "会追问的学生")
        }
        var leftSettings = 0
        var importRetries = 0
        composeRule.setContent {
            ReverseTutorTheme {
                SessionSettingsRoute(
                    destination = AppDestination.SessionSettings,
                    sessionId = "session-a",
                    sessionTitle = "概率论",
                    sessionHomePort = DeviceSessionHomePort,
                    newSessionPersistence = persistence,
                    tagLibraryPersistence = EmptyTagLibraryPersistence,
                    sessionSettingsStore = InMemorySessionSettingsStore(),
                    externalImportError = "资料导入失败，请重试。",
                    onRetryImport = { importRetries += 1 },
                    onSelectDestination = {},
                    onOpenBrain = {},
                    onBack = { leftSettings += 1 }
                )
            }
        }

        composeRule.onNodeWithText("重试").performClick()
        composeRule.runOnIdle { assertEquals(1, importRetries) }
        composeRule.onNodeWithText("世界树配置").performClick()
        composeRule.onNodeWithTag("session-settings-story").performTextReplacement("系统返回提交")
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.onNodeWithText("会话设置").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("系统返回提交", persistence.sessions.getValue("session-a").story)
            assertEquals(0, leftSettings)
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.runOnIdle { assertEquals(1, leftSettings) }
    }

    @Test
    fun chatUiUsesSnapshotNameAndRemovesAvatarNodeWhenVisibilityIsOff() {
        val state = ChatUiState.from(
            sessionTitle = "概率论",
            records = emptyList(),
            composer = ChatComposerState(""),
            sessionSnapshot = NewSessionConfiguration(
                learnerDisplayName = "小概",
                learnerRole = "会追问",
                learnerImageRef = "content://avatar/current",
                avatarVisible = false
            )
        )
        composeRule.setContent {
            ReverseTutorTheme {
                ChatScreen(
                    state = state,
                    onComposerTextChange = {},
                    onSendMessage = {},
                    onCancelQuote = {},
                    onCreateImageDraft = {},
                    onCancelImageDraft = {},
                    onMessageAction = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("小概 · 会追问").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("学习者小概").assertCountEquals(0)
    }
}

private object EmptyTagLibraryPersistence : TagLibraryPersistence {
    override fun loadTagLibrary(): TagLibrarySnapshot? = null
    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) = Unit
}

private object DeviceSessionHomePort : SessionHomePort {
    override suspend fun loadSessionCards() = listOf(
        SessionListItem("session-a", "概率论", 1L, false, "", 0, "概", learnerRole = "会追问的学生")
    )
    override suspend fun renameSession(sessionId: String, title: String, nowEpochMillis: Long) = true
    override suspend fun setPinned(sessionId: String, pinned: Boolean, nowEpochMillis: Long) = true
    override suspend fun stageDelete(sessionId: String, nowEpochMillis: Long) = true
    override suspend fun undoDelete(sessionId: String) = true
    override suspend fun commitDelete(sessionId: String, nowEpochMillis: Long) = true
}

private class DeviceNewSessionPersistence : NewSessionPersistence {
    val sessions = mutableMapOf<String, NewSessionConfiguration>()
    override fun loadDrafts(): List<NewSessionDraftRecord> = emptyList()
    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) = Unit
    override fun loadFavorites(): List<NewSessionFavorite> = emptyList()
    override fun replaceFavorites(favorites: List<NewSessionFavorite>) = Unit
    override fun promoteDraft(draftId: String, sessionId: String, snapshot: NewSessionConfiguration) { sessions[sessionId] = snapshot }
    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? = sessions[sessionId]
    override fun saveSessionSnapshot(sessionId: String, snapshot: NewSessionConfiguration) { sessions[sessionId] = snapshot }
}
