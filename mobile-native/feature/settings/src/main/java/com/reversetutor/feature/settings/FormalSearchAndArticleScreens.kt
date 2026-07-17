package com.reversetutor.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Immutable
data class FormalSearchUiState(
    val query: String = "",
    val resultCount: Int = 0,
    val selectedTypeLabel: String = "全部类型",
    val recentSearches: List<String> = emptyList(),
    val recentVisits: List<FormalSearchVisit> = emptyList(),
    val resultGroups: List<FormalSearchResultGroup> = emptyList(),
    val indexStatus: String = "内容索引已更新"
) {
    val showingResults: Boolean get() = query.isNotBlank()
}

@Immutable
data class FormalSearchVisit(
    val id: String,
    val title: String,
    val subtitle: String,
    val timeLabel: String,
    val kind: FormalSearchKind
)

@Immutable
data class FormalSearchResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val kind: FormalSearchKind,
    val emphasizedTerm: String? = null
)

@Immutable
data class FormalSearchResultGroup(
    val title: String,
    val count: Int,
    val results: List<FormalSearchResult>
)

enum class FormalSearchKind {
    Session,
    Message,
    KnowledgeNode,
    Material,
    StudyPlan
}

@Composable
fun FormalGlobalSearchScreen(
    state: FormalSearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onClearRecentSearches: () -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onResultClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Column(modifier = modifier.fillMaxSize().background(colors.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(colors.surface)
                .padding(start = 7.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(role = Role.Button, onClickLabel = "返回", onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = "返回",
                    tint = colors.ink,
                    modifier = Modifier.size(20.dp).then(Modifier)
                )
            }
            Surface(
                modifier = Modifier.weight(1f).height(44.dp),
                color = colors.surfaceQuiet,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.border)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = colors.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = type.style(11f, 17f, FontWeight.Medium, colors.ink),
                        cursorBrush = SolidColor(colors.primary),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSubmitSearch() }),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (state.query.isBlank()) {
                                    Text(
                                        "搜索会话、消息、资料和节点",
                                        color = colors.faint,
                                        style = type.style(10f, 17f)
                                    )
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (state.query.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .size(25.dp)
                                .background(colors.border, CircleShape)
                                .clickable(role = Role.Button, onClickLabel = "清除搜索", onClick = onClearQuery),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Close, "清除搜索", tint = colors.surface, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
        if (state.showingResults) {
            SearchResultsContent(state, onResultClick)
        } else {
            SearchRecentContent(state, onClearRecentSearches, onRecentSearchClick, onResultClick)
        }
    }
}

@Composable
private fun SearchRecentContent(
    state: FormalSearchUiState,
    onClearRecentSearches: () -> Unit,
    onRecentSearchClick: (String) -> Unit,
    onVisitClick: (String) -> Unit
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 18.dp, 16.dp, 28.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "最近搜索",
                    color = colors.ink,
                    style = type.style(13f, 20f, FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                if (state.recentSearches.isNotEmpty()) {
                    Text(
                        "清除",
                        color = Color(0xFF4C86C5),
                        style = type.style(9f, 15f),
                        modifier = Modifier.clickable(onClickLabel = "清除最近搜索", onClick = onClearRecentSearches)
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            if (state.recentSearches.isEmpty()) {
                FormalInfoBanner(title = "暂无最近搜索", body = "")
            } else {
                FormalOutlinedCard(modifier = Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 13.dp)) {
                    Column {
                        state.recentSearches.forEachIndexed { index, value ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable(onClickLabel = value) { onRecentSearchClick(value) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.History, null, tint = colors.muted, modifier = Modifier.size(17.dp))
                                Spacer(Modifier.width(13.dp))
                                Text(value, color = colors.ink, style = type.style(10f, 16f), modifier = Modifier.weight(1f))
                                Icon(Icons.AutoMirrored.Rounded.Reply, null, tint = colors.muted, modifier = Modifier.size(15.dp))
                            }
                            if (index != state.recentSearches.lastIndex) {
                                Box(Modifier.fillMaxWidth().padding(start = 25.dp).height(1.dp).background(colors.divider))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            FormalSectionLabel("最近访问")
            Spacer(Modifier.height(10.dp))
        }
        items(state.recentVisits, key = { it.id }) { visit ->
            SearchVisitRow(visit, onVisitClick)
        }
        item {
            Spacer(Modifier.height(26.dp))
            FormalInfoBanner(
                title = state.indexStatus,
                body = "",
                success = true
            )
        }
    }
}

@Composable
private fun SearchVisitRow(visit: FormalSearchVisit, onClick: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = visit.title) { onClick(visit.id) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormalGlossySquare(visit.kind.icon, null, visit.kind.color, size = 38.dp, glyphSize = 17.dp)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(visit.title, color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
            Text(visit.subtitle, color = colors.faint, style = type.style(8f, 13f))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(visit.timeLabel, color = colors.faint, style = type.style(8f, 12f))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(17.dp))
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 42.dp).height(1.dp).background(colors.divider))
}

@Composable
private fun SearchResultsContent(state: FormalSearchUiState, onResultClick: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 12.dp, 16.dp, 28.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.resultCount} 条结果",
                    color = colors.ink,
                    style = type.style(12f, 18f, FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = colors.primarySoft,
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, colors.border)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(state.selectedTypeLabel, color = colors.muted, style = type.style(9f, 14f))
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
        }
        state.resultGroups.forEach { group ->
            item {
                FormalSectionLabel(group.title, trailing = group.count.toString())
                Spacer(Modifier.height(5.dp))
            }
            items(group.results, key = { it.id }) { result ->
                SearchResultRow(result, state.query, onResultClick)
            }
            item { Spacer(Modifier.height(15.dp)) }
        }
    }
}

@Composable
private fun SearchResultRow(result: FormalSearchResult, query: String, onClick: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = result.title) { onClick(result.id) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (result.kind == FormalSearchKind.Message) {
            Box(Modifier.width(3.dp).height(40.dp).background(Color(0xFF4C91EC), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(11.dp))
        } else {
            FormalGlossySquare(result.kind.icon, null, result.kind.color, size = 30.dp, glyphSize = 14.dp)
            Spacer(Modifier.width(11.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = highlighted(result.title, result.emphasizedTerm ?: query, colors.primary, colors.ink),
                style = type.style(10f, 16f, if (result.kind == FormalSearchKind.Message) FontWeight.Normal else FontWeight.Medium)
            )
            Text(result.subtitle, color = colors.faint, style = type.style(8f, 13f))
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = 40.dp).height(1.dp).background(colors.divider))
}

private fun highlighted(text: String, term: String, highlight: Color, normal: Color): AnnotatedString =
    buildAnnotatedString {
        val start = text.indexOf(term, ignoreCase = true)
        if (start < 0 || term.isBlank()) {
            withStyle(SpanStyle(color = normal)) { append(text) }
        } else {
            withStyle(SpanStyle(color = normal)) { append(text.substring(0, start)) }
            withStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Medium)) {
                append(text.substring(start, start + term.length))
            }
            withStyle(SpanStyle(color = normal)) { append(text.substring(start + term.length)) }
        }
    }

private val FormalSearchKind.icon: ImageVector
    get() = when (this) {
        FormalSearchKind.Session -> Icons.Rounded.ChatBubbleOutline
        FormalSearchKind.Message -> Icons.Rounded.ChatBubbleOutline
        FormalSearchKind.KnowledgeNode -> Icons.Rounded.Share
        FormalSearchKind.Material -> Icons.Rounded.Description
        FormalSearchKind.StudyPlan -> Icons.Rounded.CheckCircleOutline
    }

private val FormalSearchKind.color: Color
    get() = when (this) {
        FormalSearchKind.Session -> Color(0xFF5B92D0)
        FormalSearchKind.Message -> Color(0xFF5B92D0)
        FormalSearchKind.KnowledgeNode -> Color(0xFF3FA27F)
        FormalSearchKind.Material -> Color(0xFFC38A31)
        FormalSearchKind.StudyPlan -> Color(0xFF6B85AC)
    }

enum class FormalArticleInitialSection(internal val listIndex: Int) {
    Top(0),
    Continuation(5)
}

@Immutable
data class FormalPublicArticleUiState(
    val initialSection: FormalArticleInitialSection = FormalArticleInitialSection.Top,
    val title: String,
    val lead: String,
    val publisher: String,
    val publishLabel: String,
    val readTimeLabel: String,
    val progressPercent: Int = 0,
    val bodyParagraphs: List<String>,
    val continuationTitle: String,
    val continuationParagraphs: List<String>,
    val quote: String,
    val quoteAttribution: String,
    val trialWeeks: String,
    val participantCount: String,
    val averageMinutes: String,
    val relatedArticles: List<FormalRelatedArticle>
)

@Immutable
data class FormalRelatedArticle(
    val id: String,
    val title: String,
    val subtitle: String
)

@Composable
fun FormalPublicArticleScreen(
    state: FormalPublicArticleUiState,
    onBack: () -> Unit,
    onRelatedArticleClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.initialSection.listIndex)
    FormalBatch6Page(
        title = if (state.initialSection == FormalArticleInitialSection.Top) "公益读本" else "安静阅读",
        subtitle = if (state.initialSection == FormalArticleInitialSection.Top) "每日由编辑发布" else "阅读进度 ${state.progressPercent}%",
        onBack = onBack,
        modifier = modifier,
        trailing = {
            ArticleStaticAction(Icons.Rounded.BookmarkBorder, "收藏")
            ArticleStaticAction(Icons.Rounded.Share, "分享")
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item { ArticleHero() }
            item { ArticleHeader(state) }
            item { ArticleParagraphs(state.bodyParagraphs) }
            if (state.quote.isNotBlank()) {
                item { ArticleQuote(state) }
                item { Spacer(Modifier.height(28.dp)) }
            }
            if (state.continuationTitle.isNotBlank() || state.continuationParagraphs.isNotEmpty()) {
                item { ArticleContinuation(state) }
            }
            if (state.quote.isNotBlank() && state.quoteAttribution.isNotBlank()) {
                item { ArticleDialogueCard() }
            }
            if (state.trialWeeks.isNotBlank() || state.participantCount.isNotBlank() || state.averageMinutes.isNotBlank()) {
                item { ArticleTrialCard(state) }
            }
            if (state.relatedArticles.isNotEmpty()) {
                item { ArticleRelated(state.relatedArticles, onRelatedArticleClick) }
            }
        }
    }
}

@Composable
private fun ArticleStaticAction(icon: ImageVector, description: String) {
    val colors = formalBatch6Colors()
    Surface(
        modifier = Modifier.size(34.dp),
        color = colors.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 2.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = colors.muted, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ArticleHero() {
    val colors = formalBatch6Colors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(205.dp)
            .background(Color(0xFFE9F1F7), RoundedCornerShape(8.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.formal_public_article_quiet_reading),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Text(
            "教育支持 · 第 12 期",
            color = colors.muted,
            style = LocalFormalTypeScale.current.style(8f, 13f),
            modifier = Modifier.align(Alignment.TopStart).padding(14.dp).background(colors.surface, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ArticleHeader(state: FormalPublicArticleUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Text(state.title, color = colors.ink, style = type.style(22f, 30f, FontWeight.Bold))
    Spacer(Modifier.height(8.dp))
    Text(state.lead, color = colors.muted, style = type.style(11f, 20f))
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormalGlossySquare(Icons.Rounded.Lightbulb, null, Color(0xFF46A57C), size = 34.dp, glyphSize = 17.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(state.publisher, color = colors.ink, style = type.style(10f, 15f, FontWeight.Medium))
            Text("${state.publishLabel} · ${state.readTimeLabel}", color = colors.faint, style = type.style(8f, 13f))
        }
        Text("今日更新", color = colors.success, style = type.style(8f, 13f), modifier = Modifier.background(colors.successSoft, RoundedCornerShape(14.dp)).padding(horizontal = 15.dp, vertical = 6.dp))
    }
    Spacer(Modifier.height(20.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun ArticleParagraphs(paragraphs: List<String>) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    paragraphs.forEach {
        Text(it, color = colors.ink, style = type.style(12f, 24f))
        Spacer(Modifier.height(26.dp))
    }
}

@Composable
private fun ArticleQuote(state: FormalPublicArticleUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(background = colors.primarySoft, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text("”", color = Color(0xFF4C86C5), style = type.style(24f, 24f, FontWeight.Bold))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(state.quote, color = colors.ink, style = type.style(12f, 20f, FontWeight.Medium))
                Spacer(Modifier.height(4.dp))
                Text(state.quoteAttribution, color = colors.faint, style = type.style(8f, 13f))
            }
        }
    }
}

@Composable
private fun ArticleContinuation(state: FormalPublicArticleUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Text(state.continuationTitle, color = colors.ink, style = type.style(17f, 25f, FontWeight.Bold))
    Spacer(Modifier.height(14.dp))
    ArticleParagraphs(state.continuationParagraphs)
}

@Composable
private fun ArticleDialogueCard() {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(background = Color(0xFFF0F6FA), padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("学习室里的一段对话", color = colors.muted, style = type.style(9f, 14f))
            Surface(color = colors.surface, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("志愿者", color = colors.primary, style = type.style(8f, 13f, FontWeight.Medium))
                    Text("这里最不一样的是什么？", color = colors.ink, style = type.style(10f, 16f))
                }
            }
            Surface(color = colors.successSoft, shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("小宇", color = colors.success, style = type.style(8f, 13f, FontWeight.Medium))
                    Text("门关上以后，能听见自己翻书的声音。", color = colors.ink, style = type.style(10f, 16f))
                }
            }
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun ArticleTrialCard(state: FormalPublicArticleUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalSectionLabel("试点记录", trailing = "演示数据")
    Spacer(Modifier.height(9.dp))
    FormalOutlinedCard(background = colors.successSoft, border = Color(0xFFB8DEC9)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                state.trialWeeks to "连续开放",
                state.participantCount to "服务学生",
                state.averageMinutes to "工作日平均到访"
            ).forEachIndexed { index, pair ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(pair.first, color = Color(0xFF217755), style = type.style(20f, 26f, FontWeight.Medium))
                    Text(pair.second, color = colors.muted, style = type.style(8f, 13f))
                }
                if (index != 2) Box(Modifier.width(1.dp).height(54.dp).background(Color(0xFFB8DEC9)))
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun ArticleRelated(items: List<FormalRelatedArticle>, onClick: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalSectionLabel("相关阅读")
    Spacer(Modifier.height(9.dp))
    items.forEach { item ->
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = item.title) { onClick(item.id) }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(58.dp).background(colors.primarySoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Description, null, tint = colors.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
                Text(item.subtitle, color = colors.faint, style = type.style(8f, 13f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(19.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
    }
    Spacer(Modifier.height(24.dp))
}
