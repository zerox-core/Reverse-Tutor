package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.agent.AssistantReplyArtifactRepository
import com.reversetutor.core.data.agent.InMemoryAssistantReplyArtifactStore
import com.reversetutor.core.llm.LlmAssistantReplyEnvelope
import com.reversetutor.core.llm.LlmRichContentBlock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionRichReplyPortAdapterTest {
    @Test
    fun adapterPublishesBlocksAndOpaqueHandlesOnlyForOwningSession() = runBlocking {
        val artifacts = AssistantReplyArtifactRepository(InMemoryAssistantReplyArtifactStore())
        artifacts.save(
            assistantMessageId = "assistant-1",
            sessionId = "session-1",
            envelope = LlmAssistantReplyEnvelope(
                blocks = listOf(LlmRichContentBlock.CodeBlock("kotlin", "val x = 1")),
                evidenceReferenceIds = listOf("evidence-1")
            ),
            toolResultCodes = listOf("document:document-1", "rejected:tool_scope_denied"),
            nowEpochMillis = 100L
        )

        val port = SessionRichReplyPortAdapter(artifacts)
        val reply = port.load("session-1", "assistant-1")!!

        assertEquals("evidence-1", reply.evidence.single().id)
        assertEquals("document", reply.toolResults.first().kind)
        assertEquals("rejected", reply.toolResults.last().status)
        assertNull(port.load("session-2", "assistant-1"))
    }
}
