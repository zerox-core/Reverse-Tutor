package com.reversetutor.feature.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.data.llm.LlmProfileInput
import com.reversetutor.core.data.migration.NativeImportMode
import com.reversetutor.core.llm.LlmProviderPreset
import com.reversetutor.core.model.LlmProviderKind

@Composable
fun SettingsFoundationScreen(
    state: SettingsUiState,
    onOpenAbout: () -> Unit,
    llmProfileState: LlmProfileSettingsUiState = LlmProfileSettingsUiState.from(
        profiles = emptyList(),
        presets = LlmProviderPreset.defaults,
        connectionResult = null
    ),
    onSaveLlmProfile: (LlmProfileInput) -> Unit = {},
    onActivateLlmProfile: (String) -> Unit = {},
    onDeleteLlmProfile: (String) -> Unit = {},
    onTestLlmProfile: (String) -> Unit = {},
    localDataWipeState: LocalDataWipeUiState = LocalDataWipeUiState(),
    onWipeLocalData: () -> Unit = {},
    onReturnToSessions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val firstPreset = llmProfileState.presets.firstOrNull()
    var profileName by remember { mutableStateOf("") }
    var selectedProvider by remember {
        mutableStateOf(firstPreset?.provider ?: LlmProviderKind.OpenAiCompatible)
    }
    var selectedPresetId by remember { mutableStateOf(firstPreset?.id) }
    var model by remember { mutableStateOf(firstPreset?.model.orEmpty()) }
    var baseUrl by remember { mutableStateOf(firstPreset?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<LlmProfileItem?>(null) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var wipeConfirmation by remember { mutableStateOf("") }
    val saveEnabled = profileName.trim().isNotEmpty() && model.trim().isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Settings foundation",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "DataStore-backed preview",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        SettingsInfoRow(label = "Theme", value = state.themeLabel)
        SettingsInfoRow(label = "Avatar", value = state.avatarVisibilityLabel)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Global memos",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        state.memoPreviewLines.forEach { memo ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = memo,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onOpenAbout) {
            Text("About diagnostics")
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "LLM profiles",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = llmProfileState.summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Presets",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            llmProfileState.presets.forEach { preset ->
                val selected = preset.id == selectedPresetId
                Surface(
                    onClick = {
                        selectedPresetId = preset.id
                        selectedProvider = preset.provider
                        model = preset.model
                        baseUrl = preset.baseUrl.orEmpty()
                        if (profileName.isBlank()) {
                            profileName = preset.label
                        }
                    },
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = preset.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = profileName,
            onValueChange = { profileName = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Profile name") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsInfoRow(label = "Provider", value = selectedProvider.name)
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Base URL") },
            singleLine = true
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                onSaveLlmProfile(
                    LlmProfileInput(
                        name = profileName,
                        provider = selectedProvider,
                        model = model,
                        baseUrl = baseUrl,
                        apiKey = apiKey
                    )
                )
                profileName = ""
                apiKey = ""
            },
            enabled = saveEnabled
        ) {
            Text("Save profile")
        }

        Spacer(modifier = Modifier.height(18.dp))
        llmProfileState.profileItems.forEach { item ->
            LlmProfileCard(
                item = item,
                onActivate = { onActivateLlmProfile(item.id) },
                onTest = { onTestLlmProfile(item.id) },
                onDelete = { pendingDelete = item }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (llmProfileState.profileItems.isNotEmpty()) {
            Text(
                text = llmProfileState.connectionStatusLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Local data",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Clear Room data, saved profiles, local secrets, and preview memos before restoring the default preview state.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        val wipeStatusLabel = localDataWipeState.statusLabel
        if (wipeStatusLabel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = wipeStatusLabel,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Safe empty-state return is ready from Sessions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onReturnToSessions) {
                Text("Return to Sessions")
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = { showWipeDialog = true }) {
            Text("Wipe local data")
        }
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete profile") },
            text = { Text(deleteTarget.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLlmProfile(deleteTarget.id)
                        pendingDelete = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = {
                showWipeDialog = false
                wipeConfirmation = ""
            },
            title = { Text("Wipe local data") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This clears local user data and restores the default preview state.")
                    OutlinedTextField(
                        value = wipeConfirmation,
                        onValueChange = { wipeConfirmation = it },
                        singleLine = true,
                        label = { Text("Type ${localDataWipeState.requiredPhrase}") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = localDataWipeState.canConfirm(wipeConfirmation),
                    onClick = {
                        showWipeDialog = false
                        wipeConfirmation = ""
                        onWipeLocalData()
                    }
                ) {
                    Text("Wipe data")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showWipeDialog = false
                        wipeConfirmation = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportExportScreen(
    state: ImportPipelineUiState,
    exportState: ExportPipelineUiState = ExportPipelineUiState.idle(),
    importText: String,
    canExportCurrentSession: Boolean = false,
    onImportTextChange: (String) -> Unit,
    onImportModeChange: (NativeImportMode) -> Unit,
    onPickFile: () -> Unit,
    onDryRun: () -> Unit,
    onImport: (overwriteConfirmed: Boolean) -> Unit,
    onExportCurrentSession: () -> Unit = {},
    onExportGraphSnapshot: () -> Unit = {},
    onExportFullBackup: () -> Unit = {},
    onExportPreset: () -> Unit = {},
    onShareExport: () -> Unit = {},
    onSaveExport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var overwriteConfirmation by remember { mutableStateOf("") }
    val spacing = 12.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "Import/export",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(spacing))
        MigrationNotice()

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Import data",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        ImportFlowSteps(state = state, importText = importText)
        Spacer(modifier = Modifier.height(12.dp))
        ImportSourceSummary(state = state)
        Spacer(modifier = Modifier.height(12.dp))
        ImportModeSelector(
            selectedMode = state.selectedMode,
            onImportModeChange = onImportModeChange
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Mode: ${state.modeLabel}",
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onPickFile) {
                Text("Choose JSON")
            }
            TextButton(
                enabled = importText.isNotBlank(),
                onClick = onDryRun
            ) {
                Text("Dry run")
            }
            Button(
                enabled = state.canImport,
                onClick = {
                    if (state.selectedMode == NativeImportMode.Overwrite) {
                        showOverwriteDialog = true
                    } else {
                        onImport(false)
                    }
                }
            ) {
                Text("Import")
            }
        }
        if (state.selectedMode == NativeImportMode.Overwrite) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Overwrite replaces the current target space and requires ${ImportPipelineUiState.overwriteConfirmationPhrase}.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = importText,
            onValueChange = onImportTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            maxLines = 12,
            label = { Text("Import JSON") }
        )
        Spacer(modifier = Modifier.height(18.dp))
        ImportResultPanel(state)

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "Export",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = exportState.keyHandlingLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = canExportCurrentSession,
                onClick = onExportCurrentSession
            ) {
                Text("Current session")
            }
            Button(onClick = onExportGraphSnapshot) {
                Text("Graph snapshot")
            }
            Button(onClick = onExportFullBackup) {
                Text("Full backup")
            }
            TextButton(onClick = onExportPreset) {
                Text("Preset")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                enabled = exportState.canShareOrSave,
                onClick = onShareExport
            ) {
                Text("Share")
            }
            TextButton(
                enabled = exportState.canShareOrSave,
                onClick = onSaveExport
            ) {
                Text("Save")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = exportState.targetFileName ?: "No export prepared",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(12.dp))
        ExportResultPanel(exportState)
    }

    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = {
                showOverwriteDialog = false
                overwriteConfirmation = ""
            },
            title = { Text("Overwrite import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This replaces local data in the target space before importing this file.")
                    OutlinedTextField(
                        value = overwriteConfirmation,
                        onValueChange = { overwriteConfirmation = it },
                        singleLine = true,
                        label = { Text("Type ${ImportPipelineUiState.overwriteConfirmationPhrase}") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = overwriteConfirmation.trim() == ImportPipelineUiState.overwriteConfirmationPhrase,
                    onClick = {
                        showOverwriteDialog = false
                        overwriteConfirmation = ""
                        onImport(true)
                    }
                ) {
                    Text("Overwrite")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverwriteDialog = false
                        overwriteConfirmation = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MigrationNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Migration preview",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Use exported JSON only. Direct IndexedDB migration is not used. API keys stay out of imports and exports.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportFlowSteps(
    state: ImportPipelineUiState,
    importText: String
) {
    val hasSource = state.selectedFileName != null || importText.isNotBlank()
    val hasResult = state.statusLabel != "No import result"
    val steps = listOf(
        "Select" to hasSource,
        "Detect" to hasResult,
        "Validate" to hasResult,
        "Dry-run" to (state.statusLabel == "Dry run" || state.canImport),
        "Mode" to true,
        "Confirm" to (state.selectedMode != NativeImportMode.Overwrite || hasResult),
        "Write" to (state.statusLabel == "Completed" || state.statusLabel == "Partial"),
        "Result" to hasResult
    )

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        steps.forEach { (label, active) ->
            StatusChip(label = label, active = active)
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    active: Boolean
) {
    Surface(
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ImportSourceSummary(state: ImportPipelineUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = state.sourceFileLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.documentTypeLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.detectStepLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.validationStepLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.apiKeyHandlingLabel, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImportModeSelector(
    selectedMode: NativeImportMode,
    onImportModeChange: (NativeImportMode) -> Unit
) {
    Text(
        text = "Mode",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Current mode: ${selectedMode.label}",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NativeImportMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            if (selected) {
                Button(onClick = { onImportModeChange(mode) }) {
                    Text(mode.label)
                }
            } else {
                TextButton(onClick = { onImportModeChange(mode) }) {
                    Text(mode.label)
                }
            }
        }
    }
}

@Composable
private fun ExportResultPanel(state: ExportPipelineUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = state.statusLabel,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Type: ${state.kindLabel}", style = MaterialTheme.typography.bodyMedium)
            Text(text = state.documentLabel, style = MaterialTheme.typography.bodyMedium)
            state.targetFileName?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            ResultLines(title = "Summary", lines = state.summaryLines)
            ResultLines(title = "Warnings", lines = state.warnings)
            ResultLines(title = "Errors", lines = state.errors)
            ResultLines(title = "Next actions", lines = state.nextActionLines)
        }
    }
}

@Composable
private fun ImportResultPanel(state: ImportPipelineUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = state.statusLabel,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Text(text = "Selected mode: ${state.modeLabel}", style = MaterialTheme.typography.bodyMedium)
            Text(text = state.targetSpaceLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.sourceFileLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.schemaLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.documentTypeLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.apiKeyHandlingLabel, style = MaterialTheme.typography.bodyMedium)
            ResultLines(title = "Inserted", lines = state.insertedLines)
            ResultLines(title = "Skipped", lines = state.skippedLines)
            ResultLines(title = "Failed", lines = state.failedLines)
            ResultLines(title = "Warnings", lines = state.warnings)
            ResultLines(title = "Errors", lines = state.errors)
            ResultLines(title = "Next actions", lines = state.nextActionLines)
        }
    }
}

@Composable
private fun ResultLines(title: String, lines: List<String>) {
    if (lines.isEmpty()) return
    Text(
        text = title,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
    lines.forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun LlmProfileCard(
    item: LlmProfileItem,
    onActivate: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.providerModelLabel,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = if (item.active) "Active" else "Inactive",
                    color = if (item.active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = item.baseUrlLabel, fontSize = 13.sp)
            Text(text = item.keyStatusLabel, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!item.active) {
                    TextButton(onClick = onActivate) {
                        Text("Set active")
                    }
                }
                TextButton(onClick = onTest) {
                    Text("Test")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
fun AboutDiagnosticsScreen(
    diagnostics: NativeDiagnosticsInfo,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = diagnostics.appName,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        diagnostics.lines.forEach { line ->
            Text(
                text = line,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Native Android diagnostics only.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
        TextButton(onClick = {}) {
            Text(value)
        }
    }
}
