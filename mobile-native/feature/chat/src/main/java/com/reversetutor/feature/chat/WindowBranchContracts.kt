package com.reversetutor.feature.chat

import com.reversetutor.core.domain.WindowKind

/** Stable feature boundary for the replaceable branch-management surface. */
interface WindowBranchPort {
    suspend fun load(windowId: String): WindowBranchSnapshot
    suspend fun createChild(request: CreateWindowBranchRequest): WindowBranchResult
    suspend fun setHeartbeat(windowId: String, enabled: Boolean): WindowBranchResult
    suspend fun deleteChild(windowId: String): WindowBranchResult
}

data class CreateWindowBranchRequest(
    val parentWindowId: String,
    val title: String
)

data class WindowBranchSnapshot(
    val current: WindowBranchItem,
    val parent: WindowBranchItem?,
    val children: List<WindowBranchItem>,
    val heartbeat: WindowBranchHeartbeat,
    val merge: WindowBranchMerge,
    val canDelete: Boolean = false,
    val deleteUnavailableReason: String? = null
)

data class WindowBranchItem(
    val id: String,
    val title: String,
    val kind: WindowKind
)

data class WindowBranchHeartbeat(
    val enabled: Boolean,
    val canToggle: Boolean,
    val stateLabel: String
)

sealed interface WindowBranchMerge {
    data object UnavailableNoDelta : WindowBranchMerge
}

sealed interface WindowBranchResult {
    data class Created(val child: WindowBranchItem) : WindowBranchResult
    data object HeartbeatUpdated : WindowBranchResult
    data class Deleted(val parentWindowId: String) : WindowBranchResult
    data object NotFound : WindowBranchResult
    data object InvalidParent : WindowBranchResult
    data object InvalidHeartbeatTransition : WindowBranchResult
    data object UnavailableConfiguration : WindowBranchResult
    data object Failed : WindowBranchResult
}

sealed interface WindowBranchAction {
    data object Create : WindowBranchAction
    data class OpenWindow(val windowId: String) : WindowBranchAction
    data class SetHeartbeat(val enabled: Boolean) : WindowBranchAction
    data object RequestDelete : WindowBranchAction
    data object ConfirmDelete : WindowBranchAction
    data object CancelDelete : WindowBranchAction
    data object Back : WindowBranchAction
}

data class WindowBranchUiState(
    val title: String,
    val current: WindowBranchItem,
    val parent: WindowBranchItem?,
    val children: List<WindowBranchItem>,
    val heartbeat: WindowBranchHeartbeat,
    val mergeEnabled: Boolean,
    val mergeReason: String?,
    val deleteEnabled: Boolean,
    val deleteReason: String?,
    val deleteConfirmationVisible: Boolean = false,
    val notice: String? = null
)

object WindowBranchPresenter {
    const val NoDeltaReason = "暂无可归并的结构化记忆"
    const val DeleteUnavailableReason = "删除能力暂不可用"

    fun present(snapshot: WindowBranchSnapshot): WindowBranchUiState = WindowBranchUiState(
        title = "分支管理",
        current = snapshot.current,
        parent = snapshot.parent,
        children = snapshot.children,
        heartbeat = snapshot.heartbeat,
        mergeEnabled = false,
        mergeReason = when (snapshot.merge) {
            WindowBranchMerge.UnavailableNoDelta -> NoDeltaReason
        },
        deleteEnabled = snapshot.current.kind == WindowKind.CHILD && snapshot.canDelete,
        deleteReason = if (snapshot.current.kind == WindowKind.CHILD && !snapshot.canDelete) {
            snapshot.deleteUnavailableReason ?: DeleteUnavailableReason
        } else {
            null
        }
    )
}
