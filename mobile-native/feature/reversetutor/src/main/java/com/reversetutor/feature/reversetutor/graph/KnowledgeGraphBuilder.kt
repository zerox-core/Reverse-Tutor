package com.reversetutor.feature.reversetutor.graph

import com.reversetutor.feature.reversetutor.core.PyCompat
import com.reversetutor.feature.reversetutor.curriculum.CurriculumCatalog
import kotlin.math.max

/**
 * 知识图谱构建（规格源：spike app/tools/memory.py）。
 *
 * P3 课标化：先修边由课标 prerequisites 派生，不再硬编码；
 * 写节点时自动补课标事实（grade/difficulty/prerequisites）。
 */
class KnowledgeGraphBuilder(private val catalog: CurriculumCatalog) {

    /** 图谱节点（level 为 0.0-1.0 掌握度浮点，区别于会话 understanding_level 1-5）。 */
    data class KgNodeFull(
        val id: String,
        val status: String,
        val level: Double,
        val grade: String? = null,
        val difficulty: Int? = null,
        val prerequisites: List<String>? = null
    )

    data class KgEdge(val from: String, val to: String, val type: String)

    data class KgGraph(
        val nodes: List<KgNodeFull> = emptyList(),
        val edges: List<KgEdge> = emptyList()
    )

    /** 从课标先修链自动生成概念边：prerequisites 生成 (prereq → concept, "prerequisite")。 */
    fun curriculumEdges(): List<KgEdge> =
        catalog.allConcepts().flatMap { (_, entry) ->
            entry.prerequisites.map { pre -> KgEdge(pre, entry.id, "prerequisite") }
        }

    /** 写节点时自动补课标事实；未收录则不加。 */
    private fun enriched(node: KgNodeFull, concept: String): KgNodeFull {
        val entry = catalog.findInCurriculum(concept).entry ?: return node
        return node.copy(
            grade = entry.grade,
            difficulty = entry.difficulty,
            prerequisites = entry.prerequisites.toList()
        )
    }

    data class GraphUpdateInput(
        val graph: KgGraph = KgGraph(),
        val oldMastered: List<String> = emptyList(),
        val oldConfused: List<String> = emptyList(),
        val addMastered: List<String> = emptyList(),
        val addPending: List<String> = emptyList(),
        val relationships: List<KgEdge> = emptyList()
    )

    data class GraphUpdateResult(
        val graph: KgGraph,
        val masteredConcepts: List<String>,
        val confusedConcepts: List<String>,
        val nextOptimalConcepts: List<String>,
        val learningProgress: Double,
        val weakAreas: List<String>
    )

    /**
     * 更新图谱（对齐 spike _update_knowledge_graph）：
     * - mastered 节点 level+0.3 封顶 1.0；pending 节点 level+0.15 夹 0.1-0.9 且不覆盖已 mastered
     * - 节点写回时自动补课标事实
     * - 边 = 课标先修边 + LLM 声明的关系
     * - 清单：mastered = 旧∪新增；confused = (旧∪pending) − mastered
     * - next_optimal：先修已 mastered、自身未 mastered 的后继（取 3）
     */
    fun updateGraph(input: GraphUpdateInput): GraphUpdateResult {
        val nodes = LinkedHashMap<String, KgNodeFull>()
        input.graph.nodes.forEach { nodes[it.id] = it }

        for (c in input.addMastered) {
            val n = nodes.getOrPut(c) { KgNodeFull(c, "not_started", 0.0) }
            nodes[c] = enriched(
                n.copy(status = "mastered", level = PyCompat.round((n.level + 0.3).coerceAtMost(1.0), 2)), c)
        }
        for (c in input.addPending) {
            val n = nodes.getOrPut(c) { KgNodeFull(c, "not_started", 0.0) }
            val updated = if (n.status != "mastered") {
                n.copy(status = "learning",
                    level = PyCompat.round(max(0.1, (n.level + 0.15).coerceAtMost(0.9)), 2))
            } else n
            nodes[c] = enriched(updated, c)
        }

        val edges = curriculumEdges() + input.relationships
        val newGraph = KgGraph(nodes.values.toList(), edges)

        val masteredN = newGraph.nodes.count { it.status == "mastered" }
        val progress = PyCompat.round(
            PyCompat.clamp(masteredN.toDouble() / max(newGraph.nodes.size, 1), 0.0, 1.0), 2)
        val weak = newGraph.nodes.filter { it.status == "learning" }.map { it.id }

        val masteredIds = newGraph.nodes.filter { it.status == "mastered" }.map { it.id }.toSet()
        val nextOptimal = curriculumEdges()
            .filter { e ->
                e.from in masteredIds &&
                    newGraph.nodes.none { it.id == e.to && it.status == "mastered" }
            }
            .map { it.to }
            .take(3)   // 忠实 spike：不去重（[:3] 语义）

        val mastered = (input.oldMastered + input.addMastered).toSortedSet().toList()
        val confused = (input.oldConfused + input.addPending).toSortedSet()
            .minus(input.addMastered.toSet()).toList()

        return GraphUpdateResult(
            graph = newGraph,
            masteredConcepts = mastered,
            confusedConcepts = confused,
            nextOptimalConcepts = nextOptimal,
            learningProgress = progress,
            weakAreas = weak
        )
    }
}

typealias KgNodeFull = KnowledgeGraphBuilder.KgNodeFull
typealias KgEdge = KnowledgeGraphBuilder.KgEdge
typealias KgGraph = KnowledgeGraphBuilder.KgGraph

