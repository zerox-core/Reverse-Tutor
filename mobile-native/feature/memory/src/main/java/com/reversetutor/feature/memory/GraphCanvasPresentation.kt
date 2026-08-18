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
    const val BackgroundArgb = 0xFFF7F8FAL
    const val EdgeArgb = 0xFF64748BL
    const val EdgeAlpha = 0.16f
    const val EdgeHighlightAlpha = 0.70f
    const val SelectionHaloAlpha = 0.25f
    const val NodeRadiusMin = 2.5f
    const val NodeRadiusMax = 18f
    const val SemanticZoomHubOnly = 0.4f
    const val SemanticZoomHubSecondary = 0.7f
    const val SemanticZoomAll = 0.9f
    const val SemanticZoomLabel = 1.6f
}

fun graphNodeStyle(
    node: GraphLayoutNode,
    selected: Boolean
): GraphNodeVisualStyle =
    GraphNodeVisualStyle(
        fillArgb = graphKindFillArgb(node.kind),
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

fun graphKindFillArgb(kind: GraphNodeKind): Long = when (kind) {
    GraphNodeKind.Concept -> 0xFF3B82F6L
    GraphNodeKind.Requirement -> 0xFFFF6B61L
    GraphNodeKind.Source -> 0xFF84B547L
    GraphNodeKind.Session -> 0xFF8B5CF6L
    GraphNodeKind.Person -> 0xFFF59E0BL
    GraphNodeKind.Other -> 0xFF22B8CFL
}

fun graphDisplayImportance(node: GraphLayoutNode, relatedEdgeCount: Int): Int =
    if (node.isLocked) 0 else (relatedEdgeCount.coerceAtLeast(0) * 25).coerceIn(0, 100)

private fun GraphNodeStatus.canvasStrokeArgb(): Long = when (this) {
    GraphNodeStatus.Active -> 0xFF2E5BFF
    GraphNodeStatus.NeedsReview -> 0xFFCA8A04
    GraphNodeStatus.Approved -> 0xFF15803D
    GraphNodeStatus.Hidden -> 0xFF8794A9
    GraphNodeStatus.Archived -> 0xFF738099
}
