package com.reversetutor.core.data.agent

import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.llm.LlmRichContentBlock
import com.reversetutor.core.llm.LlmSourceCheckRule
import com.reversetutor.core.llm.LlmSourceGroundedCheckPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantReplyArtifactRepositoryTest {

    @Test
    fun artifactStoresOnlyValidatedBlocksAndOpaqueReferenceHandles() = kotlinx.coroutines.runBlocking {
        val repository = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore())
        repository.save(
            assistantMessageId = "assistant-1",
            sessionId = "session-1",
            envelope = LlmAssistantReplyEnvelope(
                blocks = listOf(LlmRichContentBlock.Paragraph("先检查定义域。")),
                evidenceReferenceIds = listOf("source-1")
            ),
            nowEpochMillis = 100L
        )

        val artifact = repository.read("session-1", "assistant-1")!!
        assertEquals(listOf("source-1"), artifact.evidenceReferenceIds)
        assertEquals("先检查定义域。", (artifact.blocks.single() as RichDocumentBlock.Paragraph).text)
        assertFalse(artifact.toString().contains("https://"))
        assertTrue(repository.read("session-2", "assistant-1") == null)
    }

    @Test
    fun artifactRestoresBoundedCheckPlanAfterRestart() = kotlinx.coroutines.runBlocking {
        val repository = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore())
        val plan = LlmSourceGroundedCheckPlan(
            id = "check-1",
            sourceRevision = "rev-book-1",
            sourceReferenceIds = listOf("source:book:rev-book-1"),
            prompt = "说明定义",
            expectedAnswer = "偶函数",
            rule = LlmSourceCheckRule.ExactText("偶函数"),
            conceptKey = "函数"
        )
        repository.save(
            assistantMessageId = "assistant-check",
            sessionId = "session-1",
            envelope = LlmAssistantReplyEnvelope(
                blocks = listOf(LlmRichContentBlock.Paragraph("偶函数")),
                checkPlan = plan
            ),
            nowEpochMillis = 100L
        )

        val restored = repository.read("session-1", "assistant-check")!!.checkPlan
        assertEquals(plan.normalized(), restored)
    }
}
