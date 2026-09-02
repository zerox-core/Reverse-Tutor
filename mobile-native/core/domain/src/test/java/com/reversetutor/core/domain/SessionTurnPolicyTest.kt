package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session turn policy tests.
 *
 * Task 1 covers wire-value normalization; Task 2 adds behavioral decision
 * rules migrated from old `main:engine.py` `_normalize_turn_payload`.
 */
class SessionTurnPolicyTest {

    // =========================================================================
    // Task 1: Normalization tests
    // =========================================================================

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

    // =========================================================================
    // Task 2: Behavioral decision rules
    // =========================================================================

    // --- study action whitelist and fallback -------------------------------

    @Test
    fun studyInvalidActionFallsBackToAsk() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = "bogus_action",
            entryStatus = EntryStatusWire.HAS_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.ASK, out.action.type)
    }

    @Test
    fun studyValidActionIsPreserved() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.CHALLENGE,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            knowledgePoint = "导数定义"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.CHALLENGE, out.action.type)
    }

    // --- no_entry + ask/probe/next/recap -> clue ----------------------------

    @Test
    fun noEntryWithAskBecomesClue() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.NO_ENTRY,
            knowledgePoint = "极限"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.CLUE, out.action.type)
    }

    @Test
    fun noEntryWithProbeBecomesClue() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.PROBE,
            entryStatus = EntryStatusWire.NO_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.CLUE, out.action.type)
    }

    @Test
    fun noEntryWithNextBecomesClue() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.NEXT,
            entryStatus = EntryStatusWire.NO_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.CLUE, out.action.type)
    }

    @Test
    fun noEntryWithRecapBecomesClue() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.RECAP,
            entryStatus = EntryStatusWire.NO_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.CLUE, out.action.type)
    }

    // --- has_entry + clue/scaffold_example -> probe -------------------------

    @Test
    fun hasEntryWithClueBecomesProbe() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.CLUE,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            knowledgePoint = "连续性"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.PROBE, out.action.type)
    }

    @Test
    fun hasEntryWithScaffoldExampleBecomesProbe() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.SCAFFOLD_EXAMPLE,
            entryStatus = EntryStatusWire.HAS_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.PROBE, out.action.type)
    }

    // --- understood claim -> examiner_verify --------------------------------

    @Test
    fun understoodClaimBecomesExaminerVerify() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            userInput = "我懂了这个知识点",
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            knowledgePoint = "导数"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.EXAMINER_VERIFY, out.action.type)
    }

    @Test
    fun understoodClaimWithDifferentKeyword() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            userInput = "明白了，就是这么算的",
            actionType = ActionTypeWire.PROBE,
            entryStatus = EntryStatusWire.HAS_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.EXAMINER_VERIFY, out.action.type)
    }

    @Test
    fun studyMisconceptionForcesCounterexampleChallenge() {
        val output = SessionTurnPolicy.normalize(
            SessionPolicyInput(
                mode = SessionModeWire.STUDY,
                userInput = "我确定这个规则永远成立",
                misconception = "忽略定义域",
                actionType = ActionTypeWire.ASK
            )
        )

        assertEquals(ActionTypeWire.CHALLENGE, output.action.type)
        assertEquals(StudentRoleWire.CONFUSED_STUDENT, output.action.studentRole)
    }

    @Test
    fun understoodClaimRequiresExaminerVerificationWithoutMasteryEvidence() {
        val output = SessionTurnPolicy.normalize(
            SessionPolicyInput(
                mode = SessionModeWire.STUDY,
                userInput = "我懂了",
                evidenceType = MasteryEvidenceTypeWire.TRANSFER,
                evidenceStatus = MasteryEvidenceStatusWire.PASSED
            )
        )

        assertEquals(ActionTypeWire.EXAMINER_VERIFY, output.action.type)
        assertEquals(MasteryEvidenceTypeWire.NONE, output.evaluation.evidence.type)
        assertEquals(MasteryEvidenceStatusWire.NONE, output.evaluation.evidence.status)
    }

    // --- force probe --------------------------------------------------------

    @Test
    fun forceProbeOverridesActionToProbe() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            forceProbe = true,
            knowledgePoint = "极限"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.PROBE, out.action.type)
    }

    // --- high probing intensity converting ask -> probe ---------------------

    @Test
    fun highProbingIntensityConvertsAskToProbe() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            settings = SessionStrategySettings(probingIntensity = 5),
            knowledgePoint = "泰勒展开"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.PROBE, out.action.type)
    }

    @Test
    fun lowProbingIntensityKeepsAsk() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            settings = SessionStrategySettings(probingIntensity = 3),
            knowledgePoint = "泰勒展开"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.ASK, out.action.type)
    }

    // --- active error + low correctness -> small_lecture ---------------------

    @Test
    fun activeErrorAndLowCorrectnessConvertsToSmallLecture() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            settings = SessionStrategySettings(probingIntensity = 3),
            correctness = 0.2f,
            hasActiveError = true,
            knowledgePoint = "链式法则"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.SMALL_LECTURE, out.action.type)
    }

    @Test
    fun noActiveErrorKeepsAsk() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            settings = SessionStrategySettings(probingIntensity = 3),
            correctness = 0.2f,
            hasActiveError = false,
            knowledgePoint = "链式法则"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.ASK, out.action.type)
    }

    // --- summary_only correction -> recap -----------------------------------

    @Test
    fun summaryOnlyCorrectionConvertsToRecap() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.CHALLENGE,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            settings = SessionStrategySettings(correctionTiming = CorrectionTimingWire.SUMMARY_ONLY),
            evidenceType = MasteryEvidenceTypeWire.CORRECTION,
            knowledgePoint = "积分"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.RECAP, out.action.type)
        assertEquals(CorrectionTimingWire.SUMMARY_ONLY, out.correctionTiming)
    }

    @Test
    fun immediateTimingDoesNotConvertToRecap() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.CHALLENGE,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            settings = SessionStrategySettings(correctionTiming = CorrectionTimingWire.IMMEDIATE),
            evidenceType = MasteryEvidenceTypeWire.CORRECTION,
            knowledgePoint = "积分"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.CHALLENGE, out.action.type)
    }

    // --- goal/companion whitelist and evidence reset -----------------------

    @Test
    fun goalModeResetsEvidenceToNone() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.GOAL,
            actionType = ActionTypeWire.ADVANCE,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            evidenceType = MasteryEvidenceTypeWire.EXPLANATION,
            evidenceStatus = MasteryEvidenceStatusWire.PASSED,
            knowledgePoint = "项目里程碑"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(MasteryEvidenceTypeWire.NONE, out.evaluation.evidence.type)
        assertEquals(MasteryEvidenceStatusWire.NONE, out.evaluation.evidence.status)
        assertTrue(out.evaluation.evidence.reason.contains("goal"))
    }

    @Test
    fun companionModeResetsEvidenceToNone() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.COMPANION,
            actionType = ActionTypeWire.OBSERVE,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            evidenceType = MasteryEvidenceTypeWire.RETRIEVAL,
            evidenceStatus = MasteryEvidenceStatusWire.PASSED,
            knowledgePoint = "情绪"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(MasteryEvidenceTypeWire.NONE, out.evaluation.evidence.type)
        assertEquals(MasteryEvidenceStatusWire.NONE, out.evaluation.evidence.status)
        assertTrue(out.evaluation.evidence.reason.contains("companion"))
    }

    @Test
    fun goalModeInvalidActionFallsBackToAdvance() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.GOAL,
            actionType = ActionTypeWire.ASK,  // study action in goal mode
            entryStatus = EntryStatusWire.HAS_ENTRY,
            knowledgePoint = "目标"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertTrue(out.action.type in ActionTypeWire.GOAL_ALL)
    }

    @Test
    fun companionModeNegativeInputBecomesEmpathize() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.COMPANION,
            userInput = "太累了，不想学了",
            actionType = "bogus",
            entryStatus = EntryStatusWire.HAS_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.EMPATHIZE, out.action.type)
    }

    // --- first-turn constraints ---------------------------------------------

    @Test
    fun firstTurnStudyIsAsk() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            isFirstTurn = true,
            actionType = ActionTypeWire.CHALLENGE,  // should be overridden
            knowledgePoint = "导数"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.ASK, out.action.type)
        assertEquals(0f, out.evaluation.correctness, 0f)
    }

    @Test
    fun firstTurnGoalIsDecompose() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.GOAL,
            isFirstTurn = true,
            actionType = ActionTypeWire.ADVANCE,  // should be overridden
            knowledgePoint = "项目"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.DECOMPOSE, out.action.type)
    }

    @Test
    fun firstTurnCompanionIsObserve() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.COMPANION,
            isFirstTurn = true,
            actionType = ActionTypeWire.SOFT_GUIDE,  // should be overridden
            knowledgePoint = "状态"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(ActionTypeWire.OBSERVE, out.action.type)
    }

    // --- process summary ----------------------------------------------------

    @Test
    fun processSummaryIsNonEmpty() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.PROBE,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            knowledgePoint = "极限"
        )
        val out = SessionTurnPolicy.normalize(input)
        assertTrue(out.processSummary.isNotEmpty())
        assertTrue(out.processSummary.contains("极限"))
    }

    @Test
    fun blankKnowledgePointUsesDefault() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            knowledgePoint = ""
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(SessionTurnContracts.DEFAULT_KNOWLEDGE_POINT, out.action.knowledgePoint)
    }

    @Test
    fun probeWithEnoughCorrectnessAndDepthDerivesExplanationEvidence() {
        val out = SessionTurnPolicy.normalize(
            SessionPolicyInput(
                mode = SessionModeWire.STUDY,
                actionType = ActionTypeWire.PROBE,
                entryStatus = EntryStatusWire.HAS_ENTRY,
                correctness = 0.5f,
                depth = 0.4f,
                evidenceType = MasteryEvidenceTypeWire.NONE
            )
        )

        assertEquals(MasteryEvidenceTypeWire.EXPLANATION, out.evaluation.evidence.type)
        assertEquals(MasteryEvidenceStatusWire.PASSED, out.evaluation.evidence.status)
    }

    // --- entry status derivation from keywords ------------------------------

    @Test
    fun recallDecayKeywordSetsEntryStatus() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            userInput = "我忘了怎么算这个",
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(EntryStatusWire.RECALL_DECAY, out.evaluation.entryStatus)
    }

    @Test
    fun noEntryKeywordSetsEntryStatus() {
        val input = SessionPolicyInput(
            mode = SessionModeWire.STUDY,
            userInput = "完全没听说过这个",
            actionType = ActionTypeWire.ASK,
            entryStatus = EntryStatusWire.HAS_ENTRY
        )
        val out = SessionTurnPolicy.normalize(input)
        assertEquals(EntryStatusWire.NO_ENTRY, out.evaluation.entryStatus)
    }
}
