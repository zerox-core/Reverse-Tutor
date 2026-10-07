package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * Companion-memory evolution policy tests.
 *
 * Package A (task 3): Hermes-like background curation, domain isolation, and
 * the "no personality forgetting curve" rule, all pure and transcript-minimizing.
 */
class CompanionMemoryEvolutionPolicyTest {

    private val now: Long = 10_000_000_000L

    private fun window(kind: WindowKind, id: String) = WindowRef(id, id, null, kind)

    private fun observation(
        value: String,
        sourceClass: String,
        observedAtOffset: Long,
        confidence: Float,
        partition: CompanionMemoryPartition = CompanionMemoryPartition.STABLE_PREFERENCE
    ) = MemoryObservation(
        domain = MemoryDomain.COMPANION,
        partition = partition,
        normalizedValue = value,
        sourceClass = sourceClass,
        observedAtEpochMillis = now - observedAtOffset,
        confidence = confidence,
        provenanceHandle = "prov-$value"
    )

    @Test
    fun companion_domain_is_only_readable_by_companion_root() {
        val companionRoot = window(WindowKind.COMPANION_ROOT, "croot")
        val taskRoot = window(WindowKind.LEARNING_ROOT, "taskroot")
        assertTrue(CompanionMemoryEvolutionPolicy.canReadDomain(MemoryDomain.COMPANION, companionRoot))
        assertFalse(CompanionMemoryEvolutionPolicy.canReadDomain(MemoryDomain.COMPANION, taskRoot))
        assertTrue(CompanionMemoryEvolutionPolicy.canReadDomain(MemoryDomain.GLOBAL_LEARNING, companionRoot))
        assertTrue(CompanionMemoryEvolutionPolicy.canReadDomain(MemoryDomain.GLOBAL_LEARNING, taskRoot))
    }

    @Test
    fun task_window_can_emit_learning_fact_but_not_personality_observation() {
        val taskRoot = window(WindowKind.LEARNING_ROOT, "taskroot")
        val learningFact = MemoryObservation(
            domain = MemoryDomain.GLOBAL_LEARNING,
            partition = null,
            normalizedValue = "normalized mastery fact",
            sourceClass = MemoryObservationSourceClass.LEARNING_RETENTION,
            observedAtEpochMillis = now,
            confidence = 0.9f,
            provenanceHandle = "fact-1"
        )
        val personality = observation(
            value = "often references web sources",
            sourceClass = MemoryObservationSourceClass.BEHAVIOR_OBSERVATION,
            observedAtOffset = 10_000L,
            confidence = 0.9f
        )
        assertTrue(CompanionMemoryEvolutionPolicy.canEmit(learningFact, taskRoot))
        assertFalse(CompanionMemoryEvolutionPolicy.canEmit(personality, taskRoot))
    }

    @Test
    fun one_observation_cannot_supersede_active_core_setting() {
        val active = ActiveMemoryVersion(
            partition = CompanionMemoryPartition.CORE_SETTING,
            value = "template persona",
            origin = MemoryOrigin.TEMPLATE,
            promotedAtEpochMillis = now - 1_000_000L
        )
        val single = listOf(
            observation(
                value = "a different persona",
                sourceClass = MemoryObservationSourceClass.USER_STATEMENT,
                observedAtOffset = 1_000L,
                confidence = 0.95f
            )
        )
        val decision = CompanionMemoryEvolutionPolicy.decide(active, single, now)
        assertTrue(decision !is MemoryEvolutionDecision.Supersede)
    }

    @Test
    fun stable_independent_observations_can_supersede_active_setting() {
        val active = ActiveMemoryVersion(
            partition = CompanionMemoryPartition.STABLE_PREFERENCE,
            value = "concise, formal explanations",
            origin = MemoryOrigin.TEMPLATE,
            promotedAtEpochMillis = now - 20_000_000L
        )
        val independent = listOf(
            observation(
                value = "socratic, gets to the point quickly",
                sourceClass = MemoryObservationSourceClass.USER_STATEMENT,
                observedAtOffset = 30_000_000L,
                confidence = 0.9f
            ),
            observation(
                value = "socratic, gets to the point quickly",
                sourceClass = MemoryObservationSourceClass.BEHAVIOR_OBSERVATION,
                observedAtOffset = 3_000_000L,
                confidence = 0.85f
            )
        )
        val decision = CompanionMemoryEvolutionPolicy.decide(active, independent, now)
        assertTrue(decision is MemoryEvolutionDecision.Supersede)
        assertEquals("socratic, gets to the point quickly", (decision as MemoryEvolutionDecision.Supersede).value)
        assertEquals(2L, decision.revision)
    }

    @Test
    fun short_lived_situation_expires_without_erasing_stable_preference() {
        val active = ActiveMemoryVersion(
            partition = CompanionMemoryPartition.STABLE_PREFERENCE,
            value = "wants drills before theory",
            origin = MemoryOrigin.MANUAL,
            promotedAtEpochMillis = now - 10_000_000L
        )
        val expired = listOf(
            observation(
                value = "was overwhelmed this week",
                sourceClass = MemoryObservationSourceClass.BEHAVIOR_OBSERVATION,
                observedAtOffset = 30L * 24 * 60 * 60 * 1000L,
                confidence = 0.8f,
                partition = CompanionMemoryPartition.SHORT_LIVED_SITUATION
            )
        )
        val decision = CompanionMemoryEvolutionPolicy.decide(active, expired, now)
        assertTrue(decision !is MemoryEvolutionDecision.Supersede)
        assertEquals(MemoryEvolutionDecision.Ignore, decision)
    }

    @Test
    fun learning_retention_signal_is_not_personality_evolution_evidence() {
        val active = ActiveMemoryVersion(
            partition = CompanionMemoryPartition.STABLE_PREFERENCE,
            value = "explains with worked examples",
            origin = MemoryOrigin.INFERRED,
            promotedAtEpochMillis = now - 5_000_000L
        )
        val retentionSignal = listOf(
            MemoryObservation(
                domain = MemoryDomain.GLOBAL_LEARNING,
                partition = null,
                normalizedValue = "mastery 0.8",
                sourceClass = MemoryObservationSourceClass.LEARNING_RETENTION,
                observedAtEpochMillis = now - 1_000L,
                confidence = 0.95f,
                provenanceHandle = "retention-1"
            )
        )
        assertEquals(MemoryEvolutionDecision.Ignore, CompanionMemoryEvolutionPolicy.decide(active, retentionSignal, now))
    }

    @Test
    fun raw_transcript_is_not_a_field_of_memory_observation() {
        val fields = MemoryObservation.ALLOWED_PERSISTED_FIELDS
        assertFalse(fields.any { it.contains("transcript", ignoreCase = true) })
        assertFalse(fields.any { it.equals("rawText", ignoreCase = true) || it.equals("messageText", ignoreCase = true) })
        assertTrue(fields.contains("normalizedValue"))
        assertTrue(fields.contains("provenanceHandle"))
        assertTrue(fields.contains("sourceClass"))
    }
}
