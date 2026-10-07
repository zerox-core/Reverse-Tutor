package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Learning-scope guard tests.
 *
 * Package A (task 4): soft-only classification. The guard never blocks a turn
 * and never rewrites the declared learning intent; for sustained drift it only
 * emits a re-anchoring constraint.
 */
class LearningScopeGuardTest {

    private val envelope = LearningIntentEnvelope(
        windowId = "learn-root-1",
        declaredSubject = "math",
        declaredLevel = "high_school",
        declaredGoal = "university entrance",
        studyMethod = "worked_examples"
    )

    @Test
    fun high_school_to_university_math_is_related_evolution() {
        val decision = LearningScopeGuard.classify(
            envelope,
            listOf(ScopeSignal(ScopeSignalCategory.LEVEL_PROGRESSION, 1))
        )
        assertEquals(ScopeRelation.RELATED_EVOLUTION, decision.relation)
        assertNull(decision.reanchorConstraint)
    }

    @Test
    fun changing_explanation_style_is_related_evolution() {
        val decision = LearningScopeGuard.classify(
            envelope,
            listOf(ScopeSignal(ScopeSignalCategory.STYLE_CHANGE, 1))
        )
        assertEquals(ScopeRelation.RELATED_EVOLUTION, decision.relation)
        assertNull(decision.reanchorConstraint)
    }

    @Test
    fun one_off_unrelated_joke_is_ambiguous_not_out_of_scope() {
        val decision = LearningScopeGuard.classify(
            envelope,
            listOf(ScopeSignal(ScopeSignalCategory.UNRELATED, 1))
        )
        assertEquals(ScopeRelation.AMBIGUOUS, decision.relation)
        assertNull(decision.reanchorConstraint)
    }

    @Test
    fun sustained_unrelated_drift_returns_soft_reanchor_only() {
        val decision = LearningScopeGuard.classify(
            envelope,
            listOf(ScopeSignal(ScopeSignalCategory.UNRELATED, 4))
        )
        assertEquals(ScopeRelation.SUSTAINED_OUT_OF_SCOPE, decision.relation)
        assertNotNull(decision.reanchorConstraint)
        // The intent envelope is never rewritten by the guard.
        assertEquals("math", envelope.declaredSubject)
        assertEquals("high_school", envelope.declaredLevel)
        // Soft only: no blocking/rewrite field exists on the decision.
        assertTrue(decision.reanchorConstraint!!.contains("math") || decision.reanchorConstraint!!.contains("high_school"))
    }

    @Test
    fun companion_root_does_not_run_learning_scope_guard() {
        val companionRoot = WindowRef("croot", "croot", null, WindowKind.COMPANION_ROOT)
        assertFalse(LearningScopeGuard.forWindow(companionRoot, envelope))
        assertFalse(LearningScopeGuard.forWindow(companionRoot, null))
        assertTrue(LearningScopeGuard.forWindow(WindowRef("lroot", "lroot", null, WindowKind.LEARNING_ROOT), envelope))
    }

    @Test
    fun signal_has_minimal_provenance_but_no_raw_user_text() {
        val fields = ScopeSignal.ALLOWED_PERSISTED_FIELDS
        assertFalse(fields.any { it.contains("transcript", ignoreCase = true) })
        assertFalse(fields.any { it.contains("rawText", ignoreCase = true) || it.contains("messageText", ignoreCase = true) })
        assertTrue(fields.contains("category"))
        assertTrue(fields.contains("count"))
        assertTrue(fields.contains("sourceTurnId"))
        assertTrue(fields.contains("observedAtEpochMillis"))
    }
}
