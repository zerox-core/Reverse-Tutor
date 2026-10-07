package com.reversetutor.preview.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

internal object TransientIndicatorSpec {
    const val HoldMillis = 700L
    const val FadeMillis = 240
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun WorkspacePagerHost(
    state: WorkspaceUiState,
    interactions: WorkspaceInteractionBindings,
    onPageSelected: (WorkspacePage) -> Unit,
    onVerticalPageSelected: (WorkspaceVerticalPage) -> Unit = {},
    showIndicator: Boolean = true,
    challengeContent: @Composable () -> Unit = {},
    pageContent: @Composable (WorkspacePage) -> Unit,
    onChallengeEntryStarted: () -> Unit = {}
) {
    val initialPage = remember(state.pages) {
        WorkspaceLoopingPager.initialIndex(state.pages, state.currentPage)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) {
        WorkspaceLoopingPager.virtualPageCount(state.pages)
    }
    val gestureSlopPx = with(LocalDensity.current) { 12.dp.toPx() }
    var gestureOwner by remember { mutableStateOf(WorkspaceGestureOwner.WorkspacePager) }
    val workspaceGestureGuard = Modifier.pointerInput(
        state.horizontalPagingEnabled,
        state.interactionLocks.graphCanvasModeActive,
        state.interactionLocks.innerHorizontalControlActive,
        gestureSlopPx
    ) {
        awaitEachGesture {
            try {
                val firstDown = awaitFirstDown(requireUnconsumed = false)
                var horizontalDistancePx = 0f
                var verticalDistancePx = 0f
                var gestureDecided = false
                do {
                    val event = awaitPointerEvent()
                    event.changes.firstOrNull { it.id == firstDown.id }?.let { change ->
                        horizontalDistancePx += change.position.x - change.previousPosition.x
                        verticalDistancePx += change.position.y - change.previousPosition.y
                    }
                    if (!gestureDecided &&
                        (kotlin.math.abs(horizontalDistancePx) >= gestureSlopPx ||
                            kotlin.math.abs(verticalDistancePx) >= gestureSlopPx)
                    ) {
                        gestureDecided = true
                        gestureOwner = workspaceGestureOwner(
                            horizontalDeltaPx = horizontalDistancePx,
                            verticalDeltaPx = verticalDistancePx,
                            innerHorizontalControlActive = state.interactionLocks
                                .innerHorizontalControlActive,
                            graphCanvasModeActive = state.interactionLocks.graphCanvasModeActive,
                            workspacePagingEnabled = state.horizontalPagingEnabled
                        )
                    }
                } while (event.changes.any { it.pressed })
            } finally {
                gestureOwner = WorkspaceGestureOwner.WorkspacePager
            }
        }
    }

    LaunchedEffect(gestureOwner, pagerState) {
        if (gestureOwner != WorkspaceGestureOwner.WorkspacePager &&
            pagerState.isScrollInProgress
        ) {
            pagerState.scrollToPage(pagerState.settledPage)
        }
    }

    LaunchedEffect(state.currentPage, state.pages) {
        val targetPage = WorkspaceLoopingPager.nearestIndexFor(
            pages = state.pages,
            page = state.currentPage,
            fromIndex = pagerState.currentPage
        )
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(
        pagerState,
        state.pages,
        state.interactionLocks.graphEdgePagingActive
    ) {
        if (state.interactionLocks.graphEdgePagingActive) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                onPageSelected(WorkspaceLoopingPager.pageAt(state.pages, pageIndex))
            }
    }

    CompositionLocalProvider(LocalWorkspaceInteractionBindings provides interactions) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = state.horizontalPagingEnabled &&
                    gestureOwner == WorkspaceGestureOwner.WorkspacePager,
                modifier = Modifier
                    .fillMaxSize()
                    .then(workspaceGestureGuard)
            ) { pageIndex ->
                val page = WorkspaceLoopingPager.pageAt(state.pages, pageIndex)
                WorkspaceSurfaceShell(
                    state = state.surfaceStateFor(page),
                    onRetry = { interactions.onRetrySurface(page) }
                ) {
                    when (page) {
                        WorkspacePage.SessionHome -> HomeChallengePagerHost(
                            state.verticalPage,
                            state.verticalPage != WorkspaceVerticalPage.Challenge ||
                                state.challengeCanReturnHome,
                            interactions::onChallengeDragChanged,
                            onVerticalPageSelected,
                            challengeContent,
                            { pageContent(WorkspacePage.SessionHome) },
                            onChallengeEntryStarted
                        )
                        WorkspacePage.GlobalGraph -> key(state.resourceReleaseGeneration) {
                            pageContent(page)
                        }
                        else -> pageContent(page)
                    }
                }
            }
            val horizontalIndicatorActive = pagerState.isScrollInProgress ||
                state.interactionLocks.graphEdgePagingActive
            if (showIndicator && state.verticalPage == WorkspaceVerticalPage.SessionHome) {
                TransientIndicator(
                    visible = horizontalIndicatorActive,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                ) {
                    WorkspacePagerIndicator(
                        pagerState = pagerState,
                        pages = state.pages,
                        homeSelected = state.currentPage == WorkspacePage.SessionHome,
                        modifier = Modifier.workspaceHorizontalGestureControl(interactions)
                    )
                }
            }
            if (state.currentPage == WorkspacePage.SessionHome) {
                TransientIndicator(
                    visible = state.interactionLocks.challengeDragActive,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(34.dp)
                            .height(8.dp)
                            .background(Color(0xFFC8CFDB), RoundedCornerShape(99.dp))
                    )
                }
            }
        }
    }
}

internal fun Modifier.workspaceHorizontalGestureControl(
    interactions: WorkspaceInteractionBindings
): Modifier = pointerInput(interactions) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        interactions.onInnerHorizontalControlChanged(true)
        try {
            do {
                val event = awaitPointerEvent()
                event.changes.forEach { change ->
                    if (change.positionChanged()) change.consume()
                }
            } while (event.changes.any { it.pressed })
        } finally {
            interactions.onInnerHorizontalControlChanged(false)
        }
    }
}

@Composable
private fun TransientIndicator(
    visible: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            shown = true
        } else {
            delay(TransientIndicatorSpec.HoldMillis)
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(160)),
        exit = fadeOut(animationSpec = tween(TransientIndicatorSpec.FadeMillis))
    ) {
        content()
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun WorkspacePagerIndicator(
    pagerState: PagerState,
    pages: List<WorkspacePage>,
    homeSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val position by remember(pagerState, pages.size) {
        derivedStateOf {
            workspaceIndicatorProgress(
                page = Math.floorMod(pagerState.currentPage, pages.size),
                offsetFraction = pagerState.currentPageOffsetFraction,
                pageCount = pages.size
            )
        }
    }
    val weeklyPageIndex = pages.indexOf(WorkspacePage.WeeklyDashboard)
    val scope = rememberCoroutineScope()

    WorkspacePageIndicator(
        pagePosition = position,
        pageCount = pages.size,
        onClick = if (homeSelected && weeklyPageIndex >= 0) {
            {
                scope.launch {
                    pagerState.animateScrollToPage(
                        WorkspaceLoopingPager.nearestIndexFor(
                            pages = pages,
                            page = WorkspacePage.WeeklyDashboard,
                            fromIndex = pagerState.currentPage
                        )
                    )
                }
            }
        } else {
            null
        },
        modifier = modifier
    )
}
