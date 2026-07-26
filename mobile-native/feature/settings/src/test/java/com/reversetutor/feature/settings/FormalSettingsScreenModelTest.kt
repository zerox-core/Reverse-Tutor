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
    fun sectionsFollowGlobalSettingsWorkspaceOrderAndKeepExistingActionsScoped() {
        val sections = formalSettingsSections(FormalSettingsUiState())

        assertEquals(
            listOf("外观与布局", "模型与连接", "数据与资料", "权限与系统", "关于与版本"),
            sections.map { it.title }
        )
        assertEquals(
            listOf(
                "默认布局大小",
                "触感反馈",
                "LLM API 配置",
                "同步与备份",
                "存储空间",
                "导入与导出",
                "挑战任务提醒",
                "隐私与权限",
                "帮助与关于"
            ),
            sections.flatMap { it.rows }.map { it.label }
        )
        assertEquals(
            listOf(
                FormalSettingsAction.ToggleHapticFeedback,
                FormalSettingsAction.OpenLlmConfiguration,
                FormalSettingsAction.OpenStorage,
                FormalSettingsAction.OpenImportExport,
                FormalSettingsAction.ToggleChallengeReminder,
                FormalSettingsAction.OpenAbout
            ),
            sections.flatMap { it.rows }.mapNotNull { it.action }
        )
    }

    @Test
    fun settingsStateIncludesPersistedToggleValues() {
        val state = FormalSettingsUiState.from(
            llmProfileState = llmState(emptyList()),
            challengeReminderEnabled = false,
            hapticFeedbackEnabled = false
        )

        assertEquals(false, state.challengeReminderEnabled)
        assertEquals(false, state.hapticFeedbackEnabled)
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
