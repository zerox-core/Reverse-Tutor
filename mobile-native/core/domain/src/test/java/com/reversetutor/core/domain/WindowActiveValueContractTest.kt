package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Red slice V2-002: window-level active values (Layer 3) and their evolution.
 * Window-local evolution is deliberately lighter than companion-memory
 * evolution: inside one window a confident user statement may supersede at
 * once, while behavior observations alone may not.
 */
class WindowActiveValueContractTest {

    private fun obs(
        value: String = "late_night_coding",
        sourceClass: String = MemoryObservationSourceClass.BEHAVIOR_OBSERVATION,
        confidence: Double = 0.6,
        salience: SignalSalience = SignalSalience.NORMAL,
        at: Long = 1000L,
    ) = WindowLocalObservation(
        category = WindowObservationCategory.ACTIVITY_CONTEXT,
        slotKey = WindowObservationCategory.ACTIVITY_CONTEXT.name,
        value = value,
        sourceClass = sourceClass,
        confidence = confidence,
        salience = salience,
        occurredAtEpochMillis = at,
        provenanceHandle = "turn:w1:t1",
    )

    private fun active(
        value: String = "late_night_coding",
        weight: Double = 0.5,
        revision: Long = 1L,
    ) = WindowActiveValue(
        category = WindowObservationCategory.ACTIVITY_CONTEXT,
        slotKey = WindowObservationCategory.ACTIVITY_CONTEXT.name,
        value = value,
        revision = revision,
        weight = weight,
        sourceClass = MemoryObservationSourceClass.BEHAVIOR_OBSERVATION,
        provenanceHandle = "turn:w1:t0",
        updatedAtEpochMillis = 500L,
    )

    @Test
    fun `low salience observation is ignored even without current value`() {
        assertEquals(
            WindowEvolutionDecision.IGNORE,
            WindowActiveValuePolicy.decide(null, obs(salience = SignalSalience.LOW)),
        )
    }

    @Test
    fun `normal salience without current value creates`() {
        assertEquals(WindowEvolutionDecision.CREATE, WindowActiveValuePolicy.decide(null, obs()))
    }

    @Test
    fun `same value reinforces and bumps weight by step`() {
        val current = active(weight = 0.5)
        assertEquals(WindowEvolutionDecision.REINFORCE, WindowActiveValuePolicy.decide(current, obs()))
        val updated = WindowActiveValuePolicy.reinforce(current, obs())
        assertEquals(0.65, updated.weight, 0.0001)
        assertEquals(2L, updated.revision)
        assertEquals("turn:w1:t1", updated.provenanceHandle)
    }

    @Test
    fun `reinforce caps weight at max`() {
        val updated = WindowActiveValuePolicy.reinforce(active(weight = 0.95), obs())
        assertEquals(WindowActiveValuePolicy.MAX_WEIGHT, updated.weight, 0.0001)
    }

    @Test
    fun `different value from confident user statement supersedes`() {
        val o = obs(
            value = "morning_study",
            sourceClass = MemoryObservationSourceClass.USER_STATEMENT,
            confidence = 0.9,
        )
        assertEquals(WindowEvolutionDecision.SUPERSEDE, WindowActiveValuePolicy.decide(active(), o))
        val updated = WindowActiveValuePolicy.supersede(active(), o)
        assertEquals("morning_study", updated.value)
        assertEquals(2L, updated.revision)
        assertEquals(0.9, updated.weight, 0.0001)
    }

    @Test
    fun `different value from behavior observation conflicts`() {
        assertEquals(
            WindowEvolutionDecision.CONFLICT,
            WindowActiveValuePolicy.decide(active(), obs(value = "afternoon_gaming")),
        )
    }

    @Test
    fun `create via supersede starts at revision one`() {
        val created = WindowActiveValuePolicy.supersede(null, obs())
        assertEquals(1L, created.revision)
        assertEquals("late_night_coding", created.value)
    }
}
