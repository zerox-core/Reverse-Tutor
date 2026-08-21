package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.llm.ChatGenerationInput
import com.reversetutor.core.data.llm.ChatGenerationOutcome
import com.reversetutor.core.domain.ChatGenerationPort
import com.reversetutor.core.domain.GenerationOutcome
import com.reversetutor.core.domain.GenerationRequest
import com.reversetutor.core.domain.SessionPolicyOutput
import com.reversetutor.core.domain.SessionTurnContracts
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmGenerationToken

/**
 * Adapts the frozen `ChatGenerationRepository.generateReply` to the non-frozen
 * domain [ChatGenerationPort]. Production wiring binds [generate] to the frozen
 * repository method; tests inject a controlled seam.
 *
 * Honesty contract (see tasks/native-p2-007-api-fact-map.md §3):
 * - Builds a frozen [ChatGenerationInput] using ONLY fields the frozen protocol
 *   safely receives: sessionId, userMessageId, userText, token, contextEvidence.
 * - Does NOT inject [SessionPolicyOutput] (action/evaluation/processSummary):
 *   the frozen `ChatGenerationInput` has no such field. Faking injection would
 *   be dishonest. The gap is recorded in
 *   tasks/capability-requests/P3-session-policy-context.md.
 * - Maps context-evidence strings to structured [LlmContextEvidence] using the
 *   real gap/review text as the body — no fabricated structure.
 * - Maps the frozen [ChatGenerationOutcome] to the domain [GenerationOutcome],
 *   routing provider failures through [SessionTurnContracts.safeGenerationFailureCode]
 *   so no Provider URL, API key, Authorization header, or raw exception text
 *   reaches the domain/UI.
 *
 * Token/session currency is enforced by the [canPersistResult] guard the
 * coordinator supplies (it is built from the persistence port). The frozen
 * `isTokenCurrent` is wired to a permissive pass: in this wiring the frozen
 * repository owns assistant persistence, and staleness is gated through
 * `canPersistResult` before the frozen repo persists.
 */
class ChatGenerationPortAdapter(
    private val generate: suspend (
        input: ChatGenerationInput,
        nowEpochMillis: Long,
        isTokenCurrent: (LlmGenerationToken) -> Boolean,
        canPersistResult: suspend () -> Boolean
    ) -> ChatGenerationOutcome,
    private val readAssistantText: suspend (sessionId: String, assistantMessageId: String) -> String = { _, _ -> "" }
) : ChatGenerationPort {

    override suspend fun generateReply(
        request: GenerationRequest,
        nowEpochMillis: Long,
        canPersistResult: suspend () -> Boolean
    ): GenerationOutcome {
        val input = buildInput(request)
        val outcome = generate(input, nowEpochMillis, { _ -> true }, canPersistResult)
        return mapOutcome(request, outcome)
    }

    /**
     * Maps the domain [GenerationRequest] to the frozen [ChatGenerationInput].
     * Policy fields ([GenerationRequest.policy]) are deliberately NOT mapped —
     * the frozen input has no field for them.
     */
    internal fun buildInput(request: GenerationRequest): ChatGenerationInput =
        ChatGenerationInput(
            sessionId = request.sessionId,
            userMessageId = request.userMessageId,
            userText = request.userText,
            token = LlmGenerationToken(request.token),
            modelBindingId = null,
            capabilities = null,
            quoteExcerpt = null,
            imageAttachments = emptyList(),
            contextEvidence = request.contextEvidence.mapIndexed { index, text ->
                LlmContextEvidence(
                    id = "ctx-${request.turnId}-$index",
                    title = text.take(80).ifBlank { "Context" },
                    body = text,
                    kind = "SessionContext"
                )
            }
        )

    /**
     * Maps the frozen [ChatGenerationOutcome] to the domain [GenerationOutcome].
     * Provider failures are always reduced to [GenerationOutcome.ProviderFailed]
     * with a sanitized code; the raw [ChatGenerationOutcome.ProviderFailed.message]
     * (which may carry provider text) is never forwarded.
     */
    internal suspend fun mapOutcome(
        request: GenerationRequest,
        outcome: ChatGenerationOutcome
    ): GenerationOutcome = when (outcome) {
        is ChatGenerationOutcome.Generated -> {
            val text = readAssistantText(request.sessionId, outcome.assistantMessageId)
            GenerationOutcome.Generated(
                assistantMessageId = outcome.assistantMessageId,
                assistantText = text
            )
        }
        ChatGenerationOutcome.NoModelConfigured -> GenerationOutcome.NoModelConfigured
        is ChatGenerationOutcome.ProviderFailed ->
            GenerationOutcome.ProviderFailed(
                safeError = SessionTurnContracts.safeGenerationFailureCode(outcome.message)
            )
        ChatGenerationOutcome.UnsupportedVision -> GenerationOutcome.UnsupportedVision
        ChatGenerationOutcome.BlankPrompt -> GenerationOutcome.BlankPrompt
        ChatGenerationOutcome.Stale -> GenerationOutcome.Stale
    }
}
