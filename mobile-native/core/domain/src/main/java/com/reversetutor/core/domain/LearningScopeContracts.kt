package com.reversetutor.core.domain

/**
 * Learning-scope guard contracts — soft-only, transcript-minimizing.
 *
 * These types live in [core:domain] (non-frozen) and are Android-free. A
 * [ScopeSignal] stores only a category, count, source-turn handle, and event
 * time, never raw user text. The
 * only intervention a [ScopeDecision] can produce is a soft [ScopeDecision.reanchorConstraint].
 */
data class LearningIntentEnvelope(
    val windowId: String,
    val declaredSubject: String,
    val declaredLevel: String,
    val declaredGoal: String,
    val studyMethod: String
)

enum class ScopeRelation {
    CONTINUOUS,
    RELATED_EVOLUTION,
    AMBIGUOUS,
    SUSTAINED_OUT_OF_SCOPE
}

object ScopeSignalCategory {
    const val SUBJECT_PROGRESSION = "subject_progression"
    const val LEVEL_PROGRESSION = "level_progression"
    const val METHOD_CHANGE = "method_change"
    const val STYLE_CHANGE = "style_change"
    const val UNRELATED = "unrelated"
}

/** A minimal structured trajectory signal (no raw text). */
data class ScopeSignal(
    val category: String,
    val count: Int,
    val sourceTurnId: String = "",
    val observedAtEpochMillis: Long = 0L
) {
    companion object {
        /** Only these fields may be persisted; raw user text must never join. */
        val ALLOWED_PERSISTED_FIELDS: Set<String> = setOf(
            "category", "count", "sourceTurnId", "observedAtEpochMillis"
        )
    }
}

data class ScopeDecision(
    val relation: ScopeRelation,
    val reanchorConstraint: String? = null
)
