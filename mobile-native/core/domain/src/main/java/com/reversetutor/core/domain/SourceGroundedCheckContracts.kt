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
    /**
     * NEWMP-V1-004 Task 1: explicit per-handle revision bindings. An empty map
     * means the legacy single-revision format: every handle is validated
     * against [sourceRevision]. A non-empty map must cover **every** handle in
     * [sourceHandles] with a non-blank revision, so no referenced source can be
     * validated less strictly than the others.
     */
    val sourceRevisions: Map<String, String> = emptyMap(),
    val prompt: String,
    val expectedAnswer: String,
    val rule: CheckRule,
    val conceptKey: String
) {
    /** The revision this plan requires for one handle (explicit binding wins; legacy plans share [sourceRevision]). */
    fun revisionFor(handle: String): String =
        sourceRevisions[handle]?.takeIf { it.isNotBlank() } ?: sourceRevision
}

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
