package com.reversetutor.feature.memory

import kotlin.math.min
import kotlin.math.sqrt

enum class GraphLayer(
    val label: String,
    val showLockedNodes: Boolean
) {
    CurrentProgress("当前进度", false),
    FullRoute("全部路线", true)
}

fun graphLayersFor(scope: GraphScope): List<GraphLayer> = when (scope) {
    GraphScope.Session -> listOf(GraphLayer.CurrentProgress, GraphLayer.FullRoute)
    GraphScope.Global -> emptyList()
}

data class GraphPoint(
    val x: Float,
    val y: Float
)

data class GraphCanvasExtent(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val nodeCount: Int
) {
    val width: Float
        get() = (maxX - minX).coerceAtLeast(MinExtent)

    val height: Float
        get() = (maxY - minY).coerceAtLeast(MinExtent)

    val centerX: Float
        get() = (minX + maxX) / 2f

    val centerY: Float
        get() = (minY + maxY) / 2f

    companion object {
        private const val MinExtent = 0.01f

        val Empty = GraphCanvasExtent(
            minX = 0f,
            maxX = 1f,
            minY = 0f,
            maxY = 1f,
            nodeCount = 0
        )
    }
}

fun graphCanvasExtent(
    nodes: List<GraphLayoutNode>,
    semanticMode: GraphSemanticMode
): GraphCanvasExtent {
    if (nodes.isEmpty()) return GraphCanvasExtent.Empty

    val minNodeX = nodes.minOf { node ->
        node.x - if (semanticMode == GraphSemanticMode.DetailCards) node.cardHalfWidth else node.radius
    }
    val maxNodeX = nodes.maxOf { node ->
        node.x + if (semanticMode == GraphSemanticMode.DetailCards) node.cardHalfWidth else node.radius
    }
    val minNodeY = nodes.minOf { node ->
        node.y - if (semanticMode == GraphSemanticMode.DetailCards) node.cardHalfHeight else node.radius
    }
    val maxNodeY = nodes.maxOf { node ->
        node.y + if (semanticMode == GraphSemanticMode.DetailCards) node.cardHalfHeight else node.radius
    }
    val countPadding = (0.045f + sqrt(nodes.size.toFloat()) * 0.012f).coerceAtMost(0.20f)

    return GraphCanvasExtent(
        minX = minNodeX - countPadding,
        maxX = maxNodeX + countPadding,
        minY = minNodeY - countPadding,
        maxY = maxNodeY + countPadding,
        nodeCount = nodes.size
    )
}

fun graphHitTest(
    nodes: List<GraphLayoutNode>,
    target: GraphPoint,
    semanticMode: GraphSemanticMode,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    density: Float,
    scale: Float = 1f,
    minimumTouchTargetDp: Float = 44f
): GraphLayoutNode? {
    val width = viewportWidthPx.coerceAtLeast(1f)
    val height = viewportHeightPx.coerceAtLeast(1f)
    val safeDensity = density.coerceAtLeast(0.01f)
    val safeScale = scale.coerceAtLeast(0.01f)
    val minimumHalfTargetPx = minimumTouchTargetDp.coerceAtLeast(0f) * safeDensity / 2f

    return nodes.mapNotNull { node ->
        val dxPx = (target.x - node.x) * width * safeScale
        val dyPx = (target.y - node.y) * height * safeScale
        val hit = when (semanticMode) {
            GraphSemanticMode.OverviewCircles -> {
                val visualHitRadiusPx = node.radius * min(width, height) * safeScale * 1.35f
                val hitRadiusPx = visualHitRadiusPx.coerceAtLeast(minimumHalfTargetPx)
                dxPx * dxPx + dyPx * dyPx <= hitRadiusPx * hitRadiusPx
            }
            GraphSemanticMode.DetailCards -> {
                val halfWidthPx = (node.cardHalfWidth * width * safeScale)
                    .coerceAtLeast(minimumHalfTargetPx)
                val halfHeightPx = (node.cardHalfHeight * height * safeScale)
                    .coerceAtLeast(minimumHalfTargetPx)
                kotlin.math.abs(dxPx) <= halfWidthPx && kotlin.math.abs(dyPx) <= halfHeightPx
            }
        }
        if (hit) GraphHitCandidate(node, dxPx * dxPx + dyPx * dyPx) else null
    }.minWithOrNull(
        compareBy<GraphHitCandidate> { it.distanceSquaredPx }
            .thenBy { it.node.id }
    )?.node
}

private data class GraphHitCandidate(
    val node: GraphLayoutNode,
    val distanceSquaredPx: Float
)

data class GraphPanBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float
) {
    fun clamp(point: GraphPoint): GraphPoint = GraphPoint(
        x = point.x.coerceIn(minX, maxX),
        y = point.y.coerceIn(minY, maxY)
    )
}

fun graphPanBounds(
    extent: GraphCanvasExtent,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    scale: Float,
    edgeMarginFraction: Float = 0.08f
): GraphPanBounds {
    val width = viewportWidthPx.coerceAtLeast(1f)
    val height = viewportHeightPx.coerceAtLeast(1f)
    val safeScale = scale.coerceAtLeast(0.01f)
    val marginX = width * edgeMarginFraction.coerceIn(0f, 0.45f)
    val marginY = height * edgeMarginFraction.coerceIn(0f, 0.45f)
    val leftWithoutPan = width / 2f + (extent.minX * width - width / 2f) * safeScale
    val rightWithoutPan = width / 2f + (extent.maxX * width - width / 2f) * safeScale
    val topWithoutPan = height / 2f + (extent.minY * height - height / 2f) * safeScale
    val bottomWithoutPan = height / 2f + (extent.maxY * height - height / 2f) * safeScale

    fun axisBounds(
        leadingWithoutPan: Float,
        trailingWithoutPan: Float,
        viewportSize: Float,
        margin: Float
    ): Pair<Float, Float> {
        val lower = viewportSize - margin - trailingWithoutPan
        val upper = margin - leadingWithoutPan
        return if (lower <= upper) {
            lower to upper
        } else {
            val centered = viewportSize / 2f - (leadingWithoutPan + trailingWithoutPan) / 2f
            centered to centered
        }
    }

    val horizontal = axisBounds(leftWithoutPan, rightWithoutPan, width, marginX)
    val vertical = axisBounds(topWithoutPan, bottomWithoutPan, height, marginY)
    return GraphPanBounds(
        minX = horizontal.first,
        maxX = horizontal.second,
        minY = vertical.first,
        maxY = vertical.second
    )
}

data class GraphViewportTransform(
    val scale: Float,
    val pan: GraphPoint
)

fun graphFitTransform(
    extent: GraphCanvasExtent,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    minScale: Float = 0.55f,
    maxScale: Float = 3.2f,
    paddingFraction: Float = 0.10f
): GraphViewportTransform {
    if (extent.nodeCount == 0) return GraphViewportTransform(1f, GraphPoint(0f, 0f))

    val availableFraction = (1f - paddingFraction.coerceIn(0f, 0.40f) * 2f).coerceAtLeast(0.2f)
    val scale = min(
        availableFraction / extent.width,
        availableFraction / extent.height
    ).coerceIn(minScale, maxScale)
    val pan = GraphPoint(
        x = (0.5f - extent.centerX) * viewportWidthPx.coerceAtLeast(1f) * scale,
        y = (0.5f - extent.centerY) * viewportHeightPx.coerceAtLeast(1f) * scale
    )
    val bounds = graphPanBounds(extent, viewportWidthPx, viewportHeightPx, scale)
    return GraphViewportTransform(scale = scale, pan = bounds.clamp(pan))
}

enum class GraphGesturePhase {
    Idle,
    Pressed,
    Panning,
    Scaling,
    DraggingNode
}

data class GraphGestureState(
    val phase: GraphGesturePhase = GraphGesturePhase.Idle,
    val scale: Float = 1f,
    val pan: GraphPoint = GraphPoint(0f, 0f),
    val pressedNodeId: String? = null,
    val draggingNodeId: String? = null,
    val nodePositions: Map<String, GraphPoint> = emptyMap()
) {
    val interactionNodeId: String?
        get() = draggingNodeId ?: pressedNodeId
}

sealed interface GraphGestureAction {
    data class PressNode(val nodeId: String?) : GraphGestureAction
    data class PanBy(
        val delta: GraphPoint,
        val bounds: GraphPanBounds
    ) : GraphGestureAction
    data class ZoomBy(
        val factor: Float,
        val boundsForScale: (Float) -> GraphPanBounds
    ) : GraphGestureAction
    data class BeginNodeDrag(val nodeId: String) : GraphGestureAction
    data class DragNodeTo(
        val nodeId: String,
        val position: GraphPoint,
        val extent: GraphCanvasExtent
    ) : GraphGestureAction
    data class Fit(val transform: GraphViewportTransform) : GraphGestureAction
    data object Release : GraphGestureAction
    data object Cancel : GraphGestureAction
}

fun reduceGraphGesture(
    state: GraphGestureState,
    action: GraphGestureAction
): GraphGestureState = when (action) {
    is GraphGestureAction.PressNode -> state.copy(
        phase = GraphGesturePhase.Pressed,
        pressedNodeId = action.nodeId,
        draggingNodeId = null
    )
    is GraphGestureAction.PanBy -> state.copy(
        phase = GraphGesturePhase.Panning,
        pan = action.bounds.clamp(
            GraphPoint(
                x = state.pan.x + action.delta.x,
                y = state.pan.y + action.delta.y
            )
        ),
        pressedNodeId = null
    )
    is GraphGestureAction.ZoomBy -> {
        val nextScale = (state.scale * action.factor).coerceIn(0.55f, 3.2f)
        state.copy(
            phase = GraphGesturePhase.Scaling,
            scale = nextScale,
            pan = action.boundsForScale(nextScale).clamp(state.pan),
            pressedNodeId = null,
            draggingNodeId = null
        )
    }
    is GraphGestureAction.BeginNodeDrag -> state.copy(
        phase = GraphGesturePhase.DraggingNode,
        pressedNodeId = action.nodeId,
        draggingNodeId = action.nodeId
    )
    is GraphGestureAction.DragNodeTo -> state.copy(
        phase = GraphGesturePhase.DraggingNode,
        pressedNodeId = action.nodeId,
        draggingNodeId = action.nodeId,
        nodePositions = state.nodePositions + (
            action.nodeId to GraphPoint(
                x = action.position.x.coerceIn(action.extent.minX, action.extent.maxX),
                y = action.position.y.coerceIn(action.extent.minY, action.extent.maxY)
            )
        )
    )
    is GraphGestureAction.Fit -> state.copy(
        phase = GraphGesturePhase.Idle,
        scale = action.transform.scale,
        pan = action.transform.pan,
        pressedNodeId = null,
        draggingNodeId = null
    )
    GraphGestureAction.Release,
    GraphGestureAction.Cancel -> state.copy(
        phase = GraphGesturePhase.Idle,
        pressedNodeId = null,
        draggingNodeId = null
    )
}

internal fun GraphRenderSnapshot.withNodePositions(
    positions: Map<String, GraphPoint>
): GraphRenderSnapshot {
    if (positions.isEmpty()) return this
    return copy(
        nodes = nodes.map { node ->
            positions[node.id]?.let { point -> node.copy(x = point.x, y = point.y) } ?: node
        }
    )
}
