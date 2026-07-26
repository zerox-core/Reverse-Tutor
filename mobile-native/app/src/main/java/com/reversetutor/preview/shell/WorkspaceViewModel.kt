package com.reversetutor.preview.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WorkspaceViewModel(
    initialState: WorkspaceUiState = WorkspaceUiState()
) {
    private val mutableUiState = MutableStateFlow(initialState)
    val uiState: StateFlow<WorkspaceUiState> = mutableUiState.asStateFlow()

    fun onAction(action: WorkspaceUiAction) {
        mutableUiState.value = reduce(mutableUiState.value, action)
    }

    private fun reduce(
        state: WorkspaceUiState,
        action: WorkspaceUiAction
    ): WorkspaceUiState =
        when (action) {
            is WorkspaceUiAction.SelectPage -> state.copy(
                currentPage = action.page,
                verticalPage = if (action.page == WorkspacePage.SessionHome) {
                    state.verticalPage
                } else {
                    WorkspaceVerticalPage.SessionHome
                },
                interactionLocks = if (action.page == WorkspacePage.GlobalGraph) {
                    state.interactionLocks
                } else {
                    state.interactionLocks.copy(graphCanvasModeActive = false)
                }
            )
            is WorkspaceUiAction.SetComposerInputActive -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    composerInputActive = action.active
                )
            )
            is WorkspaceUiAction.SetWidgetDragActive -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    widgetDragActive = action.active
                )
            )
            is WorkspaceUiAction.SetFullscreenGraphActive -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    fullscreenGraphActive = action.active
                )
            )
            is WorkspaceUiAction.SetGraphCanvasModeActive -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    graphCanvasModeActive = action.active
                )
            )
            is WorkspaceUiAction.SetInnerHorizontalControlActive -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    innerHorizontalControlActive = action.active
                )
            )
            is WorkspaceUiAction.SelectVerticalPage -> state.copy(
                currentPage = WorkspacePage.SessionHome,
                verticalPage = action.page,
                challengeCanReturnHome = if (action.page == WorkspaceVerticalPage.Challenge) {
                    false
                } else {
                    state.challengeCanReturnHome
                }
            )
            is WorkspaceUiAction.SetChallengeExitBoundary -> state.copy(
                challengeCanReturnHome = action.canReturnHome
            )
            is WorkspaceUiAction.SetChallengeDragActive -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    challengeDragActive = action.active
                )
            )
            is WorkspaceUiAction.SetGraphEdgePagingActive -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    graphEdgePagingActive = action.active
                )
            )
            is WorkspaceUiAction.SetSurfaceState -> state.copy(
                surfaceStates = state.surfaceStates + (action.page to action.state)
            )
            is WorkspaceUiAction.RetrySurface -> state.copy(
                surfaceStates = state.surfaceStates +
                    (action.page to WorkspaceSurfaceState.Loading),
                surfaceRetryGenerations = state.surfaceRetryGenerations +
                    (action.page to ((state.surfaceRetryGenerations[action.page] ?: 0L) + 1L))
            )
            is WorkspaceUiAction.RecordVerticalScroll -> state.copy(
                verticalScrollOffsets = state.verticalScrollOffsets +
                    (action.page to action.offset.coerceAtLeast(0))
            )
            WorkspaceUiAction.ReleaseHeavyResources -> state.copy(
                interactionLocks = state.interactionLocks.copy(
                    fullscreenGraphActive = false,
                    graphCanvasModeActive = false,
                    graphEdgePagingActive = false
                ),
                resourceReleaseGeneration = state.resourceReleaseGeneration + 1
            )
        }
}

fun interface WorkspaceViewModelFactory {
    fun create(): WorkspaceViewModel
}

object DefaultWorkspaceViewModelFactory : WorkspaceViewModelFactory {
    override fun create(): WorkspaceViewModel = WorkspaceViewModel()
}
