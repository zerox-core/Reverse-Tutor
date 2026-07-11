package com.reversetutor.preview.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun WorkspacePagerHost(
    state: WorkspaceUiState,
    interactions: WorkspaceInteractionBindings,
    onPageSelected: (WorkspacePage) -> Unit,
    pageContent: @Composable (WorkspacePage) -> Unit
) {
    val initialPage = state.pages.indexOf(state.currentPage).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) {
        state.pages.size
    }

    LaunchedEffect(state.currentPage, state.pages) {
        val targetPage = state.pages.indexOf(state.currentPage)
        if (targetPage >= 0 && pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }
    LaunchedEffect(pagerState, state.pages) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                state.pages.getOrNull(pageIndex)?.let(onPageSelected)
            }
    }

    CompositionLocalProvider(LocalWorkspaceInteractionBindings provides interactions) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = state.horizontalPagingEnabled,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val page = state.pages.getOrNull(pageIndex)
            if (page != null) {
                pageContent(page)
            }
        }
    }
}
