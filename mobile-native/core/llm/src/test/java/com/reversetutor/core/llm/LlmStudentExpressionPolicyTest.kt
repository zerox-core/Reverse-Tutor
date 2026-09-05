package com.reversetutor.core.llm

import org.junit.Assert.assertTrue
import org.junit.Test

class LlmStudentExpressionPolicyTest {
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
}
