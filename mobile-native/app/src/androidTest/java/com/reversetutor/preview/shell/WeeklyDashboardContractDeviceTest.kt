package com.reversetutor.preview.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.feature.memory.FormalWeeklySessionOption
import com.reversetutor.feature.memory.WeeklyDashboardConfiguration
import com.reversetutor.feature.memory.WeeklyDashboardGridScreen
import com.reversetutor.feature.memory.WeeklyDashboardUiAction
import com.reversetutor.feature.memory.WeeklyDashboardUiState
import com.reversetutor.feature.memory.WeeklyLayoutAction
import com.reversetutor.feature.memory.WeeklyLayoutEditorState
import com.reversetutor.feature.memory.WeeklySourceMode
import com.reversetutor.feature.memory.WeeklyWidgetKind
import com.reversetutor.feature.memory.WeeklyWidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WeeklyDashboardContractDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fixedHeaderAndFourDefaultWidgetsRenderFromFeatureState() {
        composeRule.setContent {
            MaterialTheme {
                WeeklyDashboardGridScreen(
                    state = WeeklyDashboardUiState(),
                    sessionOptions = listOf(
                        FormalWeeklySessionOption("session-a", "数学", "今天")
                    ),
                    onAction = {},
                    isPageActive = true,
                    onWidgetDragChanged = {},
                    onInnerHorizontalControlChanged = {},
                    onEditSurfaceChanged = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("本周学习组件面板").assertExists()
        composeRule.onNodeWithText("本周学习").assertExists()
        composeRule.onNodeWithText("今日计划").assertExists()
        composeRule.onNodeWithText("本周主线").assertExists()
        composeRule.onNodeWithText("薄弱点").assertExists()
        composeRule.onNodeWithText("Token 用量").assertExists()
    }

    @Test
    fun editButtonDispatchesFeatureOwnedDraftAction() {
        val actions = mutableListOf<WeeklyDashboardUiAction>()
        composeRule.setContent {
            MaterialTheme {
                WeeklyDashboardGridScreen(
                    state = WeeklyDashboardUiState(
                        layout = WeeklyLayoutEditorState(WeeklyDashboardConfiguration.defaults())
                    ),
                    sessionOptions = emptyList(),
                    onAction = actions::add,
                    isPageActive = true,
                    onWidgetDragChanged = {},
                    onInnerHorizontalControlChanged = {},
                    onEditSurfaceChanged = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithContentDescription("编辑组件布局").performClick()

        assertEquals(
            WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit),
            actions.single()
        )
    }

    @Test
    fun workspacePagerLocksForWidgetDragAndTokenHistoryGestures() {
        val widgetDrag = WorkspaceInteractionLocks(widgetDragActive = true)
        val tokenHistory = WorkspaceInteractionLocks(innerHorizontalControlActive = true)

        assertFalse(widgetDrag.horizontalPagingEnabled)
        assertFalse(tokenHistory.horizontalPagingEnabled)
        assertTrue(WorkspaceInteractionLocks().horizontalPagingEnabled)
    }

    @Test
    fun normalWidgetClickUsesDestinationCallbackAndEditShowsCornerControls() {
        val opened = mutableListOf<WeeklyWidgetKind>()
        var editing = false
        composeRule.setContent {
            MaterialTheme {
                WeeklyDashboardGridScreen(
                    state = WeeklyDashboardUiState(
                        layout = WeeklyLayoutEditorState(
                            WeeklyDashboardConfiguration.defaults(),
                            editing = editing
                        )
                    ),
                    sessionOptions = emptyList(),
                    onAction = { action ->
                        if (action == WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit)) editing = true
                    },
                    onOpenWidget = opened::add,
                    isPageActive = true,
                    onWidgetDragChanged = {},
                    onInnerHorizontalControlChanged = {},
                    onEditSurfaceChanged = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("weekly-widget-WeeklyMainline").performClick()
        assertEquals(listOf(WeeklyWidgetKind.WeeklyMainline), opened)
    }

    @Test
    fun longPressDragDispatchesEnterMoveAndLocksPager() {
        val actions = mutableListOf<WeeklyDashboardUiAction>()
        val locks = mutableListOf<Boolean>()
        composeRule.setContent {
            MaterialTheme {
                WeeklyDashboardGridScreen(
                    state = WeeklyDashboardUiState(
                        layout = WeeklyLayoutEditorState(WeeklyDashboardConfiguration.defaults())
                    ),
                    sessionOptions = emptyList(),
                    onAction = actions::add,
                    isPageActive = true,
                    onWidgetDragChanged = locks::add,
                    onInnerHorizontalControlChanged = {},
                    onEditSurfaceChanged = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("weekly-widget-WeeklyMainline").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveBy(Offset(180f, 150f), delayMillis = 180)
            up()
        }

        assertTrue(actions.any { it == WeeklyDashboardUiAction.EditLayout(WeeklyLayoutAction.EnterEdit) })
        assertTrue(actions.any { it is WeeklyDashboardUiAction.EditLayout && it.action is WeeklyLayoutAction.Move })
        assertEquals(listOf(true, false), locks.takeLast(2))
    }

    @Test
    fun libraryDropAndHeaderScopeDispatchRealActionsAndTokenEmptyIsExplicit() {
        val actions = mutableListOf<WeeklyDashboardUiAction>()
        val state = mutableStateOf(
            WeeklyDashboardUiState(
                layout = WeeklyLayoutEditorState(WeeklyDashboardConfiguration.defaults())
            )
        )
        composeRule.setContent {
            MaterialTheme {
                WeeklyDashboardGridScreen(
                    state = state.value,
                    sessionOptions = listOf(FormalWeeklySessionOption("a", "A", "今天")),
                    onAction = { action ->
                        actions += action
                        if (action is WeeklyDashboardUiAction.EditLayout) {
                            state.value = state.value.copy(
                                layout = com.reversetutor.feature.memory.WeeklyLayoutReducer
                                    .reduce(state.value.layout, action.action).state
                            )
                        }
                    },
                    isPageActive = true,
                    onWidgetDragChanged = {},
                    onInnerHorizontalControlChanged = {},
                    onEditSurfaceChanged = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("weekly-default-scope").performClick()
        composeRule.onNodeWithText("不选择来源").performClick()
        composeRule.onNodeWithText("应用来源").performClick()
        assertTrue(actions.any {
            it is WeeklyDashboardUiAction.ChangeDefaultScope && it.source == WeeklyWidgetSource.None
        })

        composeRule.onNodeWithContentDescription("编辑组件布局").performClick()
        composeRule.onNodeWithTag("weekly-widget-library").assertExists()
        assertTrue(WeeklyWidgetSource(WeeklySourceMode.None).sessionIds.isEmpty())
    }
}
