package com.reversetutor.preview.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSpatialNavigationDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homePullDownRevealsChallengeAndReverseSwipeReturnsHome() {
        composeRule.setContent { SpatialWorkspaceTestContent() }

        composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-home").performTouchInput {
            swipeDown(startY = top + 20f, endY = bottom - 20f, durationMillis = 450)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-challenge").assertIsDisplayed()

        composeRule.onNodeWithTag("workspace-challenge").performTouchInput {
            swipeUp(startY = bottom - 20f, endY = top + 20f, durationMillis = 450)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
    }

    @Test
    fun shortHomePullCancelsWithoutOpeningChallenge() {
        composeRule.setContent { SpatialWorkspaceTestContent() }

        composeRule.onNodeWithTag("workspace-home").performTouchInput {
            down(center)
            moveBy(Offset(0f, 36f), delayMillis = 120)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
        composeRule.onNodeWithTag("workspace-challenge").assertIsNotDisplayed()
    }

    @Test
    fun graphEdgesPageOnlyToAdjacentWorkspacePages() {
        composeRule.setContent { GraphWorkspaceTestContent() }
        val edgeDragDistancePx = edgeDragDistancePx()

        composeRule.onNodeWithTag("workspace-graph").assertIsDisplayed()
        composeRule.onNodeWithTag("graph-edge-left").performTouchInput {
            swipeRight(
                startX = left + 2f,
                endX = right + edgeDragDistancePx,
                durationMillis = 450
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
        waitForTag("workspace-state-SessionHome")
        waitForTag("workspace-graph-edge-lock-false")
        composeRule.onNodeWithTag("workspace-state-SessionHome").fetchSemanticsNode()
        composeRule.onNodeWithTag("workspace-graph-edge-lock-false").fetchSemanticsNode()

        composeRule.onNodeWithTag("workspace-home").performTouchInput {
            swipeLeft(startX = right - 20f, endX = left + 20f, durationMillis = 450)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-graph").assertIsDisplayed()
        waitForTag("workspace-state-GlobalGraph")
        waitForTag("graph-edge-right")
        composeRule.onNodeWithTag("workspace-state-GlobalGraph").fetchSemanticsNode()

        composeRule.onNodeWithTag("graph-edge-right").performTouchInput {
            swipeLeft(
                startX = right - 2f,
                endX = left - edgeDragDistancePx,
                durationMillis = 450
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-community").assertIsDisplayed()
    }

    @Test
    fun graphCenterDragKeepsGlobalGraphVisible() {
        composeRule.setContent { GraphWorkspaceTestContent() }

        composeRule.onNodeWithTag("workspace-graph").performTouchInput {
            swipeRight(startX = centerX - 70f, endX = centerX + 70f, durationMillis = 300)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workspace-graph").assertIsDisplayed()
    }

    @Test
    fun graphEdgePagingUsesSystemSafeCenteredGestureRails() {
        composeRule.setContent { GraphWorkspaceTestContent() }

        composeRule.onNodeWithTag("graph-edge-left")
            .assertWidthIsEqualTo(32.dp)
            .assertHeightIsEqualTo(180.dp)
        composeRule.onNodeWithTag("graph-edge-right")
            .assertWidthIsEqualTo(32.dp)
            .assertHeightIsEqualTo(180.dp)
    }

    @Test
    fun graphEdgeOvershootStillSnapsToTheCommunityAnchor() {
        composeRule.setContent { GraphWorkspaceTestContent() }
        val pageWidthPx = composeRule.onNodeWithTag("workspace-graph")
            .fetchSemanticsNode()
            .boundsInRoot
            .width

        composeRule.onNodeWithTag("graph-edge-right").performTouchInput {
            swipeLeft(
                startX = right - 2f,
                endX = left - pageWidthPx * 1.25f,
                durationMillis = 600
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("workspace-community")
            .assertLeftPositionInRootIsEqualTo(0.dp)
        assertIndicatorPosition(3f)
    }

    @Test
    fun leftGraphEdgeOvershootNeverRevealsWeeklyWhileFingerIsDown() {
        composeRule.setContent { GraphWorkspaceTestContent() }
        val pageWidthPx = composeRule.onNodeWithTag("workspace-graph")
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val rail = composeRule.onNodeWithTag("graph-edge-left")

        rail.performTouchInput {
            down(center)
            moveBy(Offset(pageWidthPx * 1.25f, 0f), delayMillis = 600)
        }
        try {
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("workspace-weekly").assertIsNotDisplayed()
            composeRule.onNodeWithTag("workspace-home")
                .assertLeftPositionInRootIsEqualTo(0.dp)
        } finally {
            rail.performTouchInput { up() }
        }

        waitForTag("workspace-state-SessionHome")
        composeRule.onNodeWithTag("workspace-home")
            .assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun workspacePagesAndIndicatorFollowTheConfirmedHorizontalOrder() {
        composeRule.setContent { OrderedWorkspaceTestContent() }
        val edgeDragDistancePx = edgeDragDistancePx()

        composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
        assertIndicatorPosition(1f)

        composeRule.onNodeWithTag("workspace-home").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-weekly").assertIsDisplayed()
        assertIndicatorPosition(0f)

        composeRule.onNodeWithTag("workspace-weekly").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-home").assertIsDisplayed()
        assertIndicatorPosition(1f)

        composeRule.onNodeWithTag("workspace-home").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-graph").assertIsDisplayed()
        assertIndicatorPosition(2f)

        composeRule.onNodeWithTag("graph-edge-right").performTouchInput {
            swipeLeft(
                startX = right - 2f,
                endX = left - edgeDragDistancePx,
                durationMillis = 450
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-community").assertIsDisplayed()
        assertIndicatorPosition(3f)

        composeRule.onNodeWithTag("workspace-community").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("workspace-settings").assertIsDisplayed()
        assertIndicatorPosition(4f)
    }

    @Test
    fun homeIndicatorNamesItsWeeklyActionForTalkBack() {
        composeRule.setContent { OrderedWorkspaceTestContent() }

        composeRule.onNodeWithTag("workspace-page-indicator")
            .assertContentDescriptionEquals("本周学习概览")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(
                SemanticsMatcher("weekly action label") { node ->
                    node.config[SemanticsActions.OnClick].label ==
                        "打开本周学习概览"
                }
            )
            .performClick()

        waitForTag("workspace-weekly")
        composeRule.onNodeWithTag("workspace-weekly").assertIsDisplayed()
    }

    private fun assertIndicatorPosition(expected: Float) {
        composeRule.onNodeWithTag("workspace-page-indicator").assert(
            SemanticsMatcher.expectValue(WorkspaceIndicatorProgressKey, expected)
        )
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun edgeDragDistancePx(): Float = with(composeRule.density) {
        120.dp.toPx()
    }
}

@Composable
private fun SpatialWorkspaceTestContent() {
    var selectedPage by remember { mutableStateOf(WorkspaceVerticalPage.SessionHome) }
    HomeChallengePagerHost(
        selectedPage,
        true,
        onDragActiveChanged = {},
        onPageSelected = { selectedPage = it },
        challengeContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF4F1FF))
                    .testTag("workspace-challenge")
            )
        },
        homeContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .testTag("workspace-home")
            )
        }
    )
}

@Composable
private fun GraphWorkspaceTestContent() {
    val viewModel = remember {
        WorkspaceViewModel(
            initialState = WorkspaceUiState(currentPage = WorkspacePage.GlobalGraph)
        )
    }
    val state by viewModel.uiState.collectAsState()
    val interactions = remember(viewModel) {
        WorkspaceInteractionBindings(viewModel::onAction)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("workspace-state-${state.currentPage}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(
                    "workspace-graph-edge-lock-${state.interactionLocks.graphEdgePagingActive}"
                )
        ) {
            WorkspacePagerHost(
                state = state,
                interactions = interactions,
                onPageSelected = { viewModel.onAction(WorkspaceUiAction.SelectPage(it)) },
                onVerticalPageSelected = {
                    viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(it))
                },
                challengeContent = { WorkspaceTestPage("workspace-challenge", Color(0xFFF4F1FF)) },
                pageContent = { page ->
                    when (page) {
                        WorkspacePage.WeeklyDashboard -> WorkspaceTestPage("workspace-weekly", Color(0xFFF4F7FA))
                        WorkspacePage.SessionHome -> WorkspaceTestPage("workspace-home", Color.White)
                        WorkspacePage.GlobalGraph -> WorkspaceTestPage("workspace-graph", Color(0xFFF0F5F4))
                        WorkspacePage.Community -> WorkspaceTestPage("workspace-community", Color(0xFFF8F8F8))
                        WorkspacePage.Settings -> WorkspaceTestPage("workspace-settings", Color(0xFFF2F2F7))
                    }
                }
            )
        }
    }
}

@Composable
private fun OrderedWorkspaceTestContent() {
    val viewModel = remember { WorkspaceViewModel() }
    val state by viewModel.uiState.collectAsState()
    val interactions = remember(viewModel) {
        WorkspaceInteractionBindings(viewModel::onAction)
    }

    WorkspacePagerHost(
        state = state,
        interactions = interactions,
        onPageSelected = { viewModel.onAction(WorkspaceUiAction.SelectPage(it)) },
        onVerticalPageSelected = {
            viewModel.onAction(WorkspaceUiAction.SelectVerticalPage(it))
        },
        challengeContent = { WorkspaceTestPage("workspace-challenge", Color(0xFFF4F1FF)) },
        pageContent = { page ->
            when (page) {
                WorkspacePage.WeeklyDashboard -> WorkspaceTestPage("workspace-weekly", Color(0xFFF4F7FA))
                WorkspacePage.SessionHome -> WorkspaceTestPage("workspace-home", Color.White)
                WorkspacePage.GlobalGraph -> WorkspaceTestPage("workspace-graph", Color(0xFFF0F5F4))
                WorkspacePage.Community -> WorkspaceTestPage("workspace-community", Color(0xFFF8F8F8))
                WorkspacePage.Settings -> WorkspaceTestPage("workspace-settings", Color(0xFFF2F2F7))
            }
        }
    )
}

@Composable
private fun WorkspaceTestPage(
    tag: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .testTag(tag)
    )
}
