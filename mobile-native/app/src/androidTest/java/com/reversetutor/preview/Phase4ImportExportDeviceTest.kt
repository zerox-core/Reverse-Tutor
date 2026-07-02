package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase4ImportExportDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun phase4ImportModesExportAndWipeWorkOnDevice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.reversetutor.preview", context.packageName)
        resetLocalData(context)

        openImportExport()
        enterImportJson()

        verifyImportMode(modeLabel = "Append")
        verifyImportMode(modeLabel = "New space")
        verifyOverwriteMode()

        openImportedSession()
        verifyExportsForActiveSession()
        verifyLocalDataWipe()
    }

    private fun resetLocalData(context: Context) {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
        composeRule.activityRule.scenario.recreate()
        waitForText("Sessions")
    }

    private fun openImportExport() {
        waitForText("Import/export")
        composeRule.onNodeWithText("Import/export").performScrollTo().performClick()
        waitForText("Migration preview")
    }

    private fun enterImportJson() {
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement(sessionExportJson)
    }

    private fun verifyImportMode(modeLabel: String) {
        composeRule.onNodeWithText(modeLabel).performScrollTo().performClick()
        composeRule.onNodeWithText("Dry run").performScrollTo().performClick()
        waitForText("Dry run")
        assertDisplayedAfterScroll("Mode: $modeLabel")
        assertDisplayedAfterScroll("reverse_tutor_session_export_v1")
        assertDisplayedAfterScroll("sessions: 1")
        assertDisplayedAfterScroll("messages: 1")
        composeRule.onNodeWithText("Import").performScrollTo().performClick()
        waitForText("Completed")
        assertDisplayedAfterScroll("Mode: $modeLabel")
        assertDisplayedAfterScroll("sessions: 1")
        assertDisplayedAfterScroll("messages: 1")
    }

    private fun verifyOverwriteMode() {
        composeRule.onNodeWithText("Overwrite").performScrollTo().performClick()
        composeRule.onNodeWithText("Dry run").performScrollTo().performClick()
        waitForText("Mode: Overwrite")
        assertDisplayedAfterScroll("sessions: 1")
        assertDisplayedAfterScroll("messages: 1")
        composeRule.onNodeWithText("Import").performScrollTo().performClick()
        waitForText("Overwrite import")
        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("OVERWRITE")
        composeRule.onAllNodesWithText("Overwrite").onLast().performClick()
        waitForText("Completed")
        assertDisplayedAfterScroll("Mode: Overwrite")
    }

    private fun openImportedSession() {
        composeRule.onNodeWithText("Sessions").performScrollTo().performClick()
        waitForText("Search sessions")
        composeRule.onAllNodes(hasSetTextAction()).onFirst().performTextReplacement("P4-import-fixture")
        waitForText("P4-import-fixture")
        composeRule.onAllNodesWithText("Open").onFirst().performClick()
        waitForText("Hello-from-P4-fixture")
        composeRule.onNodeWithText("You").assertIsDisplayed()
    }

    private fun verifyExportsForActiveSession() {
        openImportExport()
        composeRule.onNodeWithText("Current session").performScrollTo().performClick()
        waitForText("Export ready")
        assertDisplayedAfterScroll("Type: Current session")
        assertDisplayedAfterScroll("reverse-tutor-session-p4-session-1.json")
        composeRule.onNodeWithText("Share").assertIsEnabled()
        composeRule.onNodeWithText("Save").assertIsEnabled()

        composeRule.onNodeWithText("Full backup").performScrollTo().performClick()
        waitForText("reverse-tutor-full-backup.json")
        assertDisplayedAfterScroll("Type: Full backup")
        composeRule.onNodeWithText("Share").assertIsEnabled()
        composeRule.onNodeWithText("Save").assertIsEnabled()
    }

    private fun verifyLocalDataWipe() {
        composeRule.onNodeWithText("Settings").performScrollTo().performClick()
        waitForText("Settings preview")
        composeRule.onNodeWithText("Wipe local data").performScrollTo().performClick()
        waitForText("Type WIPE")
        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextReplacement("WIPE")
        composeRule.onNodeWithText("Wipe data").performClick()
        waitForText("Local data wiped. Default preview restored. Secrets removed: 0.")
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertDisplayedAfterScroll(text: String) {
        composeRule.onAllNodesWithText(text).onLast().performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val sessionExportJson =
            """{"schema":"reverse_tutor_session_export_v1","version":1,"type":"session_export","created_at":"2026-07-01T00:00:00Z","session":{"id":"p4-session-1","title":"P4-import-fixture"},"messages":[{"id":"p4-message-1","role":"user","text":"Hello-from-P4-fixture"}]}"""
    }
}
