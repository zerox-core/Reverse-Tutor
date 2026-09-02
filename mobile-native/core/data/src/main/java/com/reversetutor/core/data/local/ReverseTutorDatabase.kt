package com.reversetutor.core.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.reversetutor.core.data.local.dao.BackgroundJobDao
import com.reversetutor.core.data.local.dao.ExportRecordDao
import com.reversetutor.core.data.local.dao.GraphDao
import com.reversetutor.core.data.local.dao.ImportBatchDao
import com.reversetutor.core.data.local.dao.LearningDao
import com.reversetutor.core.data.local.dao.LlmProfileDao
import com.reversetutor.core.data.local.dao.MemoryDao
import com.reversetutor.core.data.local.dao.MessageAttachmentDao
import com.reversetutor.core.data.local.dao.MessageDao
import com.reversetutor.core.data.local.dao.MessageQuoteDao
import com.reversetutor.core.data.local.dao.SessionDao
import com.reversetutor.core.data.local.dao.SessionSettingsDao
import com.reversetutor.core.data.local.dao.ModelConnectionDao
import com.reversetutor.core.data.local.dao.SearchDocumentDao
import com.reversetutor.core.data.local.dao.SourceDao
import com.reversetutor.core.data.local.dao.SpaceDao
import com.reversetutor.core.data.local.dao.SyncDao
import com.reversetutor.core.data.local.dao.TurnRunDao
import com.reversetutor.core.data.local.dao.WindowTopologyDao
import com.reversetutor.core.data.local.dao.LearningLedgerDao
import com.reversetutor.core.data.local.dao.CompanionMemoryDao
import com.reversetutor.core.data.local.dao.WindowHeartbeatDao
import com.reversetutor.core.data.local.dao.WorldTreeDao
import com.reversetutor.core.data.local.dao.SessionAgentDao
import com.reversetutor.core.data.local.entity.AnchorEntity
import com.reversetutor.core.data.local.entity.BackgroundJobEntity
import com.reversetutor.core.data.local.entity.ErrorLogEntity
import com.reversetutor.core.data.local.entity.ExportRecordEntity
import com.reversetutor.core.data.local.entity.GraphEdgeEntity
import com.reversetutor.core.data.local.entity.GraphNodeEntity
import com.reversetutor.core.data.local.entity.ImportBatchEntity
import com.reversetutor.core.data.local.entity.ContextSnapshotEntity
import com.reversetutor.core.data.local.entity.EntityTombstoneEntity
import com.reversetutor.core.data.local.entity.LlmProfileEntity
import com.reversetutor.core.data.local.entity.MemoryItemEntity
import com.reversetutor.core.data.local.entity.MessageAttachmentEntity
import com.reversetutor.core.data.local.entity.MessageEntity
import com.reversetutor.core.data.local.entity.MessageQuoteEntity
import com.reversetutor.core.data.local.entity.ModelBindingEntity
import com.reversetutor.core.data.local.entity.NoteEntity
import com.reversetutor.core.data.local.entity.SessionEntity
import com.reversetutor.core.data.local.entity.SessionSettingsEntity
import com.reversetutor.core.data.local.entity.ProviderConnectionEntity
import com.reversetutor.core.data.local.entity.SearchDocumentEntity
import com.reversetutor.core.data.local.entity.StudyPlanTaskEntity
import com.reversetutor.core.data.local.entity.SourceChunkEntity
import com.reversetutor.core.data.local.entity.SourceEntity
import com.reversetutor.core.data.local.entity.SpaceEntity
import com.reversetutor.core.data.local.entity.SyncConflictEntity
import com.reversetutor.core.data.local.entity.SyncCursorEntity
import com.reversetutor.core.data.local.entity.SyncOutboxEntity
import com.reversetutor.core.data.local.entity.TokenUsageRecordEntity
import com.reversetutor.core.data.local.entity.TurnRunEntity
import com.reversetutor.core.data.local.entity.WeeklySummaryEntity
import com.reversetutor.core.data.local.entity.WidgetLayoutPreferenceEntity
import com.reversetutor.core.data.local.entity.WorldTreeDraftEntity
import com.reversetutor.core.data.local.entity.WorldTreeSectionEntity
import com.reversetutor.core.data.local.entity.WorldTreeSourceCrossRef
import com.reversetutor.core.data.local.entity.WindowEntity
import com.reversetutor.core.data.local.entity.WindowSnapshotEntity
import com.reversetutor.core.data.local.entity.WindowDeltaEntity
import com.reversetutor.core.data.local.entity.MergeCommitEntity
import com.reversetutor.core.data.local.entity.LearningFactReceiptEntity
import com.reversetutor.core.data.local.entity.ScopeSignalEntity
import com.reversetutor.core.data.local.entity.CompanionMemoryVersionEntity
import com.reversetutor.core.data.local.entity.MemoryObservationEntity
import com.reversetutor.core.data.local.entity.WindowHeartbeatEntity
import com.reversetutor.core.data.local.entity.AssistantReplyArtifactEntity
import com.reversetutor.core.data.local.entity.SessionDocumentEntity
import com.reversetutor.core.data.local.entity.SessionDocumentBlockEntity
import com.reversetutor.core.data.local.entity.SessionTableEntity
import com.reversetutor.core.data.local.entity.SessionTableColumnEntity
import com.reversetutor.core.data.local.entity.SessionTableRowEntity
import com.reversetutor.core.data.local.entity.ToolCallReceiptEntity

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
        ExportRecordEntity::class,
        ProviderConnectionEntity::class,
        ModelBindingEntity::class,
        ContextSnapshotEntity::class,
        TurnRunEntity::class,
        StudyPlanTaskEntity::class,
        WeeklySummaryEntity::class,
        TokenUsageRecordEntity::class,
        WidgetLayoutPreferenceEntity::class,
        SearchDocumentEntity::class,
        SyncOutboxEntity::class,
        SyncCursorEntity::class,
        SyncConflictEntity::class,
        EntityTombstoneEntity::class,
        WorldTreeDraftEntity::class,
        WorldTreeSectionEntity::class,
        WorldTreeSourceCrossRef::class,
        WindowEntity::class,
        WindowSnapshotEntity::class,
        WindowDeltaEntity::class,
        MergeCommitEntity::class,
        LearningFactReceiptEntity::class,
        ScopeSignalEntity::class,
        CompanionMemoryVersionEntity::class,
        MemoryObservationEntity::class,
        WindowHeartbeatEntity::class,
        AssistantReplyArtifactEntity::class,
        SessionDocumentEntity::class,
        SessionDocumentBlockEntity::class,
        SessionTableEntity::class,
        SessionTableColumnEntity::class,
        SessionTableRowEntity::class,
        ToolCallReceiptEntity::class
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
    abstract fun modelConnectionDao(): ModelConnectionDao
    abstract fun turnRunDao(): TurnRunDao
    abstract fun learningDao(): LearningDao
    abstract fun searchDocumentDao(): SearchDocumentDao
    abstract fun syncDao(): SyncDao
    abstract fun worldTreeDao(): WorldTreeDao
    abstract fun windowTopologyDao(): WindowTopologyDao
    abstract fun learningLedgerDao(): LearningLedgerDao
    abstract fun companionMemoryDao(): CompanionMemoryDao
    abstract fun windowHeartbeatDao(): WindowHeartbeatDao
    abstract fun sessionAgentDao(): SessionAgentDao
}
