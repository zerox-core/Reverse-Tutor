package com.reversetutor.core.data.memory

import com.reversetutor.core.data.local.dao.MemoryDao
import com.reversetutor.core.data.local.entity.toDomain
import com.reversetutor.core.data.local.entity.toEntity
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.Anchor
import com.reversetutor.core.model.ErrorLog
import com.reversetutor.core.model.MemoryItem
import com.reversetutor.core.model.MemoryItemKind
import com.reversetutor.core.model.Note
import java.util.UUID

class MemoryRepository(
    private val memoryDao: MemoryDao,
    private val defaultSpaceId: String = SessionRepository.defaultSpaceId
) {
    suspend fun snapshot(spaceId: String = defaultSpaceId): MemorySnapshot =
        MemorySnapshot(
            anchors = memoryDao.listAnchorsBySpace(spaceId).map { it.toDomain() },
            notes = memoryDao.listNotesBySpace(spaceId).map { it.toDomain() },
            errors = memoryDao.listErrorsBySpace(spaceId).map { it.toDomain() },
            items = memoryDao.listMemoryItemsBySpace(spaceId).map { it.toDomain() }
        )

    suspend fun createAnchor(
        input: AnchorInput,
        nowEpochMillis: Long,
        spaceId: String = defaultSpaceId,
        anchorId: String = "anchor-${UUID.randomUUID()}"
    ): Anchor? {
        val normalized = input.normalized() ?: return null
        val anchor = Anchor(
            id = anchorId,
            spaceId = spaceId,
            title = normalized.title,
            body = normalized.body,
            createdAtEpochMillis = nowEpochMillis,
            sourceMessageId = normalized.sourceMessageId,
            sourceId = normalized.sourceId
        )
        memoryDao.insertAnchor(anchor.toEntity())
        memoryDao.insertMemoryItem(anchor.toMemoryItem().toEntity())
        return anchor
    }

    suspend fun createNote(
        input: NoteInput,
        nowEpochMillis: Long,
        spaceId: String = defaultSpaceId,
        noteId: String = "note-${UUID.randomUUID()}"
    ): Note? {
        val normalized = input.normalized() ?: return null
        val note = Note(
            id = noteId,
            spaceId = spaceId,
            title = normalized.title,
            body = normalized.body,
            createdAtEpochMillis = nowEpochMillis,
            sourceMessageId = normalized.sourceMessageId
        )
        memoryDao.insertNote(note.toEntity())
        memoryDao.insertMemoryItem(note.toMemoryItem().toEntity())
        return note
    }

    suspend fun logError(
        input: ErrorLogInput,
        nowEpochMillis: Long,
        spaceId: String = defaultSpaceId,
        errorId: String = "error-${UUID.randomUUID()}"
    ): ErrorLog? {
        val normalized = input.normalized() ?: return null
        val error = ErrorLog(
            id = errorId,
            spaceId = spaceId,
            title = normalized.title,
            detail = normalized.detail,
            createdAtEpochMillis = nowEpochMillis,
            sourceMessageId = normalized.sourceMessageId,
            resolved = false
        )
        memoryDao.insertError(error.toEntity())
        memoryDao.insertMemoryItem(error.toMemoryItem().toEntity())
        return error
    }

    suspend fun updateNote(
        id: String,
        title: String,
        body: String
    ): Boolean {
        val normalized = NoteInput(title = title, body = body).normalized() ?: return false
        return memoryDao.updateNote(id, normalized.title, normalized.body) > 0
    }

    suspend fun deleteNote(id: String): Boolean =
        memoryDao.deleteNote(id) > 0

    suspend fun deleteAnchor(id: String): Boolean =
        memoryDao.deleteAnchor(id) > 0

    suspend fun resolveError(
        id: String,
        resolved: Boolean
    ): Boolean =
        memoryDao.setErrorResolved(id, resolved) > 0
}

data class MemorySnapshot(
    val anchors: List<Anchor>,
    val notes: List<Note>,
    val errors: List<ErrorLog>,
    val items: List<MemoryItem>
)

data class AnchorInput(
    val title: String,
    val body: String,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
) {
    fun normalized(): AnchorInput? {
        val normalizedTitle = title.trim()
        val normalizedBody = body.trim()
        if (normalizedTitle.isEmpty() || normalizedBody.isEmpty()) return null
        return copy(
            title = normalizedTitle,
            body = normalizedBody,
            sourceMessageId = sourceMessageId?.trim()?.takeIf { it.isNotEmpty() },
            sourceId = sourceId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

data class NoteInput(
    val title: String,
    val body: String,
    val sourceMessageId: String? = null
) {
    fun normalized(): NoteInput? {
        val normalizedTitle = title.trim()
        val normalizedBody = body.trim()
        if (normalizedTitle.isEmpty() || normalizedBody.isEmpty()) return null
        return copy(
            title = normalizedTitle,
            body = normalizedBody,
            sourceMessageId = sourceMessageId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

data class ErrorLogInput(
    val title: String,
    val detail: String,
    val sourceMessageId: String? = null
) {
    fun normalized(): ErrorLogInput? {
        val normalizedTitle = title.trim()
        val normalizedDetail = detail.trim()
        if (normalizedTitle.isEmpty() || normalizedDetail.isEmpty()) return null
        return copy(
            title = normalizedTitle,
            detail = normalizedDetail,
            sourceMessageId = sourceMessageId?.trim()?.takeIf { it.isNotEmpty() }
        )
    }
}

private fun Anchor.toMemoryItem(): MemoryItem =
    MemoryItem(
        id = "memory-$id",
        spaceId = spaceId,
        kind = MemoryItemKind.Requirement,
        title = title,
        body = body,
        createdAtEpochMillis = createdAtEpochMillis,
        sourceMessageId = sourceMessageId,
        sourceId = sourceId
    )

private fun Note.toMemoryItem(): MemoryItem =
    MemoryItem(
        id = "memory-$id",
        spaceId = spaceId,
        kind = MemoryItemKind.Note,
        title = title,
        body = body,
        createdAtEpochMillis = createdAtEpochMillis,
        sourceMessageId = sourceMessageId
    )

private fun ErrorLog.toMemoryItem(): MemoryItem =
    MemoryItem(
        id = "memory-$id",
        spaceId = spaceId,
        kind = MemoryItemKind.Error,
        title = title,
        body = detail,
        createdAtEpochMillis = createdAtEpochMillis,
        sourceMessageId = sourceMessageId
    )
