package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.domain.WindowKind
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.core.domain.WindowSnapshotRef
import com.reversetutor.core.model.Message
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WindowVisibleHistoryReaderTest {

    private val windows = mapOf(
        "root" to WindowRef("root", "root", null, WindowKind.TASK_ROOT),
        "child-1" to WindowRef("child-1", "root", "root", WindowKind.CHILD),
        "child-2" to WindowRef("child-2", "root", "child-1", WindowKind.CHILD),
        "sibling" to WindowRef("sibling", "root", "root", WindowKind.CHILD)
    )
    private val snapshots = mapOf(
        "child-1" to WindowSnapshotRef(ancestorRevision = 1L, forkedAtEpochMillis = 20L),
        "child-2" to WindowSnapshotRef(ancestorRevision = 1L, forkedAtEpochMillis = 40L),
        "sibling" to WindowSnapshotRef(ancestorRevision = 1L, forkedAtEpochMillis = 20L)
    )
    private val records = mapOf(
        "root" to listOf(
            record("root-before", "root", 10L),
            record("root-after-child-1", "root", 30L)
        ),
        "child-1" to listOf(
            record("child-1-before", "child-1", 35L),
            record("child-1-after-child-2", "child-1", 50L)
        ),
        "child-2" to listOf(record("child-2-local", "child-2", 60L)),
        "sibling" to listOf(record("sibling-local", "sibling", 45L))
    )

    private val reader = WindowVisibleHistoryReader(
        readWindow = windows::get,
        readSnapshot = snapshots::get,
        listMessageRecords = { records[it].orEmpty() }
    )

    @Test
    fun nested_child_reads_only_each_ancestor_state_visible_at_its_fork() = runBlocking {
        val visible = reader.recordsFor("child-2")

        assertEquals(
            listOf("root-before", "child-1-before", "child-2-local"),
            visible.map { it.record.message.id }
        )
        assertFalse(
            visible.any {
                it.record.message.id in setOf("root-after-child-1", "child-1-after-child-2")
            }
        )
    }

    @Test
    fun sibling_records_never_enter_child_visible_history() = runBlocking {
        assertFalse(reader.recordsFor("child-1").any { it.record.message.id == "sibling-local" })
    }

    @Test
    fun context_port_uses_same_fork_bound_messages_as_timeline() = runBlocking {
        val contextPort = TopologyAwareMessageContextPort(reader)

        assertEquals(
            listOf("root-before", "child-1-before", "child-2-local"),
            contextPort.listRecentMessages("space-a", "child-2", limit = 10).map { it.messageId }
        )
    }

    @Test
    fun absent_topology_keeps_an_existing_session_local() = runBlocking {
        val legacyReader = WindowVisibleHistoryReader(
            readWindow = { null },
            readSnapshot = { null },
            listMessageRecords = { records[it].orEmpty() }
        )

        assertEquals(listOf("root-before", "root-after-child-1"), legacyReader.recordsFor("root").map { it.record.message.id })
    }

    @Test
    fun cyclic_parent_links_do_not_duplicate_or_recurse_forever() = runBlocking {
        val cyclic = WindowVisibleHistoryReader(
            readWindow = { id ->
                when (id) {
                    "a" -> WindowRef("a", "root", "b", WindowKind.CHILD)
                    "b" -> WindowRef("b", "root", "a", WindowKind.CHILD)
                    else -> null
                }
            },
            readSnapshot = { WindowSnapshotRef(ancestorRevision = 1L, forkedAtEpochMillis = 100L) },
            listMessageRecords = { listOf(record("message-$it", it, 1L)) }
        )

        assertEquals(listOf("message-a"), cyclic.recordsFor("a").map { it.record.message.id })
    }

    @Test
    fun timeline_and_generation_context_share_the_same_visible_ids() = runBlocking {
        val timelineIds = WindowVisibleTimelinePortAdapter(reader).load("child-2").map { it.id }
        val contextIds = TopologyAwareMessageContextPort(reader)
            .listRecentMessages("space-a", "child-2", limit = 10)
            .map { it.messageId }

        assertEquals(timelineIds, contextIds)
        assertEquals(
            listOf("root-before", "child-1-before", "child-2-local"),
            timelineIds
        )
        assertFalse(timelineIds.any { it in setOf("root-after-child-1", "sibling-local") })
        assertFalse(contextIds.any { it in setOf("root-after-child-1", "sibling-local") })
    }

    private fun record(id: String, sessionId: String, timestamp: Long) = MessageRecord(
        message = Message(
            id = id,
            spaceId = "space-a",
            sessionId = sessionId,
            role = MessageRole.User,
            text = id,
            createdAtEpochMillis = timestamp
        ),
        quote = null
    )
}
