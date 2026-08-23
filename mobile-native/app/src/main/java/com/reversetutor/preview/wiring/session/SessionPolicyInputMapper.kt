package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.CorrectionPersistenceWire
import com.reversetutor.core.domain.SessionModeWire
import com.reversetutor.core.domain.SessionPolicyInput
import com.reversetutor.core.domain.SessionPolicyOutput
import com.reversetutor.core.domain.SessionStrategySettings
import com.reversetutor.core.domain.SessionTurnContracts
import com.reversetutor.core.llm.LlmContextEvidence
import com.reversetutor.core.llm.LlmSessionPolicyContext
import com.reversetutor.feature.chat.NewSessionConfiguration

private const val MaxContextEvidence = 6
private const val MaxEvidenceBodyChars = 900

/**
 * Maps a [NewSessionConfiguration] snapshot to a [SessionPolicyInput] with
 * a fixed study mode. Never infers mode from free-text dialogueStrategy.
 */
internal fun NewSessionConfiguration?.toSessionPolicyInput(userText: String): SessionPolicyInput {
    val snapshot = this
    return SessionPolicyInput(
        mode = SessionModeWire.STUDY,
        userInput = userText,
        knowledgePoint = snapshot?.goal?.ifBlank { snapshot.title }.orEmpty(),
        settings = SessionStrategySettings(
            probingIntensity = snapshot?.probingIntensity ?: 3,
            correctionPersistence = when (snapshot?.correctionPersistence) {
                "\u5bbd\u677e" -> CorrectionPersistenceWire.GENTLE
                "\u4e25\u683c" -> CorrectionPersistenceWire.PERSISTENT
                else -> CorrectionPersistenceWire.BALANCED
            }
        )
    )
}

/**
 * Maps a [SessionPolicyOutput] to the frozen [LlmSessionPolicyContext] wire type.
 * Text fields are sanitized with [SessionTurnContracts.sanitizeContractText]
 * to cap length and strip sensitive patterns.
 */
internal fun SessionPolicyOutput.toLlmSessionPolicyContext(): LlmSessionPolicyContext =
    LlmSessionPolicyContext(
        actionType = SessionTurnContracts.sanitizeContractText(action.type, maxLength = 48),
        studentRole = SessionTurnContracts.sanitizeContractText(action.studentRole, maxLength = 64),
        knowledgePoint = SessionTurnContracts.sanitizeContractText(action.knowledgePoint, maxLength = 120),
        difficulty = action.difficulty,
        processSummary = SessionTurnContracts.sanitizeContractText(processSummary, maxLength = 320),
        evaluationCorrectness = evaluation.correctness,
        userEmotion = SessionTurnContracts.sanitizeContractText(evaluation.userEmotion, maxLength = 48),
        correctionTiming = SessionTurnContracts.sanitizeContractText(correctionTiming, maxLength = 48)
    )

/**
 * Converts a [ConversationContextContract] into bounded [LlmContextEvidence].
 * Uses only recent messages, memory, sources, gaps, review points, and
 * historical errors. Text is capped with [SessionTurnContracts.sanitizeContractText].
 */
internal fun ConversationContextContract.toLlmContextEvidence(): List<LlmContextEvidence> {
    val evidence = mutableListOf<LlmContextEvidence>()

    recentMessages.forEach { msg ->
        evidence.add(LlmContextEvidence(
            id = "msg-\${msg.messageId}",
            title = SessionTurnContracts.sanitizeContractText(msg.role, maxLength = 48),
            body = SessionTurnContracts.sanitizeContractText(msg.text, maxLength = MaxEvidenceBodyChars),
            kind = "Message",
            sourceMessageId = msg.messageId
        ))
    }

    relatedMemory.forEach { mem ->
        evidence.add(LlmContextEvidence(
            id = mem.id,
            title = "Memory",
            body = SessionTurnContracts.sanitizeContractText(mem.summary, maxLength = MaxEvidenceBodyChars),
            kind = "Memory"
        ))
    }

    sourceEvidence.forEach { src ->
        evidence.add(LlmContextEvidence(
            id = src.id,
            title = SessionTurnContracts.sanitizeContractText(src.title, maxLength = 120),
            body = SessionTurnContracts.sanitizeContractText(src.excerpt, maxLength = MaxEvidenceBodyChars),
            kind = "Source",
            sourceId = src.id
        ))
    }

    historicalErrors.forEach { err ->
        evidence.add(LlmContextEvidence(
            id = err.id,
            title = SessionTurnContracts.sanitizeContractText(err.errorType, maxLength = 48),
            body = SessionTurnContracts.sanitizeContractText(err.description, maxLength = MaxEvidenceBodyChars),
            kind = "Error"
        ))
    }

    if (prerequisiteGaps.isNotEmpty()) {
        evidence.add(LlmContextEvidence(
            id = "gaps-\$sessionId",
            title = "\u77e5\u8bc6\u7f3a\u53e3",
            body = SessionTurnContracts.sanitizeContractText(prerequisiteGaps.joinToString("; "), maxLength = MaxEvidenceBodyChars),
            kind = "Gaps"
        ))
    }

    if (pendingReviewKnowledgePoints.isNotEmpty()) {
        evidence.add(LlmContextEvidence(
            id = "review-\$sessionId",
            title = "\u5f85\u590d\u4e60",
            body = SessionTurnContracts.sanitizeContractText(pendingReviewKnowledgePoints.joinToString("; "), maxLength = MaxEvidenceBodyChars),
            kind = "Review"
        ))
    }

    return evidence.take(MaxContextEvidence)
}
