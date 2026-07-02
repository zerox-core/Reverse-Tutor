package com.reversetutor.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpaceDomainModelTest {
    @Test
    fun importedSpaceCarriesUserVisiblePartitionMetadata() {
        val space = Space(
            id = "space-imported",
            name = "Imported backup",
            kind = SpaceKind.Imported,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 200L,
            sourceImportId = "import-1"
        )

        assertEquals("space-imported", space.id)
        assertEquals("Imported backup", space.name)
        assertEquals(SpaceKind.Imported, space.kind)
        assertEquals(100L, space.createdAtEpochMillis)
        assertEquals(200L, space.updatedAtEpochMillis)
        assertEquals("import-1", space.sourceImportId)
    }

    @Test
    fun defaultSpaceHasNoImportSource() {
        val space = Space(
            id = "space-default",
            name = "Default",
            kind = SpaceKind.Default,
            createdAtEpochMillis = 100L,
            updatedAtEpochMillis = 100L
        )

        assertNull(space.sourceImportId)
    }

    @Test
    fun userDataDomainModelsExposeSpaceOwnership() {
        assertEquals(
            "space-1",
            TutorSession(
                id = "session-1",
                spaceId = "space-1",
                title = "Session",
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            Message(
                id = "message-1",
                spaceId = "space-1",
                sessionId = "session-1",
                role = MessageRole.User,
                text = "Hello",
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            LlmProfile(
                id = "profile-1",
                spaceId = "space-1",
                name = "Default model",
                provider = LlmProviderKind.Custom,
                model = "demo",
                secretRef = null,
                createdAtEpochMillis = 100L,
                updatedAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            MemoryItem(
                id = "memory-1",
                spaceId = "space-1",
                kind = MemoryItemKind.Note,
                title = "Memory",
                body = "Body",
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            Anchor(
                id = "anchor-1",
                spaceId = "space-1",
                title = "Anchor",
                body = "Body",
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            Note(
                id = "note-1",
                spaceId = "space-1",
                title = "Note",
                body = "Body",
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            ErrorLog(
                id = "error-1",
                spaceId = "space-1",
                title = "Error",
                detail = "Detail",
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            GraphNode(
                id = "node-1",
                spaceId = "space-1",
                label = "Node",
                kind = GraphNodeKind.Concept,
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            GraphEdge(
                id = "edge-1",
                spaceId = "space-1",
                fromNodeId = "node-1",
                toNodeId = "node-2",
                relation = "relates_to",
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            SourceRecord(
                id = "source-1",
                spaceId = "space-1",
                title = "Source",
                type = SourceType.Markdown,
                parserStatus = SourceParserStatus.FullyLocal,
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            SourceChunk(
                id = "chunk-1",
                spaceId = "space-1",
                sourceId = "source-1",
                chunkIndex = 0,
                text = "Chunk"
            ).spaceId
        )
        assertEquals(
            "space-1",
            BackgroundJob(
                id = "job-1",
                spaceId = "space-1",
                kind = BackgroundJobKind.Generation,
                status = BackgroundJobStatus.Queued,
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            ExportRecord(
                id = "export-1",
                spaceId = "space-1",
                schema = "reverse-tutor.export",
                targetFileName = "backup.json",
                status = ExportStatus.Created,
                createdAtEpochMillis = 100L
            ).spaceId
        )
        assertEquals(
            "space-1",
            SessionSettings(
                id = "settings-1",
                spaceId = "space-1",
                sessionId = "session-1",
                llmProfileId = "profile-1"
            ).spaceId
        )
    }
}
