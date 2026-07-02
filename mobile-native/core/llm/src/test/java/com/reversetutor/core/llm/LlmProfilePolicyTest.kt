package com.reversetutor.core.llm

import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProfilePolicyTest {
    @Test
    fun providerPresetsCoverPrimaryProtocolFamiliesAndCapabilities() {
        val presets = LlmProviderPreset.defaults

        assertEquals(
            listOf("OpenAI-compatible", "Anthropic-compatible", "Custom/local"),
            presets.map { it.label }
        )
        assertEquals(
            listOf(
                LlmProviderKind.OpenAiCompatible,
                LlmProviderKind.AnthropicCompatible,
                LlmProviderKind.Custom
            ),
            presets.map { it.provider }
        )
        assertTrue(presets.first { it.id == "openai-compatible" }.capabilities.supportsJsonMode)
        assertTrue(presets.first { it.id == "openai-compatible" }.capabilities.supportsVision)
        assertTrue(presets.first { it.id == "anthropic-compatible" }.capabilities.supportsVision)
        assertFalse(presets.first { it.id == "anthropic-compatible" }.capabilities.supportsJsonMode)
    }

    @Test
    fun capabilityResolverInfersVisionWithoutPersistedSchemaFields() {
        val openAiVision = LlmProfileCapabilityResolver.infer(
            profile(
                provider = LlmProviderKind.OpenAiCompatible,
                model = "gpt-4o-mini"
            )
        )
        val customText = LlmProfileCapabilityResolver.infer(
            profile(
                provider = LlmProviderKind.Custom,
                model = "plain-local-model"
            )
        )

        assertTrue(openAiVision.supportsVision)
        assertTrue(openAiVision.supportsJsonMode)
        assertFalse(customText.supportsVision)
        assertFalse(customText.supportsJsonMode)
    }

    @Test
    fun validatorRejectsIncompleteProfilesAndRawSecretMetadata() {
        val result = LlmProfileValidator.validate(
            LlmProfileDraft(
                name = "sk-secret should not be here",
                provider = LlmProviderKind.OpenAiCompatible,
                model = "",
                baseUrl = "not-a-url",
                apiKey = "  sk-test  ",
                capabilities = LlmCapabilities(supportsVision = true, supportsJsonMode = true)
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("model", ignoreCase = true) })
        assertTrue(result.errors.any { it.contains("base URL", ignoreCase = true) })
        assertTrue(result.errors.any { it.contains("secret", ignoreCase = true) })
    }

    @Test
    fun exportPolicyRedactsSecretReferencesByDefault() {
        val exportable = LlmProfileExportPolicy.redacted(
            LlmProfile(
                id = "profile-1",
                spaceId = "space-1",
                name = "Work model",
                provider = LlmProviderKind.OpenAiCompatible,
                model = "gpt-4o-mini",
                secretRef = "llm-secret-profile-1",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 2L,
                baseUrl = "https://api.openai.com/v1"
            ),
            capabilities = LlmCapabilities(supportsVision = true, supportsJsonMode = true)
        )

        assertEquals("profile-1", exportable.id)
        assertEquals("redacted", exportable.secret)
        assertTrue(exportable.hasSecret)
        assertFalse(exportable.toString().contains("llm-secret-profile-1"))
    }

    @Test
    fun mockConnectionTesterNeverRequiresRealProviderCalls() {
        val tester = MockLlmConnectionTester(
            outcomes = mapOf(
                "profile-ok" to LlmConnectionResult.Success("Mock connection ready")
            )
        )

        val result = tester.testConnection(
            LlmProfile(
                id = "profile-ok",
                spaceId = "space-1",
                name = "Mock",
                provider = LlmProviderKind.Custom,
                model = "demo",
                secretRef = "llm-secret-profile-ok",
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 2L,
                baseUrl = "http://localhost:11434"
            )
        )

        assertEquals(LlmConnectionResult.Success("Mock connection ready"), result)
        assertEquals(0, tester.realProviderCallCount)
    }

    private fun profile(
        provider: LlmProviderKind,
        model: String
    ): LlmProfile = LlmProfile(
        id = "profile-$model",
        spaceId = "space-1",
        name = model,
        provider = provider,
        model = model,
        secretRef = null,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 2L,
        baseUrl = null
    )
}
