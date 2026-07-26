package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceViewModelTest {
    @Test
    fun workspaceStartsOnSessionHomeInFivePageOrder() {
        val viewModel = WorkspaceViewModel()

        assertEquals(
            listOf(
                WorkspacePage.WeeklyDashboard,
                WorkspacePage.SessionHome,
                WorkspacePage.GlobalGraph,
                WorkspacePage.Community,
                WorkspacePage.Settings
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
        viewModel.onAction(WorkspaceUiAction.SetInnerHorizontalControlActive(true))
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)

        viewModel.onAction(WorkspaceUiAction.SetInnerHorizontalControlActive(false))
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
        assertEquals(WorkspacePage.Settings, AppDestination.Settings.workspacePage)
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
                WorkspacePage.Community,
                WorkspacePage.Settings
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
    fun graphCanvasModeAloneLocksWorkspacePaging() {
        val viewModel = WorkspaceViewModel()
        viewModel.onAction(WorkspaceUiAction.SelectPage(WorkspacePage.GlobalGraph))

        assertTrue(viewModel.uiState.value.horizontalPagingEnabled)
        viewModel.onAction(WorkspaceUiAction.SetGraphCanvasModeActive(true))
        assertFalse(viewModel.uiState.value.horizontalPagingEnabled)
        viewModel.onAction(WorkspaceUiAction.SetGraphCanvasModeActive(false))
        assertTrue(viewModel.uiState.value.horizontalPagingEnabled)
    }

    @Test
    fun selectingChallengeKeepsHorizontalWorkspaceOnHome() {
        val viewModel = WorkspaceViewModel()

        viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(WorkspaceVerticalPage.Challenge))

        assertEquals(WorkspacePage.SessionHome, viewModel.uiState.value.currentPage)
        assertEquals(WorkspaceVerticalPage.Challenge, viewModel.uiState.value.verticalPage)
        assertFalse(viewModel.uiState.value.challengeCanReturnHome)
    }

    @Test
    fun challengeReturnUnlocksOnlyAfterContentReachesBottom() {
        val viewModel = WorkspaceViewModel()

        assertFalse(viewModel.uiState.value.challengeCanReturnHome)
        viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(WorkspaceVerticalPage.Challenge))
        assertFalse(viewModel.uiState.value.challengeCanReturnHome)

        viewModel.onAction(WorkspaceUiAction.SetChallengeExitBoundary(true))
        assertTrue(viewModel.uiState.value.challengeCanReturnHome)

        viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(WorkspaceVerticalPage.SessionHome))
        viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(WorkspaceVerticalPage.Challenge))
        assertFalse(viewModel.uiState.value.challengeCanReturnHome)
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

    @Test
    fun transientIndicatorsUseRealDeviceTuning() {
        assertEquals(700L, TransientIndicatorSpec.HoldMillis)
        assertEquals(240, TransientIndicatorSpec.FadeMillis)
    }

    @Test
    fun loopingPagerKeepsTheFixedOrderInBothDirectionsWithoutAnEdge() {
        val pages = WorkspacePage.FixedOrder
        val homeIndex = WorkspaceLoopingPager.initialIndex(pages, WorkspacePage.SessionHome)

        assertEquals(WorkspacePage.WeeklyDashboard, WorkspaceLoopingPager.pageAt(pages, homeIndex - 1))
        assertEquals(WorkspacePage.GlobalGraph, WorkspaceLoopingPager.pageAt(pages, homeIndex + 1))
        assertEquals(WorkspacePage.Settings, WorkspaceLoopingPager.pageAt(pages, homeIndex - 2))
        assertEquals(WorkspacePage.WeeklyDashboard, WorkspaceLoopingPager.pageAt(pages, homeIndex + 4))
        assertTrue(WorkspaceLoopingPager.virtualPageCount(pages) > pages.size)
    }

    @Test
    fun programmaticWorkspaceSelectionUsesTheClosestLoopedCopy() {
        val pages = WorkspacePage.FixedOrder
        val index = WorkspaceLoopingPager.initialIndex(pages, WorkspacePage.SessionHome)

        assertEquals(index - 2, WorkspaceLoopingPager.nearestIndexFor(pages, WorkspacePage.Settings, index))
        assertEquals(index + 1, WorkspaceLoopingPager.nearestIndexFor(pages, WorkspacePage.GlobalGraph, index))
    }

    @Test
    fun dominantAxisAndInnerControlsKeepWorkspacePagerFromStealingGestures() {
        assertEquals(
            WorkspaceGestureOwner.InnerVerticalContent,
            workspaceGestureOwner(18f, 72f, false, false, true)
        )
        assertEquals(
            WorkspaceGestureOwner.InnerHorizontalControl,
            workspaceGestureOwner(72f, 18f, true, false, true)
        )
        assertEquals(
            WorkspaceGestureOwner.GraphCanvas,
            workspaceGestureOwner(72f, 18f, false, true, true)
        )
        assertEquals(
            WorkspaceGestureOwner.Locked,
            workspaceGestureOwner(72f, 18f, false, false, false)
        )
    }

    @Test
    fun backgroundReleasePreservesRouteButClearsHeavyGraphInteractionState() {
        val viewModel = WorkspaceViewModel(
            WorkspaceUiState(currentPage = WorkspacePage.GlobalGraph)
        )
        viewModel.onAction(WorkspaceUiAction.SetGraphCanvasModeActive(true))
        viewModel.onAction(WorkspaceUiAction.SetFullscreenGraphActive(true))
        viewModel.onAction(WorkspaceUiAction.ReleaseHeavyResources)

        assertEquals(WorkspacePage.GlobalGraph, viewModel.uiState.value.currentPage)
        assertFalse(viewModel.uiState.value.interactionLocks.graphCanvasModeActive)
        assertFalse(viewModel.uiState.value.interactionLocks.fullscreenGraphActive)
        assertEquals(1L, viewModel.uiState.value.resourceReleaseGeneration)
    }

    @Test
    fun graphCanvasBackYieldsToTopSurfacesBeforeLeavingCanvasMode() {
        val canvasState = WorkspaceUiState(
            interactionLocks = WorkspaceInteractionLocks(graphCanvasModeActive = true)
        )

        assertTrue(shouldExitGraphCanvasBeforeNavigation(AppNavigationState(), canvasState))
        assertFalse(
            shouldExitGraphCanvasBeforeNavigation(
                AppNavigationState(modal = AppModal.Status),
                canvasState
            )
        )
        assertFalse(
            shouldExitGraphCanvasBeforeNavigation(
                AppNavigationState(drawerOpen = true),
                canvasState
            )
        )
        assertFalse(
            shouldExitGraphCanvasBeforeNavigation(
                AppNavigationState(),
                canvasState,
                pageLocalActionSurfaceActive = true
            )
        )
        assertEquals(
            WorkspaceBackTarget.AppNavigationSurface,
            workspaceBackTarget(
                AppNavigationState(modal = AppModal.Status),
                canvasState,
                pageLocalActionSurfaceActive = true
            )
        )
        assertEquals(
            WorkspaceBackTarget.PageLocalActionSurface,
            workspaceBackTarget(
                AppNavigationState(),
                canvasState,
                pageLocalActionSurfaceActive = true
            )
        )
        assertEquals(
            WorkspaceBackTarget.GraphCanvas,
            workspaceBackTarget(
                AppNavigationState(),
                canvasState,
                pageLocalActionSurfaceActive = false
            )
        )
    }

    @Test
    fun workspaceSurfaceSlotsArePageLocalAndDoNotBlockSettings() {
        val viewModel = WorkspaceViewModel()
        viewModel.onAction(
            WorkspaceUiAction.SetSurfaceState(
                WorkspacePage.GlobalGraph,
                WorkspaceSurfaceState.Offline("图谱连接不可用")
            )
        )

        assertTrue(
            viewModel.uiState.value.surfaceStateFor(WorkspacePage.GlobalGraph) is
                WorkspaceSurfaceState.Offline
        )
        assertEquals(
            WorkspaceSurfaceState.Content,
            viewModel.uiState.value.surfaceStateFor(WorkspacePage.Settings)
        )

        viewModel.onAction(WorkspaceUiAction.RetrySurface(WorkspacePage.GlobalGraph))

        assertEquals(
            WorkspaceSurfaceState.Loading,
            viewModel.uiState.value.surfaceStateFor(WorkspacePage.GlobalGraph)
        )
        assertEquals(1L, viewModel.uiState.value.surfaceRetryGenerations[WorkspacePage.GlobalGraph])
        assertEquals(
            WorkspaceSurfaceState.Content,
            viewModel.uiState.value.surfaceStateFor(WorkspacePage.Settings)
        )
    }

    @Test
    fun reusableSurfacePresentationsCoverEveryNonContentStateAndRetrySlot() {
        val states = listOf(
            WorkspaceSurfaceState.Loading,
            WorkspaceSurfaceState.Empty(),
            WorkspaceSurfaceState.Offline(),
            WorkspaceSurfaceState.Error(),
            WorkspaceSurfaceState.PermissionDenied()
        )

        assertEquals(
            listOf("正在加载", "暂无内容", "网络不可用", "出现错误", "权限受限"),
            states.map { workspaceSurfacePresentation(it)?.title }
        )
        assertEquals(
            listOf(null, "刷新", "重试", "重试", "重新检查权限"),
            states.map { workspaceSurfacePresentation(it)?.actionLabel }
        )
    }

}
