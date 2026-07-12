package com.reversetutor.core.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "world_tree_drafts",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("spaceId"),
        Index(value = ["sessionId"], unique = true)
    ]
)
data class WorldTreeDraftEntity(
    val id: String,
    val spaceId: String,
    val sessionId: String? = null,
    val templateId: String? = null,
    val title: String,
    val mode: String,
    val schemaVersion: Int,
    val state: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "world_tree_sections",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = WorldTreeDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("draftId"),
        Index(value = ["draftId", "orderIndex"], unique = true)
    ]
)
data class WorldTreeSectionEntity(
    val id: String,
    val draftId: String,
    val type: String,
    val title: String,
    val orderIndex: Int,
    val payloadJson: String,
    val required: Boolean,
    val completed: Boolean,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "world_tree_source_cross_ref",
    primaryKeys = ["draftId", "sourceId"],
    foreignKeys = [
        ForeignKey(
            entity = WorldTreeDraftEntity::class,
            parentColumns = ["id"],
            childColumns = ["draftId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("sourceId"),
        Index(value = ["draftId", "orderIndex"], unique = true)
    ]
)
data class WorldTreeSourceCrossRef(
    val draftId: String,
    val sourceId: String,
    val orderIndex: Int
)
