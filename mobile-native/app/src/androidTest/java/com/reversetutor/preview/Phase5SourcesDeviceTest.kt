package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.sources.SourceImportInput
import com.reversetutor.feature.sources.SourcesRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
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
        }
    }

    @After
    fun cleanUpLocalData() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
    }

    @Test
    fun sourcesLibraryShowsParsedAndUnsupportedFilesWithReprocess() {
        composeRule.setContent {
            ReverseTutorTheme {
                SourcesRoute(
                    sourceRepository = DataModule.sourceRepository(context),
                    pendingImport = null,
                    onPickSource = {}
                )
            }
        }

        composeRule.onNodeWithText("资料库").assertIsDisplayed()
        composeRule.onNodeWithText("解析状态").assertIsDisplayed()
        composeRule.onNodeWithText("notes.md").assertIsDisplayed()
        composeRule.onNodeWithText("已解析").assertIsDisplayed()
        composeRule.onNodeWithText("Alpha heading", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("archive.bin").assertIsDisplayed()
        composeRule.onNodeWithText("暂不支持").assertIsDisplayed()

        composeRule.onNodeWithTag("source-action-source-device-markdown").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("最近导入：已解析")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
