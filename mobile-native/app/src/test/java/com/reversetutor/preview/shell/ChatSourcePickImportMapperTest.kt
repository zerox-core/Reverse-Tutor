package com.reversetutor.preview.shell

import com.reversetutor.core.data.sources.SourceImportResult
import com.reversetutor.core.model.SourceChunk
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.SourceRecord
import com.reversetutor.core.model.SourceType
import com.reversetutor.feature.chat.NewSessionConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-006 Task 3: contracts for the in-chat "从手机选择资料" import flow.
 * A usable import must bind the new source to the current session snapshot
 * (distinct, appended) and surface a safe notice; unusable imports and missing
 * sessions must surface a retryable failure notice.
 */
class ChatSourcePickImportMapperTest {

    @Test
    fun usableImportBindsSourceToCurrentSessionWithDistinctSelection() {
        val outcome = mapChatSourcePickImport(
            imported = usableImport("source-9"),
            sessionId = "session-1",
            currentSnapshot = NewSessionConfiguration(sourceSelections = listOf("source-1", "source-9"))
        )

        val bound = outcome as ChatSourcePickImportOutcome.Bound
        assertEquals(listOf("source-1", "source-9"), bound.snapshot.sourceSelections)
        assertEquals("资料已加入本会话，将用于后续回复。", bound.notice)
    }

    @Test
    fun usableImportIsAppendedAfterExistingSelections() {
        val outcome = mapChatSourcePickImport(
            imported = usableImport("source-9"),
            sessionId = "session-1",
            currentSnapshot = NewSessionConfiguration(sourceSelections = listOf("source-1"))
        ) as ChatSourcePickImportOutcome.Bound

        assertEquals(listOf("source-1", "source-9"), outcome.snapshot.sourceSelections)
    }

    @Test
    fun unusableOrMissingSessionImportsAreRejectedWithRetryNotice() {
        val unusable = listOf(
            importResult(SourceParserStatus.Failed, text = "正文", chunks = listOf("正文")),
            importResult(SourceParserStatus.Unsupported, text = "正文", chunks = listOf("正文")),
            importResult(SourceParserStatus.FullyLocal, text = "", chunks = emptyList())
        )

        unusable.forEach { imported ->
            val outcome = mapChatSourcePickImport(imported, "session-1", NewSessionConfiguration())
            assertTrue("expected rejection for $imported", outcome is ChatSourcePickImportOutcome.Rejected)
            assertTrue(
                (outcome as ChatSourcePickImportOutcome.Rejected).notice.isNotBlank()
            )
        }

        val noSession = mapChatSourcePickImport(
            usableImport("source-9"),
            sessionId = null,
            currentSnapshot = NewSessionConfiguration()
        )
        assertTrue(noSession is ChatSourcePickImportOutcome.Rejected)
        assertTrue((noSession as ChatSourcePickImportOutcome.Rejected).notice.isNotBlank())
    }

    // NEWMP-V1-006 Task 3 Red: a readable import arriving while the session
    // snapshot cannot be loaded must surface a safe failure notice instead of
    // being silently dropped with no user feedback.
    @Test
    fun missingSnapshotSurfacesFailureNoticeInsteadOfSilentDrop() {
        val outcome = mapChatSourcePickImport(
            imported = usableImport("source-9"),
            sessionId = "session-1",
            currentSnapshot = null
        )

        assertNotNull("snapshot read failure must not be silently dropped", outcome)
        assertTrue(
            "expected a rejected outcome with a retry notice, got $outcome",
            outcome is ChatSourcePickImportOutcome.Rejected
        )
        assertTrue((outcome as ChatSourcePickImportOutcome.Rejected).notice.isNotBlank())
    }

    private fun usableImport(id: String): SourceImportResult =
        importResult(SourceParserStatus.FullyLocal, text = "正文内容", chunks = listOf("正文内容"), sourceId = id)

    private fun importResult(
        status: SourceParserStatus,
        text: String?,
        chunks: List<String>,
        sourceId: String = "source-new"
    ): SourceImportResult = SourceImportResult(
        source = SourceRecord(
            id = sourceId,
            spaceId = "space",
            title = "$sourceId.txt",
            type = SourceType.Text,
            parserStatus = status,
            createdAtEpochMillis = 1L,
            extractedText = text
        ),
        chunks = chunks.mapIndexed { index, body ->
            SourceChunk(
                id = "chunk-$index",
                spaceId = "space",
                sourceId = sourceId,
                chunkIndex = index,
                text = body,
                tokenEstimate = 1
            )
        },
        warnings = emptyList(),
        errors = emptyList()
    )
}
