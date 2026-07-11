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
            is WorkspaceUiAction.SelectPage -> state.copy(currentPage = action.page)
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
            is WorkspaceUiAction.RecordVerticalScroll -> state.copy(
                verticalScrollOffsets = state.verticalScrollOffsets +
                    (action.page to action.offset.coerceAtLeast(0))
            )
        }
}

fun interface WorkspaceViewModelFactory {
    fun create(): WorkspaceViewModel
}

object DefaultWorkspaceViewModelFactory : WorkspaceViewModelFactory {
    override fun create(): WorkspaceViewModel = WorkspaceViewModel()
}
