package com.reversetutor.preview.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.reversetutor.preview.theme.ReverseTutorDesign
import com.reversetutor.preview.ui.RtButton
import com.reversetutor.preview.ui.RtButtonSize
import com.reversetutor.preview.ui.RtButtonTone
import com.reversetutor.preview.ui.RtCard
import com.reversetutor.preview.ui.RtCardVariant
import com.reversetutor.preview.ui.RtChip
import com.reversetutor.preview.ui.RtIconKey
import com.reversetutor.preview.ui.RtSectionHeader
import com.reversetutor.preview.ui.RtSliderRow
import com.reversetutor.preview.ui.RtToggleRow
import com.reversetutor.feature.chat.SessionHomePort
import com.reversetutor.feature.chat.SessionHomeViewModel
import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionPersistence
import com.reversetutor.feature.chat.SessionSettingsCoordinator
import com.reversetutor.feature.chat.SessionSettingsDocument
import com.reversetutor.feature.chat.SessionSettingsScreen
import com.reversetutor.feature.chat.SessionSettingsSection
import com.reversetutor.feature.chat.SessionSettingsStore
import com.reversetutor.feature.chat.SessionSource
import com.reversetutor.feature.chat.SourceFileDeleteCapability
import com.reversetutor.feature.chat.TagLibraryPersistence
import com.reversetutor.feature.chat.Task2ADeletionDelegate

fun interface SessionSettingsCoordinatorFactory {
    fun create(
        sessionId: String,
        snapshot: NewSessionConfiguration,
        sources: List<SessionSource>,
        onSourcesChanged: () -> Unit
    ): SessionSettingsCoordinator
}

class ProductionSessionSettingsCoordinatorFactory(
    private val store: SessionSettingsStore,
    private val persistence: NewSessionPersistence?,
    private val deleteCapability: SourceFileDeleteCapability = SourceFileDeleteCapability.Unavailable
) : SessionSettingsCoordinatorFactory {
    override fun create(
        sessionId: String,
        snapshot: NewSessionConfiguration,
        sources: List<SessionSource>,
        onSourcesChanged: () -> Unit
    ): SessionSettingsCoordinator = SessionSettingsCoordinator(
        sessionId = sessionId,
        initial = SessionSettingsDocument.fromSnapshot(snapshot),
        initialSources = sources,
        store = store,
        deleteCapability = deleteCapability,
        onSnapshotApplied = { applied ->
            saveAppliedSessionSnapshot(persistence, sessionId, applied, onSourcesChanged)
        },
        onSourcesChanged = onSourcesChanged
    )
}

internal fun createTask2ADeletionDelegate(
    viewModel: SessionHomeViewModel,
    sessionId: String,
    onDeletionStaged: (Boolean) -> Unit
): Task2ADeletionDelegate = Task2ADeletionDelegate(
    request = { viewModel.requestDelete(sessionId) },
    confirm = {
        onDeletionStaged(true)
        viewModel.confirmDelete()
    },
    dismiss = viewModel::dismissDelete,
    undo = {
        onDeletionStaged(false)
        viewModel.undoDelete()
    }
)

data class PickedSessionSource(
    val source: SessionSource,
    val replacingSourceId: String? = null
)

@Composable
fun SessionSettingsRoute(
    destination: AppDestination,
    sessionId: String?,
    sessionTitle: String,
    sessionHomePort: SessionHomePort,
    newSessionPersistence: NewSessionPersistence? = null,
    tagLibraryPersistence: TagLibraryPersistence? = null,
    sessionSettingsStore: SessionSettingsStore? = null,
    coordinatorFactory: SessionSettingsCoordinatorFactory? = null,
    initialSources: List<SessionSource> = emptyList(),
    highlightedSourceId: String? = null,
    pickedSource: PickedSessionSource? = null,
    onPickedSourceConsumed: () -> Unit = {},
    onSourcesChanged: () -> Unit = {},
    onSessionTitleChanged: (String) -> Unit = {},
    onSessionLearnerRoleChanged: (String) -> Unit = {},
    onSessionDeleted: () -> Unit = {},
    onSelectDestination: (AppDestination) -> Unit,
    onOpenBrain: () -> Unit,
    onPickSource: () -> Unit = {},
    onPickManagedSource: (String?) -> Unit = {},
    externalImportError: String? = null,
    onRetryImport: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val sessionHomeViewModel = remember(sessionHomePort) {
        SessionHomeViewModel(sessionHomePort, scope)
    }
    val sessionHomeState by sessionHomeViewModel.uiState.collectAsState()
    val activeSession = sessionHomeState.sessions.firstOrNull { it.id == sessionId }
    if (destination in setOf(AppDestination.SessionSettings, AppDestination.SessionSettingsSources) &&
        sessionId != null &&
        tagLibraryPersistence != null &&
        sessionSettingsStore != null
    ) {
        val snapshot = remember(sessionId, newSessionPersistence) {
            newSessionPersistence?.loadSessionSnapshot(sessionId) ?: NewSessionConfiguration(
                title = sessionTitle,
                learnerRole = activeSession?.learnerRole ?: "学习者",
                learnerProfile = "未设置",
                goal = "未设置",
                dialogueStrategy = "未设置"
            )
        }
        val productionCoordinatorFactory = remember(sessionSettingsStore, newSessionPersistence) {
            ProductionSessionSettingsCoordinatorFactory(sessionSettingsStore, newSessionPersistence)
        }
        val resolvedCoordinatorFactory = coordinatorFactory ?: productionCoordinatorFactory
        val coordinator = remember(sessionId, resolvedCoordinatorFactory) {
            resolvedCoordinatorFactory.create(sessionId, snapshot, initialSources, onSourcesChanged)
        }
        LaunchedEffect(initialSources) {
            refreshSessionSettingsSources(coordinator, initialSources)
        }
        LaunchedEffect(coordinator) {
            saveAppliedSessionSnapshot(
                newSessionPersistence,
                sessionId,
                coordinator.state.applied.snapshot,
                onSourcesChanged
            )
        }
        var deletionStaged by remember(sessionId) { mutableStateOf(false) }
        LaunchedEffect(pickedSource?.source?.id, pickedSource?.replacingSourceId) {
            val picked = pickedSource ?: return@LaunchedEffect
            if (picked.replacingSourceId == null) {
                coordinator.addSource(picked.source)
            } else if (!coordinator.replaceSourceRevision(picked.replacingSourceId, picked.source)) {
                coordinator.reportError("无法替换所选资料，请重试。")
            }
            onPickedSourceConsumed()
        }
        LaunchedEffect(deletionStaged, sessionHomeState.undo, sessionHomeState.sessions) {
            if (deletionStaged &&
                sessionHomeState.undo == null &&
                sessionHomeState.sessions.none { it.id == sessionId }
            ) {
                sessionSettingsStore.remove(sessionId)
                onSessionDeleted()
            }
        }
        val deletionDelegate = createTask2ADeletionDelegate(sessionHomeViewModel, sessionId) {
            deletionStaged = it
        }
        SessionSettingsScreen(
            coordinator = coordinator,
            tagLibraryPersistence = tagLibraryPersistence,
            initialSection = if (destination == AppDestination.SessionSettingsSources) {
                SessionSettingsSection.SourceManagement
            } else {
                null
            },
            highlightedSourceId = highlightedSourceId,
            onBack = onBack,
            onProfileBoundary = { profile ->
                onSessionLearnerRoleChanged(profile.learnerRole)
                activeSession?.let { session ->
                    if (profile.title.isNotBlank() && profile.title != session.title) {
                        sessionHomeViewModel.rename(sessionId, profile.title)
                        onSessionTitleChanged(profile.title)
                    }
                    if (profile.avatarVisible != session.perSessionAvatarVisible) {
                        sessionHomeViewModel.setAvatarVisible(sessionId, profile.avatarVisible)
                    }
                }
            },
            onPickSource = onPickManagedSource,
            onRequestDeleteSession = deletionDelegate::request,
            pendingSessionDeleteTitle = sessionHomeState.pendingDelete?.title,
            externalErrorMessage = sessionHomeState.errorMessage,
            externalImportError = externalImportError,
            onRetryImport = onRetryImport,
            onConfirmDeleteSession = deletionDelegate::confirm,
            onDismissDeleteSession = deletionDelegate::dismiss,
            canUndoSessionDelete = sessionHomeState.undo?.session?.id == sessionId,
            onUndoSessionDelete = deletionDelegate::undo,
            modifier = modifier
        )
        return
    }
    if (destination == AppDestination.SessionSettingsLibrary) {
        SessionLibrarySettingsScreen(
            sessionTitle = sessionTitle,
            onBack = onBack,
            onSelectDestination = onSelectDestination,
            onPickSource = onPickSource,
            modifier = modifier
        )
        return
    }
    if (destination == AppDestination.SessionSettingsPersona) {
        FormalRoleGoalScreen(
            sessionTitle = sessionTitle,
            onBack = onBack,
            onSelectDestination = onSelectDestination,
            modifier = modifier
        )
        return
    }
    if (destination == AppDestination.SessionSettingsPersonalization) {
        FormalPersonalizationScreen(
            sessionTitle = sessionTitle,
            avatarVisible = activeSession?.perSessionAvatarVisible ?: true,
            avatarControlEnabled = activeSession != null && !sessionHomeState.actionInProgress,
            onAvatarVisibleChange = { visible ->
                sessionId?.let { sessionHomeViewModel.setAvatarVisible(it, visible) }
            },
            onBack = onBack,
            onSelectDestination = onSelectDestination,
            modifier = modifier
        )
        return
    }
    val surfaces = ReverseTutorDesign.surfaces

    Column(modifier = modifier.fillMaxSize()) {
        SessionSettingsTabs(destination, onSelectDestination)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = surfaces.home
        ) {
            when (destination) {
                AppDestination.SessionSettingsLibrary -> LibrarySettingsContent(onPickSource)
                AppDestination.SessionSettingsGraph -> GraphSettingsContent(onOpenBrain)
                AppDestination.SessionSettingsPersona -> PersonaSettingsContent()
                AppDestination.SessionSettingsPersonalization -> PersonalizationSettingsContent(sessionTitle)
                else -> LibrarySettingsContent(onPickSource)
            }
        }
    }
}

internal fun saveAppliedSessionSnapshot(
    persistence: NewSessionPersistence?,
    sessionId: String,
    snapshot: NewSessionConfiguration,
    onChanged: () -> Unit = {}
) {
    persistence?.saveSessionSnapshot(sessionId, snapshot)
    onChanged()
}

internal fun refreshSessionSettingsSources(
    coordinator: SessionSettingsCoordinator,
    sources: List<SessionSource>
) {
    coordinator.refreshSources(sources)
}

@Composable
private fun SessionSettingsTabs(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit
) {
    val tabs = listOf(
        AppDestination.SessionSettingsLibrary to "资料库",
        AppDestination.SessionSettingsGraph to "图谱",
        AppDestination.SessionSettingsPersona to "人格目标",
        AppDestination.SessionSettingsPersonalization to "个性化"
    )
    val selectedIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        containerColor = ReverseTutorDesign.surfaces.card,
        contentColor = MaterialTheme.colorScheme.primary,
        indicator = {},
        divider = {}
    ) {
        tabs.forEachIndexed { index, (destination, label) ->
            val isSelected = index == selectedIndex
            Tab(
                selected = isSelected,
                onClick = { onSelect(destination) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (isSelected) {
                            Spacer(Modifier.height(ReverseTutorDesign.spacing.space1))
                            Box(
                                Modifier
                                    .width(26.dp)
                                    .height(3.dp)
                                    .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ReverseTutorDesign.spacing.space5, vertical = ReverseTutorDesign.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space4),
        content = content
    )
}

@Composable
private fun LibrarySettingsContent(onPickSource: () -> Unit) = SettingsScroll {
    Text(
        text = "只管理当前会话引用的资料，避免把全局资料库做成入口噪音。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    RtCard {
        RtSectionHeader(
            title = "已启用资料",
            leadingIcon = RtIconKey.Folder
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "3",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(ReverseTutorDesign.spacing.space4))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "份资料正在参与回答",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "优先引用本会话上传、链接和图片。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RtButton(
                label = "＋ 补充资料",
                onClick = onPickSource,
                size = RtButtonSize.Small,
                tone = RtButtonTone.Secondary
            )
        }
    }

    RtCard {
        RtSectionHeader(
            title = "添加到会话",
            leadingIcon = RtIconKey.Add
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2)
        ) {
            listOf(RtIconKey.FileUpload to "文件", RtIconKey.Link to "链接", RtIconKey.Image to "图片").forEach { (icon, label) ->
                RtButton(
                    label = label,
                    onClick = onPickSource,
                    modifier = Modifier.weight(1f),
                    size = RtButtonSize.Small,
                    tone = RtButtonTone.Secondary,
                    leadingIcon = icon
                )
            }
        }
    }

    RtCard {
        RtSectionHeader(
            title = "当前资料",
            leadingIcon = RtIconKey.Folder
        )
        SourceRow(
            title = "宏观经济学教材节选",
            detail = "PDF · 42 页 · 已向量化",
            status = "已启用",
            tone = MaterialTheme.colorScheme.tertiary
        )
        RtDivider()
        SourceRow(
            title = "央行政策新闻摘录",
            detail = "网页 · 6 个段落 · 昨天导入",
            status = "引用中",
            tone = MaterialTheme.colorScheme.tertiary
        )
        RtDivider()
        SourceRow(
            title = "课堂板书图片",
            detail = "图片 · 识别 18 条笔记",
            status = "待校对",
            tone = MaterialTheme.colorScheme.secondary
        )
    }

    RtCard {
        RtSectionHeader(
            title = "注入策略",
            leadingIcon = RtIconKey.Settings
        )
        var retrieveFirst by remember { mutableStateOf(true) }
        var conflictPrompt by remember { mutableStateOf(true) }
        RtToggleRow(
            label = "回答前优先检索会话资料",
            checked = retrieveFirst,
            onCheckedChange = { retrieveFirst = it }
        )
        RtToggleRow(
            label = "资料冲突时提醒我确认来源",
            checked = conflictPrompt,
            onCheckedChange = { conflictPrompt = it }
        )
    }

    RtButton(
        label = "保存资料设置",
        onClick = {},
        size = RtButtonSize.Large,
        fillWidth = true,
        leadingIcon = RtIconKey.Check
    )
}

@Composable
private fun GraphSettingsContent(onOpenBrain: () -> Unit) = SettingsScroll {
    RtCard {
        RtSectionHeader(
            title = "本会话知识图谱",
            leadingIcon = RtIconKey.GraphicEq,
            action = {
                RtButton(
                    label = "局部视图",
                    onClick = {},
                    size = RtButtonSize.Small,
                    tone = RtButtonTone.Quiet
                )
            }
        )
        MiniGraph()
        RtButton(
            label = "查看全图",
            onClick = onOpenBrain,
            size = RtButtonSize.Medium,
            tone = RtButtonTone.Secondary,
            fillWidth = true
        )
    }

    RtCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Metric("12", "概念节点", MaterialTheme.colorScheme.primary)
            Metric("3", "待确认", MaterialTheme.colorScheme.secondary)
            Metric("2", "关系缺口", MaterialTheme.colorScheme.tertiary)
        }
    }

    RtCard {
        RtSectionHeader(
            title = "推荐复盘路径",
            leadingIcon = RtIconKey.Check
        )
        ActionRow(
            title = "财政政策如何影响利率水平",
            body = "建议从 IS-LM 曲线移动开始讲清因果链。",
            action = "开始检查",
            isPrimary = true
        )
        RtDivider()
        ActionRow(
            title = "货币政策与挤出效应的关系",
            body = "这条边缺少来源，建议补一段材料。",
            action = "补资料",
            isPrimary = false
        )
    }

    RtCard {
        RtSectionHeader(
            title = "近期沉淀",
            leadingIcon = RtIconKey.School
        )
        Text(
            text = "GDP 上升不一定代表福利同步提升",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "IS-LM 曲线在反推时容易忽略外生冲击。",
            style = MaterialTheme.typography.bodyMedium
        )
    }

    RtButton(
        label = "查看完整学习大脑",
        onClick = onOpenBrain,
        size = RtButtonSize.Large,
        fillWidth = true,
        leadingIcon = RtIconKey.GraphicEq
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PersonaSettingsContent() {
    var inquiry by remember { mutableFloatStateOf(0.72f) }
    var explanation by remember { mutableFloatStateOf(0.48f) }
    var autoSources by remember { mutableStateOf(true) }
    var allowPushback by remember { mutableStateOf(true) }
    var selectedStyle by remember { mutableStateOf(setOf("先提问", "分步骤讲")) }

    SettingsScroll {
        RtCard {
            RtSectionHeader(
                title = "当前人格",
                leadingIcon = RtIconKey.Psychology
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "耐心追问型导师",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "先判断你的理解，再用问题推动修正。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RtChip(
                    label = "启用中",
                    selected = true,
                    onClick = {}
                )
            }
        }

        RtCard {
            RtSectionHeader(
                title = "本会话目标",
                leadingIcon = RtIconKey.School
            )
            Surface(
                color = ReverseTutorDesign.surfaces.input,
                shape = RoundedCornerShape(ReverseTutorDesign.shapes.radiusMedium),
                border = androidx.compose.foundation.BorderStroke(1.dp, ReverseTutorDesign.surfaces.inputBorder)
            ) {
                Text(
                    text = "理解 GDP、财政政策与利率之间的关系，并能解释政策组合的取舍。",
                    modifier = Modifier.padding(ReverseTutorDesign.spacing.space4),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        RtCard {
            RtSectionHeader(
                title = "教学节奏",
                leadingIcon = RtIconKey.Psychology
            )
            RtSliderRow(
                label = "追问强度",
                valueLabel = "偏高",
                value = inquiry,
                onValueChange = { inquiry = it }
            )
            RtSliderRow(
                label = "讲解难度",
                valueLabel = "中等",
                value = explanation,
                onValueChange = { explanation = it }
            )
        }

        RtCard {
            RtSectionHeader(
                title = "回复风格预设",
                leadingIcon = RtIconKey.Check
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2),
                verticalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2)
            ) {
                listOf("先提问", "举例子", "考试模式", "分步骤讲", "少提示").forEach { label ->
                    RtChip(
                        label = label,
                        selected = selectedStyle.contains(label),
                        onClick = {
                            selectedStyle = if (selectedStyle.contains(label)) {
                                selectedStyle - label
                            } else {
                                selectedStyle + label
                            }
                        }
                    )
                }
            }
        }

        RtCard {
            RtToggleRow(
                label = "自动引用资料",
                checked = autoSources,
                onCheckedChange = { autoSources = it }
            )
            RtToggleRow(
                label = "允许不确定时反问",
                checked = allowPushback,
                onCheckedChange = { allowPushback = it }
            )
        }

        RtButton(
            label = "保存人格目标",
            onClick = {},
            size = RtButtonSize.Large,
            fillWidth = true,
            leadingIcon = RtIconKey.Check
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PersonalizationSettingsContent(sessionTitle: String) {
    var title by remember(sessionTitle) { mutableStateOf(sessionTitle) }
    var showSources by remember { mutableStateOf(true) }
    var showMemory by remember { mutableStateOf(true) }
    var selectedTheme by remember { mutableStateOf("默认") }
    var selectedTags by remember { mutableStateOf(setOf("经济学", "重点")) }

    SettingsScroll {
        RtCard {
            RtSectionHeader(
                title = "会话名称",
                leadingIcon = RtIconKey.Folder
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                RtButton(
                    label = "重命名",
                    onClick = {},
                    size = RtButtonSize.Small,
                    tone = RtButtonTone.Secondary
                )
            }
        }

        RtCard {
            RtSectionHeader(
                title = "聊天背景",
                leadingIcon = RtIconKey.Psychology
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2)) {
                listOf("默认" to Color(0xFFF3F4FB), "纸张" to Color(0xFFFFF6E2), "深色" to Color(0xFF202637)).forEach { (label, color) ->
                    val selected = selectedTheme == label
                    Surface(
                        onClick = { selectedTheme = label },
                        color = color,
                        shape = RoundedCornerShape(ReverseTutorDesign.shapes.radiusMedium),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else ReverseTutorDesign.surfaces.inputBorder
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = ReverseTutorDesign.spacing.space3),
                            textAlign = TextAlign.Center,
                            color = if (label == "深色") Color.White else ReverseTutorDesign.text.ink,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        RtCard {
            RtSectionHeader(
                title = "会话标签",
                leadingIcon = RtIconKey.Label
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2),
                verticalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2)
            ) {
                listOf("经济学", "重点", "考试准备", "IS-LM", "＋ 新增标签").forEach { label ->
                    val isAdd = label == "＋ 新增标签"
                    RtChip(
                        label = label,
                        selected = if (isAdd) false else selectedTags.contains(label),
                        onClick = {
                            if (!isAdd) {
                                selectedTags = if (selectedTags.contains(label)) {
                                    selectedTags - label
                                } else {
                                    selectedTags + label
                                }
                            }
                        }
                    )
                }
            }
        }

        RtCard {
            RtSectionHeader(
                title = "显示与引用",
                leadingIcon = RtIconKey.Settings
            )
            RtToggleRow(
                label = "显示资料引用",
                checked = showSources,
                onCheckedChange = { showSources = it }
            )
            RtToggleRow(
                label = "显示记忆标记",
                checked = showMemory,
                onCheckedChange = { showMemory = it }
            )
        }

        RtCard(variant = RtCardVariant.Outlined) {
            Text(
                text = "导出会话",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            RtDivider()
            Text(
                text = "清空本会话上下文",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        RtButton(
            label = "保存个性化设置",
            onClick = {},
            size = RtButtonSize.Large,
            fillWidth = true,
            leadingIcon = RtIconKey.Check
        )
    }
}

@Composable
private fun SourceRow(
    title: String,
    detail: String,
    status: String,
    tone: Color
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            color = tone.copy(alpha = 0.10f),
            contentColor = tone,
            shape = RoundedCornerShape(ReverseTutorDesign.shapes.radiusMedium),
            border = androidx.compose.foundation.BorderStroke(1.dp, tone.copy(alpha = 0.35f))
        ) {
            Text(
                text = status,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun MiniGraph() {
    val lineColor = ReverseTutorDesign.surfaces.inputBorder
    Box(Modifier.fillMaxWidth().height(145.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * .5f, size.height * .52f)
            listOf(
                Offset(size.width * .20f, size.height * .25f),
                Offset(size.width * .83f, size.height * .18f),
                Offset(size.width * .18f, size.height * .78f),
                Offset(size.width * .83f, size.height * .76f)
            ).forEach {
                drawLine(lineColor, center, it, 1.dp.toPx())
            }
        }
        GraphLabel("IS-LM", true, Modifier.align(Alignment.Center))
        GraphLabel("GDP", false, Modifier.align(Alignment.TopStart).padding(start = 36.dp, top = 8.dp))
        GraphLabel("财政政策", false, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 4.dp))
        GraphLabel("利率", false, Modifier.align(Alignment.BottomStart).padding(start = 62.dp, bottom = 8.dp))
        GraphLabel("挤出效应", false, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun GraphLabel(text: String, selected: Boolean, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = if (selected) MaterialTheme.colorScheme.primary else ReverseTutorDesign.surfaces.input,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else ReverseTutorDesign.text.ink,
        shape = RoundedCornerShape(ReverseTutorDesign.shapes.radiusMedium),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, ReverseTutorDesign.surfaces.inputBorder)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    body: String,
    action: String,
    isPrimary: Boolean
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ReverseTutorDesign.spacing.space2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RtButton(
            label = action,
            onClick = {},
            size = RtButtonSize.Small,
            tone = if (isPrimary) RtButtonTone.Primary else RtButtonTone.Secondary
        )
    }
}

@Composable
private fun RtDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ReverseTutorDesign.surfaces.inputBorder)
    )
}
