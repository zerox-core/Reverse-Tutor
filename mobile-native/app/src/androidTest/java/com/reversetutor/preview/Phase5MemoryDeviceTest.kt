package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.memory.NoteInput
import com.reversetutor.feature.memory.ContextHubRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5MemoryDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun seedNote() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
            DataModule.memoryRepository(context).createNote(
                input = NoteInput(
                    title = "函数易错点",
                    body = "记住定义域限制。",
                    sourceMessageId = "message-memory"
                ),
                nowEpochMillis = 100L,
                noteId = "note-memory"
            )
        }
    }

    @After
    fun cleanUpLocalData() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
    }

    @Test
    fun persistedChatLinkedNoteIsVisibleInContextHub() {
        composeRule.setContent {
            ReverseTutorTheme {
                ContextHubRoute(
                    memoryRepository = DataModule.memoryRepository(context),
                    graphRepository = DataModule.graphRepository(context),
                    sessionId = "session-memory",
                    sessionTitle = "函数训练",
                    onOpenChat = {},
                    onOpenSources = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("随笔：1")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithText("随笔")
            .filterToOne(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
            )
            .performClick()
        composeRule.onNodeWithText("1 篇随笔").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("打开关联聊天证据").performScrollTo().assertIsDisplayed()
    }
}
