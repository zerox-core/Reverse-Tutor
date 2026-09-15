package com.reversetutor.feature.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.data.sources.SourceImportInput
import com.reversetutor.core.data.sources.SourceImportResult
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.data.sources.SourceWithChunks
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import kotlinx.coroutines.launch

@Composable
fun SourcesRoute(
    sourceRepository: SourceRepository,
    pendingImport: SourceImportInput?,
    highlightedSourceId: String? = null,
    sessionTitle: String? = null,
    sessionReferencedIds: Set<String> = emptySet(),
    openInSessionFilter: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onPickSource: () -> Unit,
    onSourceIndexed: (SourceImportResult) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(emptyList<SourceWithChunks>()) }
    var lastImport by remember { mutableStateOf<SourceImportResult?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    fun reload() {
        refreshKey += 1
    }

    LaunchedEffect(refreshKey) {
        sources = sourceRepository.listSourcesWithChunks()
    }

    LaunchedEffect(pendingImport?.requestId) {
        val import = pendingImport ?: return@LaunchedEffect
        val imported = sourceRepository.importSource(
            input = import,
            nowEpochMillis = System.currentTimeMillis()
        )
        lastImport = imported
        // NEWMP-V1-024: kick off background semantic indexing.
        onSourceIndexed(imported)
        reload()
    }

    SourcesScreen(
        state = SourcesUiState.from(sources = sources, lastImport = lastImport),
        highlightedSourceId = highlightedSourceId,
        sessionTitle = sessionTitle,
        sessionReferencedIds = sessionReferencedIds,
        openInSessionFilter = openInSessionFilter,
        searchQuery = searchQuery,
        onSearchQueryChange = onSearchQueryChange,
        onPickSource = onPickSource,
        onReprocess = { sourceId ->
            scope.launch {
                val reprocessed = sourceRepository.reprocessSource(
                    sourceId = sourceId,
                    nowEpochMillis = System.currentTimeMillis()
                )
                lastImport = reprocessed
                // NEWMP-V1-024: re-index embeddings for the fresh chunks.
                reprocessed?.let(onSourceIndexed)
                reload()
            }
        },
        onBack = onBack,
        modifier = modifier
    )
}

@Immutable
private data class SourcesColors(
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val borderStrong: Color,
    val divider: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color
)

@Composable
private fun sourcesColors(): SourcesColors {
    val scheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        SourcesColors(
            background = scheme.background,
            surface = scheme.surface,
            surfaceSubtle = scheme.surfaceVariant,
            ink = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            faint = scheme.onSurfaceVariant.copy(alpha = 0.72f),
            border = scheme.outlineVariant,
            borderStrong = scheme.outline,
            divider = scheme.outlineVariant,
            success = FormalColors.Success,
            successSoft = FormalColors.SuccessSoft.copy(alpha = 0.18f),
            warning = FormalColors.Warning,
            warningSoft = FormalColors.WarningSoft.copy(alpha = 0.18f),
            danger = FormalColors.Danger,
            dangerSoft = FormalColors.Danger.copy(alpha = 0.14f)
        )
    } else {
        SourcesColors(
            background = FormalColors.Background,
            surface = FormalColors.Surface,
            surfaceSubtle = FormalColors.SurfaceSubtle,
            ink = FormalColors.Ink,
            muted = FormalColors.Muted,
            faint = FormalColors.Tertiary,
            border = FormalColors.Border,
            borderStrong = FormalColors.BorderStrong,
            divider = FormalColors.Divider,
            success = FormalColors.Success,
            successSoft = FormalColors.SuccessSoft,
            warning = FormalColors.Warning,
            warningSoft = FormalColors.WarningSoft,
            danger = FormalColors.Danger,
            dangerSoft = FormalColors.Danger.copy(alpha = 0.12f)
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SourcesScreen(
    state: SourcesUiState,
    highlightedSourceId: String? = null,
    sessionTitle: String? = null,
    sessionReferencedIds: Set<String> = emptySet(),
    openInSessionFilter: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onPickSource: () -> Unit,
    onReprocess: (String) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var helpExpanded by remember { mutableStateOf(false) }
    var sessionFilter by remember { mutableStateOf(openInSessionFilter) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var expandedIds by remember(highlightedSourceId) {
        mutableStateOf(setOfNotNull(highlightedSourceId))
    }
    val hasSessionScope = sessionReferencedIds.isNotEmpty()
    val visibleItems = filterSourceItems(
        items = state.items,
        sessionScope = sessionFilter && hasSessionScope,
        sessionReferencedIds = sessionReferencedIds,
        typeLabel = selectedType,
        statusLabel = selectedStatus,
        searchQuery = searchQuery
    )
    val typeOptions = state.items.map { it.typeLabel }.distinct().sorted()
    val statusOptions = listOf("已解析", "部分解析", "等待能力", "暂不支持", "失败")
        .filter { label -> state.items.any { it.statusLabel == label } }
    val filtersActive = visibleItems.size != state.items.size
    val colors = sourcesColors()
    val type = LocalFormalTypeScale.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        SourcesTopBar(
            summary = if (filtersActive) {
                "${visibleItems.size} / ${state.items.size} 份资料"
            } else {
                state.summary
            },
            colors = colors,
            onBack = onBack,
            onPickSource = onPickSource
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            if (hasSessionScope) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!sessionTitle.isNullOrBlank()) {
                        Text(
                            text = "当前会话：$sessionTitle",
                            color = colors.faint,
                            style = type.style(9f, 14f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SourcesPillChip(
                            label = "全部",
                            selected = !sessionFilter,
                            colors = colors,
                            onClick = { sessionFilter = false },
                            modifier = Modifier.testTag("sources-filter-all")
                        )
                        SourcesPillChip(
                            label = "本会话",
                            selected = sessionFilter,
                            colors = colors,
                            onClick = { sessionFilter = true },
                            modifier = Modifier.testTag("sources-filter-session")
                        )
                    }
                }
            }
            if (state.items.isNotEmpty()) {
                SourcesSearchField(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    colors = colors,
                    modifier = Modifier.testTag("sources-search")
                )
                if (typeOptions.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourcesPillChip(
                            label = "全部类型",
                            selected = selectedType == null,
                            colors = colors,
                            onClick = { selectedType = null },
                            modifier = Modifier.testTag("sources-type-filter-all")
                        )
                        typeOptions.forEach { label ->
                            SourcesPillChip(
                                label = label,
                                selected = selectedType == label,
                                colors = colors,
                                onClick = {
                                    selectedType = if (selectedType == label) null else label
                                },
                                modifier = Modifier.testTag("sources-type-filter-$label")
                            )
                        }
                    }
                }
                if (statusOptions.size > 1) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SourcesPillChip(
                            label = "全部状态",
                            selected = selectedStatus == null,
                            colors = colors,
                            onClick = { selectedStatus = null },
                            modifier = Modifier.testTag("sources-status-filter-all")
                        )
                        statusOptions.forEach { label ->
                            SourcesPillChip(
                                label = label,
                                selected = selectedStatus == label,
                                colors = colors,
                                onClick = {
                                    selectedStatus = if (selectedStatus == label) null else label
                                },
                                modifier = Modifier.testTag("sources-status-filter-$label")
                            )
                        }
                    }
                }
            }
            SourcesHelpCard(
                expanded = helpExpanded,
                onToggle = { helpExpanded = !helpExpanded },
                colors = colors
            )
            val importStatus = state.importStatusLabel
            if (importStatus != null) {
                ImportStatusPanel(status = importStatus, lines = state.importDetailLines, colors = colors)
            }
            if (state.isEmpty) {
                EmptySources(
                    title = state.emptyTitle,
                    onPickSource = onPickSource,
                    colors = colors
                )
            } else if (visibleItems.isEmpty()) {
                if (sessionFilter && hasSessionScope) {
                    EmptyNotice(
                        text = "当前会话还没有引用资料。切换到「全部」可查看所有资料。",
                        colors = colors
                    )
                } else {
                    EmptyNotice(
                        text = "没有符合当前搜索或筛选条件的资料。",
                        colors = colors
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleItems.forEach { item ->
                        SourceCard(
                            item = item,
                            highlighted = item.id == highlightedSourceId,
                            expanded = expandedIds.contains(item.id),
                            onToggleExpanded = {
                                expandedIds = if (expandedIds.contains(item.id)) {
                                    expandedIds - item.id
                                } else {
                                    expandedIds + item.id
                                }
                            },
                            onReprocess = { onReprocess(item.id) },
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesTopBar(
    summary: String,
    colors: SourcesColors,
    onBack: () -> Unit,
    onPickSource: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(colors.surface)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 7.dp)
                .size(44.dp)
                .clickable(role = Role.Button, onClickLabel = "返回", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "返回",
                tint = colors.ink,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 56.dp, end = 64.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = "资料中心",
                color = colors.ink,
                style = type.style(18f, 25f, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                color = colors.faint,
                style = type.style(9f, 14f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(40.dp)
                .background(colors.surfaceSubtle, RoundedCornerShape(FormalShapes.CardRadius))
                .clickable(role = Role.Button, onClickLabel = "添加资料", onClick = onPickSource)
                .testTag("sources-add"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = "添加资料",
                tint = colors.ink,
                modifier = Modifier.size(22.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider)
        )
    }
}

@Composable
private fun SourcesPillChip(
    label: String,
    selected: Boolean,
    colors: SourcesColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = modifier.clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        color = if (selected) colors.ink else colors.surface,
        shape = RoundedCornerShape(FormalShapes.PillRadius),
        border = BorderStroke(1.dp, if (selected) colors.ink else colors.border)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) colors.surface else colors.ink,
            style = type.style(9f, 14f, FontWeight.Medium)
        )
    }
}

@Composable
private fun SourcesSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    colors: SourcesColors,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(44.dp),
        color = colors.surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.style(11f, 17f, FontWeight.Medium, colors.ink),
                cursorBrush = SolidColor(colors.ink),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = "搜索资料标题",
                                color = colors.faint,
                                style = type.style(10f, 17f)
                            )
                        }
                        inner()
                    }
                },
                modifier = modifier.weight(1f)
            )
            if (query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .background(colors.border, CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "清除搜索",
                            onClick = { onQueryChange("") }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "清除搜索",
                        tint = colors.surface,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SourcesOutlinedCard(
    colors: SourcesColors,
    modifier: Modifier = Modifier,
    background: Color? = null,
    border: Color? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        color = background ?: colors.surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, border ?: colors.border)
    ) {
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SourcesHelpCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    colors: SourcesColors
) {
    val type = LocalFormalTypeScale.current
    SourcesOutlinedCard(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClickLabel = "解析状态说明", onClick = onToggle)
                    .testTag("sources-help-toggle")
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(colors.surfaceSubtle, RoundedCornerShape(FormalShapes.CompactRadius)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "解析状态",
                        color = colors.ink,
                        style = type.style(11f, 17f, FontWeight.Medium)
                    )
                    Text(
                        text = "各状态含义与当前解析能力",
                        color = colors.faint,
                        style = type.style(8f, 13f)
                    )
                }
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(1.dp)
                        .background(colors.divider)
                )
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "TXT 和 Markdown 可本地解析，HTML 会做安全清洗后部分提取。PDF、Word、PPT、电子书和图片会就地抽取正文与插图文字，抽取不到的会保留为等待能力的资料，不会被隐藏。",
                        color = colors.muted,
                        style = type.style(9f, 15f)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("已解析", "部分解析", "等待能力", "暂不支持", "失败").forEach { label ->
                            Text(
                                text = label,
                                color = colors.faint,
                                style = type.style(8f, 13f, FontWeight.Medium),
                                modifier = Modifier
                                    .background(
                                        colors.surfaceSubtle,
                                        RoundedCornerShape(FormalShapes.CompactRadius)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportStatusPanel(
    status: String,
    lines: List<String>,
    colors: SourcesColors
) {
    val type = LocalFormalTypeScale.current
    SourcesOutlinedCard(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "最近导入：$status",
                    color = colors.ink,
                    style = type.style(10f, 15f, FontWeight.Medium)
                )
                lines.forEach { line ->
                    Text(
                        text = line,
                        color = colors.faint,
                        style = type.style(8f, 13f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySources(
    title: String,
    onPickSource: () -> Unit,
    colors: SourcesColors
) {
    val type = LocalFormalTypeScale.current
    SourcesOutlinedCard(
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.surfaceSubtle, RoundedCornerShape(FormalShapes.IconRadius)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = colors.faint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title,
                color = colors.ink,
                style = type.style(11f, 17f, FontWeight.SemiBold)
            )
            Text(
                text = "选择 TXT、Markdown、HTML、PDF、DOCX、PPTX、EPUB、图片或其他文件。暂不支持的文件也会保留并显示状态。",
                color = colors.muted,
                style = type.style(9f, 15f),
                textAlign = TextAlign.Center
            )
            SourcesPrimaryPill(
                label = "添加资料",
                onClick = onPickSource,
                colors = colors
            )
        }
    }
}

@Composable
private fun EmptyNotice(
    text: String,
    colors: SourcesColors
) {
    val type = LocalFormalTypeScale.current
    SourcesOutlinedCard(
        colors = colors,
        modifier = Modifier.fillMaxWidth(),
        padding = PaddingValues(18.dp)
    ) {
        Text(
            text = text,
            color = colors.muted,
            style = type.style(9f, 15f)
        )
    }
}

@Composable
private fun SourcesPrimaryPill(
    label: String,
    onClick: () -> Unit,
    colors: SourcesColors,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(colors.ink, RoundedCornerShape(FormalShapes.PillRadius))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = colors.surface,
            style = type.style(10f, 15f, FontWeight.Medium)
        )
    }
}

private fun typeIcon(typeLabel: String): ImageVector = when (typeLabel) {
    "PDF" -> Icons.Rounded.PictureAsPdf
    "DOCX" -> Icons.Rounded.Description
    "TXT" -> Icons.Rounded.TextSnippet
    "Markdown" -> Icons.Rounded.TextSnippet
    "HTML" -> Icons.Rounded.Code
    "PPTX" -> Icons.Rounded.Slideshow
    "EPUB" -> Icons.Rounded.MenuBook
    "图片" -> Icons.Rounded.Image
    "JSON 导出" -> Icons.Rounded.DataObject
    else -> Icons.Rounded.InsertDriveFile
}

@Composable
private fun SourceCard(
    item: SourceCardUiItem,
    highlighted: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onReprocess: () -> Unit,
    colors: SourcesColors
) {
    val type = LocalFormalTypeScale.current
    val statusBackground = when (item.statusTone) {
        SourceStatusTone.Success -> colors.successSoft
        SourceStatusTone.Warning -> colors.warningSoft
        SourceStatusTone.Info -> colors.surfaceSubtle
        SourceStatusTone.Disabled -> colors.divider
        SourceStatusTone.Error -> colors.dangerSoft
    }
    val statusForeground = when (item.statusTone) {
        SourceStatusTone.Success -> colors.success
        SourceStatusTone.Warning -> colors.warning
        SourceStatusTone.Info -> colors.muted
        SourceStatusTone.Disabled -> colors.faint
        SourceStatusTone.Error -> colors.danger
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("source-card-${item.id}")
            .semantics { selected = highlighted },
        color = if (highlighted) colors.surfaceSubtle else colors.surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, if (highlighted) colors.borderStrong else colors.border)
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onToggleExpanded)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            colors.divider.copy(alpha = 0.55f),
                            RoundedCornerShape(FormalShapes.CompactRadius)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon(item.typeLabel),
                        contentDescription = item.typeLabel,
                        tint = colors.muted,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = item.title,
                        color = colors.ink,
                        style = type.style(11f, 16f, FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.typeLabel,
                        color = colors.faint,
                        style = type.style(8f, 12f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    color = statusBackground,
                    shape = RoundedCornerShape(FormalShapes.PillRadius)
                ) {
                    Text(
                        text = item.statusLabel,
                        modifier = Modifier
                            .testTag("source-status-${item.id}")
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        color = statusForeground,
                        style = type.style(8f, 12f, FontWeight.Medium)
                    )
                }
            }
            Text(
                text = item.impactMessage,
                color = colors.muted,
                style = type.style(9f, 14f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.chunkCountLabel,
                    color = colors.faint,
                    style = type.style(8f, 12f),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) {
                        Icons.Rounded.KeyboardArrowUp
                    } else {
                        Icons.Rounded.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "收起片段" else "展开片段",
                    tint = colors.faint,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.divider)
                )
                Text(
                    text = "片段",
                    color = colors.ink,
                    style = type.style(9f, 14f, FontWeight.SemiBold)
                )
                if (item.snippets.isNotEmpty()) {
                    item.snippets.forEach { snippet ->
                        Text(
                            text = snippet,
                            color = colors.muted,
                            style = type.style(9f, 14f)
                        )
                    }
                } else {
                    Text(
                        text = "尚无可引用片段。",
                        color = colors.faint,
                        style = type.style(9f, 14f)
                    )
                }
                Text(
                    text = item.evidenceSummary,
                    color = colors.faint,
                    style = type.style(8f, 13f)
                )
            }
            if (item.recoveryEnabled && item.recoveryLabel != null) {
                Row {
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 36.dp)
                            .testTag("source-action-${item.id}")
                            .clickable(
                                role = Role.Button,
                                onClickLabel = item.recoveryLabel,
                                onClick = onReprocess
                            ),
                        color = colors.surface,
                        shape = RoundedCornerShape(FormalShapes.PillRadius),
                        border = BorderStroke(1.dp, colors.borderStrong)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = item.recoveryLabel,
                                color = colors.ink,
                                style = type.style(9f, 13f, FontWeight.Medium)
                            )
                        }
                    }
                }
            } else {
                item.recoveryReason?.let { reason ->
                    Text(
                        text = reason,
                        color = colors.faint,
                        style = type.style(8f, 13f)
                    )
                }
            }
        }
    }
}
