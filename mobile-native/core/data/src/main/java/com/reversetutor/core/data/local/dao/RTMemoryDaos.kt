package com.reversetutor.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reversetutor.core.data.local.entity.RTEventEntity
import com.reversetutor.core.data.local.entity.RTSessionSnapshotEntity
import com.reversetutor.core.data.local.entity.RTTeachingPatternEntity

/**
 * 反转家教长期记忆三表 DAO（V21）。查询语义对齐 spike store.py：
 * 关键字走 SQLite `LIKE`（ASCII 大小写不敏感，未转义 %/_，与 spike 同）；
 * created_at 相等行的顺序未定义（与 spike `ORDER BY` 同）。
 */
@Dao
interface RTMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: RTEventEntity)

    @Query(
        """SELECT * FROM rt_events
        WHERE (:eventType IS NULL OR eventType = :eventType)
          AND (:query = '' OR payload LIKE '%' || :query || '%' OR concept LIKE '%' || :query || '%')
          AND (:timeRange IS NULL OR createdAt >= :timeRange)
        ORDER BY createdAt DESC
        LIMIT :limit"""
    )
    suspend fun queryEvents(
        query: String,
        eventType: String?,
        timeRange: String?,
        limit: Int
    ): List<RTEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTeachingPattern(pattern: RTTeachingPatternEntity)

    @Query("SELECT * FROM rt_teaching_patterns ORDER BY effectiveness DESC LIMIT :limit")
    suspend fun topTeachingPatterns(limit: Int): List<RTTeachingPatternEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(snapshot: RTSessionSnapshotEntity)

    @Query(
        """SELECT * FROM rt_session_snapshots
        WHERE (:studentId IS NULL OR studentId = :studentId)
          AND (:since IS NULL OR createdAt >= :since)
        ORDER BY createdAt DESC
        LIMIT 50"""
    )
    suspend fun sessionsForReport(studentId: String?, since: String?): List<RTSessionSnapshotEntity>
}
