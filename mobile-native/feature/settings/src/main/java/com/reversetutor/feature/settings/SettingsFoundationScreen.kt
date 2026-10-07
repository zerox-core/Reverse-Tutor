package com.reversetutor.feature.settings

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ButtonDefaults
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
@OptIn(ExperimentalLayoutApi::class)
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
    onOpenImportExport: () -> Unit = {},
    onReturnToSessions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val providerGroups = remember(llmProfileState.presets) {
        settingsModelPresetGroups(llmProfileState.presets)
    }
    val firstPreset = providerGroups.firstOrNull()?.presets?.firstOrNull()
    var profileName by remember { mutableStateOf("") }
    var selectedProvider by remember {
        mutableStateOf(firstPreset?.provider ?: LlmProviderKind.OpenAiCompatible)
    }
    var selectedPresetId by remember { mutableStateOf(firstPreset?.id) }
    var selectedGroupId by remember { mutableStateOf(providerGroups.firstOrNull()?.id ?: "openai") }
    var model by remember { mutableStateOf(firstPreset?.model.orEmpty()) }
    var baseUrl by remember { mutableStateOf(firstPreset?.baseUrl.orEmpty()) }
    var apiKey by remember { mutableStateOf("") }
    var showProfileForm by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<LlmProfileItem?>(null) }
    var showWipeDialog by remember { mutableStateOf(false) }
    val saveEnabled = profileName.trim().isNotEmpty() && model.trim().isNotEmpty()
    val selectedGroup = providerGroups.firstOrNull { it.id == selectedGroupId }
        ?: providerGroups.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "配置您的工作空间和模型参数",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "模型配置",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "服务商预设、Base URL、模型和 API Key。密钥只显示保存状态。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = llmProfileState.summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "预设",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            providerGroups.forEach { group ->
                SettingsPresetChip(
                    label = group.label,
                    selected = group.id == selectedGroup.id,
                    onClick = { selectedGroupId = group.id }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = selectedGroup.description,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedGroup.presets.forEach { preset ->
                SettingsPresetChip(
                    label = preset.label,
                    selected = preset.id == selectedPresetId,
                    onClick = {
                        selectedPresetId = preset.id
                        selectedProvider = preset.provider
                        model = preset.model
                        baseUrl = preset.baseUrl.orEmpty()
                        profileName = preset.label
                        showProfileForm = true
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = { showProfileForm = !showProfileForm }) {
            Text(if (showProfileForm) "收起输入区" else "展开输入区")
        }
        if (showProfileForm) {
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = profileName,
                onValueChange = { profileName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("配置名称") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsInfoRow(label = "服务商", value = selectedProvider.name)
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("模型") },
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
                label = { Text("API Key") },
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
                    showProfileForm = false
                },
                enabled = saveEnabled
            ) {
                Text("保存配置")
            }
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
            text = "数据管理",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "导入、导出和备份都从这里进入。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = onOpenImportExport) {
            Text("导入与导出")
        }
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
                text = "已恢复到可从会话列表重新开始的空状态。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onReturnToSessions) {
                Text("返回会话")
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "外观、记忆与诊断",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsInfoRow(label = "主题", value = state.themeLabel)
        SettingsInfoRow(label = "头像", value = state.avatarVisibilityLabel)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "全局备忘",
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
            Text("关于诊断")
        }
        Spacer(modifier = Modifier.height(28.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "危险区域",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "此操作将永久删除本机设备上的所有对话、上下文和配置文件。此操作不可逆。",
                    fontSize = 14.sp
                )
                TextButton(
                    onClick = { showWipeDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("擦除本地数据")
                }
            }
        }
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除配置") },
            text = { Text(deleteTarget.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLlmProfile(deleteTarget.id)
                        pendingDelete = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showWipeDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            title = { Text(localDataWipeState.confirmationTitle) },
            text = {
                Text(localDataWipeState.confirmationBody)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWipeDialog = false
                        onWipeLocalData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(localDataWipeState.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeDialog = false }
                ) {
                    Text(localDataWipeState.dismissLabel)
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
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
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private data class SettingsModelPreset(
    val id: String,
    val label: String,
    val provider: LlmProviderKind,
    val baseUrl: String?,
    val model: String
)

private data class SettingsModelGroup(
    val id: String,
    val label: String,
    val description: String,
    val presets: List<SettingsModelPreset>
)

private fun settingsModelPresetGroups(
    basePresets: List<LlmProviderPreset>
): List<SettingsModelGroup> {
    val openAiPresets = basePresets
        .filter { it.provider == LlmProviderKind.OpenAiCompatible }
        .map { it.toSettingsModelPreset() }
        .ifEmpty {
            listOf(
                SettingsModelPreset(
                    id = "openai-compatible",
                    label = "OpenAI 兼容",
                    provider = LlmProviderKind.OpenAiCompatible,
                    baseUrl = "https://api.openai.com/v1",
                    model = "gpt-4o-mini"
                )
            )
        }
    val customPresets = basePresets
        .filter { it.provider == LlmProviderKind.Custom || it.provider == LlmProviderKind.Local }
        .map { it.toSettingsModelPreset() }
        .ifEmpty {
            listOf(
                SettingsModelPreset(
                    id = "custom-local",
                    label = "自定义/本地",
                    provider = LlmProviderKind.Custom,
                    baseUrl = "http://localhost:11434",
                    model = "local-model"
                )
            )
        }

    return listOf(
        SettingsModelGroup(
            id = "openai",
            label = "OpenAI / A 类",
            description = "OpenAI 兼容协议，适合 A 类模型或兼容网关。",
            presets = openAiPresets
        ),
        SettingsModelGroup(
            id = "kimi",
            label = "Kimi",
            description = "Moonshot/Kimi OpenAI 兼容接口，保存前请确认账号权限。",
            presets = listOf(
                SettingsModelPreset(
                    id = "kimi-k27-code",
                    label = "Kimi K2.7 Code",
                    provider = LlmProviderKind.OpenAiCompatible,
                    baseUrl = "https://api.moonshot.ai/v1",
                    model = "kimi-k2.7-code-highspeed"
                ),
                SettingsModelPreset(
                    id = "kimi-k26",
                    label = "Kimi K2.6",
                    provider = LlmProviderKind.OpenAiCompatible,
                    baseUrl = "https://api.moonshot.ai/v1",
                    model = "kimi-k2.6"
                )
            )
        ),
        SettingsModelGroup(
            id = "deepseek",
            label = "DeepSeek",
            description = "DeepSeek 官方 OpenAI 兼容接口。",
            presets = listOf(
                SettingsModelPreset(
                    id = "deepseek-v4-flash",
                    label = "DeepSeek V4 Flash",
                    provider = LlmProviderKind.DeepSeek,
                    baseUrl = "https://api.deepseek.com",
                    model = "deepseek-v4-flash"
                ),
                SettingsModelPreset(
                    id = "deepseek-v4-pro",
                    label = "DeepSeek V4 Pro",
                    provider = LlmProviderKind.DeepSeek,
                    baseUrl = "https://api.deepseek.com",
                    model = "deepseek-v4-pro"
                )
            )
        ),
        SettingsModelGroup(
            id = "glm",
            label = "GLM",
            description = "智谱 GLM / Z.ai 兼容接口，按账号控制台可用模型调整。",
            presets = listOf(
                SettingsModelPreset(
                    id = "glm-5-2",
                    label = "GLM-5.2",
                    provider = LlmProviderKind.FreeGlm,
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                    model = "glm-5.2"
                ),
                SettingsModelPreset(
                    id = "glm-5v",
                    label = "GLM-5V",
                    provider = LlmProviderKind.FreeGlm,
                    baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                    model = "glm-5v-turbo"
                )
            )
        ),
        SettingsModelGroup(
            id = "custom",
            label = "自定义",
            description = "本地模型、私有网关或其他 OpenAI 兼容服务。",
            presets = customPresets
        )
    )
}

private fun LlmProviderPreset.toSettingsModelPreset(): SettingsModelPreset =
    SettingsModelPreset(
        id = id,
        label = label,
        provider = provider,
        baseUrl = baseUrl,
        model = model
    )

@Composable
@OptIn(ExperimentalLayoutApi::class)
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
            text = "导入与导出",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(spacing))
        MigrationNotice()

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "导入数据",
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
                    text = "模式：${state.modeLabel}",
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onPickFile) {
                Text("选择 JSON")
            }
            TextButton(
                enabled = importText.isNotBlank(),
                onClick = onDryRun
            ) {
                Text("试运行")
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
                Text("导入")
            }
        }
        if (state.selectedMode == NativeImportMode.Overwrite) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "覆盖模式会替换当前目标空间，且需要输入 ${ImportPipelineUiState.overwriteConfirmationPhrase} 确认。",
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
            label = { Text("导入 JSON") }
        )
        Spacer(modifier = Modifier.height(18.dp))
        ImportResultPanel(state)

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "导出",
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
                Text("当前会话")
            }
            Button(onClick = onExportGraphSnapshot) {
                Text("图谱快照")
            }
            Button(onClick = onExportFullBackup) {
                Text("完整备份")
            }
            TextButton(onClick = onExportPreset) {
                Text("预设")
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
                Text("共享")
            }
            TextButton(
                enabled = exportState.canShareOrSave,
                onClick = onSaveExport
            ) {
                Text("保存")
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = exportState.targetFileName ?: "尚未准备导出",
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
            title = { Text("覆盖导入") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("这会在导入当前文件前替换目标空间中的本地数据。")
                    OutlinedTextField(
                        value = overwriteConfirmation,
                        onValueChange = { overwriteConfirmation = it },
                        singleLine = true,
                        label = { Text("输入 ${ImportPipelineUiState.overwriteConfirmationPhrase}") }
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
                    Text("覆盖")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showOverwriteDialog = false
                        overwriteConfirmation = ""
                    }
                ) {
                    Text("取消")
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
                text = "迁移预览",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "仅使用导出的 JSON。不会直接迁移 IndexedDB，API Key 不会进入导入或导出。",
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
    val hasResult = state.statusLabel != "暂无导入结果"
    val steps = listOf(
        "选择" to hasSource,
        "识别" to hasResult,
        "校验" to hasResult,
        "试运行" to (state.statusLabel == "试运行完成" || state.canImport),
        "模式" to true,
        "确认" to (state.selectedMode != NativeImportMode.Overwrite || hasResult),
        "写入" to (state.statusLabel == "已完成" || state.statusLabel == "部分完成"),
        "结果" to hasResult
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
        text = "模式",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelMedium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "当前模式：${selectedMode.settingsLabel}",
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
                    Text(mode.settingsLabel)
                }
            } else {
                TextButton(onClick = { onImportModeChange(mode) }) {
                    Text(mode.settingsLabel)
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
            Text(text = "类型：${state.kindLabel}", style = MaterialTheme.typography.bodyMedium)
            Text(text = state.documentLabel, style = MaterialTheme.typography.bodyMedium)
            state.targetFileName?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            ResultLines(title = "摘要", lines = state.summaryLines)
            ResultLines(title = "警告", lines = state.warnings)
            ResultLines(title = "错误", lines = state.errors)
            ResultLines(title = "下一步", lines = state.nextActionLines)
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
            Text(text = "选择模式：${state.modeLabel}", style = MaterialTheme.typography.bodyMedium)
            Text(text = state.targetSpaceLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.sourceFileLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.schemaLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.documentTypeLabel, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.apiKeyHandlingLabel, style = MaterialTheme.typography.bodyMedium)
            ResultLines(title = "已写入", lines = state.insertedLines)
            ResultLines(title = "已跳过", lines = state.skippedLines)
            ResultLines(title = "失败", lines = state.failedLines)
            ResultLines(title = "警告", lines = state.warnings)
            ResultLines(title = "错误", lines = state.errors)
            ResultLines(title = "下一步", lines = state.nextActionLines)
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
                    text = if (item.active) "当前" else "未启用",
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
                        Text("设为当前")
                    }
                }
                TextButton(onClick = onTest) {
                    Text("测试")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
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
            text = "仅显示 Native Android 诊断信息。",
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
