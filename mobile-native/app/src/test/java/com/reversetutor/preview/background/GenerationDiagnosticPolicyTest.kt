package com.reversetutor.preview.background

import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.data.llm.ChatGenerationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GenerationDiagnosticPolicyTest {
    @Test
    fun providerFailureUsesFixedSafeRecordWithoutAcceptingRawFailureText() {
        val diagnostic = GenerationDiagnosticPolicy.providerFailure()

        assertEquals("模型服务请求失败", diagnostic.title)
        assertEquals("provider_request_failed", diagnostic.code)
        assertEquals("模型服务请求未完成，请检查网络或配置后重试。", diagnostic.detail)
        assertFalse(diagnostic.detail.contains("Authorization", ignoreCase = true))
        assertFalse(diagnostic.detail.contains("sk-", ignoreCase = true))
        assertFalse(diagnostic.detail.contains("https://", ignoreCase = true))
    }

    @Test
    fun backgroundFailureUsesFixedSafeRecord() {
        val diagnostic = GenerationDiagnosticPolicy.backgroundFailure()

        assertEquals("后台生成失败", diagnostic.title)
        assertEquals("background_generation_failed", diagnostic.code)
        assertEquals("后台生成任务未完成，请打开应用后重试。", diagnostic.detail)
    }

    @Test
    fun onlyProviderAndBackgroundFailureHaveDiagnosticRecords() {
        assertNull(GenerationDiagnosticPolicy.forProviderOutcome(ChatGenerationOutcome.NoModelConfigured))
        assertEquals(
            "provider_request_failed",
            GenerationDiagnosticPolicy.forProviderOutcome(
                ChatGenerationOutcome.ProviderFailed(
                    "Authorization: Bearer sk-test-secret https://provider.example?key=private"
                )
            )?.code
        )
        assertNull(GenerationDiagnosticPolicy.forBackgroundOutcome(BackgroundGenerationOutcome.Cancelled))
        assertEquals(
            "background_generation_failed",
            GenerationDiagnosticPolicy.forBackgroundOutcome(
                BackgroundGenerationOutcome.Failed("sk-test-secret")
            )?.code
        )
    }

    @Test
    fun controlledBackgroundFailuresDoNotProduceDiagnosticRecords() {
        listOf(
            "No model configured",
            "Vision input unsupported",
            "Blank prompt"
        ).forEach { controlledFailure ->
            assertNull(
                "Controlled failure must not be recorded: $controlledFailure",
                GenerationDiagnosticPolicy.forBackgroundOutcome(
                    BackgroundGenerationOutcome.Failed(controlledFailure)
                )
            )
        }
    }

    @Test
    fun unknownPersistedCodeFallsBackToFixedSafeCopy() {
        val diagnostic = GenerationDiagnosticPolicy.forCode(
            "Authorization: Bearer sk-test-secret https://provider.example?key=private"
        )

        assertEquals("生成任务失败", diagnostic.title)
        assertEquals("generation_failed", diagnostic.code)
        assertFalse(diagnostic.detail.contains("sk-", ignoreCase = true))
        assertFalse(diagnostic.detail.contains("https://", ignoreCase = true))
    }
}
