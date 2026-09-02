package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.EnableWindowHeartbeatCommand
import com.reversetutor.core.domain.HeartbeatScheduleContract
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.feature.chat.CreateWindowBranchRequest
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.WindowBranchHeartbeat
import com.reversetutor.feature.chat.WindowBranchItem
import com.reversetutor.feature.chat.WindowBranchMerge
import com.reversetutor.feature.chat.WindowBranchPort
import com.reversetutor.feature.chat.WindowBranchResult
import com.reversetutor.feature.chat.WindowBranchSnapshot

/**
 * App-owned branch orchestration. It receives capability seams and never leaks
 * repositories into feature-chat. It neither calls a Provider nor writes an
 * assistant message.
 */
class WindowBranchCoordinator(
    private val sessionExists: suspend (String) -> Boolean,
    private val sessionTitle: suspend (String) -> String?,
    private val readWindow: suspend (String) -> WindowRef?,
    private val createTaskRoot: suspend (String, Long) -> WindowRef,
    private val listWindows: suspend () -> List<WindowRef>,
    private val loadSessionSnapshot: (String) -> NewSessionConfiguration?,
    private val createSession: suspend (NewSessionConfiguration, String, Long) -> Boolean,
    private val saveSessionSnapshot: (String, NewSessionConfiguration) -> Unit,
    private val forkChild: suspend (String, String, Long, Long) -> Unit,
    private val localMessageCount: suspend (String) -> Long,
    private val rollbackChildSession: suspend (String) -> Unit,
    private val heartbeatFor: suspend (WindowRef) -> HeartbeatScheduleContract,
    private val enableChildHeartbeat: suspend (WindowRef, EnableWindowHeartbeatCommand) -> Unit,
    private val deleteHeartbeat: suspend (String) -> Unit,
    private val cancelSessionGenerationJobs: suspend (String, Long) -> Unit,
    private val deleteBranch: suspend (String) -> Unit,
    private val deleteAvailable: Boolean = false,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : WindowBranchPort {

    override suspend fun load(windowId: String): WindowBranchSnapshot {
        val current = ensureWindow(windowId) ?: return missingSnapshot(windowId)
        val parent = current.parentId?.let { parentId -> readWindow(parentId) }
        val children = listWindows()
            .filter { it.parentId == current.id }
            .sortedBy { it.id }
            .map { child -> item(child) }
        val schedule = heartbeatFor(current)
        return WindowBranchSnapshot(
            current = item(current),
            parent = parent?.let { parentWindow -> item(parentWindow) },
            children = children,
            heartbeat = heartbeat(current, schedule.enabled),
            merge = WindowBranchMerge.UnavailableNoDelta,
            canDelete = current.kind == WindowKind.CHILD && deleteAvailable,
            deleteUnavailableReason = if (current.kind == WindowKind.CHILD && !deleteAvailable) {
                "删除能力暂不可用"
            } else {
                null
            }
        )
    }

    override suspend fun createChild(request: CreateWindowBranchRequest): WindowBranchResult {
        val parent = ensureWindow(request.parentWindowId) ?: return WindowBranchResult.NotFound
        val sourceSnapshot = loadSessionSnapshot(parent.id) ?: return WindowBranchResult.UnavailableConfiguration
        val now = nowEpochMillis()
        val childId = "branch-${parent.id}-$now"
        val childSnapshot = sourceSnapshot.copy(title = request.title.trim().ifBlank { "${sourceSnapshot.title} · 分支" })
        return try {
            if (!createSession(childSnapshot, childId, now)) return WindowBranchResult.Failed
            saveSessionSnapshot(childId, childSnapshot)
            forkChild(childId, parent.id, localMessageCount(parent.id), now)
            WindowBranchResult.Created(WindowBranchItem(childId, childSnapshot.title, WindowKind.CHILD))
        } catch (_: Exception) {
            rollbackChildSession(childId)
            WindowBranchResult.Failed
        }
    }

    override suspend fun setHeartbeat(windowId: String, enabled: Boolean): WindowBranchResult {
        val window = ensureWindow(windowId) ?: return WindowBranchResult.NotFound
        if (window.kind != WindowKind.CHILD && !enabled) return WindowBranchResult.InvalidHeartbeatTransition
        return try {
            if (window.kind == WindowKind.CHILD && enabled) {
                enableChildHeartbeat(
                    window,
                    EnableWindowHeartbeatCommand(windowId = window.id, requestedBy = "branch_management")
                )
            } else if (window.kind == WindowKind.CHILD) {
                cancelSessionGenerationJobs(window.id, nowEpochMillis())
                deleteHeartbeat(window.id)
            }
            WindowBranchResult.HeartbeatUpdated
        } catch (_: Exception) {
            WindowBranchResult.Failed
        }
    }

    override suspend fun deleteChild(windowId: String): WindowBranchResult {
        if (!deleteAvailable) return WindowBranchResult.Failed
        val window = ensureWindow(windowId) ?: return WindowBranchResult.NotFound
        val parentId = window.parentId ?: return WindowBranchResult.InvalidParent
        if (window.kind != WindowKind.CHILD || readWindow(parentId) == null) return WindowBranchResult.InvalidParent
        return try {
            deleteBranch(window.id)
            WindowBranchResult.Deleted(parentId)
        } catch (_: Exception) {
            WindowBranchResult.Failed
        }
    }

    private suspend fun ensureWindow(sessionId: String): WindowRef? {
        readWindow(sessionId)?.let { return it }
        if (!sessionExists(sessionId)) return null
        return createTaskRoot(sessionId, nowEpochMillis())
    }

    private suspend fun item(window: WindowRef): WindowBranchItem =
        WindowBranchItem(window.id, sessionTitle(window.id) ?: "当前会话", window.kind)

    private fun heartbeat(window: WindowRef, enabled: Boolean): WindowBranchHeartbeat =
        if (window.kind == WindowKind.CHILD) {
            WindowBranchHeartbeat(enabled, true, if (enabled) "主动对话已开启" else "主动对话未开启")
        } else {
            WindowBranchHeartbeat(true, false, "主动对话默认开启")
        }

    private fun missingSnapshot(windowId: String) = WindowBranchSnapshot(
        current = WindowBranchItem(windowId, "当前会话", WindowKind.TASK_ROOT),
        parent = null,
        children = emptyList(),
        heartbeat = WindowBranchHeartbeat(false, false, "状态暂不可用"),
        merge = WindowBranchMerge.UnavailableNoDelta
    )
}
