package com.reversetutor.core.data.agent

import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.llm.LlmRichContentBlock
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
}
