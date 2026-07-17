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
        assertEquals(AppDestination.WeeklyDashboard, WorkspacePage.WeeklyDashboard.destination)
        assertEquals(AppDestination.Sessions, WorkspacePage.SessionHome.destination)
        assertEquals(WorkspacePage.WeeklyDashboard, AppDestination.WeeklyDashboard.workspacePage)
        assertEquals(WorkspacePage.GlobalGraph, AppDestination.GlobalGraph.workspacePage)
        assertEquals(WorkspacePage.Community, AppDestination.Community.workspacePage)
    }

    @Test
    fun homeVerticalPagesPutChallengeAboveSessionHome() {
        assertEquals(
            listOf(WorkspaceVerticalPage.Challenge, WorkspaceVerticalPage.SessionHome),
            WorkspaceVerticalPage.entries
        )
        assertEquals(WorkspaceVerticalPage.SessionHome, WorkspaceUiState().verticalPage)
    }

    @Test
    fun workspaceUsesAnExplicitStableHorizontalOrder() {
        assertEquals(
            listOf(
                WorkspacePage.WeeklyDashboard,
                WorkspacePage.SessionHome,
                WorkspacePage.GlobalGraph,
                WorkspacePage.Community
            ),
            WorkspacePage.FixedOrder
        )
    }

    @Test
    fun challengeAndGraphEdgeGesturesLockTheCorrectPager() {
        val viewModel = WorkspaceViewModel()

        viewModel.onAction(WorkspaceUiAction.SetChallengeDragActive(true))
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        viewModel.onAction(WorkspaceUiAction.SetChallengeDragActive(false))
        viewModel.onAction(WorkspaceUiAction.SetGraphEdgePagingActive(true))
        assertFalse(viewModel.uiState.value.graphCanvasInteractionEnabled)
    }

    @Test
    fun selectingChallengeKeepsHorizontalWorkspaceOnHome() {
        val viewModel = WorkspaceViewModel()

        viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(WorkspaceVerticalPage.Challenge))

        assertEquals(WorkspacePage.SessionHome, viewModel.uiState.value.currentPage)
        assertEquals(WorkspaceVerticalPage.Challenge, viewModel.uiState.value.verticalPage)
    }

    @Test
    fun destinationChangesSynchronizeBothWorkspaceAxes() {
        val challenge = requireNotNull(workspaceSelectionFor(AppDestination.Challenge))
        assertEquals(WorkspacePage.SessionHome, challenge.horizontal)
        assertEquals(WorkspaceVerticalPage.Challenge, challenge.vertical)

        val graph = requireNotNull(workspaceSelectionFor(AppDestination.GlobalGraph))
        assertEquals(WorkspacePage.GlobalGraph, graph.horizontal)
        assertEquals(null, graph.vertical)
    }

    @Test
    fun routeSelectionOverridesStaleWorkspaceStateBeforePagerComposition() {
        val stale = WorkspaceUiState(
            currentPage = WorkspacePage.SessionHome,
            verticalPage = WorkspaceVerticalPage.SessionHome
        )

        val community = stale.selectedForDestination(AppDestination.Community)
        val challenge = stale.selectedForDestination(AppDestination.Challenge)

        assertEquals(WorkspacePage.Community, community.currentPage)
        assertEquals(WorkspaceVerticalPage.SessionHome, community.verticalPage)
        assertEquals(WorkspacePage.SessionHome, challenge.currentPage)
        assertEquals(WorkspaceVerticalPage.Challenge, challenge.verticalPage)
    }

    @Test
    fun graphEdgePagingTargetsOnlyAdjacentPages() {
        assertEquals(
            WorkspacePage.SessionHome,
            graphEdgeTarget(
                edge = GraphEdge.Left,
                distancePx = 130f,
                velocityPx = 0f,
                distanceThresholdPx = 96f,
                velocityThresholdPx = 900f
            )
        )
        assertEquals(
            WorkspacePage.Community,
            graphEdgeTarget(
                edge = GraphEdge.Right,
                distancePx = 130f,
                velocityPx = 0f,
                distanceThresholdPx = 96f,
                velocityThresholdPx = 900f
            )
        )
        assertEquals(
            null,
            graphEdgeTarget(
                edge = GraphEdge.Left,
                distancePx = 12f,
                velocityPx = 0f,
                distanceThresholdPx = 96f,
                velocityThresholdPx = 900f
            )
        )
    }

    @Test
    fun graphEdgeDragBackInsideThresholdSettlesGraph() {
        val outward = graphEdgeDragUpdate(
            edge = GraphEdge.Left,
            currentDistancePx = 0f,
            dragDeltaPx = 130f,
            maxPagerDistancePx = 1_000f
        )
        assertEquals(130f, outward.distancePx)
        assertEquals(-130f, outward.pagerDeltaPx)

        val returned = graphEdgeDragUpdate(
            edge = GraphEdge.Left,
            currentDistancePx = outward.distancePx,
            dragDeltaPx = -100f,
            maxPagerDistancePx = 1_000f
        )
        assertEquals(30f, returned.distancePx)
        assertEquals(100f, returned.pagerDeltaPx)
        assertEquals(
            null,
            graphEdgeTarget(
                edge = GraphEdge.Left,
                distancePx = returned.distancePx,
                velocityPx = 0f,
                distanceThresholdPx = 96f,
                velocityThresholdPx = 900f
            )
        )
    }

    @Test
    fun graphEdgeOvershootNeverScrollsPastTheAdjacentPage() {
        val overshoot = graphEdgeDragUpdate(
            edge = GraphEdge.Left,
            currentDistancePx = 0f,
            dragDeltaPx = 900f,
            maxPagerDistancePx = 720f
        )
        assertEquals(900f, overshoot.distancePx)
        assertEquals(-720f, overshoot.pagerDeltaPx)

        val stillOvershooting = graphEdgeDragUpdate(
            edge = GraphEdge.Left,
            currentDistancePx = overshoot.distancePx,
            dragDeltaPx = -100f,
            maxPagerDistancePx = 720f
        )
        assertEquals(800f, stillOvershooting.distancePx)
        assertEquals(0f, stillOvershooting.pagerDeltaPx, 0f)

        val backInsidePage = graphEdgeDragUpdate(
            edge = GraphEdge.Left,
            currentDistancePx = stillOvershooting.distancePx,
            dragDeltaPx = -200f,
            maxPagerDistancePx = 720f
        )
        assertEquals(600f, backInsidePage.distancePx)
        assertEquals(120f, backInsidePage.pagerDeltaPx)
    }

    @Test
    fun graphEdgeThresholdsScaleWithScreenDensity() {
        val thresholds = graphEdgeThresholds(density = 3f)

        assertEquals(288f, thresholds.distancePx)
        assertEquals(2_700f, thresholds.velocityPxPerSecond)
    }

}
