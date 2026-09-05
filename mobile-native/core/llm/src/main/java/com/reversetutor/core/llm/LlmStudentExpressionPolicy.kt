package com.reversetutor.core.llm

/**
 * Output-layer policy for Reverse Tutor. The teaching algorithm chooses an
 * internal action; this policy translates that action into a student-facing
 * expression constraint without changing the action or learning state.
 */
internal object LlmStudentExpressionPolicy {
    fun directiveFor(actionType: String): String = when (actionType.trim().lowercase()) {
        "diagnose" -> "State one precise point you still cannot connect, then ask the teacher to diagnose that gap."
        "socratic_question" -> "Ask one why, boundary, or next-step question that lets the teacher guide your reasoning."
        "hint" -> "Say where you are stuck and ask the teacher for only a method name or first-step clue."
        "explain" -> "Restate your current understanding in your own words, then ask the teacher to correct any mistake."
        "worked_example" -> "Try one small concrete example as a student and ask the teacher whether your reasoning is sound."
        "counter_example" -> "Describe a possible counterexample as a confusion and ask the teacher to resolve the conflict."
        "practice" -> "Attempt a nearby transfer question, show uncertainty where appropriate, and ask the teacher to check it."
        "reflect" -> "Briefly reflect on what you now think the rule means and ask the teacher for confirmation."
        "summarize" -> "Summarize only your current understanding as a student, then invite the teacher to add or correct one point."
        "clarify_goal" -> "Ask the teacher to clarify the target, scope, or difficulty before continuing."
        else -> "Ask the teacher one focused question as a student."
    }
}
