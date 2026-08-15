package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.graph.GraphEdgeInput
import com.reversetutor.core.data.graph.GraphNodeInput
import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.feature.memory.ContextHubRoute
import com.reversetutor.feature.memory.KnowledgeGraphPanel
import com.reversetutor.feature.memory.KnowledgeGraphUiState
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5GraphDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun seedGraph() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
            val repository = DataModule.graphRepository(context)
            repository.saveNode(
                input = GraphNodeInput("node-a", "Alpha", GraphNodeKind.Concept),
                nowEpochMillis = 100L
            )
            repository.saveNode(
                input = GraphNodeInput("node-b", "Beta", GraphNodeKind.Requirement),
                nowEpochMillis = 110L
            )
            repository.saveEdge(
                input = GraphEdgeInput("edge-a-b", "node-a", "node-b", "supports"),
                nowEpochMillis = 120L
            )
        }
    }

    @After
    fun cleanUpLocalData() {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
        }
    }

    @Test
    fun contextHubRendersSeededNativeGraphCanvas() {
        composeRule.setContent {
            ReverseTutorTheme {
                ContextHubRoute(
                    memoryRepository = DataModule.memoryRepository(context),
                    graphRepository = DataModule.graphRepository(context),
                    sessionId = "session-graph",
                    sessionTitle = "函数训练",
                    onOpenChat = {},
                    onOpenSources = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("图谱节点：2")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithText("图谱")
            .filterToOne(
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
            )
            .performClick()
        composeRule.onNodeWithTag("knowledge-graph-canvas").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun graphEmptyAndErrorStatesExposeHonestRecoveryActions() {
        var openChatCount = 0
        var retryCount = 0
        val graphState = mutableStateOf(KnowledgeGraphUiState.from(emptyList(), emptyList()))
        composeRule.setContent {
            ReverseTutorTheme {
                KnowledgeGraphPanel(
                    state = graphState.value,
                    onSelectedNodeChange = {},
                    onCreateEvidence = { openChatCount += 1 },
                    onRetry = { retryCount += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("graph-status-empty").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-recovery-createevidence").performClick()
        composeRule.runOnIdle { assertEquals(1, openChatCount) }

        composeRule.runOnIdle {
            graphState.value = KnowledgeGraphUiState.error()
        }

        composeRule.onNodeWithTag("graph-status-error").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-recovery-retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

    @Test
    fun invalidAndLargeGraphsProvideAccessibleNodeListAndDetail() {
        val invalidState = KnowledgeGraphUiState.from(
            nodes = listOf(graphNode("invalid-node", "待审核节点")),
            edges = listOf(
                GraphEdge(
                    id = "invalid-edge",
                    spaceId = "space-1",
                    fromNodeId = "invalid-node",
                    toNodeId = "missing-node",
                    relation = "supports",
                    createdAtEpochMillis = 2L
                )
            )
        )
        val graphState = mutableStateOf(invalidState)
        composeRule.setContent {
            var selectedNodeId by remember { mutableStateOf<String?>(null) }
            ReverseTutorTheme {
                KnowledgeGraphPanel(
                    state = graphState.value.withSelection(selectedNodeId),
                    onSelectedNodeChange = { selectedNodeId = it }
                )
            }
        }

        composeRule.onNodeWithTag("graph-status-invalid").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-node-list").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-node-invalid-node").performClick()
        composeRule.onNodeWithTag("graph-node-detail").assertIsDisplayed()

        val largeState = KnowledgeGraphUiState.from(
            nodes = (1..61).map { graphNode("large-$it", "节点 $it") },
            edges = emptyList()
        )
        composeRule.runOnIdle {
            graphState.value = largeState
        }
        composeRule.onNodeWithTag("graph-status-large").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-node-list").assertIsDisplayed()
    }

    @Test
    fun nativeCanvasKeepsFitControlAndOnlyShowsAvailableEvidenceActions() {
        val state = KnowledgeGraphUiState.from(
            nodes = listOf(
                GraphNode(
                    id = "evidence-node",
                    spaceId = "space-1",
                    label = "带证据节点",
                    kind = GraphNodeKind.Concept,
                    createdAtEpochMillis = 1L,
                    sourceMemoryId = "memory-1"
                )
            ),
            edges = emptyList(),
            memoryItems = listOf(
                com.reversetutor.core.model.MemoryItem(
                    id = "memory-1",
                    spaceId = "space-1",
                    kind = com.reversetutor.core.model.MemoryItemKind.Note,
                    title = "证据",
                    body = "来自聊天",
                    createdAtEpochMillis = 1L,
                    sourceMessageId = "message-evidence"
                )
            )
        )
        var selectedNodeId: String? = null
        var chatTarget: String? = null
        composeRule.setContent {
            var selected by remember { mutableStateOf<String?>(null) }
            ReverseTutorTheme {
                KnowledgeGraphPanel(
                    state = state.withSelection(selected),
                    onSelectedNodeChange = {
                        selected = it
                        selectedNodeId = it
                    },
                    onOpenChatEvidence = { chatTarget = it.sourceMessageId }
                )
            }
        }

        composeRule.onNodeWithTag("knowledge-graph-canvas").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-fit").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-node-list").performClick()
        composeRule.onNodeWithTag("graph-node-evidence-node").performClick()
        composeRule.runOnIdle { assertEquals("evidence-node", selectedNodeId) }
        composeRule.onNodeWithTag("graph-evidence-chat-message-evidence").performClick()
        composeRule.runOnIdle { assertEquals("message-evidence", chatTarget) }
        assertTrue(
            composeRule.onAllNodesWithText("查看资料引用", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }

    private fun graphNode(id: String, label: String): GraphNode = GraphNode(
        id = id,
        spaceId = "space-1",
        label = label,
        kind = GraphNodeKind.Concept,
        createdAtEpochMillis = 1L
    )
}
