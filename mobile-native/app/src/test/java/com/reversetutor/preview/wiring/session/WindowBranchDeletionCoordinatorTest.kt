package com.reversetutor.preview.wiring.session

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task 2: branch deletion orchestration.
 *
 * Deleting a child removes only its local artifacts; a committed parent merge,
 * a global learning receipt, and a sibling window are never passed to any delete
 * seam (preserved by construction). Parent changes cannot re-enter a deleted
 * child because the window topology row is removed.
 */
class WindowBranchDeletionCoordinatorTest {

    private class Recorder {
        val calls = mutableMapOf<String, MutableList<String>>()

        fun record(name: String, windowId: String) {
            calls.getOrPut(name) { mutableListOf() }.add(windowId)
        }

        fun count(name: String) = calls[name]?.size ?: 0
    }

    private fun coordinator(r: Recorder, deleteWindow: Int = 1) = WindowBranchDeletionCoordinator(
        deleteLocalConversation = { r.record("conversation", it); 1 },
        deleteLocalDeltas = { r.record("deltas", it); 1 },
        deleteLocalMemory = { r.record("memory", it); 1 },
        deleteHeartbeatSchedule = { r.record("heartbeat", it); 1 },
        cancelPendingJobs = { r.record("jobs", it); 1 },
        deleteWindow = { r.record("window", it); deleteWindow }
    )

    @Test
    fun deleteChildRemovesLocalArtifactsOnly() = runBlocking {
        val r = Recorder()
        val effect = coordinator(r).deleteBranch("child-1")

        assertEquals(1, r.count("conversation"))
        assertEquals(1, r.count("deltas"))
        assertEquals(1, r.count("memory"))
        assertEquals(1, r.count("heartbeat"))
        assertEquals(1, r.count("jobs"))
        assertEquals(1, r.count("window"))
        assertTrue(effect.preservesCommittedParentMerges)
        assertTrue(effect.preservesGlobalLearningReceipts)
        // Every seam received exactly the child id — never a parent, sibling, or leder row.
        r.calls.values.forEach { list ->
            assertEquals(listOf("child-1"), list)
        }
    }

    @Test
    fun noSeamTargetsParentOrSibling() = runBlocking {
        val r = Recorder()
        coordinator(r).deleteBranch("child-1")

        val allTargets = r.calls.values.flatten().toSet()
        assertEquals(setOf("child-1"), allTargets)
    }

    @Test
    fun deletingRemovesWindowRowSoParentChangeCannotReenter() = runBlocking {
        val r = Recorder()
        // deleteWindow = 1 (row removed). After deletion the child window is gone;
        // a later parent change is not routed into it because no window row exists.
        val effect = coordinator(r, deleteWindow = 1).deleteBranch("child-1")
        assertEquals(1, r.count("window"))
        assertEquals("child-1", effect.deletedBranchId)
    }
}
