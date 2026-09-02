package com.reversetutor.core.data.agent

import com.reversetutor.core.data.local.dao.SessionAgentDao
import com.reversetutor.core.data.local.entity.SessionTableColumnEntity
import com.reversetutor.core.data.local.entity.SessionTableEntity
import com.reversetutor.core.data.local.entity.SessionTableRowEntity
import com.reversetutor.core.data.local.entity.ToolCallReceiptEntity

data class SessionTableColumn(val name: String, val valueType: String = "text")
data class SessionTableRow(val rowKey: String, val cells: List<String>)
data class SessionTableSnapshot(
    val id: String,
    val documentId: String,
    val title: String,
    val columns: List<SessionTableColumn>,
    val rows: List<SessionTableRow> = emptyList()
)

data class SessionTableWriteResult(val applied: Boolean, val table: SessionTableSnapshot?)

interface SessionTableStore {
    suspend fun readByCallId(callId: String): SessionTableSnapshot?
    suspend fun create(callId: String, sessionId: String, table: SessionTableSnapshot, nowEpochMillis: Long): Boolean
    suspend fun upsertRow(callId: String, sessionId: String, tableId: String, row: SessionTableRow, nowEpochMillis: Long): Boolean
    suspend fun read(sessionId: String, tableId: String): SessionTableSnapshot?
}

class SessionTableRepository(private val store: SessionTableStore) {
    suspend fun createIfAbsent(
        callId: String,
        sessionId: String,
        documentId: String,
        title: String,
        columns: List<SessionTableColumn>,
        nowEpochMillis: Long
    ): SessionTableWriteResult {
        val normalizedCallId = callId.trim().take(120)
        val normalizedSessionId = sessionId.trim().take(120)
        val normalizedDocumentId = documentId.trim().take(160)
        val normalizedTitle = title.trim().take(120)
        val normalizedColumns = columns.take(8).mapNotNull { column ->
            column.name.trim().take(80).takeIf(String::isNotEmpty)?.let { SessionTableColumn(it, "text") }
        }
        if (normalizedCallId.isEmpty() || normalizedSessionId.isEmpty() || normalizedDocumentId.isEmpty() || normalizedTitle.isEmpty() || normalizedColumns.isEmpty()) {
            return SessionTableWriteResult(false, null)
        }
        store.readByCallId(normalizedCallId)?.let { return SessionTableWriteResult(false, it) }
        val table = SessionTableSnapshot("table-$normalizedCallId", normalizedDocumentId, normalizedTitle, normalizedColumns)
        val applied = store.create(normalizedCallId, normalizedSessionId, table, nowEpochMillis)
        return SessionTableWriteResult(applied, if (applied) table else store.readByCallId(normalizedCallId))
    }

    suspend fun upsertRowIfAbsent(
        callId: String,
        sessionId: String,
        tableId: String,
        rowKey: String,
        cells: List<String>,
        nowEpochMillis: Long
    ): Boolean {
        val normalizedCallId = callId.trim().take(120)
        val normalizedSessionId = sessionId.trim().take(120)
        val normalizedTableId = tableId.trim().take(160)
        val normalizedRowKey = rowKey.trim().take(120)
        val normalizedCells = cells.take(8).map { it.trim().take(240) }
        if (normalizedCallId.isEmpty() || normalizedSessionId.isEmpty() || normalizedTableId.isEmpty() || normalizedRowKey.isEmpty() || normalizedCells.isEmpty() || normalizedCells.any(String::isEmpty)) return false
        if (store.readByCallId(normalizedCallId) != null) return false
        return store.upsertRow(normalizedCallId, normalizedSessionId, normalizedTableId, SessionTableRow(normalizedRowKey, normalizedCells), nowEpochMillis)
    }

    suspend fun read(sessionId: String, tableId: String): SessionTableSnapshot? = store.read(sessionId.trim(), tableId.trim())
}

class InMemorySessionTableStore : SessionTableStore {
    private data class Stored(val sessionId: String, val table: SessionTableSnapshot)
    private val tables = linkedMapOf<String, Stored>()
    private val calls = linkedMapOf<String, String>()

    override suspend fun readByCallId(callId: String): SessionTableSnapshot? = calls[callId]?.let { tables[it]?.table }

    override suspend fun create(callId: String, sessionId: String, table: SessionTableSnapshot, nowEpochMillis: Long): Boolean {
        if (callId in calls || table.id in tables) return false
        tables[table.id] = Stored(sessionId, table)
        calls[callId] = table.id
        return true
    }

    override suspend fun upsertRow(callId: String, sessionId: String, tableId: String, row: SessionTableRow, nowEpochMillis: Long): Boolean {
        if (callId in calls) return false
        val stored = tables[tableId]?.takeIf { it.sessionId == sessionId } ?: return false
        if (row.cells.size != stored.table.columns.size) return false
        val rows = stored.table.rows.filterNot { it.rowKey == row.rowKey } + row
        tables[tableId] = stored.copy(table = stored.table.copy(rows = rows))
        calls[callId] = tableId
        return true
    }

    override suspend fun read(sessionId: String, tableId: String): SessionTableSnapshot? = tables[tableId]?.takeIf { it.sessionId == sessionId }?.table
}

class RoomSessionTableStore(private val dao: SessionAgentDao) : SessionTableStore {
    override suspend fun readByCallId(callId: String): SessionTableSnapshot? {
        val receipt = dao.getReceipt(callId) ?: return null
        if (receipt.status != "completed" || !receipt.safeResultPayload.startsWith("table:")) return null
        return read(receipt.sessionId, receipt.safeResultPayload.removePrefix("table:").substringBefore(':'))
    }

    override suspend fun create(callId: String, sessionId: String, table: SessionTableSnapshot, nowEpochMillis: Long): Boolean {
        if (dao.getReceipt(callId) != null || dao.getDocument(sessionId, table.documentId) == null) return false
        return runCatching {
            dao.createTableWithReceipt(
                SessionTableEntity(table.id, table.documentId, table.title, nowEpochMillis, nowEpochMillis),
                table.columns.mapIndexed { index, column -> SessionTableColumnEntity("${table.id}-column-$index", table.id, index, column.name, column.valueType) },
                ToolCallReceiptEntity(callId, sessionId, "session_table.create", "completed", "table:${table.id}:0", nowEpochMillis)
            )
        }.getOrDefault(false)
    }

    override suspend fun upsertRow(callId: String, sessionId: String, tableId: String, row: SessionTableRow, nowEpochMillis: Long): Boolean {
        if (dao.getReceipt(callId) != null) return false
        val table = dao.getTable(sessionId, tableId) ?: return false
        if (dao.listColumns(table.id).size != row.cells.size) return false
        return runCatching {
            dao.upsertRowWithReceipt(
                SessionTableRowEntity("${table.id}-row-${row.rowKey}", table.id, row.rowKey, AgentPayloadCodec.encodeList(row.cells), nowEpochMillis),
                ToolCallReceiptEntity(callId, sessionId, "session_table.upsert_row", "completed", "table:${table.id}:1", nowEpochMillis)
            )
        }.getOrDefault(false)
    }

    override suspend fun read(sessionId: String, tableId: String): SessionTableSnapshot? {
        val table = dao.getTable(sessionId, tableId) ?: return null
        return SessionTableSnapshot(
            table.id, table.documentId, table.title,
            dao.listColumns(table.id).map { SessionTableColumn(it.name, it.valueType) },
            dao.listRows(table.id).map { SessionTableRow(it.rowKey, AgentPayloadCodec.decodeList(it.cellsPayload)) }
        )
    }
}
