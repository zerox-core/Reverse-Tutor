package com.reversetutor.feature.reversetutor.memory

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * 反转家教长期记忆存储端口（对齐 spike `app/store.py` 的 events /
 * teaching_patterns / session_snapshots 三张表语义）。
 *
 * 为什么是端口而非 Room 实现：`core/data/local` 与 Room schema 属**冻结区**
 * （AGENTS.md §0.1），新增表必须改 `DatabaseSchema.version` 与 migration，
 * 需用户明确批准。故此处只定义契约与**进程内**默认实现；接入 Room 时
 * 替换实现即可，工具实现与契约测试无需改动。
 *
 * 已知偏差（相对 spike 的 SQLite 实现）：
 * 1. 不跨进程持久化——App 重启后事件/教学策略/会话快照丢失。
 * 2. `queryEvents` 的关键字匹配用 `contains(ignoreCase)` 近似 SQLite `LIKE`
 *    （SQLite 只对 ASCII 大小写不敏感，此处对全部字符不敏感）。
 * 3. `created_at` 相同的排序：此处为稳定排序（保留插入顺序），
 *    SQLite `ORDER BY` 对等值行的顺序未定义。
 */
interface RTMemoryStore {

    /** 对齐 spike `add_event`：`INSERT OR REPLACE`，返回 event_id。 */
    fun addEvent(sessionId: String, event: JSONObject): String

    /** 对齐 spike `query_events`：`created_at DESC`，keyword 命中 payload 或 concept。 */
    fun queryEvents(
        query: String = "",
        eventType: String? = null,
        timeRange: String? = null,
        limit: Int = 10
    ): List<JSONObject>

    /** 对齐 spike `add_teaching_pattern`：`effectiveness REAL`，返回 pattern_id。 */
    fun addTeachingPattern(strategy: String, effectiveness: Double, context: String): String

    /** 对齐 spike `top_teaching_patterns`：`ORDER BY effectiveness DESC`。 */
    fun topTeachingPatterns(limit: Int = 5): List<JSONObject>

    /** 对齐 spike `save_snapshot`：按 session_id `INSERT OR REPLACE` 快照。 */
    fun saveSnapshot(session: JSONObject)

    /** 对齐 spike `sessions_for_report`：`created_at DESC LIMIT 50`。 */
    fun sessionsForReport(studentId: String? = null, since: String? = null): List<JSONObject>
}

/** ID 与时间戳生成（对齐 spike `store.py` 的 `new_id` / `now_iso`）。 */
object RTIds {

    private val ISO_UTC = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+00:00'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** 对齐 `datetime.now(timezone.utc).isoformat(timespec="seconds")`。 */
    fun nowIso(): String = synchronized(ISO_UTC) { ISO_UTC.format(Date()) }

    /** 对齐 `f"{prefix}_{uuid4().hex[:12]}"`。 */
    fun newId(prefix: String): String =
        prefix + "_" + UUID.randomUUID().toString().replace("-", "").take(12)
}

/**
 * 进程内默认实现。语义对齐 spike 的 SQLite 实现，但不落盘。
 */
class InMemoryRTMemoryStore : RTMemoryStore {

    private data class StoredEvent(
        val eventId: String,
        val sessionId: String,
        val eventType: String,
        val concept: String,
        val payload: String,
        val createdAt: String
    )

    private data class Pattern(
        val patternId: String,
        val strategy: String,
        val effectiveness: Double,
        val context: String,
        val createdAt: String
    )

    private data class Snapshot(
        val sessionId: String,
        val payload: String,
        val createdAt: String,
        val studentId: String
    )

    private val lock = Any()
    private val events = LinkedHashMap<String, StoredEvent>()
    private val patterns = LinkedHashMap<String, Pattern>()
    private val snapshots = LinkedHashMap<String, Snapshot>()

    override fun addEvent(sessionId: String, event: JSONObject): String = synchronized(lock) {
        val eventId = event.optString("event_id").ifEmpty { RTIds.newId("evt") }
        val createdAt = event.optString("timestamp").ifEmpty { RTIds.nowIso() }
        events[eventId] = StoredEvent(
            eventId = eventId,
            sessionId = sessionId,
            eventType = event.optString("event_type", "generic"),
            concept = event.optString("concept", ""),
            payload = event.toString(),
            createdAt = createdAt
        )
        eventId
    }

    override fun queryEvents(
        query: String,
        eventType: String?,
        timeRange: String?,
        limit: Int
    ): List<JSONObject> = synchronized(lock) {
        val needle = query.lowercase(Locale.US)
        events.values
            .filter { e ->
                (eventType == null || e.eventType == eventType) &&
                    (query.isEmpty() ||
                        e.payload.lowercase(Locale.US).contains(needle) ||
                        e.concept.lowercase(Locale.US).contains(needle)) &&
                    (timeRange == null || e.createdAt >= timeRange)
            }
            .sortedByDescending { it.createdAt }
            .take(limit)
            .map { e ->
                val o = JSONObject()
                o.put("event_id", e.eventId)
                o.put("session_id", e.sessionId)
                o.put("event_type", e.eventType)
                o.put("concept", e.concept)
                // 对齐 `{**json.loads(payload)}`：payload 展开覆盖上面的基础键
                val payload = JSONObject(e.payload)
                for (k in payload.keys()) o.put(k, payload.get(k))
                // created_at 在展开之后覆盖（spike 字典字面量顺序）
                o.put("created_at", e.createdAt)
                o
            }
    }

    override fun addTeachingPattern(strategy: String, effectiveness: Double, context: String): String =
        synchronized(lock) {
            val pid = RTIds.newId("pat")
            patterns[pid] = Pattern(pid, strategy, effectiveness, context, RTIds.nowIso())
            pid
        }

    override fun topTeachingPatterns(limit: Int): List<JSONObject> = synchronized(lock) {
        patterns.values
            .sortedByDescending { it.effectiveness }
            .take(limit)
            .map { p ->
                JSONObject()
                    .put("pattern_id", p.patternId)
                    .put("strategy", p.strategy)
                    .put("effectiveness", p.effectiveness)
                    .put("context", p.context)
            }
    }

    override fun saveSnapshot(session: JSONObject) {
        val sid = session.optString("session_id")
        if (sid.isEmpty()) return
        // spike 另有 pre_level/post_level 两列，但仅 `sessions_for_report` 读取 snapshot
        // 字段，两列无任何读路径（只供 SQL 侧检视），故此处不保留。
        synchronized(lock) {
            snapshots[sid] = Snapshot(
                sessionId = sid,
                payload = session.toString(),
                createdAt = RTIds.nowIso(),
                studentId = session.optString("student_id", "anonymous")
            )
        }
    }

    override fun sessionsForReport(studentId: String?, since: String?): List<JSONObject> =
        synchronized(lock) {
            snapshots.values
                .filter { s ->
                    (studentId.isNullOrEmpty() || s.studentId == studentId) &&
                        (since.isNullOrEmpty() || s.createdAt >= since)
                }
                .sortedByDescending { it.createdAt }
                .take(50)
                .map { JSONObject(it.payload) }
        }

    /** 测试辅助：清空全部数据（对齐重建 SQLite 表的效果）。 */
    fun clear() = synchronized(lock) {
        events.clear()
        patterns.clear()
        snapshots.clear()
    }
}

/**
 * 未注入 [RTMemoryStore] 时的进程级兜底实例（对齐 spike 模块级 `STORE` 单例）。
 * 工具调用方应通过 [com.reversetutor.feature.reversetutor.tools.CoreTools.KEY_MEMORY_STORE]
 * 注入共享实例，以获得跨会话累积；测试亦应显式注入以避免相互污染。
 */
object RTMemoryStores {
    val default: RTMemoryStore by lazy { InMemoryRTMemoryStore() }
}
