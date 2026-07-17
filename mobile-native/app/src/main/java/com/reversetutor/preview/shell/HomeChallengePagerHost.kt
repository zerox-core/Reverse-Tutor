package com.reversetutor.preview.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun HomeChallengePagerHost(
    selectedPage: WorkspaceVerticalPage,
    userScrollEnabled: Boolean,
    onDragActiveChanged: (Boolean) -> Unit,
    onPageSelected: (WorkspaceVerticalPage) -> Unit,
    challengeContent: @Composable () -> Unit,
    homeContent: @Composable () -> Unit
) {
    val pages = WorkspaceVerticalPage.entries
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(selectedPage).coerceAtLeast(0)
    ) { pages.size }

    LaunchedEffect(selectedPage) {
        val targetPage = pages.indexOf(selectedPage)
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect(onDragActiveChanged)
    }
    LaunchedEffect(pagerState, pages) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                pages.getOrNull(pageIndex)?.let(onPageSelected)
            }
    }

    VerticalPager(
        state = pagerState,
        userScrollEnabled = userScrollEnabled,
        beyondBoundsPageCount = 1,
        key = { pages[it] },
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        when (pages[pageIndex]) {
            WorkspaceVerticalPage.Challenge -> challengeContent()
            WorkspaceVerticalPage.SessionHome -> homeContent()
        }
    }
}
