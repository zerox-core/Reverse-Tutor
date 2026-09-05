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
        knowledgePoint = SessionTurnContracts.sanitizeContractText(
            snapshot?.goal?.ifBlank { snapshot.title } ?: "", maxLength = 320
        ),
        learnerRole = SessionTurnContracts.sanitizeContractText(
            snapshot?.learnerRole?.ifBlank { "学习者" } ?: "学习者", maxLength = 120
        ),
        learnerProfile = SessionTurnContracts.sanitizeContractText(
            snapshot?.learnerProfile?.ifBlank { "未设置" } ?: "未设置", maxLength = 320
        ),
        learningPlan = SessionTurnContracts.sanitizeContractText(
            snapshot?.plan?.ifBlank { "未设置" } ?: "未设置", maxLength = 320
        ),
        dialogueStrategy = SessionTurnContracts.sanitizeContractText(
            snapshot?.dialogueStrategy?.ifBlank { "未设置" } ?: "未设置", maxLength = 320
        ),
        speakingTone = SessionTurnContracts.sanitizeContractText(
            snapshot?.speakingTone?.ifBlank { "自然" } ?: "自然", maxLength = 64
        ),
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
            id = "msg-${msg.messageId}",
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
            // The evidence id embeds the source revision, so a snapshot taken
            // before a re-import keeps its old, version-pinned id while the
            // next turn naturally binds to the new revision.
            id = "source:${src.id}:${src.sourceRevision.ifBlank { "legacy" }}",
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
            id = "gaps-$sessionId",
            title = "\u77e5\u8bc6\u7f3a\u53e3",
            body = SessionTurnContracts.sanitizeContractText(prerequisiteGaps.joinToString("; "), maxLength = MaxEvidenceBodyChars),
            kind = "Gaps"
        ))
    }

    if (pendingReviewKnowledgePoints.isNotEmpty()) {
        evidence.add(LlmContextEvidence(
            id = "review-$sessionId",
            title = "\u5f85\u590d\u4e60",
            body = SessionTurnContracts.sanitizeContractText(pendingReviewKnowledgePoints.joinToString("; "), maxLength = MaxEvidenceBodyChars),
            kind = "Review"
        ))
    }

    return evidence.take(MaxContextEvidence)
}

/**
 * Projects the immutable session template into one bounded evidence item so
 * the generation runtime receives the same persona/goal constraints that the
 * policy mapper used. Raw configuration maps and image references are never
 * forwarded.
 */
internal fun NewSessionConfiguration?.toLlmTemplateEvidence(): LlmContextEvidence? {
    val snapshot = this ?: return null
    val body = buildString {
        append("角色=").append(snapshot.learnerRole.ifBlank { "学习者" }).append('\n')
        append("画像=").append(snapshot.learnerProfile.ifBlank { "未设置" }).append('\n')
        append("目标=").append(snapshot.goal.ifBlank { snapshot.title }.ifBlank { "未设置" }).append('\n')
        append("计划=").append(snapshot.plan.ifBlank { "未设置" }).append('\n')
        append("对话策略=").append(snapshot.dialogueStrategy.ifBlank { "未设置" }).append('\n')
        append("语气=").append(snapshot.speakingTone.ifBlank { "自然" })
    }
    return LlmContextEvidence(
        id = "template",
        title = "会话模板",
        body = SessionTurnContracts.sanitizeContractText(body, maxLength = MaxEvidenceBodyChars),
        kind = "Template"
    )
}
