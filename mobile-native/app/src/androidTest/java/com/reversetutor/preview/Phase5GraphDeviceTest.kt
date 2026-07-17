package com.reversetutor.preview

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.graph.GraphEdgeInput
import com.reversetutor.core.data.graph.GraphNodeInput
import com.reversetutor.core.data.memory.AnchorInput
import com.reversetutor.core.model.GraphNodeKind
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase5GraphDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun nativeGraphCanvasShowsSeededNodesSelectionAndGlobalMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        resetAndSeedGraph(context)

        waitForText("Sessions")
        composeRule.onNodeWithText("New session").performClick()
        composeRule.onNodeWithText("Exam sprint").performClick()
        composeRule.onNodeWithText("Create").performClick()
        dismissOkIfPresent()
        waitForText("Exam sprint")

        composeRule.onAllNodesWithText("Open").onLast().performClick()
        waitForText("No messages yet")
        composeRule.onNodeWithText("Open context hub").performClick()
        waitForText("Context hub")
        composeRule.onNodeWithText("Graph").performScrollTo().performClick()

        waitForText("Session graph")
        composeRule.onNodeWithText("Nodes: 2").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Edges: 1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("knowledge-graph-canvas").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Reset view").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Alpha").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Beta").performScrollTo().performClick()
        waitForText("Node detail")
        composeRule.onNodeWithText("node-a supports node-b").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mark needs review").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open chat evidence").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Open source evidence").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Mark needs review").performScrollTo().performClick()
        waitForText("Status: Needs review")
        composeRule.onNodeWithText("Open chat evidence").performScrollTo().performClick()
        waitForText("Evidence target: message-beta")

        composeRule.onNodeWithText("Global graph").performScrollTo().performClick()
        waitForText("Cross-session graph view")
        composeRule.onNodeWithText(
            "Global graph: 2 nodes and 1 relations rendered with native Canvas.",
            substring = true
        )
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun resetAndSeedGraph(context: Context) {
        runBlocking {
            DataModule.localDataWipeRepository(context).wipeLocalData(System.currentTimeMillis())
            DataModule.memoryRepository(context).createAnchor(
                input = AnchorInput(
                    title = "Beta evidence",
                    body = "Imported source evidence for Beta.",
                    sourceMessageId = "message-beta",
                    sourceId = "source-beta"
                ),
                nowEpochMillis = 90L,
                anchorId = "anchor-1"
            )
            val repository = DataModule.graphRepository(context)
            repository.saveNode(
                input = GraphNodeInput(
                    id = "node-a",
                    label = "Alpha",
                    kind = GraphNodeKind.Concept,
                    sourceMemoryId = "memory-alpha"
                ),
                nowEpochMillis = 100L
            )
            repository.saveNode(
                input = GraphNodeInput(
                    id = "node-b",
                    label = "Beta",
                    kind = GraphNodeKind.Requirement,
                    sourceMemoryId = "memory-anchor-1"
                ),
                nowEpochMillis = 110L
            )
            repository.saveEdge(
                input = GraphEdgeInput(
                    id = "edge-a-b",
                    fromNodeId = "node-a",
                    toNodeId = "node-b",
                    relation = "supports",
                    sourceMemoryId = "memory-alpha"
                ),
                nowEpochMillis = 120L
            )
        }
        composeRule.activityRule.scenario.recreate()
    }

    private fun waitForText(text: String, timeoutMillis: Long = 5_000) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dismissOkIfPresent() {
        val okNodes = composeRule.onAllNodesWithText("OK", useUnmergedTree = true)
        if (okNodes.fetchSemanticsNodes().isNotEmpty()) {
            okNodes.onLast().performClick()
        }
    }
}
