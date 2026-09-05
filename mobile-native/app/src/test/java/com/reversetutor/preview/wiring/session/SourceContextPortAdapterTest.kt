package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.local.dao.SourceDao
import com.reversetutor.core.data.local.entity.SourceChunkEntity
import com.reversetutor.core.data.local.entity.SourceEntity
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.domain.ConversationContextContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-002 plan Task 1 · source revision and hot-update projection.
 *
 * Contracts pinned here:
 *  - reprocessing the same source yields a different `sourceRevision`;
 *  - the next context read uses the new revision, while an already-captured
 *    evidence snapshot keeps the old revision and old excerpt;
 *  - evidence ids embed the revision (`source:<id>:<rev>`);
 *  - sources of another space are never visible.
 */
class SourceContextPortAdapterTest {

    private class FakeSourceDao : SourceDao {
        val sources = linkedMapOf<String, SourceEntity>()
        val chunks = linkedMapOf<String, MutableList<SourceChunkEntity>>()

        override suspend fun insertSource(source: SourceEntity) {
            sources[source.id] = source
        }

        override suspend fun insertChunk(chunk: SourceChunkEntity) {
            chunks.getOrPut(chunk.sourceId) { mutableListOf() }.add(chunk)
        }

        override suspend fun getSourceById(id: String): SourceEntity? = sources[id]

        override suspend fun listSourcesBySpace(spaceId: String): List<SourceEntity> =
            sources.values.filter { it.spaceId == spaceId }

        override suspend fun listChunksForSource(sourceId: String): List<SourceChunkEntity> =
            chunks[sourceId].orEmpty().sortedBy { it.chunkIndex }

        override suspend fun deleteChunksForSource(sourceId: String): Int {
            val removed = chunks[sourceId].orEmpty().size
            chunks[sourceId] = mutableListOf()
            return removed
        }
    }

    private fun sourceEntity(id: String, spaceId: String, createdAt: Long, title: String = "单调性讲义") =
        SourceEntity(
            id = id,
            spaceId = spaceId,
            title = title,
            type = "Text",
            parserStatus = "FullyLocal",
            createdAtEpochMillis = createdAt,
            extractedText = "正文 v@$createdAt"
        )

    private fun chunkOf(sourceId: String, spaceId: String, text: String) =
        SourceChunkEntity(
            id = "$sourceId-chunk-0",
            spaceId = spaceId,
            sourceId = sourceId,
            chunkIndex = 0,
            text = text
        )

    // 1+2. 重新处理同一 sourceId → 新 revision；下一回合读取新版本
    @Test
    fun reprocessedSourceYieldsNewRevisionForNextTurn() = runBlocking {
        val dao = FakeSourceDao()
        val repo = SourceRepository(dao)
        val adapter = SourceContextPortAdapter(repo)
        dao.insertSource(sourceEntity("src-1", "space-a", createdAt = 1000L))
        dao.insertChunk(chunkOf("src-1", "space-a", "旧版正文"))

        val first = adapter.listSourceEvidence("space-a", "sess-1", 5).single()
        assertTrue("版本标识不得为空", first.sourceRevision.isNotBlank())
        assertTrue("版本必须由 sourceId + createdAt 构成", first.sourceRevision.contains("src-1"))

        // 重新处理：同 id，新 createdAt、新正文
        dao.insertSource(sourceEntity("src-1", "space-a", createdAt = 2000L))
        dao.deleteChunksForSource("src-1")
        dao.insertChunk(chunkOf("src-1", "space-a", "新版正文"))

        val second = adapter.listSourceEvidence("space-a", "sess-1", 5).single()
        assertNotEquals(first.sourceRevision, second.sourceRevision)
        assertEquals("新版正文", second.excerpt)
        assertTrue(second.sourceRevision.contains("2000"))
        // 旧快照对象不漂移（值语义）
        assertEquals("旧版正文", first.excerpt)
    }

    // 3. 证据 ID 必须包含来源版本
    @Test
    fun evidenceIdsEmbedTheSourceRevision() = runBlocking {
        val dao = FakeSourceDao()
        val repo = SourceRepository(dao)
        dao.insertSource(sourceEntity("src-9", "space-a", createdAt = 123L))
        dao.insertChunk(chunkOf("src-9", "space-a", "正文"))
        val contract = SourceContextPortAdapter(repo)
            .listSourceEvidence("space-a", "sess-1", 5)
            .single()

        val evidence = ConversationContextContract(
            spaceId = "space-a",
            sessionId = "sess-1",
            prerequisiteGaps = emptyList(),
            relatedMemory = emptyList(),
            sourceEvidence = listOf(contract),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = emptyList(),
            recentMessages = emptyList(),
            warnings = emptyList()
        ).toLlmContextEvidence()

        val sourceEvidence = evidence.single { it.kind == "Source" }
        assertEquals("source:src-9:${contract.sourceRevision}", sourceEvidence.id)
        assertEquals("src-9", sourceEvidence.sourceId)
    }

    // 4. 空间隔离：其他空间的来源不可见
    @Test
    fun sourcesOfAnotherSpaceAreNeverVisible() = runBlocking {
        val dao = FakeSourceDao()
        val repo = SourceRepository(dao)
        dao.insertSource(sourceEntity("src-a", "space-a", createdAt = 1L))
        dao.insertSource(sourceEntity("src-b", "space-b", createdAt = 1L))
        dao.insertChunk(chunkOf("src-a", "space-a", "A空间正文"))
        dao.insertChunk(chunkOf("src-b", "space-b", "B空间正文"))

        val visible = SourceContextPortAdapter(repo).listSourceEvidence("space-a", "sess-1", 5)
        assertEquals(listOf("src-a"), visible.map { it.id })
        assertTrue(visible.none { it.excerpt.contains("B空间") })
    }
}
