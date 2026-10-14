package com.reversetutor.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 反转家教长期记忆三表（V21，对齐 spike app/store.py 的 events /
 * teaching_patterns / session_snapshots 语义）。列与 feature:reversetutor
 * 的 RTMemoryStore 端口一一对应；payload 存原始 JSON 文本，读取侧展开。
 */
@Entity(
    tableName = "rt_events",
    indices = [Index("sessionId"), Index("createdAt"), Index("eventType")]
)
data class RTEventEntity(
    @PrimaryKey val eventId: String,
    val sessionId: String,
    val eventType: String,
    val concept: String,
    val payload: String,
    val createdAt: String
)

@Entity(tableName = "rt_teaching_patterns", indices = [Index("effectiveness")])
data class RTTeachingPatternEntity(
    @PrimaryKey val patternId: String,
    val strategy: String,
    val effectiveness: Double,
    val context: String,
    val createdAt: String
)

@Entity(
    tableName = "rt_session_snapshots",
    indices = [Index("studentId"), Index("createdAt")]
)
data class RTSessionSnapshotEntity(
    @PrimaryKey val sessionId: String,
    val payload: String,
    val createdAt: String,
    val studentId: String
)
