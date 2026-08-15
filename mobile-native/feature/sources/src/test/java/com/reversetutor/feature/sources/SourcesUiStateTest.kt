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
        assertEquals("还没有资料", state.emptyTitle)
        assertEquals("0 份资料", state.summary)
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
        assertEquals("已解析", state.items.single().statusLabel)
        assertEquals("1 个片段", state.items.single().chunkCountLabel)
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

        assertEquals("失败", state.items.single().statusLabel)
        assertEquals("重试", state.items.single().recoveryLabel)
        assertTrue(state.items.single().recoveryEnabled)
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
        assertEquals("图片", item.typeLabel)
        assertEquals("等待能力", item.statusLabel)
        assertEquals("0 个片段", item.chunkCountLabel)
        assertTrue(item.statusDetail.contains("视觉"))
        assertFalse(item.recoveryEnabled)
        assertEquals(null, item.recoveryLabel)
    }

    @Test
    fun parserStatusCopyKeepsNonLocalFilesVisibleAndRecoverable() {
        val state = SourcesUiState.from(
            sources = listOf(
                source(
                    id = "source-deferred",
                    title = "chapter.pdf",
                    type = SourceType.Pdf,
                    status = SourceParserStatus.FutureAssisted
                ),
                source(
                    id = "source-unsupported",
                    title = "archive.bin",
                    type = SourceType.Other,
                    status = SourceParserStatus.Unsupported
                ),
                source(
                    id = "source-failed",
                    title = "empty.txt",
                    type = SourceType.Text,
                    status = SourceParserStatus.Failed
                )
            ),
            lastImport = null
        )

        state.items.forEach { item ->
            assertTrue(item.statusDetail.contains("保留"))
        }
    }

    @Test
    fun `supported source explains available snippets and link context`() {
        val item = SourcesUiState.from(
            sources = listOf(
                source("supported", "guide.md", SourceType.Markdown, SourceParserStatus.FullyLocal)
            ),
            lastImport = null
        ).items.single()

        assertEquals(SourceStatusTone.Success, item.statusTone)
        assertTrue(item.impactMessage.contains("片段"))
        assertTrue(item.recoveryEnabled)
        assertEquals("重新解析", item.recoveryLabel)
        assertTrue(item.evidenceSummary.contains("资料库"))
    }

    @Test
    fun `partial source preserves file and explains partial extraction`() {
        val item = SourcesUiState.from(
            sources = listOf(
                source("partial", "page.html", SourceType.Html, SourceParserStatus.PartiallyLocal)
            ),
            lastImport = null
        ).items.single()

        assertEquals(SourceStatusTone.Warning, item.statusTone)
        assertTrue(item.impactMessage.contains("部分"))
        assertTrue(item.recoveryEnabled)
    }

    @Test
    fun `deferred source preserves file and exposes current limitation`() {
        val item = SourcesUiState.from(
            sources = listOf(
                source("deferred", "book.pdf", SourceType.Pdf, SourceParserStatus.FutureAssisted)
            ),
            lastImport = null
        ).items.single()

        assertEquals(SourceStatusTone.Info, item.statusTone)
        assertFalse(item.recoveryEnabled)
        assertEquals(null, item.recoveryLabel)
        assertTrue(item.recoveryReason.orEmpty().contains("解析能力"))
    }

    @Test
    fun `unsupported source stays visible and does not promise parsing`() {
        val item = SourcesUiState.from(
            sources = listOf(
                source("unsupported", "archive.bin", SourceType.Other, SourceParserStatus.Unsupported)
            ),
            lastImport = null
        ).items.single()

        assertEquals(SourceStatusTone.Disabled, item.statusTone)
        assertFalse(item.recoveryEnabled)
        assertEquals(null, item.recoveryLabel)
        assertTrue(item.impactMessage.contains("保留"))
    }

    @Test
    fun `failed source stays visible and exposes retry`() {
        val item = SourcesUiState.from(
            sources = listOf(
                source("failed", "empty.txt", SourceType.Text, SourceParserStatus.Failed)
            ),
            lastImport = null
        ).items.single()

        assertEquals(SourceStatusTone.Error, item.statusTone)
        assertTrue(item.recoveryEnabled)
        assertEquals("重试", item.recoveryLabel)
    }

    @Test
    fun `image source and chat attachment capability are represented separately`() {
        val item = SourcesUiState.from(
            sources = listOf(
                source("image", "question.png", SourceType.Image, SourceParserStatus.FutureAssisted)
            ),
            lastImport = null
        ).items.single()

        assertTrue(item.impactMessage.contains("图片资料"))
        assertTrue(item.impactMessage.contains("聊天附件"))
    }

    private fun source(
        id: String,
        title: String,
        type: SourceType,
        status: SourceParserStatus
    ): SourceWithChunks =
        SourceWithChunks(
            source = SourceRecord(
                id = id,
                spaceId = "space-1",
                title = title,
                type = type,
                parserStatus = status,
                createdAtEpochMillis = 100L
            ),
            chunks = emptyList()
        )
}
