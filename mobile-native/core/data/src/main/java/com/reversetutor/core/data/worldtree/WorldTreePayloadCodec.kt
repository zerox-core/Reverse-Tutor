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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class WorldTreePayloadCodecException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

class WorldTreePayloadCodec {
    fun encode(
        schemaVersion: Int,
        type: WorldTreeSectionType,
        payload: WorldTreeSectionPayload
    ): String {
        requireVersion(schemaVersion)
        val encoded = when (type) {
            WorldTreeSectionType.StudentRole -> {
                val value = payload as? StudentRolePayload ?: mismatch(type)
                buildJsonObject {
                    putNullableString("avatarRef", value.avatarRef)
                    put("name", value.name)
                    put("personality", value.personality)
                    put("currentUnderstanding", value.currentUnderstanding)
                    put("interactionHabits", value.interactionHabits)
                }
            }
            WorldTreeSectionType.LearningGoal -> {
                val value = payload as? LearningGoalPayload ?: mismatch(type)
                buildJsonObject {
                    put("description", value.description)
                    put("acceptanceCriteria", value.acceptanceCriteria.toJsonArray())
                }
            }
            WorldTreeSectionType.StudySchedule -> {
                val value = payload as? StudySchedulePayload ?: mismatch(type)
                buildJsonObject {
                    putNullableLong("startAtEpochMillis", value.startAtEpochMillis)
                    putNullableLong("endAtEpochMillis", value.endAtEpochMillis)
                    put("weeklyRhythm", value.weeklyRhythm)
                    put("milestones", buildJsonArray {
                        value.milestones.forEach { milestone ->
                            add(buildJsonObject {
                                put("id", milestone.id)
                                put("title", milestone.title)
                                put("description", milestone.description)
                                putNullableLong("targetAtEpochMillis", milestone.targetAtEpochMillis)
                                put("order", milestone.order)
                            })
                        }
                    })
                }
            }
            WorldTreeSectionType.PortraitSystem -> {
                val value = payload as? PortraitSystemPayload ?: mismatch(type)
                buildJsonObject {
                    put("dimensions", buildJsonArray {
                        value.dimensions.forEach { dimension ->
                            add(buildJsonObject {
                                put("id", dimension.id)
                                put("title", dimension.title)
                                put("content", dimension.content)
                                put("order", dimension.order)
                            })
                        }
                    })
                }
            }
            WorldTreeSectionType.StoryPlot -> {
                val value = payload as? StoryPlotPayload ?: mismatch(type)
                buildJsonObject {
                    put("background", value.background)
                    put("relationships", value.relationships)
                    put("stages", buildJsonArray {
                        value.stages.forEach { stage ->
                            add(buildJsonObject {
                                put("id", stage.id)
                                put("title", stage.title)
                                put("description", stage.description)
                                putNullableString("illustrationRef", stage.illustrationRef)
                                put("order", stage.order)
                            })
                        }
                    })
                }
            }
            WorldTreeSectionType.SourceLibrary -> {
                val value = payload as? SourceLibraryPayload ?: mismatch(type)
                buildJsonObject {
                    put("sourceIds", value.sourceIds.toJsonArray())
                }
            }
            WorldTreeSectionType.Custom -> {
                val value = payload as? CustomPayload ?: mismatch(type)
                buildJsonObject { put("content", value.content) }
            }
        }
        return encoded.toString()
    }

    fun decode(
        schemaVersion: Int,
        type: WorldTreeSectionType,
        payloadJson: String
    ): WorldTreeSectionPayload {
        requireVersion(schemaVersion)
        return try {
            val value = Json.parseToJsonElement(payloadJson).jsonObject
            when (type) {
                WorldTreeSectionType.StudentRole -> StudentRolePayload(
                    avatarRef = value.nullableString("avatarRef"),
                    name = value.requiredString("name"),
                    personality = value.requiredString("personality"),
                    currentUnderstanding = value.requiredString("currentUnderstanding"),
                    interactionHabits = value.requiredString("interactionHabits")
                )
                WorldTreeSectionType.LearningGoal -> LearningGoalPayload(
                    description = value.requiredString("description"),
                    acceptanceCriteria = value.requiredArray("acceptanceCriteria")
                        .map { it.requiredStringValue() }
                )
                WorldTreeSectionType.StudySchedule -> StudySchedulePayload(
                    startAtEpochMillis = value.nullableLong("startAtEpochMillis"),
                    endAtEpochMillis = value.nullableLong("endAtEpochMillis"),
                    weeklyRhythm = value.requiredString("weeklyRhythm"),
                    milestones = value.requiredArray("milestones").map { element ->
                        val milestone = element.requiredObject()
                        StudyMilestone(
                            id = milestone.requiredString("id"),
                            title = milestone.requiredString("title"),
                            description = milestone.requiredString("description"),
                            targetAtEpochMillis = milestone.nullableLong("targetAtEpochMillis"),
                            order = milestone.requiredInt("order")
                        )
                    }
                )
                WorldTreeSectionType.PortraitSystem -> PortraitSystemPayload(
                    dimensions = value.requiredArray("dimensions").map { element ->
                        val dimension = element.requiredObject()
                        PortraitDimension(
                            id = dimension.requiredString("id"),
                            title = dimension.requiredString("title"),
                            content = dimension.requiredString("content"),
                            order = dimension.requiredInt("order")
                        )
                    }
                )
                WorldTreeSectionType.StoryPlot -> StoryPlotPayload(
                    background = value.requiredString("background"),
                    relationships = value.requiredString("relationships"),
                    stages = value.requiredArray("stages").map { element ->
                        val stage = element.requiredObject()
                        StoryStage(
                            id = stage.requiredString("id"),
                            title = stage.requiredString("title"),
                            description = stage.requiredString("description"),
                            illustrationRef = stage.nullableString("illustrationRef"),
                            order = stage.requiredInt("order")
                        )
                    }
                )
                WorldTreeSectionType.SourceLibrary -> SourceLibraryPayload(
                    sourceIds = value.requiredArray("sourceIds").map { it.requiredStringValue() }
                )
                WorldTreeSectionType.Custom -> CustomPayload(
                    content = value.requiredString("content")
                )
            }
        } catch (error: WorldTreePayloadCodecException) {
            throw error
        } catch (error: RuntimeException) {
            throw WorldTreePayloadCodecException("Invalid WorldTree payload for $type", error)
        }
    }

    private fun requireVersion(schemaVersion: Int) {
        if (schemaVersion != SupportedSchemaVersion) {
            throw WorldTreePayloadCodecException(
                "Unsupported WorldTree schema version: $schemaVersion"
            )
        }
    }

    private fun mismatch(type: WorldTreeSectionType): Nothing =
        throw WorldTreePayloadCodecException("Payload does not match section type: $type")

    private companion object {
        const val SupportedSchemaVersion = 1
    }
}

private fun List<String>.toJsonArray(): JsonArray =
    buildJsonArray { this@toJsonArray.forEach { add(JsonPrimitive(it)) } }

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(
    name: String,
    value: String?
) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableLong(
    name: String,
    value: Long?
) {
    put(name, value?.let(::JsonPrimitive) ?: JsonNull)
}

private fun JsonObject.requiredString(name: String): String =
    getValue(name).jsonPrimitive.contentOrNull
        ?: throw WorldTreePayloadCodecException("Expected string: $name")

private fun JsonObject.nullableString(name: String): String? =
    getValue(name).takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull
        ?: if (getValue(name) is JsonNull) null
        else throw WorldTreePayloadCodecException("Expected nullable string: $name")

private fun JsonObject.nullableLong(name: String): Long? =
    getValue(name).takeUnless { it is JsonNull }?.jsonPrimitive?.longOrNull
        ?: if (getValue(name) is JsonNull) null
        else throw WorldTreePayloadCodecException("Expected nullable long: $name")

private fun JsonObject.requiredInt(name: String): Int =
    getValue(name).jsonPrimitive.intOrNull
        ?: throw WorldTreePayloadCodecException("Expected int: $name")

private fun JsonObject.requiredArray(name: String): JsonArray =
    getValue(name) as? JsonArray
        ?: throw WorldTreePayloadCodecException("Expected array: $name")

private fun JsonElement.requiredObject(): JsonObject =
    this as? JsonObject ?: throw WorldTreePayloadCodecException("Expected object")

private fun JsonElement.requiredStringValue(): String =
    jsonPrimitive.contentOrNull
        ?: throw WorldTreePayloadCodecException("Expected string value")
