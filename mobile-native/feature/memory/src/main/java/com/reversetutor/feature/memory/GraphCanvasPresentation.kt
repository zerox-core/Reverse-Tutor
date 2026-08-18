package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus

data class GraphNodeVisualStyle(
    val fillArgb: Long,
    val strokeArgb: Long,
    val fillAlpha: Float,
    val strokeWidthDp: Float,
    val radiusMultiplier: Float,
    val semanticKind: GraphNodeKind,
    val semanticStatus: GraphNodeStatus
)

object GraphCanvasPresentation {
    const val ControlTouchTargetDp = 48f
}

fun graphNodeStyle(
    node: GraphLayoutNode,
    selected: Boolean
): GraphNodeVisualStyle =
    GraphNodeVisualStyle(
        fillArgb = node.kind.canvasFillArgb(),
        strokeArgb = node.status.canvasStrokeArgb(),
        fillAlpha = when {
            node.isLocked -> 0.30f
            selected -> 1f
            else -> 0.82f
        },
        strokeWidthDp = if (selected) 3f else 1.5f,
        radiusMultiplier = if (selected) 1.16f else 1f,
        semanticKind = node.kind,
        semanticStatus = node.status
    )

private fun GraphNodeKind.canvasFillArgb(): Long = when (this) {
    GraphNodeKind.Concept -> 0xFF63BFE3
    GraphNodeKind.Requirement -> 0xFFE3AF42
    GraphNodeKind.Source -> 0xFF4FC2AF
    GraphNodeKind.Session -> 0xFF7AA8ED
    GraphNodeKind.Person -> 0xFF9B7AD1
    GraphNodeKind.Other -> 0xFF8C73C5
}

private fun GraphNodeStatus.canvasStrokeArgb(): Long = when (this) {
    GraphNodeStatus.Active -> 0xFF2E5BFF
    GraphNodeStatus.NeedsReview -> 0xFFCA8A04
    GraphNodeStatus.Approved -> 0xFF15803D
    GraphNodeStatus.Hidden -> 0xFF8794A9
    GraphNodeStatus.Archived -> 0xFF738099
}

