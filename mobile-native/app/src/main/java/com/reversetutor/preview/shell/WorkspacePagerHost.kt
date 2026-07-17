package com.reversetutor.preview.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun WorkspacePagerHost(
    state: WorkspaceUiState,
    interactions: WorkspaceInteractionBindings,
    onPageSelected: (WorkspacePage) -> Unit,
    onVerticalPageSelected: (WorkspaceVerticalPage) -> Unit = {},
    showIndicator: Boolean = true,
    challengeContent: @Composable () -> Unit = {},
    pageContent: @Composable (WorkspacePage) -> Unit
) {
    val initialPage = state.pages.indexOf(state.currentPage).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) {
        state.pages.size
    }

    LaunchedEffect(state.currentPage, state.pages) {
        val targetPage = state.pages.indexOf(state.currentPage)
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
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
                state.pages.getOrNull(pageIndex)?.let(onPageSelected)
            }
    }

    CompositionLocalProvider(LocalWorkspaceInteractionBindings provides interactions) {
        Box(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = state.horizontalPagingEnabled &&
                    state.currentPage != WorkspacePage.GlobalGraph,
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val page = state.pages.getOrNull(pageIndex)
                when (page) {
                    WorkspacePage.SessionHome -> HomeChallengePagerHost(
                        selectedPage = state.verticalPage,
                        userScrollEnabled = state.verticalPage != WorkspaceVerticalPage.Challenge ||
                            state.challengeCanReturnHome,
                        onDragActiveChanged = interactions::onChallengeDragChanged,
                        onPageSelected = onVerticalPageSelected,
                        challengeContent = challengeContent,
                        homeContent = { pageContent(WorkspacePage.SessionHome) }
                    )
                    null -> Unit
                    else -> pageContent(page)
                }
            }
            if (
                state.currentPage == WorkspacePage.GlobalGraph ||
                state.interactionLocks.graphEdgePagingActive
            ) {
                GraphEdgePagingOverlay(
                    pagerState = pagerState,
                    pages = state.pages,
                    interactions = interactions
                )
            }
            if (showIndicator && state.verticalPage == WorkspaceVerticalPage.SessionHome) {
                WorkspacePagerIndicator(
                    pagerState = pagerState,
                    pages = state.pages,
                    homeSelected = state.currentPage == WorkspacePage.SessionHome,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                )
            }
        }
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
                page = pagerState.currentPage,
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
                    pagerState.animateScrollToPage(weeklyPageIndex)
                }
            }
        } else {
            null
        },
        modifier = modifier
    )
}
