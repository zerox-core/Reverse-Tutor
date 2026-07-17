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
            avatarVisibilityLabel = if (preferences.globalAvatarVisible) "显示" else "隐藏",
            memoPreviewLines = listOf(
                preferences.primaryMemo,
                preferences.secondaryMemo,
                preferences.scratchMemo
            ).map { it.ifBlank { "空白备忘" } },
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
                0 -> "暂无模型配置"
                1 -> "1 个模型配置"
                else -> "${profiles.size} 个模型配置"
            }
            return LlmProfileSettingsUiState(
                summary = summary,
                presetLabels = presets.map { it.label },
                profileItems = profiles.map { profile ->
                    LlmProfileItem(
                        id = profile.id,
                        name = profile.name,
                        providerModelLabel = "${profile.provider.name} · ${profile.model}",
                        baseUrlLabel = profile.baseUrl ?: "未设置 Base URL",
                        keyStatusLabel = if (profile.secretRef == null) "未保存 API Key" else "已保存 API Key",
                        active = profile.enabled
                    )
                },
                connectionStatusLabel = when (connectionResult) {
                    is LlmConnectionResult.Success -> connectionResult.message
                    is LlmConnectionResult.Failure -> connectionResult.message
                    null -> "尚未测试连接"
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
    val confirmationTitle: String = "擦除本地数据？"
    val confirmationBody: String =
        "这会删除本机的会话、上下文、Memory、资料索引和模型配置。此操作不可撤销。"
    val confirmLabel: String = "确认擦除"
    val dismissLabel: String = "取消"

    fun canConfirm(input: String): Boolean =
        input.trim() == requiredPhrase
}

data class ImportPipelineUiState(
    val selectedFileName: String? = null,
    val selectedMode: NativeImportMode = NativeImportMode.Append,
    val modeLabel: String = NativeImportMode.Append.settingsLabel,
    val sourceFileLabel: String = "来源文件：未选择",
    val documentTypeLabel: String = "文档：未识别",
    val detectStepLabel: String = "识别：等待 JSON",
    val validationStepLabel: String = "校验：请先试运行",
    val schemaLabel: String = "未识别 schema",
    val statusLabel: String = "暂无导入结果",
    val canImport: Boolean = false,
    val apiKeyHandlingLabel: String = importApiKeyHandlingCopy,
    val targetSpaceLabel: String = NativeImportMode.Append.targetSpaceLabel,
    val insertedLines: List<String> = emptyList(),
    val skippedLines: List<String> = emptyList(),
    val failedLines: List<String> = listOf("记录：0"),
    val nextActionLines: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val protocolJson: String? = null
) {
    companion object {
        const val overwriteConfirmationPhrase = "OVERWRITE"
        const val importApiKeyHandlingCopy =
            "API Key 和密钥材料不会被导入。包含密钥字段的文件会校验失败。"

        fun idle(mode: NativeImportMode = NativeImportMode.Append): ImportPipelineUiState =
            ImportPipelineUiState(
                selectedMode = mode,
                modeLabel = mode.settingsLabel,
                targetSpaceLabel = mode.targetSpaceLabel
            )

        fun from(result: NativeImportResult): ImportPipelineUiState =
            ImportPipelineUiState(
                selectedFileName = result.sourceFileName,
                selectedMode = result.mode,
                modeLabel = result.mode.settingsLabel,
                sourceFileLabel = "来源文件：${result.sourceFileName}",
                documentTypeLabel = "文档：${result.documentType.labelOrFallback()}",
                detectStepLabel = "识别：${result.sourceSchema}",
                validationStepLabel = if (result.errors.isEmpty()) {
                    "校验：可导入"
                } else {
                    "校验：已阻止"
                },
                schemaLabel = result.sourceSchema,
                statusLabel = result.status.settingsLabel,
                canImport = result.status == NativeImportStatus.DryRun && result.canWrite,
                targetSpaceLabel = result.mode.targetSpaceLabel,
                insertedLines = result.insertedCounts.toCountLines(),
                skippedLines = result.skippedCounts.toCountLines(),
                failedLines = listOf("记录：${result.errors.size}"),
                nextActionLines = result.nextActionLines(),
                warnings = result.warnings,
                errors = result.errors,
                protocolJson = result.toProtocolJson()
            )
    }
}

data class ExportPipelineUiState(
    val kindLabel: String = "暂无导出",
    val documentLabel: String = "文档：未准备",
    val targetFileName: String? = null,
    val statusLabel: String = "暂无导出结果",
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
            "API Key、密钥引用、token 和密码默认会被排除或脱敏。"

        fun idle(): ExportPipelineUiState = ExportPipelineUiState()

        fun from(result: NativeExportResult): ExportPipelineUiState =
            ExportPipelineUiState(
                kindLabel = result.kind.label,
                documentLabel = "文档：${result.documentType.labelOrFallback()}",
                targetFileName = result.targetFileName,
                statusLabel = if (result.isValid) "导出已准备" else "导出失败",
                canShareOrSave = result.isValid && !result.json.isNullOrBlank(),
                summaryLines = result.snapshot.toSummaryLines(),
                nextActionLines = if (result.isValid) {
                    listOf("共享导出", "保存导出")
                } else {
                    listOf("检查错误后重新准备导出")
                },
                warnings = result.warnings,
                errors = result.errors,
                json = result.json
            )

        fun unavailable(message: String, kindLabel: String = "暂无导出"): ExportPipelineUiState =
            ExportPipelineUiState(
                kindLabel = kindLabel,
                statusLabel = "导出不可用",
                nextActionLines = listOf("请选择已支持的导出类型，或等待对应功能完成。"),
                errors = listOf(message)
            )

        fun saved(targetFileName: String?): ExportPipelineUiState =
            ExportPipelineUiState(
                kindLabel = "已保存导出",
                documentLabel = "文档：已保存 JSON",
                targetFileName = targetFileName,
                statusLabel = "导出已保存",
                canShareOrSave = false,
                nextActionLines = listOf("返回会话，或准备另一次导出")
            )
    }
}

data class FirstLaunchImportPromptUiState(
    val enabledForReplacementBuild: Boolean,
    val alreadySeen: Boolean,
    val title: String = "导入已有数据",
    val body: String = "从现有版本导出的 JSON 导入。不会直接迁移 IndexedDB，也不会导入 API Key；请在模型配置中重新添加。校验后可选择追加、覆盖或新空间。",
    val confirmLabel: String = "选择导入",
    val dismissLabel: String = "稍后"
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
            "包名：$packageName",
            "版本：$versionName ($versionCode)",
            dataNotice,
            "支持：$supportChannel",
            "分发：$distribution"
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
            appName = "Reverse Tutor Native 预览",
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            dataNotice = "本地优先 Android 数据使用 Room 和 DataStore。",
            supportChannel = "正式替换发布前会配置支持渠道。",
            distribution = "内部 debug 预览"
        )
    }
}

private val ThemePreference.label: String
    get() = when (this) {
        ThemePreference.System -> "跟随系统"
        ThemePreference.Light -> "浅色"
        ThemePreference.Dark -> "深色"
        ThemePreference.Focus -> "专注"
    }

private fun Map<String, Int>.toCountLines(): List<String> =
    entries.map { (name, count) -> "$name：$count" }

private fun NativeImportResult.nextActionLines(): List<String> =
    when (status) {
        NativeImportStatus.DryRun -> listOf(
            "确认摘要无误后选择导入模式并写入。",
            "覆盖模式需要输入 ${ImportPipelineUiState.overwriteConfirmationPhrase} 确认。"
        )
        NativeImportStatus.Completed -> listOf(
            "到会话列表打开已导入会话。",
            "如需审计，可保留本次结果 JSON。"
        )
        NativeImportStatus.Partial -> listOf(
            "继续前请检查跳过和失败记录。",
            "查看警告后再打开已导入会话。"
        )
        NativeImportStatus.Failed -> listOf(
            "修复文件，或选择另一个导出 JSON。",
            "校验失败时不会写入记录。"
        )
    }

internal val NativeImportMode.settingsLabel: String
    get() = when (this) {
        NativeImportMode.Append -> "追加到当前空间"
        NativeImportMode.Overwrite -> "覆盖当前空间"
        NativeImportMode.NewSpace -> "导入到新空间"
    }

private val NativeImportMode.targetSpaceLabel: String
    get() = when (this) {
        NativeImportMode.Append -> "目标空间：当前空间，追加记录"
        NativeImportMode.Overwrite -> "目标空间：当前空间，导入前替换"
        NativeImportMode.NewSpace -> "目标空间：新导入空间"
    }

private val NativeExportKind.label: String
    get() = when (this) {
        NativeExportKind.CurrentSession -> "当前会话"
        NativeExportKind.GraphSnapshot -> "图谱快照"
        NativeExportKind.FullBackup -> "完整备份"
    }

private fun NativeExportSnapshot.toSummaryLines(): List<String> =
    buildList {
        add("会话：${sessions.size}")
        add("消息：${messages.size}")
        add("模型配置：${llmProfiles.size}")
        graph?.let {
            add("图谱节点：${it.nodes.size}")
            add("图谱关系：${it.edges.size}")
        }
    }

private fun ProtocolDocumentType?.labelOrFallback(): String =
    when (this) {
        ProtocolDocumentType.LegacyExport -> "旧版导出"
        ProtocolDocumentType.FullBackup -> "完整备份"
        ProtocolDocumentType.SessionExport -> "会话导出"
        ProtocolDocumentType.GraphSnapshot -> "图谱快照"
        ProtocolDocumentType.Preset -> "预设"
        ProtocolDocumentType.LlmProfile -> "模型配置"
        ProtocolDocumentType.ImportResult -> "导入结果"
        null -> "未识别"
    }

private val NativeImportStatus.settingsLabel: String
    get() = when (this) {
        NativeImportStatus.DryRun -> "试运行完成"
        NativeImportStatus.Completed -> "已完成"
        NativeImportStatus.Partial -> "部分完成"
        NativeImportStatus.Failed -> "失败"
    }
