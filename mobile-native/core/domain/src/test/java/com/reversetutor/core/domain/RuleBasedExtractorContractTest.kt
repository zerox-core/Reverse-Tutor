package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Red slice V2-003: rule-based extractor v1. Deterministic signals only,
 * batch-at-window-eviction, never per sentence, values are normalized
 * labels (no raw transcript) with provenance handles pointing back.
 */
class RuleBasedExtractorContractTest {

    private fun user(text: String, hour: Int = 15, id: String = "u1", at: Long = 100L) =
        ExtractableMessage(messageId = id, role = MessageRole.USER, text = text, occurredAtEpochMillis = at, hourOfDay = hour)

    private fun assistant(text: String, hour: Int = 15, id: String = "a1", at: Long = 99L) =
        ExtractableMessage(messageId = id, role = MessageRole.ASSISTANT, text = text, occurredAtEpochMillis = at, hourOfDay = hour)

    @Test
    fun `goal statement produces goal candidate with high confidence`() {
        val out = RuleBasedMemoryExtractor.extract(listOf(user("我要在这个月把圆锥曲线刷完")))
        assertEquals(1, out.size)
        assertEquals(WindowObservationCategory.GOAL_STATEMENT, out[0].category)
        assertEquals(MemoryObservationSourceClass.USER_STATEMENT, out[0].sourceClass)
        assertTrue(out[0].confidence >= 0.9)
        assertTrue(out[0].provenanceHandle.contains("u1"))
    }

    @Test
    fun `user reply after assistant reminder produces reminder feedback`() {
        val out = RuleBasedMemoryExtractor.extract(
            listOf(
                assistant("都凌晨三点了，快去睡觉吧", hour = 3),
                user("解决完这个我就睡", hour = 3, at = 101L),
            ),
        )
        assertEquals(1, out.size)
        assertEquals(WindowObservationCategory.REMINDER_FEEDBACK, out[0].category)
        assertEquals(SignalSalience.HIGH, out[0].salience)
    }

    @Test
    fun `late night coding produces activity context behavior observation`() {
        val out = RuleBasedMemoryExtractor.extract(listOf(user("还在写代码", hour = 3)))
        assertEquals(1, out.size)
        assertEquals(WindowObservationCategory.ACTIVITY_CONTEXT, out[0].category)
        assertEquals(MemoryObservationSourceClass.BEHAVIOR_OBSERVATION, out[0].sourceClass)
        assertEquals(SignalSalience.HIGH, out[0].salience)
    }

    @Test
    fun `afternoon coding produces no activity candidate`() {
        assertTrue(RuleBasedMemoryExtractor.extract(listOf(user("我在写代码", hour = 15))).isEmpty())
    }

    @Test
    fun `batch is capped at max candidates`() {
        val msgs = listOf(
            user("我要学数学", id = "u1", at = 1L),
            user("我要学物理", id = "u2", at = 2L),
            user("我要学化学", id = "u3", at = 3L),
            user("我要学生物", id = "u4", at = 4L),
        )
        assertEquals(RuleBasedMemoryExtractor.MAX_CANDIDATES_PER_BATCH, RuleBasedMemoryExtractor.extract(msgs).size)
    }

    @Test
    fun `assistant messages never produce candidates`() {
        assertTrue(RuleBasedMemoryExtractor.extract(listOf(assistant("我要帮你定个目标"))).isEmpty())
    }

    @Test
    fun `casual small talk produces zero candidates`() {
        assertTrue(RuleBasedMemoryExtractor.extract(listOf(user("嗯嗯，好的"))).isEmpty())
    }
}
