package com.reversetutor.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.reversetutor.core.data.local.entity.AnchorEntity
import com.reversetutor.core.data.local.entity.BackgroundJobEntity
import com.reversetutor.core.data.local.entity.ErrorLogEntity
import com.reversetutor.core.data.local.entity.ExportRecordEntity
import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.data.local.entity.ImportBatchEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MemoryItemEntity
import com.reversetutor.core.data.local.entity.MessageAttachmentEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.data.local.entity.NoteEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.SourceChunkEntity
import com.reversetutor.core.data.local.entity.SourceEntity
import com.reversetutor.core.data.local.entity.SpaceEntity

@Dao
interface SpaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(space: SpaceEntity)

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getById(id: String): SpaceEntity?

    @Query("SELECT * FROM spaces ORDER BY updatedAtEpochMillis DESC")
    suspend fun listAll(): List<SpaceEntity>
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE spaceId = :spaceId AND archived = 0 ORDER BY pinned DESC, updatedAtEpochMillis DESC")
    suspend fun listBySpace(spaceId: String): List<SessionEntity>

    @Query("UPDATE sessions SET title = :title, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun rename(id: String, title: String, updatedAtEpochMillis: Long): Int

    @Query("UPDATE sessions SET pinned = :pinned, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, updatedAtEpochMillis: Long): Int

    @Query("UPDATE sessions SET archived = 1, updatedAtEpochMillis = :updatedAtEpochMillis WHERE id = :id")
    suspend fun archive(id: String, updatedAtEpochMillis: Long): Int

    @Query("UPDATE sessions SET modelBindingId = :modelBindingId WHERE id = :sessionId")
    suspend fun updateSessionModelBinding(sessionId: String, modelBindingId: String): Int

    @Query("UPDATE session_settings SET modelBindingId = :modelBindingId WHERE sessionId = :sessionId")
    suspend fun updateSessionSettingsModelBinding(sessionId: String, modelBindingId: String): Int

    @Transaction
    suspend fun setModelBinding(sessionId: String, modelBindingId: String): Boolean {
        val updated = updateSessionModelBinding(sessionId, modelBindingId)
        if (updated > 0) {
            updateSessionSettingsModelBinding(sessionId, modelBindingId)
        }
        return updated > 0
    }
}

@Dao
interface SessionSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SessionSettingsEntity)

    @Query("SELECT * FROM session_settings WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getBySessionId(sessionId: String): SessionSettingsEntity?
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAtEpochMillis ASC")
    suspend fun listBySession(sessionId: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: String): Int
}

@Dao
interface MessageAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: MessageAttachmentEntity)

    @Query("SELECT * FROM message_attachments WHERE messageId = :messageId ORDER BY name ASC")
    suspend fun listByMessageId(messageId: String): List<MessageAttachmentEntity>

    @Query("SELECT * FROM message_attachments WHERE messageId IN (:messageIds) ORDER BY messageId ASC, name ASC")
    suspend fun listByMessageIds(messageIds: List<String>): List<MessageAttachmentEntity>

    @Query("DELETE FROM message_attachments WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String): Int
}

@Dao
interface MessageQuoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quote: MessageQuoteEntity)

    @Query("SELECT * FROM message_quotes WHERE messageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: String): MessageQuoteEntity?

    @Query("DELETE FROM message_quotes WHERE messageId = :messageId")
    suspend fun deleteByMessageId(messageId: String): Int
}

@Dao
interface LlmProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: LlmProfileEntity)

    @Query("SELECT * FROM llm_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): LlmProfileEntity?

    @Query("SELECT * FROM llm_profiles WHERE spaceId = :spaceId ORDER BY updatedAtEpochMillis DESC")
    suspend fun listBySpace(spaceId: String): List<LlmProfileEntity>

    @Query("SELECT * FROM llm_profiles ORDER BY updatedAtEpochMillis DESC")
    suspend fun listAll(): List<LlmProfileEntity>

    @Query("UPDATE llm_profiles SET enabled = CASE WHEN id = :enabledProfileId THEN 1 ELSE 0 END, updatedAtEpochMillis = :updatedAtEpochMillis WHERE spaceId = :spaceId")
    suspend fun setEnabledForSpace(spaceId: String, enabledProfileId: String, updatedAtEpochMillis: Long)

    @Query("DELETE FROM llm_profiles WHERE id = :id")
    suspend fun deleteById(id: String): Int
}

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnchor(anchor: AnchorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertError(error: ErrorLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryItem(memoryItem: MemoryItemEntity)

    @Query("SELECT * FROM anchors WHERE spaceId = :spaceId ORDER BY createdAtEpochMillis DESC")
    suspend fun listAnchorsBySpace(spaceId: String): List<AnchorEntity>

    @Query("SELECT * FROM notes WHERE spaceId = :spaceId ORDER BY createdAtEpochMillis DESC")
    suspend fun listNotesBySpace(spaceId: String): List<NoteEntity>

    @Query("SELECT * FROM error_logs WHERE spaceId = :spaceId ORDER BY createdAtEpochMillis DESC")
    suspend fun listErrorsBySpace(spaceId: String): List<ErrorLogEntity>

    @Query("SELECT * FROM memory_items WHERE spaceId = :spaceId ORDER BY createdAtEpochMillis DESC")
    suspend fun listMemoryItemsBySpace(spaceId: String): List<MemoryItemEntity>

    @Query("UPDATE notes SET title = :title, body = :body WHERE id = :id")
    suspend fun updateNote(id: String, title: String, body: String): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNote(id: String): Int

    @Query("DELETE FROM anchors WHERE id = :id")
    suspend fun deleteAnchor(id: String): Int

    @Query("UPDATE error_logs SET resolved = :resolved WHERE id = :id")
    suspend fun setErrorResolved(id: String, resolved: Boolean): Int
}

@Dao
interface GraphDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNode(node: GraphNodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: GraphEdgeEntity)

    @Query("SELECT * FROM graph_nodes WHERE spaceId = :spaceId ORDER BY label ASC")
    suspend fun listNodesBySpace(spaceId: String): List<GraphNodeEntity>

    @Query(
        """
        SELECT DISTINCT graph_nodes.*
        FROM graph_nodes
        INNER JOIN memory_items
            ON memory_items.id = graph_nodes.sourceMemoryId
        INNER JOIN messages
            ON messages.id = memory_items.sourceMessageId
        WHERE messages.sessionId = :sessionId
        ORDER BY graph_nodes.label ASC
        """
    )
    suspend fun listNodesBySession(sessionId: String): List<GraphNodeEntity>

    @Query("SELECT * FROM graph_edges WHERE spaceId = :spaceId ORDER BY createdAtEpochMillis ASC")
    suspend fun listEdgesBySpace(spaceId: String): List<GraphEdgeEntity>
}

@Dao
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunk(chunk: SourceChunkEntity)

    @Query("SELECT * FROM sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: String): SourceEntity?

    @Query("SELECT * FROM sources WHERE spaceId = :spaceId ORDER BY createdAtEpochMillis DESC")
    suspend fun listSourcesBySpace(spaceId: String): List<SourceEntity>

    @Query("SELECT * FROM source_chunks WHERE sourceId = :sourceId ORDER BY chunkIndex ASC")
    suspend fun listChunksForSource(sourceId: String): List<SourceChunkEntity>

    @Query("DELETE FROM source_chunks WHERE sourceId = :sourceId")
    suspend fun deleteChunksForSource(sourceId: String): Int
}

@Dao
interface BackgroundJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: BackgroundJobEntity)

    @Query("SELECT * FROM background_jobs WHERE id = :id")
    suspend fun getById(id: String): BackgroundJobEntity?

    @Query("SELECT * FROM background_jobs WHERE kind = 'Generation' AND status IN (:statuses) ORDER BY createdAtEpochMillis ASC")
    suspend fun listGenerationByStatuses(statuses: List<String>): List<BackgroundJobEntity>

    @Query("SELECT * FROM background_jobs WHERE kind = 'Generation' AND sessionId = :sessionId ORDER BY createdAtEpochMillis ASC")
    suspend fun listGenerationBySession(sessionId: String): List<BackgroundJobEntity>

}

@Dao
interface ImportBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun getById(id: String): ImportBatchEntity?
}

@Dao
interface ExportRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExportRecordEntity)

    @Query("SELECT * FROM export_records WHERE id = :id")
    suspend fun getById(id: String): ExportRecordEntity?
}
