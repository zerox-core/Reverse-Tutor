package com.reversetutor.core.domain

/**
 * Pure window-topology decision policy.
 *
 * This object is pure Kotlin: it never reads a clock, Room, a DAO, a Repository,
 * an LLM, or any Android API. It returns the same result for the same
 * `(childId, parentId, deltaId, sourceRevision)` tuple.
 *
 * Rules encoded here (mirrors the design's acceptance invariants):
 * - Every root has heartbeat capability enabled by default.
 * - A child has no heartbeat until an explicit enable command.
 * - A child snapshot reads only the ancestor revisions at its fork.
 * - A child may merge only into its direct parent; siblings never merge.
 * - A merge is a one-way, idempotent commit; later child changes never mutate it.
 * - Branch deletion never rewinds a committed parent merge or a global receipt.
 */
object WindowTopologyPolicy {

    fun defaultHeartbeatState(kind: WindowKind): WindowHeartbeatState =
        if (kind == WindowKind.CHILD) WindowHeartbeatState.ChildDisabled
        else WindowHeartbeatState.RootEnabled

    /** Idempotent heartbeat enable. A root stays [WindowHeartbeatState.RootEnabled]. */
    fun enableHeartbeat(state: WindowHeartbeatState): WindowHeartbeatState =
        when (state) {
            WindowHeartbeatState.RootEnabled -> WindowHeartbeatState.RootEnabled
            WindowHeartbeatState.ChildDisabled -> WindowHeartbeatState.ExplicitlyEnabled
            WindowHeartbeatState.ExplicitlyEnabled -> WindowHeartbeatState.ExplicitlyEnabled
        }

    /** True when [snapshot] may serve the requested [revision] (fork-bound isolation). */
    fun snapshotCovers(snapshot: WindowSnapshotRef, revision: Long): Boolean =
        revision >= 0L && revision <= snapshot.ancestorRevision

    /** Stable, deterministic merge-commit identifier. */
    fun mergeCommitId(childId: String, parentId: String, deltaId: String, sourceRevision: Long): String =
        "$childId|$parentId|$deltaId|$sourceRevision"

    /**
     * Validate a merge proposal. Only a direct child may target its direct
     * parent; siblings and self-targets are denied; a non-child may not merge.
     */
    fun mergeDecision(child: WindowRef, parent: WindowRef, deltaId: String, sourceRevision: Long): WindowMergeDecision {
        if (!child.isChild) return WindowMergeDecision.Denied(MergeDenial.NOT_CHILD)
        if (child.id == parent.id) return WindowMergeDecision.Denied(MergeDenial.SELF_TARGET)
        if (child.parentId != parent.id) {
            val denial = if (parent.kind == WindowKind.CHILD) MergeDenial.SIBLING_TARGET else MergeDenial.NOT_DIRECT_PARENT
            return WindowMergeDecision.Denied(denial)
        }
        return WindowMergeDecision.Allowed(
            MergeCommit(
                id = mergeCommitId(child.id, parent.id, deltaId, sourceRevision),
                childId = child.id,
                parentId = parent.id,
                deltaId = deltaId,
                sourceRevision = sourceRevision
            )
        )
    }

    /** Branch deletion never rewinds a committed parent merge or a global receipt. */
    fun branchDeletionEffect(deletedChildId: String): BranchDeletionEffect =
        BranchDeletionEffect(deletedBranchId = deletedChildId)
}
