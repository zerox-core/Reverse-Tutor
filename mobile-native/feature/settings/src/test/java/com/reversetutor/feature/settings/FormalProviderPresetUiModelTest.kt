package com.reversetutor.feature.settings

import com.reversetutor.core.model.LlmProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormalProviderPresetUiModelTest {
    @Test
    fun catalogFollowsFormalFigmaOrder() {
        assertEquals(
            listOf(
                "手动填写",
                "DeepSeek / OpenAI",
                "DeepSeek / Anthropic",
                "Kimi / Moonshot CN",
                "百炼 / Qwen",
                "智谱 GLM",
                "MiniMax / OpenAI",
                "MiniMax / Anthropic",
                "小米 MiMo",
                "Claude",
                "豆包 / 火山方舟",
                "硅基流动",
                "百度千帆",
                "腾讯混元",
                "OpenAI"
            ),
            formalProviderPresetCatalog.map { it.title }
        )
    }

    @Test
    fun filtersUseInterfaceFormatAndDomesticMetadata() {
        assertEquals(
            listOf("DeepSeek / Anthropic", "MiniMax / Anthropic", "Claude"),
            formalProviderPresetRows("", FormalProviderPresetFilter.Anthropic).map { it.title }
        )
        assertEquals(
            11,
            formalProviderPresetRows("", FormalProviderPresetFilter.OpenAi).size
        )
        assertEquals(
            12,
            formalProviderPresetRows("", FormalProviderPresetFilter.Domestic).size
        )
    }

    @Test
    fun searchMatchesProviderInterfaceAndAddress() {
        assertEquals(
            listOf("百度千帆", "腾讯混元"),
            formalProviderPresetRows("地域", FormalProviderPresetFilter.All).map { it.title }
        )
        assertEquals(
            listOf("Kimi / Moonshot CN"),
            formalProviderPresetRows("moonshot.cn", FormalProviderPresetFilter.All).map { it.title }
        )
        assertEquals(
            listOf("DeepSeek / Anthropic", "MiniMax / Anthropic", "Claude"),
            formalProviderPresetRows("anthropic", FormalProviderPresetFilter.All).map { it.title }
        )
    }

    @Test
    fun onlySupportedProviderRowsNavigate() {
        assertEquals(
            listOf(
                FormalLlmProvider.DeepSeek,
                FormalLlmProvider.DeepSeek,
                FormalLlmProvider.Kimi
            ),
            formalProviderPresetCatalog.mapNotNull { it.destination }
        )
        assertNull(formalProviderPresetCatalog.first { it.title == "百炼 / Qwen" }.destination)
        assertNull(formalProviderPresetCatalog.first { it.title == "OpenAI" }.destination)
    }

    @Test
    fun editorDraftUsesProviderDefaultsAndProducesRepositoryInput() {
        val deepSeek = FormalLlmProfileDraft.forProvider(FormalLlmProvider.DeepSeek)
        val kimi = FormalLlmProfileDraft.forProvider(FormalLlmProvider.Kimi)

        assertEquals("DeepSeek", deepSeek.name)
        assertEquals("deepseek-chat", deepSeek.model)
        assertEquals("https://api.deepseek.com", deepSeek.baseUrl)
        assertEquals(LlmProviderKind.DeepSeek, deepSeek.providerKind)
        assertEquals("moonshot-v1-8k", kimi.model)
        assertEquals("https://api.moonshot.cn/v1", kimi.baseUrl)
        assertEquals(LlmProviderKind.OpenAiCompatible, kimi.providerKind)

        val input = deepSeek.copy(apiKey = " secret-key ").toInput()
        assertEquals(" secret-key ", input.apiKey)
        assertEquals(LlmProviderKind.DeepSeek, input.provider)
    }
}
