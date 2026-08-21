package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.SessionTurnPersistencePort

/**
 * Adapts existing repository capabilities to the non-frozen
 * [SessionTurnPersistencePort]. Production wiring binds each seam to the
 * corresponding frozen repository method; tests inject controlled fakes.
 *
 * Honesty contract (see tasks/native-p2-007-api-fact-map.md §4):
 * - [acceptUserMessage] delegates to the frozen `MessageRepository.sendUserMessage`
 *   (real persistence of the user message).
 * - [acceptAssistantResult] is a CONFIRMATION only — the frozen
 *   `ChatGenerationRepository` already persisted the assistant message inside
 *   `generateReply` (it owns assistant persistence). Re-saving here would
 *   duplicate frozen persistence semantics, so this method does not re-write.
 * - [recordTerminalFailure] records a terminal failure through [recordFailure].
 *   The default production binding returns `true` without fabricating a run
 *   state: the frozen `ConversationSessionCoordinator` wiring in this phase
 *   does not create a `TurnRun` per turn, so there is no run to mark; the
 *   failure remains observable via the absence of an assistant message.
 *   A faithful run-state recording belongs to the P2 run-lifecycle capability.
 * - [isSessionDeleted] delegates to the frozen run repository's tombstone +
 *   session-existence check.
 * - [isTokenCurrent] defaults to `true`: token currency in this wiring is
 *   enforced through the `canPersistResult` guard supplied to generation
 *   (built from this same port), not through a run-based token lookup. A real
 *   run-based token-currency strategy is a P2 capability.
 * - [isTurnCompleted] delegates to the frozen run repository's latest run
 *   terminal check; with no run created in this phase it returns `false`.
 */
class SessionTurnPersistencePortAdapter(
    private val saveUserMessage: suspend (sessionId: String, text: String, nowEpochMillis: Long, messageId: String) -> Boolean,
    private val checkSessionDeleted: suspend (sessionId: String) -> Boolean,
    private val checkTokenCurrent: suspend (token: String) -> Boolean = { true },
    private val checkTurnCompleted: suspend (turnId: String) -> Boolean = { false },
    private val recordFailure: suspend (spaceId: String, sessionId: String, turnId: String, safeError: String) -> Boolean = { _, _, _, _ -> true },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : SessionTurnPersistencePort {

    override suspend fun acceptUserMessage(
        spaceId: String,
        sessionId: String,
        turnId: String,
        userMessageId: String,
        userText: String
    ): Boolean =
        saveUserMessage(sessionId, userText, nowEpochMillis(), userMessageId)

    override suspend fun acceptAssistantResult(
        spaceId: String,
        sessionId: String,
        turnId: String,
        assistantMessageId: String,
        assistantText: String
    ): Boolean = true

    override suspend fun recordTerminalFailure(
        spaceId: String,
        sessionId: String,
        turnId: String,
        safeError: String
    ): Boolean = recordFailure(spaceId, sessionId, turnId, safeError)

    override suspend fun isSessionDeleted(sessionId: String): Boolean =
        checkSessionDeleted(sessionId)

    override suspend fun isTokenCurrent(token: String): Boolean =
        checkTokenCurrent(token)

    override suspend fun isTurnCompleted(turnId: String): Boolean =
        checkTurnCompleted(turnId)
}
