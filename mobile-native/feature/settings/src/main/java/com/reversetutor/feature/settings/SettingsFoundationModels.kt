package com.reversetutor.feature.settings

import com.reversetutor.core.data.preferences.AppPreferences
import com.reversetutor.core.data.preferences.ThemePreference
import com.reversetutor.core.data.migration.NativeImportMode
import com.reversetutor.core.data.migration.NativeExportKind
import com.reversetutor.core.data.migration.NativeExportResult
import com.reversetutor.core.data.migration.NativeExportSnapshot
import com.reversetutor.core.data.migration.NativeImportResult
import com.reversetutor.core.data.migration.NativeImportStatus
import com.reversetutor.core.llm.LlmConnectionResult
import com.reversetutor.core.llm.LlmProviderPreset
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.protocol.ProtocolDocumentType

data class SettingsUiState(
    val themeLabel: String,
    val avatarVisibilityLabel: String,
    val memoPreviewLines: List<String>,
    val diagnostics: NativeDiagnosticsInfo
) {
    companion object {
        fun from(
            preferences: AppPreferences,
            diagnostics: NativeDiagnosticsInfo
        ): SettingsUiState = SettingsUiState(
            themeLabel = preferences.theme.label,
            avatarVisibilityLabel = if (preferences.globalAvatarVisible) "Visible" else "Hidden",
            memoPreviewLines = listOf(
                preferences.primaryMemo,
                preferences.secondaryMemo,
                preferences.scratchMemo
            ).map { it.ifBlank { "Empty memo slot" } },
            diagnostics = diagnostics
        )
    }
}

data class LlmProfileSettingsUiState(
    val summary: String,
    val presetLabels: List<String>,
    val profileItems: List<LlmProfileItem>,
    val connectionStatusLabel: String,
    val presets: List<LlmProviderPreset>
) {
    companion object {
        fun from(
            profiles: List<LlmProfile>,
            presets: List<LlmProviderPreset>,
            connectionResult: LlmConnectionResult?
        ): LlmProfileSettingsUiState {
            val summary = when (profiles.size) {
                0 -> "No profiles"
                1 -> "1 profile"
                else -> "${profiles.size} profiles"
            }
            return LlmProfileSettingsUiState(
                summary = summary,
                presetLabels = presets.map { it.label },
                profileItems = profiles.map { profile ->
                    LlmProfileItem(
                        id = profile.id,
                        name = profile.name,
                        providerModelLabel = "${profile.provider.name} | ${profile.model}",
                        baseUrlLabel = profile.baseUrl ?: "No base URL",
                        keyStatusLabel = if (profile.secretRef == null) "No key saved" else "Key saved",
                        active = profile.enabled
                    )
                },
                connectionStatusLabel = when (connectionResult) {
                    is LlmConnectionResult.Success -> connectionResult.message
                    is LlmConnectionResult.Failure -> connectionResult.message
                    null -> "Not tested"
                },
                presets = presets
            )
        }
    }
}

data class LlmProfileItem(
    val id: String,
    val name: String,
    val providerModelLabel: String,
    val baseUrlLabel: String,
    val keyStatusLabel: String,
    val active: Boolean
)

data class LocalDataWipeUiState(
    val requiredPhrase: String = "WIPE",
    val statusLabel: String? = null
) {
    fun canConfirm(input: String): Boolean =
        input.trim() == requiredPhrase
}

data class ImportPipelineUiState(
    val selectedFileName: String? = null,
    val selectedMode: NativeImportMode = NativeImportMode.Append,
    val modeLabel: String = NativeImportMode.Append.label,
    val sourceFileLabel: String = "Source file: not selected",
    val documentTypeLabel: String = "Document: not detected",
    val detectStepLabel: String = "Detect: waiting for JSON",
    val validationStepLabel: String = "Validate: run dry run",
    val schemaLabel: String = "No schema",
    val statusLabel: String = "No import result",
    val canImport: Boolean = false,
    val apiKeyHandlingLabel: String = importApiKeyHandlingCopy,
    val targetSpaceLabel: String = NativeImportMode.Append.targetSpaceLabel,
    val insertedLines: List<String> = emptyList(),
    val skippedLines: List<String> = emptyList(),
    val failedLines: List<String> = listOf("records: 0"),
    val nextActionLines: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val protocolJson: String? = null
) {
    companion object {
        const val overwriteConfirmationPhrase = "OVERWRITE"
        const val importApiKeyHandlingCopy =
            "API keys and secret material are not imported. Files containing key fields fail validation."

        fun idle(mode: NativeImportMode = NativeImportMode.Append): ImportPipelineUiState =
            ImportPipelineUiState(
                selectedMode = mode,
                modeLabel = mode.label,
                targetSpaceLabel = mode.targetSpaceLabel
            )

        fun from(result: NativeImportResult): ImportPipelineUiState =
            ImportPipelineUiState(
                selectedFileName = result.sourceFileName,
                selectedMode = result.mode,
                modeLabel = result.mode.label,
                sourceFileLabel = "Source file: ${result.sourceFileName}",
                documentTypeLabel = "Document: ${result.documentType.labelOrFallback()}",
                detectStepLabel = "Detect: ${result.sourceSchema}",
                validationStepLabel = if (result.errors.isEmpty()) {
                    "Validate: ready"
                } else {
                    "Validate: blocked"
                },
                schemaLabel = result.sourceSchema,
                statusLabel = result.status.label,
                canImport = result.status == NativeImportStatus.DryRun && result.canWrite,
                targetSpaceLabel = result.mode.targetSpaceLabel,
                insertedLines = result.insertedCounts.toCountLines(),
                skippedLines = result.skippedCounts.toCountLines(),
                failedLines = listOf("records: ${result.errors.size}"),
                nextActionLines = result.nextActionLines(),
                warnings = result.warnings,
                errors = result.errors,
                protocolJson = result.toProtocolJson()
            )
    }
}

data class ExportPipelineUiState(
    val kindLabel: String = "No export",
    val documentLabel: String = "Document: not prepared",
    val targetFileName: String? = null,
    val statusLabel: String = "No export result",
    val canShareOrSave: Boolean = false,
    val keyHandlingLabel: String = exportKeyHandlingCopy,
    val summaryLines: List<String> = emptyList(),
    val nextActionLines: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val json: String? = null
) {
    companion object {
        const val exportKeyHandlingCopy =
            "API keys, secret refs, tokens, and passwords are excluded or redacted by default."

        fun idle(): ExportPipelineUiState = ExportPipelineUiState()

        fun from(result: NativeExportResult): ExportPipelineUiState =
            ExportPipelineUiState(
                kindLabel = result.kind.label,
                documentLabel = "Document: ${result.documentType.labelOrFallback()}",
                targetFileName = result.targetFileName,
                statusLabel = if (result.isValid) "Export ready" else "Export failed",
                canShareOrSave = result.isValid && !result.json.isNullOrBlank(),
                summaryLines = result.snapshot.toSummaryLines(),
                nextActionLines = if (result.isValid) {
                    listOf("Share export", "Save export")
                } else {
                    listOf("Review errors and prepare export again")
                },
                warnings = result.warnings,
                errors = result.errors,
                json = result.json
            )

        fun unavailable(message: String, kindLabel: String = "No export"): ExportPipelineUiState =
            ExportPipelineUiState(
                kindLabel = kindLabel,
                statusLabel = "Export unavailable",
                nextActionLines = listOf("Use a supported export type or return after the owning feature lands."),
                errors = listOf(message)
            )

        fun saved(targetFileName: String?): ExportPipelineUiState =
            ExportPipelineUiState(
                kindLabel = "Saved export",
                documentLabel = "Document: saved JSON",
                targetFileName = targetFileName,
                statusLabel = "Export saved",
                canShareOrSave = false,
                nextActionLines = listOf("Return to Sessions or prepare another export")
            )
    }
}

data class FirstLaunchImportPromptUiState(
    val enabledForReplacementBuild: Boolean,
    val alreadySeen: Boolean,
    val title: String = "Import existing data",
    val body: String = "Import an export JSON from your existing version. Direct IndexedDB migration is not used. API keys are not imported; add them again in LLM profiles. After validation, choose append, overwrite, or new space.",
    val confirmLabel: String = "Choose import",
    val dismissLabel: String = "Later"
) {
    val shouldShow: Boolean
        get() = enabledForReplacementBuild && !alreadySeen

    companion object {
        fun preview(): FirstLaunchImportPromptUiState =
            FirstLaunchImportPromptUiState(
                enabledForReplacementBuild = false,
                alreadySeen = false
            )

        fun replacementBuild(alreadySeen: Boolean): FirstLaunchImportPromptUiState =
            FirstLaunchImportPromptUiState(
                enabledForReplacementBuild = true,
                alreadySeen = alreadySeen
            )
    }
}

data class NativeDiagnosticsInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val dataNotice: String,
    val supportChannel: String,
    val distribution: String
) {
    val lines: List<String>
        get() = listOf(
            "Package: $packageName",
            "Version: $versionName ($versionCode)",
            dataNotice,
            "Support: $supportChannel",
            "Distribution: $distribution"
        )

    fun containsPwaInstallHints(): Boolean {
        val forbidden = Regex("pwa|capacitor|install to home|add to home|browser install", RegexOption.IGNORE_CASE)
        return lines.any { forbidden.containsMatchIn(it) }
    }

    companion object {
        fun preview(
            packageName: String,
            versionName: String,
            versionCode: Int
        ): NativeDiagnosticsInfo = NativeDiagnosticsInfo(
            appName = "Reverse Tutor Native Preview",
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            dataNotice = "Local-first Android data uses Room and DataStore.",
            supportChannel = "Support channel will be configured before replacement release.",
            distribution = "Internal debug preview"
        )
    }
}

private val ThemePreference.label: String
    get() = when (this) {
        ThemePreference.System -> "System"
        ThemePreference.Light -> "Light"
        ThemePreference.Dark -> "Dark"
        ThemePreference.Focus -> "Focus"
    }

private fun Map<String, Int>.toCountLines(): List<String> =
    entries.map { (name, count) -> "$name: $count" }

private fun NativeImportResult.nextActionLines(): List<String> =
    when (status) {
        NativeImportStatus.DryRun -> listOf(
            "Choose import mode and write when the summary is correct.",
            "Overwrite requires the ${ImportPipelineUiState.overwriteConfirmationPhrase} confirmation."
        )
        NativeImportStatus.Completed -> listOf(
            "Open imported sessions from Sessions.",
            "Keep this result JSON for an audit trail if needed."
        )
        NativeImportStatus.Partial -> listOf(
            "Review skipped and failed records before continuing.",
            "Open imported sessions only after checking warnings."
        )
        NativeImportStatus.Failed -> listOf(
            "Fix the file or choose another export JSON.",
            "No records were written when validation failed."
        )
    }

private val NativeImportMode.targetSpaceLabel: String
    get() = when (this) {
        NativeImportMode.Append -> "Target space: current space, append records"
        NativeImportMode.Overwrite -> "Target space: current space, replace before import"
        NativeImportMode.NewSpace -> "Target space: new imported space"
    }

private val NativeExportKind.label: String
    get() = when (this) {
        NativeExportKind.CurrentSession -> "Current session"
        NativeExportKind.GraphSnapshot -> "Graph snapshot"
        NativeExportKind.FullBackup -> "Full backup"
    }

private fun NativeExportSnapshot.toSummaryLines(): List<String> =
    buildList {
        add("sessions: ${sessions.size}")
        add("messages: ${messages.size}")
        add("llm_profiles: ${llmProfiles.size}")
        graph?.let {
            add("graph_nodes: ${it.nodes.size}")
            add("graph_edges: ${it.edges.size}")
        }
    }

private fun ProtocolDocumentType?.labelOrFallback(): String =
    when (this) {
        ProtocolDocumentType.LegacyExport -> "Legacy export"
        ProtocolDocumentType.FullBackup -> "Full backup"
        ProtocolDocumentType.SessionExport -> "Session export"
        ProtocolDocumentType.GraphSnapshot -> "Graph snapshot"
        ProtocolDocumentType.Preset -> "Preset"
        ProtocolDocumentType.LlmProfile -> "LLM profile"
        ProtocolDocumentType.ImportResult -> "Import result"
        null -> "not detected"
    }
