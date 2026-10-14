package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.local.dao.RTMemoryDao
import com.reversetutor.core.data.local.entity.RTEventEntity
import com.reversetutor.core.data.local.entity.RTSessionSnapshotEntity
import com.reversetutor.core.data.local.entity.RTTeachingPatternEntity
import com.reversetutor.feature.reversetutor.memory.RTIds
import com.reversetutor.feature.reversetutor.memory.RTMemoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * [RTMemoryStore] 的 Room 落盘实现（V21 三表）。语义对齐 InMemoryRTMemoryStore
 * （契约基准）：addEvent 的 INSERT OR REPLACE、payload 展开与 created_at 覆盖、
 * effectiveness DESC、快照 session_id 为空则丢弃。端口为同步签名；DAO 统一在
 * Dispatchers.IO 上执行（调用方已在后台协程内，单条插入/小结果集）。
 */
class RoomRTMemoryStore(private val dao: RTMemoryDao) : RTMemoryStore {

    private inline fun <T> io(crossinline block: suspend () -> T): T =
        runBlocking { withContext(Dispatchers.IO) { block() } }

    override fun addEvent(sessionId: String, event: JSONObject): String {
        val eventId = event.optString("event_id").ifEmpty { RTIds.newId("evt") }
        val createdAt = event.optString("timestamp").ifEmpty { RTIds.nowIso() }
        val entity = RTEventEntity(
            eventId = eventId,
            sessionId = sessionId,
            eventType = event.optString("event_type", "generic"),
            concept = event.optString("concept", ""),
            payload = event.toString(),
            createdAt = createdAt
        )
        io { dao.upsertEvent(entity) }
        return eventId
    }

    override fun queryEvents(
        query: String,
        eventType: String?,
        timeRange: String?,
        limit: Int
    ): List<JSONObject> {
        val rows = io {
            dao.queryEvents(query = query, eventType = eventType, timeRange = timeRange, limit = limit)
        }
        return rows.map { e ->
            val o = JSONObject()
            o.put("event_id", e.eventId)
            o.put("session_id", e.sessionId)
            o.put("event_type", e.eventType)
            o.put("concept", e.concept)
            // 对齐 `{**json.loads(payload)}`：payload 展开覆盖基础键
            val payload = JSONObject(e.payload)
            for (k in payload.keys()) o.put(k, payload.get(k))
            // created_at 在展开之后覆盖（spike 字典字面量顺序）
            o.put("created_at", e.createdAt)
            o
        }
    }

    override fun addTeachingPattern(strategy: String, effectiveness: Double, context: String): String {
        val pid = RTIds.newId("pat")
        io {
            dao.upsertTeachingPattern(
                RTTeachingPatternEntity(
                    patternId = pid,
                    strategy = strategy,
                    effectiveness = effectiveness,
                    context = context,
                    createdAt = RTIds.nowIso()
                )
            )
        }
        return pid
    }

    override fun topTeachingPatterns(limit: Int): List<JSONObject> =
        io { dao.topTeachingPatterns(limit) }.map { p ->
            JSONObject()
                .put("pattern_id", p.patternId)
                .put("strategy", p.strategy)
                .put("effectiveness", p.effectiveness)
                .put("context", p.context)
        }

    override fun saveSnapshot(session: JSONObject) {
        val sid = session.optString("session_id")
        if (sid.isEmpty()) return
        io {
            dao.upsertSnapshot(
                RTSessionSnapshotEntity(
                    sessionId = sid,
                    payload = session.toString(),
                    createdAt = RTIds.nowIso(),
                    studentId = session.optString("student_id", "anonymous")
                )
            )
        }
    }

    override fun sessionsForReport(studentId: String?, since: String?): List<JSONObject> =
        io { dao.sessionsForReport(studentId = studentId, since = since) }
            .map { JSONObject(it.payload) }
}
