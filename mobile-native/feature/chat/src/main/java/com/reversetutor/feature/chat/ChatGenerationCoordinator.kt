package com.reversetutor.feature.chat

import com.reversetutor.core.data.llm.ChatGenerationInput
import com.reversetutor.core.data.llm.ChatGenerationOutcome
import com.reversetutor.core.llm.LlmGenerationToken

internal fun interface ChatGenerationExecutor {
    suspend fun generate(
        input: ChatGenerationInput,
        nowEpochMillis: Long,
        isTokenCurrent: (LlmGenerationToken) -> Boolean
    ): ChatGenerationOutcome
}

internal class ChatGenerationCoordinator(
    private val executor: ChatGenerationExecutor,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    private var activeToken: LlmGenerationToken? = null

    suspend fun generate(
        input: ChatGenerationInput,
        onStateChanged: (ChatGenerationUiState) -> Unit
    ) {
        activeToken = input.token
        onStateChanged(ChatGenerationUiState.Pending)
        val outcome = executor.generate(input, nowEpochMillis()) { it == activeToken }
        if (activeToken != input.token) return
        activeToken = null
        onStateChanged(outcome.toChatGenerationUiState())
    }

    fun invalidate() {
        activeToken = null
    }
}

private fun ChatGenerationOutcome.toChatGenerationUiState(): ChatGenerationUiState =
    when (this) {
        is ChatGenerationOutcome.Generated -> ChatGenerationUiState.Idle
        is ChatGenerationOutcome.ProviderFailed -> ChatGenerationUiState.Failure(message)
        ChatGenerationOutcome.NoModelConfigured -> ChatGenerationUiState.NoModel
        ChatGenerationOutcome.UnsupportedVision -> ChatGenerationUiState.Failure("图片输入暂不支持")
        ChatGenerationOutcome.BlankPrompt -> ChatGenerationUiState.Failure("不能发送空内容")
        ChatGenerationOutcome.Stale -> ChatGenerationUiState.Idle
    }
