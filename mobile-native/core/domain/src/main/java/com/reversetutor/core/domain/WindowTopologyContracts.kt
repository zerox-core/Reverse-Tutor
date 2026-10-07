package com.reversetutor.core.domain

/**
 * Window-tree topology contracts — wire-safe, Android-free types used by the
 * pure [WindowTopologyPolicy].
 *
 * These types live in [core:domain] (non-frozen). Every value is a stable
 * string/enum so the persistence layer (P6) can translate them without touching
 * frozen [core:model]/[core:protocol]/[core:data] contracts.
 */
enum class WindowKind { COMPANION_ROOT, LEARNING_ROOT, TASK_ROOT, CHILD }

/** Identifies a single window inside a topology. */
data class WindowRef(
    val id: String,
    val rootId: String,
    val parentId: String?,
    val kind: WindowKind
) {
    val isRoot: Boolean get() = kind != WindowKind.CHILD
    val isChild: Boolean get() = kind == WindowKind.CHILD
}

/**
 * The immutable parent snapshot a child receives at fork time. Parent changes
 * after [ancestorRevision] never flow into the child.
 */
data class WindowSnapshotRef(
    val ancestorRevision: Long,
    val forkedAtEpochMillis: Long
)

/** A one-way, idempotent commit of a selected memory delta into its parent. */
data class MergeCommit(
    val id: String,
    val childId: String,
    val parentId: String,
    val deltaId: String,
    val sourceRevision: Long
)

/**
 * Default heartbeat capability per window kind. A root always owns a heartbeat;
 * a child has none until an explicit enable command targets it.
 */
sealed interface WindowHeartbeatState {
    data object RootEnabled : WindowHeartbeatState
    data object ChildDisabled : WindowHeartbeatState
    data object ExplicitlyEnabled : WindowHeartbeatState
}

/** Outcome of a merge proposal against the topology policy. */
sealed interface WindowMergeDecision {
    data class Allowed(val commit: MergeCommit) : WindowMergeDecision
    data class Denied(val reason: MergeDenial) : WindowMergeDecision
}

enum class MergeDenial {
    NOT_CHILD, NOT_DIRECT_PARENT, SIBLING_TARGET, SELF_TARGET
}

/**
 * The effect of deleting a branch. Branch deletion removes only records owned
 * by that branch; it never rewinds a committed parent merge nor a normalized
 * global learning receipt.
 */
data class BranchDeletionEffect(
    val deletedBranchId: String,
    val removesLocalConversation: Boolean = true,
    val removesLocalMemory: Boolean = true,
    val preservesCommittedParentMerges: Boolean = true,
    val preservesGlobalLearningReceipts: Boolean = true
)
