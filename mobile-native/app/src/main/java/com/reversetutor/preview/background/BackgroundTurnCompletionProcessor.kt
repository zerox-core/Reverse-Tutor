package com.reversetutor.preview.background

import com.reversetutor.core.data.agent.AssistantReplyArtifactRepository
import com.reversetutor.core.data.agent.SessionToolExecutionRepository
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.domain.SessionToolCall
import com.reversetutor.core.domain.SessionToolResult
import com.reversetutor.core.domain.ToolSafeResult
import com.reversetutor.preview.wiring.session.PostTurnProjector

/**
 * Runs only after the existing generation repository has written the assistant
 * message. It cannot generate text or write another assistant message.
 */
class BackgroundTurnCompletionProcessor(
    private val artifacts: AssistantReplyArtifactRepository,
    private val tools: SessionToolExecutionRepository,
    private val projector: PostTurnProjector
) {
    suspend fun process(
        jobId: String,
        job: BackgroundGenerationJob,
        outcome: BackgroundGenerationOutcome.Completed,
        nowEpochMillis: Long
    ): List<SessionToolResult> {
        val envelope = outcome.replyEnvelope
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
        }
        projector.project(jobId, outcome.structuredOutcome)
        return results
    }
}

private fun SessionToolResult.toArtifactCode(): String = when (val value = safeResult) {
    is ToolSafeResult.Document -> "document:${value.documentId}"
    is ToolSafeResult.Table -> "table:${value.tableId}:${value.rowCount}"
    is ToolSafeResult.ReferenceOpenTarget -> "reference:${value.sourceId}"
    is ToolSafeResult.Rejected -> "rejected:${value.code}"
}
