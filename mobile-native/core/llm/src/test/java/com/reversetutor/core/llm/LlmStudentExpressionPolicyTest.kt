package com.reversetutor.core.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-014: legacy session-policy actions must map to Chinese student
 * expression directives that carry the old engine.py persona rules. The two
 * original directiveFor() tests are kept unchanged for coverage.
 */
class LlmStudentExpressionPolicyTest {

    @Test
    fun legacyActionsMapToChineseDirectives() {
        val probe = LlmStudentExpressionPolicy.sessionPolicyDirectiveFor("PROBE")
        assertTrue(probe.contains("追问一个"))

        val challenge = LlmStudentExpressionPolicy.sessionPolicyDirectiveFor("Challenge")
        assertTrue(challenge.contains("反例"))
        assertTrue(challenge.contains("你错了"))

        val clue = LlmStudentExpressionPolicy.sessionPolicyDirectiveFor("clue")
        assertTrue(clue.contains("老师，据说"))
        assertTrue(clue.contains("我来教你"))

        val examiner = LlmStudentExpressionPolicy.sessionPolicyDirectiveFor("examiner_verify")
        assertTrue(examiner.contains("验证题"))
    }

    @Test
    fun unknownActionFallsBackToStudentQuestion() {
        val fallback = LlmStudentExpressionPolicy.sessionPolicyDirectiveFor("brand_new_action")
        assertTrue(fallback.contains("学生口吻"))
        assertTrue(fallback.contains("聚焦的问题"))
    }

    @Test
    fun action_is_translated_into_student_expression_without_teacher_lecture_language() {
        val directive = LlmStudentExpressionPolicy.directiveFor("diagnose")

        assertTrue(directive.contains("student", ignoreCase = true) || directive.contains("teacher", ignoreCase = true))
        assertTrue(!directive.startsWith("Explain the answer", ignoreCase = true))
    }

    @Test
    fun unknown_action_falls_back_to_a_focused_student_question() {
        assertTrue(
            LlmStudentExpressionPolicy.directiveFor("unknown").startsWith("Ask the teacher")
        )
    }

    @Test
    fun guidedVocabularyDirectivesStayStableInEnglish() {
        val diagnose = LlmStudentExpressionPolicy.directiveFor("diagnose")
        assertEquals(
            "State one precise point you still cannot connect, then ask the teacher to diagnose that gap.",
            diagnose,
        )
    }
}
