package com.reversetutor.core.domain

/**
 * NEWMP-V1-002 plan Task 2 · deterministic source-grounded check policy.
 *
 * Two responsibilities, both pure and clock-free:
 *
 *  - [normalize]: accept or reject a check-plan candidate. Acceptance requires
 *    a non-blank source revision, a non-empty set of handles fully contained
 *    in the *current context whitelist*, bounded text and no secret-like
 *    fragments anywhere. A rejected candidate yields `null` — it never becomes
 *    a partial plan.
 *  - [validateAnswer]: decide a saved plan against a candidate answer *locally
 *    only*. A revision mismatch or an undecidable rule yields
 *    [CheckVerification.Unverified]; Unverified is never a learning failure,
 *    so Provider or material drift cannot poison the ledger.
 *
 * The policy intentionally receives no transcript, Provider payload or user
 * identity — the plan and the candidate answer string are its whole input.
 */
object SourceGroundedCheckPolicy {

    const val PLAN_ID_MAX = 80
    const val PROMPT_MAX = 400
    const val EXPECTED_ANSWER_MAX = 200
    const val CONCEPT_KEY_MAX = 40
    const val REVISION_MAX = 120
    const val HANDLE_MAX = 160
    const val MAX_HANDLES = 6
    const val MAX_RUBRIC_CRITERIA = 8
    const val RUBRIC_ITEM_MAX = 160
    const val MAX_CONCEPT_TERMS = 12
    const val CONCEPT_TERM_MAX = 48

    private val sensitivePatterns = listOf(
        Regex("(?i)sk-[a-z0-9_-]{2,}"),
        Regex("(?i)authorization\\s*[:=]"),
        Regex("(?i)bearer\\s+[a-z0-9._-]+"),
        Regex("(?i)https?://[^\\s]+")
    )

    /**
     * Validate and bound a candidate plan against the visible-context handle
     * whitelist. Returns `null` (whole-plan rejection) on: blank revision, no
     * handles, any unknown handle, or any secret-like fragment.
     */
    fun normalize(
        plan: SourceGroundedCheckPlan,
        allowedSourceHandles: Set<String>
    ): SourceGroundedCheckPlan? {
        val revision = plan.sourceRevision.trim().take(REVISION_MAX)
        if (revision.isEmpty()) return null

        val handles = plan.sourceHandles.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_HANDLES)
            .toList()
        if (handles.isEmpty()) return null
        if (handles.any { it.length > HANDLE_MAX || !allowedSourceHandles.contains(it) }) return null

        val id = plan.id.trim().take(PLAN_ID_MAX)
        val prompt = collapseSpaces(plan.prompt).take(PROMPT_MAX)
        val expected = collapseSpaces(plan.expectedAnswer).take(EXPECTED_ANSWER_MAX)
        val conceptKey = collapseSpaces(plan.conceptKey).take(CONCEPT_KEY_MAX)
        if (id.isEmpty() || prompt.isEmpty()) return null

        val rule = when (val rule = plan.rule) {
            is CheckRule.ExactText -> CheckRule.ExactText(collapseSpaces(rule.normalizedAnswer).take(EXPECTED_ANSWER_MAX))
            is CheckRule.NumericTolerance -> CheckRule.NumericTolerance(
                expected = rule.expected.coerceIn(-1e9, 1e9),
                tolerance = rule.tolerance.coerceIn(0.0, 1e6)
            )
            is CheckRule.RequiredConcepts -> CheckRule.RequiredConcepts(
                rule.terms.asSequence()
                    .map { collapseSpaces(it) }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(MAX_CONCEPT_TERMS)
                    .map { it.take(CONCEPT_TERM_MAX) }
                    .toList()
            )
            is CheckRule.Rubric -> CheckRule.Rubric(
                rule.criteria.asSequence()
                    .map { collapseSpaces(it) }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(MAX_RUBRIC_CRITERIA)
                    .map { it.take(RUBRIC_ITEM_MAX) }
                    .toList()
            )
        }

        // Any secret-like fragment anywhere in the normalized plan rejects it.
        val scanFields = buildList {
            add(id); add(revision); add(prompt); add(expected); add(conceptKey)
            addAll(handles)
            when (rule) {
                is CheckRule.ExactText -> add(rule.normalizedAnswer)
                is CheckRule.RequiredConcepts -> addAll(rule.terms)
                is CheckRule.Rubric -> addAll(rule.criteria)
                is CheckRule.NumericTolerance -> Unit
            }
        }
        if (scanFields.any { field -> sensitivePatterns.any { it.containsMatchIn(field) } }) return null

        return SourceGroundedCheckPlan(
            id = id,
            sourceRevision = revision,
            sourceHandles = handles,
            prompt = prompt,
            expectedAnswer = expected,
            rule = rule,
            conceptKey = conceptKey.ifEmpty { GuidedLearningContracts.UNKNOWN_CONCEPT }
        )
    }

    /**
     * Locally decide a candidate answer for an already-normalized [plan].
     * Never throws and never reads anything beyond the two strings given.
     */
    fun validateAnswer(
        plan: SourceGroundedCheckPlan,
        candidateAnswer: String,
        currentSourceRevision: String
    ): CheckVerification {
        if (plan.sourceRevision.isBlank() || currentSourceRevision.trim() != plan.sourceRevision) {
            return CheckVerification.Unverified
        }
        return when (val rule = plan.rule) {
            is CheckRule.ExactText ->
                if (collapseSpaces(candidateAnswer).lowercase() == rule.normalizedAnswer.lowercase()) {
                    CheckVerification.VerifiedPassed
                } else {
                    CheckVerification.VerifiedFailed
                }

            is CheckRule.NumericTolerance -> {
                val parsed = parseNumber(candidateAnswer)
                when {
                    parsed == null -> CheckVerification.VerifiedFailed
                    kotlin.math.abs(parsed - rule.expected) <= rule.tolerance -> CheckVerification.VerifiedPassed
                    else -> CheckVerification.VerifiedFailed
                }
            }

            is CheckRule.RequiredConcepts -> {
                if (rule.terms.isEmpty()) return CheckVerification.Unverified
                val answer = collapseSpaces(candidateAnswer).lowercase()
                val hits = rule.terms.count { answer.contains(it.lowercase()) }
                when {
                    hits == rule.terms.size -> CheckVerification.VerifiedPassed
                    hits == 0 -> CheckVerification.VerifiedFailed
                    else -> CheckVerification.VerifiedPartial
                }
            }

            // Free-form rubric criteria cannot be decided by deterministic local
            // rules; claiming otherwise would be guessing at mastery.
            is CheckRule.Rubric -> CheckVerification.Unverified
        }
    }

    private fun parseNumber(candidate: String): Double? {
        val token = candidate.trim()
            .replace(",", ".")
            .replace("，", ".")
            .let { Regex("[-+]?\\d+(\\.\\d+)?").find(it)?.value }
        return token?.toDoubleOrNull()
    }

    private fun collapseSpaces(value: String): String =
        value.trim().replace(Regex("\\s+"), " ")
}
