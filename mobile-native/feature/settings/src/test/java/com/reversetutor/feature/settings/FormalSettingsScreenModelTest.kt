package com.reversetutor.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class FormalSettingsScreenModelTest {
    @Test
    fun emptyProfilesKeepFigmaUnconfiguredLabel() {
        val state = FormalSettingsUiState.from(llmState(profileItems = emptyList()))

        assertEquals("未配置", state.llmConfigurationLabel)
    }

    @Test
    fun anySavedProfileReportsConfiguredWithoutExposingSecretState() {
        val state = FormalSettingsUiState.from(
            llmState(
                profileItems = listOf(
                    LlmProfileItem(
                        id = "profile-1",
                        name = "DeepSeek",
                        providerModelLabel = "DeepSeek · deepseek-chat",
                        baseUrlLabel = "https://api.deepseek.com",
                        keyStatusLabel = "已保存 API Key",
                        active = false
                    )
                )
            )
        )

        assertEquals("已配置", state.llmConfigurationLabel)
    }

    @Test
    fun sectionsFollowFigmaOrderAndOnlyPrototypeHotspotsAreInteractive() {
        val sections = formalSettingsSections(FormalSettingsUiState())

        assertEquals(
            listOf("学习与内容", "模型与连接", "使用体验", "数据与安全"),
            sections.map { it.title }
        )
        assertEquals(
            listOf(
                "挑战任务提醒",
                "资料自动下载",
                "LLM API 配置",
                "默认布局大小",
                "触感反馈",
                "同步与备份",
                "存储空间",
                "导入与导出",
                "隐私与权限",
                "帮助与关于"
            ),
            sections.flatMap { it.rows }.map { it.label }
        )
        assertEquals(
            listOf(
                FormalSettingsAction.OpenLlmConfiguration,
                FormalSettingsAction.OpenStorage,
                FormalSettingsAction.OpenImportExport,
                FormalSettingsAction.OpenAbout
            ),
            sections.flatMap { it.rows }.mapNotNull { it.action }
        )
    }

    private fun llmState(
        profileItems: List<LlmProfileItem>
    ): LlmProfileSettingsUiState = LlmProfileSettingsUiState(
        summary = "",
        presetLabels = emptyList(),
        profileItems = profileItems,
        connectionStatusLabel = "",
        presets = emptyList()
    )
}
