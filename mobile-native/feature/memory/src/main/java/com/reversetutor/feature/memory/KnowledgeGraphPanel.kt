@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.reversetutor.feature.memory

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import kotlin.math.abs

data class GraphViewportState(
    val scale: Float = 1f,
    val pan: Offset = Offset.Zero,
    val semanticMode: GraphSemanticMode = GraphSemanticMode.OverviewCircles
)

@Composable
fun KnowledgeGraphPanel(
    state: KnowledgeGraphUiState,
    onSelectedNodeChange: (String?) -> Unit,
    editable: Boolean = true,
    onNodeLabelSave: (GraphLayoutNode, String) -> Unit = { _, _ -> },
    onNodeReviewAction: (GraphLayoutNode, GraphNodeReviewAction) -> Unit = { _, _ -> },
    onGraphInteractionChanged: (Boolean) -> Unit = {},
    onOpenChatEvidence: (GraphLayoutNode) -> Unit = {},
    onOpenSourceEvidence: (GraphLayoutNode) -> Unit = {},
    onCreateEvidence: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showLockedNodes by remember(state.scope) { mutableStateOf(false) }
    val presentation = state.presentation()
    var showNodeList by remember(state.scope, state.status, state.allNodes) {
        mutableStateOf(state.status in setOf(GraphRenderStatus.Invalid, GraphRenderStatus.Large))
    }
    val selectedNode = state.selectedNode
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        contentColor = FormalColors.Ink,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = FormalColors.Ink,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = state.summary,
                        color = FormalColors.Muted,
                        fontSize = 10.sp,
                        lineHeight = 16.sp
                    )
                }
                if (state.scope == GraphScope.Session && state.lockedNodes.isNotEmpty()) {
                    TextButton(
                        onClick = { showLockedNodes = !showLockedNodes },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("graph-locked-nodes-toggle")
                    ) {
                        Text(if (showLockedNodes) "隐藏未解锁" else "显示未解锁", fontSize = 10.sp)
                    }
                }
            }
            val recoveryAction = presentation.recoveryAction
            val recoveryClick: (() -> Unit)? = when (recoveryAction) {
                GraphRecoveryAction.CreateEvidence -> onCreateEvidence
                GraphRecoveryAction.Retry -> onRetry
                GraphRecoveryAction.Review,
                GraphRecoveryAction.BrowseNodes -> { { showNodeList = true } }
                null -> null
            }
            if (state.status != GraphRenderStatus.Ready) {
                GraphStatusPanel(
                    state = state,
                    action = recoveryAction,
                    onAction = recoveryClick,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (presentation.showCanvas) {
                FormalGraphCanvas(
                    state = state,
                    showLockedNodes = showLockedNodes,
                    onSelectedNodeChange = onSelectedNodeChange,
                    onInteractionChanged = onGraphInteractionChanged,
                    showToolbar = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (state.scope == GraphScope.Session) 520.dp else 430.dp)
                )
            }
            if (presentation.showNodeList) {
                GraphNodeListControl(
                    open = showNodeList,
                    onOpen = { showNodeList = true },
                    onClose = { showNodeList = false },
                    onSelectedNodeChange = {
                        onSelectedNodeChange(it)
                        showNodeList = false
                    },
                    nodes = state.accessibleNodes(),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            if (selectedNode != null) {
                GraphNodeDetail(
                    node = selectedNode,
                    relations = state.relatedEdges(selectedNode.id),
                    editable = editable,
                    onNodeLabelSave = onNodeLabelSave,
                    onNodeReviewAction = onNodeReviewAction,
                    onOpenChatEvidence = onOpenChatEvidence,
                    onOpenSourceEvidence = onOpenSourceEvidence,
                    onClearSelection = { onSelectedNodeChange(null) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun GraphStatusPanel(
    state: KnowledgeGraphUiState,
    action: GraphRecoveryAction?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("graph-status-${state.status.name.lowercase()}"),
        color = FormalColors.PrimarySoft,
        contentColor = FormalColors.Ink,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(state.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (state.summary.isNotBlank()) {
                Text(state.summary, color = FormalColors.Muted, fontSize = 11.sp, lineHeight = 17.sp)
            }
            action?.let { recoveryAction ->
                if (onAction != null) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("graph-recovery-${recoveryAction.name.lowercase()}"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(recoveryAction.label)
                    }
                } else {
                    Text(
                        text = "下一步：${recoveryAction.label}",
                        color = FormalColors.Muted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GraphNodeListControl(
    open: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onSelectedNodeChange: (String) -> Unit,
    nodes: List<GraphLayoutNode>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = if (open) onClose else onOpen,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("graph-node-list")
        ) {
            Text(if (open) "收起节点列表" else "打开节点列表（${nodes.size}）")
        }
        if (open) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = FormalColors.Surface,
                contentColor = FormalColors.Ink,
                shape = RoundedCornerShape(FormalShapes.CardRadius),
                border = BorderStroke(1.dp, FormalColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text("节点列表", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    nodes.forEach { node ->
                        TextButton(
                            onClick = { onSelectedNodeChange(node.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("graph-node-${node.id}")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(node.label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${node.kindLabel} · ${node.statusLabel}",
                                    color = FormalColors.Muted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FormalGraphCanvas(
    state: KnowledgeGraphUiState,
    showLockedNodes: Boolean,
    onSelectedNodeChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    initialScale: Float = 1f,
    expandedSessionRoot: Boolean = false,
    showToolbar: Boolean = false,
    onFilterClick: (() -> Unit)? = null,
    interactionEnabled: Boolean = true,
    onRequestInteraction: () -> Unit = {},
    onInteractionChanged: (Boolean) -> Unit = {},
    onViewportChanged: (GraphViewportState) -> Unit = {},
    onNodePositionChanged: (String, GraphPoint) -> Unit = { _, _ -> }
) {
    var gestureState by remember(state.scope, state.allNodes.map { it.id }, initialScale) {
        mutableStateOf(GraphGestureState(scale = initialScale.coerceIn(0.55f, 3.2f)))
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var lastEmptyTap by remember(state.scope) { mutableStateOf<GraphTapRecord?>(null) }
    val typeMultiplier = LocalFormalTypeScale.current.multiplier
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val renderSnapshot = state.renderSnapshot(showLockedNodes)
        .withNodePositions(gestureState.nodePositions)
    val semanticMode = graphSemanticMode(state.scope, gestureState.scale)
    val extent = graphCanvasExtent(renderSnapshot.nodes, semanticMode)
    val currentSnapshot by rememberUpdatedState(renderSnapshot)
    val currentSemanticMode by rememberUpdatedState(semanticMode)
    val currentExtent by rememberUpdatedState(extent)

    fun panBounds(scale: Float): GraphPanBounds = graphPanBounds(
        extent = currentExtent,
        viewportWidthPx = canvasSize.width.toFloat(),
        viewportHeightPx = canvasSize.height.toFloat(),
        scale = scale
    )

    fun fitCanvas() {
        gestureState = reduceGraphGesture(
            gestureState,
            GraphGestureAction.Fit(
                graphFitTransform(
                    extent = currentExtent,
                    viewportWidthPx = canvasSize.width.toFloat(),
                    viewportHeightPx = canvasSize.height.toFloat()
                )
            )
        )
    }

    LaunchedEffect(gestureState.scale, gestureState.pan, semanticMode) {
        onViewportChanged(
            GraphViewportState(
                scale = gestureState.scale,
                pan = Offset(gestureState.pan.x, gestureState.pan.y),
                semanticMode = semanticMode
            )
        )
    }

    Box(
        modifier = modifier
            .background(GraphCanvasBackground)
            .testTag("knowledge-graph-canvas")
            .semantics { contentDescription = "知识图谱画布" }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .then(if (interactionEnabled) Modifier.pointerInput(
                    state.scope,
                    state.allNodes.map { it.id },
                    showLockedNodes,
                    onSelectedNodeChange,
                    onInteractionChanged,
                    onNodePositionChanged
                ) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val downPosition = first.position
                        var lastPosition = first.position
                        var movedDistance = 0f
                        var zoomed = false
                        val downModel = screenToGraphModel(
                            position = downPosition,
                            canvasSize = canvasSize,
                            scale = gestureState.scale,
                            pan = gestureState.pan
                        )
                        val pressedNode = graphNodeAt(
                            snapshot = currentSnapshot,
                            point = downModel,
                            semanticMode = currentSemanticMode,
                            canvasSize = canvasSize,
                            density = density.density,
                            scale = gestureState.scale
                        )
                        gestureState = reduceGraphGesture(
                            gestureState,
                            GraphGestureAction.PressNode(pressedNode?.id)
                        )
                        onInteractionChanged(true)
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val gesturePan = event.calculatePan()
                                val gestureZoom = event.calculateZoom()
                                if (gestureZoom.isFinite() && abs(gestureZoom - 1f) > 0.001f) {
                                    gestureState = reduceGraphGesture(
                                        gestureState,
                                        GraphGestureAction.ZoomBy(gestureZoom, ::panBounds)
                                    )
                                    zoomed = true
                                }
                                event.changes.firstOrNull { it.id == first.id }?.let { change ->
                                    lastPosition = change.position
                                    movedDistance += gesturePan.getDistance()
                                    val heldLongEnough = change.uptimeMillis - first.uptimeMillis >=
                                        viewConfiguration.longPressTimeoutMillis
                                    if (!zoomed &&
                                        gestureState.phase == GraphGesturePhase.Pressed &&
                                        pressedNode != null &&
                                        heldLongEnough
                                    ) {
                                        gestureState = reduceGraphGesture(
                                            gestureState,
                                            GraphGestureAction.BeginNodeDrag(pressedNode.id)
                                        )
                                    }
                                    if (gestureState.draggingNodeId != null) {
                                        val modelPosition = screenToGraphModel(
                                            position = change.position,
                                            canvasSize = canvasSize,
                                            scale = gestureState.scale,
                                            pan = gestureState.pan
                                        )
                                        gestureState = reduceGraphGesture(
                                            gestureState,
                                            GraphGestureAction.DragNodeTo(
                                                nodeId = gestureState.draggingNodeId.orEmpty(),
                                                position = modelPosition,
                                                extent = currentExtent
                                            )
                                        )
                                        gestureState.nodePositions[gestureState.draggingNodeId]
                                            ?.let { position ->
                                                onNodePositionChanged(
                                                    gestureState.draggingNodeId.orEmpty(),
                                                    position
                                                )
                                            }
                                    } else if (!zoomed &&
                                        gesturePan != Offset.Zero &&
                                        movedDistance >= viewConfiguration.touchSlop
                                    ) {
                                        gestureState = reduceGraphGesture(
                                            gestureState,
                                            GraphGestureAction.PanBy(
                                                delta = GraphPoint(gesturePan.x, gesturePan.y),
                                                bounds = panBounds(gestureState.scale)
                                            )
                                        )
                                    }
                                    Unit
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            onInteractionChanged(false)
                        }

                        val wasDraggingNode = gestureState.draggingNodeId != null
                        if (!zoomed && !wasDraggingNode && movedDistance < viewConfiguration.touchSlop) {
                            val tapModel = screenToGraphModel(
                                position = lastPosition,
                                canvasSize = canvasSize,
                                scale = gestureState.scale,
                                pan = gestureState.pan
                            )
                            val hit = graphNodeAt(
                                snapshot = currentSnapshot,
                                point = tapModel,
                                semanticMode = currentSemanticMode,
                                canvasSize = canvasSize,
                                density = density.density,
                                scale = gestureState.scale
                            )
                            if (hit != null) {
                                lastEmptyTap = null
                                onSelectedNodeChange(hit.id)
                            } else {
                                val previous = lastEmptyTap
                                val isDoubleTap = previous != null &&
                                    first.uptimeMillis - previous.uptimeMillis <=
                                    viewConfiguration.doubleTapTimeoutMillis &&
                                    (lastPosition - previous.position).getDistance() <=
                                    with(density) { 32.dp.toPx() }
                                onSelectedNodeChange(null)
                                if (isDoubleTap) {
                                    fitCanvas()
                                    lastEmptyTap = null
                                } else {
                                    lastEmptyTap = GraphTapRecord(first.uptimeMillis, lastPosition)
                                }
                            }
                        }
                        gestureState = reduceGraphGesture(gestureState, GraphGestureAction.Release)
                    }
                } else Modifier.pointerInput(onRequestInteraction, viewConfiguration.touchSlop) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        var distance = 0f
                        do {
                            val event = awaitPointerEvent()
                            event.changes.firstOrNull { it.id == first.id }?.let { change ->
                                distance += (change.position - change.previousPosition).getDistance()
                            }
                        } while (event.changes.any { it.pressed })
                        if (distance < viewConfiguration.touchSlop) onRequestInteraction()
                    }
                })
        ) {
            drawGraphDotGrid()
            withTransform({
                translate(left = gestureState.pan.x, top = gestureState.pan.y)
                scale(
                    scaleX = gestureState.scale,
                    scaleY = gestureState.scale,
                    pivot = center
                )
            }) {
                drawGraphEdges(renderSnapshot, state.selectedNodeId, showLockedNodes)
                renderSnapshot.nodes.forEach { node ->
                    when (semanticMode) {
                        GraphSemanticMode.OverviewCircles -> drawCircleGraphNode(
                            node = node,
                            selected = node.id == state.selectedNodeId,
                            interactionActive = node.id == gestureState.interactionNodeId,
                            typeMultiplier = typeMultiplier
                        )
                        GraphSemanticMode.DetailCards -> drawCardGraphNode(
                            node = node,
                            selected = node.id == state.selectedNodeId,
                            interactionActive = node.id == gestureState.interactionNodeId,
                            scope = state.scope,
                            expandedSessionRoot = expandedSessionRoot,
                            typeMultiplier = typeMultiplier
                        )
                    }
                }
            }
        }

        if (showToolbar) {
            GraphToolbar(
                onCenter = ::fitCanvas,
                onZoomOut = {
                    gestureState = reduceGraphGesture(
                        gestureState,
                        GraphGestureAction.ZoomBy(1f / 1.22f, ::panBounds)
                    )
                },
                onZoomIn = {
                    gestureState = reduceGraphGesture(
                        gestureState,
                        GraphGestureAction.ZoomBy(1.22f, ::panBounds)
                    )
                },
                onFilter = onFilterClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            )
        }
    }
}

private data class GraphTapRecord(
    val uptimeMillis: Long,
    val position: Offset
)

private fun screenToGraphModel(
    position: Offset,
    canvasSize: IntSize,
    scale: Float,
    pan: GraphPoint
): GraphPoint {
    val width = canvasSize.width.toFloat().coerceAtLeast(1f)
    val height = canvasSize.height.toFloat().coerceAtLeast(1f)
    val safeScale = scale.coerceAtLeast(0.01f)
    return GraphPoint(
        x = (((position.x - pan.x - width / 2f) / safeScale) + width / 2f) / width,
        y = (((position.y - pan.y - height / 2f) / safeScale) + height / 2f) / height
    )
}

private fun graphNodeAt(
    snapshot: GraphRenderSnapshot,
    point: GraphPoint,
    semanticMode: GraphSemanticMode,
    canvasSize: IntSize,
    density: Float,
    scale: Float
): GraphLayoutNode? = graphHitTest(
    nodes = snapshot.nodes,
    target = point,
    semanticMode = semanticMode,
    viewportWidthPx = canvasSize.width.toFloat(),
    viewportHeightPx = canvasSize.height.toFloat(),
    density = density,
    scale = scale
)

private fun DrawScope.drawGraphDotGrid() {
    val dotColor = Color(0x28758F92)
    val step = 24.dp.toPx()
    var x = step / 2f
    while (x < size.width) {
        var y = step / 2f
        while (y < size.height) {
            drawCircle(dotColor, radius = 0.7.dp.toPx(), center = Offset(x, y))
            y += step
        }
        x += step
    }
}

private fun DrawScope.drawGraphEdges(
    snapshot: GraphRenderSnapshot,
    selectedNodeId: String?,
    showLockedNodes: Boolean
) {
    val nodeById = snapshot.nodes.associateBy { it.id }
    snapshot.edges.forEach { edge ->
        val from = nodeById[edge.fromNodeId] ?: return@forEach
        val to = nodeById[edge.toNodeId] ?: return@forEach
        val start = Offset(from.x * size.width, from.y * size.height)
        val end = Offset(to.x * size.width, to.y * size.height)
        val path = Path().apply {
            moveTo(start.x, start.y)
            val controlY = (start.y + end.y) / 2f
            cubicTo(start.x, controlY, end.x, controlY, end.x, end.y)
        }
        val selected = edge.fromNodeId == selectedNodeId || edge.toNodeId == selectedNodeId
        val locked = showLockedNodes && (from.isLocked || to.isLocked)
        drawPath(
            path = path,
            color = when {
                locked -> GraphLocked.copy(alpha = 0.38f)
                selected -> FormalColors.Primary.copy(alpha = 0.82f)
                else -> nodeColor(from).copy(alpha = 0.50f)
            },
            style = Stroke(
                width = if (selected) 1.8.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (locked) PathEffect.dashPathEffect(floatArrayOf(7f, 6f)) else null
            )
        )
    }
}

private fun DrawScope.drawCircleGraphNode(
    node: GraphLayoutNode,
    selected: Boolean,
    interactionActive: Boolean,
    typeMultiplier: Float
) {
    val center = Offset(node.x * size.width, node.y * size.height)
    val radius = node.radius * size.minDimension
    val color = nodeColor(node)
    if (interactionActive) {
        drawCircle(
            color = color.copy(alpha = 0.20f),
            radius = radius + 12.dp.toPx(),
            center = center
        )
    }
    if (selected) {
        drawCircle(
            color = FormalColors.Primary.copy(alpha = 0.16f),
            radius = radius + 8.dp.toPx(),
            center = center
        )
        drawCircle(
            color = FormalColors.Primary,
            radius = radius + 4.dp.toPx(),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }
    drawCircle(Color(0x18000000), radius = radius + 3.dp.toPx(), center = center + Offset(0f, 3.dp.toPx()))
    drawCircle(color.copy(alpha = if (node.isLocked) 0.30f else 0.95f), radius = radius, center = center)
    drawCircle(Color.White.copy(alpha = 0.48f), radius = radius * 0.32f, center = center - Offset(radius * 0.24f, radius * 0.26f))
    val label = compactLabel(node.label, 9)
    val bold = radius >= 14.dp.toPx()
    val insideTextSize = 9f
    val outsideTextSize = 8f
    val insideLabelWidth = measureGraphText(label, insideTextSize, typeMultiplier, bold)
    val wantsInside = radius >= 14.dp.toPx() && insideLabelWidth <= radius * 2f + 8.dp.toPx()
    val outsideLabelWidth = if (wantsInside) {
        insideLabelWidth
    } else {
        measureGraphText(label, outsideTextSize, typeMultiplier, bold = false)
    }
    val labelPlacement = graphOverviewLabelPlacement(
        wantsInside = wantsInside,
        centerX = center.x,
        radius = radius,
        labelWidth = outsideLabelWidth,
        viewportWidth = size.width,
        margin = 8.dp.toPx(),
        gap = 5.dp.toPx()
    )
    val labelX = when (labelPlacement) {
        GraphOverviewLabelPlacement.Inside -> center.x
        GraphOverviewLabelPlacement.Right -> center.x + radius + 5.dp.toPx()
        GraphOverviewLabelPlacement.Left -> center.x - radius - 5.dp.toPx()
    }
    drawGraphText(
        text = label,
        x = labelX,
        y = center.y + 3.dp.toPx(),
        color = if (labelPlacement == GraphOverviewLabelPlacement.Inside) Color(0xFF102036) else GraphInk,
        sizeSp = if (labelPlacement == GraphOverviewLabelPlacement.Inside) insideTextSize else outsideTextSize,
        typeMultiplier = typeMultiplier,
        centered = labelPlacement == GraphOverviewLabelPlacement.Inside,
        bold = bold && labelPlacement == GraphOverviewLabelPlacement.Inside,
        textAlign = when (labelPlacement) {
            GraphOverviewLabelPlacement.Inside -> Paint.Align.CENTER
            GraphOverviewLabelPlacement.Right -> Paint.Align.LEFT
            GraphOverviewLabelPlacement.Left -> Paint.Align.RIGHT
        }
    )
}

internal enum class GraphOverviewLabelPlacement { Inside, Right, Left }

internal fun graphOverviewLabelPlacement(
    wantsInside: Boolean,
    centerX: Float,
    radius: Float,
    labelWidth: Float,
    viewportWidth: Float,
    margin: Float,
    gap: Float
): GraphOverviewLabelPlacement {
    val insideFits = centerX - labelWidth / 2f >= margin &&
        centerX + labelWidth / 2f <= viewportWidth - margin
    if (wantsInside && insideFits) return GraphOverviewLabelPlacement.Inside

    val rightFits = centerX + radius + gap + labelWidth <= viewportWidth - margin
    return if (rightFits) GraphOverviewLabelPlacement.Right else GraphOverviewLabelPlacement.Left
}

private fun DrawScope.drawCardGraphNode(
    node: GraphLayoutNode,
    selected: Boolean,
    interactionActive: Boolean,
    scope: GraphScope,
    expandedSessionRoot: Boolean,
    typeMultiplier: Float
) {
    val center = Offset(node.x * size.width, node.y * size.height)
    val isRoot = node.kind == GraphNodeKind.Session
    val width = when {
        isRoot && scope == GraphScope.Session -> 104.dp.toPx()
        isRoot -> 104.dp.toPx()
        scope == GraphScope.Session && node.kind in setOf(
            GraphNodeKind.Person,
            GraphNodeKind.Requirement,
            GraphNodeKind.Source
        ) -> 115.dp.toPx()
        scope == GraphScope.Session -> 100.dp.toPx()
        else -> 96.dp.toPx()
    }
    val height = when {
        isRoot && scope == GraphScope.Session && expandedSessionRoot -> 116.dp.toPx()
        isRoot && scope == GraphScope.Session -> 64.dp.toPx()
        isRoot -> 64.dp.toPx()
        scope == GraphScope.Session && node.kind in setOf(
            GraphNodeKind.Person,
            GraphNodeKind.Requirement,
            GraphNodeKind.Source
        ) -> 58.dp.toPx()
        scope == GraphScope.Session -> 40.dp.toPx()
        else -> 48.dp.toPx()
    }
    val rect = Rect(
        left = center.x - width / 2f,
        top = center.y - height / 2f,
        right = center.x + width / 2f,
        bottom = center.y + height / 2f
    )
    val radius = 8.dp.toPx()
    val baseColor = when {
        node.isLocked -> GraphLockedCard
        selected -> FormalColors.Primary
        isRoot -> Color(0xFFFDFFFF)
        else -> Color(0xFDFBFDFF)
    }
    if (interactionActive) {
        drawRoundRect(
            color = nodeColor(node).copy(alpha = 0.18f),
            topLeft = Offset(rect.left - 8.dp.toPx(), rect.top - 8.dp.toPx()),
            size = Size(rect.width + 16.dp.toPx(), rect.height + 16.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius + 7.dp.toPx())
        )
    }
    drawRoundRect(
        color = Color(0x160D2748),
        topLeft = Offset(rect.left, rect.top + 4.dp.toPx()),
        size = Size(rect.width, rect.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
    )
    if (selected) {
        drawRoundRect(
            color = FormalColors.Primary.copy(alpha = 0.17f),
            topLeft = Offset(rect.left - 5.dp.toPx(), rect.top - 5.dp.toPx()),
            size = Size(rect.width + 10.dp.toPx(), rect.height + 10.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius + 4.dp.toPx())
        )
    }
    drawRoundRect(
        color = baseColor,
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
    )
    drawRoundRect(
        color = when {
            selected -> FormalColors.Primary
            node.isLocked -> GraphLocked.copy(alpha = 0.54f)
            else -> nodeColor(node).copy(alpha = 0.82f)
        },
        topLeft = rect.topLeft,
        size = rect.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        style = Stroke(width = if (selected) 1.5.dp.toPx() else 1.dp.toPx())
    )
    val textColor = when {
        selected -> Color.White
        node.isLocked -> Color(0xFF8A95A8)
        else -> GraphInk
    }
    val statusColor = if (selected) Color(0xFFDCE7FF) else GraphMuted
    if (isRoot && expandedSessionRoot) {
        val iconSize = 34.dp.toPx()
        drawRoundRect(
            color = nodeColor(node).copy(alpha = 0.92f),
            topLeft = Offset(center.x - iconSize / 2f, rect.top + 10.dp.toPx()),
            size = Size(iconSize, iconSize),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx())
        )
        drawGraphText(
            text = if (node.label.contains("Python", ignoreCase = true)) "Py" else "学",
            x = center.x,
            y = rect.top + 32.dp.toPx(),
            color = Color.White,
            sizeSp = 9f,
            typeMultiplier = typeMultiplier,
            centered = true,
            bold = true
        )
    }
    val labelY = when {
        isRoot && expandedSessionRoot -> rect.top + 66.dp.toPx()
        isRoot -> rect.top + 27.dp.toPx()
        else -> rect.top + 17.dp.toPx()
    }
    drawGraphText(
        text = compactLabel(node.label, if (isRoot) 9 else 10),
        x = center.x,
        y = labelY,
        color = textColor,
        sizeSp = if (isRoot) 10f else 9f,
        typeMultiplier = typeMultiplier,
        centered = true,
        bold = isRoot || selected
    )
    if (height >= 46.dp.toPx()) {
        drawGraphText(
            text = node.statusLabel,
            x = center.x,
            y = rect.bottom - 10.dp.toPx(),
            color = statusColor,
            sizeSp = 7f,
            typeMultiplier = typeMultiplier,
            centered = true
        )
    }
    val statusDot = Offset(rect.right - 11.dp.toPx(), rect.top + 11.dp.toPx())
    if (node.isLocked) {
        drawLock(statusDot, GraphLocked)
    } else {
        drawCircle(
            color = statusColor(node),
            radius = 4.dp.toPx(),
            center = statusDot,
            style = if (node.status == GraphNodeStatus.Active) Stroke(1.5.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill
        )
    }
}

private fun DrawScope.drawLock(center: Offset, color: Color) {
    val bodyWidth = 7.dp.toPx()
    val bodyHeight = 6.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - bodyWidth / 2f, center.y),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
    )
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(center.x - 2.5.dp.toPx(), center.y - 4.dp.toPx()),
        size = Size(5.dp.toPx(), 7.dp.toPx()),
        style = Stroke(1.2.dp.toPx())
    )
}

private fun DrawScope.drawGraphText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    sizeSp: Float,
    typeMultiplier: Float,
    centered: Boolean,
    bold: Boolean = false,
    textAlign: Paint.Align = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = sizeSp.sp.toPx() * typeMultiplier
        typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        this.textAlign = textAlign
    }
    drawIntoCanvas { canvas -> canvas.nativeCanvas.drawText(text, x, y, paint) }
}

private fun DrawScope.measureGraphText(
    text: String,
    sizeSp: Float,
    typeMultiplier: Float,
    bold: Boolean
): Float = Paint(Paint.ANTI_ALIAS_FLAG).run {
    textSize = sizeSp.sp.toPx() * typeMultiplier
    typeface = if (bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    measureText(text)
}

private fun compactLabel(value: String, maxCharacters: Int): String = when {
    value.length <= maxCharacters -> value
    maxCharacters <= 1 -> value.take(maxCharacters)
    else -> value.take(maxCharacters - 1) + "…"
}

@Composable
private fun GraphToolbar(
    onCenter: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onFilter: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(260.dp)
            .height(52.dp),
        color = Color(0xF7FAFBFE),
        contentColor = GraphInk,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, Color(0x1F576B94)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GraphToolbarButton(GraphToolbarGlyph.Target, selected = true, onClick = onCenter)
            GraphToolbarButton(GraphToolbarGlyph.Minus, onClick = onZoomOut)
            GraphToolbarButton(GraphToolbarGlyph.Plus, onClick = onZoomIn)
            GraphToolbarButton(GraphToolbarGlyph.Filter, onClick = onFilter)
        }
    }
}

private enum class GraphToolbarGlyph(
    val description: String,
    val tag: String
) {
    Target("适应图谱", "graph-fit"),
    Minus("缩小", "graph-zoom-out"),
    Plus("放大", "graph-zoom-in"),
    Filter("筛选", "graph-filter")
}

@Composable
private fun GraphToolbarButton(
    glyph: GraphToolbarGlyph,
    selected: Boolean = false,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(if (selected) FormalColors.PrimarySoft else Color.Transparent, CircleShape)
            .semantics { contentDescription = glyph.description }
            .testTag(glyph.tag)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(22.dp)) {
            val color = if (selected) FormalColors.Primary else Color(0xFF36527D)
            val stroke = 1.6.dp.toPx()
            when (glyph) {
                GraphToolbarGlyph.Target -> {
                    drawCircle(color, size.minDimension * 0.27f, center, style = Stroke(stroke))
                    drawCircle(color, 2.dp.toPx(), center)
                    drawLine(color, Offset(center.x, 0f), Offset(center.x, size.height * 0.23f), stroke)
                    drawLine(color, Offset(center.x, size.height * 0.77f), Offset(center.x, size.height), stroke)
                    drawLine(color, Offset(0f, center.y), Offset(size.width * 0.23f, center.y), stroke)
                    drawLine(color, Offset(size.width * 0.77f, center.y), Offset(size.width, center.y), stroke)
                }
                GraphToolbarGlyph.Minus -> drawLine(
                    color, Offset(size.width * 0.28f, center.y), Offset(size.width * 0.72f, center.y), stroke, StrokeCap.Round
                )
                GraphToolbarGlyph.Plus -> {
                    drawLine(color, Offset(size.width * 0.25f, center.y), Offset(size.width * 0.75f, center.y), stroke, StrokeCap.Round)
                    drawLine(color, Offset(center.x, size.height * 0.25f), Offset(center.x, size.height * 0.75f), stroke, StrokeCap.Round)
                }
                GraphToolbarGlyph.Filter -> {
                    listOf(0.30f to 0.62f, 0.50f to 0.40f, 0.70f to 0.58f).forEach { (y, dotX) ->
                        drawLine(color, Offset(size.width * 0.20f, size.height * y), Offset(size.width * 0.80f, size.height * y), stroke, StrokeCap.Round)
                        drawCircle(color, 1.7.dp.toPx(), Offset(size.width * dotX, size.height * y))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyGraphPanel(state: KnowledgeGraphUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(GraphCanvasBackground),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) { drawGraphDotGrid() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = state.title,
                color = GraphInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = state.summary,
                color = GraphMuted,
                fontSize = 10.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun GraphNodeDetail(
    node: GraphLayoutNode,
    relations: List<GraphLayoutEdge>,
    editable: Boolean,
    onNodeLabelSave: (GraphLayoutNode, String) -> Unit,
    onNodeReviewAction: (GraphLayoutNode, GraphNodeReviewAction) -> Unit,
    onOpenChatEvidence: (GraphLayoutNode) -> Unit,
    onOpenSourceEvidence: (GraphLayoutNode) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftLabel by remember(node.id, node.label) { mutableStateOf(node.label) }
    val canSaveLabel = draftLabel.trim().isNotEmpty() && draftLabel.trim() != node.label
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("graph-node-detail"),
        color = Color(0xFFFDFFFF),
        contentColor = GraphInk,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(node.label, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${node.kindLabel} · ${node.statusLabel}",
                        color = GraphMuted,
                        fontSize = 9.sp,
                        lineHeight = 15.sp
                    )
                }
                TextButton(onClick = onClearSelection) { Text("关闭", fontSize = 10.sp) }
            }
            node.reviewCards.forEach { card ->
                Text(card.body, color = GraphMuted, fontSize = 10.sp, lineHeight = 17.sp)
            }
            if (relations.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    relations.take(6).forEach { relation ->
                        Surface(
                            color = FormalColors.PrimarySoft,
                            contentColor = FormalColors.Primary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                relation.relation.ifBlank { "关联节点" },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
            if (editable && !node.isLocked) {
                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = { draftLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("节点名称") }
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        enabled = canSaveLabel,
                        onClick = { onNodeLabelSave(node, draftLabel.trim()) },
                        colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("保存节点")
                    }
                    node.reviewActions.forEach { action ->
                        TextButton(onClick = { onNodeReviewAction(node, action) }) {
                            Text(action.label)
                        }
                    }
                }
            }
            val evidenceActions = node.evidenceActions()
            if (evidenceActions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    evidenceActions.forEach { action ->
                        val tag = when (action.destination) {
                            GraphEvidenceDestination.Chat -> "graph-evidence-chat-${action.targetId}"
                            GraphEvidenceDestination.Source -> "graph-evidence-source-${action.targetId}"
                        }
                        TextButton(
                            onClick = {
                                when (action.destination) {
                                    GraphEvidenceDestination.Chat -> onOpenChatEvidence(node)
                                    GraphEvidenceDestination.Source -> onOpenSourceEvidence(node)
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(tag)
                        ) {
                            Text(action.label)
                        }
                    }
                }
            }
        }
    }
}

private fun nodeColor(node: GraphLayoutNode): Color = when (node.kind) {
    GraphNodeKind.Concept -> Color(0xFF63BFE3)
    GraphNodeKind.Requirement -> Color(0xFFE3AF42)
    GraphNodeKind.Source -> Color(0xFF4FC2AF)
    GraphNodeKind.Session -> Color(0xFF7AA8ED)
    GraphNodeKind.Person -> Color(0xFF9B7AD1)
    GraphNodeKind.Other -> Color(0xFF8C73C5)
}

private fun statusColor(node: GraphLayoutNode): Color = when (node.status) {
    GraphNodeStatus.Active -> FormalColors.Primary
    GraphNodeStatus.NeedsReview -> FormalColors.Warning
    GraphNodeStatus.Approved -> FormalColors.Success
    GraphNodeStatus.Hidden -> GraphLocked
    GraphNodeStatus.Archived -> GraphMuted
}

private val GraphCanvasBackground = Color.White
private val GraphInk = Color(0xFF141C29)
private val GraphMuted = Color(0xFF738099)
private val GraphLocked = Color(0xFF8794A9)
private val GraphLockedCard = Color(0xE9EEF3F9)
