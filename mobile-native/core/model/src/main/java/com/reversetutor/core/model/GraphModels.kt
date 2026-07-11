package com.reversetutor.core.model

data class GraphNode(
    val id: String,
    val spaceId: String,
    val label: String,
    val kind: GraphNodeKind,
    val createdAtEpochMillis: Long,
    val status: GraphNodeStatus = GraphNodeStatus.Active,
    val sourceMemoryId: String? = null
)

enum class GraphNodeKind {
    Concept,
    Requirement,
    Source,
    Session,
    Person,
    Other
}

enum class GraphNodeStatus {
    Active,
    NeedsReview,
    Approved,
    Hidden,
    Archived
}

data class GraphEdge(
    val id: String,
    val spaceId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val relation: String,
    val createdAtEpochMillis: Long,
    val sourceMemoryId: String? = null
)

sealed interface GraphScope {
    data class Global(val spaceId: String) : GraphScope

    data class Session(val sessionId: String) : GraphScope
}

enum class GraphEmptyReason {
    NoExtractedNodes
}

data class GraphSnapshot(
    val nodes: List<GraphNode>,
    val edges: List<GraphEdge>,
    val invalidEdgeCount: Int = 0
)

sealed interface GraphSnapshotResult {
    data class Empty(val reason: GraphEmptyReason) : GraphSnapshotResult

    data class Ready(val snapshot: GraphSnapshot) : GraphSnapshotResult

    data class Error(val error: DomainError) : GraphSnapshotResult
}
