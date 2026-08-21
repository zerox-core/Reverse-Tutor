package com.reversetutor.core.domain

/**
 * Non-frozen adapter ports for the session turn coordinator.
 *
 * These are domain-level interfaces that adapters implement by delegating to
 * existing frozen Repository methods. They do NOT modify frozen Repository
 * signatures — they wrap them behind a stable domain boundary.
 */

// ---------------------------------------------------------------------------
// Generation port
// ---------------------------------------------------------------------------

/** Safe, domain-level generation request (no raw API key or Authorization). */
data class GenerationRequest(
    val spaceId: String,
    val sessionId: String,
    val turnId: String,
    val userMessageId: String,
    val userText: String,
    val token: String,
    val contextEvidence: List<String>
)

/**
 * Domain-level mapping of the frozen ChatGenerationOutcome.
 * [Generated.rawProviderName] is optional and must never contain a URL or key.
 */
sealed interface GenerationOutcome {
    data class Generated(
        val assistantMessageId: String,
        val assistantText: String,
        val rawProviderName: String? = null
    ) : GenerationOutcome
    data object NoModelConfigured : GenerationOutcome
    data class ProviderFailed(val safeError: String) : GenerationOutcome
    data object UnsupportedVision : GenerationOutcome
    data object BlankPrompt : GenerationOutcome
    data object Stale : GenerationOutcome
}

/**
 * Adapter port wrapping the frozen [ChatGenerationRepository].
 * The adapter translates between domain-safe [GenerationRequest] and the
 * frozen ChatGenerationInput/ChatGenerationOutcome.
 */
interface ChatGenerationPort {
    suspend fun generateReply(
        request: GenerationRequest,
        nowEpochMillis: Long,
        isTokenCurrent: Boolean,
        canPersistResult: Boolean
    ): GenerationOutcome
}

// ---------------------------------------------------------------------------
// Persistence port
// ---------------------------------------------------------------------------

/**
 * Adapter port for user-message acceptance, assistant-result acceptance,
 * terminal failure recording, and session/token validity checks.
 */
interface SessionTurnPersistencePort {
    suspend fun acceptUserMessage(
        spaceId: String,
        sessionId: String,
        turnId: String,
        userMessageId: String,
        userText: String
    ): Boolean

    suspend fun acceptAssistantResult(
        spaceId: String,
        sessionId: String,
        turnId: String,
        assistantMessageId: String,
        assistantText: String
    ): Boolean

    suspend fun recordTerminalFailure(
        spaceId: String,
        sessionId: String,
        turnId: String,
        safeError: String
    ): Boolean

    suspend fun isSessionDeleted(sessionId: String): Boolean

    suspend fun isTokenCurrent(token: String): Boolean

    suspend fun isTurnCompleted(turnId: String): Boolean
}

// ---------------------------------------------------------------------------
// Coordinator result
// ---------------------------------------------------------------------------

/** Safe result returned to the caller. Never leaks Provider URL, key, or raw exception. */
sealed interface SessionTurnResult {
    data class Success(
        val assistantMessageId: String,
        val assistantText: String,
        val evaluation: SessionEvaluationContract,
        val action: SessionActionContract,
        val context: ConversationContextContract,
        val processSummary: String
    ) : SessionTurnResult

    data class NoModel(
        val context: ConversationContextContract,
        val processSummary: String
    ) : SessionTurnResult

    data class ProviderError(
        val safeError: String,
        val context: ConversationContextContract
    ) : SessionTurnResult

    data class StaleToken(val context: ConversationContextContract) : SessionTurnResult
    data class SessionDeleted(val context: ConversationContextContract) : SessionTurnResult
    data class UnsupportedInput(val context: ConversationContextContract) : SessionTurnResult
    data class BlankInput(val context: ConversationContextContract) : SessionTurnResult
    data class Discarded(val reason: String, val context: ConversationContextContract) : SessionTurnResult
}

// ---------------------------------------------------------------------------
// Coordinator
// ---------------------------------------------------------------------------

/**
 * Orchestrates a single conversation turn across context read, policy,
 * persistence, and generation — without implementing LLM transport or
 * modifying frozen Repository signatures.
 *
 * Sequence: context read → policy → session check → user persistence →
 * generation → token/session gate → assistant persistence → result mapping.
 */
class ConversationSessionCoordinator(
    private val contextAssembler: ConversationContextAssembler,
    private val generationPort: ChatGenerationPort,
    private val persistencePort: SessionTurnPersistencePort,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {

    suspend fun executeTurn(
        spaceId: String,
        sessionId: String,
        turnId: String,
        userMessageId: String,
        userText: String,
        token: String,
        policyInput: SessionPolicyInput
    ): SessionTurnResult {

        // 1. Read context (partial-failure safe)
        val context = contextAssembler.assemble(spaceId, sessionId)

        // 2. Apply policy (pure, no side effects)
        val policyOutput = SessionTurnPolicy.normalize(policyInput)

        // 3. Check session deleted
        if (persistencePort.isSessionDeleted(sessionId)) {
            return SessionTurnResult.SessionDeleted(context)
        }

        // 4. Check duplicate turn (idempotent)
        if (persistencePort.isTurnCompleted(turnId)) {
            return SessionTurnResult.Discarded("duplicate turn", context)
        }

        // 5. Accept user message
        persistencePort.acceptUserMessage(spaceId, sessionId, turnId, userMessageId, userText)

        // 6. Build generation request
        val request = GenerationRequest(
            spaceId = spaceId,
            sessionId = sessionId,
            turnId = turnId,
            userMessageId = userMessageId,
            userText = userText,
            token = token,
            contextEvidence = context.prerequisiteGaps + context.pendingReviewKnowledgePoints
        )

        // 7. Check token current
        val isCurrent = persistencePort.isTokenCurrent(token)

        // 8. Generate
        val outcome = generationPort.generateReply(
            request = request,
            nowEpochMillis = nowEpochMillis(),
            isTokenCurrent = isCurrent,
            canPersistResult = true
        )

        // 9. Map outcome to safe result
        return when (outcome) {
            is GenerationOutcome.Generated -> {
                // Re-check session validity before persisting
                if (persistencePort.isSessionDeleted(sessionId)) {
                    return SessionTurnResult.SessionDeleted(context)
                }
                // Stale token → do NOT persist assistant result
                if (!isCurrent) {
                    return SessionTurnResult.StaleToken(context)
                }
                persistencePort.acceptAssistantResult(
                    spaceId, sessionId, turnId,
                    outcome.assistantMessageId, outcome.assistantText
                )
                SessionTurnResult.Success(
                    assistantMessageId = outcome.assistantMessageId,
                    assistantText = outcome.assistantText,
                    evaluation = policyOutput.evaluation,
                    action = policyOutput.action,
                    context = context,
                    processSummary = policyOutput.processSummary
                )
            }
            GenerationOutcome.NoModelConfigured ->
                SessionTurnResult.NoModel(context, policyOutput.processSummary)
            is GenerationOutcome.ProviderFailed -> {
                persistencePort.recordTerminalFailure(spaceId, sessionId, turnId, outcome.safeError)
                SessionTurnResult.ProviderError(outcome.safeError, context)
            }
            GenerationOutcome.UnsupportedVision ->
                SessionTurnResult.UnsupportedInput(context)
            GenerationOutcome.BlankPrompt ->
                SessionTurnResult.BlankInput(context)
            GenerationOutcome.Stale ->
                SessionTurnResult.StaleToken(context)
        }
    }
}
