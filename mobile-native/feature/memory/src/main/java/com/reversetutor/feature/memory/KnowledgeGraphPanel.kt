package com.reversetutor.feature.memory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.reversetutor.core.model.GraphNodeKind

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun KnowledgeGraphPanel(
    state: KnowledgeGraphUiState,
    onSelectedNodeChange: (String?) -> Unit,
    editable: Boolean = true,
    onNodeLabelSave: (GraphLayoutNode, String) -> Unit = { _, _ -> },
    onNodeReviewAction: (GraphLayoutNode, GraphNodeReviewAction) -> Unit = { _, _ -> },
    onOpenChatEvidence: (GraphLayoutNode) -> Unit = {},
    onOpenSourceEvidence: (GraphLayoutNode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val selectedNode = state.selectedNode
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(text = state.summary, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = state.status.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GraphMetric(label = "Nodes", value = state.nodes.size.toString())
                GraphMetric(label = "Edges", value = state.edgeCount.toString())
                if (state.invalidEdgeCount > 0) {
                    GraphMetric(label = "Invalid", value = state.invalidEdgeCount.toString())
                }
            }

            if (state.nodes.isEmpty()) {
                EmptyGraphPanel(state = state)
            } else {
                NativeGraphCanvas(
                    state = state,
                    onSelectedNodeChange = onSelectedNodeChange
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.nodes.forEach { node ->
                        val selected = node.id == state.selectedNodeId
                        Surface(
                            modifier = Modifier
                                .testTag("graph-node-${node.id}")
                                .clickable { onSelectedNodeChange(if (selected) null else node.id) },
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = node.label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
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
                        onClearSelection = { onSelectedNodeChange(null) }
                    )
                } else {
                    Text(
                        text = "Select a node to inspect relationships and evidence.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun NativeGraphCanvas(
    state: KnowledgeGraphUiState,
    onSelectedNodeChange: (String?) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val canvasBackground = MaterialTheme.colorScheme.surface
    val edgeColor = MaterialTheme.colorScheme.outline
    val selectedColor = MaterialTheme.colorScheme.primary
    val nodeColors = graphNodePalette()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(canvasBackground, RoundedCornerShape(8.dp))
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .onSizeChanged { canvasSize = it }
                .testTag("knowledge-graph-canvas")
                .semantics {
                    contentDescription = "Knowledge graph canvas"
                }
                .pointerInput(state.nodes, scale, pan) {
                    detectTapGestures { tap ->
                        val width = canvasSize.width.toFloat().coerceAtLeast(1f)
                        val height = canvasSize.height.toFloat().coerceAtLeast(1f)
                        val modelX = ((tap.x - pan.x) / scale) / width
                        val modelY = ((tap.y - pan.y) / scale) / height
                        val hitNode = state.hitTest(modelX, modelY)
                        onSelectedNodeChange(hitNode?.id)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                        scale = (scale * gestureZoom).coerceIn(0.65f, 3.0f)
                        pan += gesturePan
                    }
                }
        ) {
            withTransform({
                translate(left = pan.x, top = pan.y)
                scale(scaleX = scale, scaleY = scale)
            }) {
                val nodeById = state.nodes.associateBy { it.id }
                state.visibleEdges.forEach { edge ->
                    val from = nodeById[edge.fromNodeId]
                    val to = nodeById[edge.toNodeId]
                    if (from != null && to != null) {
                        drawLine(
                            color = edgeColor,
                            start = Offset(from.x * size.width, from.y * size.height),
                            end = Offset(to.x * size.width, to.y * size.height),
                            strokeWidth = 3f
                        )
                    }
                }
                state.nodes.forEach { node ->
                    val center = Offset(node.x * size.width, node.y * size.height)
                    val radiusPx = node.radius * minOf(size.width, size.height)
                    val selected = node.id == state.selectedNodeId
                    drawCircle(
                        color = nodeColors[node.kind] ?: nodeColors.getValue(GraphNodeKind.Other),
                        radius = radiusPx,
                        center = center
                    )
                    drawCircle(
                        color = if (selected) selectedColor else edgeColor,
                        radius = radiusPx,
                        center = center,
                        style = Stroke(width = if (selected) 6f else 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun graphNodePalette(): Map<GraphNodeKind, Color> =
    mapOf(
        GraphNodeKind.Concept to MaterialTheme.colorScheme.primaryContainer,
        GraphNodeKind.Requirement to MaterialTheme.colorScheme.secondaryContainer,
        GraphNodeKind.Source to MaterialTheme.colorScheme.tertiaryContainer,
        GraphNodeKind.Session to MaterialTheme.colorScheme.inversePrimary,
        GraphNodeKind.Person to MaterialTheme.colorScheme.errorContainer,
        GraphNodeKind.Other to MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.28f)
    )

@Composable
private fun GraphMetric(
    label: String,
    value: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun EmptyGraphPanel(state: KnowledgeGraphUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Native graph canvas",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(text = state.summary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun GraphNodeDetail(
    node: GraphLayoutNode,
    relations: List<GraphLayoutEdge>,
    editable: Boolean,
    onNodeLabelSave: (GraphLayoutNode, String) -> Unit,
    onNodeReviewAction: (GraphLayoutNode, GraphNodeReviewAction) -> Unit,
    onOpenChatEvidence: (GraphLayoutNode) -> Unit,
    onOpenSourceEvidence: (GraphLayoutNode) -> Unit,
    onClearSelection: () -> Unit
) {
    var draftLabel by remember(node.id, node.label) { mutableStateOf(node.label) }
    val canSaveLabel = draftLabel.trim().isNotEmpty() && draftLabel.trim() != node.label
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("graph-node-detail"),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Node detail",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = node.label,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(onClick = onClearSelection) {
                    Text("Clear")
                }
            }
            Text(text = "Kind: ${node.kindLabel}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Status: ${node.statusLabel}", style = MaterialTheme.typography.bodyMedium)
            node.sourceMemoryId?.let {
                Text(text = "Evidence: $it", style = MaterialTheme.typography.bodyMedium)
            }
            if (node.reviewCards.isNotEmpty()) {
                Text(
                    text = "Review cards",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                node.reviewCards.forEach { card ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = card.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(text = card.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (editable) {
                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = { draftLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Node label") }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = canSaveLabel,
                        onClick = { onNodeLabelSave(node, draftLabel) }
                    ) {
                        Text("Save node")
                    }
                    node.reviewActions.forEach { action ->
                        TextButton(onClick = { onNodeReviewAction(node, action) }) {
                            Text(action.label)
                        }
                    }
                }
            } else {
                Text(
                    text = "Read-only review",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (node.sourceMessageId != null) {
                    TextButton(onClick = { onOpenChatEvidence(node) }) {
                        Text("Open chat evidence")
                    }
                }
                if (node.sourceId != null) {
                    TextButton(onClick = { onOpenSourceEvidence(node) }) {
                        Text("Open source evidence")
                    }
                }
            }
            Text(
                text = "Relationships",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (relations.isEmpty()) {
                Text(text = "No visible relationships for this node.", style = MaterialTheme.typography.bodyMedium)
            } else {
                relations.forEach { relation ->
                    Text(
                        text = "${relation.fromNodeId} ${relation.relation} ${relation.toNodeId}",
                        modifier = Modifier.heightIn(min = 24.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Button(onClick = onClearSelection) {
                Text("Close detail")
            }
        }
    }
}
