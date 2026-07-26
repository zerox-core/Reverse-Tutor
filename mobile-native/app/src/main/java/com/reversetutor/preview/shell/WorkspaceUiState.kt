package com.reversetutor.preview.shell

import androidx.compose.runtime.staticCompositionLocalOf

enum class WorkspacePage {
    WeeklyDashboard,
    SessionHome,
    GlobalGraph,
    Community,
    Settings;

    companion object {
        val FixedOrder: List<WorkspacePage> = listOf(
            WeeklyDashboard,
            SessionHome,
            GlobalGraph,
            Community,
            Settings
        )
    }
}

enum class WorkspaceVerticalPage {
    Challenge,
    SessionHome
}

/** Keeps the five logical workspace pages continuously reachable in both directions. */
internal object WorkspaceLoopingPager {
    private const val AnchorCycle = 10_000

    fun initialIndex(pages: List<WorkspacePage>, page: WorkspacePage): Int =
        AnchorCycle * pages.size + pages.indexOf(page).coerceAtLeast(0)

    fun pageAt(pages: List<WorkspacePage>, virtualIndex: Int): WorkspacePage =
        pages[Math.floorMod(virtualIndex, pages.size)]

    fun nearestIndexFor(
        pages: List<WorkspacePage>,
        page: WorkspacePage,
        fromIndex: Int
    ): Int {
        val targetOffset = pages.indexOf(page).coerceAtLeast(0)
        val currentCycle = Math.floorDiv(fromIndex, pages.size)
        return listOf(currentCycle - 1, currentCycle, currentCycle + 1)
            .map { cycle -> cycle * pages.size + targetOffset }
            .minBy { kotlin.math.abs(it - fromIndex) }
    }

    fun virtualPageCount(pages: List<WorkspacePage>): Int = pages.size * 20_001
}

internal enum class WorkspaceGestureOwner {
    WorkspacePager,
    InnerHorizontalControl,
    InnerVerticalContent,
    GraphCanvas,
    Locked
}

internal fun workspaceGestureOwner(
    horizontalDeltaPx: Float,
    verticalDeltaPx: Float,
    innerHorizontalControlActive: Boolean,
    graphCanvasModeActive: Boolean,
    workspacePagingEnabled: Boolean
): WorkspaceGestureOwner = when {
    !workspacePagingEnabled -> WorkspaceGestureOwner.Locked
    graphCanvasModeActive -> WorkspaceGestureOwner.GraphCanvas
    innerHorizontalControlActive -> WorkspaceGestureOwner.InnerHorizontalControl
    kotlin.math.abs(verticalDeltaPx) > kotlin.math.abs(horizontalDeltaPx) ->
        WorkspaceGestureOwner.InnerVerticalContent
    else -> WorkspaceGestureOwner.WorkspacePager
}

internal fun shouldExitGraphCanvasBeforeNavigation(
    navigationState: AppNavigationState,
    workspaceState: WorkspaceUiState
): Boolean = navigationState.modal == null &&
    !navigationState.drawerOpen &&
    workspaceState.interactionLocks.graphCanvasModeActive

val WorkspacePage.destination: AppDestination
    get() = when (this) {
        WorkspacePage.WeeklyDashboard -> AppDestination.WeeklyDashboard
        WorkspacePage.SessionHome -> AppDestination.Sessions
        WorkspacePage.GlobalGraph -> AppDestination.GlobalGraph
        WorkspacePage.Community -> AppDestination.Community
        WorkspacePage.Settings -> AppDestination.Settings
    }

val AppDestination.workspacePage: WorkspacePage?
    get() = when (this) {
        AppDestination.WeeklyDashboard -> WorkspacePage.WeeklyDashboard
        AppDestination.Sessions -> WorkspacePage.SessionHome
        AppDestination.Challenge -> WorkspacePage.SessionHome
        AppDestination.GlobalGraph -> WorkspacePage.GlobalGraph
        AppDestination.Community -> WorkspacePage.Community
        AppDestination.Settings -> WorkspacePage.Settings
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
    val graphCanvasModeActive: Boolean = false,
    val innerHorizontalControlActive: Boolean = false,
    val challengeDragActive: Boolean = false,
    val graphEdgePagingActive: Boolean = false
) {
    val horizontalPagingEnabled: Boolean
        get() = !composerInputActive &&
            !widgetDragActive &&
            !fullscreenGraphActive &&
            !graphCanvasModeActive &&
            !innerHorizontalControlActive &&
            !challengeDragActive

    val graphCanvasInteractionEnabled: Boolean
        get() = !graphEdgePagingActive
}

data class WorkspaceUiState(
    val pages: List<WorkspacePage> = WorkspacePage.FixedOrder,
    val currentPage: WorkspacePage = WorkspacePage.SessionHome,
    val verticalPage: WorkspaceVerticalPage = WorkspaceVerticalPage.SessionHome,
    val challengeCanReturnHome: Boolean = false,
    val interactionLocks: WorkspaceInteractionLocks = WorkspaceInteractionLocks(),
    val verticalScrollOffsets: Map<WorkspacePage, Int> = emptyMap(),
    val resourceReleaseGeneration: Long = 0L
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
    data class SetGraphCanvasModeActive(val active: Boolean) : WorkspaceUiAction
    data class SetInnerHorizontalControlActive(val active: Boolean) : WorkspaceUiAction
    data class SelectVerticalPage(val page: WorkspaceVerticalPage) : WorkspaceUiAction
    data class SetChallengeExitBoundary(val canReturnHome: Boolean) : WorkspaceUiAction
    data class SetChallengeDragActive(val active: Boolean) : WorkspaceUiAction
    data class SetGraphEdgePagingActive(val active: Boolean) : WorkspaceUiAction
    data class RecordVerticalScroll(
        val page: WorkspacePage,
        val offset: Int
    ) : WorkspaceUiAction
    data object ReleaseHeavyResources : WorkspaceUiAction
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

    fun onGraphCanvasModeChanged(active: Boolean) {
        dispatch(WorkspaceUiAction.SetGraphCanvasModeActive(active))
    }

    fun onInnerHorizontalControlChanged(active: Boolean) {
        dispatch(WorkspaceUiAction.SetInnerHorizontalControlActive(active))
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
