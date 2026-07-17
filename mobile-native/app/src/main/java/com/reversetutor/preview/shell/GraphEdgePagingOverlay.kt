package com.reversetutor.preview.shell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

enum class GraphEdge {
    Left,
    Right
}

internal data class GraphEdgeDragUpdate(
    val distancePx: Float,
    val pagerDeltaPx: Float
)

internal data class GraphEdgeThresholds(
    val distancePx: Float,
    val velocityPxPerSecond: Float
)

internal fun graphEdgeThresholds(density: Float): GraphEdgeThresholds {
    val safeDensity = density.coerceAtLeast(0f)
    return GraphEdgeThresholds(
        distancePx = 96f * safeDensity,
        velocityPxPerSecond = 900f * safeDensity
    )
}

internal fun graphEdgeDragUpdate(
    edge: GraphEdge,
    currentDistancePx: Float,
    dragDeltaPx: Float,
    maxPagerDistancePx: Float
): GraphEdgeDragUpdate {
    val outwardDeltaPx = when (edge) {
        GraphEdge.Left -> dragDeltaPx
        GraphEdge.Right -> -dragDeltaPx
    }
    val nextDistancePx = (currentDistancePx + outwardDeltaPx).coerceAtLeast(0f)
    val visualLimitPx = maxPagerDistancePx.coerceAtLeast(0f)
    val appliedOutwardDeltaPx = nextDistancePx.coerceAtMost(visualLimitPx) -
        currentDistancePx.coerceAtMost(visualLimitPx)
    val pagerDeltaPx = when (edge) {
        GraphEdge.Left -> -appliedOutwardDeltaPx
        GraphEdge.Right -> appliedOutwardDeltaPx
    }
    return GraphEdgeDragUpdate(
        distancePx = nextDistancePx,
        pagerDeltaPx = pagerDeltaPx
    )
}

internal fun graphEdgeTarget(
    edge: GraphEdge,
    distancePx: Float,
    velocityPx: Float,
    distanceThresholdPx: Float,
    velocityThresholdPx: Float
): WorkspacePage? {
    if (distancePx < distanceThresholdPx && velocityPx < velocityThresholdPx) {
        return null
    }
    return when (edge) {
        GraphEdge.Left -> WorkspacePage.SessionHome
        GraphEdge.Right -> WorkspacePage.Community
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun GraphEdgePagingOverlay(
    pagerState: PagerState,
    pages: List<WorkspacePage>,
    interactions: WorkspaceInteractionBindings,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        GraphEdgeDragZone(
            edge = GraphEdge.Left,
            pagerState = pagerState,
            pages = pages,
            interactions = interactions,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        GraphEdgeDragZone(
            edge = GraphEdge.Right,
            pagerState = pagerState,
            pages = pages,
            interactions = interactions,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun GraphEdgeDragZone(
    edge: GraphEdge,
    pagerState: PagerState,
    pages: List<WorkspacePage>,
    interactions: WorkspaceInteractionBindings,
    modifier: Modifier = Modifier
) {
    var dragDistancePx by remember(edge) { mutableFloatStateOf(0f) }
    var maxPagerDistancePx by remember(edge) { mutableFloatStateOf(0f) }
    var deltaChannel by remember(edge) { mutableStateOf<Channel<Float>?>(null) }
    var scrollJob by remember(edge) { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val thresholds = graphEdgeThresholds(LocalDensity.current.density)
    val draggableState = rememberDraggableState { deltaPx ->
        val update = graphEdgeDragUpdate(
            edge = edge,
            currentDistancePx = dragDistancePx,
            dragDeltaPx = deltaPx,
            maxPagerDistancePx = maxPagerDistancePx
        )
        dragDistancePx = update.distancePx
        if (update.pagerDeltaPx != 0f) {
            deltaChannel?.trySend(update.pagerDeltaPx)
        }
    }

    Box(
        modifier = modifier
            .height(180.dp)
            .width(32.dp)
            .systemGestureExclusion()
            .testTag(
                when (edge) {
                    GraphEdge.Left -> "graph-edge-left"
                    GraphEdge.Right -> "graph-edge-right"
                }
            )
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = {
                    dragDistancePx = 0f
                    maxPagerDistancePx = (
                        pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                    ).toFloat()
                    scrollJob?.cancel()
                    val channel = Channel<Float>(Channel.UNLIMITED)
                    deltaChannel = channel
                    scrollJob = scope.launch {
                        pagerState.scroll(MutatePriority.UserInput) {
                            for (delta in channel) {
                                scrollBy(delta)
                            }
                        }
                    }
                    interactions.onGraphEdgePagingChanged(true)
                },
                onDragStopped = { velocityPx ->
                    try {
                        deltaChannel?.close()
                        scrollJob?.join()
                        val outwardVelocityPx = when (edge) {
                            GraphEdge.Left -> max(velocityPx, 0f)
                            GraphEdge.Right -> max(-velocityPx, 0f)
                        }
                        val targetPage = graphEdgeTarget(
                            edge = edge,
                            distancePx = dragDistancePx,
                            velocityPx = outwardVelocityPx,
                            distanceThresholdPx = thresholds.distancePx,
                            velocityThresholdPx = thresholds.velocityPxPerSecond
                        ) ?: WorkspacePage.GlobalGraph
                        val targetIndex = pages.indexOf(targetPage)
                        if (targetIndex >= 0) {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    } finally {
                        deltaChannel = null
                        scrollJob = null
                        dragDistancePx = 0f
                        interactions.onGraphEdgePagingChanged(false)
                    }
                }
            )
    )
}
