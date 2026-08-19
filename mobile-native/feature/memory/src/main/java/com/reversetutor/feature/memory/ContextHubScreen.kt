package com.reversetutor.feature.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.data.graph.GraphRepository
import com.reversetutor.core.data.graph.GraphNodeEditInput
import com.reversetutor.core.data.memory.MemoryRepository
import kotlinx.coroutines.launch

@Composable
fun ContextHubRoute(
    memoryRepository: MemoryRepository,
    graphRepository: GraphRepository,
    sessionId: String?,
    sessionTitle: String?,
    onOpenChat: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenChatEvidence: (String) -> Unit = { onOpenChat() },
    onOpenSourceEvidence: (String) -> Unit = { onOpenSources() },
    onOpenSettings: () -> Unit,
    onOpenGlobalGraph: () -> Unit = {},
    graphScope: GraphScope = GraphScope.Session,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var state by remember(sessionId, sessionTitle) {
        mutableStateOf(
            ContextHubUiState.fromActiveSession(
                sessionId = sessionId,
                sessionTitle = sessionTitle
            )
        )
    }
    var refreshKey by remember(sessionId, sessionTitle, graphScope) { mutableIntStateOf(0) }

    fun saveGraphNode(
        node: GraphLayoutNode,
        label: String,
        status: com.reversetutor.core.model.GraphNodeStatus = node.status
    ) {
        scope.launch {
            graphRepository.updateNode(
                input = GraphNodeEditInput(
                    id = node.id,
                    label = label,
                    kind = node.kind,
                    status = status,
                    sourceMemoryId = node.sourceMemoryId
                ),
                nowEpochMillis = System.currentTimeMillis()
            )
            refreshKey += 1
        }
    }

    LaunchedEffect(sessionId, sessionTitle, graphScope, refreshKey) {
        val memorySnapshot = memoryRepository.snapshot()
        val graphSnapshot = graphRepository.snapshot()
        val graphState = KnowledgeGraphUiState.from(
            nodes = graphSnapshot.nodes,
            edges = graphSnapshot.edges,
            scope = graphScope,
            memoryItems = memorySnapshot.items
        )
        state = ContextHubUiState.fromMemorySnapshot(
            sessionId = sessionId,
            sessionTitle = sessionTitle,
            graphState = graphState,
            snapshot = ContextMemorySnapshot(
                anchors = memorySnapshot.anchors.map {
                    ContextMemoryEntry(
                        id = it.id,
                        title = it.title,
                        body = it.body,
                        sourceMessageId = it.sourceMessageId,
                        sourceId = it.sourceId
                    )
                },
                notes = memorySnapshot.notes.map {
                    ContextMemoryEntry(
                        id = it.id,
                        title = it.title,
                        body = it.body,
                        sourceMessageId = it.sourceMessageId
                    )
                },
                errors = memorySnapshot.errors.map {
                    ContextErrorEntry(
                        id = it.id,
                        title = it.title,
                        detail = it.detail,
                        sourceMessageId = it.sourceMessageId,
                        resolved = it.resolved
                    )
                }
            )
        )
    }

    ContextHubScreen(
        state = state,
        onOpenChat = onOpenChat,
        onOpenSources = onOpenSources,
        onOpenSettings = onOpenSettings,
        onOpenGlobalGraph = onOpenGlobalGraph,
        onGraphNodeLabelSave = { node, label ->
            saveGraphNode(node = node, label = label)
        },
        onGraphNodeReviewAction = { node, action ->
            saveGraphNode(node = node, label = node.label, status = action.targetStatus)
        },
        onOpenGraphChatEvidence = onOpenChatEvidence,
        onOpenGraphSourceEvidence = onOpenSourceEvidence,
        onGraphRetry = { refreshKey += 1 },
        onOpenChatEvidence = onOpenChatEvidence,
        onOpenSourceEvidence = onOpenSourceEvidence,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun GlobalGraphRoute(
    graphRepository: GraphRepository,
    memoryRepository: MemoryRepository,
    onOpenGraphChatEvidence: (String) -> Unit = {},
    onOpenGraphSourceEvidence: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    onBack: () -> Unit = {},
    canvasModeActive: Boolean = false,
    onCanvasModeChange: (Boolean) -> Unit = {},
    onGraphInteractionChanged: (Boolean) -> Unit = {},
    onWorkspaceChromeObscuredChanged: (Boolean) -> Unit = {},
    onPageLocalActionSurfaceChanged: (Boolean, () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(KnowledgeGraphUiState.loading(scope = GraphScope.Global))
    }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedNodeId) {
        onWorkspaceChromeObscuredChanged(selectedNodeId != null)
        onPageLocalActionSurfaceChanged(
            selectedNodeId != null,
            { selectedNodeId = null }
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            onWorkspaceChromeObscuredChanged(false)
            onPageLocalActionSurfaceChanged(false) {}
        }
    }

    fun saveGraphNode(
        node: GraphLayoutNode,
        label: String,
        status: com.reversetutor.core.model.GraphNodeStatus = node.status
    ) {
        scope.launch {
            graphRepository.updateNode(
                input = GraphNodeEditInput(
                    id = node.id,
                    label = label,
                    kind = node.kind,
                    status = status,
                    sourceMemoryId = node.sourceMemoryId
                ),
                nowEpochMillis = System.currentTimeMillis()
            )
            refreshKey += 1
        }
    }

    LaunchedEffect(refreshKey) {
        state = KnowledgeGraphUiState.loading(GraphScope.Global)
        state = try {
            val snapshot = graphRepository.snapshot()
            val memorySnapshot = memoryRepository.snapshot()
            KnowledgeGraphUiState.from(
                nodes = snapshot.nodes,
                edges = snapshot.edges,
                memoryItems = memorySnapshot.items,
                selectedNodeId = selectedNodeId,
                scope = GraphScope.Global
            )
        } catch (_: Exception) {
            KnowledgeGraphUiState.error(GraphScope.Global)
        }
    }

    val selectedState = state.withSelection(selectedNodeId)
    FormalGlobalKnowledgeGraphScreen(
        state = selectedState,
        onSelectedNodeChange = { selectedNodeId = it },
        modifier = modifier,
        title = "全局图谱",
        subtitle = "跨会话知识结构",
        onBack = {
            if (canvasModeActive) onCanvasModeChange(false) else onBack()
        },
        onMore = onOpenSettings,
        onOpenChatEvidence = { node ->
            node.sourceMessageId?.let(onOpenGraphChatEvidence)
        },
        onOpenSourceEvidence = { node ->
            node.sourceId?.let(onOpenGraphSourceEvidence)
        },
        canvasModeActive = canvasModeActive,
        onCanvasModeChange = onCanvasModeChange,
        onRetry = { refreshKey += 1 },
        onGraphInteractionChanged = onGraphInteractionChanged
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ContextHubScreen(
    state: ContextHubUiState,
    onOpenChat: () -> Unit,
    onOpenSources: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGlobalGraph: () -> Unit = {},
    onGraphNodeLabelSave: (GraphLayoutNode, String) -> Unit = { _, _ -> },
    onGraphNodeReviewAction: (GraphLayoutNode, GraphNodeReviewAction) -> Unit = { _, _ -> },
    onOpenGraphChatEvidence: (String) -> Unit = {},
    onOpenGraphSourceEvidence: (String) -> Unit = {},
    onGraphRetry: () -> Unit = {},
    onOpenChatEvidence: (String) -> Unit = {},
    onOpenSourceEvidence: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSection by remember(state.sessionId) {
        mutableStateOf(ContextHubSection.Overview)
    }
    var selectedGraphNodeId by remember(state.sessionId, state.graphState.scope) {
        mutableStateOf<String?>(null)
    }
    val selectedState = state.sections.firstOrNull { it.section == selectedSection }
        ?: state.sections.first()
    val selectedGraphState = state.graphState.withSelection(selectedGraphNodeId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = "学习脉络",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "当前会话：${state.sessionTitle}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.sessionStatusLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        ContextHubNotice(hasActiveSession = state.hasActiveSession)
        Spacer(modifier = Modifier.height(18.dp))
        ContextHubOverview(lines = state.overviewLines)
        Spacer(modifier = Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.sections.forEach { item ->
                FilterChip(
                    selected = item.section == selectedSection,
                    onClick = { selectedSection = item.section },
                    label = { Text(item.section.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (selectedSection == ContextHubSection.Graph) {
            KnowledgeGraphPanel(
                state = selectedGraphState,
                onSelectedNodeChange = { selectedGraphNodeId = it },
                onNodeLabelSave = onGraphNodeLabelSave,
                onNodeReviewAction = onGraphNodeReviewAction,
                onOpenChatEvidence = { node ->
                    node.sourceMessageId?.let(onOpenGraphChatEvidence)
                },
                onOpenSourceEvidence = { node ->
                    node.sourceId?.let(onOpenGraphSourceEvidence)
                },
                onCreateEvidence = onOpenChat,
                onRetry = onGraphRetry
            )
        } else {
            ContextHubSectionPanel(
                state = selectedState,
                onOpenChatEvidence = onOpenChatEvidence,
                onOpenSourceEvidence = onOpenSourceEvidence
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenChat,
                modifier = Modifier
                    .testTag("context-return-chat")
                    .heightIn(min = 48.dp)
            ) {
                Text("返回聊天")
            }
            TextButton(
                onClick = onOpenGlobalGraph,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("全局图谱")
            }
            TextButton(
                onClick = onOpenSources,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("资料")
            }
            TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("设置")
            }
        }
    }
}

@Composable
private fun ContextHubNotice(hasActiveSession: Boolean) {
    val container = if (hasActiveSession) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (hasActiveSession) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = if (hasActiveSession) {
                "这个页面已连接到当前聊天会话。空状态和待启用区域会随着 Memory 与图谱数据逐步填充。"
            } else {
                "请先打开一个会话，再查看图谱、锚点、随笔、错因和会话设置。"
            },
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ContextHubOverview(lines: List<String>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "概览",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            lines.forEach { line ->
                Text(text = line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ContextHubSectionPanel(
    state: ContextHubSectionState,
    onOpenChatEvidence: (String) -> Unit,
    onOpenSourceEvidence: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("context-section-${state.section.name.lowercase()}"),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.section.label,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = state.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = state.statusLabel,
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(text = state.body, style = MaterialTheme.typography.bodyMedium)
            if (state.evidenceItems.isNotEmpty()) {
                ContextHubEvidenceList(
                    items = state.evidenceItems,
                    onOpenChatEvidence = onOpenChatEvidence,
                    onOpenSourceEvidence = onOpenSourceEvidence
                )
            }
            Text(
                text = "下一步",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            state.nextActions.forEach { action ->
                Text(text = action, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ContextHubEvidenceList(
    items: List<ContextHubEvidenceItem>,
    onOpenChatEvidence: (String) -> Unit,
    onOpenSourceEvidence: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { item ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        item.statusLabel?.let { label ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = label,
                                    modifier = Modifier
                                        .heightIn(min = 32.dp)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                    if (item.body.isNotBlank()) {
                        Text(
                            text = item.body,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (item.evidenceAvailabilityLabel.isNotBlank()) {
                        Text(
                            text = item.evidenceAvailabilityLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(
                                "context-evidence-availability-${item.id}"
                            )
                        )
                    }
                    if (item.actions.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item.actions.forEach { action ->
                                val isChat =
                                    action.destination == ContextEvidenceDestination.Chat
                                val tag = if (isChat) {
                                    "context-evidence-chat-${action.targetId}"
                                } else {
                                    "context-evidence-source-${action.targetId}"
                                }
                                TextButton(
                                    onClick = {
                                        if (isChat) {
                                            onOpenChatEvidence(action.targetId)
                                        } else {
                                            onOpenSourceEvidence(action.targetId)
                                        }
                                    },
                                    modifier = Modifier
                                        .testTag(tag)
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text(action.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
