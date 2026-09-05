package com.reversetutor.preview.background

import com.reversetutor.core.data.agent.AssistantReplyArtifactRepository
import com.reversetutor.core.data.agent.SessionToolExecutionRepository
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.domain.SessionToolCall
import com.reversetutor.core.domain.SessionToolResult
import com.reversetutor.core.domain.ToolSafeResult
import com.reversetutor.core.llm.timelineText
import com.reversetutor.preview.wiring.session.PostTurnProjector
import com.reversetutor.preview.wiring.session.SourceGroundedEvidenceVerifier
import com.reversetutor.preview.wiring.session.SourceGroundedLocalVerifier
import com.reversetutor.preview.wiring.session.toDomainCheckPlan
import com.reversetutor.preview.wiring.session.LocalLearningEvidenceInput

/**
 * Runs only after the existing generation repository has written the assistant
 * message. It cannot generate text or write another assistant message.
 */
class BackgroundTurnCompletionProcessor(
    private val artifacts: AssistantReplyArtifactRepository,
    private val tools: SessionToolExecutionRepository,
    private val projector: PostTurnProjector,
    private val sourceGroundedVerifier: SourceGroundedEvidenceVerifier = SourceGroundedLocalVerifier(),
    private val loadCurrentSourceRevision: suspend (String, String) -> String? = { _, _ -> null }
) {
    suspend fun process(
        jobId: String,
        job: BackgroundGenerationJob,
        outcome: BackgroundGenerationOutcome.Completed,
        nowEpochMillis: Long
    ): List<SessionToolResult> {
        val envelope = outcome.replyEnvelope
        val storedArtifact = if (envelope == null) {
            artifacts.read(job.sessionId, outcome.assistantMessageId)
        } else {
            null
        }
        val results = envelope?.toolCalls.orEmpty().map { call ->
            tools.execute(
                spaceId = job.spaceId,
                currentSessionId = job.sessionId,
                call = SessionToolCall(call.callId, call.name, job.sessionId, call.argumentsJson),
                allowedReferenceIds = job.contextEvidence.map { it.id }.toSet(),
                nowEpochMillis = nowEpochMillis
            )
        }
        if (envelope != null) {
            artifacts.save(
                assistantMessageId = outcome.assistantMessageId,
                sessionId = job.sessionId,
                envelope = envelope,
                toolResultCodes = results.map(SessionToolResult::toArtifactCode),
                nowEpochMillis = nowEpochMillis
            )
            val allowedSourceHandles = job.contextEvidence
                .filter { it.kind.equals("Source", ignoreCase = true) }
                .map { it.id }
                .toSet()
            val checkPlan = envelope.checkPlan?.toDomainCheckPlan(allowedSourceHandles)
            if (checkPlan != null) {
                val currentRevision = currentRevisionForCheck(job.spaceId, checkPlan.sourceHandles)
                projector.projectCheck(
                    input = LocalLearningEvidenceInput(
                        jobId = jobId,
                        plan = checkPlan,
                        candidateAnswer = envelope.timelineText(),
                        currentSourceRevision = currentRevision
                    ),
                    outcome = outcome.structuredOutcome.copy(
                        knowledgePoint = outcome.structuredOutcome.knowledgePoint.ifBlank { checkPlan.conceptKey }
                    ),
                    verifier = sourceGroundedVerifier
                )
            }
        } else if (storedArtifact != null) {
            // A completed job can be replayed after process death. The durable
            // artifact is the source of the bounded check plan and candidate
            // blocks; no Provider call or second assistant write is needed.
            val checkPlan = storedArtifact.checkPlan?.toDomainCheckPlan(
                job.contextEvidence.filter { it.kind.equals("Source", ignoreCase = true) }
                    .map { it.id }.toSet()
            )
            if (checkPlan != null) {
                val currentRevision = currentRevisionForCheck(job.spaceId, checkPlan.sourceHandles)
                projector.projectCheck(
                    input = LocalLearningEvidenceInput(
                        jobId = jobId,
                        plan = checkPlan,
                        candidateAnswer = storedArtifact.blocks.joinToString("\n") { it.toCandidateText() },
                        currentSourceRevision = currentRevision
                    ),
                    outcome = outcome.structuredOutcome.copy(
                        knowledgePoint = outcome.structuredOutcome.knowledgePoint.ifBlank { checkPlan.conceptKey }
                    ),
                    verifier = sourceGroundedVerifier
                )
            }
        }
        projector.project(jobId, outcome.structuredOutcome)
        return results
    }

    /**
     * Plan Task 1: verify the *current* revision of every source handle that a
     * check plan references. A drifted, deleted or unreadable handle yields "",
     * which never equals the plan revision, so the whole plan comes back
     * Unverified and no learning fact is written. When all handles are current,
     * the first handle's live revision feeds the version check.
     */
    private suspend fun currentRevisionForCheck(
        spaceId: String,
        handles: List<String>
    ): String {
        if (handles.isEmpty()) return ""
        handles.forEach { handle ->
            val embedded = handle.substringAfterLast(":", "")
            val live = loadCurrentSourceRevision(spaceId, handle) ?: return ""
            if (embedded.isNotEmpty() && live != embedded) return ""
        }
        return loadCurrentSourceRevision(spaceId, handles.first()).orEmpty()
    }
}


private fun com.reversetutor.core.data.agent.RichDocumentBlock.toCandidateText(): String = when (this) {
    is com.reversetutor.core.data.agent.RichDocumentBlock.Heading -> text
    is com.reversetutor.core.data.agent.RichDocumentBlock.Paragraph -> text
    is com.reversetutor.core.data.agent.RichDocumentBlock.BulletList -> items.joinToString("\n")
    is com.reversetutor.core.data.agent.RichDocumentBlock.NumberedList -> items.joinToString("\n")
    is com.reversetutor.core.data.agent.RichDocumentBlock.CodeBlock -> code
    is com.reversetutor.core.data.agent.RichDocumentBlock.Callout -> text
    is com.reversetutor.core.data.agent.RichDocumentBlock.SimpleTable -> rows.joinToString("\n") { it.joinToString(" | ") }
}

private fun SessionToolResult.toArtifactCode(): String = when (val value = safeResult) {
    is ToolSafeResult.Document -> "document:${value.documentId}"
    is ToolSafeResult.Table -> "table:${value.tableId}:${value.rowCount}"
    is ToolSafeResult.ReferenceOpenTarget -> "reference:${value.sourceId}"
    is ToolSafeResult.Rejected -> "rejected:${value.code}"
}
