package com.reversetutor.core.data.llm

import com.reversetutor.core.data.model.ExecutionModelConfiguration
import com.reversetutor.core.data.model.ExecutionModelResolver
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.core.llm.LlmAssistantReplyEnvelopeParser
import com.reversetutor.core.llm.timelineText
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmGenerationBlockReason
import com.reversetutor.core.llm.LlmGenerationPlan
import com.reversetutor.core.llm.LlmGenerationPlanner
import com.reversetutor.core.llm.LlmGenerationResult
import com.reversetutor.core.llm.LlmGenerationRuntime
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmProfileCapabilityResolver
import com.reversetutor.core.llm.LlmSessionPolicyContext
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
            contextEvidence = input.contextEvidence,
            sessionPolicy = input.sessionPolicy,
            assistantTurnEnvelope = input.assistantTurnEnvelope,
            allowPlanDrivenOpening = input.allowPlanDrivenOpening
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
                    val parsedReply = LlmAssistantReplyEnvelopeParser.parseValidated(
                        rawText = replyText,
                        allowedEvidenceIds = input.contextEvidence.mapNotNull { it.normalized()?.id }.toSet()
                    )
                    val timelineText = parsedReply?.timelineText() ?: replyText
                    val assistantMessageId = "assistant-${input.token.value}"
                    messageRepository.saveMessage(
                        Message(
                            id = assistantMessageId,
                            spaceId = input.spaceId?.trim()?.ifEmpty { null } ?: activeProfile.spaceId,
                            sessionId = input.sessionId,
                            role = MessageRole.Assistant,
                            text = timelineText,
                            createdAtEpochMillis = nowEpochMillis
                        )
                    )
                    ChatGenerationOutcome.Generated(assistantMessageId, parsedReply)
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
    val userMessageId: String? = null,
    val userText: String? = null,
    val token: LlmGenerationToken,
    val allowPlanDrivenOpening: Boolean = false,
    val spaceId: String? = null,
    val modelBindingId: String? = null,
    val capabilities: LlmCapabilities? = null,
    val quoteExcerpt: String? = null,
    val imageAttachments: List<MessageAttachment> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList(),
    val sessionPolicy: LlmSessionPolicyContext? = null,
    val assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null
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
    data class Generated(
        val assistantMessageId: String,
        val replyEnvelope: LlmAssistantReplyEnvelope? = null
    ) : ChatGenerationOutcome
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
