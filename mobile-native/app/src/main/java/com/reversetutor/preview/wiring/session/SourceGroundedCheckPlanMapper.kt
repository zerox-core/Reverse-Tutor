package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.CheckRule
import com.reversetutor.core.domain.SourceGroundedCheckPlan
import com.reversetutor.core.domain.SourceGroundedCheckPolicy
import com.reversetutor.core.llm.LlmSourceCheckRule
import com.reversetutor.core.llm.LlmSourceGroundedCheckPlan

/** Converts an untrusted provider candidate into a domain check plan. */
internal fun LlmSourceGroundedCheckPlan.toDomainCheckPlan(
    allowedSourceHandles: Set<String>
): SourceGroundedCheckPlan? {
    val normalized = normalized() ?: return null
    val rule = when (val candidate = normalized.rule) {
        is LlmSourceCheckRule.ExactText -> CheckRule.ExactText(candidate.normalizedAnswer)
        is LlmSourceCheckRule.NumericTolerance -> CheckRule.NumericTolerance(candidate.expected, candidate.tolerance)
        is LlmSourceCheckRule.RequiredConcepts -> CheckRule.RequiredConcepts(candidate.terms)
        is LlmSourceCheckRule.Rubric -> CheckRule.Rubric(candidate.criteria)
    }
    return SourceGroundedCheckPolicy.normalize(
        SourceGroundedCheckPlan(
            id = normalized.id,
            sourceRevision = normalized.sourceRevision,
            sourceHandles = normalized.sourceReferenceIds,
            prompt = normalized.prompt,
            expectedAnswer = normalized.expectedAnswer,
            rule = rule,
            conceptKey = normalized.conceptKey
        ),
        allowedSourceHandles = allowedSourceHandles
    )
}

internal fun LlmSourceGroundedCheckPlan.sourceRevisionFor(
    allowedSourceHandles: Set<String>
): String? = normalized()
    ?.takeIf { it.sourceReferenceIds.all(allowedSourceHandles::contains) }
    ?.sourceRevision
