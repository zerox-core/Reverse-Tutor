package com.reversetutor.feature.chat

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomColumnModelsTest {
    @Test
    fun oneEditorAddsAndEditsUniqueNamedTextColumnsWithCategorizedTags() {
        val library = DefaultTagLibrary.snapshot()
        val tag = library.orderedTags().first()
        val editor = CustomColumnEditor(idFactory = ids())

        val columnId = editor.addColumn(
            name = "能力",
            content = "能独立推导",
            tags = TagFieldSelection().toggle(tag, library)
        )!!

        assertNull(editor.addColumn("能力", "重复"))
        assertTrue(editor.editColumn(columnId, "能力画像", "会举反例", editor.state.columns.single().tags))
        assertEquals("能力画像", editor.state.columns.single().name)
        assertEquals("会举反例", editor.state.columns.single().content)
        assertEquals(listOf(tag.name), editor.state.columns.single().tags.orderedValues(library))
    }

    @Test
    fun columnsSupportLongPressOrderCopySuffixAndConfirmedDelete() {
        val editor = CustomColumnEditor(idFactory = ids())
        val first = editor.addColumn("能力", "一")!!
        val second = editor.addColumn("情绪", "二")!!

        assertTrue(editor.reorderColumn(second, 0))
        assertEquals(listOf(second, first), editor.state.columns.map { it.id })
        val copy = editor.copyColumn(first)!!
        assertEquals("能力 副本", editor.state.columns.first { it.id == copy }.name)

        editor.requestDelete(first)
        assertEquals(first, editor.state.pendingDeleteColumnId)
        editor.cancelDelete()
        assertTrue(editor.state.columns.any { it.id == first })
        editor.requestDelete(first)
        assertTrue(editor.confirmDelete())
        assertFalse(editor.state.columns.any { it.id == first })
    }

    @Test
    fun droppingColumnOntoAnotherPerformsRealReorderContract() {
        val editor = CustomColumnEditor(idFactory = ids())
        val first = editor.addColumn("第一", "一")!!
        val second = editor.addColumn("第二", "二")!!
        val third = editor.addColumn("第三", "三")!!

        assertTrue(editor.dropColumnOnto(first, third))

        assertEquals(listOf(second, first, third), editor.state.columns.map(CustomColumn::id))
    }

    @Test
    fun dragBoundsRegistryFiltersStaleTargetsAndSupportsExplicitCleanup() {
        val registry = DragBoundsRegistry<String>()
        val sharedBounds = Rect(0f, 0f, 20f, 20f)
        registry.update("removed", sharedBounds)
        registry.update("visible", sharedBounds)

        assertEquals("visible", registry.hitTest(Offset(10f, 10f), listOf("visible")))
        assertNull(registry.hitTest(Offset(10f, 10f), emptyList()))

        registry.retainOnly(setOf("visible"))
        assertEquals(setOf("visible"), registry.trackedIds())
        registry.remove("visible")
        assertTrue(registry.trackedIds().isEmpty())
    }

    @Test
    fun draftFavoriteAndSessionSnapshotsStayIsolatedAndKeepDeletedTagText() {
        val libraryEditor = TagLibraryEditor(RecordingPersistence(), idFactory = ids()).also { it.load() }
        val tagId = libraryEditor.addTag("紧张", null)
        val tag = libraryEditor.state.library.tags.getValue(tagId)
        val originalColumn = CustomColumn(
            id = "column-1",
            name = "情绪",
            content = "愿意尝试",
            tags = TagFieldSelection().toggle(tag, libraryEditor.state.library)
        )
        val template = NewSessionConfiguration(customColumns = listOf(originalColumn))
        val draft = template.deepCopy().withCustomColumns(
            listOf(originalColumn.copy(content = "草稿已修改"))
        )
        val historicalSession = template.deepCopy()
        val favorite = template.deepCopy()

        libraryEditor.deleteTag(tagId)
        val editedSession = historicalSession.withCustomColumns(
            listOf(originalColumn.copy(content = "仅会话修改"))
        )

        assertEquals("愿意尝试", template.customColumns.single().content)
        assertEquals("愿意尝试", favorite.customColumns.single().content)
        assertEquals("草稿已修改", draft.customColumns.single().content)
        assertEquals("仅会话修改", editedSession.customColumns.single().content)
        assertEquals("愿意尝试", historicalSession.customColumns.single().content)
        assertEquals(listOf("紧张"), historicalSession.customColumns.single().tags.orderedValues(libraryEditor.state.library))
        assertNotEquals(draft, historicalSession)
    }

    @Test
    fun modelContainsOnlyLongTermMemoryCompatibleContentAndTags() {
        val propertyNames = CustomColumn::class.java.declaredFields
            .filterNot { it.isSynthetic || it.name.startsWith("$") }
            .map { it.name }
            .toSet()

        assertEquals(setOf("id", "name", "content", "tags"), propertyNames)
        assertTrue(propertyNames.none { it.contains("notification", ignoreCase = true) || it.contains("automation", ignoreCase = true) })
    }

    private fun ids(): () -> String {
        var next = 0
        return { "id-${next++}" }
    }
}

private class RecordingPersistence : TagLibraryPersistence {
    private var snapshot: TagLibrarySnapshot? = null

    override fun loadTagLibrary(): TagLibrarySnapshot? = snapshot
    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) {
        this.snapshot = snapshot.deepCopy()
    }
}
