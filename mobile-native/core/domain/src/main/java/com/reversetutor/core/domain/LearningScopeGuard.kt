package com.reversetutor.core.domain

/**
 * Pure soft learning-scope guard.
 *
 * Only a window that owns a [LearningIntentEnvelope] runs this guard; the
 * companion root has no learning intent and never runs it. The guard classifies
 * a trajectory signal as continuous, related evolution, ambiguous, or sustained
 * out-of-scope. It never blocks a turn and never rewrites the declared intent:
 * for sustained high-confidence drift the only intervention is a soft
 * [ScopeDecision.reanchorConstraint] on the next turn plan.
 */
object LearningScopeGuard {

    private const val SUSTAINED_DRIFT_THRESHOLD = 3

    private val PROGRESSION_CATEGORIES = setOf(
        ScopeSignalCategory.SUBJECT_PROGRESSION,
        ScopeSignalCategory.LEVEL_PROGRESSION,
        ScopeSignalCategory.METHOD_CHANGE,
        ScopeSignalCategory.STYLE_CHANGE
    )

    /** True when a window actually runs the scope guard. */
    fun forWindow(window: WindowRef, envelope: LearningIntentEnvelope?): Boolean =
        window.kind != WindowKind.COMPANION_ROOT && envelope != null

    fun classify(envelope: LearningIntentEnvelope, signals: List<ScopeSignal>): ScopeDecision {
        val unrelatedCount = signals.filter { it.category == ScopeSignalCategory.UNRELATED }.sumOf { it.count }
        return when {
            unrelatedCount >= SUSTAINED_DRIFT_THRESHOLD ->
                ScopeDecision(ScopeRelation.SUSTAINED_OUT_OF_SCOPE, reanchorConstraint(envelope))
            signals.any { it.category in PROGRESSION_CATEGORIES } ->
                ScopeDecision(ScopeRelation.RELATED_EVOLUTION)
            unrelatedCount >= 1 ->
                ScopeDecision(ScopeRelation.AMBIGUOUS)
            else ->
                ScopeDecision(ScopeRelation.CONTINUOUS)
        }
    }

    private fun reanchorConstraint(envelope: LearningIntentEnvelope): String =
        "尊重已声明学习范围（${envelope.declaredSubject} / ${envelope.declaredLevel}），保持既定目标，不做硬性改变"
}
