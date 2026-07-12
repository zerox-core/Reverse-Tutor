package com.reversetutor.core.model

data class WorldTreeDraft(
    val id: String,
    val spaceId: String,
    val sessionId: String?,
    val templateId: String?,
    val title: String,
    val mode: WorldTreeMode,
    val schemaVersion: Int,
    val sections: List<WorldTreeSection>,
    val sourceIds: List<String>,
    val state: WorldTreeDraftState,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
) {
    val isReady: Boolean
        get() = state == WorldTreeDraftState.Ready && sections
            .filter { it.required }
            .all { it.completed && it.isPayloadCompatible() }
}

enum class WorldTreeMode { Learning, Review, Companion }

enum class WorldTreeDraftState { Draft, Ready, Archived }

data class WorldTreeSection(
    val id: String,
    val type: WorldTreeSectionType,
    val title: String,
    val order: Int,
    val payload: WorldTreeSectionPayload,
    val required: Boolean,
    val completed: Boolean
) {
    fun isPayloadCompatible(): Boolean = when (type) {
        WorldTreeSectionType.StudentRole -> payload is StudentRolePayload
        WorldTreeSectionType.LearningGoal -> payload is LearningGoalPayload
        WorldTreeSectionType.StudySchedule -> payload is StudySchedulePayload
        WorldTreeSectionType.PortraitSystem -> payload is PortraitSystemPayload
        WorldTreeSectionType.StoryPlot -> payload is StoryPlotPayload
        WorldTreeSectionType.SourceLibrary -> payload is SourceLibraryPayload
        WorldTreeSectionType.Custom -> payload is CustomPayload
    }
}

enum class WorldTreeSectionType {
    StudentRole,
    LearningGoal,
    StudySchedule,
    PortraitSystem,
    StoryPlot,
    SourceLibrary,
    Custom
}

sealed interface WorldTreeSectionPayload

data class StudentRolePayload(
    val avatarRef: String? = null,
    val name: String = "",
    val personality: String = "",
    val currentUnderstanding: String = "",
    val interactionHabits: String = ""
) : WorldTreeSectionPayload

data class LearningGoalPayload(
    val description: String = "",
    val acceptanceCriteria: List<String> = emptyList()
) : WorldTreeSectionPayload

data class StudySchedulePayload(
    val startAtEpochMillis: Long? = null,
    val endAtEpochMillis: Long? = null,
    val weeklyRhythm: String = "",
    val milestones: List<StudyMilestone> = emptyList()
) : WorldTreeSectionPayload

data class StudyMilestone(
    val id: String,
    val title: String,
    val description: String,
    val targetAtEpochMillis: Long?,
    val order: Int
)

data class PortraitSystemPayload(
    val dimensions: List<PortraitDimension> = emptyList()
) : WorldTreeSectionPayload

data class PortraitDimension(
    val id: String,
    val title: String,
    val content: String,
    val order: Int
)

data class StoryPlotPayload(
    val background: String = "",
    val relationships: String = "",
    val stages: List<StoryStage> = emptyList()
) : WorldTreeSectionPayload

data class StoryStage(
    val id: String,
    val title: String,
    val description: String,
    val illustrationRef: String?,
    val order: Int
)

data class SourceLibraryPayload(
    val sourceIds: List<String> = emptyList()
) : WorldTreeSectionPayload

data class CustomPayload(
    val content: String = ""
) : WorldTreeSectionPayload

data class CreateWorldTreeDraftCommand(
    val id: String,
    val spaceId: String,
    val templateId: String? = null,
    val title: String,
    val mode: WorldTreeMode,
    val sections: List<WorldTreeSection> = emptyList(),
    val sourceIds: List<String> = emptyList(),
    val state: WorldTreeDraftState = WorldTreeDraftState.Draft,
    val nowEpochMillis: Long
)
