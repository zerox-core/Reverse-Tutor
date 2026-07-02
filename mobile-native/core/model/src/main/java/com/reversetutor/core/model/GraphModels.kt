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
