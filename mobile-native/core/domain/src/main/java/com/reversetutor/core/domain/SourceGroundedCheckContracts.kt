package com.reversetutor.core.domain

/**
 * NEWMP-V1-002 plan Task 2 · source-grounded check contracts.
 *
 * A [SourceGroundedCheckPlan] is a bounded, privacy-safe question whose content
 * is tied to one materialized source revision. LLM-produced questions, expected
 * answers and rubrics are only ever *candidates* in this shape until a local
 * [CheckRule] decides them — never a Provider claim of mastery.
 *
 * These types carry no Android, storage or generation dependency and no raw
 * transcript: only the question, a canonical expected answer token and the
 * rule needed to decide it.
 */

data class SourceGroundedCheckPlan(
    val id: String,
    val sourceRevision: String,
    val sourceHandles: List<String>,
    val prompt: String,
    val expectedAnswer: String,
    val rule: CheckRule,
    val conceptKey: String
)

sealed interface CheckRule {
    /** Normalized exact-string decision. */
    data class ExactText(val normalizedAnswer: String) : CheckRule

    /** Numeric decision with an inclusive tolerance band. */
    data class NumericTolerance(val expected: Double, val tolerance: Double) : CheckRule

    /** Every listed concept must appear; a subset yields partial credit. */
    data class RequiredConcepts(val terms: List<String>) : CheckRule

    /** Free-form criteria. Locally undecidable — always maps to Unverified. */
    data class Rubric(val criteria: List<String>) : CheckRule
}

enum class CheckVerification {
    VerifiedPassed,
    VerifiedPartial,
    VerifiedFailed,
    Unverified
}
