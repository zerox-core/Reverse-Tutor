package com.reversetutor.preview.shell

import androidx.compose.runtime.staticCompositionLocalOf

enum class WorkspacePage {
    WeeklyDashboard,
    SessionHome,
    GlobalGraph,
    Community;

    companion object {
        val FixedOrder: List<WorkspacePage> = listOf(
            WeeklyDashboard,
            SessionHome,
            GlobalGraph,
            Community
        )
    }
}

enum class WorkspaceVerticalPage {
    Challenge,
    SessionHome
}

val WorkspacePage.destination: AppDestination
    get() = when (this) {
        WorkspacePage.WeeklyDashboard -> AppDestination.WeeklyDashboard
        WorkspacePage.SessionHome -> AppDestination.Sessions
        WorkspacePage.GlobalGraph -> AppDestination.GlobalGraph
        WorkspacePage.Community -> AppDestination.Community
    }

val AppDestination.workspacePage: WorkspacePage?
    get() = when (this) {
        AppDestination.WeeklyDashboard -> WorkspacePage.WeeklyDashboard
        AppDestination.Sessions -> WorkspacePage.SessionHome
        AppDestination.Challenge -> WorkspacePage.SessionHome
        AppDestination.GlobalGraph -> WorkspacePage.GlobalGraph
        AppDestination.Community -> WorkspacePage.Community
        else -> null
    }

val AppDestination.workspaceVerticalPage: WorkspaceVerticalPage?
    get() = when (this) {
        AppDestination.Challenge -> WorkspaceVerticalPage.Challenge
        AppDestination.Sessions -> WorkspaceVerticalPage.SessionHome
        else -> null
    }

data class WorkspaceSelection(
    val horizontal: WorkspacePage,
    val vertical: WorkspaceVerticalPage?
)

fun workspaceSelectionFor(destination: AppDestination): WorkspaceSelection? =
    destination.workspacePage?.let { page ->
        WorkspaceSelection(
            horizontal = page,
            vertical = destination.workspaceVerticalPage
        )
    }

fun WorkspaceUiState.selectedForDestination(destination: AppDestination): WorkspaceUiState {
    val selection = workspaceSelectionFor(destination) ?: return this
    val selectedVerticalPage = selection.vertical ?: if (
        selection.horizontal == WorkspacePage.SessionHome
    ) {
        verticalPage
    } else {
        WorkspaceVerticalPage.SessionHome
    }
    return copy(
        currentPage = selection.horizontal,
        verticalPage = selectedVerticalPage
    )
}

data class WorkspaceInteractionLocks(
    val composerInputActive: Boolean = false,
    val widgetDragActive: Boolean = false,
    val fullscreenGraphActive: Boolean = false,
    val challengeDragActive: Boolean = false,
    val graphEdgePagingActive: Boolean = false
) {
    val horizontalPagingEnabled: Boolean
        get() = !composerInputActive &&
            !widgetDragActive &&
            !fullscreenGraphActive &&
            !challengeDragActive

    val graphCanvasInteractionEnabled: Boolean
        get() = !graphEdgePagingActive
}

data class WorkspaceUiState(
    val pages: List<WorkspacePage> = WorkspacePage.FixedOrder,
    val currentPage: WorkspacePage = WorkspacePage.SessionHome,
    val verticalPage: WorkspaceVerticalPage = WorkspaceVerticalPage.SessionHome,
    val challengeCanReturnHome: Boolean = true,
    val interactionLocks: WorkspaceInteractionLocks = WorkspaceInteractionLocks(),
    val verticalScrollOffsets: Map<WorkspacePage, Int> = emptyMap()
) {
    val horizontalPagingEnabled: Boolean
        get() = interactionLocks.horizontalPagingEnabled

    val graphCanvasInteractionEnabled: Boolean
        get() = interactionLocks.graphCanvasInteractionEnabled
}

sealed interface WorkspaceUiAction {
    data class SelectPage(val page: WorkspacePage) : WorkspaceUiAction
    data class SetComposerInputActive(val active: Boolean) : WorkspaceUiAction
    data class SetWidgetDragActive(val active: Boolean) : WorkspaceUiAction
    data class SetFullscreenGraphActive(val active: Boolean) : WorkspaceUiAction
    data class SelectVerticalPage(val page: WorkspaceVerticalPage) : WorkspaceUiAction
    data class SetChallengeExitBoundary(val canReturnHome: Boolean) : WorkspaceUiAction
    data class SetChallengeDragActive(val active: Boolean) : WorkspaceUiAction
    data class SetGraphEdgePagingActive(val active: Boolean) : WorkspaceUiAction
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

    fun onChallengeDragChanged(active: Boolean) {
        dispatch(WorkspaceUiAction.SetChallengeDragActive(active))
    }

    fun onGraphEdgePagingChanged(active: Boolean) {
        dispatch(WorkspaceUiAction.SetGraphEdgePagingActive(active))
    }
}

val LocalWorkspaceInteractionBindings =
    staticCompositionLocalOf<WorkspaceInteractionBindings?> { null }
