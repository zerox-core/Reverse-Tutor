package com.reversetutor.preview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.feature.memory.FormalGlobalKnowledgeGraphScreen
import com.reversetutor.feature.memory.FormalSessionWorldTreeScreen
import com.reversetutor.feature.memory.GraphScope
import com.reversetutor.feature.memory.KnowledgeGraphUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraphInteractionContractDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun globalGraphRequiresCanvasTapBeforeCanvasMode() {
        var canvasMode by mutableStateOf(false)
        composeRule.setContent {
            FormalGlobalKnowledgeGraphScreen(
                state = KnowledgeGraphUiState.from(
                    nodes = listOf(node("topic", GraphNodeStatus.Active)),
                    edges = emptyList(),
                    scope = GraphScope.Global
                ),
                onSelectedNodeChange = {},
                canvasModeActive = canvasMode,
                onCanvasModeChange = { canvasMode = it }
            )
        }

        composeRule.onNodeWithTag("graph-page-mode-indicator").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-canvas-mode-exit").assertDoesNotExist()

        composeRule.onNodeWithTag("knowledge-graph-canvas").performTouchInput { click() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("graph-page-mode-indicator").assertDoesNotExist()
        composeRule.onNodeWithTag("graph-canvas-mode-exit")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(44.dp)
        composeRule.onNodeWithContentDescription("回到中心")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun emptyGraphUsesExactCopyOnARealCanvas() {
        composeRule.setContent {
            FormalGlobalKnowledgeGraphScreen(
                state = KnowledgeGraphUiState.from(
                    nodes = emptyList(),
                    edges = emptyList(),
                    scope = GraphScope.Global
                ),
                onSelectedNodeChange = {}
            )
        }

        composeRule.onNodeWithTag("knowledge-graph-canvas").assertIsDisplayed()
        composeRule.onNodeWithText("当前会话信息过少，再多聊会天吧").assertIsDisplayed()
    }

    @Test
    fun sessionGraphAloneExposesProgressAndFullRouteSegments() {
        var showLockedNodes by mutableStateOf(false)
        composeRule.setContent {
            FormalSessionWorldTreeScreen(
                title = "会话图谱",
                state = KnowledgeGraphUiState.from(
                    nodes = listOf(
                        node("unlocked", GraphNodeStatus.Active),
                        node("locked", GraphNodeStatus.Hidden)
                    ),
                    edges = emptyList(),
                    scope = GraphScope.Session
                ),
                showLockedNodes = showLockedNodes,
                onShowLockedNodesChange = { showLockedNodes = it },
                onBack = {},
                onSelectedNodeChange = {}
            )
        }

        composeRule.onNodeWithText("当前进度").assertIsDisplayed()
        composeRule.onNodeWithText("全部路线").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("graph-layer-FullRoute").assertIsDisplayed()
    }

    private fun node(
        id: String,
        status: GraphNodeStatus
    ) = GraphNode(
        id = id,
        spaceId = "space-1",
        label = id,
        kind = GraphNodeKind.Concept,
        createdAtEpochMillis = 1L,
        status = status
    )
}
