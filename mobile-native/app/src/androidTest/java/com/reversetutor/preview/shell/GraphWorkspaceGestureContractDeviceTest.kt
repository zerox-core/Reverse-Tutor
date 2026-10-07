package com.reversetutor.preview.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.feature.memory.FormalGlobalKnowledgeGraphScreen
import com.reversetutor.feature.memory.GraphLayoutNode
import com.reversetutor.feature.memory.GraphPoint
import com.reversetutor.feature.memory.GraphRenderStatus
import com.reversetutor.feature.memory.GraphScope
import com.reversetutor.feature.memory.GraphViewportState
import com.reversetutor.feature.memory.KnowledgeGraphUiState
import com.reversetutor.preview.MainActivity
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraphWorkspaceGestureContractDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun pageModeSwipePagesWorkspaceAndOnlyTapEntersCanvas() {
        val probe = GraphWorkspaceProbe()
        setGraphWorkspace(probe)

        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("contract-community").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(WorkspacePage.Community, probe.currentPage)
            assertFalse(probe.canvasMode)
        }

        composeRule.onNodeWithTag("contract-community").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("knowledge-graph-canvas").assertIsDisplayed()

        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("graph-canvas-mode-exit").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(WorkspacePage.GlobalGraph, probe.currentPage)
            assertTrue(probe.canvasMode)
        }
    }

    @Test
    fun canvasPointerLoopHandlesTapLongDragZoomPanFitAndCenter() {
        val probe = GraphWorkspaceProbe()
        setGraphWorkspace(probe)
        enterCanvasMode()

        val node = contractGraphState.nodes.first { it.id == "node-a" }
        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput {
            click(Offset(width * node.x, height * node.y))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("node-a").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("node-a", probe.selectedNodeId) }

        pressSystemBack()
        composeRule.runOnIdle {
            assertNull(probe.selectedNodeId)
            assertTrue(probe.canvasMode)
        }

        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput {
            val start = Offset(width * node.x, height * node.y)
            down(start)
            advanceEventTime(650L)
            moveTo(start + Offset(54f, 42f), 120L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("node-a", probe.draggedNodeId)
            assertNotEquals(GraphPoint(node.x, node.y), probe.draggedNodePosition)
        }

        val scaleBeforePinch = probe.viewport.scale
        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput {
            pinch(
                Offset(center.x - 28f, center.y),
                Offset(center.x - 92f, center.y),
                Offset(center.x + 28f, center.y),
                Offset(center.x + 92f, center.y),
                420L
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(probe.viewport.scale > scaleBeforePinch) }

        val panBeforeDrag = probe.viewport.pan
        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput {
            swipeRight(
                startX = centerX - 45f,
                endX = centerX + 75f,
                durationMillis = 360
            )
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertNotEquals(panBeforeDrag, probe.viewport.pan) }

        val viewportBeforeFit = probe.viewport
        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput {
            doubleClick(Offset(width * 0.05f, height * 0.90f))
        }
        composeRule.waitForIdle()
        val fitViewport = probe.viewport
        assertViewportChanged(viewportBeforeFit, fitViewport)

        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput {
            pinch(
                Offset(center.x - 30f, center.y),
                Offset(center.x - 80f, center.y),
                Offset(center.x + 30f, center.y),
                Offset(center.x + 80f, center.y),
                360L
            )
        }
        composeRule.waitForIdle()
        assertViewportChanged(fitViewport, probe.viewport)

        composeRule.onNodeWithContentDescription("回到中心").performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertTrue(abs(probe.viewport.scale - fitViewport.scale) < 0.001f)
            assertTrue((probe.viewport.pan - fitViewport.pan).getDistance() < 0.5f)
            assertEquals(WorkspacePage.GlobalGraph, probe.currentPage)
        }
    }

    @Test
    fun systemBackClosesPanelThenCanvasThenReturnsHome() {
        val probe = GraphWorkspaceProbe()
        setGraphWorkspace(probe)
        enterCanvasMode()

        val node = contractGraphState.nodes.first { it.id == "node-b" }
        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput {
            click(Offset(width * node.x, height * node.y))
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals("node-b", probe.selectedNodeId)
            assertTrue(probe.canvasMode)
        }

        pressSystemBack()
        composeRule.runOnIdle {
            assertNull(probe.selectedNodeId)
            assertTrue(probe.canvasMode)
            assertEquals(WorkspacePage.GlobalGraph, probe.currentPage)
        }

        pressSystemBack()
        composeRule.runOnIdle {
            assertFalse(probe.canvasMode)
            assertEquals(WorkspacePage.GlobalGraph, probe.currentPage)
        }

        pressSystemBack()
        composeRule.onNodeWithTag("contract-home").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(WorkspacePage.SessionHome, probe.currentPage)
            assertFalse(probe.exitRequested)
        }
    }

    private fun setGraphWorkspace(probe: GraphWorkspaceProbe) {
        composeRule.setContent {
            ReverseTutorTheme {
                GraphWorkspaceContractHarness(probe)
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("knowledge-graph-canvas").assertIsDisplayed()
    }

    private fun enterCanvasMode() {
        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput { click() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("graph-canvas-mode-exit").assertIsDisplayed()
    }

    private fun pressSystemBack() {
        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun assertViewportChanged(
        before: GraphViewportState,
        after: GraphViewportState
    ) {
        assertTrue(
            abs(before.scale - after.scale) > 0.001f ||
                (before.pan - after.pan).getDistance() > 0.5f
        )
    }
}

private class GraphWorkspaceProbe {
    @Volatile
    var currentPage: WorkspacePage = WorkspacePage.GlobalGraph
    @Volatile
    var canvasMode: Boolean = false
    @Volatile
    var selectedNodeId: String? = null
    @Volatile
    var viewport: GraphViewportState = GraphViewportState()
    @Volatile
    var draggedNodeId: String? = null
    @Volatile
    var draggedNodePosition: GraphPoint? = null
    @Volatile
    var exitRequested: Boolean = false
}

@Composable
private fun GraphWorkspaceContractHarness(probe: GraphWorkspaceProbe) {
    val viewModel = remember {
        WorkspaceViewModel(WorkspaceUiState(currentPage = WorkspacePage.GlobalGraph))
    }
    val workspaceState by viewModel.uiState.collectAsState()
    val interactions = remember(viewModel) {
        WorkspaceInteractionBindings(viewModel::onAction)
    }
    var navigationState by remember {
        mutableStateOf(AppNavigationState().navigate(AppDestination.GlobalGraph))
    }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workspaceState.currentPage, workspaceState.interactionLocks.graphCanvasModeActive) {
        probe.currentPage = workspaceState.currentPage
        probe.canvasMode = workspaceState.interactionLocks.graphCanvasModeActive
    }
    LaunchedEffect(navigationState.current) {
        navigationState.current.workspacePage?.let { page ->
            if (page != workspaceState.currentPage) {
                viewModel.onAction(WorkspaceUiAction.SelectPage(page))
            }
        }
    }

    WorkspaceBackHandler(
        navigationState = navigationState,
        workspaceState = workspaceState,
        pageLocalActionSurfaceActive = selectedNodeId != null,
        onDismissPageLocalActionSurface = {
            selectedNodeId = null
            probe.selectedNodeId = null
        },
        onExitGraphCanvas = {
            viewModel.onAction(WorkspaceUiAction.SetGraphCanvasModeActive(false))
        },
        onNavigationStateChange = { navigationState = it },
        onExitRequested = { probe.exitRequested = true }
    )

    WorkspacePagerHost(
        state = workspaceState,
        interactions = interactions,
        onPageSelected = { page ->
            viewModel.onAction(WorkspaceUiAction.SelectPage(page))
            navigationState = navigationState.navigate(page.destination)
        },
        pageContent = { page ->
            when (page) {
                WorkspacePage.GlobalGraph -> FormalGlobalKnowledgeGraphScreen(
                    state = contractGraphState.withSelection(selectedNodeId),
                    onSelectedNodeChange = { nodeId ->
                        selectedNodeId = nodeId
                        probe.selectedNodeId = nodeId
                    },
                    canvasModeActive = workspaceState.interactionLocks.graphCanvasModeActive,
                    onCanvasModeChange = { active ->
                        viewModel.onAction(WorkspaceUiAction.SetGraphCanvasModeActive(active))
                    },
                    onViewportChanged = { probe.viewport = it },
                    onNodePositionChanged = { nodeId, position ->
                        probe.draggedNodeId = nodeId
                        probe.draggedNodePosition = position
                    }
                )
                WorkspacePage.SessionHome -> ContractPage("contract-home", Color.White)
                WorkspacePage.Community -> ContractPage("contract-community", Color(0xFFF3F5F7))
                WorkspacePage.WeeklyDashboard -> ContractPage("contract-weekly", Color(0xFFF4F7FA))
                WorkspacePage.Settings -> ContractPage("contract-settings", Color(0xFFF2F2F7))
            }
        }
    )
}

@Composable
private fun ContractPage(tag: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .testTag(tag)
    )
}

private val contractGraphState = KnowledgeGraphUiState(
    scope = GraphScope.Global,
    status = GraphRenderStatus.Ready,
    title = "全局图谱",
    summary = "手势合同图谱",
    nodes = listOf(
        contractNode("node-a", 0.24f, 0.27f),
        contractNode("node-b", 0.74f, 0.29f),
        contractNode("node-c", 0.27f, 0.73f),
        contractNode("node-d", 0.76f, 0.75f)
    ),
    visibleEdges = emptyList(),
    invalidEdgeCount = 0
)

private fun contractNode(
    id: String,
    x: Float,
    y: Float
) = GraphLayoutNode(
    id = id,
    label = id,
    kind = GraphNodeKind.Concept,
    status = GraphNodeStatus.Active,
    x = x,
    y = y,
    radius = 0.014f
)
