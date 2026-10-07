package com.reversetutor.preview.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.data.search.RoomGlobalSearchRepository
import com.reversetutor.core.domain.ContentRepository
import com.reversetutor.core.domain.OnlineContentArticle
import com.reversetutor.core.domain.OnlineData
import com.reversetutor.core.model.SearchTarget
import com.reversetutor.core.model.SearchTargetType
import com.reversetutor.feature.settings.FormalArticleInitialSection
import com.reversetutor.feature.settings.FormalGlobalSearchScreen
import com.reversetutor.feature.settings.FormalPublicArticleScreen
import com.reversetutor.feature.settings.FormalPublicArticleUiState
import com.reversetutor.feature.settings.FormalSearchKind
import com.reversetutor.feature.settings.FormalSearchResult
import com.reversetutor.feature.settings.FormalSearchResultGroup
import com.reversetutor.feature.settings.FormalSearchUiState
import com.reversetutor.preview.theme.ReverseTutorDesign
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun FormalGlobalSearchRoute(
    searchRepository: RoomGlobalSearchRepository,
    onBack: () -> Unit,
    onTargetSelected: (SearchTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var state by remember {
        mutableStateOf(FormalSearchUiState(indexStatus = "本地索引可用"))
    }
    var targets by remember { mutableStateOf(emptyMap<String, SearchTarget>()) }

    fun search(query: String = state.query) {
        val normalized = query.trim()
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
                        kind = result.target.type.toFormalKind(),
                        emphasizedTerm = normalized
                    )
                }
                targets = results.associate { result ->
                    "${result.target.type.name}:${result.target.entityId}" to result.target
                }
                state = state.copy(
                    query = normalized,
                    resultCount = mapped.size,
                    resultGroups = mapped
                        .groupBy { it.second.kind }
                        .entries
                        .sortedBy { it.key.displayOrder }
                        .map { (kind, items) ->
                            FormalSearchResultGroup(
                                title = kind.displayLabel,
                                count = items.size,
                                results = items.map { it.second }
                            )
                        }
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
        onQueryChange = { state = state.copy(query = it) },
        onSubmitSearch = { search() },
        onClearQuery = { search("") },
        onClearRecentSearches = {},
        onRecentSearchClick = { value ->
            state = state.copy(query = value)
            search(value)
        },
        onResultClick = { id -> targets[id]?.let(onTargetSelected) },
        modifier = modifier
    )
}

@Composable
fun FormalPublicArticleRoute(
    contentRepository: ContentRepository?,
    slug: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(slug) { mutableStateOf<FormalPublicArticleUiState?>(null) }
    var status by remember(slug, contentRepository) {
        mutableStateOf(if (contentRepository == null) "公益内容当前离线" else "正在获取公益内容")
    }

    LaunchedEffect(contentRepository, slug) {
        val repository = contentRepository ?: return@LaunchedEffect
        if (slug.isBlank()) {
            status = "公益内容暂不可用"
            return@LaunchedEffect
        }
        try {
            when (val result = repository.detail(slug)) {
                is OnlineData.Content -> state = result.value.toFormalArticleUiState()
                is OnlineData.Failure -> status = "公益内容暂不可用"
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = "公益内容暂不可用"
        }
    }

    val current = state
    if (current == null) {
        FormalRuntimeStatusScreen(status = status, onBack = onBack, modifier = modifier)
    } else {
        FormalPublicArticleScreen(
            state = current,
            onBack = onBack,
            onRelatedArticleClick = {},
            modifier = modifier
        )
    }
}

internal fun OnlineContentArticle.toFormalArticleUiState(): FormalPublicArticleUiState {
    val paragraphs = bodyMarkdown
        .split(Regex("\\r?\\n\\s*\\r?\\n"))
        .map { paragraph -> paragraph.lineSequence().joinToString(" ") { it.trim() }.trim() }
        .filter { it.isNotBlank() }
    val splitAt = ((paragraphs.size + 1) / 2).coerceAtLeast(1)
    val first = paragraphs.take(splitAt).ifEmpty { listOf(summary.summary) }
    val continuation = paragraphs.drop(splitAt)
    val published = SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(summary.publishedAtEpochMillis))
    val estimatedMinutes = (bodyMarkdown.count { !it.isWhitespace() } / 500).coerceAtLeast(1)
    return FormalPublicArticleUiState(
        initialSection = FormalArticleInitialSection.Top,
        title = summary.title,
        lead = summary.summary,
        publisher = summary.publisherName?.takeIf { it.isNotBlank() } ?: "公益内容发布方",
        publishLabel = published,
        readTimeLabel = "${estimatedMinutes}分钟阅读",
        bodyParagraphs = first,
        continuationTitle = if (continuation.isEmpty()) "" else "继续阅读",
        continuationParagraphs = continuation,
        quote = "",
        quoteAttribution = "",
        trialWeeks = "",
        participantCount = "",
        averageMinutes = "",
        relatedArticles = emptyList()
    )
}

@Composable
private fun FormalRuntimeStatusScreen(
    status: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ReverseTutorDesign.tokens.colorScheme.background)
            .padding(20.dp)
    ) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            Text("返回")
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "公益读本",
                color = ReverseTutorDesign.text.ink,
                style = ReverseTutorDesign.tokens.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = status,
                color = ReverseTutorDesign.text.muted,
                style = ReverseTutorDesign.tokens.typography.bodyMedium
            )
        }
    }
}

private fun SearchTargetType.toFormalKind(): FormalSearchKind = when (this) {
    SearchTargetType.Session -> FormalSearchKind.Session
    SearchTargetType.Message -> FormalSearchKind.Message
    SearchTargetType.Source -> FormalSearchKind.Material
    SearchTargetType.Memory,
    SearchTargetType.GraphNode -> FormalSearchKind.KnowledgeNode
    SearchTargetType.StudyPlan -> FormalSearchKind.StudyPlan
}

private val FormalSearchKind.displayLabel: String
    get() = when (this) {
        FormalSearchKind.Session -> "会话"
        FormalSearchKind.Message -> "消息"
        FormalSearchKind.KnowledgeNode -> "知识节点"
        FormalSearchKind.Material -> "资料"
        FormalSearchKind.StudyPlan -> "学习计划"
    }

private val FormalSearchKind.displayOrder: Int
    get() = when (this) {
        FormalSearchKind.Session -> 0
        FormalSearchKind.Message -> 1
        FormalSearchKind.KnowledgeNode -> 2
        FormalSearchKind.Material -> 3
        FormalSearchKind.StudyPlan -> 4
    }
