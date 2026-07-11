package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceViewModelTest {
    @Test
    fun workspaceStartsOnSessionHomeInFourPageOrder() {
        val viewModel = WorkspaceViewModel()

        assertEquals(
            listOf(
                WorkspacePage.WeeklyDashboard,
                WorkspacePage.SessionHome,
                WorkspacePage.GlobalGraph,
                WorkspacePage.Community
            ),
            viewModel.uiState.value.pages
        )
        assertEquals(WorkspacePage.SessionHome, viewModel.uiState.value.currentPage)
    }

    @Test
    fun interactionLocksDisableHorizontalPagingIndependently() {
        val viewModel = WorkspaceViewModel()

        viewModel.onAction(WorkspaceUiAction.SetComposerInputActive(true))
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        viewModel.onAction(WorkspaceUiAction.SetComposerInputActive(false))
        viewModel.onAction(WorkspaceUiAction.SetWidgetDragActive(true))
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        viewModel.onAction(WorkspaceUiAction.SetWidgetDragActive(false))
        viewModel.onAction(WorkspaceUiAction.SetFullscreenGraphActive(true))
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        viewModel.onAction(WorkspaceUiAction.SetFullscreenGraphActive(false))
        assertTrue(viewModel.uiState.value.horizontalPagingEnabled)
    }

    @Test
    fun verticalScrollStateIsPreservedPerPage() {
        val viewModel = WorkspaceViewModel()

        viewModel.onAction(
            WorkspaceUiAction.RecordVerticalScroll(
                page = WorkspacePage.WeeklyDashboard,
                offset = 120
            )
        )
        viewModel.onAction(
            WorkspaceUiAction.RecordVerticalScroll(
                page = WorkspacePage.SessionHome,
                offset = 360
            )
        )

        assertEquals(120, viewModel.uiState.value.verticalScrollOffsets[WorkspacePage.WeeklyDashboard])
        assertEquals(360, viewModel.uiState.value.verticalScrollOffsets[WorkspacePage.SessionHome])
    }

    @Test
    fun interactionBindingsDriveThePagerLocks() {
        val viewModel = WorkspaceViewModel()
        val bindings = WorkspaceInteractionBindings(viewModel::onAction)

        bindings.onComposerFocusChanged(true)
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        bindings.onComposerFocusChanged(false)
        bindings.onWidgetDragChanged(true)
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        bindings.onWidgetDragChanged(false)
        bindings.onFullscreenGraphInteractionChanged(true)
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        bindings.onFullscreenGraphInteractionChanged(false)
        assertTrue(viewModel.uiState.value.horizontalPagingEnabled)
    }

    @Test
    fun workspacePagesMapToExistingTopLevelDestinations() {
        assertEquals(AppDestination.ContextHub, WorkspacePage.WeeklyDashboard.destination)
        assertEquals(AppDestination.Sessions, WorkspacePage.SessionHome.destination)
        assertEquals(WorkspacePage.GlobalGraph, AppDestination.GlobalGraph.workspacePage)
        assertEquals(WorkspacePage.Community, AppDestination.Community.workspacePage)
    }
}
