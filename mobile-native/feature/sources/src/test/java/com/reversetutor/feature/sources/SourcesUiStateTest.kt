package com.reversetutor.feature.sources

import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.model.SourceChunk
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.SourceRecord
import com.reversetutor.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesUiStateTest {
    @Test
    fun emptyStateInvitesImportWithoutHidingUnsupportedFilesPolicy() {
        val state = SourcesUiState.from(sources = emptyList(), lastImport = null)

        assertTrue(state.isEmpty)
        assertEquals("No sources yet", state.emptyTitle)
        assertEquals("0 sources", state.summary)
    }

    @Test
    fun sourceCardsExposeParserStatusAndSnippets() {
        val state = SourcesUiState.from(
            sources = listOf(
                SourceWithChunks(
                    source = SourceRecord(
                        id = "source-1",
                        spaceId = "space-1",
                        title = "Guide.md",
                        type = SourceType.Markdown,
                        parserStatus = SourceParserStatus.FullyLocal,
                        createdAtEpochMillis = 100L
                    ),
                    chunks = listOf(
                        SourceChunk(
                            id = "chunk-1",
                            spaceId = "space-1",
                            sourceId = "source-1",
                            chunkIndex = 0,
                            text = "This is the first chunk."
                        )
                    )
                )
            ),
            lastImport = null
        )

        assertFalse(state.isEmpty)
        assertEquals("supported_local", state.items.single().statusLabel)
        assertEquals("1 chunk", state.items.single().chunkCountLabel)
        assertEquals(listOf("This is the first chunk."), state.items.single().snippets)
    }

    @Test
    fun failedSourceShowsRetryAction() {
        val state = SourcesUiState.from(
            sources = listOf(
                SourceWithChunks(
                    source = SourceRecord(
                        id = "source-1",
                        spaceId = "space-1",
                        title = "empty.txt",
                        type = SourceType.Text,
                        parserStatus = SourceParserStatus.Failed,
                        createdAtEpochMillis = 100L
                    ),
                    chunks = emptyList()
                )
            ),
            lastImport = null
        )

        assertEquals("failed", state.items.single().statusLabel)
        assertEquals("Retry", state.items.single().actionLabel)
    }

    @Test
    fun complexAndImageSourcesShowQueuedParserStatusWithoutSnippets() {
        val state = SourcesUiState.from(
            sources = listOf(
                SourceWithChunks(
                    source = SourceRecord(
                        id = "source-image",
                        spaceId = "space-1",
                        title = "question.png",
                        type = SourceType.Image,
                        parserStatus = SourceParserStatus.FutureAssisted,
                        createdAtEpochMillis = 100L,
                        uri = "content://sources/question.png"
                    ),
                    chunks = emptyList()
                )
            ),
            lastImport = null
        )

        val item = state.items.single()
        assertEquals("Image", item.typeLabel)
        assertEquals("queued_for_future_api", item.statusLabel)
        assertEquals("0 chunks", item.chunkCountLabel)
        assertTrue(item.statusDetail.contains("vision", ignoreCase = true))
        assertEquals("Reprocess", item.actionLabel)
    }
}
