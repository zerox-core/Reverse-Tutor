package com.reversetutor.preview.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.data.search.RoomGlobalSearchRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.model.SearchTarget
import com.reversetutor.core.model.SearchTargetType
import com.reversetutor.core.model.SourceType
import com.reversetutor.feature.settings.FormalGlobalSearchScreen
import com.reversetutor.feature.settings.FormalSearchKind
import com.reversetutor.feature.settings.FormalSearchResult
import com.reversetutor.feature.settings.FormalSearchResultGroup
import com.reversetutor.feature.settings.FormalSearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * NEWMP-V1-026: 资料中心四入口容器 —— 搜索 / 资料 / 图谱 / 设置。
 *
 * 顶部入口可以在四个页签之间切换；每个页签的内容由调用方以 slot 传入，
 * 容器本身只负责页签栏与内容切换，不持有任何业务状态。
 */
enum class SourcesCenterTab(val label: String) {
    Search("搜索"),
    Materials("资料"),
    Graph("图谱"),
    Settings("设置")
}

@Composable
fun SourcesCenterHost(
    selectedTab: SourcesCenterTab,
    onTabSelected: (SourcesCenterTab) -> Unit,
    searchContent: @Composable () -> Unit,
    materialsContent: @Composable () -> Unit,
    graphContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.testTag("sources-center-tabs")
        ) {
            SourcesCenterTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label) },
                    modifier = Modifier.testTag("sources-center-tab-${tab.name.lowercase()}")
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                SourcesCenterTab.Search -> searchContent()
                SourcesCenterTab.Materials -> materialsContent()
                SourcesCenterTab.Graph -> graphContent()
                SourcesCenterTab.Settings -> settingsContent()
            }
        }
    }
}

/**
 * NEWMP-V1-026: 搜索入口 —— 复用全局搜索界面，但结果分组按用户口径重排：
 * 先「聊天记录」（会话 + 消息），再「资料 · PDF / Word / …」按来源文件类型分组，
 * 最后是「知识节点」「学习计划」。
 *
 * [query] / [onQueryChange] 与资料入口的搜索框共享同一份状态，
 * 保证两个入口的搜索词同步（资料入口不含聊天记录）。
 */
@Composable
fun GlobalSearchCenterContent(
    searchRepository: RoomGlobalSearchRepository,
    sourceRepository: SourceRepository,
    query: String,
    onQueryChange: (String) -> Unit,
    onTargetSelected: (SearchTarget) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(FormalSearchUiState(query = query, indexStatus = "本地索引可用"))
    }
    var targets by remember { mutableStateOf(emptyMap<String, SearchTarget>()) }

    fun search(queryValue: String = state.query) {
        val normalized = queryValue.trim()
        if (normalized.isEmpty()) {
            targets = emptyMap()
            state = state.copy(query = "", resultCount = 0, resultGroups = emptyList())
            return
        }
        scope.launch {
            try {
                val results = searchRepository.searchResults(
                    spaceId = com.reversetutor.core.data.session.SessionRepository.defaultSpaceId,
                    query = normalized
                )
                val mapped = results.map { result ->
                    val id = "${result.target.type.name}:${result.target.entityId}"
                    id to FormalSearchResult(
                        id = id,
                        title = result.title,
                        subtitle = result.excerpt.take(96),
                        kind = result.target.type.toCenterSearchKind(),
                        emphasizedTerm = normalized
                    )
                }
                targets = results.associate { result ->
                    "${result.target.type.name}:${result.target.entityId}" to result.target
                }
                val sourceTypesById = if (results.any { it.target.type == SearchTargetType.Source }) {
                    sourceRepository.listSourcesWithChunks().associate { it.source.id to it.source.type }
                } else {
                    emptyMap()
                }
                state = state.copy(
                    query = normalized,
                    resultCount = mapped.size,
                    resultGroups = regroupCenterSearchResults(
                        results = mapped.map { it.second },
                        sourceTypesById = sourceTypesById
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                targets = emptyMap()
                state = state.copy(
                    query = normalized,
                    resultCount = 0,
                    resultGroups = emptyList(),
                    indexStatus = "本地索引暂不可用"
                )
            }
        }
    }

    FormalGlobalSearchScreen(
        state = state,
        onBack = onBack,
        onQueryChange = { value ->
            state = state.copy(query = value)
            onQueryChange(value)
        },
        onSubmitSearch = { search() },
        onClearQuery = {
            state = state.copy(query = "", resultCount = 0, resultGroups = emptyList())
            onQueryChange("")
            targets = emptyMap()
        },
        onClearRecentSearches = {},
        onRecentSearchClick = { value ->
            state = state.copy(query = value)
            onQueryChange(value)
            search(value)
        },
        onResultClick = { id -> targets[id]?.let(onTargetSelected) },
        modifier = modifier
    )
}

/**
 * 搜索结果重排：聊天记录 → 资料（按文件类型） → 知识节点 → 学习计划。
 * 纯函数，便于后续单测覆盖。
 */
internal fun regroupCenterSearchResults(
    results: List<FormalSearchResult>,
    sourceTypesById: Map<String, SourceType>
): List<FormalSearchResultGroup> {
    val chatResults = results.filter {
        it.kind == FormalSearchKind.Session || it.kind == FormalSearchKind.Message
    }
    val materialResults = results.filter { it.kind == FormalSearchKind.Material }
    val knowledgeResults = results.filter { it.kind == FormalSearchKind.KnowledgeNode }
    val planResults = results.filter { it.kind == FormalSearchKind.StudyPlan }
    return buildList {
        if (chatResults.isNotEmpty()) {
            add(FormalSearchResultGroup("聊天记录", chatResults.size, chatResults))
        }
        if (materialResults.isNotEmpty()) {
            materialResults
                .groupBy { result -> centerMaterialTypeLabel(result.id, sourceTypesById) }
                .forEach { (label, items) ->
                    add(FormalSearchResultGroup("资料 · $label", items.size, items))
                }
        }
        if (knowledgeResults.isNotEmpty()) {
            add(FormalSearchResultGroup("知识节点", knowledgeResults.size, knowledgeResults))
        }
        if (planResults.isNotEmpty()) {
            add(FormalSearchResultGroup("学习计划", planResults.size, planResults))
        }
    }
}

private fun centerMaterialTypeLabel(
    resultId: String,
    sourceTypesById: Map<String, SourceType>
): String {
    val sourceId = resultId.removePrefix("${SearchTargetType.Source.name}:")
    return when (sourceTypesById[sourceId]) {
        SourceType.Pdf -> "PDF"
        SourceType.Docx -> "Word"
        SourceType.Pptx -> "PPT"
        SourceType.Epub -> "EPUB"
        SourceType.Image -> "图片"
        SourceType.Text -> "TXT"
        SourceType.Markdown -> "Markdown"
        SourceType.Html -> "HTML"
        SourceType.JsonExport -> "JSON 导出"
        SourceType.Other -> "其他"
        null -> "资料"
    }
}

private fun SearchTargetType.toCenterSearchKind(): FormalSearchKind = when (this) {
    SearchTargetType.Session -> FormalSearchKind.Session
    SearchTargetType.Message -> FormalSearchKind.Message
    SearchTargetType.Source -> FormalSearchKind.Material
    SearchTargetType.Memory,
    SearchTargetType.GraphNode -> FormalSearchKind.KnowledgeNode
    SearchTargetType.StudyPlan -> FormalSearchKind.StudyPlan
}

/**
 * NEWMP-V1-026: 图谱入口占位页。本期只预留接口：展示当前会话范围内的
 * 资料切片关系示意，渲染器后续单独设计实现。
 */
@Composable
fun GraphCenterPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "知识图谱",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "展示当前会话资料切片之间的关联，也就是知识库向量内容的可视化浏览。渲染器开发中，本期先预留入口。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shape = RoundedCornerShape(50)
        ) {
            Text(
                text = "范围：当前会话",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sources-graph-canvas"),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                val nodeColor = Color(0xFF6750A4)
                val edgeColor = nodeColor.copy(alpha = 0.4f)
                val nodePositions = listOf(
                    0.22f to 0.30f,
                    0.50f to 0.18f,
                    0.78f to 0.32f,
                    0.35f to 0.62f,
                    0.66f to 0.66f,
                    0.50f to 0.86f
                )
                val points = nodePositions.map { (x, y) ->
                    Offset(size.width * x, size.height * y)
                }
                val edges = listOf(
                    0 to 1,
                    1 to 2,
                    0 to 3,
                    1 to 3,
                    2 to 4,
                    3 to 4,
                    3 to 5,
                    4 to 5
                )
                edges.forEach { (from, to) ->
                    drawLine(
                        color = edgeColor,
                        start = points[from],
                        end = points[to],
                        strokeWidth = 3f
                    )
                }
                points.forEachIndexed { index, point ->
                    drawCircle(
                        color = if (index % 2 == 0) nodeColor else nodeColor.copy(alpha = 0.7f),
                        radius = 14f,
                        center = point
                    )
                }
            }
        }
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
                    text = "分工说明",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "资料库解决「如何找到一个精准的对象」；知识图谱解决「对象与对象之间的关系如何查询、如何演进」。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Text(
            text = "开发中",
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
