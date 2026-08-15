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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.graph.GraphEdgeInput
import com.reversetutor.core.data.graph.GraphNodeInput
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.feature.memory.ContextHubRoute
import com.reversetutor.preview.theme.ReverseTutorTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
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
}
