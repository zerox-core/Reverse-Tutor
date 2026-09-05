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
import com.reversetutor.core.llm.LlmTurnPlan
import com.reversetutor.core.llm.LlmAssistantTurnEnvelope
import com.reversetutor.core.llm.LlmWindowContext
import com.reversetutor.core.llm.StructuredTurnOutcome
import com.reversetutor.core.llm.LlmSourceCheckRule
import com.reversetutor.core.llm.LlmSourceGroundedCheckPlan
import com.reversetutor.preview.wiring.session.PostTurnProjector
import com.reversetutor.preview.wiring.session.TurnProjectionSink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun sourceCheckUsesAssistantCandidateAndProjectsOnlyVerifiedEvidence() = runBlocking {
        val received = mutableListOf<StructuredTurnOutcome>()
        val processor = BackgroundTurnCompletionProcessor(
            artifacts = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore()),
            tools = SessionToolExecutionRepository(
                SessionDocumentRepository(InMemorySessionDocumentStore()),
                SessionTableRepository(InMemorySessionTableStore()),
                ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
            ),
            projector = PostTurnProjector(TurnProjectionSink { _, outcome -> received += outcome }),
            loadCurrentSourceRevision = { _, _ -> "rev-book-1" }
        )
        val handle = "source:book:rev-book-1"
        val completion = BackgroundGenerationOutcome.Completed(
            assistantMessageId = "assistant-1",
            structuredOutcome = StructuredTurnOutcome(windowId = "window-1", knowledgePoint = "函数"),
            replyEnvelope = LlmAssistantReplyEnvelope(
                blocks = listOf(LlmRichContentBlock.Paragraph("偶函数")),
                evidenceReferenceIds = listOf(handle),
                checkPlan = LlmSourceGroundedCheckPlan(
                    id = "check-1", sourceRevision = "rev-book-1", sourceReferenceIds = listOf(handle),
                    prompt = "说明定义", expectedAnswer = "偶函数",
                    rule = LlmSourceCheckRule.ExactText("偶函数"), conceptKey = "函数"
                )
            )
        )

        processor.process(
            jobId = "job-check", job = job().copy(
                contextEvidence = listOf(com.reversetutor.core.llm.LlmContextEvidence(handle, "书", "定义", "Source")),
                assistantTurnEnvelope = LlmAssistantTurnEnvelope(
                    window = LlmWindowContext("window-1", "window-1"),
                    turnPlan = LlmTurnPlan(intent = "verify", actionType = "practice", studentRole = "student", knowledgePoint = "函数")
                )
            ), outcome = completion, nowEpochMillis = 100L
        )

        assertEquals(1, received.size)
        assertEquals("passed", received.single().evidenceStatus)
        assertEquals(1f, received.single().correctness, 0.0001f)
    }

    private fun job() = BackgroundGenerationJob(
        id = "job-1", spaceId = "space-1", sessionId = "session-1", userMessageId = "message-1", userText = "帮我整理",
        token = LlmGenerationToken("token-1"), status = BackgroundJobStatus.Completed, createdAtEpochMillis = 1L
    )

    // Task 1: every referenced source handle must still match its live revision.
    @Test
    fun multiSourceCheckRejectsWhenAnyReferencedRevisionDrifts() = runBlocking {
        val received = mutableListOf<StructuredTurnOutcome>()
        val processor = BackgroundTurnCompletionProcessor(
            artifacts = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore()),
            tools = SessionToolExecutionRepository(
                SessionDocumentRepository(InMemorySessionDocumentStore()),
                SessionTableRepository(InMemorySessionTableStore()),
                ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
            ),
            projector = PostTurnProjector(TurnProjectionSink { _, outcome -> received += outcome }),
            loadCurrentSourceRevision = { _, handle ->
                when {
                    handle.contains("book") -> "rev-book-1"
                    else -> "rev-map-1" // the map handle referenced rev-map-9: drift
                }
            }
        )
        processor.process(
            jobId = "job-multi",
            job = job().copy(
                contextEvidence = listOf(
                    com.reversetutor.core.llm.LlmContextEvidence("source:book:rev-book-1", "书", "定义", "Source"),
                    com.reversetutor.core.llm.LlmContextEvidence("source:map:rev-map-9", "图", "边界", "Source")
                )
            ),
            outcome = completedCheck(
                sourceRevision = "rev-book-1",
                handles = listOf("source:book:rev-book-1", "source:map:rev-map-9")
            ),
            nowEpochMillis = 100L
        )
        assertEquals(0, received.size)
    }

    // Task 1: a deleted or unreadable source degrades to no evidence, never a failure record.
    @Test
    fun deletedSourceYieldsNoLearningFact() = runBlocking {
        val received = mutableListOf<StructuredTurnOutcome>()
        val processor = BackgroundTurnCompletionProcessor(
            artifacts = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore()),
            tools = SessionToolExecutionRepository(
                SessionDocumentRepository(InMemorySessionDocumentStore()),
                SessionTableRepository(InMemorySessionTableStore()),
                ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
            ),
            projector = PostTurnProjector(TurnProjectionSink { _, outcome -> received += outcome }),
            loadCurrentSourceRevision = { _, _ -> null }
        )
        processor.process(
            jobId = "job-deleted",
            job = job().copy(
                contextEvidence = listOf(
                    com.reversetutor.core.llm.LlmContextEvidence("source:book:rev-book-1", "书", "定义", "Source")
                )
            ),
            outcome = completedCheck(sourceRevision = "rev-book-1", handles = listOf("source:book:rev-book-1")),
            nowEpochMillis = 100L
        )
        assertEquals(0, received.size)
    }

    private fun completedCheck(
        sourceRevision: String,
        handles: List<String>
    ): BackgroundGenerationOutcome.Completed = BackgroundGenerationOutcome.Completed(
        assistantMessageId = "assistant-1",
        structuredOutcome = StructuredTurnOutcome(windowId = "window-1", knowledgePoint = "函数"),
        replyEnvelope = LlmAssistantReplyEnvelope(
            blocks = listOf(LlmRichContentBlock.Paragraph("偶函数")),
            evidenceReferenceIds = handles,
            checkPlan = LlmSourceGroundedCheckPlan(
                id = "check-multi", sourceRevision = sourceRevision, sourceReferenceIds = handles,
                prompt = "说明定义", expectedAnswer = "偶函数",
                rule = LlmSourceCheckRule.ExactText("偶函数"), conceptKey = "函数"
            )
        )
    )

    // Task 3: a locally verified WRONG answer records a failed evidence receipt â
    // never a mastery/passed judgement.
    @Test
    fun verifiedFailureProjectsFailedReceiptNotMastery() = runBlocking {
        val received = mutableListOf<StructuredTurnOutcome>()
        val processor = BackgroundTurnCompletionProcessor(
            artifacts = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore()),
            tools = SessionToolExecutionRepository(
                SessionDocumentRepository(InMemorySessionDocumentStore()),
                SessionTableRepository(InMemorySessionTableStore()),
                ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
            ),
            projector = PostTurnProjector(TurnProjectionSink { _, outcome -> received += outcome }),
            loadCurrentSourceRevision = { _, _ -> "rev-book-1" }
        )
        processor.process(
            jobId = "job-wrong",
            job = job().copy(
                contextEvidence = listOf(
                    com.reversetutor.core.llm.LlmContextEvidence("source:book:rev-book-1", "book", "definition", "Source")
                )
            ),
            outcome = BackgroundGenerationOutcome.Completed(
                assistantMessageId = "assistant-1",
                structuredOutcome = StructuredTurnOutcome(
                    windowId = "window-1", knowledgePoint = "function",
                    correctness = 1f, depth = 1f, evidenceType = "explanation", evidenceStatus = "passed"
                ),
                replyEnvelope = LlmAssistantReplyEnvelope(
                    blocks = listOf(LlmRichContentBlock.Paragraph("odd-function")),
                    evidenceReferenceIds = listOf("source:book:rev-book-1"),
                    checkPlan = LlmSourceGroundedCheckPlan(
                        id = "check-w", sourceRevision = "rev-book-1",
                        sourceReferenceIds = listOf("source:book:rev-book-1"),
                        prompt = "explain definition", expectedAnswer = "even-function",
                        rule = LlmSourceCheckRule.ExactText("even-function"), conceptKey = "function"
                    )
                )
            ),
            nowEpochMillis = 100L
        )
        assertEquals(1, received.size)
        assertEquals("failed", received.single().evidenceStatus)
        assertEquals(0f, received.single().correctness, 0.0001f)
    }

    // Task 3: an ordinary chat turn without a check plan writes no learning fact.
    @Test
    fun chatOnlyTurnProjectsNoLearningFact() = runBlocking {
        val received = mutableListOf<StructuredTurnOutcome>()
        val processor = BackgroundTurnCompletionProcessor(
            artifacts = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore()),
            tools = SessionToolExecutionRepository(
                SessionDocumentRepository(InMemorySessionDocumentStore()),
                SessionTableRepository(InMemorySessionTableStore()),
                ToolCallReceiptRepository(InMemoryToolCallReceiptStore())
            ),
            projector = PostTurnProjector(TurnProjectionSink { _, outcome -> received += outcome }),
            loadCurrentSourceRevision = { _, _ -> "rev-book-1" }
        )
        processor.process(
            jobId = "job-chat",
            job = job(),
            outcome = BackgroundGenerationOutcome.Completed(
                assistantMessageId = "assistant-chat",
                structuredOutcome = StructuredTurnOutcome(windowId = "window-1", knowledgePoint = "casual-chat"),
                replyEnvelope = LlmAssistantReplyEnvelope(
                    blocks = listOf(LlmRichContentBlock.Paragraph("rough day"))
                )
            ),
            nowEpochMillis = 100L
        )
        assertEquals(0, received.size)
    }

    // Task 5: a rejected tool call never suppresses the assistant artifact.
    @Test
    fun rejectedToolCallKeepsAssistantArtifact() = runBlocking {
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
            assistantMessageId = "assistant-rejected",
            replyEnvelope = LlmAssistantReplyEnvelope(
                blocks = listOf(LlmRichContentBlock.Paragraph("normal student reply.")),
                toolCalls = listOf(LlmToolCall("evil-1", "drop_everything", "{}"))
            )
        )

        val results = processor.process("job-rejected", job(), completion, 100L)

        assertTrue(
            "tool must be rejected",
            (results.single().safeResult as? com.reversetutor.core.domain.ToolSafeResult.Rejected) != null
        )
        val artifact = artifactRepository.read("session-1", "assistant-rejected")
        org.junit.Assert.assertNotNull("assistant artifact must survive the failed tool", artifact)
        assertEquals(
            "normal student reply.",
            (artifact!!.blocks.single() as com.reversetutor.core.data.agent.RichDocumentBlock.Paragraph).text
        )
    }
}
