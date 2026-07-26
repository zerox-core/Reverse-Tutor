package com.reversetutor.feature.chat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SessionSettingsScreen(
    coordinator: SessionSettingsCoordinator,
    tagLibraryPersistence: TagLibraryPersistence,
    initialSection: SessionSettingsSection? = null,
    highlightedSourceId: String? = null,
    onBack: () -> Unit,
    onProfileBoundary: (SessionSettingsProfile) -> Unit = {},
    onPickSource: (String?) -> Unit = {},
    onRequestDeleteSession: () -> Unit = {},
    pendingSessionDeleteTitle: String? = null,
    externalErrorMessage: String? = null,
    externalImportError: String? = null,
    onRetryImport: () -> Unit = {},
    onConfirmDeleteSession: () -> Unit = {},
    onDismissDeleteSession: () -> Unit = {},
    canUndoSessionDelete: Boolean = false,
    onUndoSessionDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    var revision by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val tagEditor = remember(tagLibraryPersistence) { TagLibraryEditor(tagLibraryPersistence) }
    var tagState by remember(tagEditor) {
        tagEditor.load()
        mutableStateOf(tagEditor.state)
    }
    val refresh = { revision += 1 }
    val commitBoundary = {
        coordinator.commitTextBoundary()
        onProfileBoundary(coordinator.state.applied.profile)
        refresh()
    }
    val context = LocalContext.current
    val learnerImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            coordinator.setLearnerImageRef(uri.toString())
            refresh()
        }
    }
    val storyImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            coordinator.setStoryImageRef(uri.toString())
            refresh()
        }
    }
    val handleBack = {
        commitBoundary()
        if (section == null) onBack() else section = null
    }
    BackHandler(onBack = handleBack)

    DisposableEffect(lifecycleOwner, coordinator) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                coordinator.onApplicationBackgrounded()
                onProfileBoundary(coordinator.state.applied.profile)
                refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val undo = coordinator.state.sourceUndo
    LaunchedEffect(undo?.expiresAtEpochMillis) {
        val current = undo ?: return@LaunchedEffect
        delay((current.expiresAtEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L))
        coordinator.finalizeExpiredSourceAction()
        refresh()
    }

    Column(modifier.fillMaxSize().background(Color(0xFFF4F7FC)).testTag("session-settings-screen")) {
        SessionSettingsHeader(
            title = section?.label ?: "会话设置",
            subtitle = coordinator.state.applied.profile.title,
            onBack = handleBack
        )
        externalErrorMessage?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFFEDEA)).padding(12.dp),
                color = Color(0xFFB3261E)
            )
        }
        (externalImportError ?: coordinator.state.errorMessage)?.let { message ->
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFFFFEDEA)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message, Modifier.weight(1f), color = Color(0xFFB3261E))
                if (externalImportError != null) TextButton(onClick = onRetryImport) { Text("重试") }
            }
        }
        when (section) {
            null -> SettingsIndex(coordinator.state.applied, onOpen = { section = it })
            SessionSettingsSection.Basic -> BasicProfilePage(
                coordinator,
                commitBoundary,
                onPickLearnerImage = { learnerImageLauncher.launch(arrayOf("image/*")) },
                refresh = refresh
            )
            SessionSettingsSection.GoalPlan -> GoalPlanPage(
                coordinator,
                tagEditor,
                tagState,
                onTagStateChange = { tagState = it },
                commitBoundary,
                refresh
            )
            SessionSettingsSection.ConversationStrategy -> StrategyPage(coordinator, refresh)
            SessionSettingsSection.SourceManagement -> SourceManagementPage(
                coordinator,
                onPickSource,
                refresh,
                highlightedSourceId
            )
            SessionSettingsSection.WorldTree -> WorldTreePage(
                coordinator,
                tagEditor,
                tagState,
                onTagStateChange = { tagState = it },
                commitBoundary,
                onPickLearnerImage = { learnerImageLauncher.launch(arrayOf("image/*")) },
                onPickStoryImage = { storyImageLauncher.launch(arrayOf("image/*")) },
                refresh = refresh
            )
            SessionSettingsSection.Danger -> DangerPage(
                onRequestDeleteSession,
                canUndoSessionDelete,
                onUndoSessionDelete
            )
        }
    }

    coordinator.state.pendingConfirmation?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                coordinator.dismissProtectedChanges()
                refresh()
            },
            title = { Text("确认影响后续行为") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("仅影响后续对话；历史、收藏模板和既有资料关系保持不变。")
                    pending.differences.forEach {
                        Text("${it.field}：${it.before.ifBlank { "空" }} → ${it.after.ifBlank { "空" }}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    coordinator.confirmProtectedChanges()
                    onProfileBoundary(coordinator.state.applied.profile)
                    refresh()
                }) { Text("确认修改") }
            },
            dismissButton = {
                TextButton(onClick = {
                    coordinator.dismissProtectedChanges()
                    refresh()
                }) { Text("保留原值") }
            }
        )
    }

    pendingSessionDeleteTitle?.let { title ->
        AlertDialog(
            onDismissRequest = onDismissDeleteSession,
            title = { Text("删除当前会话？") },
            text = { Text("将删除“$title”的消息、设置、输入草稿和会话图谱状态；共享资料文件不会被删除。") },
            confirmButton = {
                Button(onClick = onConfirmDeleteSession) { Text("删除并提供 5 秒撤销") }
            },
            dismissButton = { TextButton(onClick = onDismissDeleteSession) { Text("取消") } }
        )
    }
}

@Composable
private fun SessionSettingsHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Surface(color = Color(0xFFFAFCFE), shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
private fun SettingsIndex(document: SessionSettingsDocument, onOpen: (SessionSettingsSection) -> Unit) {
    val summaries = mapOf(
        SessionSettingsSection.Basic to "${document.profile.learnerDisplayName} · ${document.profile.learnerRole}",
        SessionSettingsSection.GoalPlan to document.goalPlan.primaryGoal.ifBlank { "尚未设置主要目标" },
        SessionSettingsSection.ConversationStrategy to "反馈 ${document.strategy.feedbackIntensity}/5 · ${document.strategy.speakingTone}",
        SessionSettingsSection.SourceManagement to "已引用 ${document.snapshot.sourceSelections.size} 份资料",
        SessionSettingsSection.WorldTree to "独立快照 · ${document.snapshot.effectiveCustomColumns().size} 个自定义栏目",
        SessionSettingsSection.Danger to "删除当前会话"
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("session-settings-index"),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(SessionSettingsSection.entries) { section ->
            SettingsGroup {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(section) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(section.label, fontWeight = FontWeight.SemiBold)
                        Text(summaries.getValue(section), style = MaterialTheme.typography.bodySmall, color = Color(0xFF687386))
                    }
                    Icon(Icons.Filled.ChevronRight, null)
                }
            }
        }
    }
}

@Composable
private fun BasicProfilePage(
    coordinator: SessionSettingsCoordinator,
    commitBoundary: () -> Unit,
    onPickLearnerImage: () -> Unit,
    refresh: () -> Unit
) = SettingsPage {
    val profile = coordinator.state.form.profile
    SettingsTextField("会话标题", profile.title, { coordinator.editProfile { p -> p.copy(title = it) }; refresh() }, commitBoundary)
    SettingsTextField("学习者显示名", profile.learnerDisplayName, { coordinator.editProfile { p -> p.copy(learnerDisplayName = it) }; refresh() }, commitBoundary)
    SettingsTextField("学习者角色", profile.learnerRole, { coordinator.editProfile { p -> p.copy(learnerRole = it) }; refresh() }, commitBoundary)
    SettingsTextField("人格", profile.personality, { coordinator.editProfile { p -> p.copy(personality = it) }; refresh() }, commitBoundary)
    SettingsTextField("互动习惯", profile.interactionHabits, { coordinator.editProfile { p -> p.copy(interactionHabits = it) }; refresh() }, commitBoundary)
    SettingsGroup {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null)
            Spacer(Modifier.width(12.dp))
            Text("显示当前会话头像", Modifier.weight(1f))
            Switch(profile.avatarVisible, onCheckedChange = { coordinator.setAvatarVisible(it); refresh() })
        }
    }
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("当前头像：${coordinator.state.form.snapshot.learnerImageRef ?: "未设置"}")
            OutlinedButton(onClick = onPickLearnerImage, Modifier.fillMaxWidth()) { Text("选择或修改头像") }
            if (coordinator.state.form.snapshot.learnerImageRef != null) {
                TextButton(
                    onClick = { coordinator.setLearnerImageRef(null); refresh() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("移除当前会话头像") }
            }
        }
    }
    Text("已经发送的开场消息属于历史记录，不在此处提供编辑。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF687386))
}

@Composable
private fun GoalPlanPage(
    coordinator: SessionSettingsCoordinator,
    tagEditor: TagLibraryEditor,
    tagState: TagLibraryEditorState,
    onTagStateChange: (TagLibraryEditorState) -> Unit,
    commitBoundary: () -> Unit,
    refresh: () -> Unit
) = SettingsPage {
    val value = coordinator.state.form.goalPlan
    listOf(
        "主要目标" to value.primaryGoal,
        "截止时间" to value.deadline,
        "学习范围" to value.learningScope,
        "模块" to value.modules,
        "阶段里程碑" to value.stageMilestones,
        "每周计划" to value.weeklyPlan,
        "当前状态" to value.currentState
    ).forEach { (label, text) ->
        SettingsTextField(label, text, { next ->
            coordinator.editGoalPlan { current ->
                when (label) {
                    "主要目标" -> current.copy(primaryGoal = next)
                    "截止时间" -> current.copy(deadline = next)
                    "学习范围" -> current.copy(learningScope = next)
                    "模块" -> current.copy(modules = next)
                    "阶段里程碑" -> current.copy(stageMilestones = next)
                    "每周计划" -> current.copy(weeklyPlan = next)
                    else -> current.copy(currentState = next)
                }
            }
            refresh()
        }, commitBoundary)
    }
    Text("分类快捷标签", fontWeight = FontWeight.SemiBold)
    TagLibraryPicker(
        state = tagState,
        selection = coordinator.state.form.quickTags["goal"] ?: TagFieldSelection(),
        editor = tagEditor,
        onStateChange = onTagStateChange,
        onToggle = { coordinator.toggleQuickTag("goal", TagSelectionValue(it.id, it.name)); refresh() },
        onExpansionChange = { groupId, expanded ->
            tagEditor.setGroupExpanded(groupId, expanded)
            onTagStateChange(tagEditor.state)
        }
    )
}

@Composable
private fun StrategyPage(coordinator: SessionSettingsCoordinator, refresh: () -> Unit) = SettingsPage {
    val strategy = coordinator.state.applied.strategy
    StrategySlider("反馈强度", strategy.feedbackIntensity) { value ->
        coordinator.setStrategy { current -> current.copy(feedbackIntensity = value) }
        refresh()
    }
    StrategySlider("追问强度", strategy.probingIntensity) { value -> coordinator.setStrategy { it.copy(probingIntensity = value) }; refresh() }
    StrategySlider("脚手架强度", strategy.scaffoldingIntensity) { value -> coordinator.setStrategy { it.copy(scaffoldingIntensity = value) }; refresh() }
    StrategySegments("纠错坚持度", strategy.correctionPersistence, listOf("宽松", "适中", "严格")) {
        coordinator.setStrategy { current -> current.copy(correctionPersistence = it) }; refresh()
    }
    StrategySegments("复习频率", strategy.reviewFrequency, listOf("低", "每周", "高")) {
        coordinator.setStrategy { current -> current.copy(reviewFrequency = it) }; refresh()
    }
    StrategySegments("说话语气", strategy.speakingTone, listOf("温和", "自然", "直接")) {
        coordinator.setSpeakingTone(it); refresh()
    }
}

@Composable
private fun StrategySlider(label: String, value: Int, onChange: (Int) -> Unit) {
    SettingsGroup {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label)
                Text("$value / 5")
            }
            Slider(value.toFloat(), onValueChange = { onChange(it.toInt().coerceIn(1, 5)) }, valueRange = 1f..5f, steps = 3)
        }
    }
}

@Composable
private fun StrategySegments(label: String, selected: String, values: List<String>, onSelect: (String) -> Unit) {
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { value -> FilterChip(selected == value, onClick = { onSelect(value) }, label = { Text(value) }) }
            }
        }
    }
}

@Composable
private fun SourceManagementPage(
    coordinator: SessionSettingsCoordinator,
    onPickSource: (String?) -> Unit,
    refresh: () -> Unit,
    highlightedSourceId: String? = null
) {
    var filter by remember { mutableStateOf(SourceFilter.All) }
    var query by remember { mutableStateOf("") }
    var detailId by remember(highlightedSourceId) { mutableStateOf(highlightedSourceId) }
    var aliasSource by remember { mutableStateOf<SessionSource?>(null) }
    val detail = detailId?.let(coordinator::source)
    if (detail != null) {
        SettingsPage {
            Text("内容预览", fontWeight = FontWeight.SemiBold)
            SettingsGroup { Text(detail.preview.ifBlank { "暂无可预览内容；可查看文件元数据和解析状态。" }, Modifier.padding(16.dp)) }
            Text("${detail.typeLabel} · ${detail.readState.label} · 本会话${if (detail.currentSessionReferenced) "已引用" else "未引用"}")
            Text("共 ${detail.referenceOwnerIds.size} 个会话/模板引用 · 最近使用 ${formatSourceTime(detail.lastUsedAtEpochMillis)}（无使用记录时回退创建时间）")
            SettingsGroup {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPickSource(detail.id) }, Modifier.fillMaxWidth()) { Text("重新选择文件") }
                    OutlinedButton(
                        onClick = { coordinator.unlinkCurrentSession(detail.id); refresh() },
                        enabled = detail.currentSessionReferenced,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (detail.currentSessionReferenced) "取消本会话引用" else "本会话未引用") }
                    Button(onClick = { coordinator.requestDeleteSource(detail.id); refresh() }, Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Delete, null)
                        Text("删除资料文件")
                    }
                    TextButton(onClick = { aliasSource = detail }, Modifier.fillMaxWidth()) { Text("编辑本会话显示别名") }
                }
            }
            coordinator.state.sourceUndo?.let {
                SettingsGroup {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (it.kind == SourceUndoKind.Unlink) "已取消本会话引用" else "资料文件待删除", Modifier.weight(1f))
                        TextButton(onClick = { coordinator.undoSourceAction(); refresh() }) { Text("撤销（5 秒）") }
                    }
                }
            }
            TextButton(onClick = { detailId = null }) { Text("返回资料列表") }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("按名称搜索") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) }
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceFilter.entries.forEach { item ->
                        FilterChip(filter == item, onClick = { filter = item }, label = { Text(item.label) })
                    }
                }
            }
            item { OutlinedButton(onClick = { onPickSource(null) }, Modifier.fillMaxWidth()) { Text("添加资料") } }
            val sources = coordinator.visibleSources(filter, query)
            if (sources.isEmpty()) {
                item { Text("没有符合条件的资料。可调整筛选或添加文件。", color = Color(0xFF687386)) }
            } else {
                items(sources, key = SessionSource::id) { source ->
                    SettingsGroup {
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                coordinator.markSourceUsed(source.id)
                                detailId = source.id
                                refresh()
                            }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(source.displayName, fontWeight = FontWeight.SemiBold)
                                Text("${source.typeLabel} · ${source.readState.label} · 本会话${if (source.currentSessionReferenced) "已引用" else "未引用"}")
                                Text("共 ${source.referenceOwnerIds.size} 个会话/模板 · 最近使用 ${formatSourceTime(source.lastUsedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Filled.ChevronRight, null)
                        }
                    }
                }
            }
            coordinator.state.sourceUndo?.let {
                item {
                    SettingsGroup {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (it.kind == SourceUndoKind.Unlink) "已取消本会话引用" else "资料文件待删除", Modifier.weight(1f))
                            TextButton(onClick = { coordinator.undoSourceAction(); refresh() }) { Text("撤销（5 秒）") }
                        }
                    }
                }
            }
        }
    }

    aliasSource?.let { source ->
        var alias by remember(source.id) { mutableStateOf(source.displayName) }
        AlertDialog(
            onDismissRequest = { aliasSource = null },
            title = { Text("本会话显示别名") },
            text = { OutlinedTextField(alias, onValueChange = { alias = it }) },
            confirmButton = {
                Button(onClick = { if (coordinator.setSourceAlias(source.id, alias)) aliasSource = null; refresh() }) { Text("保存别名") }
            },
            dismissButton = { TextButton(onClick = { aliasSource = null }) { Text("取消") } }
        )
    }

    coordinator.state.pendingSourceDelete?.let { pending ->
        AlertDialog(
            onDismissRequest = { coordinator.dismissSourceDelete(); refresh() },
            title = { Text("删除资料文件") },
            text = {
                Column {
                    Text("受影响的完整会话/模板列表：")
                    pending.impactedOwnerIds.forEach { Text("· $it") }
                    if (pending.requiresImpactConfirmation) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                pending.deleteEnabled,
                                enabled = coordinator.deleteCapability.available,
                                onCheckedChange = { coordinator.acknowledgeDeleteImpact(it); refresh() }
                            )
                            Text("确认使这些引用失效")
                        }
                    }
                    Text(
                        if (coordinator.deleteCapability.available) "删除和取消引用不同：文件将由真实仓储能力删除。"
                        else "当前版本暂不支持删除资料文件；引用、文件内容与读取状态都会保留。"
                    )
                }
            },
            confirmButton = {
                Button(enabled = pending.deleteEnabled, onClick = { coordinator.confirmDeleteSource(); refresh() }) { Text("删除资料文件") }
            },
            dismissButton = { TextButton(onClick = { coordinator.dismissSourceDelete(); refresh() }) { Text("取消") } }
        )
    }
}

@Composable
private fun WorldTreePage(
    coordinator: SessionSettingsCoordinator,
    tagEditor: TagLibraryEditor,
    tagState: TagLibraryEditorState,
    onTagStateChange: (TagLibraryEditorState) -> Unit,
    commitBoundary: () -> Unit,
    onPickLearnerImage: () -> Unit,
    onPickStoryImage: () -> Unit,
    refresh: () -> Unit
) = SettingsPage {
    val document = coordinator.state.form
    val snapshot = document.snapshot
    Text("只编辑当前会话的独立快照", fontWeight = FontWeight.SemiBold)
    Text("不会回写内置预设、草稿、收藏或模板；此页不含草稿箱、收藏和随机组合。", style = MaterialTheme.typography.bodySmall)
    SettingsTextField("会话标题", document.profile.title, { coordinator.editProfile { p -> p.copy(title = it) }; refresh() }, commitBoundary)
    SettingsTextField("学习者角色", document.profile.learnerRole, { coordinator.editProfile { p -> p.copy(learnerRole = it) }; refresh() }, commitBoundary)
    SettingsTextField("学习者画像", document.profile.personality, { coordinator.editProfile { p -> p.copy(personality = it) }; refresh() }, commitBoundary)
    SettingsTextField("主要目标", document.goalPlan.primaryGoal, { coordinator.editGoalPlan { p -> p.copy(primaryGoal = it) }; refresh() }, commitBoundary)
    SettingsTextField("学习计划", document.goalPlan.weeklyPlan, { coordinator.editGoalPlan { p -> p.copy(weeklyPlan = it) }; refresh() }, commitBoundary)
    SettingsTextField("对话策略", document.profile.interactionHabits, { coordinator.editProfile { p -> p.copy(interactionHabits = it) }; refresh() }, commitBoundary)
    SettingsTextField("世界树故事", snapshot.story, {
        coordinator.editWorldTree { current -> current.copy(story = it) }
        refresh()
    }, commitBoundary, testTag = "session-settings-story")
    Text("资料 Source ID：${snapshot.sourceSelections.ifEmpty { listOf("未选择") }.joinToString()}")
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("学习者图片：${snapshot.learnerImageRef ?: "未设置"}")
            OutlinedButton(onClick = onPickLearnerImage, Modifier.fillMaxWidth()) { Text("选择或修改学习者图片") }
            Text("故事图片：${snapshot.storyImageRef ?: "未设置"}")
            OutlinedButton(onClick = onPickStoryImage, Modifier.fillMaxWidth()) { Text("选择或修改故事图片") }
        }
    }
    SettingsTextField("来源预设标识", snapshot.builtInPresetId.orEmpty(), {
        coordinator.editWorldTree { current -> current.copy(builtInPresetId = it.trim().ifEmpty { null }) }
        refresh()
    }, commitBoundary)
    Text("分类快捷标签", fontWeight = FontWeight.SemiBold)
    TagLibraryPicker(
        state = tagState,
        selection = document.quickTags["world-tree"] ?: TagFieldSelection(),
        editor = tagEditor,
        onStateChange = onTagStateChange,
        onToggle = { coordinator.toggleQuickTag("world-tree", TagSelectionValue(it.id, it.name)); refresh() },
        onExpansionChange = { groupId, expanded ->
            tagEditor.setGroupExpanded(groupId, expanded)
            onTagStateChange(tagEditor.state)
        }
    )
    Text("自定义栏目", fontWeight = FontWeight.SemiBold)
    CustomColumnEditorScreen(
        columns = snapshot.effectiveCustomColumns(),
        tagLibraryState = tagState,
        tagLibraryEditor = tagEditor,
        onTagLibraryStateChange = onTagStateChange,
        onColumnsChange = { columns -> coordinator.updateWorldTree { it.withCustomColumns(columns) }; refresh() }
    )
}

@Composable
private fun DangerPage(
    onRequestDeleteSession: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit
) = SettingsPage {
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("删除当前会话", color = Color(0xFFB3261E), fontWeight = FontWeight.Bold)
            Text("复用首页会话删除确认、durable 清理、5 秒撤销及幂等竞态处理。")
            Button(onClick = onRequestDeleteSession, Modifier.fillMaxWidth()) { Text("删除当前会话") }
            if (canUndo) OutlinedButton(onClick = onUndo, Modifier.fillMaxWidth()) { Text("撤销删除（5 秒）") }
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onBoundary: () -> Unit,
    testTag: String? = null
) {
    SessionConfigurationTextField(
        label = label,
        value = value,
        testTag = testTag,
        onBoundary = onBoundary,
        onValueChange = onValueChange
    )
}

@Composable
private fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { item { Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content) } }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFAFCFE),
        tonalElevation = 1.dp
    ) { Column(content = content) }
}

internal fun formatSourceTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
