package com.reversetutor.core.data.session

import androidx.room.withTransaction
import com.reversetutor.core.data.local.ReverseTutorDatabase
import com.reversetutor.core.data.local.entity.EntityTombstoneEntity

class SessionDeletionRepository(
    private val database: ReverseTutorDatabase
) {
    suspend fun deleteSession(
        sessionId: String,
        deletedAtEpochMillis: Long,
        revision: Long,
        idempotencyKey: String? = null
    ): Boolean =
        database.withTransaction {
            val session = database.sessionDao().getById(sessionId) ?: return@withTransaction false
            database.syncDao().upsertTombstone(
                EntityTombstoneEntity(
                    id = "session-$sessionId",
                    spaceId = session.spaceId,
                    entityType = SessionEntityType,
                    entityId = sessionId,
                    revision = revision,
                    deletedAtEpochMillis = deletedAtEpochMillis,
                    idempotencyKey = idempotencyKey
                )
            )

            database.turnRunDao().deleteBySession(sessionId)
            database.turnRunDao().deleteSnapshotsBySession(sessionId)
            database.learningDao().deletePlanTasksBySession(sessionId)
            database.searchDocumentDao().deleteBySession(sessionId)
            deleteSessionOwnedRows(sessionId)
            true
        }

    private fun deleteSessionOwnedRows(sessionId: String) {
        val sql = database.openHelper.writableDatabase
        val args = arrayOf<Any>(sessionId)
        listOf(
            "DELETE FROM message_quotes WHERE messageId IN (SELECT id FROM messages WHERE sessionId = ?)",
            "DELETE FROM message_attachments WHERE messageId IN (SELECT id FROM messages WHERE sessionId = ?)",
            "DELETE FROM messages WHERE sessionId = ?",
            "DELETE FROM session_settings WHERE sessionId = ?",
            "DELETE FROM background_jobs WHERE sessionId = ?",
            "DELETE FROM sessions WHERE id = ?"
        ).forEach { statement -> sql.execSQL(statement, args) }
    }

    private companion object {
        const val SessionEntityType = "session"
    }
}
