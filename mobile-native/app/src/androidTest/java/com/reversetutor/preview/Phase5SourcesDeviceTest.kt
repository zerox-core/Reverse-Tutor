package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.sources.SourceImportInput
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5SourcesDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun sourcesLibraryShowsParsedAndUnsupportedFilesWithReprocess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resetAndSeedSources(context)

        waitForText("Sources")
        composeRule.onNodeWithText("Sources").performScrollTo().performClick()
        waitForText("Parser status")

        composeRule.onNodeWithText("notes.md").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("supported_local").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Alpha heading").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("source-action-source-device-markdown")
            .performScrollTo()
            .performClick()
        waitForText("Last import: supported_local")

        composeRule.onNodeWithText("archive.bin").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("unsupported").performScrollTo().assertIsDisplayed()
    }

    private fun resetAndSeedSources(context: Context) {
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
        composeRule.activityRule.scenario.recreate()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
