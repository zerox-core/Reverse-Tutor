package com.reversetutor.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun parserKeepsBoundedSourceCheckOnlyWhenReferencesAreWhitelisted() {
        val parsed = LlmAssistantReplyEnvelopeParser.parseValidated(
            rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"复述"}],"evidenceReferenceIds":["source:book:rev-1"],"toolCalls":[],"outcome":{},"checkPlan":{"id":"check-1","sourceRevision":"rev-1","sourceReferenceIds":["source:book:rev-1"],"prompt":"说明定义","expectedAnswer":"偶函数","rule":{"type":"exact_text","normalizedAnswer":"偶函数"},"conceptKey":"函数"}}""",
            allowedEvidenceIds = setOf("source:book:rev-1")
        )

        assertEquals("rev-1", parsed?.checkPlan?.sourceRevision)
        assertTrue(parsed?.checkPlan?.rule is LlmSourceCheckRule.ExactText)
    }

    @Test
    fun parserDropsSourceCheckWithUnknownReferenceWithoutDroppingChat() {
        val parsed = LlmAssistantReplyEnvelopeParser.parseValidated(
            rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"复述"}],"evidenceReferenceIds":[],"toolCalls":[],"outcome":{},"checkPlan":{"id":"check-1","sourceRevision":"rev-1","sourceReferenceIds":["source:book:rev-1"],"prompt":"说明定义","expectedAnswer":"偶函数","rule":{"type":"exact_text","normalizedAnswer":"偶函数"},"conceptKey":"函数"}}""",
            allowedEvidenceIds = setOf("source:other:rev-2")
        )

        assertTrue(parsed != null)
        assertEquals(null, parsed?.checkPlan)
    }

    // Task 2: all four rule kinds parse into their typed candidates.
    @Test
    fun allFourCheckRuleKindsParseWhenReferencesAreWhitelisted() {
        val rules = listOf(
            """{"type":"exact_text","normalizedAnswer":"even-function"}""" to "ExactText",
            """{"type":"numeric_tolerance","expected":4.0,"tolerance":0.5}""" to "NumericTolerance",
            """{"type":"required_concepts","terms":["root","discriminant"]}""" to "RequiredConcepts",
            """{"type":"rubric","criteria":["complete explanation"]}""" to "Rubric"
        )
        rules.forEach { (ruleJson, _) ->
            val parsed = LlmAssistantReplyEnvelopeParser.parseValidated(
                rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"recap"}],"evidenceReferenceIds":["source:book:rev-1"],"toolCalls":[],"outcome":{},"checkPlan":{"id":"check-rule","sourceRevision":"rev-1","sourceReferenceIds":["source:book:rev-1"],"prompt":"explain definition","expectedAnswer":"answer","rule":$ruleJson,"conceptKey":"function"}}""",
                allowedEvidenceIds = setOf("source:book:rev-1")
            )
            org.junit.Assert.assertNotNull("rule kind must parse: $ruleJson", parsed?.checkPlan?.rule)
        }
    }

    // Task 2: an unknown rule kind drops the plan, never the chat reply.
    @Test
    fun unknownCheckRuleKindDropsPlanButKeepsChat() {
        val parsed = LlmAssistantReplyEnvelopeParser.parseValidated(
            rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"recap"}],"evidenceReferenceIds":["source:book:rev-1"],"toolCalls":[],"outcome":{},"checkPlan":{"id":"check-bad","sourceRevision":"rev-1","sourceReferenceIds":["source:book:rev-1"],"prompt":"explain definition","expectedAnswer":"answer","rule":{"type":"guarantee_mastery","value":"passed"},"conceptKey":"function"}}""",
            allowedEvidenceIds = setOf("source:book:rev-1")
        )
        assertTrue(parsed != null)
        assertEquals(null, parsed?.checkPlan)
        assertEquals("recap", parsed?.blocks?.single()?.let { (it as LlmRichContentBlock.Paragraph).text })
    }

    // Task 2: secret-like fields in the plan reject the plan without failing the reply.
    @Test
    fun sensitiveFieldsInCheckPlanAreRejected() {
        for (needle in listOf(
            "see https://leak.invalid/x",
            "key sk-abcdef123456",
            "Authorization: Bearer tok-1"
        )) {
            val parsed = LlmAssistantReplyEnvelopeParser.parseValidated(
                rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"recap"}],"evidenceReferenceIds":["source:book:rev-1"],"toolCalls":[],"outcome":{},"checkPlan":{"id":"check-secret","sourceRevision":"rev-1","sourceReferenceIds":["source:book:rev-1"],"prompt":"$needle","expectedAnswer":"answer","rule":{"type":"exact_text","normalizedAnswer":"answer"},"conceptKey":"function"}}""",
                allowedEvidenceIds = setOf("source:book:rev-1")
            )
            assertTrue("reply must survive", parsed != null)
            assertEquals("sensitive plan must be dropped: $needle", null, parsed?.checkPlan)
        }
    }

    // V1-004 Task 1: explicit per-handle revisions parse; a partial map drops
    // the plan while the chat reply survives untouched.
    @Test
    fun parserHandlesExplicitRevisionMapAndPartialMapFailsClosed() {
        val base = """"version":"v1","blocks":[{"type":"paragraph","text":"复述"}","""
        val full = LlmAssistantReplyEnvelopeParser.parseValidated(
            rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"复述"}],"evidenceReferenceIds":["source:book","source:map"],"toolCalls":[],"outcome":{},"checkPlan":{"id":"check-map","sourceRevision":"rev-primary","sourceReferenceIds":["source:book","source:map"],"sourceRevisions":{"source:book":"rev-a","source:map":"rev-b"},"prompt":"联合定义","expectedAnswer":"答案","rule":{"type":"exact_text","normalizedAnswer":"答案"},"conceptKey":"函数"}}""",
            allowedEvidenceIds = setOf("source:book", "source:map")
        )
        assertEquals("rev-a", full?.checkPlan?.sourceRevisions?.get("source:book"))
        assertEquals("rev-b", full?.checkPlan?.sourceRevisions?.get("source:map"))

        val partial = LlmAssistantReplyEnvelopeParser.parseValidated(
            rawText = """{"version":"v1","blocks":[{"type":"paragraph","text":"复述"}],"evidenceReferenceIds":["source:book","source:map"],"toolCalls":[],"outcome":{},"checkPlan":{"id":"check-map","sourceRevision":"rev-primary","sourceReferenceIds":["source:book","source:map"],"sourceRevisions":{"source:book":"rev-a"},"prompt":"联合定义","expectedAnswer":"答案","rule":{"type":"exact_text","normalizedAnswer":"答案"},"conceptKey":"函数"}}""",
            allowedEvidenceIds = setOf("source:book", "source:map")
        )
        assertTrue("chat must survive a dropped plan", partial != null)
        assertEquals(null, partial?.checkPlan)
    }

    @Test
    fun visibleTimelineTextDropsInternalTeachingLabelsAndBoundsLongTurns() {
        val envelope = LlmAssistantReplyEnvelope(
            blocks = listOf(
                LlmRichContentBlock.Paragraph(
                    "Teaching policy:\nAction: probe\nKnowledge point: factoring\n先说第一步。\n" +
                        "x".repeat(2_000)
                )
            ),
            outcome = StructuredTurnOutcome(
                windowId = "window-1",
                actionType = "probe",
                knowledgePoint = "factoring",
                evidenceType = "explanation",
                evidenceStatus = "passed"
            )
        )

        val visible = envelope.timelineText()

        assertTrue(visible.contains("先说第一步。"))
        assertFalse(visible.contains("Teaching policy:"))
        assertFalse(visible.contains("Action: probe"))
        assertFalse(visible.contains("Knowledge point: factoring"))
        assertTrue(visible.length <= 1_200)
    }
}
