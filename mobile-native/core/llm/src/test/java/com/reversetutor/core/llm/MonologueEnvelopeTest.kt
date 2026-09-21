package com.reversetutor.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Expression-loop slice 4: monologue/body envelope splitting (SPEC §4.7, §4.4). */
class MonologueEnvelopeTest {

    // ---- splitComplete ----

    @Test
    fun splitCompleteWithoutMarkerKeepsWholeTextAsBody() {
        val text = "老师，我试了下 x=2 代进去，好像对不上？"
        val split = MonologueEnvelope.splitComplete(text)
        assertNull(split.monologue)
        assertEquals(text, split.body)
    }

    @Test
    fun splitCompleteParsesMonologueAndBody() {
        val split = MonologueEnvelope.splitComplete(
            "<thinking>他上一轮已经试过代入了，我该往哪问呢…</thinking>\n老师，那如果 x 取负数呢？"
        )
        assertEquals("他上一轮已经试过代入了，我该往哪问呢…", split.monologue)
        assertEquals("老师，那如果 x 取负数呢？", split.body)
    }

    @Test
    fun splitCompleteTreatsUnclosedMarkerAsMonologueOnly() {
        val split = MonologueEnvelope.splitComplete("<thinking>只想到了一半")
        assertEquals("只想到了一半", split.monologue)
        assertEquals("", split.body)
    }

    @Test
    fun splitCompleteIgnoresBlankMonologue() {
        val split = MonologueEnvelope.splitComplete("<thinking>   </thinking>老师好")
        assertNull(split.monologue)
        assertEquals("老师好", split.body)
    }

    @Test
    fun splitCompleteCapsOverlongMonologue() {
        val monologue = "想".repeat(MonologueEnvelope.MAX_MONOLOGUE_CHARACTERS + 100)
        val split = MonologueEnvelope.splitComplete("<thinking>$monologue</thinking>正文")
        assertEquals(MonologueEnvelope.MAX_MONOLOGUE_CHARACTERS, split.monologue!!.length)
        assertEquals("正文", split.body)
    }

    @Test
    fun splitCompleteIsCaseSensitiveOnMarker() {
        // A differently-cased marker is NOT a monologue marker: the whole text
        // stays visible body — we never eat words on a near miss.
        val split = MonologueEnvelope.splitComplete("<Thinking>你好</Thinking>老师好")
        assertNull(split.monologue)
        assertTrue(split.body.contains("<Thinking>"))
    }

    @Test
    fun splitCompleteTrimsSurroundingWhitespace() {
        val split = MonologueEnvelope.splitComplete("   <thinking>  我卡住了  </thinking>  \n正文在这里  ")
        assertEquals("我卡住了", split.monologue)
        assertEquals("正文在这里", split.body)
    }

    @Test
    fun splitCompleteKeepsMarkerInsideBodyUnchanged() {
        // Marker NOT at the start is ordinary text (decision: only the leading
        // segment is a monologue; nothing mid-reply is ever stripped).
        val text = "我想想 <thinking>哦</thinking> 老师，是这样吗"
        val split = MonologueEnvelope.splitComplete(text)
        assertNull(split.monologue)
        assertEquals(text, split.body)
    }

    // ---- MonologueStreamSplitter ----

    @Test
    fun streamSplitterHoldsEverythingWhileMonologueStreams() {
        val splitter = MonologueStreamSplitter()
        assertNull(splitter.onChunk("<thi"))
        assertNull(splitter.onChunk("nking>我卡在货币乘数这"))
        assertNull(splitter.onChunk("想先问清楚他上一问的意图</th"))
        assertNull(splitter.onChunk("inking>"))
        // Body only becomes visible once the end marker fully arrived.
        assertEquals("老师，", splitter.onChunk("\n老师，"))
        assertEquals("那负数呢？", splitter.onChunk("那负数呢？"))
        val split = splitter.splitSoFar()
        assertEquals("我卡在货币乘数这想先问清楚他上一问的意图", split.monologue)
        assertEquals("老师，那负数呢？", split.body)
    }

    @Test
    fun streamSplitterPassesLegacyTextThroughChunkByChunk() {
        val splitter = MonologueStreamSplitter()
        assertEquals("老", splitter.onChunk("老"))
        assertEquals("师好", splitter.onChunk("师好"))
        assertNull(splitter.splitSoFar().monologue)
        assertEquals("老师好", splitter.splitSoFar().body)
    }

    @Test
    fun streamSplitterHoldsOnlyWhileStartPrefixIsPossible() {
        val splitter = MonologueStreamSplitter()
        assertNull(splitter.onChunk("<"))      // could still grow into the marker
        assertNull(splitter.onChunk("think"))  // still possible
        assertEquals("<think!", splitter.onChunk("!")) // mismatch: everything flushes
    }

    @Test
    fun streamSplitterEmitsNothingForEmptyChunks() {
        val splitter = MonologueStreamSplitter()
        assertNull(splitter.onChunk(""))
        assertNull(splitter.onChunk(""))
        assertEquals("a", splitter.onChunk("a"))
    }

    // ---- selfAssessmentPayload ----

    @Test
    fun selfAssessmentPayloadReturnsNullForEmptyOutcome() {
        assertNull(MonologueEnvelope.selfAssessmentPayload(StructuredTurnOutcome.EMPTY))
    }

    @Test
    fun selfAssessmentPayloadSerializesOutcome() {
        val payload = MonologueEnvelope.selfAssessmentPayload(
            StructuredTurnOutcome(
                knowledgePoint = "货币乘数",
                evidenceType = "explanation",
                evidenceStatus = "partial",
                correctness = 0.5f,
                depth = 0.25f,
                processSummary = "老师解释了准备金率的影响"
            )
        )
        assertEquals(
            "kp=货币乘数|evidence=explanation/partial|correctness=0.5|depth=0.25|summary=老师解释了准备金率的影响",
            payload
        )
    }

    @Test
    fun selfAssessmentPayloadCapsLength() {
        val payload = MonologueEnvelope.selfAssessmentPayload(
            StructuredTurnOutcome(
                knowledgePoint = "k".repeat(400),
                processSummary = "s".repeat(400)
            )
        )!!
        assertTrue(payload.length <= MonologueEnvelope.MAX_SELF_ASSESSMENT_CHARACTERS)
    }
}
