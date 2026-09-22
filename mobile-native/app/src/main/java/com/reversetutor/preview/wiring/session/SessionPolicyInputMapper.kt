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
private const val MaxRecentMessageEvidence = 2
private const val MaxLightweightEvidenceBodyChars = 400

/**
 * Legacy engine's due-review soft hint (old `build_system_prompt`): a due
 * review must not interrupt the current teaching step; whether the student
 * weaves the point back in depends on the teacher's reply.
 */
private const val DueReviewSoftHint =
    "（到期复习软提示：不强制打断当前推进，是否带回视用户回复决定）"

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
 * Converts a [ConversationContextContract] into bounded [LlmContextEvidence]
 * for learning turns. Uses only the [MaxRecentMessageEvidence] most recent
 * messages, memory, sources, gaps and review points; per-concept mastery
 * and historical errors are decision-layer inputs already folded into the
 * deterministic turn note, so they are not duplicated here. Text is capped
 * with [SessionTurnContracts.sanitizeContractText].
 */
internal fun ConversationContextContract.toLlmContextEvidence(): List<LlmContextEvidence> {
    val evidence = mutableListOf<LlmContextEvidence>()

    // NEWMP-V1-017 old parity: the legacy engine injected the compressed
    // early-history digest into the system prompt ahead of everything else
    // ("# 早期对话摘要（已压缩，其后附最近原文）"), so the digest goes FIRST —
    // ahead of even the aggregates — and therefore always survives the cap.
    if (earlyHistoryDigest.isNotBlank()) {
        evidence.add(LlmContextEvidence(
            id = "summary-" + sessionId,
            title = "\u65e9\u671f\u5bf9\u8bdd\u6458\u8981",
            body = SessionTurnContracts.sanitizeContractText(
                "\uff08\u5df2\u538b\u7f29\u7684\u65e9\u671f\u5bf9\u8bdd\uff0c\u5176\u540e\u4e3a\u6700\u8fd1\u539f\u6587\uff09\n" + earlyHistoryDigest,
                maxLength = MaxEvidenceBodyChars
            ),
            kind = "Summary"
        ))
    }

    // Old-parity ordering: the legacy engine injected prerequisite gaps and
    // due reviews into the system prompt ahead of per-item evidence, so the
    // aggregates go FIRST here too — this also guarantees they survive the
    // bounded evidence cap below instead of being starved by message
    // evidence.
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
            body = SessionTurnContracts.sanitizeContractText(
                pendingReviewKnowledgePoints.joinToString("; ") + DueReviewSoftHint,
                maxLength = MaxEvidenceBodyChars
            ),
            kind = "Review"
        ))
    }

    recentMessages.take(MaxRecentMessageEvidence).forEach { msg ->
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

    // Expression-loop speed pass (2026-09-20 拍板): per-concept mastery rows
    // are decision-layer inputs — the deterministic turn note already carries
    // the current concept's mastery score, so they are not duplicated into
    // prompt evidence.

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

    return evidence.take(MaxContextEvidence)
}

/**
 * Expression-loop speed pass (2026-09-20 拍板): casual (OffTopic) and
 * goal-change turns carry only the [MaxRecentMessageEvidence] most recent
 * messages, bounded tighter than the learning path. The digest, aggregates,
 * memory, sources and errors are all skipped — such a reply only needs the
 * persona (the caller still adds the session-template evidence) plus the
 * freshest conversational context, which keeps the whole prompt in the
 * ~1.5k-char range so provider prefill stops dominating casual-turn latency.
 */
internal fun ConversationContextContract.toLlmLightweightContextEvidence(): List<LlmContextEvidence> =
    recentMessages.take(MaxRecentMessageEvidence).map { msg ->
        LlmContextEvidence(
            id = "msg-${msg.messageId}",
            title = SessionTurnContracts.sanitizeContractText(msg.role, maxLength = 48),
            body = SessionTurnContracts.sanitizeContractText(msg.text, maxLength = MaxLightweightEvidenceBodyChars),
            kind = "Message",
            sourceMessageId = msg.messageId
        )
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
        // R69：状态/截止/里程碑此前只在设置页可见，AI 生成时收不到——
        // 接线进模板证据，每条消息发出时随会话模板一起送达（未设置则省略）。
        if (snapshot.deadline.isNotBlank() && snapshot.deadline != "未设置") {
            append("截止=").append(snapshot.deadline).append('\n')
        }
        if (snapshot.currentState.isNotBlank() && snapshot.currentState != "未设置") {
            append("当前状态=").append(snapshot.currentState).append('\n')
        }
        if (snapshot.stageMilestones.isNotBlank() && snapshot.stageMilestones != "未设置") {
            append("阶段里程碑=").append(snapshot.stageMilestones).append('\n')
        }
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
