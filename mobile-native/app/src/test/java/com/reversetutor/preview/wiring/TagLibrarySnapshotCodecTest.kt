package com.reversetutor.preview.wiring

import com.reversetutor.feature.chat.DefaultTagLibrary
import com.reversetutor.feature.chat.QuickTag
import com.reversetutor.feature.chat.TagGroup
import com.reversetutor.feature.chat.TagLibrarySnapshot
import com.reversetutor.feature.chat.TagOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class TagLibrarySnapshotCodecTest {
    @Test
    fun completeCustomLibraryRoundTripsWithOrderGroupsAndUngroupedTags() {
        val defaults = DefaultTagLibrary.snapshot()
        val snapshot = TagLibrarySnapshot(
            groups = defaults.groups + TagGroup(
                id = "custom:group",
                name = "自定义组",
                colorIndex = 11,
                origin = TagOrigin.Custom,
                tagIds = listOf("custom:two", "custom:one")
            ),
            tags = LinkedHashMap(defaults.tags).apply {
                put("custom:one", QuickTag("custom:one", "同名", TagOrigin.Custom))
                put("custom:two", QuickTag("custom:two", "第二个", TagOrigin.Custom))
                put("ungrouped:one", QuickTag("ungrouped:one", "未分组", TagOrigin.Custom))
            },
            ungroupedTagIds = listOf("ungrouped:one")
        )

        val decoded = TagLibrarySnapshotCodec.decode(TagLibrarySnapshotCodec.encode(snapshot))

        assertEquals(snapshot, decoded)
        assertEquals(listOf("custom:two", "custom:one"), decoded.group("custom:group")?.tagIds)
    }
}
