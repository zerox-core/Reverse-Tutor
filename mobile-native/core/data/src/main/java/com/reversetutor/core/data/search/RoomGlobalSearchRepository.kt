package com.reversetutor.core.data.search

import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.SearchDocumentEntity
import com.reversetutor.core.domain.GlobalSearchRepository
import com.reversetutor.core.model.SearchTarget
import com.reversetutor.core.model.SearchTargetType

class RoomGlobalSearchRepository(
    private val database: ReverseTutorDatabase
) : GlobalSearchRepository {
    suspend fun index(document: SearchDocument) {
        database.searchDocumentDao().upsert(document.toEntity())
    }

    override suspend fun search(spaceId: String, query: String, limit: Int): List<SearchTarget> =
        searchResults(spaceId, query, limit).map { it.target }

    suspend fun searchResults(spaceId: String, query: String, limit: Int = 50): List<SearchResult> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return emptyList()
        return database.searchDocumentDao().search(spaceId, normalized, limit).map { entity ->
            SearchResult(
                target = SearchTarget(
                    type = entity.entityType.toTargetType(),
                    entityId = entity.entityId,
                    spaceId = entity.spaceId,
                    parentEntityId = entity.parentEntityId,
                    sessionId = entity.sessionId
                ),
                title = entity.title,
                excerpt = entity.body
            )
        }
    }

    private fun SearchDocument.toEntity(): SearchDocumentEntity = SearchDocumentEntity(
        id = id,
        spaceId = spaceId,
        entityType = target.type.name,
        entityId = target.entityId,
        sessionId = target.sessionId,
        parentEntityId = target.parentEntityId,
        title = title,
        body = body,
        normalizedText = normalize("$title $body"),
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun normalize(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun String.toTargetType(): SearchTargetType =
        runCatching { SearchTargetType.valueOf(this) }.getOrDefault(SearchTargetType.Message)
}

data class SearchDocument(
    val id: String,
    val spaceId: String,
    val target: SearchTarget,
    val title: String,
    val body: String,
    val updatedAtEpochMillis: Long
)

data class SearchResult(
    val target: SearchTarget,
    val title: String,
    val excerpt: String
)
