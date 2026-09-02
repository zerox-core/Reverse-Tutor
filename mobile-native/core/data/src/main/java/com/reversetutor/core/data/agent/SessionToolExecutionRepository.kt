package com.reversetutor.core.data.agent

import com.reversetutor.core.domain.SessionToolCall
import com.reversetutor.core.domain.SessionToolPolicy
import com.reversetutor.core.domain.SessionToolResult
import com.reversetutor.core.domain.ToolSafeResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Executes only the approved, session-bound document/table/reference tools.
 * Raw tool arguments are parsed in memory and are never persisted. Completed
 * calls are replayed from a safe receipt, so retries cannot repeat a write.
 */
class SessionToolExecutionRepository(
    private val documents: SessionDocumentRepository,
    private val tables: SessionTableRepository,
    private val receipts: ToolCallReceiptRepository
) {
    suspend fun execute(
        spaceId: String,
        currentSessionId: String,
        call: SessionToolCall,
        allowedReferenceIds: Set<String>,
        nowEpochMillis: Long
    ): SessionToolResult {
        val authorization = SessionToolPolicy.authorize(currentSessionId, call)
        if (!authorization.allowed) return rejected(call, currentSessionId, authorization.code, nowEpochMillis)
        val existing = receipts.read(call.callId)
        if (existing != null) {
            return if (existing.sessionId == currentSessionId && existing.toolName == call.name) existing.toResult()
            else rejected(call, currentSessionId, "tool_call_collision", nowEpochMillis)
        }
        val arguments = parseObject(call.argumentsJson)
            ?: return rejected(call, currentSessionId, "tool_invalid_arguments", nowEpochMillis)
        return when (call.name) {
            "session_document.create" -> createDocument(spaceId, currentSessionId, call, arguments, nowEpochMillis)
            "session_document.read" -> readDocument(currentSessionId, call, arguments, nowEpochMillis)
            "session_document.replace_block" -> replaceBlock(currentSessionId, call, arguments, nowEpochMillis)
            "session_table.create" -> createTable(currentSessionId, call, arguments, nowEpochMillis)
            "session_table.upsert_row" -> upsertRow(currentSessionId, call, arguments, nowEpochMillis)
            "reference.open" -> openReference(currentSessionId, call, arguments, allowedReferenceIds, nowEpochMillis)
            else -> rejected(call, currentSessionId, "tool_not_allowed", nowEpochMillis)
        }
    }

    suspend fun readDocument(sessionId: String, documentId: String): SessionDocumentSnapshot? =
        documents.read(sessionId, documentId)

    private suspend fun createDocument(
        spaceId: String,
        sessionId: String,
        call: SessionToolCall,
        arguments: JsonObject,
        now: Long
    ): SessionToolResult {
        if (arguments.keys !in setOf(setOf("title"), setOf("title", "content"))) return rejected(call, sessionId, "tool_invalid_arguments", now)
        val title = arguments.text("title") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val content = arguments["content"]?.jsonPrimitive?.contentOrNull?.trim()?.take(4_000)
        val blocks = content?.takeIf(String::isNotEmpty)?.let { listOf(RichDocumentBlock.Paragraph(it)) }.orEmpty()
        val result = documents.createIfAbsent(call.callId, spaceId, sessionId, title, now, blocks)
        val document = result.document ?: return rejected(call, sessionId, "tool_write_failed", now)
        return completed(call, sessionId, ToolSafeResult.Document(document.id), now)
    }

    private suspend fun readDocument(
        sessionId: String,
        call: SessionToolCall,
        arguments: JsonObject,
        now: Long
    ): SessionToolResult {
        val documentId = arguments.stringOnly("documentId") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        return if (documents.read(sessionId, documentId) != null) {
            completed(call, sessionId, ToolSafeResult.Document(documentId), now)
        } else rejected(call, sessionId, "tool_scope_denied", now)
    }

    private suspend fun replaceBlock(
        sessionId: String,
        call: SessionToolCall,
        arguments: JsonObject,
        now: Long
    ): SessionToolResult {
        if (arguments.keys != setOf("documentId", "blockId", "kind", "text")) return rejected(call, sessionId, "tool_invalid_arguments", now)
        val documentId = arguments.text("documentId") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val blockId = arguments.text("blockId") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val kind = arguments.text("kind") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val text = arguments.text("text") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        return if (documents.replaceBlockIfAbsent(call.callId, sessionId, documentId, blockId, kind, text, now).applied) {
            completed(call, sessionId, ToolSafeResult.Document(documentId), now)
        } else rejected(call, sessionId, "tool_write_failed", now)
    }

    private suspend fun createTable(
        sessionId: String,
        call: SessionToolCall,
        arguments: JsonObject,
        now: Long
    ): SessionToolResult {
        if (arguments.keys != setOf("documentId", "title", "columns")) return rejected(call, sessionId, "tool_invalid_arguments", now)
        val documentId = arguments.text("documentId") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val title = arguments.text("title") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val columns = arguments["columns"]?.jsonArray?.stringsOrNull() ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        if (documents.read(sessionId, documentId) == null) return rejected(call, sessionId, "tool_scope_denied", now)
        val result = tables.createIfAbsent(call.callId, sessionId, documentId, title, columns.map(::SessionTableColumn), now)
        val table = result.table ?: return rejected(call, sessionId, "tool_scope_denied", now)
        return completed(call, sessionId, ToolSafeResult.Table(table.id, table.rows.size), now)
    }

    private suspend fun upsertRow(
        sessionId: String,
        call: SessionToolCall,
        arguments: JsonObject,
        now: Long
    ): SessionToolResult {
        if (arguments.keys != setOf("tableId", "rowKey", "cells")) return rejected(call, sessionId, "tool_invalid_arguments", now)
        val tableId = arguments.text("tableId") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val rowKey = arguments.text("rowKey") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        val cells = arguments["cells"]?.jsonArray?.stringsOrNull() ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        return if (tables.upsertRowIfAbsent(call.callId, sessionId, tableId, rowKey, cells, now)) {
            completed(call, sessionId, ToolSafeResult.Table(tableId, 1), now)
        } else rejected(call, sessionId, "tool_scope_denied", now)
    }

    private suspend fun openReference(
        sessionId: String,
        call: SessionToolCall,
        arguments: JsonObject,
        allowedReferenceIds: Set<String>,
        now: Long
    ): SessionToolResult {
        val sourceId = arguments.stringOnly("sourceId") ?: return rejected(call, sessionId, "tool_invalid_arguments", now)
        if (sourceId !in allowedReferenceIds) return rejected(call, sessionId, "tool_scope_denied", now)
        return completed(call, sessionId, ToolSafeResult.ReferenceOpenTarget(sourceId), now)
    }

    private suspend fun completed(call: SessionToolCall, sessionId: String, result: ToolSafeResult, now: Long): SessionToolResult =
        receipts.recordIfAbsent(ToolCallReceipt(call.callId, sessionId, call.name, "completed", result, now)).toResult()

    private suspend fun rejected(call: SessionToolCall, sessionId: String, code: String, now: Long): SessionToolResult {
        val callId = call.callId.trim().take(120)
        if (callId.isEmpty()) return SessionToolResult("", "rejected", ToolSafeResult.Rejected(code), com.reversetutor.core.domain.ToolPresentationIntent())
        return receipts.recordIfAbsent(
            ToolCallReceipt(callId, sessionId.trim().take(120), call.name.trim().take(80), "rejected", ToolSafeResult.Rejected(code), now)
        ).toResult()
    }

    private fun parseObject(value: String): JsonObject? = runCatching { json.parseToJsonElement(value).jsonObject }.getOrNull()

    private fun JsonObject.stringOnly(name: String): String? =
        if (keys == setOf(name)) text(name) else null

    private fun JsonObject.text(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonArray.stringsOrNull(): List<String>? =
        takeIf { it.isNotEmpty() && it.size <= 8 }?.map { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) ?: return null }

    private companion object {
        val json = Json { ignoreUnknownKeys = false; isLenient = false }
    }
}
