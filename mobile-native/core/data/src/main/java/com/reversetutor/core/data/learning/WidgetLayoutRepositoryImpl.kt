package com.reversetutor.core.data.learning

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.domain.WidgetLayoutRepository
import com.reversetutor.core.model.WidgetLayoutPreference

class WidgetLayoutRepositoryImpl(
    private val database: ReverseTutorDatabase
) : WidgetLayoutRepository {
    override suspend fun load(spaceId: String): List<WidgetLayoutPreference> =
        database.learningDao()
            .listWidgetPreferences(requireSpaceId(spaceId))
            .map { it.toDomain() }

    override suspend fun saveLayout(
        spaceId: String,
        preferences: List<WidgetLayoutPreference>
    ) {
        val normalizedSpaceId = requireSpaceId(spaceId)
        validateCompleteLayout(normalizedSpaceId, preferences)
        database.withTransaction {
            database.learningDao().deleteWidgetPreferences(normalizedSpaceId)
            if (preferences.isNotEmpty()) {
                database.learningDao().upsertWidgetPreferences(
                    preferences.sortedBy { it.order }.map { it.toEntity() }
                )
            }
        }
    }

    override suspend fun reset(spaceId: String) {
        database.learningDao().deleteWidgetPreferences(requireSpaceId(spaceId))
    }
}

internal fun requireSpaceId(spaceId: String): String =
    spaceId.trim().also {
        require(it.isNotEmpty()) { "spaceId must not be blank" }
    }

internal fun validateCompleteLayout(
    spaceId: String,
    preferences: List<WidgetLayoutPreference>
) {
    val widgetIds = hashSetOf<String>()
    val orders = hashSetOf<Int>()
    preferences.forEach { preference ->
        require(preference.spaceId == spaceId) { "preference spaceId must match layout spaceId" }
        require(preference.widgetId.isNotBlank()) { "widgetId must not be blank" }
        require(widgetIds.add(preference.widgetId)) { "widgetId must be unique" }
        require(preference.order >= 0) { "order must not be negative" }
        require(orders.add(preference.order)) { "order must be unique" }
    }
}
