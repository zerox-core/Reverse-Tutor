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

        assertEquals("专注", state.themeLabel)
        assertEquals("隐藏", state.avatarVisibilityLabel)
        assertEquals(
            listOf("Review weak concepts", "空白备忘", "Draft question"),
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

        assertEquals("Reverse Tutor Native 预览", diagnostics.appName)
        assertEquals("com.reversetutor.preview", diagnostics.packageName)
        assertTrue(diagnostics.lines.any { it.contains("本地优先 Android 数据") })
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
    fun localDataWipeUsesDangerCopyForA2Confirmation() {
        val state = LocalDataWipeUiState()

        assertEquals("擦除本地数据？", state.confirmationTitle)
        assertEquals("确认擦除", state.confirmLabel)
        assertEquals("取消", state.dismissLabel)
        assertTrue(state.confirmationBody.contains("会话"))
        assertTrue(state.confirmationBody.contains("Memory"))
        assertTrue(state.confirmationBody.contains("不可撤销"))
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
        assertEquals("导入到新空间", state.modeLabel)
        assertEquals("reverse_tutor_session_export_v1", state.schemaLabel)
        assertEquals("试运行完成", state.statusLabel)
        assertEquals("来源文件：session.json", state.sourceFileLabel)
        assertEquals("文档：会话导出", state.documentTypeLabel)
        assertEquals("识别：reverse_tutor_session_export_v1", state.detectStepLabel)
        assertEquals("校验：可导入", state.validationStepLabel)
        assertEquals("目标空间：新导入空间", state.targetSpaceLabel)
        assertTrue(state.canImport)
        assertTrue(state.insertedLines.contains("sessions：1"))
        assertTrue(state.skippedLines.contains("messages：0"))
        assertTrue(state.failedLines.contains("记录：0"))
        assertTrue(state.apiKeyHandlingLabel.contains("API Key"))
        assertTrue(state.nextActionLines.any { it.contains("写入") })
        assertTrue(state.protocolJson?.contains("native_import_result_v1") == true)
    }

    @Test
    fun importPipelineIdleCanTrackSelectedModeBeforeDryRun() {
        val state = ImportPipelineUiState.idle(NativeImportMode.Overwrite)

        assertEquals(NativeImportMode.Overwrite, state.selectedMode)
        assertEquals("覆盖当前空间", state.modeLabel)
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

        assertEquals("完整备份", ready.kindLabel)
        assertEquals("文档：完整备份", ready.documentLabel)
        assertEquals("backup.json", ready.targetFileName)
        assertEquals("导出已准备", ready.statusLabel)
        assertTrue(ready.keyHandlingLabel.contains("脱敏"))
        assertTrue(ready.summaryLines.contains("会话：0"))
        assertTrue(ready.nextActionLines.contains("共享导出"))
        assertTrue(ready.canShareOrSave)

        val unavailable = ExportPipelineUiState.unavailable("Open a session first.")

        assertEquals("导出不可用", unavailable.statusLabel)
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
        assertTrue(unseenReplacement.body.contains("JSON"))
        assertTrue(unseenReplacement.body.contains("不会直接迁移 IndexedDB"))
        assertTrue(unseenReplacement.body.contains("不会导入 API Key"))
        assertTrue(unseenReplacement.body.contains("追加、覆盖或新空间"))
    }
}
