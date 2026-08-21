package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session turn policy contract tests — wire-value normalization.
 *
 * Task 1 covers the pure normalization surface; behavioral decision rules
 * (entry-status rewrites, understood claims, probing intensity, correction
 * timing, first-turn constraints) are added in Task 2.
 */
class SessionTurnPolicyTest {

    // --- mode normalization -------------------------------------------------

    @Test
    fun invalidModeFallsBackToStudy() {
        assertEquals(SessionModeWire.STUDY, SessionTurnContracts.normalizeMode(null))
        assertEquals(SessionModeWire.STUDY, SessionTurnContracts.normalizeMode(""))
        assertEquals(SessionModeWire.STUDY, SessionTurnContracts.normalizeMode("tutor"))
        assertEquals(SessionModeWire.STUDY, SessionTurnContracts.normalizeMode("STUDY-extra"))
    }

    @Test
    fun validModesArePreserved() {
        assertEquals(SessionModeWire.STUDY, SessionTurnContracts.normalizeMode("study"))
        assertEquals(SessionModeWire.GOAL, SessionTurnContracts.normalizeMode("goal"))
        assertEquals(SessionModeWire.COMPANION, SessionTurnContracts.normalizeMode("companion"))
    }

    // --- entry status normalization ---------------------------------------

    @Test
    fun invalidEntryStatusFallsBackToHasEntry() {
        assertEquals(EntryStatusWire.HAS_ENTRY, SessionTurnContracts.normalizeEntryStatus(null))
        assertEquals(EntryStatusWire.HAS_ENTRY, SessionTurnContracts.normalizeEntryStatus("missing"))
        assertEquals(EntryStatusWire.HAS_ENTRY, SessionTurnContracts.normalizeEntryStatus(""))
    }

    @Test
    fun validEntryStatusesArePreserved() {
        assertEquals(EntryStatusWire.HAS_ENTRY, SessionTurnContracts.normalizeEntryStatus("has_entry"))
        assertEquals(EntryStatusWire.NO_ENTRY, SessionTurnContracts.normalizeEntryStatus("no_entry"))
        assertEquals(EntryStatusWire.RECALL_DECAY, SessionTurnContracts.normalizeEntryStatus("recall_decay"))
    }

    // --- user emotion normalization ---------------------------------------

    @Test
    fun invalidUserEmotionFallsBackToNeutral() {
        assertEquals(UserEmotionWire.NEUTRAL, SessionTurnContracts.normalizeUserEmotion(null))
        assertEquals(UserEmotionWire.NEUTRAL, SessionTurnContracts.normalizeUserEmotion("angry"))
    }

    @Test
    fun validUserEmotionsArePreserved() {
        assertEquals(UserEmotionWire.FRESH, SessionTurnContracts.normalizeUserEmotion("fresh"))
        assertEquals(UserEmotionWire.REFUSING, SessionTurnContracts.normalizeUserEmotion("refusing"))
    }

    // --- student role normalization ---------------------------------------

    @Test
    fun invalidStudentRoleFallsBackToProbingStudent() {
        assertEquals(StudentRoleWire.PROBING_STUDENT, SessionTurnContracts.normalizeStudentRole(null))
        assertEquals(StudentRoleWire.PROBING_STUDENT, SessionTurnContracts.normalizeStudentRole("teacher"))
    }

    // --- evidence type / status normalization -----------------------------

    @Test
    fun invalidEvidenceTypeFallsBackToNone() {
        assertEquals(MasteryEvidenceTypeWire.NONE, SessionTurnContracts.normalizeEvidenceType(null))
        assertEquals(MasteryEvidenceTypeWire.NONE, SessionTurnContracts.normalizeEvidenceType("guess"))
    }

    @Test
    fun validEvidenceTypesArePreserved() {
        assertEquals(MasteryEvidenceTypeWire.EXPLANATION, SessionTurnContracts.normalizeEvidenceType("explanation"))
        assertEquals(MasteryEvidenceTypeWire.DELAYED_RETRIEVAL, SessionTurnContracts.normalizeEvidenceType("delayed_retrieval"))
        assertEquals(MasteryEvidenceTypeWire.CORRECTION, SessionTurnContracts.normalizeEvidenceType("correction"))
    }

    @Test
    fun invalidEvidenceStatusFallsBackToNone() {
        assertEquals(MasteryEvidenceStatusWire.NONE, SessionTurnContracts.normalizeEvidenceStatus(null))
        assertEquals(MasteryEvidenceStatusWire.NONE, SessionTurnContracts.normalizeEvidenceStatus("maybe"))
    }

    @Test
    fun validEvidenceStatusesArePreserved() {
        assertEquals(MasteryEvidenceStatusWire.PASSED, SessionTurnContracts.normalizeEvidenceStatus("passed"))
        assertEquals(MasteryEvidenceStatusWire.FAILED, SessionTurnContracts.normalizeEvidenceStatus("failed"))
    }

    // --- numeric clamping ---------------------------------------------------

    @Test
    fun correctnessAndDepthAreClampedToZeroOne() {
        assertEquals(0f, SessionTurnContracts.clamp01(-0.5f), 0f)
        assertEquals(1f, SessionTurnContracts.clamp01(1.5f), 0f)
        assertEquals(0f, SessionTurnContracts.clamp01(0f), 0f)
        assertEquals(1f, SessionTurnContracts.clamp01(1f), 0f)
        assertEquals(0.5f, SessionTurnContracts.clamp01(0.5f), 0f)
    }

    @Test
    fun difficultyDefaultsAndClamps() {
        assertEquals(0.5f, SessionTurnContracts.normalizeDifficulty(null), 0f)
        assertEquals(0f, SessionTurnContracts.normalizeDifficulty(-2f), 0f)
        assertEquals(1f, SessionTurnContracts.normalizeDifficulty(9f), 0f)
    }

    // --- knowledge point default -------------------------------------------

    @Test
    fun blankKnowledgePointFallsBackToDefault() {
        assertEquals(SessionTurnContracts.DEFAULT_KNOWLEDGE_POINT, SessionTurnContracts.normalizeKnowledgePoint(null))
        assertEquals(SessionTurnContracts.DEFAULT_KNOWLEDGE_POINT, SessionTurnContracts.normalizeKnowledgePoint("   "))
        assertEquals("极限", SessionTurnContracts.normalizeKnowledgePoint("极限"))
    }

    @Test
    fun blankNoteFallsBackToDefault() {
        assertEquals(SessionTurnContracts.DEFAULT_NOTE, SessionTurnContracts.normalizeNote(null))
        assertEquals("because probe", SessionTurnContracts.normalizeNote("because probe"))
    }

    // --- length limits ------------------------------------------------------

    @Test
    fun errorPatternIsTruncatedToMax() {
        val long = "概念混淆条件遗漏步骤跳跃表达不清迁移失败超长".repeat(2)
        val normalized = SessionTurnContracts.normalizeErrorPattern(long)
        assertTrue("errorPattern must be <= ${SessionTurnContracts.ERROR_PATTERN_MAX}", normalized.length <= SessionTurnContracts.ERROR_PATTERN_MAX)
        assertEquals(SessionTurnContracts.normalizeErrorPattern(long), long.take(SessionTurnContracts.ERROR_PATTERN_MAX))
    }

    @Test
    fun misconceptionIsTruncatedToMax() {
        val long = "用户把函数值为正当成增函数的假规则还很长很长很长".repeat(2)
        val normalized = SessionTurnContracts.normalizeMisconception(long)
        assertTrue("misconception must be <= ${SessionTurnContracts.MISCONCEPTION_MAX}", normalized.length <= SessionTurnContracts.MISCONCEPTION_MAX)
        assertEquals(SessionTurnContracts.normalizeMisconception(long), long.take(SessionTurnContracts.MISCONCEPTION_MAX))
    }

    @Test
    fun nullErrorPatternAndMisconceptionBecomeEmpty() {
        assertEquals("", SessionTurnContracts.normalizeErrorPattern(null))
        assertEquals("", SessionTurnContracts.normalizeMisconception(null))
    }

    // --- strategy settings normalization -----------------------------------

    @Test
    fun probingIntensityIsClampedToOneToFive() {
        assertEquals(3, SessionTurnContracts.normalizeProbingIntensity(null))
        assertEquals(1, SessionTurnContracts.normalizeProbingIntensity(0))
        assertEquals(5, SessionTurnContracts.normalizeProbingIntensity(9))
        assertEquals(4, SessionTurnContracts.normalizeProbingIntensity(4))
    }

    @Test
    fun invalidCorrectionTimingFallsBackToImmediate() {
        assertEquals(CorrectionTimingWire.IMMEDIATE, SessionTurnContracts.normalizeCorrectionTiming(null))
        assertEquals(CorrectionTimingWire.IMMEDIATE, SessionTurnContracts.normalizeCorrectionTiming("later"))
        assertEquals(CorrectionTimingWire.SUMMARY_ONLY, SessionTurnContracts.normalizeCorrectionTiming("summary_only"))
    }

    @Test
    fun invalidCorrectionPersistenceFallsBackToBalanced() {
        assertEquals(CorrectionPersistenceWire.BALANCED, SessionTurnContracts.normalizeCorrectionPersistence(null))
        assertEquals(CorrectionPersistenceWire.BALANCED, SessionTurnContracts.normalizeCorrectionPersistence("aggressive"))
        assertEquals(CorrectionPersistenceWire.PERSISTENT, SessionTurnContracts.normalizeCorrectionPersistence("persistent"))
    }

    // --- action whitelist + canonical role --------------------------------

    @Test
    fun actionWhitelistMatchesMode() {
        assertEquals(ActionTypeWire.STUDY_ALL, SessionTurnContracts.actionWhitelistForMode(SessionModeWire.STUDY))
        assertEquals(ActionTypeWire.GOAL_ALL, SessionTurnContracts.actionWhitelistForMode(SessionModeWire.GOAL))
        assertEquals(ActionTypeWire.COMPANION_ALL, SessionTurnContracts.actionWhitelistForMode(SessionModeWire.COMPANION))
        // invalid mode falls back to study whitelist
        assertEquals(ActionTypeWire.STUDY_ALL, SessionTurnContracts.actionWhitelistForMode("tutor"))
    }

    @Test
    fun studentRoleForActionMapsCanonicalRoles() {
        assertEquals(StudentRoleWire.CONFUSED_STUDENT, SessionTurnContracts.studentRoleForAction(ActionTypeWire.CHALLENGE))
        assertEquals(StudentRoleWire.CLUE_STUDENT, SessionTurnContracts.studentRoleForAction(ActionTypeWire.CLUE))
        assertEquals(StudentRoleWire.SCAFFOLD_STUDENT, SessionTurnContracts.studentRoleForAction(ActionTypeWire.SCAFFOLD_EXAMPLE))
        assertEquals(StudentRoleWire.EXAMINER, SessionTurnContracts.studentRoleForAction(ActionTypeWire.EXAMINER_VERIFY))
        assertEquals(StudentRoleWire.REVIEW_STUDENT, SessionTurnContracts.studentRoleForAction(ActionTypeWire.RECAP))
        assertEquals(StudentRoleWire.REVIEW_STUDENT, SessionTurnContracts.studentRoleForAction(ActionTypeWire.NEXT))
        assertEquals(StudentRoleWire.GOAL_PARTNER, SessionTurnContracts.studentRoleForAction(ActionTypeWire.DECOMPOSE))
        assertEquals(StudentRoleWire.COMPANION, SessionTurnContracts.studentRoleForAction(ActionTypeWire.OBSERVE))
        assertEquals(StudentRoleWire.PROBING_STUDENT, SessionTurnContracts.studentRoleForAction("bogus"))
    }
}
