package com.reversetutor.preview.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    homeContent: @Composable () -> Unit,
    onChallengeEntryStarted: () -> Unit = {}
) {
    val pages = WorkspaceVerticalPage.entries
    val pagerState = rememberPagerState(
        initialPage = pages.indexOf(selectedPage).coerceAtLeast(0)
    ) { pages.size }
    val homePageIndex = pages.indexOf(WorkspaceVerticalPage.SessionHome)
    var challengeEntryReported by remember { mutableStateOf(false) }

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
    LaunchedEffect(pagerState, homePageIndex) {
        snapshotFlow {
            Triple(
                pagerState.isScrollInProgress,
                pagerState.settledPage,
                pagerState.currentPageOffsetFraction
            )
        }.distinctUntilChanged().collect { (scrolling, settledPage, offset) ->
            if (!scrolling) {
                challengeEntryReported = false
            } else if (
                settledPage == homePageIndex &&
                offset < 0f &&
                !challengeEntryReported
            ) {
                challengeEntryReported = true
                onChallengeEntryStarted()
            }
        }
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
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            snapPositionalThreshold = ChallengePagerPolicy.EntryPositionalThreshold
        ),
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
