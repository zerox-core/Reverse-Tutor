package com.reversetutor.feature.chat

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
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
    onOpenSourceCenter: (() -> Unit)? = null,
    onExportShare: (SessionExportPayload) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sourceOnly = initialSection == SessionSettingsSection.SourceManagement
    val sections = if (sourceOnly) {
        listOf(SessionSettingsSection.SourceManagement)
    } else {
        SessionSettingsSection.entries.filter { it != SessionSettingsSection.SourceManagement }
    }
    var revision by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = initialSection?.let { sections.indexOf(it).coerceAtLeast(0) } ?: 0
    ) { sections.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(initialSection) {
        initialSection?.let { pagerState.scrollToPage(sections.indexOf(it).coerceAtLeast(0)) }
    }
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
    val handleBack = {
        commitBoundary()
        onBack()
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

    Column(modifier.fillMaxSize().background(FormalColors.Background).testTag("session-settings-screen")) {
        SessionSettingsHeader(
            title = "窗口设置",
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
        if (!sourceOnly) {
            SectionTabStrip(
                document = coordinator.state.applied,
                sections = sections,
                selectedPage = pagerState.currentPage,
                onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } }
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (sections[page]) {
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
                SessionSettingsSection.Danger -> SystemOpsPage(
                    coordinator = coordinator,
                    onExportShare = onExportShare,
                    onRequestDeleteSession = onRequestDeleteSession,
                    canUndo = canUndoSessionDelete,
                    onUndo = onUndoSessionDelete
                )
            }
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
                FilledActionButton(
                    text = "删除并提供 5 秒撤销",
                    onClick = onConfirmDeleteSession,
                    danger = true,
                    onClickLabel = "确认删除"
                )
            },
            dismissButton = {
                TextButton(onClick = onDismissDeleteSession) { Text("取消", color = FormalColors.Muted) }
            }
        )
    }
}

@Composable
private fun SessionSettingsHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(FormalColors.Surface)) {
        Box(Modifier.fillMaxWidth().height(64.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 6.dp).size(44.dp)
                    .clickable(onClickLabel = "返回", role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = FormalColors.Ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.align(Alignment.CenterStart).padding(start = 56.dp, end = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FormalColors.Ink)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = FormalColors.Muted)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(FormalColors.Divider))
    }
}

@Composable
private fun SectionTabStrip(
    document: SessionSettingsDocument,
    sections: List<SessionSettingsSection>,
    selectedPage: Int,
    onSelect: (Int) -> Unit
) {
    val summaries = mapOf(
        SessionSettingsSection.Basic to "${document.profile.learnerDisplayName} · ${document.profile.learnerRole}",
        SessionSettingsSection.GoalPlan to document.goalPlan.primaryGoal.ifBlank { "尚未设置主要目标" },
        SessionSettingsSection.ConversationStrategy to "反馈 ${document.strategy.feedbackIntensity}/5 · ${document.strategy.speakingTone}",
        SessionSettingsSection.SourceManagement to "已引用 ${document.snapshot.sourceSelections.size} 份资料",
        SessionSettingsSection.Danger to "导出记忆库/配置 · 删除会话"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).testTag("session-settings-index"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        sections.forEachIndexed { index, section ->
            SettingsTabCard(
                section = section,
                summary = summaries.getValue(section),
                selected = index == selectedPage,
                onClick = { onSelect(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsTabCard(
    section: SessionSettingsSection,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, iconColor) = sessionSectionIcon(section)
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) FormalColors.Success.copy(alpha = 0.08f) else FormalColors.Surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) FormalColors.Success else FormalColors.Divider)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SessionGlossyIcon(icon, null, iconColor, size = 40.dp, glyphSize = 21.dp, radius = 11.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                section.label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) FormalColors.Success else FormalColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                summary,
                style = MaterialTheme.typography.labelSmall,
                color = FormalColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun sessionSectionIcon(section: SessionSettingsSection): Pair<ImageVector, Color> = when (section) {
    SessionSettingsSection.Basic -> Icons.Rounded.Person to Color(0xFF2E66C7)
    SessionSettingsSection.GoalPlan -> Icons.Rounded.Flag to Color(0xFFC96E26)
    SessionSettingsSection.ConversationStrategy -> Icons.Rounded.Tune to Color(0xFF5C6B8A)
    SessionSettingsSection.SourceManagement -> Icons.Rounded.Folder to Color(0xFF6170B8)
    SessionSettingsSection.Danger -> Icons.Rounded.Settings to Color(0xFF45506B)
}

@Composable
private fun SessionGlossyIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    color: Color,
    size: Dp = 30.dp,
    glyphSize: Dp = 17.dp,
    radius: Dp = 8.dp
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color(0x261F3861),
                spotColor = Color(0x261F3861)
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(color.copy(red = (color.red + 0.18f).coerceAtMost(1f)), color)
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(glyphSize)
        )
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
    val presetMatch = PersonalityPresets.firstOrNull { it.title == profile.personality.trim() }
    var customPersonaExpanded by remember {
        mutableStateOf(profile.personality.isNotBlank() && presetMatch == null)
    }
    SettingsTextField("会话标题", profile.title, { coordinator.editProfile { p -> p.copy(title = it) }; refresh() }, commitBoundary, singleLine = true, testTag = "session-settings-title")
    SettingsTextField("学习者显示名", profile.learnerDisplayName, { coordinator.editProfile { p -> p.copy(learnerDisplayName = it) }; refresh() }, commitBoundary, singleLine = true)
    SettingsTextField("学习者角色", profile.learnerRole, { coordinator.editProfile { p -> p.copy(learnerRole = it) }; refresh() }, commitBoundary, singleLine = true)
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("人格预设", fontWeight = FontWeight.SemiBold)
            CardRadioRow(
                options = PersonalityPresets,
                selectedId = presetMatch?.id,
                onSelect = { presetId ->
                    PersonalityPresets.firstOrNull { it.id == presetId }?.let { preset ->
                        coordinator.editProfile { p -> p.copy(personality = preset.title) }
                        customPersonaExpanded = false
                        refresh()
                    }
                }
            )
        }
    }
    SettingsGroup {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable(
                    onClickLabel = if (customPersonaExpanded) "收起自定义人设" else "展开自定义人设",
                    role = Role.Button,
                    onClick = { customPersonaExpanded = !customPersonaExpanded }
                ).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自己写人设与互动习惯（可选）", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = FormalColors.Ink)
                Icon(
                    if (customPersonaExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = FormalColors.Muted
                )
            }
            if (customPersonaExpanded) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsTextField("人格", profile.personality, { coordinator.editProfile { p -> p.copy(personality = it) }; refresh() }, commitBoundary)
                    SettingsTextField("互动习惯", profile.interactionHabits, { coordinator.editProfile { p -> p.copy(interactionHabits = it) }; refresh() }, commitBoundary)
                }
            }
        }
    }
    SettingsGroup {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Person, null, tint = FormalColors.Muted)
            Spacer(Modifier.width(12.dp))
            Text("显示当前会话头像", Modifier.weight(1f), color = FormalColors.Ink)
            Switch(
                profile.avatarVisible,
                onCheckedChange = { coordinator.setAvatarVisible(it); refresh() },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = FormalColors.Success,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE4E4E9),
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = Color(0xFFE4E4E9)
                )
            )
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
                    "模块" -> current.copy(modules = next)
                    "阶段里程碑" -> current.copy(stageMilestones = next)
                    "每周计划" -> current.copy(weeklyPlan = next)
                    else -> current.copy(currentState = next)
                }
            }
            refresh()
        }, commitBoundary)
    }
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("学习范围", fontWeight = FontWeight.SemiBold)
            TokenField(
                value = value.learningScope,
                onValueChange = { next -> coordinator.editGoalPlan { current -> current.copy(learningScope = next) }; refresh() }
            )
            Text("输入知识点后回车或点「添加」变成标签，例如：立体几何、导数、概率统计。", color = FormalColors.Muted)
        }
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
    MoodSettingGroup("反馈强度", strategy.feedbackIntensity, FeedbackMoodLevels) { value ->
        coordinator.setStrategy { current -> current.copy(feedbackIntensity = value) }
        refresh()
    }
    MoodSettingGroup("追问强度", strategy.probingIntensity, ProbingMoodLevels) { value ->
        coordinator.setStrategy { it.copy(probingIntensity = value) }; refresh()
    }
    MoodSettingGroup("脚手架强度", strategy.scaffoldingIntensity, ScaffoldingMoodLevels) { value ->
        coordinator.setStrategy { it.copy(scaffoldingIntensity = value) }; refresh()
    }
    SegmentedSettingGroup("纠错坚持度", strategy.correctionPersistence, listOf("宽松", "适中", "严格")) {
        coordinator.setStrategy { current -> current.copy(correctionPersistence = it) }; refresh()
    }
    SegmentedSettingGroup("复习频率", strategy.reviewFrequency, listOf("低", "每周", "高")) {
        coordinator.setStrategy { current -> current.copy(reviewFrequency = it) }; refresh()
    }
    SegmentedSettingGroup("说话语气", strategy.speakingTone, listOf("温和", "自然", "直接")) {
        coordinator.setSpeakingTone(it); refresh()
    }
}

@Composable
private fun MoodSettingGroup(label: String, value: Int, levels: List<MoodLevel>, onChange: (Int) -> Unit) {
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            MoodSliderRow(value = value, levels = levels, onChange = onChange)
        }
    }
}

@Composable
private fun SegmentedSettingGroup(label: String, selected: String, values: List<String>, onSelect: (String) -> Unit) {
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            SegmentedPillsRow(values = values, selected = selected, onSelect = onSelect)
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

// 导出面版式：1 = 版式C 大图标卡单选+装箱单+主按钮；2 = 版式D 胶囊分段+装箱单+主按钮（拍板后收敛为一版）
private const val ExportPanelVariant = 2

@Composable
private fun SystemOpsPage(
    coordinator: SessionSettingsCoordinator,
    onExportShare: (SessionExportPayload) -> Unit,
    onRequestDeleteSession: () -> Unit,
    canUndo: Boolean,
    onUndo: () -> Unit
) = SettingsPage {
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("导出", fontWeight = FontWeight.Bold, color = FormalColors.Ink)
            Text(
                "记忆库是当前会话的结构化记录（基本资料、目标计划、对话策略、快照与快捷标签）；分层记忆接入后会自动并入下载包。",
                color = FormalColors.Muted
            )
            val shareExport: (Boolean) -> Unit = { memory ->
                onExportShare(
                    if (memory) SessionSettingsExport.buildMemoryPayload(coordinator.state.applied)
                    else SessionSettingsExport.buildConfigPayload(coordinator.state.applied)
                )
            }
            if (ExportPanelVariant == 1) {
                ExportPickSharePanel(onShare = shareExport)
            } else {
                ExportSegmentsSharePanel(onShare = shareExport)
            }
            Text(
                "通过系统分享面板发出，可存到文件或发给好友。",
                color = FormalColors.Muted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    SettingsGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("删除当前会话", color = FormalColors.Danger, fontWeight = FontWeight.Bold)
            Text("复用首页会话删除确认、durable 清理、5 秒撤销及幂等竞态处理。", color = FormalColors.Muted)
            HoldToConfirmButton(
                text = "按住不放，删除当前会话",
                holdingText = "继续按住…",
                onConfirm = onRequestDeleteSession,
                modifier = Modifier.fillMaxWidth()
            )
            if (canUndo) SecondaryActionButton(
                text = "撤销删除（5 秒）",
                onClick = onUndo,
                danger = true,
                modifier = Modifier.fillMaxWidth(),
                testTag = "undo-delete-session"
            )
        }
    }
}


@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onBoundary: () -> Unit,
    testTag: String? = null,
    singleLine: Boolean = false
) {
    SessionConfigurationTextField(
        label = label,
        value = value,
        testTag = testTag,
        singleLine = singleLine,
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
        color = FormalColors.Surface,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) { Column(content = content) }
}

internal fun formatSourceTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochMillis))
