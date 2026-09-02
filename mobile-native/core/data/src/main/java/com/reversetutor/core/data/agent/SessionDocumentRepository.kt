package com.reversetutor.core.data.agent

import com.reversetutor.core.llm.LlmRichContentBlock
import com.reversetutor.core.data.local.dao.SessionAgentDao
import com.reversetutor.core.data.local.entity.SessionDocumentEntity
import com.reversetutor.core.data.local.entity.SessionDocumentBlockEntity
import com.reversetutor.core.data.local.entity.ToolCallReceiptEntity

sealed interface RichDocumentBlock {
    data class Heading(val level: Int, val text: String) : RichDocumentBlock
    data class Paragraph(val text: String) : RichDocumentBlock
    data class BulletList(val items: List<String>) : RichDocumentBlock
    data class NumberedList(val items: List<String>) : RichDocumentBlock
    data class CodeBlock(val language: String?, val code: String) : RichDocumentBlock
    data class Callout(val kind: String, val text: String) : RichDocumentBlock
    data class SimpleTable(val columns: List<String>, val rows: List<List<String>>) : RichDocumentBlock
}

data class SessionDocumentSnapshot(
    val id: String,
    val spaceId: String,
    val sessionId: String,
    val title: String,
    val kind: String = "learning_note",
    val blocks: List<RichDocumentBlock> = emptyList(),
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = createdAtEpochMillis
)

data class SessionDocumentCreateResult(val created: Boolean, val document: SessionDocumentSnapshot?)

/** Storage seam: callers never receive Room entities or raw tool arguments. */
interface SessionDocumentStore {
    suspend fun readByCallId(callId: String): SessionDocumentSnapshot?
    suspend fun create(callId: String, document: SessionDocumentSnapshot): Boolean
    suspend fun read(sessionId: String, documentId: String): SessionDocumentSnapshot?
    suspend fun replaceBlock(
        callId: String,
        sessionId: String,
        documentId: String,
        blockId: String,
        kind: String,
        payload: String,
        nowEpochMillis: Long
    ): Boolean
}

data class SessionDocumentBlockResult(val applied: Boolean, val blockId: String?)

class SessionDocumentRepository(private val store: SessionDocumentStore) {
    suspend fun createIfAbsent(
        callId: String,
        spaceId: String,
        sessionId: String,
        title: String,
        nowEpochMillis: Long,
        blocks: List<RichDocumentBlock> = emptyList()
    ): SessionDocumentCreateResult {
        val normalizedCallId = callId.trim().take(120)
        val normalizedSpaceId = spaceId.trim().take(120)
        val normalizedSessionId = sessionId.trim().take(120)
        val normalizedTitle = title.trim().take(120)
        if (normalizedCallId.isEmpty() || normalizedSpaceId.isEmpty() || normalizedSessionId.isEmpty() || normalizedTitle.isEmpty()) {
            return SessionDocumentCreateResult(created = false, document = null)
        }
        store.readByCallId(normalizedCallId)?.let { return SessionDocumentCreateResult(false, it) }
        val document = SessionDocumentSnapshot(
            id = "document-$normalizedCallId",
            spaceId = normalizedSpaceId,
            sessionId = normalizedSessionId,
            title = normalizedTitle,
            blocks = blocks.take(8).mapNotNull { block -> block.normalizedForStorage() },
            createdAtEpochMillis = nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis
        )
        val created = store.create(normalizedCallId, document)
        return SessionDocumentCreateResult(created, if (created) document else store.readByCallId(normalizedCallId))
    }

    suspend fun read(sessionId: String, documentId: String): SessionDocumentSnapshot? =
        store.read(sessionId.trim(), documentId.trim())

    suspend fun replaceBlockIfAbsent(
        callId: String,
        sessionId: String,
        documentId: String,
        blockId: String,
        kind: String,
        payload: String,
        nowEpochMillis: Long
    ): SessionDocumentBlockResult {
        val normalizedCallId = callId.trim().take(120)
        val normalizedSessionId = sessionId.trim().take(120)
        val normalizedDocumentId = documentId.trim().take(160)
        val normalizedBlockId = blockId.trim().take(160)
        val normalizedKind = kind.trim().lowercase().take(24)
        val normalizedPayload = payload.trim().take(4_000)
        if (
            normalizedCallId.isEmpty() || normalizedSessionId.isEmpty() || normalizedDocumentId.isEmpty() ||
            normalizedBlockId.isEmpty() || normalizedKind !in setOf("paragraph", "heading", "code") ||
            normalizedPayload.isEmpty()
        ) return SessionDocumentBlockResult(false, null)
        val existing = store.readByCallId(normalizedCallId)
        if (existing != null) return SessionDocumentBlockResult(false, normalizedBlockId)
        val applied = store.replaceBlock(
            normalizedCallId, normalizedSessionId, normalizedDocumentId, normalizedBlockId,
            normalizedKind, normalizedPayload, nowEpochMillis
        )
        return SessionDocumentBlockResult(applied, normalizedBlockId.takeIf { applied })
    }
}

private fun RichDocumentBlock.normalizedForStorage(): RichDocumentBlock? = when (this) {
    is RichDocumentBlock.Paragraph -> text.trim().take(4_000).takeIf(String::isNotEmpty)?.let(RichDocumentBlock::Paragraph)
    is RichDocumentBlock.Heading -> text.trim().take(240).takeIf(String::isNotEmpty)?.let { RichDocumentBlock.Heading(level.coerceIn(1, 3), it) }
    is RichDocumentBlock.CodeBlock -> code.trim().take(4_000).takeIf(String::isNotEmpty)?.let { RichDocumentBlock.CodeBlock(language?.trim()?.take(32), it) }
    else -> null
}

/** Test-only-friendly deterministic store; production wiring may replace it with Room. */
class InMemorySessionDocumentStore : SessionDocumentStore {
    private val documentsById = linkedMapOf<String, SessionDocumentSnapshot>()
    private val documentIdByCallId = linkedMapOf<String, String>()

    /** Test fixture helper; production wiring uses [RoomSessionDocumentStore]. */
    fun seedBlocks(documentId: String, blocks: List<RichDocumentBlock>) {
        val current = documentsById[documentId] ?: return
        documentsById[documentId] = current.copy(blocks = blocks)
    }

    override suspend fun readByCallId(callId: String): SessionDocumentSnapshot? =
        documentIdByCallId[callId]?.let(documentsById::get)

    override suspend fun create(callId: String, document: SessionDocumentSnapshot): Boolean {
        if (callId in documentIdByCallId || document.id in documentsById) return false
        documentsById[document.id] = document
        documentIdByCallId[callId] = document.id
        return true
    }

    override suspend fun read(sessionId: String, documentId: String): SessionDocumentSnapshot? =
        documentsById[documentId]?.takeIf { it.sessionId == sessionId }

    override suspend fun replaceBlock(
        callId: String,
        sessionId: String,
        documentId: String,
        blockId: String,
        kind: String,
        payload: String,
        nowEpochMillis: Long
    ): Boolean {
        if (callId in documentIdByCallId) return false
        val current = documentsById[documentId]?.takeIf { it.sessionId == sessionId } ?: return false
        val replacement = when (kind) {
            "heading" -> RichDocumentBlock.Heading(2, payload)
            "code" -> RichDocumentBlock.CodeBlock(null, payload)
            else -> RichDocumentBlock.Paragraph(payload)
        }
        val blocks = current.blocks.toMutableList()
        val ordinal = blockId.substringAfterLast('-').toIntOrNull()
        if (ordinal == null || ordinal !in blocks.indices) return false
        blocks[ordinal] = replacement
        documentsById[documentId] = current.copy(blocks = blocks, updatedAtEpochMillis = nowEpochMillis)
        documentIdByCallId[callId] = documentId
        return true
    }
}

/** Room adapter used by production wiring; every document read is session-bound. */
class RoomSessionDocumentStore(private val dao: SessionAgentDao) : SessionDocumentStore {
    override suspend fun readByCallId(callId: String): SessionDocumentSnapshot? {
        val receipt = dao.getReceipt(callId) ?: return null
        if (receipt.toolName != "session_document.create" || receipt.status != "completed") return null
        val documentId = receipt.safeResultPayload.removePrefix("document:")
        return dao.getDocument(receipt.sessionId, documentId)?.toSnapshot(dao)
    }

    override suspend fun create(callId: String, document: SessionDocumentSnapshot): Boolean {
        if (dao.getReceipt(callId) != null) return false
        return runCatching {
            dao.createDocumentWithReceipt(
                SessionDocumentEntity(
                    id = document.id,
                    spaceId = document.spaceId,
                    sessionId = document.sessionId,
                    title = document.title,
                    kind = document.kind,
                    createdAtEpochMillis = document.createdAtEpochMillis,
                    updatedAtEpochMillis = document.updatedAtEpochMillis
                ),
                document.blocks.mapIndexed { index, block ->
                    SessionDocumentBlockEntity(
                        id = "${document.id}-block-$index",
                        documentId = document.id,
                        ordinal = index,
                        kind = block.storageKind(),
                        payload = block.storagePayload(),
                        updatedAtEpochMillis = document.updatedAtEpochMillis
                    )
                },
                ToolCallReceiptEntity(
                    callId = callId,
                    sessionId = document.sessionId,
                    toolName = "session_document.create",
                    status = "completed",
                    safeResultPayload = "document:${document.id}",
                    completedAtEpochMillis = document.createdAtEpochMillis
                )
            )
        }.getOrDefault(false)
    }

    override suspend fun replaceBlock(
        callId: String,
        sessionId: String,
        documentId: String,
        blockId: String,
        kind: String,
        payload: String,
        nowEpochMillis: Long
    ): Boolean {
        if (dao.getReceipt(callId) != null || dao.getDocument(sessionId, documentId) == null) return false
        return runCatching {
            dao.replaceBlockWithReceipt(
                SessionDocumentBlockEntity(
                    id = blockId,
                    documentId = documentId,
                    ordinal = blockId.substringAfterLast('-').toIntOrNull() ?: return false,
                    kind = kind,
                    payload = payload,
                    updatedAtEpochMillis = nowEpochMillis
                ),
                ToolCallReceiptEntity(
                    callId = callId,
                    sessionId = sessionId,
                    toolName = "session_document.replace_block",
                    status = "completed",
                    safeResultPayload = "document:$documentId",
                    completedAtEpochMillis = nowEpochMillis
                )
            )
        }.getOrDefault(false)
    }

    override suspend fun read(sessionId: String, documentId: String): SessionDocumentSnapshot? =
        dao.getDocument(sessionId, documentId)?.toSnapshot(dao)
}

private fun RichDocumentBlock.storageKind(): String = when (this) {
    is RichDocumentBlock.Heading -> "heading"
    is RichDocumentBlock.CodeBlock -> "code"
    else -> "paragraph"
}

private fun RichDocumentBlock.storagePayload(): String = when (this) {
    is RichDocumentBlock.Heading -> text
    is RichDocumentBlock.CodeBlock -> code
    is RichDocumentBlock.Paragraph -> text
    else -> ""
}

private suspend fun SessionDocumentEntity.toSnapshot(dao: SessionAgentDao): SessionDocumentSnapshot =
    SessionDocumentSnapshot(
        id = id,
        spaceId = spaceId,
        sessionId = sessionId,
        title = title,
        kind = kind,
        blocks = dao.listBlocks(id).mapNotNull { it.toBlock() },
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis
    )

private fun SessionDocumentBlockEntity.toBlock(): RichDocumentBlock? =
    AgentPayloadCodec.decodeBlock(kind, payload)

internal fun LlmRichContentBlock.toDocumentBlock(): RichDocumentBlock = when (this) {
    is LlmRichContentBlock.Heading -> RichDocumentBlock.Heading(level, text)
    is LlmRichContentBlock.Paragraph -> RichDocumentBlock.Paragraph(text)
    is LlmRichContentBlock.BulletList -> RichDocumentBlock.BulletList(items)
    is LlmRichContentBlock.NumberedList -> RichDocumentBlock.NumberedList(items)
    is LlmRichContentBlock.CodeBlock -> RichDocumentBlock.CodeBlock(language, code)
    is LlmRichContentBlock.Callout -> RichDocumentBlock.Callout(kind, text)
    is LlmRichContentBlock.SimpleTable -> RichDocumentBlock.SimpleTable(columns, rows)
}
