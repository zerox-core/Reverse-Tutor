package com.reversetutor.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reversetutor.core.data.local.dao.BackgroundJobDao
import com.reversetutor.core.data.local.dao.ExportRecordDao
import com.reversetutor.core.data.local.dao.GraphDao
import com.reversetutor.core.data.local.dao.ImportBatchDao
import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.dao.MemoryDao
import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.dao.SessionSettingsDao
import com.reversetutor.core.data.local.dao.SourceDao
import com.reversetutor.core.data.local.dao.SpaceDao
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

@Database(
    entities = [
        SpaceEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        MessageAttachmentEntity::class,
        MessageQuoteEntity::class,
        LlmProfileEntity::class,
        SessionSettingsEntity::class,
        AnchorEntity::class,
        NoteEntity::class,
        ErrorLogEntity::class,
        MemoryItemEntity::class,
        GraphNodeEntity::class,
        GraphEdgeEntity::class,
        SourceEntity::class,
        SourceChunkEntity::class,
        BackgroundJobEntity::class,
        ImportBatchEntity::class,
        ExportRecordEntity::class
    ],
    version = DatabaseSchema.version,
    exportSchema = DatabaseSchema.exportSchema
)
abstract class ReverseTutorDatabase : RoomDatabase() {
    abstract fun spaceDao(): SpaceDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionSettingsDao(): SessionSettingsDao
    abstract fun messageDao(): MessageDao
    abstract fun messageAttachmentDao(): MessageAttachmentDao
    abstract fun messageQuoteDao(): MessageQuoteDao
    abstract fun llmProfileDao(): LlmProfileDao
    abstract fun memoryDao(): MemoryDao
    abstract fun graphDao(): GraphDao
    abstract fun sourceDao(): SourceDao
    abstract fun backgroundJobDao(): BackgroundJobDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun exportRecordDao(): ExportRecordDao
}
