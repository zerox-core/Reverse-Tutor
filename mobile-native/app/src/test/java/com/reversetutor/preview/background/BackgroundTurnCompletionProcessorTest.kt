package com.reversetutor.preview.background

import com.reversetutor.core.data.agent.AssistantReplyArtifactRepository
import com.reversetutor.core.data.agent.InMemoryAssistantReplyArtifactStore
import com.reversetutor.core.data.agent.InMemorySessionDocumentStore
import com.reversetutor.core.data.agent.InMemorySessionTableStore
import com.reversetutor.core.data.agent.InMemoryToolCallReceiptStore
import com.reversetutor.core.data.agent.SessionDocumentRepository
import com.reversetutor.core.data.agent.SessionTableRepository
import com.reversetutor.core.data.agent.SessionToolExecutionRepository
import com.reversetutor.core.data.agent.ToolCallReceiptRepository
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.data.background.BackgroundGenerationOutcome
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.llm.LlmRichContentBlock
import com.reversetutor.core.llm.LlmToolCall
import com.reversetutor.core.llm.StructuredTurnOutcome
import com.reversetutor.preview.wiring.session.PostTurnProjector
import com.reversetutor.preview.wiring.session.TurnProjectionSink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BackgroundTurnCompletionProcessorTest {

    @Test
    fun completionPersistsArtifactAndExecutesToolsWithoutWritingAnotherAssistant() = runBlocking {
        val artifactRepository = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore())
        val processor = BackgroundTurnCompletionProcessor(
            artifacts = artifactRepository,
            tools = SessionToolExecutionRepository(
                SessionDocumentRepository(InMemorySessionDocumentStore()),
                SessionTableRepository(InMemorySessionTableStore()),
                ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
            ),
            projector = PostTurnProjector(TurnProjectionSink { _, _ -> })
        )
        val completion = BackgroundGenerationOutcome.Completed(
            assistantMessageId = "assistant-1",
            replyEnvelope = LlmAssistantReplyEnvelope(
                blocks = listOf(LlmRichContentBlock.Paragraph("我们把这一步记下来。")),
                toolCalls = listOf(LlmToolCall("tool-1", "session_document.create", "{\"title\":\"学习笔记\"}"))
            )
        )

        val results = processor.process("job-1", job(), completion, 100L)

        assertEquals(1, results.size)
        val artifact = artifactRepository.read("session-1", "assistant-1")
        assertNotNull(artifact)
        assertEquals(listOf("document:document-tool-1"), artifact!!.toolResultCodes)
    }

    @Test
    fun repeatedCompletionReplaysToolReceiptRatherThanDuplicatingWrites() = runBlocking {
        val artifacts = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore())
        val processor = BackgroundTurnCompletionProcessor(
            artifacts,
            SessionToolExecutionRepository(
                SessionDocumentRepository(InMemorySessionDocumentStore()),
                SessionTableRepository(InMemorySessionTableStore()),
                ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
            ),
            PostTurnProjector(TurnProjectionSink { _, _ -> })
        )
        val completion = BackgroundGenerationOutcome.Completed(
            "assistant-1",
            replyEnvelope = LlmAssistantReplyEnvelope(
                listOf(LlmRichContentBlock.Paragraph("笔记已更新。")),
                toolCalls = listOf(LlmToolCall("tool-1", "session_document.create", "{\"title\":\"学习笔记\"}"))
            )
        )

        processor.process("job-1", job(), completion, 100L)
        val retry = processor.process("job-1", job(), completion, 101L)

        assertEquals("document:document-tool-1", retry.single().let { (it.safeResult as com.reversetutor.core.domain.ToolSafeResult.Document).let { result -> "document:${result.documentId}" } })
        assertEquals(listOf("document:document-tool-1"), artifacts.read("session-1", "assistant-1")!!.toolResultCodes)
    }

    private fun job() = BackgroundGenerationJob(
        id = "job-1", spaceId = "space-1", sessionId = "session-1", userMessageId = "message-1", userText = "帮我整理",
        token = LlmGenerationToken("token-1"), status = BackgroundJobStatus.Completed, createdAtEpochMillis = 1L
    )
}
