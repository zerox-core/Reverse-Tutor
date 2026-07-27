package com.reversetutor.feature.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun Task2B1NewSessionRoute(
    createPort: NewSessionCreatePort,
    persistence: NewSessionPersistence,
    tagLibraryPersistence: TagLibraryPersistence = InMemoryTagLibraryPersistence(),
    onCreated: (SessionListItem) -> Unit,
    onBack: () -> Unit = {},
    initialPrefill: NewSessionPrefillRequest? = null,
    modifier: Modifier = Modifier,
    nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    val coordinator = remember(createPort, persistence) {
        NewSessionLifecycleCoordinator(
            persistence = persistence,
            createPort = createPort,
            nowEpochMillis = nowEpochMillis
        )
    }
    val tagLibraryEditor = remember(tagLibraryPersistence) { TagLibraryEditor(tagLibraryPersistence) }
    var tagLibraryState by remember { mutableStateOf(tagLibraryEditor.state) }
    var state by remember { mutableStateOf(coordinator.state) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val rootListState = rememberLazyListState()
    val editorListState = rememberLazyListState()
    var draftMenuExpanded by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf<NewSessionDraftRecord?>(null) }
    var renameValue by remember { mutableStateOf("") }

    fun sync() {
        state = coordinator.state
    }

    fun rootPosition() = EditorScrollPosition(
        index = rootListState.firstVisibleItemIndex,
        offset = rootListState.firstVisibleItemScrollOffset
    )

    fun editorPosition() = EditorScrollPosition(
        index = editorListState.firstVisibleItemIndex,
        offset = editorListState.firstVisibleItemScrollOffset
    )

    LaunchedEffect(coordinator, initialPrefill?.requestId) {
        coordinator.load()
        initialPrefill?.let(coordinator::startPrefilledDraft)
        sync()
    }
    LaunchedEffect(tagLibraryEditor) {
        tagLibraryEditor.load()
        tagLibraryState = tagLibraryEditor.state
    }

    LaunchedEffect(state.currentDraft?.id, state.selectedSection) {
        val key = state.selectedSection?.name ?: "root"
        val saved = state.currentDraft?.scrollPositions?.get(key) ?: EditorScrollPosition()
        if (state.selectedSection == null) {
            rootListState.scrollToItem(saved.index, saved.offset)
        } else {
            editorListState.scrollToItem(saved.index, saved.offset)
        }
    }

    DisposableEffect(lifecycleOwner, coordinator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                coordinator.saveBoundary(
                    position = if (coordinator.state.selectedSection == null) rootPosition() else editorPosition()
                )
                sync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            coordinator.saveBoundary(
                position = if (coordinator.state.selectedSection == null) rootPosition() else editorPosition()
            )
        }
    }

    fun handleBack() {
        when {
            state.selectedSection != null -> {
                coordinator.closeSection(editorPosition())
                sync()
            }
            state.selectedPresetId != null -> {
                coordinator.closePresetDetail()
                sync()
            }
            state.tab == NewSessionHubTab.Custom -> {
                coordinator.saveBoundary(position = rootPosition())
                coordinator.selectTab(NewSessionHubTab.BuiltIn)
                sync()
            }
            else -> {
                coordinator.saveBoundary(position = rootPosition())
                onBack()
            }
        }
    }

    BackHandler(onBack = ::handleBack)

    FormalPageFrame(modifier) {
        when {
            state.selectedPresetId != null -> {
                val preset = FormalLearningPresets.byId(checkNotNull(state.selectedPresetId))
                if (preset != null) {
                    Task2B1PresetDetailScreen(
                        preset = preset,
                        creating = false,
                        error = null,
                        favorite = state.favorites.any { it.originBuiltInPresetId == preset.id },
                        onBack = ::handleBack,
                        onToggleFavorite = {
                            coordinator.toggleBuiltInFavorite(preset)
                            sync()
                        },
                        onUsePreset = {
                            coordinator.useBuiltInPreset(preset)
                            sync()
                        }
                    )
                }
            }
            state.selectedSection != null && state.currentDraft != null -> {
                CustomSectionEditor(
                    section = checkNotNull(state.selectedSection),
                    configuration = checkNotNull(state.currentDraft).configuration,
                    listState = editorListState,
                    onConfigurationChange = { updated ->
                        coordinator.updateConfiguration { updated }
                        sync()
                    },
                    tagLibraryState = tagLibraryState,
                    tagLibraryEditor = tagLibraryEditor,
                    onTagLibraryStateChange = { tagLibraryState = it },
                    onBack = ::handleBack
                )
            }
            else -> {
                Column(Modifier.fillMaxSize()) {
                    HubHeader(onBack = ::handleBack)
                    HubTabs(
                        selected = state.tab,
                        onSelect = {
                            coordinator.selectTab(it)
                            sync()
                        }
                    )
                    when (state.tab) {
                        NewSessionHubTab.BuiltIn -> FormalPresetLibraryContent(
                            favorites = state.favorites.mapNotNull { it.originBuiltInPresetId }.toSet(),
                            onPreset = {
                                coordinator.openPresetDetail(it.id)
                                sync()
                            },
                            onToggleFavorite = {
                                coordinator.toggleBuiltInFavorite(it)
                                sync()
                            }
                        )
                        NewSessionHubTab.Favorites -> FavoritesContent(
                            favorites = state.favorites,
                            onUse = {
                                coordinator.useFavorite(it)
                                sync()
                            },
                            onRemove = {
                                coordinator.removeFavorite(it)
                                sync()
                            }
                        )
                        NewSessionHubTab.Custom -> CustomRootScreen(
                            state = state,
                            listState = rootListState,
                            draftMenuExpanded = draftMenuExpanded,
                            onDraftMenuExpandedChange = { draftMenuExpanded = it },
                            onNewDraft = {
                                coordinator.startBlankDraft()
                                sync()
                            },
                            onRestoreDraft = {
                                coordinator.restoreDraft(it)
                                draftMenuExpanded = false
                                sync()
                            },
                            onRenameDraft = { record ->
                                renameDraft = record
                                renameValue = record.configuration.title
                                draftMenuExpanded = false
                            },
                            onCopyDraft = {
                                coordinator.copyDraft(it)
                                sync()
                            },
                            onDeleteDraft = {
                                coordinator.deleteDraft(it)
                                sync()
                            },
                            onOpenSection = {
                                coordinator.openSection(it, rootPosition())
                                sync()
                            },
                            onRandom = {
                                coordinator.previewRandom()
                                sync()
                            },
                            onUndo = {
                                coordinator.undoRandom()
                                sync()
                            },
                            onFavorite = {
                                coordinator.requestFavoriteCurrent()
                                sync()
                            },
                            onCreate = {
                                scope.launch {
                                    val outcome = coordinator.createSession()
                                    sync()
                                    if (outcome is CreateSessionOutcome.Success) onCreated(outcome.created.session)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    state.randomPreview?.let { preview ->
        DifferenceDialog(
            title = "随机结果预览",
            differences = preview.differences,
            confirmLabel = "应用",
            onConfirm = {
                coordinator.applyRandom()
                sync()
            },
            onDismiss = {
                coordinator.dismissRandom()
                sync()
            }
        )
    }
    state.favoriteUpdatePreview?.let { preview ->
        DifferenceDialog(
            title = "更新收藏前确认",
            differences = preview.differences,
            confirmLabel = "确认更新",
            onConfirm = {
                coordinator.confirmFavoriteUpdate()
                sync()
            },
            onDismiss = {
                coordinator.dismissFavoriteUpdate()
                sync()
            }
        )
    }
    renameDraft?.let { record ->
        AlertDialog(
            onDismissRequest = { renameDraft = null },
            title = { Text("重命名草稿") },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    label = { Text("草稿名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (coordinator.renameDraft(record.id, renameValue)) renameDraft = null
                    sync()
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renameDraft = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun HubHeader(onBack: () -> Unit) {
    FormalTopBar(title = "新建会话", onBack = onBack)
}

@Composable
private fun HubTabs(selected: NewSessionHubTab, onSelect: (NewSessionHubTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NewSessionHubTab.entries.forEach { tab ->
            Surface(
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f).height(44.dp).testTag("new-session-tab-${tab.name}"),
                color = if (selected == tab) FormalColors.Primary else FormalColors.Surface,
                shape = RoundedCornerShape(FormalShapes.CompactRadius),
                border = if (selected == tab) null else BorderStroke(1.dp, FormalColors.Border)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        tab.label,
                        style = LocalFormalTypeScale.current.style(
                            11f, 16f, FontWeight.Medium,
                            if (selected == tab) Color.White else FormalColors.Ink
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun Task2B1PresetLibraryScreen(
    onBack: () -> Unit,
    onCustom: () -> Unit,
    onPreset: (FormalLearningPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    FormalPageFrame(modifier.testTag("formal-preset-library-716-611")) {
        Column(Modifier.fillMaxSize()) {
            HubHeader(onBack)
            HubTabs(NewSessionHubTab.BuiltIn) { if (it == NewSessionHubTab.Custom) onCustom() }
            FormalPresetLibraryContent(emptySet(), onPreset, {})
        }
    }
}

@Composable
private fun FormalPresetLibraryContent(
    favorites: Set<String>,
    onPreset: (FormalLearningPreset) -> Unit,
    onToggleFavorite: (FormalLearningPreset) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("new-session-built-in-list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(FormalLearningPresets.all, key = { it.id }) { preset ->
            PresetSnapshotCard(
                title = preset.title,
                role = "AI 学生 ${preset.learnerName}",
                goal = preset.goal,
                plan = preset.schedule,
                sourceCount = preset.connectedSources,
                favorite = preset.id in favorites,
                onClick = { onPreset(preset) },
                onToggleFavorite = { onToggleFavorite(preset) }
            )
        }
    }
}

@Composable
private fun PresetSnapshotCard(
    title: String,
    role: String,
    goal: String,
    plan: String,
    sourceCount: Int,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = type.style(13f, 18f, FontWeight.Bold, FormalColors.Ink))
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (favorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏",
                        tint = if (favorite) FormalColors.Primary else FormalColors.Muted
                    )
                }
            }
            Text("学习者 · $role", style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Primary))
            Text("目标 · $goal", style = type.style(10f, 15f, color = FormalColors.Ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("计划 · $plan", style = type.style(9f, 14f, color = FormalColors.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("资料 · $sourceCount 项", style = type.style(9f, 14f, color = FormalColors.Muted))
        }
    }
}

@Composable
fun Task2B1PresetDetailScreen(
    preset: FormalLearningPreset,
    creating: Boolean,
    error: String?,
    onBack: () -> Unit,
    onUsePreset: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    val type = LocalFormalTypeScale.current
    Column(modifier.fillMaxSize().testTag("formal-preset-detail-${preset.figmaNodeId.replace(':', '-')}")) {
        FormalTopBar(
            title = "预设详情",
            onBack = onBack,
            trailing = {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (favorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏"
                    )
                }
            }
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PresetSnapshotCard(
                    title = preset.title,
                    role = "AI 学生 ${preset.learnerName}：${preset.learnerProfile}",
                    goal = preset.goal,
                    plan = preset.schedule,
                    sourceCount = preset.connectedSources,
                    favorite = favorite,
                    onClick = {},
                    onToggleFavorite = onToggleFavorite
                )
            }
            item { DetailBlock("学习范围", preset.scopeSummary) }
            item { DetailBlock("世界树", "${preset.episodeTitle}：${preset.episodeBody}") }
            item { DetailBlock("资料摘要", "${preset.sourceTitle} · ${preset.sourceSummary}") }
            error?.let { item { Text(it, style = type.style(10f, 15f, FontWeight.Medium, FormalColors.Danger)) } }
        }
        Button(
            onClick = onUsePreset,
            enabled = !creating,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
            shape = RoundedCornerShape(FormalShapes.CardRadius)
        ) { Text(if (creating) "创建中…" else "使用此预设") }
    }
}

@Composable
private fun DetailBlock(title: String, body: String) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = type.style(11f, 16f, FontWeight.Bold, FormalColors.Ink))
            Spacer(Modifier.height(5.dp))
            Text(body, style = type.style(10f, 16f, color = FormalColors.Muted))
        }
    }
}

@Composable
private fun FavoritesContent(
    favorites: List<NewSessionFavorite>,
    onUse: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    val type = LocalFormalTypeScale.current
    if (favorites.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有收藏的完整配置", style = type.style(11f, 17f, color = FormalColors.Muted))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("new-session-favorites-list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(favorites, key = { it.id }) { favorite ->
            PresetSnapshotCard(
                title = favorite.name,
                role = favorite.configuration.learnerRole,
                goal = favorite.configuration.goal.ifBlank { "未填写" },
                plan = favorite.configuration.plan.ifBlank { "未填写" },
                sourceCount = favorite.configuration.sourceSelections.size,
                favorite = true,
                onClick = { onUse(favorite.id) },
                onToggleFavorite = { onRemove(favorite.id) }
            )
        }
    }
}

@Composable
private fun CustomRootScreen(
    state: NewSessionLifecycleState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    draftMenuExpanded: Boolean,
    onDraftMenuExpandedChange: (Boolean) -> Unit,
    onNewDraft: () -> Unit,
    onRestoreDraft: (String) -> Unit,
    onRenameDraft: (NewSessionDraftRecord) -> Unit,
    onCopyDraft: (String) -> Unit,
    onDeleteDraft: (String) -> Unit,
    onOpenSection: (NewSessionSection) -> Unit,
    onRandom: () -> Unit,
    onUndo: () -> Unit,
    onFavorite: () -> Unit,
    onCreate: () -> Unit
) {
    val draft = state.currentDraft ?: return
    val config = draft.configuration
    val type = LocalFormalTypeScale.current
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("new-session-custom-root"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(config.title.ifBlank { "未命名配置" }, style = type.style(18f, 25f, FontWeight.Bold, FormalColors.Ink))
                        Text("完成度 ${config.completionPercent}%", style = type.style(10f, 15f, color = FormalColors.Muted))
                    }
                    Box {
                        IconButton(onClick = { onDraftMenuExpandedChange(true) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Filled.Inventory2, contentDescription = "草稿箱")
                        }
                        DropdownMenu(
                            expanded = draftMenuExpanded,
                            onDismissRequest = { onDraftMenuExpandedChange(false) }
                        ) {
                            DropdownMenuItem(
                                text = { Text("新建空白草稿") },
                                leadingIcon = { Icon(Icons.Filled.Add, null) },
                                onClick = onNewDraft
                            )
                            state.drafts.sortedByDescending { it.updatedAtEpochMillis }.forEach { record ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(record.configuration.title.ifBlank { "未命名草稿" })
                                            Text(
                                                "恢复 · ${record.configuration.completionPercent}% · ${formatDraftTime(record.updatedAtEpochMillis)}" +
                                                    if (record.favoriteId != null) " · 已收藏" else "",
                                                style = type.style(8f, 12f, color = FormalColors.Muted)
                                            )
                                        }
                                    },
                                    onClick = { onRestoreDraft(record.id) },
                                    trailingIcon = {
                                        Row {
                                            IconButton(onClick = { onRenameDraft(record) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Filled.DriveFileRenameOutline, "重命名")
                                            }
                                            IconButton(onClick = { onCopyDraft(record.id) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Filled.ContentCopy, "复制")
                                            }
                                            IconButton(onClick = { onDeleteDraft(record.id) }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Filled.Delete, "删除")
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                LinearProgressIndicator(
                    progress = config.completionPercent / 100f,
                    modifier = Modifier.fillMaxWidth(),
                    color = FormalColors.Primary,
                    trackColor = FormalColors.Border
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("随机", Icons.Filled.AutoAwesome, onRandom, Modifier.weight(1f))
                    SmallAction("撤销", Icons.Filled.Undo, onUndo, Modifier.weight(1f), enabled = state.canUndoRandom)
                    SmallAction(
                        if (draft.favoriteId == null) "收藏" else "更新收藏",
                        if (draft.favoriteId == null) Icons.Filled.BookmarkBorder else Icons.Filled.Bookmark,
                        onFavorite,
                        Modifier.weight(1f)
                    )
                }
            }
            items(NewSessionSection.entries, key = { it.name }) { section ->
                Surface(
                    onClick = { onOpenSection(section) },
                    modifier = Modifier.fillMaxWidth().height(72.dp),
                    color = FormalColors.Surface,
                    shape = RoundedCornerShape(FormalShapes.CardRadius),
                    border = BorderStroke(1.dp, FormalColors.Border)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(section.label, style = type.style(12f, 17f, FontWeight.Bold, FormalColors.Ink))
                            Text(config.sectionSummary(section), style = type.style(9f, 14f, color = FormalColors.Muted), maxLines = 1)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = "编辑${section.label}", tint = FormalColors.Muted)
                    }
                }
            }
            state.persistenceError?.let { item { Text(it, style = type.style(10f, 15f, color = FormalColors.Danger)) } }
            state.createError?.let { item { Text(it, style = type.style(10f, 15f, color = FormalColors.Danger)) } }
        }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = FormalColors.Background.copy(alpha = .97f),
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("只需会话名称与学习者角色即可创建；其余内容可保持未完成。", style = type.style(9f, 14f, color = FormalColors.Muted))
                Spacer(Modifier.height(7.dp))
                Button(
                    onClick = onCreate,
                    enabled = !state.creating && config.validationErrors().isEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
                    shape = RoundedCornerShape(FormalShapes.CardRadius)
                ) {
                    Icon(if (state.createError == null) Icons.Filled.Add else Icons.Filled.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text(when {
                        state.creating -> "创建中…"
                        state.createError != null -> "重试"
                        else -> "创建并进入聊天"
                    })
                }
            }
        }
    }
}

@Composable
private fun SmallAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CompactRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(5.dp))
            Text(label)
        }
    }
}

@Composable
private fun CustomSectionEditor(
    section: NewSessionSection,
    configuration: NewSessionConfiguration,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onConfigurationChange: (NewSessionConfiguration) -> Unit,
    tagLibraryState: TagLibraryEditorState,
    tagLibraryEditor: TagLibraryEditor,
    onTagLibraryStateChange: (TagLibraryEditorState) -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        FormalTopBar(title = section.label, onBack = onBack, trailingText = configuration.sectionSummary(section))
        if (section == NewSessionSection.CustomFields) {
            CustomColumnEditorScreen(
                columns = configuration.effectiveCustomColumns(),
                tagLibraryState = tagLibraryState,
                tagLibraryEditor = tagLibraryEditor,
                onTagLibraryStateChange = onTagLibraryStateChange,
                onColumnsChange = { onConfigurationChange(configuration.withCustomColumns(it)) },
                modifier = Modifier.weight(1f)
            )
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (section) {
                NewSessionSection.Basic -> {
                    item { SessionConfigurationTextField("会话名称 *", configuration.title, "new-session-title") { onConfigurationChange(configuration.copy(title = it)) } }
                    item { SessionConfigurationTextField("学习者角色 *", configuration.learnerRole, "new-session-learner-role") { onConfigurationChange(configuration.copy(learnerRole = it)) } }
                    item { SessionConfigurationTextField("学习者画像", configuration.learnerProfile) { onConfigurationChange(configuration.copy(learnerProfile = it)) } }
                    item { SessionConfigurationTextField("开场消息", configuration.openingMessage) { onConfigurationChange(configuration.copy(openingMessage = it)) } }
                }
                NewSessionSection.Goals -> {
                    item { SessionConfigurationTextField("主要目标", configuration.goal) { onConfigurationChange(configuration.copy(goal = it)) } }
                    item { SessionConfigurationTextField("学习计划", configuration.plan) { onConfigurationChange(configuration.copy(plan = it)) } }
                }
                NewSessionSection.Dialogue -> item {
                    SessionConfigurationTextField("对话策略", configuration.dialogueStrategy) { onConfigurationChange(configuration.copy(dialogueStrategy = it)) }
                }
                NewSessionSection.WorldTree -> item {
                    SessionConfigurationTextField("世界树与故事", configuration.story) { onConfigurationChange(configuration.copy(story = it)) }
                }
                NewSessionSection.Sources -> item {
                    SessionConfigurationTextField("资料选择（每行一项）", configuration.sourceSelections.joinToString("\n")) {
                        onConfigurationChange(configuration.copy(sourceSelections = it.lines().map(String::trim).filter(String::isNotEmpty)))
                    }
                }
                NewSessionSection.CustomFields -> Unit
            }
        }
    }
}

@Composable
fun SessionConfigurationTextField(
    label: String,
    value: String,
    testTag: String? = null,
    onBoundary: () -> Unit = {},
    onValueChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .onFocusChanged {
                if (focused && !it.isFocused) onBoundary()
                focused = it.isFocused
            },
        minLines = 3,
        maxLines = 8,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onBoundary() })
    )
}

private class InMemoryTagLibraryPersistence : TagLibraryPersistence {
    private var snapshot: TagLibrarySnapshot? = null

    override fun loadTagLibrary(): TagLibrarySnapshot? = snapshot?.deepCopy()
    override fun saveTagLibrary(snapshot: TagLibrarySnapshot) {
        this.snapshot = snapshot.deepCopy()
    }
}

@Composable
private fun formatDraftTime(epochMillis: Long): String {
    val context = LocalContext.current
    return remember(context, epochMillis) {
        val date = Date(epochMillis)
        val datePart = android.text.format.DateFormat.getDateFormat(context).format(date)
        val timePart = android.text.format.DateFormat.getTimeFormat(context).format(date)
        "$datePart $timePart"
    }
}

@Composable
fun Task2B1CustomTreeScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val preview = NewSessionDraftRecord(
        id = "preview",
        configuration = NewSessionConfiguration(
            title = "高三数学讲题冲刺",
            learnerRole = "容易跳步骤、会追问为什么的学习者",
            goal = "把会做的题讲清楚"
        ),
        updatedAtEpochMillis = 0L
    )
    FormalPageFrame(modifier.testTag("formal-custom-tree-716-494")) {
        Column(Modifier.fillMaxSize()) {
            FormalTopBar(title = "自定义", onBack = onBack)
            CustomRootScreen(
                state = NewSessionLifecycleState(tab = NewSessionHubTab.Custom, currentDraft = preview),
                listState = rememberLazyListState(),
                draftMenuExpanded = false,
                onDraftMenuExpandedChange = {}, onNewDraft = {}, onRestoreDraft = {}, onRenameDraft = {},
                onCopyDraft = {}, onDeleteDraft = {}, onOpenSection = {}, onRandom = {}, onUndo = {},
                onFavorite = {}, onCreate = {}
            )
        }
    }
}

@Composable
private fun DifferenceDialog(
    title: String,
    differences: List<ConfigurationDifference>,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(differences) { difference ->
                    Column {
                        Text(difference.field, fontWeight = FontWeight.Bold)
                        Text("原：${difference.before.ifBlank { "未填写" }}")
                        Text("新：${difference.after.ifBlank { "未填写" }}")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun FormalTopBar(
    title: String,
    onBack: () -> Unit,
    trailingText: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            color = FormalColors.Surface,
            shape = CircleShape,
            border = BorderStroke(1.dp, FormalColors.Border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f), style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink))
        when {
            trailing != null -> trailing()
            trailingText != null -> Text(trailingText, style = type.style(9f, 13f, color = FormalColors.Muted))
        }
    }
}

@Composable
private fun FormalPageFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().background(FormalColors.Background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.width(maxWidth.coerceAtMost(390.dp)).fillMaxHeight()) { content() }
    }
}
