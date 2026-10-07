package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Window topology policy tests.
 *
 * Package A (task 2): pure window-tree contracts and deterministic branch
 * policy. These must not read a clock, Room, a DAO, a Repository, or an LLM.
 * The policy is an [object] and returns the same result for the same
 * `(childId, parentId, deltaId, sourceRevision)` tuple.
 */
class WindowTopologyPolicyTest {

    private val parent = WindowRef("p1", "root-1", null, WindowKind.LEARNING_ROOT)

    private fun child(id: String, parentId: String) =
        WindowRef(id, "root-1", parentId, WindowKind.CHILD)

    @Test
    fun root_has_heartbeat_enabled_by_default() {
        listOf(
            WindowKind.COMPANION_ROOT,
            WindowKind.LEARNING_ROOT,
            WindowKind.TASK_ROOT
        ).forEach { kind ->
            assertEquals(WindowHeartbeatState.RootEnabled, WindowTopologyPolicy.defaultHeartbeatState(kind))
        }
    }

    @Test
    fun child_has_no_heartbeat_until_explicit_command() {
        assertEquals(WindowHeartbeatState.ChildDisabled, WindowTopologyPolicy.defaultHeartbeatState(WindowKind.CHILD))
        val after = WindowTopologyPolicy.enableHeartbeat(WindowTopologyPolicy.defaultHeartbeatState(WindowKind.CHILD))
        assertEquals(WindowHeartbeatState.ExplicitlyEnabled, after)
    }

    @Test
    fun child_snapshot_stops_at_its_fork_revision() {
        val snapshot = WindowSnapshotRef(ancestorRevision = 7L, forkedAtEpochMillis = 123L)
        assertTrue(WindowTopologyPolicy.snapshotCovers(snapshot, 7L))
        assertTrue(WindowTopologyPolicy.snapshotCovers(snapshot, 0L))
        assertFalse(WindowTopologyPolicy.snapshotCovers(snapshot, 8L))
        assertFalse(WindowTopologyPolicy.snapshotCovers(snapshot, -1L))
    }

    @Test
    fun only_direct_child_can_target_its_parent_for_merge() {
        val c = child("c1", "p1")
        val commit = allowedMerge(c, parent, "delta-1", 9L)
        assertEquals("p1", commit.parentId)
        assertEquals("c1", commit.childId)

        val rootCannotMerge = WindowTopologyPolicy.mergeDecision(parent, c, "delta-x", 9L)
        assertTrue(rootCannotMerge is WindowMergeDecision.Denied)
        assertEquals(MergeDenial.NOT_CHILD, (rootCannotMerge as WindowMergeDecision.Denied).reason)
    }

    @Test
    fun siblings_cannot_merge() {
        val a = child("a1", "p1")
        val b = child("b1", "p1")
        val decision = WindowTopologyPolicy.mergeDecision(a, b, "delta-1", 9L)
        assertTrue(decision is WindowMergeDecision.Denied)
        assertEquals(MergeDenial.SIBLING_TARGET, (decision as WindowMergeDecision.Denied).reason)
    }

    @Test
    fun duplicate_merge_commit_is_idempotent() {
        val c = child("c1", "p1")
        val first = allowedMerge(c, parent, "delta-1", 9L)
        val second = allowedMerge(c, parent, "delta-1", 9L)
        assertEquals(first, second)
        assertEquals(first.id, second.id)
        assertEquals("c1", second.childId)
        assertEquals("p1", second.parentId)
        assertEquals("delta-1", second.deltaId)
        assertEquals(9L, second.sourceRevision)
    }

    @Test
    fun child_mutation_after_merge_cannot_change_parent_commit() {
        val c = child("c1", "p1")
        val original = allowedMerge(c, parent, "delta-1", 9L)
        val next = allowedMerge(c, parent, "delta-2", 10L)
        assertNotEquals(original, next)
        assertNotEquals(original.id, next.id)
        val reChecked = allowedMerge(c, parent, "delta-1", 9L)
        assertEquals(original, reChecked)
        assertEquals("delta-1", original.deltaId)
        assertEquals(9L, original.sourceRevision)
    }

    @Test
    fun deleting_child_preserves_parent_merge_and_global_learning_receipt() {
        val c = child("c1", "p1")
        val commit = allowedMerge(c, parent, "delta-1", 9L)
        val effect = WindowTopologyPolicy.branchDeletionEffect("c1")
        assertTrue(effect.removesLocalConversation)
        assertTrue(effect.removesLocalMemory)
        assertTrue(effect.preservesCommittedParentMerges)
        assertTrue(effect.preservesGlobalLearningReceipts)
        assertEquals(commit, allowedMerge(c, parent, "delta-1", 9L))
    }

    private fun allowedMerge(c: WindowRef, p: WindowRef, deltaId: String, revision: Long): MergeCommit {
        val decision = WindowTopologyPolicy.mergeDecision(c, p, deltaId, revision)
        assertTrue("expected allowed merge for $c -> $p", decision is WindowMergeDecision.Allowed)
        return (decision as WindowMergeDecision.Allowed).commit
    }
}
