package com.reversetutor.core.model

data class WidgetLayoutPreference(
    val spaceId: String,
    val widgetId: String,
    val order: Int = 0,
    val hidden: Boolean = false,
    val size: WidgetSize = WidgetSize.Compact,
    val updatedAtEpochMillis: Long = 0L
)

enum class WidgetSize {
    Compact,
    FullWidth
}

data class SearchTarget(
    val type: SearchTargetType,
    val entityId: String,
    val spaceId: String? = null,
    val parentEntityId: String? = null,
    val sessionId: String? = null
)

enum class SearchTargetType {
    Session,
    Message,
    Source,
    Memory,
    GraphNode,
    StudyPlan
}
