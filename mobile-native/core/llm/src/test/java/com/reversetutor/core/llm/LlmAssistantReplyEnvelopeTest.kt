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

    // NEWMP-V1-006 Task 1: every internal structured field label must be
    // filtered from the visible chat projection, while genuine student prose
    // on either side of the labels stays visible.
    @Test
    fun visibleTimelineTextHidesOutcomeCheckPlanCorrectnessAndMasteryLabels() {
        val envelope = LlmAssistantReplyEnvelope(
            blocks = listOf(
                LlmRichContentBlock.Paragraph(
                    "老师，我卡在第三步了。\n" +
                        "Outcome: failed\n" +
                        "Correctness: 0.35\n" +
                        "Mastery: 0.2\n" +
                        "Depth: 0.4\n" +
                        "Evidence type: explanation\n" +
                        "Evidence status: partial\n" +
                        "Process summary: learner is confused about factoring\n" +
                        "checkPlan: {\"id\":\"check-1\"}\n" +
                        "Initiative source: heartbeat\n" +
                        "Window id: window-1\n" +
                        "你先帮我看看这一步好吗？"
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

        assertTrue(visible.contains("老师，我卡在第三步了。"))
        assertTrue(visible.contains("你先帮我看看这一步好吗？"))
        assertFalse(visible.contains("Outcome"))
        assertFalse(visible.contains("Correctness"))
        assertFalse(visible.contains("Mastery"))
        assertFalse(visible.contains("Depth"))
        assertFalse(visible.contains("Evidence type"))
        assertFalse(visible.contains("Evidence status"))
        assertFalse(visible.contains("Process summary"))
        assertFalse(visible.contains("checkPlan"))
        assertFalse(visible.contains("Initiative source"))
        assertFalse(visible.contains("Window id"))
    }

    // NEWMP-V1-006 Task 1: envelope sidebands (outcome/checkPlan objects) are
    // background data only and can never appear in the visible timeline.
    @Test
    fun envelopeSidebandsNeverLeakIntoTimelineText() {
        val envelope = LlmAssistantReplyEnvelope(
            blocks = listOf(LlmRichContentBlock.Paragraph("我先说说我的理解。")),
            outcome = StructuredTurnOutcome(
                windowId = "window-leak",
                actionType = "probe",
                knowledgePoint = "leak-point",
                processSummary = "internal summary leak"
            ),
            checkPlan = LlmSourceGroundedCheckPlan(
                id = "check-leak",
                sourceRevision = "rev-leak",
                sourceReferenceIds = listOf("source:leak"),
                prompt = "leak prompt",
                expectedAnswer = "leak answer",
                rule = LlmSourceCheckRule.ExactText("leak answer")
            )
        )

        val visible = envelope.timelineText()

        assertEquals("我先说说我的理解。", visible)
        assertFalse(visible.contains("window-leak"))
        assertFalse(visible.contains("leak-point"))
        assertFalse(visible.contains("internal summary leak"))
        assertFalse(visible.contains("check-leak"))
    }

    // NEWMP-V1-006 Task 1: a raw envelope-shaped JSON reply (parse failure on
    // the plain path, or the parser's own fallback paragraph) must collapse to
    // the generic student-style text instead of leaking raw JSON.
    @Test
    fun rawEnvelopeShapedJsonFallsBackToStudentTextInsteadOfLeaking() {
        val rawJson = """{"version":"v1","unknownField":true,"blocks":[{"type":"paragraph","text":"老师好"}],"outcome":{}}"""

        val plainPathVisible = rawJson.toVisibleTimelineText()
        val parserFallbackVisible = LlmAssistantReplyEnvelopeParser.parse(rawJson, emptySet()).timelineText()

        assertEquals("我还没整理好这一步，能再给我一点提示吗？", plainPathVisible)
        assertEquals("我还没整理好这一步，能再给我一点提示吗？", parserFallbackVisible)
        assertFalse(plainPathVisible.contains("{"))
        assertFalse(plainPathVisible.contains("blocks"))
    }

    // NEWMP-V1-006 Task 1: legitimate Markdown-style rich content (headings,
    // prose, code, tables, bullets) must remain fully visible.
    @Test
    fun normalMarkdownCodeTablesAndBulletsRemainVisible() {
        val envelope = LlmAssistantReplyEnvelope(
            blocks = listOf(
                LlmRichContentBlock.Heading(2, "我的理解"),
                LlmRichContentBlock.Paragraph("函数就像一台机器，输入原料输出结果。"),
                LlmRichContentBlock.CodeBlock("python", "def f(x):\n    return x + 1"),
                LlmRichContentBlock.SimpleTable(listOf("输入", "输出"), listOf(listOf("1", "2"))),
                LlmRichContentBlock.BulletList(listOf("第一个要点", "第二个要点"))
            )
        )

        val visible = envelope.timelineText()

        assertTrue(visible.contains("我的理解"))
        assertTrue(visible.contains("函数就像一台机器"))
        assertTrue(visible.contains("def f(x):"))
        assertTrue(visible.contains("输入 | 输出"))
        assertTrue(visible.contains("第一个要点"))
        assertTrue(visible.contains("第二个要点"))
    }
}
