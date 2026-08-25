package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.BranchDeletionEffect

/**
 * Single orchestration entry for deleting a child window (branch).
 *
 * It delegates to domain-safe capability seams the app wiring binds to the P6
 * repositories, so the UI never composes Repository calls itself. It removes only
 * artifacts owned by [childId]: local conversation, local window deltas, local
 * companion memory, the window heartbeat schedule, the window's pending jobs, and
 * the window topology row itself. It has NO seam to touch a committed parent merge
 * receipt, a global learning receipt, or a sibling window — so those are preserved
 * by construction. It never calls a Provider and never writes an assistant record.
 */
class WindowBranchDeletionCoordinator(
    private val deleteLocalConversation: suspend (windowId: String) -> Int,
    private val deleteLocalDeltas: suspend (windowId: String) -> Int,
    private val deleteLocalMemory: suspend (windowId: String) -> Int,
    private val deleteHeartbeatSchedule: suspend (windowId: String) -> Int,
    private val cancelPendingJobs: suspend (windowId: String) -> Int,
    private val deleteWindow: suspend (windowId: String) -> Int
) {

    suspend fun deleteBranch(childId: String): BranchDeletionEffect {
        deleteLocalConversation(childId)
        deleteLocalDeltas(childId)
        deleteLocalMemory(childId)
        deleteHeartbeatSchedule(childId)
        cancelPendingJobs(childId)
        deleteWindow(childId)
        return BranchDeletionEffect(deletedBranchId = childId)
    }
}
