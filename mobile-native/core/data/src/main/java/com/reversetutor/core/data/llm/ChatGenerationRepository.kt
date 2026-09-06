package com.reversetutor.core.data.llm

import com.reversetutor.core.data.model.ExecutionModelConfiguration
import com.reversetutor.core.data.model.ExecutionModelResolver
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.domain.TurnPlan
import com.reversetutor.core.llm.LlmCapabilities
import com.reversetutor.core.llm.LlmGuidedTurnPlan
import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.core.llm.LlmAssistantReplyEnvelopeParser
import com.reversetutor.core.llm.timelineText
import com.reversetutor.core.llm.toVisibleTimelineText
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
        canPersistResult: suspend () -> Boolean = { true },
        onChunk: (String) -> Unit = {}
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
            guidedTurnPlan = input.turnPlan?.toLlmGuidedTurnPlan(),
            onStreamChunk = { chunk ->
                if (isTokenCurrent(input.token)) onChunk(chunk.toVisibleTimelineText())
            },
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
                    val timelineText = parsedReply?.timelineText() ?: replyText.toVisibleTimelineText()
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
                ChatGenerationOutcome.ProviderFailed(result.message.toSafeProviderFailureCode())
            }
            LlmGenerationResult.Timeout -> {
                ChatGenerationOutcome.ProviderFailed("llm_provider_timeout")
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
    val assistantTurnEnvelope: LlmAssistantTurnEnvelope? = null,
    /**
     * Optional bounded teaching plan produced by the domain selector
     * (NEWMP-V1-002 Task 2.4). It constrains model *expression* only; learning
     * state remains owned by the local verifier, and a null plan keeps the
     * exact legacy request shape.
     */
    val turnPlan: TurnPlan? = null
)

/**
 * Map the domain [TurnPlan] into the wire-only [LlmGuidedTurnPlan] snapshot.
 * Enum names become canonical lowercase wire tokens; `nextActionOnSuccess` /
 * `nextActionOnFailure` are deliberately *not* forwarded — they are domain
 * scheduling data and must never reach the provider prompt.
 */
internal fun TurnPlan.toLlmGuidedTurnPlan(): LlmGuidedTurnPlan = LlmGuidedTurnPlan(
    actionType = actionType.toWireToken(),
    secondaryAction = secondaryAction?.toWireToken().orEmpty(),
    learningObjective = learningObjective,
    conceptKey = conceptKey,
    expectedUserMove = expectedUserMove,
    responseFormat = responseFormat.toWireToken(),
    hintLevel = hintLevel,
    evidenceRequirement = evidenceRequirement.toWireToken()
)

/** `WorkedExample` -> `worked_example`: the canonical wire tokens the LLM whitelist accepts. */
private fun Enum<*>.toWireToken(): String = name
    .replace(Regex("(?<=[a-z0-9])([A-Z])")) { "_" + it.groupValues[1].lowercase() }
    .lowercase()

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

/**
 * NEWMP-V1-002 Task 2.4: collapse provider diagnostics to stable safe codes.
 * Canonical runtime messages map to fixed codes; every unexpected diagnostic
 * collapses to one fixed code so raw exception detail can never reach the UI
 * or persistence.
 */
private fun String.toSafeProviderFailureCode(): String = when (trim().lowercase()) {
    "provider credential is unavailable." -> "llm_credential_unavailable"
    "provider rejected the credential." -> "llm_provider_unauthorized"
    "provider denied access." -> "llm_provider_forbidden"
    "provider endpoint or model was not found." -> "llm_provider_endpoint_unavailable"
    "provider request timed out." -> "llm_provider_timeout"
    "provider rate limit reached." -> "llm_provider_rate_limited"
    "provider is temporarily unavailable." -> "llm_provider_unavailable"
    "provider request was rejected." -> "llm_provider_rejected"
    "provider configuration is invalid." -> "llm_provider_configuration_invalid"
    "provider returned an invalid response." -> "llm_provider_invalid_response"
    "provider request failed." -> "llm_provider_request_failed"
    else -> "llm_provider_request_failed"
}
