package com.reversetutor.preview.background

import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.data.llm.ChatGenerationOutcome

/**
 * Maps generation failures to fixed, export-safe diagnostics.
 *
 * It only maps known local outcome markers; it never persists a provider message,
 * exception, request payload, endpoint or credential.
 */
object GenerationDiagnosticPolicy {

    data class Record(
        val title: String,
        val detail: String,
        val code: String
    )

    fun providerFailure(): Record = ProviderFailure

    fun backgroundFailure(): Record = BackgroundFailure

    fun forProviderOutcome(outcome: ChatGenerationOutcome): Record? =
        ProviderFailure.takeIf { outcome is ChatGenerationOutcome.ProviderFailed }

    fun forBackgroundOutcome(outcome: BackgroundGenerationOutcome): Record? =
        BackgroundFailure.takeIf {
            outcome is BackgroundGenerationOutcome.Failed &&
                outcome.message !in controlledBackgroundFailures
        }

    fun forCode(code: String?): Record = when (code) {
        ProviderFailure.code -> ProviderFailure
        BackgroundFailure.code -> BackgroundFailure
        else -> UnknownGenerationFailure
    }

    private val ProviderFailure = Record(
        title = "模型服务请求失败",
        detail = "模型服务请求未完成，请检查网络或配置后重试。",
        code = "provider_request_failed"
    )

    private val BackgroundFailure = Record(
        title = "后台生成失败",
        detail = "后台生成任务未完成，请打开应用后重试。",
        code = "background_generation_failed"
    )

    private val UnknownGenerationFailure = Record(
        title = "生成任务失败",
        detail = "生成任务未完成，请打开应用后重试。",
        code = "generation_failed"
    )

    private val controlledBackgroundFailures = setOf(
        "No model configured",
        "Vision input unsupported",
        "Blank prompt"
    )
}
