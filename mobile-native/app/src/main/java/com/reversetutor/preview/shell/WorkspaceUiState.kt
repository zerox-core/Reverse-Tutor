package com.reversetutor.preview.shell

import androidx.compose.runtime.staticCompositionLocalOf

enum class WorkspacePage {
    WeeklyDashboard,
    SessionHome,
    GlobalGraph,
    Community
}

val WorkspacePage.destination: AppDestination
    get() = when (this) {
        WorkspacePage.WeeklyDashboard -> AppDestination.ContextHub
        WorkspacePage.SessionHome -> AppDestination.Sessions
        WorkspacePage.GlobalGraph -> AppDestination.GlobalGraph
        WorkspacePage.Community -> AppDestination.Community
    }

val AppDestination.workspacePage: WorkspacePage?
    get() = when (this) {
        AppDestination.ContextHub -> WorkspacePage.WeeklyDashboard
        AppDestination.Sessions -> WorkspacePage.SessionHome
        AppDestination.GlobalGraph -> WorkspacePage.GlobalGraph
        AppDestination.Community -> WorkspacePage.Community
        else -> null
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

class WorkspaceInteractionBindings(
    private val dispatch: (WorkspaceUiAction) -> Unit
) {
    fun onComposerFocusChanged(active: Boolean) {
        dispatch(WorkspaceUiAction.SetComposerInputActive(active))
    }

    fun onWidgetDragChanged(active: Boolean) {
        dispatch(WorkspaceUiAction.SetWidgetDragActive(active))
    }

    fun onFullscreenGraphInteractionChanged(active: Boolean) {
        dispatch(WorkspaceUiAction.SetFullscreenGraphActive(active))
    }
}

val LocalWorkspaceInteractionBindings =
    staticCompositionLocalOf<WorkspaceInteractionBindings?> { null }
