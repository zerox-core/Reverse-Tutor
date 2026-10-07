package com.reversetutor.feature.settings

import com.reversetutor.core.llm.LlmConnectionResult
import com.reversetutor.core.llm.LlmProviderPreset
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProfileSettingsModelTest {
    @Test
    fun llmProfileSettingsStateSummarizesProfilesPresetsAndConnectionResult() {
        val state = LlmProfileSettingsUiState.from(
            profiles = listOf(
                LlmProfile(
                    id = "profile-1",
                    spaceId = "space-1",
                    name = "Work model",
                    provider = LlmProviderKind.OpenAiCompatible,
                    model = "gpt-4o-mini",
                    secretRef = "llm-secret-profile-1",
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 2L,
                    baseUrl = "https://api.openai.com/v1",
                    enabled = true
                )
            ),
            presets = LlmProviderPreset.defaults,
            connectionResult = LlmConnectionResult.Success("Mock connection ready")
        )

        assertEquals("1 个模型配置", state.summary)
        assertEquals(listOf("OpenAI-compatible", "Anthropic-compatible", "Custom/local"), state.presetLabels)
        assertEquals("Work model", state.profileItems.single().name)
        assertEquals("OpenAiCompatible · gpt-4o-mini", state.profileItems.single().providerModelLabel)
        assertEquals("已保存 API Key", state.profileItems.single().keyStatusLabel)
        assertTrue(state.profileItems.single().active)
        assertEquals("Mock connection ready", state.connectionStatusLabel)
    }
}
