package com.reversetutor.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.reversetutor.core.data.local.entity.WorldTreeDraftEntity
import com.reversetutor.core.data.local.entity.WorldTreeSectionEntity
import com.reversetutor.core.data.local.entity.WorldTreeSourceCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface WorldTreeDao {
    @Query("SELECT * FROM world_tree_drafts WHERE id = :draftId LIMIT 1")
    fun observeDraft(draftId: String): Flow<WorldTreeDraftEntity?>

    @Query("SELECT * FROM world_tree_sections WHERE draftId = :draftId ORDER BY orderIndex")
    fun observeSections(draftId: String): Flow<List<WorldTreeSectionEntity>>

    @Query("SELECT sourceId FROM world_tree_source_cross_ref WHERE draftId = :draftId ORDER BY orderIndex")
    fun observeSourceIds(draftId: String): Flow<List<String>>

    @Query("SELECT * FROM world_tree_drafts WHERE id = :draftId LIMIT 1")
    suspend fun getDraft(draftId: String): WorldTreeDraftEntity?

    @Query("SELECT * FROM world_tree_drafts WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getDraftBySessionId(sessionId: String): WorldTreeDraftEntity?

    @Query("SELECT * FROM world_tree_sections WHERE draftId = :draftId ORDER BY orderIndex")
    suspend fun listSections(draftId: String): List<WorldTreeSectionEntity>

    @Query("SELECT sourceId FROM world_tree_source_cross_ref WHERE draftId = :draftId ORDER BY orderIndex")
    suspend fun listSourceIds(draftId: String): List<String>

    @Query("SELECT COUNT(*) FROM sources WHERE spaceId = :spaceId AND id IN (:sourceIds)")
    suspend fun countSourcesInSpace(spaceId: String, sourceIds: List<String>): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDraft(draft: WorldTreeDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSections(sections: List<WorldTreeSectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourceLinks(links: List<WorldTreeSourceCrossRef>)

    @Query("DELETE FROM world_tree_sections WHERE draftId = :draftId")
    suspend fun deleteSections(draftId: String)

    @Query("DELETE FROM world_tree_source_cross_ref WHERE draftId = :draftId")
    suspend fun deleteSourceLinks(draftId: String)

    @Query("UPDATE world_tree_drafts SET title = :title, updatedAtEpochMillis = :nowEpochMillis WHERE id = :draftId")
    suspend fun updateTitle(draftId: String, title: String, nowEpochMillis: Long): Int

    @Query("UPDATE world_tree_drafts SET sessionId = :sessionId, updatedAtEpochMillis = :nowEpochMillis WHERE id = :draftId")
    suspend fun attachToSession(draftId: String, sessionId: String, nowEpochMillis: Long): Int

    @Query("UPDATE world_tree_drafts SET state = 'Archived', updatedAtEpochMillis = :nowEpochMillis WHERE id = :draftId")
    suspend fun archive(draftId: String, nowEpochMillis: Long): Int

    @Query("UPDATE world_tree_drafts SET updatedAtEpochMillis = :nowEpochMillis WHERE id = :draftId")
    suspend fun touch(draftId: String, nowEpochMillis: Long): Int
}
