package com.reversetutor.core.data.worldtree

import com.reversetutor.core.model.CustomPayload
import com.reversetutor.core.model.LearningGoalPayload
import com.reversetutor.core.model.PortraitDimension
import com.reversetutor.core.model.PortraitSystemPayload
import com.reversetutor.core.model.SourceLibraryPayload
import com.reversetutor.core.model.StoryPlotPayload
import com.reversetutor.core.model.StoryStage
import com.reversetutor.core.model.StudentRolePayload
import com.reversetutor.core.model.StudyMilestone
import com.reversetutor.core.model.StudySchedulePayload
import com.reversetutor.core.model.WorldTreeSectionPayload
import com.reversetutor.core.model.WorldTreeSectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WorldTreePayloadCodecTest {
    private val codec = WorldTreePayloadCodec()

    @Test
    fun roundTripsEveryVersionOnePayloadType() {
        val cases: List<Pair<WorldTreeSectionType, WorldTreeSectionPayload>> = listOf(
            WorldTreeSectionType.StudentRole to StudentRolePayload(
                avatarRef = "avatar://student",
                name = "Student",
                personality = "Curious",
                currentUnderstanding = "Algebra basics",
                interactionHabits = "Uses examples"
            ),
            WorldTreeSectionType.LearningGoal to LearningGoalPayload(
                description = "Understand functions",
                acceptanceCriteria = listOf("Explain domain", "Solve examples")
            ),
            WorldTreeSectionType.StudySchedule to StudySchedulePayload(
                startAtEpochMillis = 100,
                endAtEpochMillis = 200,
                weeklyRhythm = "Three sessions",
                milestones = listOf(
                    StudyMilestone("milestone-1", "Basics", "Finish basics", 150, 0)
                )
            ),
            WorldTreeSectionType.PortraitSystem to PortraitSystemPayload(
                dimensions = listOf(
                    PortraitDimension("dimension-1", "Confidence", "Growing", 0)
                )
            ),
            WorldTreeSectionType.StoryPlot to StoryPlotPayload(
                background = "A quiet library",
                relationships = "Tutor and learner",
                stages = listOf(
                    StoryStage("stage-1", "Opening", "Meet", null, 0)
                )
            ),
            WorldTreeSectionType.SourceLibrary to SourceLibraryPayload(
                sourceIds = listOf("source-1", "source-2")
            ),
            WorldTreeSectionType.Custom to CustomPayload("Custom context")
        )

        cases.forEach { (type, payload) ->
            val encoded = codec.encode(1, type, payload)
            assertEquals(payload, codec.decode(1, type, encoded))
        }
    }

    @Test
    fun rejectsUnsupportedVersionAndPayloadTypeMismatch() {
        assertThrows(WorldTreePayloadCodecException::class.java) {
            codec.encode(2, WorldTreeSectionType.Custom, CustomPayload("value"))
        }
        assertThrows(WorldTreePayloadCodecException::class.java) {
            codec.encode(
                1,
                WorldTreeSectionType.Custom,
                LearningGoalPayload("Goal", emptyList())
            )
        }
        assertThrows(WorldTreePayloadCodecException::class.java) {
            codec.decode(1, WorldTreeSectionType.Custom, "{}")
        }
    }
}
