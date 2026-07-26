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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    modifier: Modifier = Modifier
) {
    var showLockedNodes by remember(state.scope) { mutableStateOf(false) }
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
                    TextButton(onClick = { showLockedNodes = !showLockedNodes }) {
                        Text(if (showLockedNodes) "隐藏未解锁" else "显示未解锁", fontSize = 10.sp)
                    }
                }
            }
            if (state.allNodeCount == 0) {
                EmptyGraphPanel(state)
            } else {
                FormalGraphCanvas(
                    state = state,
                    showLockedNodes = showLockedNodes,
                    onSelectedNodeChange = onSelectedNodeChange,
                    onInteractionChanged = onGraphInteractionChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (state.scope == GraphScope.Session) 520.dp else 430.dp)
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
    onViewportChanged: (GraphViewportState) -> Unit = {}
) {
    var scale by remember(state.scope, initialScale) { mutableFloatStateOf(initialScale.coerceIn(0.72f, 3.2f)) }
    var pan by remember(state.scope) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var inertiaJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val typeMultiplier = LocalFormalTypeScale.current.multiplier
    val density = LocalDensity.current
    val renderSnapshot = state.renderSnapshot(showLockedNodes)
    val semanticMode = graphSemanticMode(state.scope, scale)

    LaunchedEffect(scale, pan, semanticMode) {
        onViewportChanged(GraphViewportState(scale, pan, semanticMode))
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
                    state.allNodes,
                    showLockedNodes,
                    onSelectedNodeChange,
                    onInteractionChanged
                ) {
                    awaitEachGesture {
                        inertiaJob?.cancel()
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(first.uptimeMillis, first.position)
                        val downPosition = first.position
                        var lastPosition = first.position
                        var movedDistance = 0f
                        var zoomed = false
                        onInteractionChanged(true)
                        try {
                            do {
                                val event = awaitPointerEvent()
                                val gesturePan = event.calculatePan()
                                val gestureZoom = event.calculateZoom()
                                if (gestureZoom.isFinite() && abs(gestureZoom - 1f) > 0.001f) {
                                    scale = (scale * gestureZoom).coerceIn(0.72f, 3.2f)
                                    zoomed = true
                                }
                                if (gesturePan != Offset.Zero) {
                                    movedDistance += gesturePan.getDistance()
                                    pan = clampPan(
                                        value = pan + gesturePan,
                                        canvasSize = canvasSize,
                                        scale = scale
                                    )
                                }
                                event.changes.firstOrNull()?.let { change ->
                                    lastPosition = change.position
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            onInteractionChanged(false)
                        }

                        if (!zoomed && movedDistance < with(density) { 8.dp.toPx() }) {
                            val width = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val height = canvasSize.height.toFloat().coerceAtLeast(1f)
                            val tap = if ((lastPosition - downPosition).getDistance() < with(density) { 8.dp.toPx() }) {
                                lastPosition
                            } else {
                                downPosition
                            }
                            val modelX = ((tap.x - pan.x) / scale) / width
                            val modelY = ((tap.y - pan.y) / scale) / height
                            val hit = state.hitTest(
                                x = modelX,
                                y = modelY,
                                semanticMode = semanticMode,
                                includeLockedNodes = showLockedNodes
                            )
                            onSelectedNodeChange(hit?.id)
                        } else if (!zoomed) {
                            val velocity = tracker.calculateVelocity()
                            inertiaJob = coroutineScope.launch {
                                var velocityX = velocity.x.coerceIn(-2800f, 2800f)
                                var velocityY = velocity.y.coerceIn(-2800f, 2800f)
                                repeat(36) {
                                    if (abs(velocityX) + abs(velocityY) < 12f) return@launch
                                    withFrameNanos { }
                                    pan = clampPan(
                                        value = pan + Offset(velocityX / 60f, velocityY / 60f),
                                        canvasSize = canvasSize,
                                        scale = scale
                                    )
                                    velocityX *= 0.88f
                                    velocityY *= 0.88f
                                }
                            }
                        }
                    }
                } else Modifier.pointerInput(onRequestInteraction) {
                    detectTapGestures(onTap = { onRequestInteraction() })
                })
        ) {
            drawGraphDotGrid()
            withTransform({
                translate(left = pan.x, top = pan.y)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                drawGraphEdges(renderSnapshot, state.selectedNodeId, showLockedNodes)
                renderSnapshot.nodes.forEach { node ->
                    when (semanticMode) {
                        GraphSemanticMode.OverviewCircles -> drawCircleGraphNode(
                            node = node,
                            selected = node.id == state.selectedNodeId,
                            typeMultiplier = typeMultiplier
                        )
                        GraphSemanticMode.DetailCards -> drawCardGraphNode(
                            node = node,
                            selected = node.id == state.selectedNodeId,
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
                onCenter = {
                    inertiaJob?.cancel()
                    pan = Offset.Zero
                    scale = initialScale.coerceIn(0.72f, 3.2f)
                },
                onZoomOut = { scale = (scale / 1.22f).coerceIn(0.72f, 3.2f) },
                onZoomIn = { scale = (scale * 1.22f).coerceIn(0.72f, 3.2f) },
                onFilter = onFilterClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
            )
        }
    }
}

private fun clampPan(
    value: Offset,
    canvasSize: IntSize,
    scale: Float
): Offset {
    val horizontalLimit = canvasSize.width * (0.34f + (scale - 1f).coerceAtLeast(0f) * 0.62f)
    val verticalLimit = canvasSize.height * (0.30f + (scale - 1f).coerceAtLeast(0f) * 0.62f)
    return Offset(
        x = value.x.coerceIn(-horizontalLimit, horizontalLimit),
        y = value.y.coerceIn(-verticalLimit, verticalLimit)
    )
}

private fun DrawScope.drawGraphDotGrid() {
    val dotColor = Color(0x244A6C9C)
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
    typeMultiplier: Float
) {
    val center = Offset(node.x * size.width, node.y * size.height)
    val radius = node.radius * size.minDimension
    val color = nodeColor(node)
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

private enum class GraphToolbarGlyph { Target, Minus, Plus, Filter }

@Composable
private fun GraphToolbarButton(
    glyph: GraphToolbarGlyph,
    selected: Boolean = false,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(if (selected) FormalColors.PrimarySoft else Color.Transparent, CircleShape)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (node.sourceMessageId != null) {
                    TextButton(onClick = { onOpenChatEvidence(node) }) { Text("查看会话引用") }
                }
                if (node.sourceId != null) {
                    TextButton(onClick = { onOpenSourceEvidence(node) }) { Text("查看资料引用") }
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

private val GraphCanvasBackground = Color(0xFFF2F6FC)
private val GraphInk = Color(0xFF141C29)
private val GraphMuted = Color(0xFF738099)
private val GraphLocked = Color(0xFF8794A9)
private val GraphLockedCard = Color(0xE9EEF3F9)
