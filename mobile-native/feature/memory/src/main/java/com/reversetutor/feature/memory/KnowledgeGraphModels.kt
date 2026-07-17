package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphEdge
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import com.reversetutor.core.model.GraphNodeStatus
import com.reversetutor.core.model.MemoryItem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class GraphRenderStatus(
    val label: String
) {
    Loading("加载中"),
    Empty("空"),
    Ready("已渲染"),
    Invalid("需审核"),
    Large("大图谱")
}

enum class GraphScope(
    val label: String
) {
    Session("会话图谱"),
    Global("全局图谱")
}

enum class GraphSemanticMode {
    OverviewCircles,
    DetailCards
}

fun graphSemanticMode(
    scope: GraphScope,
    scale: Float
): GraphSemanticMode = when {
    scope == GraphScope.Session -> GraphSemanticMode.DetailCards
    scale >= 1.5f -> GraphSemanticMode.DetailCards
    else -> GraphSemanticMode.OverviewCircles
}

enum class GraphNodeReviewAction(
    val label: String,
    val targetStatus: GraphNodeStatus
) {
    MarkNeedsReview("标为需审核", GraphNodeStatus.NeedsReview),
    Approve("通过", GraphNodeStatus.Approved),
    Restore("恢复为活跃", GraphNodeStatus.Active),
    Archive("归档", GraphNodeStatus.Archived),
    Hide("隐藏", GraphNodeStatus.Hidden)
}

data class GraphPhysicsPolicy(
    val anchorStrength: Float = 0.052f,
    val springStrength: Float = 0.020f,
    val repulsionStrength: Float = 0.00042f,
    val collisionStrength: Float = 0.56f,
    val collisionPadding: Float = 0.012f,
    val inertiaDamping: Float = 0.82f,
    val iterations: Int = 180
) {
    init {
        require(anchorStrength > 0f)
        require(springStrength > 0f)
        require(repulsionStrength > 0f)
        require(collisionStrength > 0f)
        require(collisionPadding >= 0f)
        require(inertiaDamping in 0f..1f)
        require(iterations > 0)
    }

    companion object {
        val Default = GraphPhysicsPolicy()
    }
}

data class GraphRenderSnapshot(
    val nodes: List<GraphLayoutNode>,
    val edges: List<GraphLayoutEdge>
)

data class KnowledgeGraphUiState(
    val scope: GraphScope,
    val status: GraphRenderStatus,
    val title: String,
    val summary: String,
    val nodes: List<GraphLayoutNode>,
    val visibleEdges: List<GraphLayoutEdge>,
    val invalidEdgeCount: Int,
    val selectedNodeId: String? = null,
    val lockedNodes: List<GraphLayoutNode> = emptyList(),
    val lockedEdges: List<GraphLayoutEdge> = emptyList()
) {
    val allNodes: List<GraphLayoutNode>
        get() = nodes + lockedNodes

    val selectedNode: GraphLayoutNode?
        get() = allNodes.firstOrNull { it.id == selectedNodeId }

    val edgeCount: Int
        get() = visibleEdges.size + invalidEdgeCount

    val allNodeCount: Int
        get() = nodes.size + lockedNodes.size

    fun renderSnapshot(showLockedNodes: Boolean): GraphRenderSnapshot =
        if (showLockedNodes) {
            GraphRenderSnapshot(
                nodes = allNodes,
                edges = visibleEdges + lockedEdges
            )
        } else {
            GraphRenderSnapshot(nodes = nodes, edges = visibleEdges)
        }

    fun withSelection(nodeId: String?): KnowledgeGraphUiState =
        copy(selectedNodeId = nodeId?.takeIf { id -> allNodes.any { it.id == id } })

    fun hitTest(
        x: Float,
        y: Float,
        semanticMode: GraphSemanticMode = graphSemanticMode(scope, 1f),
        includeLockedNodes: Boolean = false
    ): GraphLayoutNode? =
        renderSnapshot(includeLockedNodes)
            .nodes
            .asReversed()
            .firstOrNull { node -> node.contains(x, y, semanticMode) }

    fun relatedEdges(
        nodeId: String,
        includeLockedNodes: Boolean = true
    ): List<GraphLayoutEdge> =
        renderSnapshot(includeLockedNodes).edges.filter {
            it.fromNodeId == nodeId || it.toNodeId == nodeId
        }

    companion object {
        fun loading(scope: GraphScope = GraphScope.Session): KnowledgeGraphUiState =
            KnowledgeGraphUiState(
                scope = scope,
                status = GraphRenderStatus.Loading,
                title = "正在加载图谱",
                summary = "正在读取本地图谱数据。",
                nodes = emptyList(),
                visibleEdges = emptyList(),
                invalidEdgeCount = 0
            )

        fun from(
            nodes: List<GraphNode>,
            edges: List<GraphEdge>,
            selectedNodeId: String? = null,
            scope: GraphScope = GraphScope.Session,
            memoryItems: List<MemoryItem> = emptyList(),
            physicsPolicy: GraphPhysicsPolicy = GraphPhysicsPolicy.Default
        ): KnowledgeGraphUiState {
            val evidenceByMemoryId = memoryItems.associateBy { it.id }
            val sourceNodeIds = nodes.mapTo(linkedSetOf()) { it.id }
            val validEdges = edges
                .filter { it.fromNodeId in sourceNodeIds && it.toNodeId in sourceNodeIds }
                .sortedWith(compareBy<GraphEdge> { it.createdAtEpochMillis }.thenBy { it.id })
            val invalidEdgeCount = edges.size - validEdges.size
            val layoutNodes = HierarchicalGraphLayout.layout(
                nodes = nodes,
                edges = validEdges,
                scope = scope,
                evidenceByMemoryId = evidenceByMemoryId,
                policy = physicsPolicy
            )
            val activeNodeIds = layoutNodes
                .asSequence()
                .filterNot { it.isLocked }
                .mapTo(linkedSetOf()) { it.id }
            val lockedNodeIds = layoutNodes
                .asSequence()
                .filter { it.isLocked }
                .mapTo(linkedSetOf()) { it.id }
            val layoutEdges = validEdges.map { edge ->
                GraphLayoutEdge(
                    id = edge.id,
                    fromNodeId = edge.fromNodeId,
                    toNodeId = edge.toNodeId,
                    relation = edge.relation,
                    sourceMemoryId = edge.sourceMemoryId
                )
            }
            val visibleEdges = layoutEdges.filter {
                it.fromNodeId in activeNodeIds && it.toNodeId in activeNodeIds
            }
            val lockedEdges = layoutEdges.filter {
                it.fromNodeId in lockedNodeIds || it.toNodeId in lockedNodeIds
            }
            val visibleNodes = layoutNodes.filterNot { it.isLocked }
            val hiddenNodes = layoutNodes.filter { it.isLocked }
            val status = when {
                layoutNodes.isEmpty() -> GraphRenderStatus.Empty
                layoutNodes.size > largeGraphNodeThreshold -> GraphRenderStatus.Large
                invalidEdgeCount > 0 -> GraphRenderStatus.Invalid
                else -> GraphRenderStatus.Ready
            }
            return KnowledgeGraphUiState(
                scope = scope,
                status = status,
                title = status.titleFor(scope),
                summary = status.summaryFor(
                    scope = scope,
                    nodes = visibleNodes.size,
                    visibleEdges = visibleEdges.size,
                    invalidEdges = invalidEdgeCount
                ),
                nodes = visibleNodes,
                visibleEdges = visibleEdges,
                invalidEdgeCount = invalidEdgeCount,
                selectedNodeId = selectedNodeId?.takeIf { id -> layoutNodes.any { it.id == id } },
                lockedNodes = hiddenNodes,
                lockedEdges = lockedEdges
            )
        }

        private const val largeGraphNodeThreshold = 60

        private fun GraphRenderStatus.titleFor(scope: GraphScope): String = when (this) {
            GraphRenderStatus.Loading -> "正在加载图谱"
            GraphRenderStatus.Empty -> "还没有图谱节点"
            GraphRenderStatus.Ready -> scope.label
            GraphRenderStatus.Invalid -> "图谱需要审核"
            GraphRenderStatus.Large -> "大型图谱"
        }

        private fun GraphRenderStatus.summaryFor(
            scope: GraphScope,
            nodes: Int,
            visibleEdges: Int,
            invalidEdges: Int
        ): String = when (this) {
            GraphRenderStatus.Loading -> "正在读取本地图谱数据。"
            GraphRenderStatus.Empty -> "当前本地空间还没有图谱节点。"
            GraphRenderStatus.Ready -> "${scope.label}：$nodes 个节点、$visibleEdges 条关系。支持拖动、缩放、选择与节点列表兜底。"
            GraphRenderStatus.Invalid -> "${scope.label}：$nodes 个节点、$visibleEdges 条有效关系，$invalidEdges 条关系需要修复。支持拖动、缩放、选择与节点列表兜底。"
            GraphRenderStatus.Large -> "${scope.label}：当前有 $nodes 个节点。"
        }
    }
}

data class GraphLayoutNode(
    val id: String,
    val label: String,
    val kind: GraphNodeKind,
    val status: GraphNodeStatus,
    val x: Float,
    val y: Float,
    val radius: Float,
    val depth: Int = 0,
    val anchorX: Float = x,
    val anchorY: Float = y,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val cardHalfWidth: Float = 0.13f,
    val cardHalfHeight: Float = 0.042f,
    val sourceMemoryId: String? = null,
    val sourceMessageId: String? = null,
    val sourceId: String? = null,
    val evidenceTitle: String? = null,
    val evidenceBody: String? = null
) {
    val hitRadius: Float
        get() = (radius * 1.35f).coerceAtLeast(0.045f)

    val isLocked: Boolean
        get() = status == GraphNodeStatus.Hidden

    fun contains(
        targetX: Float,
        targetY: Float,
        semanticMode: GraphSemanticMode
    ): Boolean = when (semanticMode) {
        GraphSemanticMode.OverviewCircles -> {
            val dx = x - targetX
            val dy = y - targetY
            sqrt(dx * dx + dy * dy) <= hitRadius
        }
        GraphSemanticMode.DetailCards ->
            abs(x - targetX) <= cardHalfWidth && abs(y - targetY) <= cardHalfHeight
    }

    val kindLabel: String
        get() = when (kind) {
            GraphNodeKind.Concept -> "概念"
            GraphNodeKind.Requirement -> "目标"
            GraphNodeKind.Source -> "资料"
            GraphNodeKind.Session -> "会话"
            GraphNodeKind.Person -> "角色"
            GraphNodeKind.Other -> "节点"
        }

    val statusLabel: String
        get() = when (status) {
            GraphNodeStatus.Active -> "正在学习"
            GraphNodeStatus.NeedsReview -> "需审核"
            GraphNodeStatus.Approved -> "已完成"
            GraphNodeStatus.Hidden -> "尚未解锁"
            GraphNodeStatus.Archived -> "已归档"
        }

    val reviewActions: List<GraphNodeReviewAction>
        get() = when (status) {
            GraphNodeStatus.Active -> listOf(
                GraphNodeReviewAction.MarkNeedsReview,
                GraphNodeReviewAction.Approve,
                GraphNodeReviewAction.Archive,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.NeedsReview -> listOf(
                GraphNodeReviewAction.Approve,
                GraphNodeReviewAction.Archive,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.Approved -> listOf(
                GraphNodeReviewAction.MarkNeedsReview,
                GraphNodeReviewAction.Archive,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.Archived -> listOf(
                GraphNodeReviewAction.Restore,
                GraphNodeReviewAction.Hide
            )
            GraphNodeStatus.Hidden -> listOf(GraphNodeReviewAction.Restore)
        }

    val reviewCards: List<GraphReviewCard>
        get() = buildList {
            if (!evidenceTitle.isNullOrBlank() || !evidenceBody.isNullOrBlank()) {
                add(
                    GraphReviewCard(
                        title = evidenceTitle ?: label,
                        body = evidenceBody ?: "已关联证据，但暂无可读摘要。",
                        sourceMessageId = sourceMessageId,
                        sourceId = sourceId
                    )
                )
            }
            if (status == GraphNodeStatus.NeedsReview) {
                add(
                    GraphReviewCard(
                        title = "需要审核",
                        body = "通过这个图谱节点前，请确认节点名称、关系和关联证据。",
                        sourceMessageId = sourceMessageId,
                        sourceId = sourceId
                    )
                )
            }
        }
}

data class GraphReviewCard(
    val title: String,
    val body: String,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
)

data class GraphLayoutEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val relation: String,
    val sourceMemoryId: String? = null
)

private object HierarchicalGraphLayout {
    private data class Particle(
        val node: GraphNode,
        val depth: Int,
        val anchorX: Float,
        val anchorY: Float,
        val radius: Float,
        val halfWidth: Float,
        val halfHeight: Float,
        var x: Float,
        var y: Float,
        var velocityX: Float = 0f,
        var velocityY: Float = 0f,
        var forceX: Float = 0f,
        var forceY: Float = 0f
    )

    fun layout(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>,
        scope: GraphScope,
        evidenceByMemoryId: Map<String, MemoryItem>,
        policy: GraphPhysicsPolicy
    ): List<GraphLayoutNode> {
        val sortedNodes = nodes.sortedWith(
            compareBy<GraphNode> { nodeKindOrder(it.kind) }
                .thenBy { it.createdAtEpochMillis }
                .thenBy { it.label }
                .thenBy { it.id }
        )
        if (sortedNodes.isEmpty()) return emptyList()

        val nodeIds = sortedNodes.mapTo(linkedSetOf()) { it.id }
        val validEdges = edges.filter { it.fromNodeId in nodeIds && it.toNodeId in nodeIds }
        val outgoingCountById = validEdges.groupingBy { it.fromNodeId }.eachCount()
        val depthById = resolveDepths(sortedNodes, validEdges)
        val anchors = if (scope == GraphScope.Session) {
            sessionAnchors(sortedNodes, depthById)
        } else {
            globalAnchors(sortedNodes, depthById)
        }
        val particles = sortedNodes.map { node ->
            val anchor = anchors.getValue(node.id)
            val radius = nodeRadius(
                node = node,
                count = sortedNodes.size,
                scope = scope,
                outgoingCount = outgoingCountById[node.id] ?: 0
            )
            val halfWidth = if (scope == GraphScope.Session) 0.145f else 0.115f
            val halfHeight = if (scope == GraphScope.Session) 0.043f else 0.036f
            Particle(
                node = node,
                depth = depthById.getValue(node.id),
                anchorX = anchor.first,
                anchorY = anchor.second,
                radius = radius,
                halfWidth = halfWidth,
                halfHeight = halfHeight,
                x = anchor.first,
                y = anchor.second
            )
        }
        val particleById = particles.associateBy { it.node.id }

        repeat(policy.iterations) {
            particles.forEach { particle ->
                particle.forceX = (particle.anchorX - particle.x) * policy.anchorStrength
                particle.forceY = (particle.anchorY - particle.y) * policy.anchorStrength
            }
            applySprings(validEdges, particleById, policy)
            applyRepulsionAndCollision(particles, policy)
            particles.forEach { particle ->
                particle.velocityX = ((particle.velocityX + particle.forceX) * policy.inertiaDamping)
                    .coerceIn(-0.032f, 0.032f)
                particle.velocityY = ((particle.velocityY + particle.forceY) * policy.inertiaDamping)
                    .coerceIn(-0.032f, 0.032f)
                val horizontalBounds = if (scope == GraphScope.Session) 0.17f..0.85f else 0.07f..0.93f
                particle.x = (particle.x + particle.velocityX).coerceIn(horizontalBounds)
                particle.y = (particle.y + particle.velocityY).coerceIn(0.07f, 0.93f)
            }
        }

        return particles.map { particle ->
            val evidence = particle.node.sourceMemoryId?.let(evidenceByMemoryId::get)
            GraphLayoutNode(
                id = particle.node.id,
                label = particle.node.label,
                kind = particle.node.kind,
                status = particle.node.status,
                x = particle.x,
                y = particle.y,
                radius = particle.radius,
                depth = particle.depth,
                anchorX = particle.anchorX,
                anchorY = particle.anchorY,
                velocityX = particle.velocityX,
                velocityY = particle.velocityY,
                cardHalfWidth = particle.halfWidth,
                cardHalfHeight = particle.halfHeight,
                sourceMemoryId = particle.node.sourceMemoryId,
                sourceMessageId = evidence?.sourceMessageId,
                sourceId = evidence?.sourceId,
                evidenceTitle = evidence?.title,
                evidenceBody = evidence?.body
            )
        }
    }

    private fun resolveDepths(
        nodes: List<GraphNode>,
        edges: List<GraphEdge>
    ): Map<String, Int> {
        val incoming = nodes.associate { it.id to 0 }.toMutableMap()
        val outgoing = nodes.associate { it.id to mutableListOf<String>() }
        edges.forEach { edge ->
            incoming[edge.toNodeId] = incoming.getValue(edge.toNodeId) + 1
            outgoing.getValue(edge.fromNodeId).add(edge.toNodeId)
        }
        outgoing.values.forEach { it.sort() }
        val preferredRoot = nodes.firstOrNull { it.kind == GraphNodeKind.Session }?.id
        val roots = buildList {
            if (preferredRoot != null) add(preferredRoot)
            incoming.entries
                .asSequence()
                .filter { it.value == 0 && it.key != preferredRoot }
                .map { it.key }
                .sorted()
                .forEach(::add)
            if (isEmpty()) add(nodes.first().id)
        }
        val depths = mutableMapOf<String, Int>()
        val queue = ArrayDeque<String>()
        roots.forEach { id ->
            if (id !in depths) {
                depths[id] = 0
                queue.addLast(id)
            }
        }
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val nextDepth = depths.getValue(current) + 1
            outgoing.getValue(current).forEach { child ->
                if (child !in depths) {
                    depths[child] = nextDepth
                    queue.addLast(child)
                }
            }
        }
        nodes.filterNot { it.id in depths }.forEachIndexed { index, node ->
            depths[node.id] = index % 3
        }
        return depths
    }

    private fun globalAnchors(
        nodes: List<GraphNode>,
        depthById: Map<String, Int>
    ): Map<String, Pair<Float, Float>> {
        val maxDepth = depthById.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        return buildMap {
            nodes.groupBy { depthById.getValue(it.id) }
                .toSortedMap()
                .forEach { (depth, levelNodes) ->
                    levelNodes.sortedBy { it.id }.forEachIndexed { index, node ->
                        val fraction = (index + 1f) / (levelNodes.size + 1f)
                        val x = 0.08f + fraction * 0.84f
                        val y = 0.11f + depth.toFloat() / maxDepth.toFloat() * 0.76f
                        put(node.id, x to y)
                    }
                }
        }
    }

    private fun sessionAnchors(
        nodes: List<GraphNode>,
        depthById: Map<String, Int>
    ): Map<String, Pair<Float, Float>> {
        val rootId = nodes.firstOrNull { it.kind == GraphNodeKind.Session }?.id
        val leftNodes = nodes.filter {
            it.id != rootId && it.kind in setOf(
                GraphNodeKind.Person,
                GraphNodeKind.Requirement,
                GraphNodeKind.Source
            )
        }
        val rightNodes = nodes.filter { it.id != rootId && it !in leftNodes }
        return buildMap {
            if (rootId != null) put(rootId, 0.50f to 0.48f)
            putSessionLane(leftNodes, x = 0.18f, target = this)
            putSessionLane(rightNodes, x = 0.80f, target = this)
            nodes.filterNot { it.id in this }.forEach { node ->
                val depth = depthById.getValue(node.id)
                put(node.id, 0.50f to (0.16f + depth * 0.13f).coerceAtMost(0.86f))
            }
        }
    }

    private fun putSessionLane(
        nodes: List<GraphNode>,
        x: Float,
        target: MutableMap<String, Pair<Float, Float>>
    ) {
        nodes.sortedWith(compareBy<GraphNode> { it.kind.name }.thenBy { it.id })
            .forEachIndexed { index, node ->
                val y = if (nodes.size <= 1) 0.48f else 0.09f + index.toFloat() / (nodes.size - 1f) * 0.80f
                target[node.id] = x to y
            }
    }

    private fun applySprings(
        edges: List<GraphEdge>,
        particles: Map<String, Particle>,
        policy: GraphPhysicsPolicy
    ) {
        edges.forEach { edge ->
            val from = particles[edge.fromNodeId] ?: return@forEach
            val to = particles[edge.toNodeId] ?: return@forEach
            val dx = to.x - from.x
            val dy = to.y - from.y
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
            val targetDistance = if (abs(to.depth - from.depth) > 1) 0.24f else 0.17f
            val pull = (distance - targetDistance) * policy.springStrength
            val forceX = dx / distance * pull
            val forceY = dy / distance * pull
            from.forceX += forceX
            from.forceY += forceY
            to.forceX -= forceX
            to.forceY -= forceY
        }
    }

    private fun applyRepulsionAndCollision(
        particles: List<Particle>,
        policy: GraphPhysicsPolicy
    ) {
        for (firstIndex in 0 until particles.lastIndex) {
            val first = particles[firstIndex]
            for (secondIndex in firstIndex + 1 until particles.size) {
                val second = particles[secondIndex]
                var dx = second.x - first.x
                var dy = second.y - first.y
                if (abs(dx) < 0.0001f && abs(dy) < 0.0001f) {
                    dx = if ((firstIndex + secondIndex) % 2 == 0) 0.001f else -0.001f
                    dy = 0.001f
                }
                val distanceSquared = dx * dx + dy * dy
                val distance = sqrt(distanceSquared).coerceAtLeast(0.001f)
                val repulsion = (policy.repulsionStrength / max(distanceSquared, 0.0025f)).coerceAtMost(0.018f)
                val repelX = dx / distance * repulsion
                val repelY = dy / distance * repulsion
                first.forceX -= repelX
                first.forceY -= repelY
                second.forceX += repelX
                second.forceY += repelY

                val overlapX = first.halfWidth + second.halfWidth + policy.collisionPadding - abs(dx)
                val overlapY = first.halfHeight + second.halfHeight + policy.collisionPadding - abs(dy)
                if (overlapX > 0f && overlapY > 0f) {
                    if (overlapX < overlapY) {
                        val direction = if (dx >= 0f) 1f else -1f
                        val collision = overlapX * policy.collisionStrength
                        first.forceX -= collision * direction
                        second.forceX += collision * direction
                    } else {
                        val direction = if (dy >= 0f) 1f else -1f
                        val collision = overlapY * policy.collisionStrength
                        first.forceY -= collision * direction
                        second.forceY += collision * direction
                    }
                }
            }
        }
    }

    private fun nodeRadius(
        node: GraphNode,
        count: Int,
        scope: GraphScope,
        outgoingCount: Int
    ): Float = when {
        node.kind == GraphNodeKind.Session -> 0.070f
        scope == GraphScope.Session -> 0.038f
        count > 40 -> 0.022f
        outgoingCount >= 5 -> 0.055f
        outgoingCount >= 2 -> 0.045f
        node.kind == GraphNodeKind.Person -> 0.038f
        else -> 0.014f
    }

    private fun nodeKindOrder(kind: GraphNodeKind): Int = when (kind) {
        GraphNodeKind.Session -> 0
        GraphNodeKind.Person -> 1
        GraphNodeKind.Requirement -> 2
        GraphNodeKind.Concept -> 3
        GraphNodeKind.Source -> 4
        GraphNodeKind.Other -> 5
    }
}
