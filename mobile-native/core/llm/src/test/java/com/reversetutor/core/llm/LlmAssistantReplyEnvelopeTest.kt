package com.reversetutor.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmAssistantReplyEnvelopeTest {

    @Test
    fun parserKeepsOnlyAllowedReferencesAndToolCalls() {
        val parsed = LlmAssistantReplyEnvelopeParser.parse(
            rawText = """{"version":"v1","blocks":[{"type":"heading","level":2,"text":"标题"}],"evidenceReferenceIds":["source-1"],"toolCalls":[{"callId":"call-1","name":"session_document.create","argumentsJson":"{}"}],"outcome":{}}""",
            allowedEvidenceIds = setOf("source-1")
        )

        assertEquals(listOf("source-1"), parsed.evidenceReferenceIds)
        assertEquals("session_document.create", parsed.toolCalls.single().name)
        assertEquals("标题", (parsed.blocks.single() as LlmRichContentBlock.Heading).text)
    }

    @Test
    fun malformedEnvelopeFallsBackToPlainParagraphAndEmptySidebands() {
        val malformed = LlmAssistantReplyEnvelopeParser.parse(
            rawText = "{not json}",
            allowedEvidenceIds = emptySet()
        )

        assertEquals(StructuredTurnOutcome.EMPTY, malformed.outcome)
        assertTrue(malformed.blocks.single() is LlmRichContentBlock.Paragraph)
        assertTrue(malformed.evidenceReferenceIds.isEmpty())
        assertTrue(malformed.toolCalls.isEmpty())
    }

    @Test
    fun unknownEvidenceReferenceFailsClosed() {
        val parsed = LlmAssistantReplyEnvelopeParser.parse(
            rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"正文"}],"evidenceReferenceIds":["unknown"],"toolCalls":[],"outcome":{}}""",
            allowedEvidenceIds = setOf("source-1")
        )

        assertTrue(parsed.evidenceReferenceIds.isEmpty())
        assertTrue(parsed.toolCalls.isEmpty())
        assertTrue(parsed.blocks.single() is LlmRichContentBlock.Paragraph)
    }

    @Test
    fun unsupportedOrSensitiveToolCallFailsClosed() {
        val parsed = LlmAssistantReplyEnvelopeParser.parse(
            rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"正文"}],"evidenceReferenceIds":[],"toolCalls":[{"callId":"call-1","name":"shell.execute","argumentsJson":"{}"}],"outcome":{}}""",
            allowedEvidenceIds = emptySet()
        )

        assertTrue(parsed.evidenceReferenceIds.isEmpty())
        assertTrue(parsed.toolCalls.isEmpty())
        assertTrue(parsed.blocks.single() is LlmRichContentBlock.Paragraph)
    }
}
