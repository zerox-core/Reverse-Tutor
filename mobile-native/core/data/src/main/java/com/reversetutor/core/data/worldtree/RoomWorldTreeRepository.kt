package com.reversetutor.core.data.worldtree

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.WorldTreeDraftEntity
import com.reversetutor.core.data.local.entity.WorldTreeSectionEntity
import com.reversetutor.core.data.local.entity.WorldTreeSourceCrossRef
import com.reversetutor.core.domain.WorldTreeRepository
import com.reversetutor.core.model.CreateWorldTreeDraftCommand
import com.reversetutor.core.model.SourceLibraryPayload
import com.reversetutor.core.model.WorldTreeDraft
import com.reversetutor.core.model.WorldTreeDraftState
import com.reversetutor.core.model.WorldTreeMode
import com.reversetutor.core.model.WorldTreeSection
import com.reversetutor.core.model.WorldTreeSectionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomWorldTreeRepository(
    private val database: ReverseTutorDatabase,
    private val codec: WorldTreePayloadCodec = WorldTreePayloadCodec()
) : WorldTreeRepository {
    private val dao = database.worldTreeDao()

    override fun observeDraft(draftId: String): Flow<WorldTreeDraft?> = combine(
        dao.observeDraft(draftId),
        dao.observeSections(draftId),
        dao.observeSourceIds(draftId)
    ) { draft, sections, sourceIds ->
        draft?.toDomain(sections, sourceIds)
    }

    override suspend fun getDraft(draftId: String): WorldTreeDraft? = database.withTransaction {
        loadDraft(draftId)
    }

    override suspend fun createDraft(command: CreateWorldTreeDraftCommand): WorldTreeDraft =
        database.withTransaction {
            val draftId = command.id.trim()
            val title = command.title.trim()
            val spaceId = command.spaceId.trim()
            require(draftId.isNotEmpty()) { "WorldTree draft id is required." }
            require(title.isNotEmpty()) { "WorldTree title is required." }
            require(spaceId.isNotEmpty()) { "WorldTree space id is required." }
            require(dao.getDraft(draftId) == null) { "WorldTree draft already exists: $draftId" }
            val sections = normalizeSections(command.sections, command.sourceIds)
            validateSources(spaceId, command.sourceIds)
            val entity = WorldTreeDraftEntity(
                id = draftId,
                spaceId = spaceId,
                templateId = command.templateId?.trim()?.takeIf { it.isNotEmpty() },
                title = title,
                mode = command.mode.name,
                schemaVersion = SupportedSchemaVersion,
                state = command.state.name,
                createdAtEpochMillis = command.nowEpochMillis,
                updatedAtEpochMillis = command.nowEpochMillis
            )
            dao.insertDraft(entity)
            dao.upsertSections(sections.map { it.toEntity(draftId, command.nowEpochMillis) })
            replaceSourceLinksInternal(draftId, command.sourceIds)
            requireDraft(draftId).also(::validateReadyState)
        }

    override suspend fun updateTitle(
        draftId: String,
        title: String,
        nowEpochMillis: Long
    ): WorldTreeDraft = database.withTransaction {
        val normalized = title.trim()
        require(normalized.isNotEmpty()) { "WorldTree title is required." }
        require(dao.updateTitle(draftId, normalized, nowEpochMillis) > 0) { "WorldTree draft not found." }
        requireDraft(draftId)
    }

    override suspend fun upsertSection(
        draftId: String,
        section: WorldTreeSection,
        nowEpochMillis: Long
    ): WorldTreeDraft = database.withTransaction {
        val draft = requireDraft(draftId)
        require(section.isPayloadCompatible()) { "WorldTree payload does not match ${section.type}." }
        val current = draft.sections
        val updated = current.filterNot { it.id == section.id } + section
        val sourceIds = (section.payload as? SourceLibraryPayload)?.sourceIds ?: draft.sourceIds
        val normalized = normalizeSections(updated, sourceIds)
        validateSources(draft.spaceId, sourceIds)
        replaceSectionsInternal(draftId, normalized, nowEpochMillis)
        if (section.type == WorldTreeSectionType.SourceLibrary) {
            replaceSourceLinksInternal(draftId, sourceIds)
        }
        dao.touch(draftId, nowEpochMillis)
        requireDraft(draftId).also(::validateReadyState)
    }

    override suspend fun reorderSections(
        draftId: String,
        sectionIds: List<String>,
        nowEpochMillis: Long
    ): WorldTreeDraft = database.withTransaction {
        val draft = requireDraft(draftId)
        require(sectionIds.size == sectionIds.distinct().size) { "WorldTree section order contains duplicates." }
        require(sectionIds.toSet() == draft.sections.map { it.id }.toSet()) {
            "WorldTree section order must contain every existing section exactly once."
        }
        val byId = draft.sections.associateBy { it.id }
        val reordered = sectionIds.mapIndexed { index, id -> byId.getValue(id).copy(order = index) }
        replaceSectionsInternal(draftId, reordered, nowEpochMillis)
        dao.touch(draftId, nowEpochMillis)
        requireDraft(draftId)
    }

    override suspend fun removeCustomSection(
        draftId: String,
        sectionId: String,
        nowEpochMillis: Long
    ): WorldTreeDraft = database.withTransaction {
        val draft = requireDraft(draftId)
        val section = draft.sections.firstOrNull { it.id == sectionId }
            ?: throw IllegalArgumentException("WorldTree section not found.")
        require(section.type == WorldTreeSectionType.Custom) { "Only custom WorldTree sections can be removed." }
        val remaining = draft.sections.filterNot { it.id == sectionId }
            .mapIndexed { index, item -> item.copy(order = index) }
        replaceSectionsInternal(draftId, remaining, nowEpochMillis)
        dao.touch(draftId, nowEpochMillis)
        requireDraft(draftId)
    }

    override suspend fun replaceSourceLinks(
        draftId: String,
        sourceIds: List<String>,
        nowEpochMillis: Long
    ): WorldTreeDraft = database.withTransaction {
        val draft = requireDraft(draftId)
        validateSources(draft.spaceId, sourceIds)
        replaceSourceLinksInternal(draftId, sourceIds)
        val sections = draft.sections.map { section ->
            if (section.type == WorldTreeSectionType.SourceLibrary) {
                section.copy(payload = SourceLibraryPayload(sourceIds.distinct()))
            } else {
                section
            }
        }
        replaceSectionsInternal(draftId, sections, nowEpochMillis)
        dao.touch(draftId, nowEpochMillis)
        requireDraft(draftId)
    }

    override suspend fun attachToSession(
        draftId: String,
        sessionId: String,
        nowEpochMillis: Long
    ): WorldTreeDraft = database.withTransaction {
        val draft = requireDraft(draftId)
        val session = database.sessionDao().getById(sessionId)
            ?: throw IllegalArgumentException("Session not found.")
        require(session.spaceId == draft.spaceId) { "WorldTree and session must belong to the same space." }
        val existing = dao.getDraftBySessionId(sessionId)
        require(existing == null || existing.id == draftId) { "Session already has a WorldTree draft." }
        require(dao.attachToSession(draftId, sessionId, nowEpochMillis) > 0) { "WorldTree draft not found." }
        requireDraft(draftId)
    }

    override suspend fun archive(draftId: String, nowEpochMillis: Long) {
        require(dao.archive(draftId, nowEpochMillis) > 0) { "WorldTree draft not found." }
    }

    private suspend fun loadDraft(draftId: String): WorldTreeDraft? {
        val entity = dao.getDraft(draftId) ?: return null
        return entity.toDomain(dao.listSections(draftId), dao.listSourceIds(draftId))
    }

    private suspend fun requireDraft(draftId: String): WorldTreeDraft =
        loadDraft(draftId) ?: throw IllegalArgumentException("WorldTree draft not found: $draftId")

    private suspend fun validateSources(spaceId: String, sourceIds: List<String>) {
        val unique = sourceIds.map(String::trim).filter(String::isNotEmpty).distinct()
        require(unique.size == sourceIds.size) { "WorldTree source ids must be non-empty and unique." }
        if (unique.isNotEmpty()) {
            require(dao.countSourcesInSpace(spaceId, unique) == unique.size) {
                "WorldTree contains an unknown or cross-space source id."
            }
        }
    }

    private fun normalizeSections(
        sections: List<WorldTreeSection>,
        sourceIds: List<String>
    ): List<WorldTreeSection> {
        require(sections.size == sections.map { it.id }.distinct().size) { "WorldTree section ids must be unique." }
        require(sections.size == sections.map { it.order }.distinct().size) { "WorldTree section order must be unique." }
        require(sections.all { it.id.isNotBlank() && it.title.isNotBlank() }) { "WorldTree section id and title are required." }
        require(sections.all(WorldTreeSection::isPayloadCompatible)) { "WorldTree section payload type mismatch." }
        val sorted = sections.sortedBy { it.order }
        require(sorted.map { it.order } == sorted.indices.toList()) { "WorldTree section order must be contiguous from zero." }
        return sorted.map { section ->
            if (section.type == WorldTreeSectionType.SourceLibrary) {
                section.copy(payload = SourceLibraryPayload(sourceIds.distinct()))
            } else {
                section
            }
        }
    }

    private suspend fun replaceSectionsInternal(
        draftId: String,
        sections: List<WorldTreeSection>,
        nowEpochMillis: Long
    ) {
        dao.deleteSections(draftId)
        if (sections.isNotEmpty()) {
            dao.upsertSections(sections.map { it.toEntity(draftId, nowEpochMillis) })
        }
    }

    private suspend fun replaceSourceLinksInternal(
        draftId: String,
        sourceIds: List<String>
    ) {
        dao.deleteSourceLinks(draftId)
        val links = sourceIds.distinct().mapIndexed { index, sourceId ->
            WorldTreeSourceCrossRef(draftId, sourceId, index)
        }
        if (links.isNotEmpty()) dao.upsertSourceLinks(links)
    }

    private fun validateReadyState(draft: WorldTreeDraft) {
        if (draft.state == WorldTreeDraftState.Ready) {
            require(draft.isReady) { "Ready WorldTree drafts require all required sections to be complete." }
        }
    }

    private fun WorldTreeDraftEntity.toDomain(
        sectionEntities: List<WorldTreeSectionEntity>,
        sourceIds: List<String>
    ): WorldTreeDraft = WorldTreeDraft(
        id = id,
        spaceId = spaceId,
        sessionId = sessionId,
        templateId = templateId,
        title = title,
        mode = enumValueOrThrow(mode, "WorldTree mode"),
        schemaVersion = schemaVersion,
        sections = sectionEntities.map { it.toDomain(schemaVersion, sourceIds) },
        sourceIds = sourceIds,
        state = enumValueOrThrow(state, "WorldTree state"),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

    private fun WorldTreeSectionEntity.toDomain(
        schemaVersion: Int,
        linkedSourceIds: List<String>
    ): WorldTreeSection {
        val sectionType = enumValueOrThrow<WorldTreeSectionType>(type, "WorldTree section type")
        val decoded = codec.decode(schemaVersion, sectionType, payloadJson)
        return WorldTreeSection(
            id = id,
            type = sectionType,
            title = title,
            order = orderIndex,
            payload = if (sectionType == WorldTreeSectionType.SourceLibrary) {
                SourceLibraryPayload(linkedSourceIds)
            } else {
                decoded
            },
            required = required,
            completed = completed
        )
    }

    private fun WorldTreeSection.toEntity(
        draftId: String,
        nowEpochMillis: Long
    ): WorldTreeSectionEntity = WorldTreeSectionEntity(
        id = id,
        draftId = draftId,
        type = type.name,
        title = title,
        orderIndex = order,
        payloadJson = codec.encode(SupportedSchemaVersion, type, payload),
        required = required,
        completed = completed,
        updatedAtEpochMillis = nowEpochMillis
    )

    private inline fun <reified T : Enum<T>> enumValueOrThrow(value: String, label: String): T =
        runCatching { enumValueOf<T>(value) }
            .getOrElse { throw WorldTreePayloadCodecException("Unknown $label: $value", it) }

    private companion object {
        const val SupportedSchemaVersion = 1
    }
}
