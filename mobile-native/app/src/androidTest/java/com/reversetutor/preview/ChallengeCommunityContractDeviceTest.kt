package com.reversetutor.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.preview.shell.ChallengeVerticalEvent
import com.reversetutor.preview.shell.ChallengeVerticalState
import com.reversetutor.preview.shell.ChallengeRoute
import com.reversetutor.preview.shell.CommunityRoute
import com.reversetutor.preview.shell.HomeChallengePagerHost
import com.reversetutor.preview.shell.WorkspaceVerticalPage
import com.reversetutor.preview.shell.reduceChallengeVerticalState
import com.reversetutor.feature.chat.FormalHomeScreen
import com.reversetutor.feature.chat.FormalHomeSessionUi
import com.reversetutor.feature.chat.FormalHomeUiState
import com.reversetutor.feature.chat.FormalPublicContentUi
import com.reversetutor.feature.chat.ChallengeSessionProvenance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChallengeCommunityContractDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun realHomeHandleUsesPagerThresholdInsteadOfIndependent72DpTrigger() {
        composeRule.setContent { RealHomeEntryContent() }
        val handle = composeRule.onNodeWithTag("formal-home-challenge-handle")
        val shortDistance = with(composeRule.density) { 72.dp.toPx() }

        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, shortDistance), delayMillis = 500)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("formal-home-screen").assertIsDisplayed()
        composeRule.onNodeWithTag("formal-challenge-716-237").assertIsNotDisplayed()

        val pageHeight = composeRule.onNodeWithTag("formal-home-screen")
            .fetchSemanticsNode().boundsInRoot.height
        handle.performTouchInput {
            down(center)
            moveBy(Offset(0f, pageHeight * 0.72f), delayMillis = 700)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("formal-challenge-716-237").assertIsDisplayed()
    }

    @Test
    fun realChallengeRouteArmsAtBottomAndReturnsOnlyOnNextGesture() {
        composeRule.setContent { RealChallengeReturnContent() }
        val list = composeRule.onNodeWithTag("challenge-activity-list")

        repeat(4) {
            list.performTouchInput { swipeUp(durationMillis = 450) }
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("challenge-activity-bottom").assertIsDisplayed()
        composeRule.onNodeWithTag("formal-challenge-716-237").assertIsDisplayed()

        composeRule.onNodeWithTag("formal-challenge-716-237").performTouchInput {
            down(center)
            moveBy(Offset(0f, -36f), delayMillis = 400)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("formal-challenge-716-237").assertIsDisplayed()

        composeRule.onNodeWithTag("formal-challenge-716-237")
            .performTouchInput { swipeUp(durationMillis = 500) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("real-challenge-return-home").assertIsDisplayed()
    }

    @Test
    fun realChallengeDetailLocksOuterPagerUntilScrimDismissal() {
        composeRule.setContent { RealChallengeReturnContent() }
        composeRule.onNodeWithTag("challenge-open-detail").performClick()
        composeRule.onNodeWithTag("formal-challenge-detail-716-379").assertIsDisplayed()

        composeRule.onNodeWithTag("challenge-detail-list")
            .performTouchInput { swipeUp(durationMillis = 450) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("formal-challenge-716-237").assertIsDisplayed()
        composeRule.onNodeWithTag("real-challenge-return-home").assertIsNotDisplayed()

        val challenge = composeRule.onNodeWithTag("formal-challenge-716-237")
        challenge.performTouchInput {
            click(Offset(width - 8f, 8f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("formal-challenge-detail-716-379").assertDoesNotExist()
    }

    @Test
    fun challengeSessionBadgeRendersOnTheRealHomeCard() {
        composeRule.setContent {
            FormalHomeScreen(
                state = realHomeState(
                    sessions = listOf(
                        FormalHomeSessionUi(
                            id = "challenge-a",
                            title = "Challenge session",
                            summary = "Ready",
                            timeLabel = "Now",
                            updatedAtEpochMillis = 1L,
                            pinned = false,
                            challengeProvenance = ChallengeSessionProvenance("active-2")
                        )
                    )
                ),
                onPublicContentClick = {},
                onSessionClick = {},
                onOpenChallenge = {},
                onShowNewSessionSheet = {},
                onDismissNewSessionSheet = {},
                onStartLearningSetup = {},
                onOpenWeekly = {}
            )
        }

        composeRule.onNodeWithTag("challenge-session-badge-challenge-a").assertIsDisplayed()
    }

    @Test
    fun challengeNeedsCompletedBottomGestureThenASeparateUpwardGesture() {
        composeRule.setContent { ChallengeReturnGateContent() }

        val challenge = composeRule.onNodeWithTag("challenge-return-gate")
        challenge.assertIsDisplayed()
        challenge.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        challenge.assertIsDisplayed()

        composeRule.onNodeWithTag("arm-return-after-bottom-gesture").performClick()
        challenge.performTouchInput {
            down(center)
            moveBy(Offset(0f, -36f), delayMillis = 120)
            up()
        }
        composeRule.waitForIdle()
        challenge.assertIsDisplayed()

        composeRule.onNodeWithTag("toggle-detail-lock").performClick()
        challenge.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        challenge.assertIsDisplayed()

        composeRule.onNodeWithTag("toggle-detail-lock").performClick()
        challenge.performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("challenge-return-home").assertIsDisplayed()
    }

    @Test
    fun homeDragResetsChallengeAtTheStartOfTheReveal() {
        composeRule.setContent { ChallengeEntryStartContent() }
        val home = composeRule.onNodeWithTag("challenge-entry-home")

        home.performTouchInput {
            down(center)
            moveBy(Offset(0f, 160f), delayMillis = 240)
        }
        try {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag("challenge-entry-count-1")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        } finally {
            home.performTouchInput { up() }
        }
    }

    @Test
    fun staticCommunityExposesOnlyItsBackNavigationAsAClickAction() {
        composeRule.setContent { CommunityRoute(onBack = {}) }

        val clickActions = composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes()

        assertEquals(1, clickActions.size)
    }
}

@Composable
private fun RealHomeEntryContent() {
    var selectedPage by remember { mutableStateOf(WorkspaceVerticalPage.SessionHome) }
    HomeChallengePagerHost(
        selectedPage = selectedPage,
        userScrollEnabled = true,
        onDragActiveChanged = {},
        onPageSelected = { selectedPage = it },
        challengeContent = {
            ChallengeRoute(joined = false, onBack = {}, onJoin = {})
        },
        homeContent = {
            FormalHomeScreen(
                state = realHomeState(),
                onPublicContentClick = {},
                onSessionClick = {},
                onOpenChallenge = { selectedPage = WorkspaceVerticalPage.Challenge },
                onShowNewSessionSheet = {},
                onDismissNewSessionSheet = {},
                onStartLearningSetup = {},
                onOpenWeekly = {}
            )
        }
    )
}

private fun realHomeState(
    sessions: List<FormalHomeSessionUi> = emptyList()
) = FormalHomeUiState(
    publicContent = FormalPublicContentUi(
        id = "public",
        title = "Public content",
        summary = "Summary",
        publishedLabel = "Today",
        pageLabel = "1 / 1"
    ),
    sessions = sessions
)

@Composable
private fun RealChallengeReturnContent() {
    var selectedPage by remember { mutableStateOf(WorkspaceVerticalPage.Challenge) }
    var canReturnHome by remember { mutableStateOf(false) }
    HomeChallengePagerHost(
        selectedPage = selectedPage,
        userScrollEnabled = selectedPage != WorkspaceVerticalPage.Challenge || canReturnHome,
        onDragActiveChanged = {},
        onPageSelected = { selectedPage = it },
        challengeContent = {
            ChallengeRoute(
                joined = false,
                onBack = {},
                onJoin = {},
                onExitBoundaryChanged = { canReturnHome = it }
            )
        },
        homeContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .testTag("real-challenge-return-home")
            )
        }
    )
}

@Composable
private fun ChallengeEntryStartContent() {
    var selectedPage by remember { mutableStateOf(WorkspaceVerticalPage.SessionHome) }
    var entryCount by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize().testTag("challenge-entry-count-$entryCount")) {
        HomeChallengePagerHost(
            selectedPage = selectedPage,
            userScrollEnabled = true,
            onDragActiveChanged = {},
            onPageSelected = { selectedPage = it },
            challengeContent = {
                Box(Modifier.fillMaxSize().background(Color(0xFFF4F1FF)))
            },
            homeContent = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White)
                        .testTag("challenge-entry-home")
                )
            },
            onChallengeEntryStarted = { entryCount += 1 }
        )
    }
}

@Composable
private fun ChallengeReturnGateContent() {
    var selectedPage by remember { mutableStateOf(WorkspaceVerticalPage.Challenge) }
    var state by remember { mutableStateOf(ChallengeVerticalState()) }
    HomeChallengePagerHost(
        selectedPage = selectedPage,
        userScrollEnabled = state.outerPagerEnabled,
        onDragActiveChanged = {},
        onPageSelected = { selectedPage = it },
        challengeContent = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF4F1FF))
                    .testTag("challenge-return-gate"),
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        state = reduceChallengeVerticalState(
                            state,
                            ChallengeVerticalEvent.ContentScrollChanged(true, true)
                        )
                        state = reduceChallengeVerticalState(
                            state,
                            ChallengeVerticalEvent.ContentScrollChanged(true, false)
                        )
                    },
                    modifier = Modifier.testTag("arm-return-after-bottom-gesture")
                ) { Text("arm") }
                Button(
                    onClick = {
                        state = reduceChallengeVerticalState(
                            state,
                            if (state.detailOpen) {
                                ChallengeVerticalEvent.DetailClosed
                            } else {
                                ChallengeVerticalEvent.DetailOpened
                            }
                        )
                    },
                    modifier = Modifier.testTag("toggle-detail-lock")
                ) { Text("detail") }
            }
        },
        homeContent = {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .testTag("challenge-return-home")
            ) {}
        }
    )
}
