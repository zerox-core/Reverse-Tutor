package com.reversetutor.preview.wiring

import android.content.Context
import com.reversetutor.feature.chat.EditorScrollPosition
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.NewSessionSection

class SharedPreferencesNewSessionPersistence(context: Context) : NewSessionPersistence {
    private val preferences = context.getSharedPreferences("new_session_feature_state", Context.MODE_PRIVATE)

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
                .putString(SessionPrefix + sessionId, NewSessionSnapshotCodec.encodeConfiguration(snapshot.copy()))
                .commit()
        ) { "Unable to promote new-session draft" }
    }

    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? =
        preferences.getString(SessionPrefix + sessionId, null)
            ?.let(NewSessionSnapshotCodec::decodeConfiguration)

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
            configuration.builtInPresetId.orEmpty()
        )
    )

    fun decodeConfiguration(value: String): NewSessionConfiguration {
        val fields = unpack(value)
        require(fields.size == 13) { "Unexpected new-session configuration field count" }
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
            learnerImageRef = fields[10].ifEmpty { null },
            storyImageRef = fields[11].ifEmpty { null },
            builtInPresetId = fields[12].ifEmpty { null }
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
}
