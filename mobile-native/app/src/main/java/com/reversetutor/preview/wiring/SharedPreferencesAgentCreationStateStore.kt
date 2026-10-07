package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.AgentCreationDocAnalysis
import com.reversetutor.feature.chat.AgentCreationFeedEntry
import com.reversetutor.feature.chat.AgentCreationHistoryTurn
import com.reversetutor.feature.chat.AgentCreationPlannerState
import com.reversetutor.feature.chat.AgentCreationSnapshot
import com.reversetutor.feature.chat.AgentCreationStateStore

/**
 * R85：创建会话状态的本地仓库——快照落 SharedPreferences，
 * 切走页面 / 进程被杀后回到创建窗口时恢复创建进度，接着聊。
 */
class SharedPreferencesAgentCreationStateStore internal constructor(
    private val preferences: SharedPreferences
) : AgentCreationStateStore {
    constructor(context: Context) : this(
        context.getSharedPreferences("agent_creation_state", Context.MODE_PRIVATE)
    )

    override fun load(): AgentCreationSnapshot? =
        preferences.getString(SnapshotKey, null)
            ?.let { value -> runCatching { AgentCreationSnapshotCodec.decode(value) }.getOrNull() }

    override fun save(snapshot: AgentCreationSnapshot) {
        check(preferences.edit().putString(SnapshotKey, AgentCreationSnapshotCodec.encode(snapshot)).commit()) {
            "Unable to persist agent creation snapshot"
        }
    }

    override fun clear() {
        preferences.edit().remove(SnapshotKey).apply()
    }

    private companion object {
        const val SnapshotKey = "snapshot_v1"
    }
}

/**
 * 创建会话快照编解码（R85）：长度前缀位置编码，与 [NewSessionSnapshotCodec] 同风格；
 * 草案与草案卡配置直接复用其 29 字段编码。
 */
object AgentCreationSnapshotCodec {
    fun encode(snapshot: AgentCreationSnapshot): String = pack(
        listOf(
            Version,
            NewSessionSnapshotCodec.encodeConfiguration(snapshot.draft),
            snapshot.rawUnderstanding.toString(),
            if (snapshot.requestDocumentActive) "1" else "0",
            snapshot.entrySequence.toString(),
            snapshot.planner.rounds.toString(),
            if (snapshot.planner.converged) "1" else "0",
            pack(snapshot.planner.askedCounts.entries.flatMap { listOf(it.key, it.value.toString()) }),
            pack(snapshot.history.map { pack(listOf(if (it.isUser) "1" else "0", it.text)) }),
            pack(snapshot.feed.map(::encodeFeedEntry)),
            snapshot.docAnalysis?.let(::encodeDocAnalysis).orEmpty(),
            if (snapshot.planner.documentAsked) "1" else "0",
            if (snapshot.planner.pathConfirmAsked) "1" else "0"
        )
    )

    /** 损坏 / 版本不符一律返回 null：创建窗口从零开始，不抛异常。 */
    fun decode(value: String): AgentCreationSnapshot? = runCatching {
        val fields = unpack(value)
        if ((fields.size != FieldCount && fields.size != FieldCountV2) || fields[0] != Version) return null
        val askedCounts = unpack(fields[7]).chunked(2).mapNotNull { pair ->
            pair.takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: return@let null) }
        }.toMap()
        AgentCreationSnapshot(
            draft = NewSessionSnapshotCodec.decodeConfiguration(fields[1]),
            rawUnderstanding = fields[2].toIntOrNull() ?: 0,
            requestDocumentActive = fields[3] == "1",
            entrySequence = fields[4].toIntOrNull() ?: 0,
            planner = AgentCreationPlannerState(
                rounds = fields[5].toIntOrNull() ?: 0,
                converged = fields[6] == "1",
                askedCounts = askedCounts,
                documentAsked = fields.getOrNull(11) == "1",
                pathConfirmAsked = fields.getOrNull(12) == "1"
            ),
            history = unpack(fields[8]).mapNotNull(::decodeHistoryTurn),
            feed = unpack(fields[9]).mapNotNull(::decodeFeedEntry),
            docAnalysis = fields[10].takeIf { it.isNotEmpty() }?.let(::decodeDocAnalysis)
        )
    }.getOrNull()

    private fun encodeFeedEntry(entry: AgentCreationFeedEntry): String = when (entry) {
        is AgentCreationFeedEntry.Assistant -> pack(listOf(TagAssistant, entry.id, entry.text))
        is AgentCreationFeedEntry.User -> pack(listOf(TagUser, entry.id, entry.text))
        is AgentCreationFeedEntry.FileCard -> pack(
            listOf(TagFileCard, entry.id, entry.fileName, entry.sizeLabel, entry.status.ordinal.toString())
        )
        is AgentCreationFeedEntry.DraftCard -> pack(
            listOf(TagDraftCard, entry.id, NewSessionSnapshotCodec.encodeConfiguration(entry.configuration))
        )
    }

    private fun decodeFeedEntry(value: String): AgentCreationFeedEntry? {
        val fields = unpack(value)
        return when (fields.firstOrNull()) {
            TagAssistant -> fields.takeIf { it.size == 3 }
                ?.let { AgentCreationFeedEntry.Assistant(id = it[1], text = it[2]) }
            TagUser -> fields.takeIf { it.size == 3 }
                ?.let { AgentCreationFeedEntry.User(id = it[1], text = it[2]) }
            TagFileCard -> fields.takeIf { it.size == 5 }?.let {
                val status = AgentCreationFeedEntry.FileCard.FileStatus.values()
                    .getOrElse(it[4].toIntOrNull() ?: -1) { AgentCreationFeedEntry.FileCard.FileStatus.Failed }
                AgentCreationFeedEntry.FileCard(
                    id = it[1],
                    fileName = it[2],
                    sizeLabel = it[3],
                    // 进程被杀时可能停在「分析中」——恢复一律降级为「失败 · 可重试」。
                    status = if (status == AgentCreationFeedEntry.FileCard.FileStatus.Analyzing) {
                        AgentCreationFeedEntry.FileCard.FileStatus.Failed
                    } else {
                        status
                    }
                )
            }
            TagDraftCard -> fields.takeIf { it.size == 3 }?.let {
                AgentCreationFeedEntry.DraftCard(
                    id = it[1],
                    configuration = NewSessionSnapshotCodec.decodeConfiguration(it[2])
                )
            }
            else -> null
        }
    }

    private fun decodeHistoryTurn(value: String): AgentCreationHistoryTurn? {
        val fields = unpack(value)
        return fields.takeIf { it.size == 2 }
            ?.let { AgentCreationHistoryTurn(isUser = it[0] == "1", text = it[1]) }
    }

    private fun encodeDocAnalysis(analysis: AgentCreationDocAnalysis): String = pack(
        listOf(
            analysis.materialTitle,
            analysis.materialType,
            analysis.difficulty.toString(),
            analysis.summary,
            pack(analysis.outline),
            pack(analysis.knowledgePoints),
            pack(analysis.prerequisites),
            pack(analysis.suggestedPath)
        )
    )

    private fun decodeDocAnalysis(value: String): AgentCreationDocAnalysis? {
        val fields = unpack(value)
        return fields.takeIf { it.size == 8 }?.let {
            AgentCreationDocAnalysis(
                materialTitle = it[0],
                materialType = it[1],
                difficulty = it[2].toFloatOrNull() ?: 0.5f,
                summary = it[3],
                outline = unpack(it[4]),
                knowledgePoints = unpack(it[5]),
                prerequisites = unpack(it[6]),
                suggestedPath = unpack(it[7])
            )
        }
    }

    private fun pack(values: List<String>): String = buildString {
        values.forEach { value ->
            append(value.length)
            append(':')
            append(value)
        }
    }

    private fun unpack(value: String): List<String> {
        if (value.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var cursor = 0
        while (cursor < value.length) {
            val separator = value.indexOf(':', cursor)
            require(separator > cursor) { "Invalid length-prefixed value" }
            val length = value.substring(cursor, separator).toInt()
            val start = separator + 1
            val end = start + length
            require(end <= value.length) { "Truncated length-prefixed value" }
            result += value.substring(start, end)
            cursor = end
        }
        return result
    }

    private const val Version = "v1"
    private const val FieldCount = 11
    private const val FieldCountV2 = 13
    private const val TagAssistant = "A"
    private const val TagUser = "U"
    private const val TagFileCard = "F"
    private const val TagDraftCard = "D"
}
