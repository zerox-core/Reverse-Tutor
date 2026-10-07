package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.domain.WindowRef
import com.reversetutor.core.domain.WindowSnapshotRef

/**
 * Projects a window's visible message history without duplicating parent rows.
 * A child sees the recursively projected ancestor state at its own fork time,
 * followed by its local records. Broken topology degrades to local records.
 */
class WindowVisibleHistoryReader(
    private val readWindow: suspend (String) -> WindowRef?,
    private val readSnapshot: suspend (String) -> WindowSnapshotRef?,
    private val listMessageRecords: suspend (String) -> List<MessageRecord>
) {
    suspend fun recordsFor(windowId: String): List<VisibleMessageRecord> =
        recordsFor(windowId, mutableSetOf())

    private suspend fun recordsFor(
        windowId: String,
        visiting: MutableSet<String>
    ): List<VisibleMessageRecord> {
        if (!visiting.add(windowId)) return emptyList()
        val own = listMessageRecords(windowId).map { VisibleMessageRecord(it, inherited = false) }
        val window = readWindow(windowId) ?: run {
            visiting.remove(windowId)
            return own.sortedDeterministically()
        }
        if (window.parentId?.let { it in visiting } == true) {
            visiting.remove(windowId)
            return emptyList()
        }
        val inherited = window.parentId?.let { parentId ->
            val snapshot = readSnapshot(windowId) ?: return@let emptyList()
            recordsFor(parentId, visiting)
                .filter { it.record.message.createdAtEpochMillis <= snapshot.forkedAtEpochMillis }
                .map { it.copy(inherited = true) }
        }.orEmpty()
        visiting.remove(windowId)
        return (inherited + own).sortedDeterministically()
    }
}

data class VisibleMessageRecord(
    val record: MessageRecord,
    val inherited: Boolean
)

private fun List<VisibleMessageRecord>.sortedDeterministically(): List<VisibleMessageRecord> =
    sortedWith(
        compareBy<VisibleMessageRecord> { it.record.message.createdAtEpochMillis }
            .thenBy { it.record.message.id }
    )
