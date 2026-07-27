package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphInteractionPolicyTest {
    @Test
    fun onlySessionGraphsExposeProgressAndFullRouteLayers() {
        assertEquals(
            listOf("当前进度", "全部路线"),
            graphLayersFor(GraphScope.Session).map { it.label }
        )
        assertTrue(graphLayersFor(GraphScope.Global).isEmpty())
        assertEquals(false, GraphLayer.CurrentProgress.showLockedNodes)
        assertEquals(true, GraphLayer.FullRoute.showLockedNodes)
    }

    @Test
    fun extentUsesNodeDistributionAndCountInsteadOfViewportDimensions() {
        val compact = graphCanvasExtent(
            nodes = listOf(node("a", 0.45f, 0.45f), node("b", 0.55f, 0.55f)),
            semanticMode = GraphSemanticMode.OverviewCircles
        )
        val distributed = graphCanvasExtent(
            nodes = listOf(node("a", 0.10f, 0.15f), node("b", 0.90f, 0.85f)),
            semanticMode = GraphSemanticMode.OverviewCircles
        )
        val denser = graphCanvasExtent(
            nodes = (0 until 16).map { index ->
                node("node-$index", 0.45f + index % 4 * 0.03f, 0.45f + index / 4 * 0.03f)
            },
            semanticMode = GraphSemanticMode.OverviewCircles
        )

        assertTrue(distributed.width > compact.width)
        assertTrue(distributed.height > compact.height)
        assertTrue(denser.width > compact.width)
        assertTrue(denser.height > compact.height)
    }

    @Test
    fun panBoundsGrowForDistributedContentAndClampTheReducer() {
        val compact = GraphCanvasExtent(0.30f, 0.70f, 0.30f, 0.70f, nodeCount = 2)
        val distributed = GraphCanvasExtent(-0.20f, 1.20f, -0.10f, 1.10f, nodeCount = 20)
        val compactBounds = graphPanBounds(compact, 400f, 800f, scale = 1f)
        val distributedBounds = graphPanBounds(distributed, 400f, 800f, scale = 1f)

        assertTrue(distributedBounds.maxX - distributedBounds.minX > compactBounds.maxX - compactBounds.minX)
        assertTrue(distributedBounds.maxY - distributedBounds.minY > compactBounds.maxY - compactBounds.minY)

        val reduced = reduceGraphGesture(
            GraphGestureState(),
            GraphGestureAction.PanBy(GraphPoint(10_000f, -10_000f), distributedBounds)
        )
        assertEquals(distributedBounds.maxX, reduced.pan.x)
        assertEquals(distributedBounds.minY, reduced.pan.y)
        assertEquals(GraphGesturePhase.Panning, reduced.phase)
    }

    @Test
    fun gestureReducerSeparatesPressPanZoomNodeDragAndFit() {
        val extent = GraphCanvasExtent(0f, 1f, 0f, 1f, nodeCount = 4)
        val bounds = graphPanBounds(extent, 400f, 800f, scale = 1f)
        var state = reduceGraphGesture(GraphGestureState(), GraphGestureAction.PressNode("node-a"))
        assertEquals(GraphGesturePhase.Pressed, state.phase)
        assertEquals("node-a", state.interactionNodeId)

        state = reduceGraphGesture(state, GraphGestureAction.BeginNodeDrag("node-a"))
        state = reduceGraphGesture(
            state,
            GraphGestureAction.DragNodeTo("node-a", GraphPoint(0.75f, 0.65f), extent)
        )
        assertEquals(GraphGesturePhase.DraggingNode, state.phase)
        assertEquals(GraphPoint(0.75f, 0.65f), state.nodePositions["node-a"])

        state = reduceGraphGesture(state, GraphGestureAction.Release)
        state = reduceGraphGesture(
            state,
            GraphGestureAction.ZoomBy(1.5f) { bounds }
        )
        assertEquals(GraphGesturePhase.Scaling, state.phase)
        assertEquals(1.5f, state.scale)

        val fit = graphFitTransform(extent, 400f, 800f)
        state = reduceGraphGesture(state, GraphGestureAction.Fit(fit))
        assertEquals(GraphGesturePhase.Idle, state.phase)
        assertEquals(fit.scale, state.scale)
        assertEquals(fit.pan, state.pan)
        assertEquals(GraphPoint(0.75f, 0.65f), state.nodePositions["node-a"])
    }

    @Test
    fun overviewHitTargetUsesDensityAware44DpMinimumAtPhoneAndNarrowWidths() {
        val density = 3f
        listOf(390f, 320f).forEach { widthDp ->
            listOf(0.72f, 1f, 1.5f).forEach { scale ->
                val widthPx = widthDp * density
                val heightPx = 720f * density
                val target = node("target", 0.50f, 0.50f).copy(radius = 0.014f)
                val inside = GraphPoint(
                    x = target.x + (21.9f * density) / (widthPx * scale),
                    y = target.y
                )
                val outside = GraphPoint(
                    x = target.x + (22.1f * density) / (widthPx * scale),
                    y = target.y
                )

                assertEquals(
                    "target",
                    graphHitTest(
                        nodes = listOf(target),
                        target = inside,
                        semanticMode = GraphSemanticMode.OverviewCircles,
                        viewportWidthPx = widthPx,
                        viewportHeightPx = heightPx,
                        density = density,
                        scale = scale
                    )?.id
                )
                assertEquals(
                    null,
                    graphHitTest(
                        nodes = listOf(target),
                        target = outside,
                        semanticMode = GraphSemanticMode.OverviewCircles,
                        viewportWidthPx = widthPx,
                        viewportHeightPx = heightPx,
                        density = density,
                        scale = scale
                    )
                )
            }
        }
    }

    @Test
    fun overlappingMinimumTargetsChooseNearestThenStableIdRegardlessOfInputOrder() {
        val density = 2f
        val widthPx = 390f * density
        val heightPx = 720f * density
        val left = node("alpha", 0.46f, 0.50f).copy(radius = 0.014f)
        val right = node("beta", 0.54f, 0.50f).copy(radius = 0.014f)
        val overlapPoint = GraphPoint(0.50f, 0.50f)

        val forward = graphHitTest(
            nodes = listOf(left, right),
            target = overlapPoint,
            semanticMode = GraphSemanticMode.OverviewCircles,
            viewportWidthPx = widthPx,
            viewportHeightPx = heightPx,
            density = density
        )
        val reversed = graphHitTest(
            nodes = listOf(right, left),
            target = overlapPoint,
            semanticMode = GraphSemanticMode.OverviewCircles,
            viewportWidthPx = widthPx,
            viewportHeightPx = heightPx,
            density = density
        )

        assertEquals("alpha", forward?.id)
        assertEquals("alpha", reversed?.id)
        assertEquals(
            "beta",
            graphHitTest(
                nodes = listOf(left, right),
                target = GraphPoint(0.525f, 0.50f),
                semanticMode = GraphSemanticMode.OverviewCircles,
                viewportWidthPx = widthPx,
                viewportHeightPx = heightPx,
                density = density
            )?.id
        )
    }

    private fun node(
        id: String,
        x: Float,
        y: Float
    ) = GraphLayoutNode(
        id = id,
        label = id,
        kind = GraphNodeKind.Concept,
        status = GraphNodeStatus.Active,
        x = x,
        y = y,
        radius = 0.03f
    )
}
