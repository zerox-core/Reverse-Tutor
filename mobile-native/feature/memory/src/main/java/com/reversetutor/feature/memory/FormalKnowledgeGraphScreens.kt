package com.reversetutor.feature.memory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.model.GraphNodeKind

data class FormalBranchGraphHeader(
    val title: String,
    val subtitle: String,
    val contextTitle: String = "",
    val contextSubtitle: String = "",
    val progressLabel: String = ""
)

@Composable
fun FormalBranchGraphScreen(
    header: FormalBranchGraphHeader,
    state: KnowledgeGraphUiState,
    onBack: () -> Unit,
    onSelectedNodeChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
    onFilter: (() -> Unit)? = null,
    onEditNode: ((GraphLayoutNode) -> Unit)? = null,
    onOpenChatEvidence: ((GraphLayoutNode) -> Unit)? = null,
    onOpenSourceEvidence: ((GraphLayoutNode) -> Unit)? = null,
    onGraphInteractionChanged: (Boolean) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FormalGraphBackground)
            .testTag("formal-branch-graph-screen")
    ) {
        Column(Modifier.fillMaxSize()) {
            BranchGraphHeader(
                header = header,
                state = state,
                onBack = onBack,
                onMore = onMore
            )
            Box(modifier = Modifier.weight(1f)) {
                FormalGraphCanvas(
                    state = state,
                    showLockedNodes = false,
                    onSelectedNodeChange = onSelectedNodeChange,
                    initialScale = 1f,
                    showToolbar = true,
                    onFilterClick = onFilter,
                    onInteractionChanged = onGraphInteractionChanged,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GraphInfoChip("已显示 ${state.nodes.size} / ${state.allNodeCount}")
                    GraphInfoChip(
                        label = "当前分支",
                        containerColor = FormalColors.PrimarySoft,
                        contentColor = FormalColors.Primary
                    )
                }
            }
        }
        state.selectedNode?.let { node ->
            GraphScreenScrim(onClick = { onSelectedNodeChange(null) })
            FormalGraphNodeSheet(
                node = node,
                relations = state.relatedEdges(node.id),
                onDismiss = { onSelectedNodeChange(null) },
                onEditNode = onEditNode,
                onOpenChatEvidence = onOpenChatEvidence,
                onOpenSourceEvidence = onOpenSourceEvidence,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun FormalGlobalKnowledgeGraphScreen(
    state: KnowledgeGraphUiState,
    onSelectedNodeChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "知识图谱",
    subtitle: String = "全局",
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onEditNode: ((GraphLayoutNode) -> Unit)? = null,
    onOpenChatEvidence: ((GraphLayoutNode) -> Unit)? = null,
    onOpenSourceEvidence: ((GraphLayoutNode) -> Unit)? = null,
    canvasModeActive: Boolean = false,
    onCanvasModeChange: (Boolean) -> Unit = {},
    onRetry: (() -> Unit)? = null,
    onGraphInteractionChanged: (Boolean) -> Unit = {},
    onViewportChanged: (GraphViewportState) -> Unit = {},
    onNodePositionChanged: (String, GraphPoint) -> Unit = { _, _ -> }
) {
    var showGraphHelp by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FormalGraphBackground)
            .testTag("formal-global-graph-screen")
    ) {
        Column(Modifier.fillMaxSize()) {
            FormalGraphTopBar(
                title = title,
                subtitle = subtitle,
                inlineSubtitle = true,
                onBack = onBack,
                onBackgroundClick = if (canvasModeActive) {
                    { onCanvasModeChange(false) }
                } else {
                    null
                },
                onSearch = if (canvasModeActive) null else onSearch,
                onMore = onMore
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                FormalGraphCanvas(
                    state = state,
                    showLockedNodes = false,
                    onSelectedNodeChange = onSelectedNodeChange,
                    interactionEnabled = canvasModeActive,
                    onRequestInteraction = { onCanvasModeChange(true) },
                    onSearch = if (canvasModeActive) {
                        {
                            searchOpen = !searchOpen
                            onSearch?.invoke()
                        }
                    } else {
                        null
                    },
                    onHelpToggle = { showGraphHelp = !showGraphHelp },
                    toolbarAlignment = Alignment.TopEnd,
                    toolbarModifier = Modifier.padding(top = 16.dp, end = 16.dp),
                    showToolbar = canvasModeActive && state.allNodeCount > 0,
                    onInteractionChanged = onGraphInteractionChanged,
                    onViewportChanged = onViewportChanged,
                    onNodePositionChanged = onNodePositionChanged,
                    modifier = Modifier.fillMaxSize()
                )
                if (state.status in setOf(
                        GraphRenderStatus.Loading,
                        GraphRenderStatus.Empty,
                        GraphRenderStatus.Error
                    )
                ) {
                FormalGraphStateOverlay(
                        state = state,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        if (canvasModeActive) {
            GraphSourceLegend(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp)
            )
        }
        if (showGraphHelp && canvasModeActive) {
            GraphSourceHelpPanel(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            )
        }
        if (searchOpen && canvasModeActive) {
            GraphSourceSearchPanel(
                query = searchQuery,
                nodes = state.allNodes,
                onQueryChange = { searchQuery = it },
                onSelectNode = { nodeId ->
                    onSelectedNodeChange(nodeId)
                    searchQuery = ""
                    searchOpen = false
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp)
            )
        }
        if (canvasModeActive) {
            Surface(
                onClick = { onCanvasModeChange(false) },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .heightIn(min = 44.dp)
                    .testTag("graph-canvas-mode-exit"),
                color = FormalColors.Primary,
                contentColor = Color.White,
                shape = RoundedCornerShape(FormalShapes.PillRadius),
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "画布模式 · 退出",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.White,
                        fontSize = LocalFormalTypeScale.current.size(11f),
                        lineHeight = LocalFormalTypeScale.current.size(16f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
                    .heightIn(min = 44.dp)
                    .testTag("graph-page-mode-indicator"),
                color = FormalColors.Surface,
                contentColor = FormalColors.Muted,
                shape = RoundedCornerShape(FormalShapes.PillRadius),
                border = BorderStroke(1.dp, FormalColors.Border),
                shadowElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "页面模式 · 点击画布进入",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = FormalColors.Muted,
                        fontSize = LocalFormalTypeScale.current.size(11f),
                        lineHeight = LocalFormalTypeScale.current.size(16f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        state.selectedNode?.let { node ->
            GraphScreenScrim(onClick = { onSelectedNodeChange(null) })
            FormalGraphNodeSheet(
                node = node,
                relations = state.relatedEdges(node.id),
                onDismiss = { onSelectedNodeChange(null) },
                onEditNode = onEditNode,
                onOpenChatEvidence = onOpenChatEvidence,
                onOpenSourceEvidence = onOpenSourceEvidence,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun FormalSessionWorldTreeScreen(
    title: String,
    state: KnowledgeGraphUiState,
    showLockedNodes: Boolean,
    onShowLockedNodesChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSelectedNodeChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "当前会话世界树",
    onSearch: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    onGraphInteractionChanged: (Boolean) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FormalGraphBackground)
            .testTag("formal-session-world-tree-screen")
    ) {
        Column(Modifier.fillMaxSize()) {
            FormalGraphTopBar(
                title = title,
                subtitle = subtitle,
                onBack = onBack,
                onSearch = onSearch,
                onMore = onMore
            )
            GraphLayerSelector(
                selectedLayer = if (showLockedNodes) GraphLayer.FullRoute else GraphLayer.CurrentProgress,
                onLayerSelected = { layer -> onShowLockedNodesChange(layer.showLockedNodes) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                FormalGraphCanvas(
                    state = state,
                    showLockedNodes = showLockedNodes,
                    onSelectedNodeChange = onSelectedNodeChange,
                    expandedSessionRoot = true,
                    showToolbar = state.allNodeCount > 0,
                    onInteractionChanged = onGraphInteractionChanged,
                    modifier = Modifier.fillMaxSize()
                )
                if (state.status in setOf(
                        GraphRenderStatus.Loading,
                        GraphRenderStatus.Empty,
                        GraphRenderStatus.Error
                    )
                ) {
                    FormalGraphStateOverlay(
                        state = state,
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        state.selectedNode?.takeIf { it.isLocked }?.let { node ->
            GraphScreenScrim(onClick = { onSelectedNodeChange(null) })
            LockedNodeSheet(
                node = node,
                relations = state.relatedEdges(node.id),
                onDismiss = { onSelectedNodeChange(null) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun GraphLayerSelector(
    selectedLayer: GraphLayer,
    onLayerSelected: (GraphLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        graphLayersFor(GraphScope.Session).forEach { layer ->
            val selected = layer == selectedLayer
            Surface(
                onClick = { onLayerSelected(layer) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .testTag("graph-layer-${layer.name}"),
                color = if (selected) FormalColors.PrimarySoft else Color.White,
                contentColor = if (selected) FormalColors.Primary else FormalColors.Muted,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    1.dp,
                    if (selected) FormalColors.Primary.copy(alpha = 0.40f) else FormalColors.Border
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = layer.label,
                        fontSize = LocalFormalTypeScale.current.size(11f),
                        lineHeight = LocalFormalTypeScale.current.size(16f),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun BranchGraphHeader(
    header: FormalBranchGraphHeader,
    state: KnowledgeGraphUiState,
    onBack: () -> Unit,
    onMore: (() -> Unit)?
) {
    val type = LocalFormalTypeScale.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF2FAFBFE))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormalGraphIconButton(GraphNavGlyph.Back, "返回", onBack, circled = true)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = header.title.ifBlank { state.title },
                    color = FormalColors.Ink,
                    fontSize = type.size(14f),
                    lineHeight = type.size(21f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = header.subtitle,
                    color = FormalColors.Muted,
                    fontSize = type.size(9f),
                    lineHeight = type.size(14f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onMore != null) {
                FormalGraphIconButton(GraphNavGlyph.More, "更多", onMore, circled = true)
            }
        }
        if (header.contextTitle.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 16.dp),
                color = Color(0xF2FAFBFE),
                contentColor = FormalColors.Ink,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0x1F576B94))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(FormalColors.PrimarySoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("学", color = FormalColors.Primary, fontSize = type.size(10f), fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            header.contextTitle,
                            fontSize = type.size(10f),
                            lineHeight = type.size(15f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (header.contextSubtitle.isNotBlank()) {
                            Text(
                                header.contextSubtitle,
                                color = FormalColors.Muted,
                                fontSize = type.size(8f),
                                lineHeight = type.size(13f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (header.progressLabel.isNotBlank()) {
                        Text(
                            header.progressLabel,
                            color = FormalColors.Primary,
                            fontSize = type.size(9f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FormalGraphTopBar(
    title: String,
    subtitle: String,
    inlineSubtitle: Boolean = false,
    onBack: (() -> Unit)?,
    onBackgroundClick: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    showLockedNodes: Boolean? = null,
    onShowLockedNodesChange: ((Boolean) -> Unit)? = null,
    onMore: (() -> Unit)? = null
) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xF2FBFCFF))
            .then(
                onBackgroundClick?.let { onClick ->
                    Modifier.clickable(onClick = onClick)
                } ?: Modifier
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            FormalGraphIconButton(GraphNavGlyph.Back, "返回", onBack)
        } else {
            Spacer(Modifier.size(44.dp))
        }
        if (inlineSubtitle) {
            Row(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color(0xFF0A0F1A),
                    fontSize = type.size(18f),
                    lineHeight = type.size(26f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF7A8596),
                    fontSize = type.size(11f),
                    lineHeight = type.size(16f),
                    maxLines = 1
                )
            }
        } else {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = Color(0xFF0A0F1A),
                    fontSize = type.size(16f),
                    lineHeight = type.size(23f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF7A8596),
                    fontSize = type.size(10f),
                    lineHeight = type.size(15f),
                    maxLines = 1
                )
            }
        }
        if (onSearch != null) {
            FormalGraphIconButton(GraphNavGlyph.Search, "搜索", onSearch)
        }
        if (showLockedNodes != null && onShowLockedNodesChange != null) {
            FormalGraphIconButton(
                glyph = if (showLockedNodes) GraphNavGlyph.Eye else GraphNavGlyph.EyeOff,
                contentDescription = if (showLockedNodes) "隐藏未解锁节点" else "显示未解锁节点",
                onClick = { onShowLockedNodesChange(!showLockedNodes) }
            )
        }
        if (onMore != null) {
            FormalGraphIconButton(GraphNavGlyph.More, "更多", onMore)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x52C2CFE0))
    )
}

private enum class GraphNavGlyph { Back, Search, Eye, EyeOff, More, Close }

@Composable
private fun FormalGraphIconButton(
    glyph: GraphNavGlyph,
    contentDescription: String,
    onClick: () -> Unit,
    circled: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(
                if (circled) Color(0xEBFAFBFE) else Color.Transparent,
                CircleShape
            )
            .then(
                if (circled) Modifier else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            Modifier
                .size(24.dp)
                .testTag("graph-nav-${contentDescription}")
        ) {
            val color = Color(0xFF172033)
            val stroke = 1.8.dp.toPx()
            when (glyph) {
                GraphNavGlyph.Back -> {
                    drawLine(color, Offset(size.width * 0.66f, size.height * 0.22f), Offset(size.width * 0.38f, center.y), stroke, StrokeCap.Round)
                    drawLine(color, Offset(size.width * 0.38f, center.y), Offset(size.width * 0.66f, size.height * 0.78f), stroke, StrokeCap.Round)
                }
                GraphNavGlyph.Search -> {
                    drawCircle(color, size.minDimension * 0.28f, Offset(size.width * 0.43f, size.height * 0.43f), style = Stroke(stroke))
                    drawLine(color, Offset(size.width * 0.64f, size.height * 0.64f), Offset(size.width * 0.84f, size.height * 0.84f), stroke, StrokeCap.Round)
                }
                GraphNavGlyph.Eye,
                GraphNavGlyph.EyeOff -> {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.12f, center.y)
                        quadraticBezierTo(center.x, size.height * 0.16f, size.width * 0.88f, center.y)
                        quadraticBezierTo(center.x, size.height * 0.84f, size.width * 0.12f, center.y)
                    }
                    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawCircle(color, size.minDimension * 0.12f, center, style = Stroke(stroke))
                    if (glyph == GraphNavGlyph.EyeOff) {
                        drawLine(
                            Color(0xFF172033),
                            Offset(size.width * 0.20f, size.height * 0.82f),
                            Offset(size.width * 0.80f, size.height * 0.18f),
                            stroke + 0.6.dp.toPx(),
                            StrokeCap.Round
                        )
                    }
                }
                GraphNavGlyph.More -> {
                    listOf(0.28f, 0.50f, 0.72f).forEach { x ->
                        drawCircle(color, 1.6.dp.toPx(), Offset(size.width * x, center.y))
                    }
                }
                GraphNavGlyph.Close -> {
                    drawLine(color, Offset(size.width * 0.28f, size.height * 0.28f), Offset(size.width * 0.72f, size.height * 0.72f), stroke, StrokeCap.Round)
                    drawLine(color, Offset(size.width * 0.72f, size.height * 0.28f), Offset(size.width * 0.28f, size.height * 0.72f), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun GraphInfoChip(
    label: String,
    containerColor: Color = Color(0xE6FAFBFE),
    contentColor: Color = Color(0xFF576685)
) {
    Surface(
        modifier = Modifier.height(28.dp),
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = LocalFormalTypeScale.current.size(9f), fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun GraphSourceLegend(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.testTag("graph-source-legend"),
        color = Color(0xE6F7F8FA),
        contentColor = Color(0xFF64748B),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "知识图谱",
                fontSize = LocalFormalTypeScale.current.size(9f),
                fontWeight = FontWeight.Medium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphSourceLegendItem(GraphNodeKind.Concept, "核心概念")
                GraphSourceLegendItem(GraphNodeKind.Other, "知识领域")
                GraphSourceLegendItem(GraphNodeKind.Session, "技术项目")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GraphSourceLegendItem(GraphNodeKind.Source, "资源设施")
                GraphSourceLegendItem(GraphNodeKind.Person, "知识图谱")
                GraphSourceLegendItem(GraphNodeKind.Requirement, "产品设计")
            }
        }
    }
}

@Composable
private fun GraphSourceLegendItem(kind: GraphNodeKind, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(Color(graphKindFillArgb(kind)), CircleShape)
        )
        Text(label, fontSize = LocalFormalTypeScale.current.size(8f))
    }
}

@Composable
private fun GraphSourceHelpPanel(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .width(260.dp)
            .testTag("graph-source-help"),
        color = Color(0xF2FFFFFF),
        contentColor = Color(0xFF475569),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "交互指南",
                color = Color(0xFF1E293B),
                fontSize = LocalFormalTypeScale.current.size(11f),
                fontWeight = FontWeight.SemiBold
            )
            GraphSourceHelpRow("双指缩放", "围绕触点缩放视图")
            GraphSourceHelpRow("拖拽空白", "平移画布")
            GraphSourceHelpRow("长按节点", "移动节点")
            GraphSourceHelpRow("点击节点", "选中并查看详情")
            GraphSourceHelpRow("点击空白", "取消选中")
        }
    }
}

@Composable
private fun GraphSourceHelpRow(label: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            modifier = Modifier.width(58.dp),
            color = Color(0xFF94A3B8),
            fontSize = LocalFormalTypeScale.current.size(8f)
        )
        Text(description, fontSize = LocalFormalTypeScale.current.size(8f))
    }
}

@Composable
private fun GraphSourceSearchPanel(
    query: String,
    nodes: List<GraphLayoutNode>,
    onQueryChange: (String) -> Unit,
    onSelectNode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val matches = remember(query, nodes) {
        val normalized = query.trim()
        if (normalized.isEmpty()) emptyList()
        else nodes.filter { node ->
            node.label.contains(normalized, ignoreCase = true) ||
                node.id.contains(normalized, ignoreCase = true)
        }.take(5)
    }
    Surface(
        modifier = modifier
            .width(288.dp)
            .testTag("graph-search-panel"),
        color = Color(0xF2FFFFFF),
        contentColor = Color(0xFF475569),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("graph-search-input"),
                singleLine = true,
                placeholder = { Text("搜索节点…", fontSize = LocalFormalTypeScale.current.size(10f)) },
                label = { Text("搜索", fontSize = LocalFormalTypeScale.current.size(9f)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            when {
                query.isNotBlank() && matches.isEmpty() -> {
                    Text(
                        text = "未找到匹配的节点",
                        modifier = Modifier.testTag("graph-search-empty"),
                        color = Color(0xFF94A3B8),
                        fontSize = LocalFormalTypeScale.current.size(9f)
                    )
                }
                matches.isNotEmpty() -> {
                    matches.forEach { node ->
                        Surface(
                            onClick = { onSelectNode(node.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("graph-search-result-${node.id}"),
                            color = Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(graphKindFillArgb(node.kind)), CircleShape)
                                )
                                Column {
                                    Text(
                                        text = node.label,
                                        color = Color(0xFF1E293B),
                                        fontSize = LocalFormalTypeScale.current.size(10f),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = node.kindLabel,
                                        color = Color(0xFF94A3B8),
                                        fontSize = LocalFormalTypeScale.current.size(8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GraphScreenScrim(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x20EAF0F8))
            .clickable(onClick = onClick)
    )
}

@Composable
private fun FormalGraphNodeSheet(
    node: GraphLayoutNode,
    relations: List<GraphLayoutEdge>,
    onDismiss: () -> Unit,
    onEditNode: ((GraphLayoutNode) -> Unit)?,
    onOpenChatEvidence: ((GraphLayoutNode) -> Unit)?,
    onOpenSourceEvidence: ((GraphLayoutNode) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(364.dp)
            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = Color(0xFFFBFCFF),
        contentColor = FormalColors.Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .background(Color(0xA6A8B5C7), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 10.dp, top = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            node.label,
                            color = Color(0xFF121827),
                            fontSize = type.size(19f),
                            lineHeight = type.size(26f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(10.dp))
                        NodeKindChip(node.kindLabel)
                    }
                    Text(
                        text = node.evidenceBody?.takeIf { it.isNotBlank() }
                            ?: "${node.statusLabel} · ${relations.size} 条关联",
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFF53627A),
                        fontSize = type.size(10f),
                        lineHeight = type.size(17f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FormalGraphIconButton(GraphNavGlyph.Close, "关闭详情", onDismiss)
            }
            GraphSheetDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetLabel("所属领域")
                Spacer(Modifier.width(18.dp))
                NodeKindChip(node.kindLabel)
                Spacer(Modifier.width(8.dp))
                NodeKindChip(node.statusLabel, Color(0xFFFFF1D8), Color(0xFF9A6B1F))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetLabel("关联关系")
                Spacer(Modifier.width(18.dp))
                Text(
                    text = if (relations.isEmpty()) "暂无关联" else "${relations.size} 条",
                    color = FormalColors.Primary,
                    fontSize = type.size(10f),
                    fontWeight = FontWeight.Medium
                )
            }
            val importance = graphDisplayImportance(node, relations.size)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .testTag("graph-node-importance"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SheetLabel("重要度")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(Color(0xFFE8EDF6), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(importance / 100f)
                            .fillMaxHeight()
                            .background(Color(graphKindFillArgb(node.kind)), RoundedCornerShape(3.dp))
                    )
                }
                Text(
                    text = importance.toString(),
                    color = FormalColors.Muted,
                    fontSize = type.size(9f)
                )
            }
            if (!node.evidenceTitle.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    color = Color(0xFFF7F9FD),
                    contentColor = Color(0xFF3E4A5E),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, FormalColors.Border)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text("引用来源", color = FormalColors.Ink, fontSize = type.size(11f), fontWeight = FontWeight.Medium)
                        Text(
                            node.evidenceTitle.orEmpty(),
                            color = FormalColors.Muted,
                            fontSize = type.size(9f),
                            lineHeight = type.size(15f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(58.dp))
            }
            Spacer(Modifier.weight(1f))
            GraphSheetDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val evidenceAction = when {
                    node.sourceMessageId != null && onOpenChatEvidence != null -> onOpenChatEvidence
                    node.sourceId != null && onOpenSourceEvidence != null -> onOpenSourceEvidence
                    else -> null
                }
                if (evidenceAction != null) {
                    TextButton(
                        onClick = { evidenceAction(node) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, FormalColors.Border)
                    ) {
                        Text("查看引用", color = Color(0xFF40506A), fontSize = type.size(11f))
                    }
                }
                if (onEditNode != null) {
                    Button(
                        onClick = { onEditNode(node) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(FormalShapes.CardRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text("编辑节点", fontSize = type.size(11f), fontWeight = FontWeight.Medium)
                    }
                }
                if (evidenceAction == null && onEditNode == null) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(FormalShapes.CardRadius),
                        colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary)
                    ) {
                        Text("完成", fontSize = type.size(11f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedNodeSheet(
    node: GraphLayoutNode,
    relations: List<GraphLayoutEdge>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(324.dp)
            .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        color = Color(0xFFFBFCFF),
        contentColor = FormalColors.Ink,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .background(Color(0xA6A8B5C7), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 10.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    node.label,
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF121827),
                    fontSize = type.size(19f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                NodeKindChip("尚未解锁", Color(0xFFE9EEF5), Color(0xFF758197))
                FormalGraphIconButton(GraphNavGlyph.Close, "关闭未解锁详情", onDismiss)
            }
            GraphSheetDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    LockedDetailRow("所属阶段", "第 ${node.depth.coerceAtLeast(1)} 阶段")
                    LockedDetailRow("当前状态", node.statusLabel)
                    LockedDetailRow("关联节点", "${relations.size} 个")
                    LockedDetailRow("节点类型", node.kindLabel)
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(FormalColors.Divider)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("前置关系", color = FormalColors.Ink, fontSize = type.size(11f), fontWeight = FontWeight.Medium)
                    if (relations.isEmpty()) {
                        Text("暂无前置关系", color = FormalColors.Muted, fontSize = type.size(9f))
                    } else {
                        relations.take(3).forEach { relation ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .background(FormalColors.PrimarySoft, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✓", color = FormalColors.Primary, fontSize = type.size(9f))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    relation.relation.ifBlank { "关联节点" },
                                    color = Color(0xFF45546B),
                                    fontSize = type.size(9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (!node.evidenceBody.isNullOrBlank()) {
                Text(
                    node.evidenceBody.orEmpty(),
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = FormalColors.Muted,
                    fontSize = type.size(9f),
                    lineHeight = type.size(15f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LockedDetailRow(label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(58.dp), color = FormalColors.Muted, fontSize = type.size(8f))
        Text(
            value,
            color = Color(0xFF344157),
            fontSize = type.size(9f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NodeKindChip(
    label: String,
    containerColor: Color = Color(0xFFE3F0FF),
    contentColor: Color = Color(0xFF1A66D6)
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            fontSize = LocalFormalTypeScale.current.size(8f),
            lineHeight = LocalFormalTypeScale.current.size(13f),
            maxLines = 1
        )
    }
}

@Composable
private fun SheetLabel(label: String) {
    Text(
        label,
        color = FormalColors.Muted,
        fontSize = LocalFormalTypeScale.current.size(9f),
        lineHeight = LocalFormalTypeScale.current.size(15f)
    )
}

@Composable
private fun GraphSheetDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .height(1.dp)
            .background(FormalColors.Divider)
    )
}

@Composable
private fun FormalGraphStateOverlay(
    state: KnowledgeGraphUiState,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.status == GraphRenderStatus.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = FormalColors.Primary,
                    strokeWidth = 2.dp
                )
            }
            Text(
                text = state.title,
                color = FormalColors.Ink,
                fontSize = type.size(if (state.status == GraphRenderStatus.Empty) 16f else 15f),
                lineHeight = type.size(24f),
                fontWeight = FontWeight.Medium
            )
            if (state.summary.isNotBlank()) {
                Text(
                    text = state.summary,
                    color = FormalColors.Muted,
                    fontSize = type.size(10f),
                    lineHeight = type.size(17f)
                )
            }
            if (state.status == GraphRenderStatus.Error && onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.heightIn(min = 44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("重试")
                }
            }
        }
    }
}

private val FormalGraphBackground = Color.White
