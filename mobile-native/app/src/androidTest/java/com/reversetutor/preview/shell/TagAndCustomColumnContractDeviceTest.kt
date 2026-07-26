package com.reversetutor.preview.shell

import android.content.Context
import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.reversetutor.feature.chat.CustomColumn
import com.reversetutor.feature.chat.CustomColumnEditorScreen
import com.reversetutor.feature.chat.QuickTag
import com.reversetutor.feature.chat.TagFieldSelection
import com.reversetutor.feature.chat.TagGroup
import com.reversetutor.feature.chat.TagLibraryEditor
import com.reversetutor.feature.chat.TagLibraryPersistence
import com.reversetutor.feature.chat.TagLibraryPicker
import com.reversetutor.feature.chat.TagLibrarySnapshot
import com.reversetutor.feature.chat.TagOrigin
import com.reversetutor.preview.wiring.SharedPreferencesTagLibraryPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TagAndCustomColumnContractDeviceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressDragActuallyReordersColumnsAndMovesTagsAcrossGroups() {
        val tagEditor = customEditor()
        val firstGroup = tagEditor.createGroup("甲组")
        val secondGroup = tagEditor.createGroup("乙组")
        val first = tagEditor.addTag("一", firstGroup)
        val second = tagEditor.addTag("二", firstGroup)
        val third = tagEditor.addTag("三", firstGroup)
        val target = tagEditor.addTag("目标", secondGroup)
        val columns = mutableStateOf(
            listOf(CustomColumn("first", "第一", "一"), CustomColumn("second", "第二", "二"))
        )
        val tagState = mutableStateOf(tagEditor.state)
        composeRule.setContent {
            MaterialTheme {
                Column {
                    CustomColumnEditorScreen(
                        columns = columns.value,
                        tagLibraryState = tagState.value,
                        tagLibraryEditor = tagEditor,
                        onTagLibraryStateChange = { tagState.value = it },
                        onColumnsChange = { columns.value = it }
                    )
                }
            }
        }

        drag("custom-column-card-second", "custom-column-card-first")
        composeRule.runOnIdle { assertEquals(listOf("second", "first"), columns.value.map(CustomColumn::id)) }

        composeRule.onNodeWithTag("custom-column-add").performClick()
        drag("quick-tag-$first", "quick-tag-$third")
        composeRule.runOnIdle {
            assertEquals(listOf(second, first, third), tagEditor.state.library.group(firstGroup)?.tagIds)
        }
        drag("quick-tag-$first", "quick-tag-$target")
        composeRule.runOnIdle {
            assertEquals(listOf(first, target), tagEditor.state.library.group(secondGroup)?.tagIds)
        }
    }

    @Test
    fun rangeHeadersAcceptDropsIntoEmptyGroupEmptyUngroupedAndUngroupedReorderSlot() {
        val editor = customEditor()
        val sourceGroup = editor.createGroup("来源组")
        val emptyGroup = editor.createGroup("空组")
        val first = editor.addTag("甲", sourceGroup)
        val second = editor.addTag("乙", sourceGroup)
        val state = mutableStateOf(editor.state)
        setTagPicker(editor, state)

        drag("quick-tag-$first", "tag-group-drop-$emptyGroup")
        composeRule.runOnIdle {
            assertEquals(listOf(first), editor.state.library.group(emptyGroup)?.tagIds)
        }
        drag("quick-tag-$first", "tag-range-drop-ungrouped")
        composeRule.runOnIdle { assertEquals(listOf(first), editor.state.library.ungroupedTagIds) }
        drag("quick-tag-$second", "tag-group-drop-ungrouped")
        composeRule.runOnIdle { assertEquals(listOf(first, second), editor.state.library.ungroupedTagIds) }
        drag("quick-tag-$first", "tag-group-drop-ungrouped")
        composeRule.runOnIdle { assertEquals(listOf(second, first), editor.state.library.ungroupedTagIds) }
    }

    @Test
    fun removedColumnBoundsCannotCaptureDropFromVisibleReplacement() {
        val editor = customEditor()
        val tagState = mutableStateOf(editor.state)
        val columns = mutableStateOf(
            listOf(
                CustomColumn("removed", "待删除", "一"),
                CustomColumn("target", "目标", "二"),
                CustomColumn("dragged", "拖动", "三")
            )
        )
        composeRule.setContent {
            MaterialTheme {
                CustomColumnEditorScreen(
                    columns = columns.value,
                    tagLibraryState = tagState.value,
                    tagLibraryEditor = editor,
                    onTagLibraryStateChange = { tagState.value = it },
                    onColumnsChange = { columns.value = it }
                )
            }
        }
        composeRule.runOnIdle { columns.value = columns.value.filterNot { it.id == "removed" } }
        composeRule.waitForIdle()

        drag("custom-column-card-dragged", "custom-column-card-target")

        composeRule.runOnIdle { assertEquals(listOf("dragged", "target"), columns.value.map(CustomColumn::id)) }
    }

    @Test
    fun draggingTwoUngroupedTagsOpensCreateGroupWithBothMembers() {
        val editor = customEditor()
        val first = editor.addTag("未分组甲", null)
        val second = editor.addTag("未分组乙", null)
        val state = mutableStateOf(editor.state)
        composeRule.setContent {
            MaterialTheme {
                CustomColumnEditorScreen(
                    columns = emptyList(),
                    tagLibraryState = state.value,
                    tagLibraryEditor = editor,
                    onTagLibraryStateChange = { state.value = it },
                    onColumnsChange = {}
                )
            }
        }
        composeRule.onNodeWithTag("custom-column-add").performClick()

        drag("quick-tag-$first", "quick-tag-$second")

        composeRule.onNodeWithText("为两个标签创建组").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(listOf(first, second), editor.state.pendingGroupCreation?.initialTagIds)
        }
    }

    @Test
    fun collapsedHorizontalViewportCanScrollToTagsAfterTheFirstEight() {
        val editor = customEditor()
        val group = editor.createGroup("长列表")
        val ids = (0 until 12).map { editor.addTag("标签$it", group) }
        val state = mutableStateOf(editor.state)
        setTagPicker(editor, state)

        composeRule.onNodeWithTag("quick-tag-${ids.last()}").assertIsNotDisplayed()
        repeat(2) {
            composeRule.onNodeWithTag("tag-library-collapsed-$group").performTouchInput { swipeLeft() }
        }
        composeRule.onNodeWithTag("quick-tag-${ids.last()}").assertIsDisplayed()
    }

    @Test
    fun dialogBackCollapsesExpandedGroupsBeforeDismissingEditor() {
        val editor = customEditor()
        val group = editor.createGroup("可展开")
        editor.addTag("标签", group)
        val tagState = mutableStateOf(editor.state)
        composeRule.setContent {
            MaterialTheme {
                CustomColumnEditorScreen(
                    columns = emptyList(),
                    tagLibraryState = tagState.value,
                    tagLibraryEditor = editor,
                    onTagLibraryStateChange = { tagState.value = it },
                    onColumnsChange = {}
                )
            }
        }
        composeRule.onNodeWithTag("custom-column-add").performClick()
        composeRule.onNodeWithTag("tag-group-toggle-$group").performClick()
        composeRule.runOnIdle { assertTrue(editor.state.expandedGroupIds.isNotEmpty()) }

        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)

        composeRule.onNodeWithText("新增栏目").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(editor.state.expandedGroupIds.isEmpty()) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        composeRule.onNodeWithText("新增栏目").assertDoesNotExist()
    }

    @Test
    fun persistenceErrorIsVisibleRetryableAndPaletteTargetsAreAtLeast44Dp() {
        val persistence = DeviceTagLibraryPersistence(initial = emptyLibrary(), failSave = true)
        var next = 0
        val editor = TagLibraryEditor(persistence) { "id-${next++}" }.also { it.load() }
        val state = mutableStateOf(editor.state)
        composeRule.setContent {
            MaterialTheme {
                CustomColumnEditorScreen(
                    columns = emptyList(),
                    tagLibraryState = state.value,
                    tagLibraryEditor = editor,
                    onTagLibraryStateChange = { state.value = it },
                    onColumnsChange = {}
                )
            }
        }
        composeRule.onNodeWithTag("tag-library-manage").performClick()
        composeRule.onNodeWithTag("tag-library-new-tag-name").performTextInput("保留输入")
        composeRule.onNodeWithText("新增标签").performClick()
        composeRule.onNodeWithTag("tag-library-persistence-error").assertIsDisplayed()
        composeRule.onNodeWithTag("tag-library-new-tag-name").assertTextEquals("保留输入")
        persistence.failSave = false
        composeRule.onNodeWithTag("tag-library-save-retry").performClick()
        composeRule.onNodeWithTag("tag-library-persistence-error").assertDoesNotExist()
        composeRule.onNodeWithTag("tag-library-new-tag-name").assertTextEquals("保留输入")
        composeRule.runOnIdle { assertNotNull(persistence.snapshot?.tags?.values?.firstOrNull { it.name == "保留输入" }) }

        val density = composeRule.density
        val bounds = composeRule.onNodeWithTag("tag-color-0").fetchSemanticsNode().boundsInRoot
        assertTrue(with(density) { bounds.width.toDp().value >= 44f })
        assertTrue(with(density) { bounds.height.toDp().value >= 44f })
    }

    @Test
    fun loadFailureShowsDistinctRetryAndSuccessfulRetryRestoresLibrary() {
        val restored = TagLibrarySnapshot(
            groups = emptyList(),
            tags = linkedMapOf("restored" to QuickTag("restored", "已恢复", TagOrigin.Custom)),
            ungroupedTagIds = listOf("restored")
        )
        val persistence = DeviceTagLibraryPersistence(initial = restored, failLoad = true)
        val editor = TagLibraryEditor(persistence).also { it.load() }
        val state = mutableStateOf(editor.state)
        composeRule.setContent {
            MaterialTheme {
                CustomColumnEditorScreen(
                    columns = emptyList(),
                    tagLibraryState = state.value,
                    tagLibraryEditor = editor,
                    onTagLibraryStateChange = { state.value = it },
                    onColumnsChange = {}
                )
            }
        }

        composeRule.onNodeWithTag("tag-library-persistence-error").assertIsDisplayed()
        composeRule.onNodeWithText("重试读取").assertIsDisplayed()
        persistence.failLoad = false
        composeRule.onNodeWithTag("tag-library-load-retry").performClick()

        composeRule.onNodeWithTag("tag-library-persistence-error").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(restored, editor.state.library) }
    }

    @Test
    fun appOwnedSharedPreferencesRestoresCustomGroupsTagsAndOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("tag_library_feature_state", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        var next = 0
        val persistence = SharedPreferencesTagLibraryPersistence(context)
        val editor = TagLibraryEditor(persistence) { "persisted-${next++}" }.also { it.load() }
        val group = editor.createGroup("持久组")
        val second = editor.addTag("第二", group)
        val first = editor.addTag("第一", group)
        editor.reorderTag(first, 0)

        val restored = TagLibraryEditor(persistence).also { it.load() }.state.library

        assertEquals(listOf(first, second), restored.group(group)?.tagIds)
        assertEquals("第一", restored.tags[first]?.name)
        preferences.edit().clear().commit()
    }

    private fun setTagPicker(
        editor: TagLibraryEditor,
        state: androidx.compose.runtime.MutableState<com.reversetutor.feature.chat.TagLibraryEditorState>
    ) {
        composeRule.setContent {
            MaterialTheme {
                TagLibraryPicker(
                    state = state.value,
                    selection = TagFieldSelection(),
                    editor = editor,
                    onStateChange = { state.value = it },
                    onToggle = {},
                    onExpansionChange = { groupId, expanded ->
                        editor.setGroupExpanded(groupId, expanded)
                        state.value = editor.state
                    }
                )
            }
        }
    }

    private fun customEditor(): TagLibraryEditor {
        var next = 0
        return TagLibraryEditor(DeviceTagLibraryPersistence(emptyLibrary())) { "id-${next++}" }.also { it.load() }
    }

    private fun drag(sourceTag: String, targetTag: String) {
        val sourceBounds = composeRule.onNodeWithTag(sourceTag).fetchSemanticsNode().boundsInRoot
        val targetCenterInRoot = composeRule.onNodeWithTag(targetTag).fetchSemanticsNode().boundsInRoot.center
        val targetInSource = targetCenterInRoot - sourceBounds.topLeft
        composeRule.onNodeWithTag(sourceTag).performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(Offset((center.x + targetInSource.x) / 2f, (center.y + targetInSource.y) / 2f), 100)
            moveTo(targetInSource, 100)
            up()
        }
        composeRule.waitForIdle()
    }
}

private fun emptyLibrary() = TagLibrarySnapshot(
    groups = emptyList<TagGroup>(),
    tags = emptyMap(),
    ungroupedTagIds = emptyList()
)

private class DeviceTagLibraryPersistence(
    initial: TagLibrarySnapshot? = null,
    var failLoad: Boolean = false,
    var failSave: Boolean = false
) : TagLibraryPersistence {
    var snapshot: TagLibrarySnapshot? = initial?.deepCopy()

    override fun loadTagLibrary(): TagLibrarySnapshot? {
        if (failLoad) error("unavailable")
        return snapshot?.deepCopy()
    }

    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) {
        if (failSave) error("unavailable")
        this.snapshot = snapshot.deepCopy()
    }
}
