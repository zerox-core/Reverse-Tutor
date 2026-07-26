package com.reversetutor.preview.shell

import com.reversetutor.core.data.sources.SourceImportResult
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.model.SourceChunk
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.SourceRecord
import com.reversetutor.core.model.SourceType
import com.reversetutor.core.model.TutorSession
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSettingsProductionAdaptersTest {
    @Test
    fun importMapperRejectsUnsupportedFailedErrorsAndEmptyContentWithoutReplacingTarget() {
        val cases = listOf(
            importResult(SourceParserStatus.Unsupported),
            importResult(SourceParserStatus.Failed),
            importResult(SourceParserStatus.FullyLocal, errors = listOf("parse error")),
            importResult(SourceParserStatus.FullyLocal, text = "", chunks = emptyList())
        )

        cases.forEach { result ->
            val outcome = mapSessionSettingsImport(
                result = result,
                currentSessionId = "session-a",
                replacingSourceId = "source-old",
                lastUsedAtEpochMillis = 50L
            )

            assertTrue(outcome is SessionSourceImportOutcome.Rejected)
            assertEquals("source-old", (outcome as SessionSourceImportOutcome.Rejected).replacingSourceId)
            assertTrue(outcome.message.isNotBlank())
        }
    }

    @Test
    fun usableImportMapsOneReplacementAndLatestOwnersIncludeArchivedAndPendingUntilFinalized() {
        val usable = mapSessionSettingsImport(
            result = importResult(SourceParserStatus.FullyLocal, text = "usable", chunks = listOf("usable")),
            currentSessionId = "session-a",
            replacingSourceId = "source-old",
            lastUsedAtEpochMillis = 50L
        ) as SessionSourceImportOutcome.Usable
        assertEquals("source-old", usable.picked.replacingSourceId)

        val sessions = listOf(
            session("session-a"),
            session("session-archived", archived = true),
            session("session-pending")
        )
        val snapshots = sessions.associate { session ->
            session.id to NewSessionConfiguration(sourceSelections = listOf("source-a"))
        }
        val sources = listOf(sourceWithChunks("source-a", "讲义"))

        val beforeFinalize = buildSessionSettingsSourceCatalog(
            currentSessionId = "session-a",
            sessions = sessions,
            sessionSnapshots = snapshots,
            favorites = listOf(NewSessionFavorite("fav", "收藏", NewSessionConfiguration(sourceSelections = listOf("source-a")), 1L)),
            sources = sources,
            lastUsedAt = emptyMap()
        )
        assertEquals(
            listOf("session-a", "session-archived", "session-pending", "favorite:fav"),
            beforeFinalize.single().referenceOwnerIds
        )

        val afterFinalize = buildSessionSettingsSourceCatalog(
            currentSessionId = "session-a",
            sessions = sessions.filterNot { it.id == "session-pending" },
            sessionSnapshots = snapshots - "session-pending",
            favorites = emptyList(),
            sources = sources,
            lastUsedAt = emptyMap()
        )
        assertEquals(listOf("session-a", "session-archived"), afterFinalize.single().referenceOwnerIds)
    }

    private fun importResult(
        status: SourceParserStatus,
        text: String? = null,
        chunks: List<String> = emptyList(),
        errors: List<String> = emptyList()
    ) = SourceImportResult(
        source = SourceRecord("source-new", "space", "new.txt", SourceType.Text, status, 1L, extractedText = text),
        chunks = chunks.mapIndexed { index, body -> SourceChunk("chunk-$index", "space", "source-new", index, body, 1) },
        warnings = emptyList(),
        errors = errors
    )

    private fun sourceWithChunks(id: String, title: String) = SourceWithChunks(
        source = SourceRecord(id, "space", title, SourceType.Text, SourceParserStatus.FullyLocal, 10L, extractedText = "body"),
        chunks = listOf(SourceChunk("chunk", "space", id, 0, "body", 1))
    )

    private fun session(id: String, archived: Boolean = false) = TutorSession(
        id = id,
        spaceId = "space",
        title = id,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
        archived = archived
    )
}
