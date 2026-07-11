package com.reversetutor.core.data.session

import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.dao.SessionSettingsDao
import com.reversetutor.core.data.local.dao.SpaceDao
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.model.SessionSettings
import com.reversetutor.core.model.TutorSession

class SessionRepository(
    private val spaceDao: SpaceDao,
    private val sessionDao: SessionDao,
    private val sessionSettingsDao: SessionSettingsDao
) : com.reversetutor.core.domain.SessionRepository {
    override suspend fun sessionExists(sessionId: String): Boolean =
        sessionDao.getById(sessionId) != null

    suspend fun ensurePreviewSeed(nowEpochMillis: Long) {
        ensureDefaultSpace(nowEpochMillis)

        if (sessionDao.getById("preview-session-1") == null) {
            sessionDao.upsert(
                SessionEntity(
                    id = "preview-session-1",
                    spaceId = defaultSpaceId,
                    title = "Reverse Tutor preview",
                    createdAtEpochMillis = nowEpochMillis - 2_000L,
                    updatedAtEpochMillis = nowEpochMillis - 1_000L,
                    pinned = true
                )
            )
        }
        if (sessionDao.getById("preview-session-2") == null) {
            sessionDao.upsert(
                SessionEntity(
                    id = "preview-session-2",
                    spaceId = defaultSpaceId,
                    title = "Learning context draft",
                    createdAtEpochMillis = nowEpochMillis - 4_000L,
                    updatedAtEpochMillis = nowEpochMillis - 3_000L
                )
            )
        }
    }

    suspend fun createSession(
        input: SessionCreationInput,
        nowEpochMillis: Long,
        sessionId: String = defaultSessionId(nowEpochMillis, input.title)
    ): CreatedSession {
        val normalized = input.normalized()
        require(normalized.title.isNotEmpty()) { "Session title is required." }
        require(normalized.role.isNotEmpty()) { "Session role is required." }
        require(normalized.goal.isNotEmpty()) { "Session goal is required." }
        require(normalized.profileText.isNotEmpty()) { "Session profile is required." }

        ensureDefaultSpace(nowEpochMillis)
        val settingsId = "settings-$sessionId"
        val session = TutorSession(
            id = sessionId,
            spaceId = defaultSpaceId,
            title = normalized.title,
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            settingsId = settingsId
        )
        val settings = SessionSettingsEntity(
            id = settingsId,
            spaceId = defaultSpaceId,
            sessionId = sessionId,
            systemPrompt = normalized.toSystemPrompt()
        )

        sessionDao.upsert(session.toEntity())
        sessionSettingsDao.upsert(settings)

        return CreatedSession(
            session = session,
            settings = settings.toDomain()
        )
    }

    suspend fun getSessionSettings(sessionId: String): SessionSettings? =
        sessionSettingsDao.getBySessionId(sessionId)?.toDomain()

    private suspend fun ensureDefaultSpace(nowEpochMillis: Long) {
        if (spaceDao.getById(defaultSpaceId) == null) {
            spaceDao.upsert(
                SpaceEntity(
                    id = defaultSpaceId,
                    name = "Default",
                    kind = "Default",
                    createdAtEpochMillis = nowEpochMillis,
                    updatedAtEpochMillis = nowEpochMillis
                )
            )
        }
    }

    suspend fun saveSession(session: TutorSession) {
        sessionDao.upsert(session.toEntity())
    }

    suspend fun getSession(id: String): TutorSession? =
        sessionDao.getById(id)?.toDomain()

    suspend fun listSessions(spaceId: String = defaultSpaceId): List<TutorSession> =
        sessionDao.listBySpace(spaceId).map { it.toDomain() }

    suspend fun renameSession(
        id: String,
        title: String,
        updatedAtEpochMillis: Long
    ): Boolean {
        val normalized = title.trim()
        if (normalized.isEmpty()) return false
        return sessionDao.rename(id, normalized, updatedAtEpochMillis) > 0
    }

    suspend fun setPinned(
        id: String,
        pinned: Boolean,
        updatedAtEpochMillis: Long
    ): Boolean = sessionDao.setPinned(id, pinned, updatedAtEpochMillis) > 0

    suspend fun archiveSession(
        id: String,
        updatedAtEpochMillis: Long
    ): Boolean = sessionDao.archive(id, updatedAtEpochMillis) > 0

    companion object {
        const val defaultSpaceId = "default-space"

        private fun defaultSessionId(nowEpochMillis: Long, title: String): String {
            val slug = title.trim()
                .lowercase()
                .filter { it.isLetterOrDigit() }
                .take(12)
                .ifEmpty { "session" }
            return "session-$nowEpochMillis-$slug"
        }
    }
}

data class SessionCreationInput(
    val title: String,
    val role: String,
    val goal: String,
    val profileText: String,
    val templateId: String? = null,
    val sourceHandoffRequested: Boolean = false
) {
    fun normalized(): SessionCreationInput =
        copy(
            title = title.trim(),
            role = role.trim(),
            goal = goal.trim(),
            profileText = profileText.trim(),
            templateId = templateId?.trim()?.ifEmpty { null }
        )

    fun toSystemPrompt(): String = buildString {
        appendLine("Role: $role")
        appendLine("Goal: $goal")
        appendLine("Profile: $profileText")
        if (templateId != null) {
            appendLine("Template: $templateId")
        }
        if (sourceHandoffRequested) {
            appendLine("Initial source: Deferred handoff to :feature:sources.")
        }
    }.trim()
}

data class CreatedSession(
    val session: TutorSession,
    val settings: SessionSettings
)
