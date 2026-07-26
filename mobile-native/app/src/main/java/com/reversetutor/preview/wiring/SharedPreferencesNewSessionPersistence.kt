package com.reversetutor.preview.wiring

import android.content.Context
import android.content.SharedPreferences
import com.reversetutor.feature.chat.EditorScrollPosition
import com.reversetutor.feature.chat.CustomColumn
import com.reversetutor.feature.chat.LearnerAvatarReference
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.NewSessionSection
import com.reversetutor.feature.chat.TagFieldSelection
import com.reversetutor.feature.chat.TagSelectionValue
import com.reversetutor.feature.chat.deepCopy

class SharedPreferencesNewSessionPersistence internal constructor(
    private val preferences: SharedPreferences
) : NewSessionPersistence {
    constructor(context: Context) : this(
        context.getSharedPreferences("new_session_feature_state", Context.MODE_PRIVATE)
    )

    override fun loadDrafts(): List<NewSessionDraftRecord> =
        preferences.getString(DraftsKey, null)
            ?.let(NewSessionSnapshotCodec::decodeDrafts)
            .orEmpty()

    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) {
        check(preferences.edit().putString(DraftsKey, NewSessionSnapshotCodec.encodeDrafts(drafts)).commit()) {
            "Unable to persist new-session drafts"
        }
    }

    override fun loadFavorites(): List<NewSessionFavorite> =
        preferences.getString(FavoritesKey, null)
            ?.let(NewSessionSnapshotCodec::decodeFavorites)
            .orEmpty()

    override fun replaceFavorites(favorites: List<NewSessionFavorite>) {
        check(preferences.edit().putString(FavoritesKey, NewSessionSnapshotCodec.encodeFavorites(favorites)).commit()) {
            "Unable to persist new-session favorites"
        }
    }

    override fun promoteDraft(
        draftId: String,
        sessionId: String,
        snapshot: NewSessionConfiguration
    ) {
        val remaining = loadDrafts().filterNot { it.id == draftId }
        check(
            preferences.edit()
                .putString(DraftsKey, NewSessionSnapshotCodec.encodeDrafts(remaining))
                .putString(SessionPrefix + sessionId, NewSessionSnapshotCodec.encodeConfiguration(snapshot.deepCopy()))
                .commit()
        ) { "Unable to promote new-session draft" }
    }

    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? =
        preferences.getString(SessionPrefix + sessionId, null)
            ?.let(NewSessionSnapshotCodec::decodeConfiguration)

    override fun saveSessionSnapshot(sessionId: String, snapshot: NewSessionConfiguration) {
        check(
            preferences.edit()
                .putString(SessionPrefix + sessionId, NewSessionSnapshotCodec.encodeConfiguration(snapshot.deepCopy()))
                .commit()
        ) { "Unable to persist session snapshot" }
    }

    private companion object {
        const val DraftsKey = "drafts_v1"
        const val FavoritesKey = "favorites_v1"
        const val SessionPrefix = "session_snapshot_v1_"
    }
}

object NewSessionSnapshotCodec {
    fun encodeDrafts(drafts: List<NewSessionDraftRecord>): String =
        pack(drafts.map(::encodeDraft))

    fun decodeDrafts(value: String): List<NewSessionDraftRecord> =
        unpack(value).mapNotNull { runCatching { decodeDraft(it) }.getOrNull() }

    fun encodeFavorites(favorites: List<NewSessionFavorite>): String =
        pack(favorites.map(::encodeFavorite))

    fun decodeFavorites(value: String): List<NewSessionFavorite> =
        unpack(value).mapNotNull { runCatching { decodeFavorite(it) }.getOrNull() }

    fun encodeConfiguration(configuration: NewSessionConfiguration): String = pack(
        listOf(
            configuration.title,
            configuration.learnerRole,
            configuration.learnerProfile,
            configuration.goal,
            configuration.plan,
            configuration.dialogueStrategy,
            configuration.story,
            pack(configuration.sourceSelections),
            pack(configuration.customFields.entries.flatMap { listOf(it.key, it.value) }),
            configuration.openingMessage,
            configuration.learnerImageRef.orEmpty(),
            configuration.storyImageRef.orEmpty(),
            configuration.builtInPresetId.orEmpty(),
            pack(configuration.customColumns.map(::encodeCustomColumn)),
            configuration.learnerDisplayName,
            configuration.avatarVisible.toString(),
            configuration.deadline,
            configuration.learningScope,
            configuration.modules,
            configuration.stageMilestones,
            configuration.currentState,
            configuration.feedbackIntensity.toString(),
            configuration.probingIntensity.toString(),
            configuration.scaffoldingIntensity.toString(),
            configuration.correctionPersistence,
            configuration.reviewFrequency,
            configuration.speakingTone,
            pack(configuration.quickTags.entries.flatMap { (field, selection) ->
                selection.values.flatMap { value -> listOf(field, value.tagId.orEmpty(), value.text) }
            })
        )
    )

    fun decodeConfiguration(value: String): NewSessionConfiguration {
        val fields = unpack(value)
        require(fields.size == 13 || fields.size == 14 || fields.size == 27 || fields.size == 28) {
            "Unexpected new-session configuration field count"
        }
        val legacy = fields.size == 13 || fields.size == 14
        val customValues = unpack(fields[8])
        return NewSessionConfiguration(
            title = fields[0],
            learnerRole = fields[1],
            learnerProfile = fields[2],
            goal = fields[3],
            plan = fields[4],
            dialogueStrategy = fields[5],
            story = fields[6],
            sourceSelections = unpack(fields[7]),
            customFields = customValues.chunked(2).mapNotNull { pair ->
                pair.takeIf { it.size == 2 }?.let { it[0] to it[1] }
            }.toMap(),
            openingMessage = fields[9],
            learnerImageRef = if (legacy) migrateLegacyLearnerImageRef(fields[10]) else fields[10].ifEmpty { null },
            storyImageRef = fields[11].ifEmpty { null },
            builtInPresetId = fields[12].ifEmpty { null },
            customColumns = fields.getOrNull(13)?.let(::unpack).orEmpty().map(::decodeCustomColumn),
            learnerDisplayName = fields.getOrNull(14) ?: recoverLegacyLearnerDisplayName(
                title = fields[0],
                learnerRole = fields[1],
                builtInPresetId = fields[12]
            ),
            avatarVisible = fields.getOrNull(15)?.toBooleanStrictOrNull() ?: true,
            deadline = fields.getOrNull(16) ?: "未设置",
            learningScope = fields.getOrNull(17) ?: "未设置",
            modules = fields.getOrNull(18) ?: "未设置",
            stageMilestones = fields.getOrNull(19) ?: "未设置",
            currentState = fields.getOrNull(20) ?: "未设置",
            feedbackIntensity = fields.getOrNull(21)?.toIntOrNull() ?: 3,
            probingIntensity = fields.getOrNull(22)?.toIntOrNull() ?: 3,
            scaffoldingIntensity = fields.getOrNull(23)?.toIntOrNull() ?: 3,
            correctionPersistence = fields.getOrNull(24) ?: "适中",
            reviewFrequency = fields.getOrNull(25) ?: "每周",
            speakingTone = fields.getOrNull(26) ?: "自然",
            quickTags = fields.getOrNull(27)?.let(::unpack).orEmpty()
                .chunked(3)
                .mapNotNull { triple ->
                    triple.takeIf { it.size == 3 }?.let {
                        it[0] to TagSelectionValue(it[1].ifEmpty { null }, it[2])
                    }
                }
                .groupBy({ it.first }, { it.second })
                .mapValues { TagFieldSelection(it.value) }
        )
    }

    private fun migrateLegacyLearnerImageRef(value: String): String? {
        LearnerAvatarReference.parse(value)?.let { return it.persistedValue }
        val resourceId = value.trim().toIntOrNull()?.takeIf { it != 0 } ?: return null
        return LearnerAvatarReference.PackagedDrawable(resourceId).persistedValue
    }

    private fun recoverLegacyLearnerDisplayName(
        title: String,
        learnerRole: String,
        builtInPresetId: String
    ): String {
        legacyPresetIdentities.firstOrNull { it.id == builtInPresetId }
            ?.let { return it.learnerName }
        legacyRoleName.find(learnerRole)?.groupValues?.getOrNull(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return it }
        return legacyPresetIdentities.firstOrNull { preset ->
            preset.title == title && learnerRole.contains(preset.learnerName)
        }?.learnerName ?: "学习者"
    }

    private fun encodeCustomColumn(column: CustomColumn): String = pack(
        listOf(
            column.id,
            column.name,
            column.content,
            pack(column.tags.values.flatMap { listOf(it.tagId.orEmpty(), it.text) })
        )
    )

    private fun decodeCustomColumn(value: String): CustomColumn {
        val fields = unpack(value)
        require(fields.size == 4) { "Unexpected custom-column field count" }
        val selections = unpack(fields[3]).chunked(2).mapNotNull { pair ->
            pair.takeIf { it.size == 2 }?.let {
                TagSelectionValue(tagId = it[0].ifEmpty { null }, text = it[1])
            }
        }
        return CustomColumn(
            id = fields[0],
            name = fields[1],
            content = fields[2],
            tags = TagFieldSelection(selections)
        )
    }

    private fun encodeDraft(draft: NewSessionDraftRecord): String = pack(
        listOf(
            draft.id,
            encodeConfiguration(draft.configuration),
            draft.updatedAtEpochMillis.toString(),
            draft.favoriteId.orEmpty(),
            draft.originBuiltInPresetId.orEmpty(),
            draft.lastSection?.name.orEmpty(),
            pack(draft.scrollPositions.entries.flatMap { entry ->
                listOf(entry.key, entry.value.index.toString(), entry.value.offset.toString())
            })
        )
    )

    private fun decodeDraft(value: String): NewSessionDraftRecord {
        val fields = unpack(value)
        require(fields.size == 7) { "Unexpected draft field count" }
        val positions = unpack(fields[6]).chunked(3).mapNotNull { triple ->
            triple.takeIf { it.size == 3 }?.let {
                it[0] to EditorScrollPosition(it[1].toInt(), it[2].toInt())
            }
        }.toMap()
        return NewSessionDraftRecord(
            id = fields[0],
            configuration = decodeConfiguration(fields[1]),
            updatedAtEpochMillis = fields[2].toLong(),
            favoriteId = fields[3].ifEmpty { null },
            originBuiltInPresetId = fields[4].ifEmpty { null },
            lastSection = fields[5].ifEmpty { null }?.let(NewSessionSection::valueOf),
            scrollPositions = positions
        )
    }

    private fun encodeFavorite(favorite: NewSessionFavorite): String = pack(
        listOf(
            favorite.id,
            favorite.name,
            encodeConfiguration(favorite.configuration),
            favorite.updatedAtEpochMillis.toString(),
            favorite.originBuiltInPresetId.orEmpty()
        )
    )

    private fun decodeFavorite(value: String): NewSessionFavorite {
        val fields = unpack(value)
        require(fields.size == 5) { "Unexpected favorite field count" }
        return NewSessionFavorite(
            id = fields[0],
            name = fields[1],
            configuration = decodeConfiguration(fields[2]),
            updatedAtEpochMillis = fields[3].toLong(),
            originBuiltInPresetId = fields[4].ifEmpty { null }
        )
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

    private data class LegacyPresetIdentity(
        val id: String,
        val title: String,
        val learnerName: String
    )

    private val legacyRoleName = Regex("^\\s*AI\\s*学生\\s+(.+?)\\s*[：:]")

    private val legacyPresetIdentities = listOf(
        LegacyPresetIdentity("formal-math-sprint", "高三数学讲题冲刺", "小岚"),
        LegacyPresetIdentity("formal-python-concepts", "Python 概念讲解", "小P"),
        LegacyPresetIdentity("formal-ielts-speaking", "雅思口语表达", "Mia"),
        LegacyPresetIdentity("formal-speech-expression", "演讲表达训练", "聆听者"),
        LegacyPresetIdentity("formal-aptitude-reasoning", "行测推理讲解", "阿策"),
        LegacyPresetIdentity("formal-frontend-explain", "前端代码讲解", "Nova"),
        LegacyPresetIdentity("formal-chemistry-lab", "高中化学实验", "元素"),
        LegacyPresetIdentity("formal-machine-learning", "机器学习概念", "Echo")
    )
}
