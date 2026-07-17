package com.reversetutor.preview

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.feature.settings.FormalBatch6PreviewFixtures
import com.reversetutor.feature.settings.FormalDiagnosticReportScreen
import com.reversetutor.feature.settings.FormalDiagnosticsOverviewScreen
import com.reversetutor.feature.settings.FormalGlobalSearchScreen
import com.reversetutor.feature.settings.FormalImportExportScreen
import com.reversetutor.feature.settings.FormalImportPreviewScreen
import com.reversetutor.feature.settings.FormalPublicArticleScreen
import com.reversetutor.feature.settings.FormalSessionExportSelectionScreen
import com.reversetutor.feature.settings.FormalSyncConflictChoiceScreen
import com.reversetutor.feature.settings.FormalSyncConflictOverviewScreen
import com.reversetutor.feature.settings.FormalTokenByModelScreen
import com.reversetutor.feature.settings.FormalTokenBySessionScreen
import com.reversetutor.feature.settings.FormalTokenOverviewScreen
import com.reversetutor.feature.settings.FormalUpdateScreen
import com.reversetutor.preview.theme.ReverseTutorTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormalBatch6ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureSearchRecent() = capture("search-recent-717-1426.png") {
        FormalGlobalSearchScreen(
            state = FormalBatch6PreviewFixtures.searchRecent,
            onBack = {},
            onQueryChange = {},
            onSubmitSearch = {},
            onClearQuery = {},
            onClearRecentSearches = {},
            onRecentSearchClick = {},
            onResultClick = {}
        )
    }

    @Test
    fun captureSearchResults() = capture("search-results-717-1503.png") {
        FormalGlobalSearchScreen(
            state = FormalBatch6PreviewFixtures.searchResults,
            onBack = {},
            onQueryChange = {},
            onSubmitSearch = {},
            onClearQuery = {},
            onClearRecentSearches = {},
            onRecentSearchClick = {},
            onResultClick = {}
        )
    }

    @Test
    fun captureArticleTop() = capture("article-top-717-1587.png") {
        FormalPublicArticleScreen(FormalBatch6PreviewFixtures.article, {}, {})
    }

    @Test
    fun captureArticleContinuation() = capture("article-continuation-717-1651.png") {
        FormalPublicArticleScreen(FormalBatch6PreviewFixtures.articleContinuation, {}, {})
    }

    @Test
    fun captureImportExport() = capture("import-export-718-473.png") {
        FormalImportExportScreen(
            state = FormalBatch6PreviewFixtures.importExport,
            onBack = {},
            onChooseImportFile = {},
            onSelectSessions = {},
            onExportAllLocalData = {},
            onExportGlobalGraph = {},
            onOpenRecord = {},
            onOpenAllRecords = {}
        )
    }

    @Test
    fun captureImportPreview() = capture("import-preview-718-548.png") {
        FormalImportPreviewScreen(
            state = FormalBatch6PreviewFixtures.importPreview,
            onBack = {},
            onModeSelected = {},
            onSelectTargetSpace = {},
            onCancel = {},
            onStartImport = {}
        )
    }

    @Test
    fun captureSessionExportSelection() = capture("session-export-718-661.png") {
        FormalSessionExportSelectionScreen(
            state = FormalBatch6PreviewFixtures.sessionExport,
            onBack = {},
            onSearchQueryChange = {},
            onToggleSelectAll = {},
            onToggleSession = {},
            onCancel = {},
            onExportSelected = {}
        )
    }

    @Test
    fun captureDiagnosticsOverview() = capture("diagnostics-overview-718-756.png") {
        diagnostics(FormalBatch6PreviewFixtures.diagnostics)
    }

    @Test
    fun captureDiagnosticReport() = capture("diagnostics-report-718-869.png") {
        FormalDiagnosticReportScreen(FormalBatch6PreviewFixtures.diagnosticReport, {}, {}, {}, {})
    }

    @Test
    fun captureWipeConfirmation() = capture(
        fileName = "diagnostics-wipe-718-993.png",
        includePlatformWindows = true
    ) {
        diagnostics(FormalBatch6PreviewFixtures.diagnosticsWipe)
    }

    @Test
    fun captureTokenOverview() = capture("token-overview-718-1123.png") {
        FormalTokenOverviewScreen(FormalBatch6PreviewFixtures.tokenOverview, {}, {}, {}, {})
    }

    @Test
    fun captureTokenByModel() = capture("token-model-718-1208.png") {
        FormalTokenByModelScreen(FormalBatch6PreviewFixtures.tokenByModel, {}, {}, {})
    }

    @Test
    fun captureTokenBySession() = capture("token-session-718-1291.png") {
        FormalTokenBySessionScreen(FormalBatch6PreviewFixtures.tokenBySession, {}, {}, {}, {})
    }

    @Test
    fun captureUpdateCurrent() = capture("update-current-718-1392.png") {
        update(FormalBatch6PreviewFixtures.update)
    }

    @Test
    fun captureUpdateAvailable() = capture(
        fileName = "update-available-718-1460.png",
        includePlatformWindows = true
    ) {
        update(FormalBatch6PreviewFixtures.updateAvailable)
    }

    @Test
    fun captureSyncConflictOverview() = capture("sync-conflict-718-1556.png") {
        FormalSyncConflictOverviewScreen(FormalBatch6PreviewFixtures.syncConflict, {}, {}, {}, {})
    }

    @Test
    fun captureSyncConflictChoice() = capture("sync-choice-718-1636.png") {
        FormalSyncConflictChoiceScreen(FormalBatch6PreviewFixtures.syncChoice, {}, {}, {}, {})
    }

    @Composable
    private fun diagnostics(state: com.reversetutor.feature.settings.FormalDiagnosticsUiState) {
        FormalDiagnosticsOverviewScreen(state, {}, {}, {}, {}, {}, {}, {}, {})
    }

    @Composable
    private fun update(state: com.reversetutor.feature.settings.FormalUpdateUiState) {
        FormalUpdateScreen(state, {}, {}, {}, {}, {}, {}, {}, {})
    }

    private fun capture(
        fileName: String,
        includePlatformWindows: Boolean = false,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            ReverseTutorTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 390.dp, height = 884.dp)
                            .testTag(FixtureTag)
                    ) {
                        content()
                    }
                }
            }
        }
        val bitmap = composeRule.captureFormalFixture(
            fixtureTag = FixtureTag,
            includePlatformWindows = includePlatformWindows
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "formal-batch6").apply {
            check(mkdirs() || isDirectory)
        }
        FileOutputStream(File(directory, fileName)).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
    }

    private companion object {
        const val FixtureTag = "formal-batch6-fixture"
    }
}
