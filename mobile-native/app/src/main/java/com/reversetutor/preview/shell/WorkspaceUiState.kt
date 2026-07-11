package com.reversetutor.preview.shell

enum class WorkspacePage {
    WeeklyDashboard,
    SessionHome,
    GlobalGraph,
    Community
}

data class WorkspaceInteractionLocks(
    val composerInputActive: Boolean = false,
    val widgetDragActive: Boolean = false,
    val fullscreenGraphActive: Boolean = false
) {
    val horizontalPagingEnabled: Boolean
        get() = !composerInputActive && !widgetDragActive && !fullscreenGraphActive
}

data class WorkspaceUiState(
    val pages: List<WorkspacePage> = WorkspacePage.entries,
    val currentPage: WorkspacePage = WorkspacePage.SessionHome,
    val interactionLocks: WorkspaceInteractionLocks = WorkspaceInteractionLocks(),
    val verticalScrollOffsets: Map<WorkspacePage, Int> = emptyMap()
) {
    val horizontalPagingEnabled: Boolean
        get() = interactionLocks.horizontalPagingEnabled
}

sealed interface WorkspaceUiAction {
    data class SelectPage(val page: WorkspacePage) : WorkspaceUiAction
    data class SetComposerInputActive(val active: Boolean) : WorkspaceUiAction
    data class SetWidgetDragActive(val active: Boolean) : WorkspaceUiAction
    data class SetFullscreenGraphActive(val active: Boolean) : WorkspaceUiAction
    data class RecordVerticalScroll(
        val page: WorkspacePage,
        val offset: Int
    ) : WorkspaceUiAction
}
