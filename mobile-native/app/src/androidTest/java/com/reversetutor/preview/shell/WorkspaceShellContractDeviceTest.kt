package com.reversetutor.preview.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.feature.settings.FormalSettingsScreen
import com.reversetutor.feature.settings.FormalSettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceShellContractDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun verticalDominantDragDoesNotPageTheHorizontalWorkspace() {
        composeRule.setContent { GestureOwnershipWorkspace() }

        composeRule.onNodeWithTag("gesture-home").performTouchInput {
            down(center)
            moveBy(Offset(42f, 180f), delayMillis = 180)
            up()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("gesture-home").assertIsDisplayed()
    }

    @Test
    fun innerHorizontalControlConsumesBeforeTheWorkspacePager() {
        composeRule.setContent { GestureOwnershipWorkspace() }

        composeRule.onNodeWithTag("inner-horizontal-control").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("gesture-home").assertIsDisplayed()
    }

    @Test
    fun settingsToggleExposesCheckedSwitchSemantics() {
        composeRule.setContent {
            FormalSettingsScreen(
                state = FormalSettingsUiState(challengeReminderEnabled = true),
                onBack = {},
                onOpenLlmConfiguration = {},
                onOpenStorage = {},
                onOpenImportExport = {},
                onOpenAbout = {}
            )
        }

        composeRule.onNodeWithText("挑战任务提醒")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ToggleableState,
                    ToggleableState.On
                )
            )
    }
}

@Composable
private fun GestureOwnershipWorkspace() {
    val viewModel = remember { WorkspaceViewModel() }
    val state by viewModel.uiState.collectAsState()
    val interactions = remember(viewModel) {
        WorkspaceInteractionBindings(viewModel::onAction)
    }

    WorkspacePagerHost(
        state = state,
        interactions = interactions,
        onPageSelected = { viewModel.onAction(WorkspaceUiAction.SelectPage(it)) },
        pageContent = { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (page == WorkspacePage.SessionHome) Color.White else Color.Gray)
                    .testTag(if (page == WorkspacePage.SessionHome) "gesture-home" else "gesture-$page")
            ) {
                if (page == WorkspacePage.SessionHome) {
                    Box(
                        modifier = Modifier
                            .size(width = 220.dp, height = 64.dp)
                            .workspaceHorizontalGestureControl(interactions)
                            .testTag("inner-horizontal-control")
                    )
                }
            }
        }
    )
}
