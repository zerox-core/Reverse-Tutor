package com.reversetutor.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomColumnEditorScreen(
    columns: List<CustomColumn>,
    tagLibraryState: TagLibraryEditorState,
    tagLibraryEditor: TagLibraryEditor,
    onTagLibraryStateChange: (TagLibraryEditorState) -> Unit,
    onColumnsChange: (List<CustomColumn>) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf<CustomColumn?>(null) }
    var adding by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var managingTags by remember { mutableStateOf(false) }
    val columnBounds = remember { DragBoundsRegistry<String>() }
    val columnDrag = remember { mutableStateOf<DragSession<String>?>(null) }
    val validColumnTargets = columns.map(CustomColumn::id)
    SideEffect { columnBounds.retainOnly(validColumnTargets.toSet()) }

    BackHandler(enabled = tagLibraryState.expandedGroupIds.isNotEmpty()) {
        tagLibraryEditor.handleBack()
        onTagLibraryStateChange(tagLibraryEditor.state)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("自定义栏目", fontWeight = FontWeight.Bold)
                Text("栏目内容与所选标签随当前配置快照保存。")
            }
            IconButton(onClick = { managingTags = true }, modifier = Modifier.testTag("tag-library-manage")) {
                Icon(Icons.Filled.Settings, contentDescription = "管理快捷标签")
            }
        }
        TagLibraryStatus(tagLibraryState, tagLibraryEditor, onTagLibraryStateChange)
        columns.forEach { column ->
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .testTag("custom-column-card-${column.id}")
                    .longPressDragSource(
                        id = column.id,
                        enabled = true,
                        bounds = columnBounds,
                        drag = columnDrag,
                        validTargets = validColumnTargets,
                        onDrop = { draggedId, targetId ->
                            val editor = CustomColumnEditor(columns)
                            if (editor.dropColumnOnto(draggedId, targetId)) {
                                onColumnsChange(editor.state.columns)
                            }
                        }
                    )
                    .clickable { editing = column }
                    .graphicsLayer { alpha = if (columnDrag.value?.id == column.id) .68f else 1f },
                shape = RoundedCornerShape(14.dp),
                tonalElevation = 1.dp
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(column.name, fontWeight = FontWeight.Bold)
                            Text(column.content.ifBlank { "未填写" })
                        }
                        IconButton(onClick = { editing = column }) { Icon(Icons.Filled.Edit, "编辑") }
                        IconButton(onClick = {
                            val editor = CustomColumnEditor(columns)
                            editor.copyColumn(column.id)
                            onColumnsChange(editor.state.columns)
                        }) { Icon(Icons.Filled.ContentCopy, "复制") }
                        IconButton(onClick = { pendingDeleteId = column.id }) { Icon(Icons.Filled.Delete, "删除") }
                    }
                    if (column.tags.values.isNotEmpty()) {
                        Text(column.tags.orderedValues(tagLibraryState.library).joinToString(" · "))
                    }
                }
            }
        }
        Button(
            onClick = { adding = true },
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("custom-column-add")
        ) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("新增自定义栏目")
        }
    }

    if (adding || editing != null) {
        CustomColumnDialog(
            initial = editing,
            columns = columns,
            libraryState = tagLibraryState,
            libraryEditor = tagLibraryEditor,
            onLibraryStateChange = onTagLibraryStateChange,
            onDismiss = { adding = false; editing = null },
            onSave = { name, content, tags ->
                val editor = CustomColumnEditor(columns)
                val saved = editing?.let { editor.editColumn(it.id, name, content, tags) }
                    ?: (editor.addColumn(name, content, tags) != null)
                if (saved) {
                    onColumnsChange(editor.state.columns)
                    adding = false
                    editing = null
                }
            }
        )
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除栏目？") },
            text = { Text("只删除当前配置快照中的栏目，不影响历史会话或收藏。") },
            confirmButton = { TextButton(onClick = {
                val editor = CustomColumnEditor(columns)
                editor.requestDelete(id)
                editor.confirmDelete()
                onColumnsChange(editor.state.columns)
                pendingDeleteId = null
            }) { Text("确认删除") } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("取消") } }
        )
    }

    if (managingTags) {
        TagLibraryManagementDialog(
            state = tagLibraryState,
            editor = tagLibraryEditor,
            onStateChange = onTagLibraryStateChange,
            onDismiss = { managingTags = false }
        )
    }
    tagLibraryState.pendingGroupCreation?.let {
        PendingTagGroupDialog(
            state = tagLibraryState,
            editor = tagLibraryEditor,
            onStateChange = onTagLibraryStateChange
        )
    }
}

@Composable
private fun CustomColumnDialog(
    initial: CustomColumn?,
    columns: List<CustomColumn>,
    libraryState: TagLibraryEditorState,
    libraryEditor: TagLibraryEditor,
    onLibraryStateChange: (TagLibraryEditorState) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, TagFieldSelection) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var content by remember(initial?.id) { mutableStateOf(initial?.content.orEmpty()) }
    var selection by remember(initial?.id) { mutableStateOf(initial?.tags?.deepCopy() ?: TagFieldSelection()) }
    val duplicate = columns.any { it.id != initial?.id && it.name == name.trim() }
    fun dismissOrCollapse() {
        if (libraryEditor.handleBack()) onLibraryStateChange(libraryEditor.state) else onDismiss()
    }
    BackHandler(enabled = libraryState.expandedGroupIds.isNotEmpty()) {
        libraryEditor.handleBack()
        onLibraryStateChange(libraryEditor.state)
    }
    AlertDialog(
        onDismissRequest = ::dismissOrCollapse,
        title = { Text(if (initial == null) "新增栏目" else "编辑栏目") },
        text = {
            Column(
                Modifier.height(500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("栏目名称") },
                    isError = duplicate,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("custom-column-name")
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("文本内容") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth().testTag("custom-column-content")
                )
                Text("分类快捷标签", fontWeight = FontWeight.Bold)
                TagLibraryStatus(libraryState, libraryEditor, onLibraryStateChange)
                TagLibraryPicker(
                    state = libraryState,
                    selection = selection,
                    editor = libraryEditor,
                    onStateChange = onLibraryStateChange,
                    onToggle = { tag -> selection = selection.toggle(tag, libraryState.library) },
                    onExpansionChange = { groupId, expanded ->
                        libraryEditor.setGroupExpanded(groupId, expanded)
                        onLibraryStateChange(libraryEditor.state)
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !duplicate,
                onClick = { onSave(name, content, selection) }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = ::dismissOrCollapse) { Text("取消") } }
    )
}

@Composable
fun TagLibraryPicker(
    state: TagLibraryEditorState,
    selection: TagFieldSelection,
    editor: TagLibraryEditor,
    onStateChange: (TagLibraryEditorState) -> Unit,
    onToggle: (QuickTag) -> Unit,
    onExpansionChange: (String, Boolean) -> Unit
) {
    val tagBounds = remember { DragBoundsRegistry<TagDropTarget>() }
    val tagDrag = remember { mutableStateOf<DragSession<TagDropTarget>?>(null) }
    val validTagTargets = state.library.groups
        .flatMap(TagGroup::tagIds)
        .plus(state.library.ungroupedTagIds)
        .distinct()
        .map(TagDropTarget::Tag)
    val validRangeTargets = state.library.groups
        .filter { it.origin == TagOrigin.Custom }
        .flatMap { group ->
            listOf(
                TagDropTarget.Range(group.id, RangeDropSlot.Header),
                TagDropTarget.Range(group.id, RangeDropSlot.Body)
            )
        }
        .plus(TagDropTarget.Range(null, RangeDropSlot.Header))
        .plus(TagDropTarget.Range(null, RangeDropSlot.Body))
    val validDropTargets = validTagTargets + validRangeTargets
    SideEffect { tagBounds.retainOnly(validDropTargets.toSet()) }
    fun drop(dragged: TagDropTarget, target: TagDropTarget) {
        val draggedId = (dragged as? TagDropTarget.Tag)?.tagId ?: return
        when (target) {
            is TagDropTarget.Tag -> editor.dropTagOnto(draggedId, target.tagId)
            is TagDropTarget.Range -> editor.dropTagIntoRange(draggedId, target.groupId)
        }
        onStateChange(editor.state)
    }
    state.library.groups.forEach { group ->
        val expanded = group.id in state.expandedGroupIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .dropTarget(
                    id = TagDropTarget.Range(group.id, RangeDropSlot.Header),
                    enabled = group.origin == TagOrigin.Custom,
                    bounds = tagBounds
                )
                .testTag("tag-group-drop-${group.id}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(group.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            IconButton(
                onClick = { onExpansionChange(group.id, !expanded) },
                modifier = Modifier.testTag("tag-group-toggle-${group.id}")
            ) {
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "展开或收起")
            }
        }
        val tags = group.tagIds.mapNotNull(state.library.tags::get)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dropTarget(
                    id = TagDropTarget.Range(group.id, RangeDropSlot.Body),
                    enabled = group.origin == TagOrigin.Custom,
                    bounds = tagBounds
                )
                .testTag("tag-range-drop-${group.id}")
        ) {
            if (expanded) {
                tags.chunked(4).forEach { rowTags ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTags.forEach { tag ->
                            TagChoice(
                                tag,
                                selection,
                                group.colorIndex,
                                tagBounds,
                                tagDrag,
                                validDropTargets,
                                ::drop,
                                onToggle
                            )
                        }
                    }
                }
            } else {
                Row(
                    Modifier
                        .widthIn(max = (CollapsedTagVisibleCapacity * 44 + (CollapsedTagVisibleCapacity - 1) * 6).dp)
                        .fillMaxWidth()
                        .height(52.dp)
                        .horizontalScroll(rememberScrollState())
                        .testTag("tag-library-collapsed-${group.id}"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tags.forEach { tag ->
                        TagChoice(
                            tag,
                            selection,
                            group.colorIndex,
                            tagBounds,
                            tagDrag,
                            validDropTargets,
                            ::drop,
                            onToggle
                        )
                    }
                }
            }
        }
    }
    val expanded = UngroupedTagRangeId in state.expandedGroupIds
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .dropTarget(TagDropTarget.Range(null, RangeDropSlot.Header), enabled = true, bounds = tagBounds)
            .testTag("tag-group-drop-ungrouped"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("未分组", modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        IconButton(
            onClick = { onExpansionChange(UngroupedTagRangeId, !expanded) },
            modifier = Modifier.testTag("tag-group-toggle-ungrouped")
        ) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "展开或收起未分组标签")
        }
    }
    val tags = state.library.ungroupedTagIds.mapNotNull(state.library.tags::get)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .dropTarget(TagDropTarget.Range(null, RangeDropSlot.Body), enabled = true, bounds = tagBounds)
            .testTag("tag-range-drop-ungrouped")
    ) {
        if (expanded) {
            tags.chunked(4).forEach { rowTags ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowTags.forEach { tag ->
                        TagChoice(tag, selection, 11, tagBounds, tagDrag, validDropTargets, ::drop, onToggle)
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .widthIn(max = (CollapsedTagVisibleCapacity * 44 + (CollapsedTagVisibleCapacity - 1) * 6).dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .horizontalScroll(rememberScrollState())
                    .testTag("tag-library-collapsed-ungrouped"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tags.forEach { tag ->
                    TagChoice(tag, selection, 11, tagBounds, tagDrag, validDropTargets, ::drop, onToggle)
                }
            }
        }
    }
}

@Composable
private fun TagChoice(
    tag: QuickTag,
    selection: TagFieldSelection,
    colorIndex: Int,
    bounds: DragBoundsRegistry<TagDropTarget>,
    drag: MutableState<DragSession<TagDropTarget>?>,
    validTargets: List<TagDropTarget>,
    onDrop: (TagDropTarget, TagDropTarget) -> Unit,
    onToggle: (QuickTag) -> Unit
) {
    val selected = selection.values.any { it.tagId == tag.id }
    Surface(
        onClick = { onToggle(tag) },
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(TagColorPalette.swatches[colorIndex].argb) else Color(0xFFF3F3F3),
        modifier = Modifier
            .testTag("quick-tag-${tag.id}")
            .widthIn(min = 44.dp)
            .longPressDragSource(
                id = TagDropTarget.Tag(tag.id),
                enabled = tag.origin == TagOrigin.Custom,
                bounds = bounds,
                drag = drag,
                validTargets = validTargets,
                onDrop = onDrop
            )
            .graphicsLayer { alpha = if (drag.value?.id == TagDropTarget.Tag(tag.id)) .62f else 1f }
    ) {
        Text(tag.name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagLibraryManagementDialog(
    state: TagLibraryEditorState,
    editor: TagLibraryEditor,
    onStateChange: (TagLibraryEditorState) -> Unit,
    onDismiss: () -> Unit
) {
    var newGroupName by remember { mutableStateOf("") }
    var newTagName by remember { mutableStateOf("") }
    var targetGroupId by remember { mutableStateOf<String?>(null) }
    fun dismissOrCollapse() {
        if (editor.handleBack()) onStateChange(editor.state) else onDismiss()
    }
    BackHandler(enabled = state.expandedGroupIds.isNotEmpty()) {
        editor.handleBack()
        onStateChange(editor.state)
    }
    AlertDialog(
        onDismissRequest = ::dismissOrCollapse,
        title = { Text("管理快捷标签") },
        text = {
            Column(
                Modifier.height(560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TagLibraryStatus(state, editor, onStateChange)
                TagLibraryPicker(
                    state = state,
                    selection = TagFieldSelection(),
                    editor = editor,
                    onStateChange = onStateChange,
                    onToggle = {},
                    onExpansionChange = { groupId, expanded ->
                        editor.setGroupExpanded(groupId, expanded)
                        onStateChange(editor.state)
                    }
                )
                Text("颜色", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagColorPalette.swatches.forEachIndexed { index, swatch ->
                        Surface(
                            onClick = {
                                targetGroupId?.let { editor.changeGroupColor(it, index) }
                                onStateChange(editor.state)
                            },
                            modifier = Modifier.size(44.dp).testTag("tag-color-$index"),
                            shape = CircleShape,
                            color = Color(swatch.argb)
                        ) {}
                    }
                }
                OutlinedTextField(newGroupName, { newGroupName = it }, label = { Text("新组名称") }, singleLine = true)
                Button(enabled = newGroupName.isNotBlank(), onClick = {
                    targetGroupId = editor.createGroup(newGroupName)
                    if (editor.state.error == null) newGroupName = ""
                    onStateChange(editor.state)
                }) { Icon(Icons.Filled.Add, null); Text("创建组") }
                state.library.groups.forEachIndexed { index, group ->
                    if (group.origin == TagOrigin.Custom) {
                        var groupName by remember(group.id, group.name) { mutableStateOf(group.name) }
                        Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedTextField(
                                    groupName,
                                    { groupName = it },
                                    label = { Text("组名") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row {
                                    TextButton(onClick = { editor.renameGroup(group.id, groupName); onStateChange(editor.state) }) { Text("保存") }
                                    IconButton(onClick = { editor.reorderGroup(group.id, index - 1); onStateChange(editor.state) }) { Icon(Icons.Filled.ArrowUpward, "组上移") }
                                    IconButton(onClick = { editor.reorderGroup(group.id, index + 1); onStateChange(editor.state) }) { Icon(Icons.Filled.ArrowDownward, "组下移") }
                                    IconButton(onClick = { editor.deleteGroup(group.id); onStateChange(editor.state) }) { Icon(Icons.Filled.Delete, "删除组") }
                                    IconButton(onClick = { targetGroupId = group.id }) { Icon(Icons.Filled.Label, "选择组") }
                                }
                                group.tagIds.mapNotNull(state.library.tags::get).forEach { tag ->
                                    ManagedTagRow(tag, editor, onStateChange)
                                }
                            }
                        }
                    }
                }
                Text("新增标签到：${state.library.group(targetGroupId)?.name ?: "未分组"}")
                OutlinedTextField(
                    newTagName,
                    { newTagName = it },
                    label = { Text("标签名称") },
                    singleLine = true,
                    modifier = Modifier.testTag("tag-library-new-tag-name")
                )
                Button(enabled = newTagName.isNotBlank(), onClick = {
                    if (editor.tryAddTag(newTagName, targetGroupId) != null && editor.state.error == null) {
                        newTagName = ""
                    }
                    onStateChange(editor.state)
                }) { Icon(Icons.Filled.Add, null); Text("新增标签") }
                Text("未分组标签：在上方长按拖到另一未分组标签即可创建组。")
                state.library.ungroupedTagIds.mapNotNull(state.library.tags::get).forEach { tag ->
                    ManagedTagRow(tag, editor, onStateChange)
                }
            }
        },
        confirmButton = { TextButton(onClick = ::dismissOrCollapse) { Text("完成") } }
    )

}

@Composable
private fun PendingTagGroupDialog(
    state: TagLibraryEditorState,
    editor: TagLibraryEditor,
    onStateChange: (TagLibraryEditorState) -> Unit
) {
    var groupName by remember(state.pendingGroupCreation) { mutableStateOf("") }
    fun dismissOrCollapse() {
        if (editor.handleBack()) onStateChange(editor.state) else {
            editor.dismissPendingGroup()
            onStateChange(editor.state)
        }
    }
    BackHandler(enabled = state.expandedGroupIds.isNotEmpty()) {
        editor.handleBack()
        onStateChange(editor.state)
    }
    AlertDialog(
        onDismissRequest = ::dismissOrCollapse,
        title = { Text("为两个标签创建组") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    groupName,
                    { groupName = it },
                    label = { Text("组名") },
                    singleLine = true,
                    modifier = Modifier.testTag("pending-tag-group-name")
                )
                TagLibraryStatus(state, editor, onStateChange)
            }
        },
        confirmButton = {
            TextButton(enabled = groupName.isNotBlank(), onClick = {
                editor.confirmPendingGroup(groupName)
                onStateChange(editor.state)
            }) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = ::dismissOrCollapse) { Text("取消") } }
    )
}

@Composable
private fun ManagedTagRow(
    tag: QuickTag,
    editor: TagLibraryEditor,
    onStateChange: (TagLibraryEditorState) -> Unit
) {
    var name by remember(tag.id, tag.name) { mutableStateOf(tag.name) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.weight(1f))
        IconButton(onClick = { editor.renameTag(tag.id, name); onStateChange(editor.state) }) { Icon(Icons.Filled.Edit, "重命名标签") }
        IconButton(onClick = { editor.deleteTag(tag.id); onStateChange(editor.state) }) { Icon(Icons.Filled.Delete, "删除标签") }
    }
}

@Composable
private fun TagLibraryStatus(
    state: TagLibraryEditorState,
    editor: TagLibraryEditor,
    onStateChange: (TagLibraryEditorState) -> Unit
) {
    state.error?.let { error ->
        val retry = state.persistenceRetry
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("tag-library-persistence-error"),
            color = Color(0xFFFFE8E8),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, modifier = Modifier.weight(1f))
                retry?.let {
                    val isLoadRetry = it == TagLibraryPersistenceRetry.Load
                    TextButton(
                        onClick = {
                            if (isLoadRetry) editor.retryLoad() else editor.retrySave()
                            onStateChange(editor.state)
                        },
                        modifier = Modifier.testTag(
                            if (isLoadRetry) "tag-library-load-retry" else "tag-library-save-retry"
                        )
                    ) { Text(if (isLoadRetry) "重试读取" else "重试保存") }
                }
            }
        }
    }
    state.notice?.let { notice ->
        Text(notice, modifier = Modifier.testTag("tag-library-notice"), color = Color(0xFF496B56))
    }
}

private sealed interface TagDropTarget {
    data class Tag(val tagId: String) : TagDropTarget
    data class Range(val groupId: String?, val slot: RangeDropSlot) : TagDropTarget
}

private enum class RangeDropSlot { Header, Body }

private data class DragSession<K>(
    val id: K,
    val pointerInWindow: Offset
)

@Composable
private fun <K> Modifier.dropTarget(
    id: K,
    enabled: Boolean,
    bounds: DragBoundsRegistry<K>
): Modifier {
    DisposableEffect(bounds, id, enabled) {
        onDispose { bounds.remove(id) }
    }
    return if (!enabled) this else onGloballyPositioned { coordinates ->
        bounds.update(id, coordinates.boundsInWindow())
    }
}

@Composable
private fun <K> Modifier.longPressDragSource(
    id: K,
    enabled: Boolean,
    bounds: DragBoundsRegistry<K>,
    drag: MutableState<DragSession<K>?>,
    validTargets: List<K>,
    onDrop: (K, K) -> Unit
): Modifier {
    val latestTargets by rememberUpdatedState(validTargets)
    val latestOnDrop by rememberUpdatedState(onDrop)
    DisposableEffect(bounds, id) {
        onDispose { bounds.remove(id) }
    }
    return onGloballyPositioned { coordinates ->
        bounds.update(id, coordinates.boundsInWindow())
    }.then(if (!enabled) Modifier else Modifier.pointerInput(id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { localOffset ->
                val origin = bounds.boundsOf(id)?.topLeft ?: Offset.Zero
                drag.value = DragSession(id, origin + localOffset)
            },
            onDrag = { change, amount ->
                change.consume()
                val current = drag.value ?: return@detectDragGesturesAfterLongPress
                drag.value = current.copy(pointerInWindow = current.pointerInWindow + amount)
            },
            onDragEnd = {
                val completed = drag.value
                drag.value = null
                if (completed != null) {
                    bounds.hitTest(
                        pointer = completed.pointerInWindow,
                        validTargetsInPriorityOrder = latestTargets,
                        excluded = setOf(completed.id)
                    )?.let { targetId -> latestOnDrop(completed.id, targetId) }
                }
            },
            onDragCancel = { drag.value = null }
        )
    })
}
