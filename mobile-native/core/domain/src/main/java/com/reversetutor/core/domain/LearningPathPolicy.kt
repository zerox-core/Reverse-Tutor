package com.reversetutor.core.domain

/**
 * Ordered learning path (R86).
 *
 * The session's knowledge points frozen at creation time into one ordered
 * sequence — textbook table-of-contents order when a document supplied one,
 * otherwise the LLM's decomposition during creation. Both sources land in this
 * single type; after creation the path is read-only and the deterministic
 * [decide] walk owns progression: advance along the path, stay to consolidate,
 * or regress to patch a foundation gap. The LLM only ever *expresses* the
 * chosen step — it can never reorder, skip or invent the sequence.
 *
 * Progression is state-driven: there is no persisted cursor. Each turn the
 * walk is recomputed from the per-node [ConceptLearningState] map (sourced
 * from the mastery ledger), so process restarts and out-of-band mastery
 * updates converge to the same decision. An empty path keeps the legacy
 * free-form behavior exactly.
 */

/** One frozen step on the path. [key] is the mastery-ledger join key. */
data class LearningPathNode(
    val key: String = "",
    val label: String = ""
)

/** An ordered, bounded sequence of knowledge points. Empty = no path (legacy). */
data class LearningPath(
    val nodes: List<LearningPathNode> = emptyList()
) {
    val size: Int get() = nodes.size

    fun keyAt(index: Int): String = nodes.getOrNull(index)?.key.orEmpty()

    fun labelAt(index: Int): String = nodes.getOrNull(index)?.label.orEmpty()

    /** trim → cap → drop blank keys → dedupe by key (first wins) → cap node count. */
    fun normalized(): LearningPath {
        val seen = LinkedHashSet<String>()
        return LearningPath(
            nodes.asSequence()
                .map { node ->
                    val key = node.key.trim().take(GuidedLearningContracts.CONCEPT_KEY_MAX)
                    val label = node.label.trim()
                        .take(LearningPathPolicy.NODE_LABEL_MAX)
                        .ifEmpty { key }
                    LearningPathNode(key = key, label = label)
                }
                .filter { it.key.isNotEmpty() }
                .filter { seen.add(it.key) }
                .take(LearningPathPolicy.MAX_NODES)
                .toList()
        )
    }
}

/** How the path walk moves this turn. */
enum class PathMove {
    /** Enter the path for the first time (no mastery evidence anywhere yet). */
    Start,

    /** Stay on the current node to consolidate. */
    Stay,

    /** Move forward to the next not-yet-mastered node. */
    Advance,

    /** Move back to an earlier not-yet-mastered node to patch a foundation gap. */
    Regress,

    /** Every node on the path is mastered. */
    Completed
}

/** The deterministic outcome of one path walk. */
data class PathDecision(
    val move: PathMove = PathMove.Stay,
    val targetIndex: Int = -1,
    val targetLabel: String = "",
    val reason: String = ""
)

object LearningPathPolicy {

    const val MAX_NODES = 12
    const val NODE_LABEL_MAX = 48

    /** A node counts as done at Stable or above — advancement never waits for perfection. */
    private val masteredStatuses = setOf(ConceptStatus.Stable, ConceptStatus.Mastered)

    /** Consecutive failures on a Fragile node that trigger a foundation regress. */
    private const val REGRESS_FAILURE_STREAK = 2

    /** Build a path from raw ordered labels (textbook TOC or LLM decomposition). */
    fun fromLabels(labels: List<String>): LearningPath = LearningPath(
        labels.map { label -> LearningPathNode(key = label.trim(), label = label.trim()) }
    ).normalized()

    /**
     * Walk the path for one turn. Pure: same (path, states, currentKey) ⇒ same decision.
     *
     * - current node mastered → first not-mastered node after it (Advance); if none
     *   remains but an earlier gap exists, backfill it (Regress); else Completed;
     * - current node Fragile with [REGRESS_FAILURE_STREAK]+ consecutive failures →
     *   nearest not-mastered foundation before it (Regress), or Stay at the head;
     * - otherwise Stay on the current node;
     * - current key off-path → first not-mastered node (Start when no mastery
     *   evidence exists at all, Advance otherwise); all mastered → Completed.
     */
    fun decide(
        path: LearningPath,
        conceptStates: Map<String, ConceptLearningState>,
        currentKey: String
    ): PathDecision {
        val nodes = path.normalized().nodes
        if (nodes.isEmpty()) return PathDecision(PathMove.Stay, -1, "", "路径为空")

        fun masteredAt(index: Int): Boolean =
            conceptStates[nodes[index].key]?.status in masteredStatuses

        val currentIndex = nodes.indexOfFirst {
            it.key == currentKey.trim().take(GuidedLearningContracts.CONCEPT_KEY_MAX)
        }

        if (currentIndex >= 0) {
            val state = conceptStates[nodes[currentIndex].key]
            if (state != null &&
                state.status == ConceptStatus.Fragile &&
                state.failureStreak >= REGRESS_FAILURE_STREAK
            ) {
                for (index in currentIndex - 1 downTo 0) {
                    if (!masteredAt(index)) {
                        return PathDecision(
                            PathMove.Regress, index, nodes[index].label, "当前知识点连续受挫，回退补基础"
                        )
                    }
                }
                return PathDecision(
                    PathMove.Stay, currentIndex, nodes[currentIndex].label, "已在路径起点，原地补基础"
                )
            }
            if (masteredAt(currentIndex)) {
                for (index in currentIndex + 1 until nodes.size) {
                    if (!masteredAt(index)) {
                        return PathDecision(
                            PathMove.Advance, index, nodes[index].label, "当前已掌握，推进到下一知识点"
                        )
                    }
                }
                for (index in 0 until currentIndex) {
                    if (!masteredAt(index)) {
                        return PathDecision(
                            PathMove.Regress, index, nodes[index].label, "路径前段仍有缺口，先回填"
                        )
                    }
                }
                return PathDecision(PathMove.Completed, -1, "", "路径知识点全部掌握")
            }
            return PathDecision(
                PathMove.Stay, currentIndex, nodes[currentIndex].label, "当前知识点未掌握，留在原地巩固"
            )
        }

        val firstOpen = nodes.indices.firstOrNull { !masteredAt(it) }
            ?: return PathDecision(PathMove.Completed, -1, "", "路径知识点全部掌握")
        return if (conceptStates.isEmpty()) {
            PathDecision(PathMove.Start, firstOpen, nodes[firstOpen].label, "进入学习路径起点")
        } else {
            PathDecision(PathMove.Advance, firstOpen, nodes[firstOpen].label, "对齐到路径上首个未掌握知识点")
        }
    }
}
