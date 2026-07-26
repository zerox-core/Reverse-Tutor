package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagLibraryModelsTest {
    @Test
    fun multiSelectionTogglesAndRendersInLibraryOrder() {
        val editor = editor()
        val library = editor.state.library
        val later = library.orderedTags()[2]
        val earlier = library.orderedTags()[0]

        val selected = TagFieldSelection()
            .toggle(later, library)
            .toggle(earlier, library)

        assertEquals(listOf(earlier.name, later.name), selected.orderedValues(library))
        assertTrue(selected.toggle(earlier, library).values.none { it.tagId == earlier.id })
    }

    @Test
    fun builtInTagsAndGroupsAreImmutable() {
        val editor = editor()
        val builtInGroup = editor.state.library.groups.first { it.origin == TagOrigin.BuiltIn }
        val builtInTag = editor.state.library.tags.getValue(builtInGroup.tagIds.first())
        val before = editor.state.library

        assertFalse(editor.renameTag(builtInTag.id, "改名"))
        assertFalse(editor.deleteTag(builtInTag.id))
        assertFalse(editor.moveTag(builtInTag.id, null, 0))
        assertFalse(editor.renameGroup(builtInGroup.id, "改组"))
        assertFalse(editor.deleteGroup(builtInGroup.id))
        assertEquals(before, editor.state.library)
    }

    @Test
    fun customTagsAddRenameReorderMoveDeleteAndPersistWithoutRewritingSavedText() {
        val persistence = RecordingTagLibraryPersistence()
        val editor = editor(persistence)
        val groupA = editor.createGroup("能力")
        val groupB = editor.createGroup("状态")
        val first = editor.addTag("推导", groupA)
        val second = editor.addTag("表达", groupA)
        val selected = TagFieldSelection().toggle(editor.state.library.tags.getValue(first), editor.state.library)

        assertTrue(editor.renameTag(first, "逻辑推导"))
        assertTrue(editor.reorderTag(second, 0))
        assertTrue(editor.moveTag(first, groupB, 0))
        assertTrue(editor.moveTag(first, null, 0))
        assertTrue(editor.deleteTag(first))

        assertEquals(listOf("推导"), selected.orderedValues(editor.state.library))
        assertTrue(persistence.saved.size >= 8)
        assertNull(editor.state.library.tags[first])
    }

    @Test
    fun collapsedGroupsExposeAtMostEightTagsAndBackCollapsesEveryExpandedGroup() {
        val editor = editor()
        val group = editor.createGroup("很多标签")
        repeat(10) { editor.addTag("标签$it", group) }
        repeat(10) { editor.addTag("未分组$it", null) }

        assertEquals(8, CollapsedTagVisibleCapacity)
        assertEquals(10, editor.visibleTags(group).size)
        assertEquals(10, editor.visibleTags(UngroupedTagRangeId).size)
        editor.setGroupExpanded(group, true)
        editor.setGroupExpanded(UngroupedTagRangeId, true)
        assertEquals(10, editor.visibleTags(group).size)
        assertEquals(10, editor.visibleTags(UngroupedTagRangeId).size)
        assertTrue(editor.handleBack())
        assertTrue(editor.state.expandedGroupIds.isEmpty())
        assertFalse(editor.handleBack())
    }

    @Test
    fun droppingTagsReordersWithinGroupMovesAcrossGroupsAndRequestsUngroupedGroupCreation() {
        val editor = editor()
        val firstGroup = editor.createGroup("甲组")
        val secondGroup = editor.createGroup("乙组")
        val first = editor.addTag("一", firstGroup)
        val second = editor.addTag("二", firstGroup)
        val third = editor.addTag("三", firstGroup)
        val target = editor.addTag("乙组目标", secondGroup)

        assertTrue(editor.dropTagOnto(first, third))
        assertEquals(listOf(second, first, third), editor.state.library.group(firstGroup)?.tagIds)
        assertTrue(editor.dropTagOnto(first, target))
        assertEquals(listOf(first, target), editor.state.library.group(secondGroup)?.tagIds)

        val ungroupedA = editor.addTag("未分组甲", null)
        val ungroupedB = editor.addTag("未分组乙", null)
        assertTrue(editor.dropTagOnto(ungroupedA, ungroupedB))
        assertEquals(listOf(ungroupedA, ungroupedB), editor.state.pendingGroupCreation?.initialTagIds)
    }

    @Test
    fun droppingIntoRangeSupportsEmptyGroupEmptyUngroupedAndSeparateUngroupedReorderTarget() {
        val editor = editor()
        val sourceGroup = editor.createGroup("来源组")
        val emptyGroup = editor.createGroup("空组")
        val first = editor.addTag("甲", sourceGroup)
        val second = editor.addTag("乙", sourceGroup)

        assertTrue(editor.dropTagIntoRange(first, emptyGroup))
        assertEquals(listOf(first), editor.state.library.group(emptyGroup)?.tagIds)
        assertTrue(editor.dropTagIntoRange(first, null))
        assertEquals(listOf(first), editor.state.library.ungroupedTagIds)
        assertTrue(editor.dropTagIntoRange(second, null))
        assertEquals(listOf(first, second), editor.state.library.ungroupedTagIds)

        assertTrue(editor.dropTagOnto(first, second))
        assertEquals(listOf(first, second), editor.state.pendingGroupCreation?.initialTagIds)
        editor.dismissPendingGroup()
        assertTrue(editor.dropTagIntoRange(first, null))
        assertEquals(listOf(second, first), editor.state.library.ungroupedTagIds)
    }

    @Test
    fun persistenceFailureKeepsRecoverableMemoryStateAndRetrySavesLatestSnapshot() {
        val persistence = FailingTagLibraryPersistence()
        val editor = TagLibraryEditor(persistence, idFactory = sequenceOfIds()).also { it.load() }

        val tagId = editor.addTag("尚未落盘", null)

        assertEquals("尚未落盘", editor.state.library.tags[tagId]?.name)
        assertNotNull(editor.state.pendingPersistence)
        assertNotNull(editor.state.error)
        assertEquals(TagLibraryPersistenceRetry.Save, editor.state.persistenceRetry)
        assertFalse(editor.retryLoad())
        persistence.failSave = false
        assertTrue(editor.retrySave())
        assertNull(editor.state.pendingPersistence)
        assertNull(editor.state.error)
        assertNull(editor.state.persistenceRetry)
        assertEquals(editor.state.library, persistence.saved)
        assertFalse(editor.retryPersistence())
    }

    @Test
    fun loadFailureRetryActuallyReloadsClearsErrorAndCannotMasqueradeAsSaveRetry() {
        val restored = TagLibrarySnapshot(
            groups = emptyList(),
            tags = linkedMapOf("restored" to QuickTag("restored", "已恢复", TagOrigin.Custom)),
            ungroupedTagIds = listOf("restored")
        )
        val persistence = FailingTagLibraryPersistence().apply {
            failLoad = true
            failSave = false
            saved = restored
        }
        val editor = TagLibraryEditor(persistence, idFactory = sequenceOfIds())

        editor.load()

        assertEquals(TagLibraryPersistenceRetry.Load, editor.state.persistenceRetry)
        assertNotNull(editor.state.error)
        assertNull(editor.state.pendingPersistence)
        assertFalse(editor.retrySave())
        persistence.failLoad = false
        assertTrue(editor.retryLoad())
        assertEquals(restored, editor.state.library)
        assertNull(editor.state.error)
        assertNull(editor.state.persistenceRetry)
        assertFalse(editor.retryPersistence())
    }

    @Test
    fun droppingOneUngroupedTagOnAnotherRequestsGroupWithBothMembers() {
        val editor = editor()
        val first = editor.addTag("甲", null)
        val second = editor.addTag("乙", null)

        val request = editor.dropUngroupedTagOnto(first, second)

        assertNotNull(request)
        assertEquals(listOf(first, second), request?.initialTagIds)
        assertEquals(request, editor.state.pendingGroupCreation)
    }

    @Test
    fun paletteHasExactlyTwelveLowSaturationSwatchesAndPrefersUnusedColor() {
        assertEquals(12, TagColorPalette.swatches.size)
        assertTrue(TagColorPalette.swatches.all { it.saturation in 0.10f..0.45f })
        val editor = editor()
        val used = (0 until 4).map { editor.createGroup("组$it") }

        assertEquals(listOf(0, 1, 2, 3), used.map { id -> editor.state.library.group(id)?.colorIndex })
        assertEquals(4, editor.preferredUnusedColorIndex())
    }

    @Test
    fun groupsRenameReorderChangeColorAndDeleteReturnsTagsToUngrouped() {
        val editor = editor()
        val first = editor.createGroup("第一组")
        val second = editor.createGroup("第二组")
        val tag = editor.addTag("保留我", first)

        assertTrue(editor.renameGroup(first, "已改名"))
        assertTrue(editor.changeGroupColor(first, 11))
        assertTrue(editor.reorderGroup(second, 0))
        assertTrue(editor.deleteGroup(first))

        assertEquals(11, TagColorPalette.swatches.lastIndex)
        assertTrue(tag in editor.state.library.ungroupedTagIds)
        assertEquals("保留我", editor.state.library.tags.getValue(tag).name)
    }

    @Test
    fun deletingGroupDeterministicallyRenamesUngroupedConflictsWithoutLosingHistory() {
        val editor = editor()
        val group = editor.createGroup("待删除")
        val grouped = editor.addTag("同名", group)
        val ungrouped = editor.addTag("同名", null)
        val historical = TagFieldSelection().toggle(editor.state.library.tags.getValue(grouped), editor.state.library)

        assertTrue(editor.deleteGroup(group))

        assertEquals(listOf(ungrouped, grouped), editor.state.library.ungroupedTagIds)
        assertEquals("同名", editor.state.library.tags.getValue(ungrouped).name)
        assertEquals("同名 · 2", editor.state.library.tags.getValue(grouped).name)
        assertEquals(listOf("同名"), historical.orderedValues(editor.state.library))
        assertTrue(editor.state.notice.orEmpty().contains("同名"))
    }

    @Test
    fun groupNameAndColorChangesNeverRewriteHistoricalFieldValues() {
        val editor = editor()
        val group = editor.createGroup("旧组名")
        val tagId = editor.addTag("已保存文字", group)
        val selection = TagFieldSelection().toggle(editor.state.library.tags.getValue(tagId), editor.state.library)

        editor.renameGroup(group, "新组名")
        editor.changeGroupColor(group, 10)

        assertEquals(listOf("已保存文字"), selection.orderedValues(editor.state.library))
    }

    @Test
    fun exactDuplicateNamesAreRejectedOnlyInsideSameGroupOrUngroupedRange() {
        val editor = editor()
        val firstGroup = editor.createGroup("甲组")
        val secondGroup = editor.createGroup("乙组")
        val first = editor.addTag("同名", firstGroup)

        assertNull(editor.tryAddTag("同名", firstGroup))
        assertNotNull(editor.tryAddTag("同名", secondGroup))
        assertNotNull(editor.tryAddTag("同名", null))
        assertNull(editor.tryAddTag("同名", null))
        assertFalse(editor.moveTag(first, secondGroup, 0))
    }

    private fun editor(
        persistence: TagLibraryPersistence = RecordingTagLibraryPersistence()
    ) = TagLibraryEditor(
        persistence = persistence,
        idFactory = sequenceOfIds()
    ).also { it.load() }

    private fun sequenceOfIds(): () -> String {
        var next = 0
        return { "generated-${next++}" }
    }
}

private class RecordingTagLibraryPersistence : TagLibraryPersistence {
    val saved = mutableListOf<TagLibrarySnapshot>()
    private var stored: TagLibrarySnapshot? = null

    override fun loadTagLibrary(): TagLibrarySnapshot? = stored

    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) {
        stored = snapshot.deepCopy()
        saved += stored!!
    }
}

private class FailingTagLibraryPersistence : TagLibraryPersistence {
    var failLoad = false
    var failSave = true
    var saved: TagLibrarySnapshot? = null

    override fun loadTagLibrary(): TagLibrarySnapshot? {
        if (failLoad) error("disk unavailable")
        return saved
    }

    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) {
        if (failSave) error("disk unavailable")
        saved = snapshot.deepCopy()
    }
}
