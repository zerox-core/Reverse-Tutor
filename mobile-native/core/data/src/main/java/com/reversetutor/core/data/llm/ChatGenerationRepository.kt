package com.reversetutor.core.data.llm

import com.reversetutor.core.data.model.ExecutionModelConfiguration
import com.reversetutor.core.data.model.ExecutionModelResolver
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmGenerationBlockReason
import com.reversetutor.core.llm.LlmGenerationPlan
import com.reversetutor.core.llm.LlmGenerationPlanner
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmProfileCapabilityResolver
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageAttachment
import com.reversetutor.core.model.MessageRole
import com.reversetutor.core.model.LlmProfile
import com.reversetutor.core.model.LlmProviderKind
import com.reversetutor.core.model.ModelProtocol

class ChatGenerationRepository(
    private val messageRepository: MessageRepository,
    private val llmProfileRepository: LlmProfileRepository,
    private val runtime: LlmGenerationRuntime,
    private val modelConnectionRepository: ExecutionModelResolver? = null
) {
    suspend fun generateReply(
        input: ChatGenerationInput,
        nowEpochMillis: Long,
        isTokenCurrent: (LlmGenerationToken) -> Boolean,
        canPersistResult: suspend () -> Boolean = { true }
    ): ChatGenerationOutcome {
        if (!isTokenCurrent(input.token)) {
            return ChatGenerationOutcome.Stale
        }

        val activeProfile = resolveExecutionProfile(input)
            ?: return ChatGenerationOutcome.NoModelConfigured
        val plan = LlmGenerationPlanner.plan(
            sessionId = input.sessionId,
            userMessageId = input.userMessageId,
            userText = input.userText,
            profile = activeProfile,
            capabilities = input.capabilities ?: LlmProfileCapabilityResolver.infer(activeProfile),
            token = input.token,
            quoteExcerpt = input.quoteExcerpt,
            imageAttachments = input.imageAttachments,
            contextEvidence = input.contextEvidence
        )

        val request = when (plan) {
            is LlmGenerationPlan.Blocked -> return plan.reason.toOutcome()
            is LlmGenerationPlan.Ready -> plan.request
        }

        val result = runtime.generate(request)
        if (!isTokenCurrent(input.token) || !canPersistResult()) {
            return ChatGenerationOutcome.Stale
        }

        return when (result) {
            is LlmGenerationResult.Success,
            is LlmGenerationResult.Streamed -> {
                val replyText = result.visibleText
                if (replyText.isBlank()) {
                    ChatGenerationOutcome.ProviderFailed("Empty response")
                } else {
                    val assistantMessageId = "assistant-${input.token.value}"
                    messageRepository.saveMessage(
                        Message(
                            id = assistantMessageId,
                            spaceId = activeProfile.spaceId,
                            sessionId = input.sessionId,
                            role = MessageRole.Assistant,
                            text = replyText.withCitationFooter(input.contextEvidence),
                            createdAtEpochMillis = nowEpochMillis
                        )
                    )
                    ChatGenerationOutcome.Generated(assistantMessageId)
                }
            }
            is LlmGenerationResult.Failure -> {
                ChatGenerationOutcome.ProviderFailed(result.message)
            }
            LlmGenerationResult.Timeout -> {
                ChatGenerationOutcome.ProviderFailed(LlmGenerationResult.Timeout.message)
            }
        }
    }

    private suspend fun resolveExecutionProfile(input: ChatGenerationInput): LlmProfile? {
        val resolver = modelConnectionRepository ?: return activeLegacyProfile()
        val resolved = resolver.resolveForExecution(
            sessionId = input.sessionId,
            requestedBindingId = input.modelBindingId
        )
        if (resolved != null) return resolved.toExecutionProfile()

        val hasExplicitRequest = !input.modelBindingId.isNullOrBlank()
        if (hasExplicitRequest || resolver.hasNewConfigurationForSession(input.sessionId)) {
            return null
        }
        return activeLegacyProfile()
    }

    private suspend fun activeLegacyProfile(): LlmProfile? =
        llmProfileRepository.listProfiles().firstOrNull { it.enabled }
}

data class ChatGenerationInput(
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val token: LlmGenerationToken,
    val modelBindingId: String? = null,
    val capabilities: LlmCapabilities? = null,
    val quoteExcerpt: String? = null,
    val imageAttachments: List<MessageAttachment> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList()
)

private fun ExecutionModelConfiguration.toExecutionProfile(): LlmProfile =
    LlmProfile(
        id = binding.id,
        spaceId = binding.spaceId,
        name = binding.displayName,
        provider = connection.protocol.toLegacyProvider(),
        model = binding.modelId,
        secretRef = connection.secretRef,
        createdAtEpochMillis = binding.createdAtEpochMillis,
        updatedAtEpochMillis = maxOf(binding.updatedAtEpochMillis, connection.updatedAtEpochMillis),
        baseUrl = connection.baseUrl,
        enabled = binding.enabled && connection.enabled
    )

private fun ModelProtocol.toLegacyProvider(): LlmProviderKind = when (this) {
    ModelProtocol.OpenAiCompatible -> LlmProviderKind.OpenAiCompatible
    ModelProtocol.AnthropicCompatible -> LlmProviderKind.AnthropicCompatible
    ModelProtocol.GeminiNative -> LlmProviderKind.Gemini
}

sealed interface ChatGenerationOutcome {
    data class Generated(val assistantMessageId: String) : ChatGenerationOutcome
    data class ProviderFailed(val message: String) : ChatGenerationOutcome
    object NoModelConfigured : ChatGenerationOutcome
    object UnsupportedVision : ChatGenerationOutcome
    object BlankPrompt : ChatGenerationOutcome
    object Stale : ChatGenerationOutcome
}

private fun LlmGenerationBlockReason.toOutcome(): ChatGenerationOutcome =
    when (this) {
        LlmGenerationBlockReason.NoModelConfigured -> ChatGenerationOutcome.NoModelConfigured
        LlmGenerationBlockReason.UnsupportedVision -> ChatGenerationOutcome.UnsupportedVision
        LlmGenerationBlockReason.BlankPrompt -> ChatGenerationOutcome.BlankPrompt
    }

private fun String.withCitationFooter(evidence: List<LlmContextEvidence>): String {
    val citations = evidence
        .mapNotNull { it.normalized() }
        .take(6)
    if (citations.isEmpty()) return this
    val footer = citations.mapIndexed { index, item ->
        val target = listOfNotNull(item.sourceMessageId, item.sourceId)
            .joinToString(separator = ", ")
            .ifBlank { item.id }
        "[${index + 1}] ${item.title} ($target)"
    }.joinToString(separator = "\n")
    return trimEnd() + "\n\nSources:\n" + footer
}
