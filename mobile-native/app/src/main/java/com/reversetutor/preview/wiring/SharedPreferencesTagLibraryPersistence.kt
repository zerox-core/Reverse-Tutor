package com.reversetutor.preview.wiring

import android.content.Context
import com.reversetutor.feature.chat.QuickTag
import com.reversetutor.feature.chat.TagGroup
import com.reversetutor.feature.chat.TagLibraryPersistence
import com.reversetutor.feature.chat.TagLibrarySnapshot
import com.reversetutor.feature.chat.TagOrigin

class SharedPreferencesTagLibraryPersistence(context: Context) : TagLibraryPersistence {
    private val preferences = context.getSharedPreferences("tag_library_feature_state", Context.MODE_PRIVATE)

    override fun loadTagLibrary(): TagLibrarySnapshot? =
        preferences.getString(LibraryKey, null)?.let { encoded ->
            runCatching { TagLibrarySnapshotCodec.decode(encoded) }.getOrNull()
        }

    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) {
        check(preferences.edit().putString(LibraryKey, TagLibrarySnapshotCodec.encode(snapshot)).commit()) {
            "Unable to persist Tag library"
        }
    }

    private companion object {
        const val LibraryKey = "tag_library_v1"
    }
}

object TagLibrarySnapshotCodec {
    fun encode(snapshot: TagLibrarySnapshot): String = pack(
        listOf(
            pack(snapshot.groups.map(::encodeGroup)),
            pack(snapshot.tags.values.map(::encodeTag)),
            pack(snapshot.ungroupedTagIds)
        )
    )

    fun decode(value: String): TagLibrarySnapshot {
        val fields = unpack(value)
        require(fields.size == 3) { "Unexpected Tag library field count" }
        val groups = unpack(fields[0]).map(::decodeGroup)
        val tags = unpack(fields[1]).map(::decodeTag).associateByTo(LinkedHashMap(), QuickTag::id)
        val ungrouped = unpack(fields[2])
        val referenced = groups.flatMap(TagGroup::tagIds) + ungrouped
        require(referenced.all(tags::containsKey)) { "Tag library references an unknown Tag" }
        require(referenced.size == referenced.distinct().size) { "Tag library contains duplicate placements" }
        return TagLibrarySnapshot(groups, tags, ungrouped)
    }

    private fun encodeGroup(group: TagGroup): String = pack(
        listOf(group.id, group.name, group.colorIndex.toString(), group.origin.name, pack(group.tagIds))
    )

    private fun decodeGroup(value: String): TagGroup {
        val fields = unpack(value)
        require(fields.size == 5) { "Unexpected Tag group field count" }
        return TagGroup(
            id = fields[0],
            name = fields[1],
            colorIndex = fields[2].toInt(),
            origin = TagOrigin.valueOf(fields[3]),
            tagIds = unpack(fields[4])
        )
    }

    private fun encodeTag(tag: QuickTag): String = pack(listOf(tag.id, tag.name, tag.origin.name))

    private fun decodeTag(value: String): QuickTag {
        val fields = unpack(value)
        require(fields.size == 3) { "Unexpected Tag field count" }
        return QuickTag(fields[0], fields[1], TagOrigin.valueOf(fields[2]))
    }

    private fun pack(values: List<String>): String = buildString {
        values.forEach { value -> append(value.length).append(':').append(value) }
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
