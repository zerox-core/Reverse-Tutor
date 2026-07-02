package com.reversetutor.feature.settings

import com.reversetutor.core.data.preferences.AppPreferences
import com.reversetutor.core.data.preferences.ThemePreference
import com.reversetutor.core.data.migration.NativeExportKind
import com.reversetutor.core.data.migration.NativeExportResult
import com.reversetutor.core.data.migration.NativeExportSnapshot
import com.reversetutor.core.data.migration.NativeImportMode
import com.reversetutor.core.data.migration.NativeImportResult
import com.reversetutor.core.data.migration.NativeImportStatus
import com.reversetutor.core.protocol.ProtocolDocumentType
import com.reversetutor.core.protocol.ProtocolValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsFoundationModelTest {
    @Test
    fun settingsStateSummarizesThemeAvatarAndMemoPlaceholders() {
        val state = SettingsUiState.from(
            preferences = AppPreferences(
                theme = ThemePreference.Focus,
                globalAvatarVisible = false,
                primaryMemo = "Review weak concepts",
                secondaryMemo = "",
                scratchMemo = "Draft question"
            ),
            diagnostics = NativeDiagnosticsInfo.preview(
                packageName = "com.reversetutor.preview",
                versionName = "0.1.0-native-preview",
                versionCode = 1
            )
        )

        assertEquals("Focus", state.themeLabel)
        assertEquals("Hidden", state.avatarVisibilityLabel)
        assertEquals(
            listOf("Review weak concepts", "Empty memo slot", "Draft question"),
            state.memoPreviewLines
        )
    }

    @Test
    fun aboutDiagnosticsUseNativeMetadataWithoutPwaInstallHints() {
        val diagnostics = NativeDiagnosticsInfo.preview(
            packageName = "com.reversetutor.preview",
            versionName = "0.1.0-native-preview",
            versionCode = 1
        )

        assertEquals("Reverse Tutor Native Preview", diagnostics.appName)
        assertEquals("com.reversetutor.preview", diagnostics.packageName)
        assertTrue(diagnostics.lines.any { it.contains("Local-first Android data") })
        assertFalse(diagnostics.containsPwaInstallHints())
    }

    @Test
    fun localDataWipeRequiresExactConfirmationPhrase() {
        val state = LocalDataWipeUiState()

        assertFalse(state.canConfirm(""))
        assertFalse(state.canConfirm("wipe"))
        assertFalse(state.canConfirm(" WIPE all "))
        assertTrue(state.canConfirm(" WIPE "))
    }

    @Test
    fun importPipelineStateEnablesWriteOnlyAfterCleanDryRun() {
        val state = ImportPipelineUiState.from(
            NativeImportResult(
                batchId = "dry-run",
                sourceFileName = "session.json",
                sourceSchema = "reverse_tutor_session_export_v1",
                documentType = ProtocolDocumentType.SessionExport,
                mode = NativeImportMode.NewSpace,
                status = NativeImportStatus.DryRun,
                insertedCounts = mapOf("sessions" to 1, "messages" to 1),
                skippedCounts = mapOf("messages" to 0),
                warnings = emptyList(),
                errors = emptyList(),
                startedAtEpochMillis = 1L,
                completedAtEpochMillis = null
            )
        )

        assertEquals("session.json", state.selectedFileName)
        assertEquals(NativeImportMode.NewSpace, state.selectedMode)
        assertEquals("New space", state.modeLabel)
        assertEquals("reverse_tutor_session_export_v1", state.schemaLabel)
        assertEquals("Dry run", state.statusLabel)
        assertEquals("Source file: session.json", state.sourceFileLabel)
        assertEquals("Document: Session export", state.documentTypeLabel)
        assertEquals("Detect: reverse_tutor_session_export_v1", state.detectStepLabel)
        assertEquals("Validate: ready", state.validationStepLabel)
        assertEquals("Target space: new imported space", state.targetSpaceLabel)
        assertTrue(state.canImport)
        assertTrue(state.insertedLines.contains("sessions: 1"))
        assertTrue(state.skippedLines.contains("messages: 0"))
        assertTrue(state.failedLines.contains("records: 0"))
        assertTrue(state.apiKeyHandlingLabel.contains("API keys"))
        assertTrue(state.nextActionLines.any { it.contains("write") })
        assertTrue(state.protocolJson?.contains("native_import_result_v1") == true)
    }

    @Test
    fun importPipelineIdleCanTrackSelectedModeBeforeDryRun() {
        val state = ImportPipelineUiState.idle(NativeImportMode.Overwrite)

        assertEquals(NativeImportMode.Overwrite, state.selectedMode)
        assertEquals("Overwrite", state.modeLabel)
        assertEquals("OVERWRITE", ImportPipelineUiState.overwriteConfirmationPhrase)
        assertFalse(state.canImport)
    }

    @Test
    fun exportPipelineStateEnablesShareAndSaveOnlyForValidJson() {
        val ready = ExportPipelineUiState.from(
            NativeExportResult(
                kind = NativeExportKind.FullBackup,
                targetFileName = "backup.json",
                documentType = ProtocolDocumentType.FullBackup,
                json = """{"schema":"reverse_tutor_full_backup_v1"}""",
                snapshot = NativeExportSnapshot(),
                validation = ProtocolValidationResult(
                    schema = "reverse_tutor_full_backup_v1",
                    version = 1,
                    documentType = ProtocolDocumentType.FullBackup,
                    errors = emptyList(),
                    warnings = emptyList(),
                    redactedJson = "{}"
                ),
                warnings = emptyList(),
                errors = emptyList()
            )
        )

        assertEquals("Full backup", ready.kindLabel)
        assertEquals("Document: Full backup", ready.documentLabel)
        assertEquals("backup.json", ready.targetFileName)
        assertEquals("Export ready", ready.statusLabel)
        assertTrue(ready.keyHandlingLabel.contains("excluded"))
        assertTrue(ready.summaryLines.contains("sessions: 0"))
        assertTrue(ready.nextActionLines.contains("Share export"))
        assertTrue(ready.canShareOrSave)

        val unavailable = ExportPipelineUiState.unavailable("Open a session first.")

        assertEquals("Export unavailable", unavailable.statusLabel)
        assertFalse(unavailable.canShareOrSave)
        assertTrue(unavailable.errors.single().contains("Open a session"))
    }

    @Test
    fun firstLaunchImportPromptOnlyShowsForUnseenReplacementBuilds() {
        val preview = FirstLaunchImportPromptUiState.preview()
        val unseenReplacement = FirstLaunchImportPromptUiState.replacementBuild(alreadySeen = false)
        val seenReplacement = FirstLaunchImportPromptUiState.replacementBuild(alreadySeen = true)

        assertFalse(preview.shouldShow)
        assertTrue(unseenReplacement.shouldShow)
        assertFalse(seenReplacement.shouldShow)
        assertTrue(unseenReplacement.body.contains("export JSON"))
        assertTrue(unseenReplacement.body.contains("Direct IndexedDB migration is not used"))
        assertTrue(unseenReplacement.body.contains("API keys are not imported"))
        assertTrue(unseenReplacement.body.contains("append, overwrite, or new space"))
    }
}
