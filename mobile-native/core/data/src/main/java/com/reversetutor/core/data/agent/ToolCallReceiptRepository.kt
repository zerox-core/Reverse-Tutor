package com.reversetutor.core.data.agent

import com.reversetutor.core.data.local.dao.SessionAgentDao
import com.reversetutor.core.data.local.entity.ToolCallReceiptEntity
import com.reversetutor.core.domain.SessionToolResult
import com.reversetutor.core.domain.ToolPresentationIntent
import com.reversetutor.core.domain.ToolSafeResult

/** A persisted, safe-only outcome used as the idempotency boundary for tools. */
data class ToolCallReceipt(
    val callId: String,
    val sessionId: String,
    val toolName: String,
    val status: String,
    val safeResult: ToolSafeResult,
    val completedAtEpochMillis: Long
)

interface ToolCallReceiptStore {
    suspend fun read(callId: String): ToolCallReceipt?
    suspend fun insert(receipt: ToolCallReceipt): Boolean
}

class ToolCallReceiptRepository(private val store: ToolCallReceiptStore) {
    suspend fun read(callId: String): ToolCallReceipt? = store.read(callId.trim())

    suspend fun recordIfAbsent(receipt: ToolCallReceipt): ToolCallReceipt {
        val existing = store.read(receipt.callId)
        if (existing != null) return existing
        return if (store.insert(receipt)) receipt else store.read(receipt.callId) ?: receipt.copy(
            status = "failed",
            safeResult = ToolSafeResult.Rejected("tool_receipt_unavailable")
        )
    }
}

class InMemoryToolCallReceiptStore : ToolCallReceiptStore {
    private val receipts = linkedMapOf<String, ToolCallReceipt>()

    override suspend fun read(callId: String): ToolCallReceipt? = receipts[callId]

    override suspend fun insert(receipt: ToolCallReceipt): Boolean {
        if (receipt.callId in receipts) return false
        receipts[receipt.callId] = receipt
        return true
    }
}

class RoomToolCallReceiptStore(private val dao: SessionAgentDao) : ToolCallReceiptStore {
    override suspend fun read(callId: String): ToolCallReceipt? = dao.getReceipt(callId)?.toReceipt()

    override suspend fun insert(receipt: ToolCallReceipt): Boolean = runCatching {
        dao.insertReceipt(receipt.toEntity())
        true
    }.getOrDefault(false)
}

internal fun ToolCallReceipt.toResult(): SessionToolResult = SessionToolResult(
    callId = callId,
    status = status,
    safeResult = safeResult,
    presentation = safeResult.presentation()
)

internal fun ToolSafeResult.presentation(): ToolPresentationIntent = when (this) {
    is ToolSafeResult.Document -> ToolPresentationIntent("document", documentId)
    is ToolSafeResult.Table -> ToolPresentationIntent("table", tableId)
    is ToolSafeResult.ReferenceOpenTarget -> ToolPresentationIntent("reference", sourceId)
    is ToolSafeResult.Rejected -> ToolPresentationIntent()
}

private fun ToolCallReceiptEntity.toReceipt(): ToolCallReceipt = ToolCallReceipt(
    callId = callId,
    sessionId = sessionId,
    toolName = toolName,
    status = status,
    safeResult = safeResultPayload.toSafeResult(),
    completedAtEpochMillis = completedAtEpochMillis
)

internal fun ToolCallReceipt.toEntity(): ToolCallReceiptEntity = ToolCallReceiptEntity(
    callId = callId,
    sessionId = sessionId,
    toolName = toolName,
    status = status,
    safeResultPayload = safeResult.toPayload(),
    completedAtEpochMillis = completedAtEpochMillis
)

internal fun ToolSafeResult.toPayload(): String = when (this) {
    is ToolSafeResult.Document -> "document:$documentId"
    is ToolSafeResult.Table -> "table:$tableId:$rowCount"
    is ToolSafeResult.ReferenceOpenTarget -> "reference:$sourceId"
    is ToolSafeResult.Rejected -> "rejected:$code"
}

private fun String.toSafeResult(): ToolSafeResult {
    val parts = split(':')
    return when (parts.firstOrNull()) {
        "document" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(ToolSafeResult::Document)
            ?: ToolSafeResult.Rejected("tool_receipt_invalid")
        "table" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let { ToolSafeResult.Table(it, parts.getOrNull(2)?.toIntOrNull() ?: 0) }
            ?: ToolSafeResult.Rejected("tool_receipt_invalid")
        "reference" -> parts.getOrNull(1)?.takeIf(String::isNotBlank)?.let(ToolSafeResult::ReferenceOpenTarget)
            ?: ToolSafeResult.Rejected("tool_receipt_invalid")
        "rejected" -> ToolSafeResult.Rejected(parts.getOrNull(1).orEmpty().ifBlank { "tool_failed" })
        else -> ToolSafeResult.Rejected("tool_receipt_invalid")
    }
}
