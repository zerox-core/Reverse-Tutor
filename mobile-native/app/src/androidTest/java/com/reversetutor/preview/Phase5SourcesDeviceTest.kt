package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.sources.SourceImportInput
import com.reversetutor.feature.sources.SourcesRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5SourcesDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun seedSources() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
            val repository = DataModule.sourceRepository(context)
            repository.importSource(
                input = SourceImportInput(
                    requestId = 1L,
                    fileName = "notes.md",
                    mimeType = "text/markdown",
                    text = "# Alpha heading\n\nUseful source body.",
                    sourceId = "source-device-markdown"
                ),
                nowEpochMillis = 100L
            )
            repository.importSource(
                input = SourceImportInput(
                    requestId = 2L,
                    fileName = "archive.bin",
                    text = "",
                    sourceId = "source-device-unsupported"
                ),
                nowEpochMillis = 200L
            )
            repository.importSource(
                input = SourceImportInput(
                    requestId = 3L,
                    fileName = "partial.html",
                    mimeType = "text/html",
                    text = "<h1>Partial source</h1><script>ignored()</script>",
                    sourceId = "source-device-partial"
                ),
                nowEpochMillis = 300L
            )
            repository.importSource(
                input = SourceImportInput(
                    requestId = 4L,
                    fileName = "book.pdf",
                    mimeType = "application/pdf",
                    sourceId = "source-device-deferred"
                ),
                nowEpochMillis = 400L
            )
            repository.importSource(
                input = SourceImportInput(
                    requestId = 5L,
                    fileName = "empty.txt",
                    mimeType = "text/plain",
                    text = "",
                    sourceId = "source-device-failed"
                ),
                nowEpochMillis = 500L
            )
            repository.importSource(
                input = SourceImportInput(
                    requestId = 6L,
                    fileName = "question.png",
                    mimeType = "image/png",
                    sourceId = "source-device-image"
                ),
                nowEpochMillis = 600L
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
    fun sourcesLibraryShowsStatusMatrixAndOnlyOffersRealRecoveryActions() {
        composeRule.setContent {
            ReverseTutorTheme {
                SourcesRoute(
                    sourceRepository = DataModule.sourceRepository(context),
                    pendingImport = null,
                    highlightedSourceId = "source-device-markdown",
                    onPickSource = {}
                )
            }
        }

        composeRule.onNodeWithText("资料库").assertIsDisplayed()
        composeRule.onNodeWithText("解析状态").assertIsDisplayed()
        composeRule.onNodeWithText("notes.md").assertExists()
        assertTrue(composeRule.onAllNodesWithText("已解析").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Alpha heading", substring = true).assertExists()
        composeRule.onNodeWithText("archive.bin").assertExists()
        assertTrue(composeRule.onAllNodesWithText("暂不支持").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("partial.html").assertExists()
        assertTrue(composeRule.onAllNodesWithText("部分解析").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("book.pdf").assertExists()
        assertTrue(composeRule.onAllNodesWithText("等待能力").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("empty.txt").assertExists()
        assertTrue(composeRule.onAllNodesWithText("失败").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("question.png").assertExists()
        composeRule.onNodeWithText("聊天附件能力", substring = true).assertExists()

        composeRule.onNodeWithTag("source-card-source-device-markdown").assertIsSelected()
        composeRule.onNodeWithTag("source-action-source-device-markdown").assertExists()
        composeRule.onNodeWithTag("source-action-source-device-partial").assertExists()
        composeRule.onNodeWithTag("source-action-source-device-failed").assertExists()
        composeRule.onNodeWithTag("source-action-source-device-deferred").assertDoesNotExist()
        composeRule.onNodeWithTag("source-action-source-device-unsupported").assertDoesNotExist()
        composeRule.onNodeWithTag("source-action-source-device-image").assertDoesNotExist()

        composeRule.onNodeWithTag("source-action-source-device-markdown").performScrollTo().performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("最近导入：已解析")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
