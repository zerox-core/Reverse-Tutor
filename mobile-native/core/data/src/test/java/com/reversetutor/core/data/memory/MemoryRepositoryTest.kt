package com.reversetutor.core.data.memory

import com.reversetutor.core.data.local.dao.MemoryDao
import com.reversetutor.core.data.local.entity.AnchorEntity
import com.reversetutor.core.data.local.entity.ErrorLogEntity
import com.reversetutor.core.data.local.entity.MemoryItemEntity
import com.reversetutor.core.data.local.entity.NoteEntity
import com.reversetutor.core.model.MemoryItemKind
import com.reversetutor.core.model.ErrorLogOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryRepositoryTest {
    @Test
    fun createsListsAndLinksAnchorsNotesAndErrors() = runBlocking {
        val dao = FakeMemoryDao()
        val repository = MemoryRepository(dao, defaultSpaceId = "space-1")

        repository.createAnchor(
            input = AnchorInput(
                title = "Formula source",
                body = "x^2 - 4 is a difference of squares",
                sourceMessageId = "message-1",
                sourceId = "source-1"
            ),
            nowEpochMillis = 100L,
            anchorId = "anchor-1"
        )
        repository.createNote(
            input = NoteInput(
                title = "Remember factoring",
                body = "Student confused sign changes.",
                sourceMessageId = "message-2"
            ),
            nowEpochMillis = 110L,
            noteId = "note-1"
        )
        repository.logError(
            input = ErrorLogInput(
                title = "Misread exponent",
                detail = "Student treated x^2 as 2x.",
                sourceMessageId = "message-3"
            ),
            nowEpochMillis = 120L,
            errorId = "error-1"
        )

        val snapshot = repository.snapshot()

        assertEquals(listOf("anchor-1"), snapshot.anchors.map { it.id })
        assertEquals("source-1", snapshot.anchors.single().sourceId)
        assertEquals(listOf("note-1"), snapshot.notes.map { it.id })
        assertEquals("message-2", snapshot.notes.single().sourceMessageId)
        assertEquals(listOf("error-1"), snapshot.errors.map { it.id })
        assertFalse(snapshot.errors.single().resolved)
        assertEquals(
            listOf(MemoryItemKind.Error, MemoryItemKind.Note, MemoryItemKind.Requirement),
            snapshot.items.map { it.kind }
        )
    }

    @Test
    fun editsDeletesAndResolvesMemoryRecords() = runBlocking {
        val dao = FakeMemoryDao()
        val repository = MemoryRepository(dao, defaultSpaceId = "space-1")
        repository.createAnchor(AnchorInput("Anchor", "Body"), nowEpochMillis = 100L, anchorId = "anchor-1")
        repository.createNote(NoteInput("Note", "Body"), nowEpochMillis = 110L, noteId = "note-1")
        repository.logError(ErrorLogInput("Error", "Detail"), nowEpochMillis = 120L, errorId = "error-1")

        assertTrue(repository.updateNote("note-1", title = "Updated note", body = "Updated body"))
        assertTrue(repository.resolveError("error-1", resolved = true))
        assertTrue(repository.deleteAnchor("anchor-1"))
        assertFalse(repository.deleteAnchor("missing"))

        val snapshot = repository.snapshot()

        assertEquals(emptyList<String>(), snapshot.anchors.map { it.id })
        assertEquals("Updated note", snapshot.notes.single().title)
        assertTrue(snapshot.errors.single().resolved)
    }

    @Test
    fun retainsOnlyFiftyGenerationDiagnosticsWithoutDeletingLearningErrors() = runBlocking {
        val dao = FakeMemoryDao()
        val repository = MemoryRepository(dao, defaultSpaceId = "space-1")
        repository.logError(
            input = ErrorLogInput("学习记录", "保留的学习错误"),
            nowEpochMillis = 1L,
            errorId = "learning-error"
        )

        repeat(51) { index ->
            repository.logError(
                input = ErrorLogInput("生成失败", "模型服务请求未完成，请稍后重试。"),
                nowEpochMillis = (index + 2).toLong(),
                errorId = "generation-$index",
                origin = ErrorLogOrigin.Generation,
                code = "provider_request_failed"
            )
        }

        val errors = repository.snapshot().errors

        assertEquals(51, errors.size)
        assertTrue(errors.any { it.id == "learning-error" && it.origin == ErrorLogOrigin.Learning })
        assertFalse(errors.any { it.id == "generation-0" })
        assertTrue(errors.all { it.origin == ErrorLogOrigin.Learning || it.code == "provider_request_failed" })
    }
}

private class FakeMemoryDao : MemoryDao {
    private val anchors = linkedMapOf<String, AnchorEntity>()
    private val notes = linkedMapOf<String, NoteEntity>()
    private val errors = linkedMapOf<String, ErrorLogEntity>()
    private val items = linkedMapOf<String, MemoryItemEntity>()

    override suspend fun insertAnchor(anchor: AnchorEntity) {
        anchors[anchor.id] = anchor
    }

    override suspend fun insertNote(note: NoteEntity) {
        notes[note.id] = note
    }

    override suspend fun insertError(error: ErrorLogEntity) {
        errors[error.id] = error
    }

    override suspend fun insertMemoryItem(memoryItem: MemoryItemEntity) {
        items[memoryItem.id] = memoryItem
    }

    override suspend fun listAnchorsBySpace(spaceId: String): List<AnchorEntity> =
        anchors.values.filter { it.spaceId == spaceId }.sortedByDescending { it.createdAtEpochMillis }

    override suspend fun listNotesBySpace(spaceId: String): List<NoteEntity> =
        notes.values.filter { it.spaceId == spaceId }.sortedByDescending { it.createdAtEpochMillis }

    override suspend fun listErrorsBySpace(spaceId: String): List<ErrorLogEntity> =
        errors.values.filter { it.spaceId == spaceId }.sortedByDescending { it.createdAtEpochMillis }

    override suspend fun listMemoryItemsBySpace(spaceId: String): List<MemoryItemEntity> =
        items.values.filter { it.spaceId == spaceId }.sortedByDescending { it.createdAtEpochMillis }

    override suspend fun updateNote(id: String, title: String, body: String): Int {
        val existing = notes[id] ?: return 0
        notes[id] = existing.copy(title = title, body = body)
        return 1
    }

    override suspend fun deleteNote(id: String): Int =
        if (notes.remove(id) == null) 0 else 1

    override suspend fun deleteAnchor(id: String): Int =
        if (anchors.remove(id) == null) 0 else 1

    override suspend fun setErrorResolved(id: String, resolved: Boolean): Int {
        val existing = errors[id] ?: return 0
        errors[id] = existing.copy(resolved = resolved)
        return 1
    }

    override suspend fun deleteError(id: String): Int =
        if (errors.remove(id) == null) 0 else 1

    override suspend fun deleteMemoryItem(id: String): Int =
        if (items.remove(id) == null) 0 else 1
}
