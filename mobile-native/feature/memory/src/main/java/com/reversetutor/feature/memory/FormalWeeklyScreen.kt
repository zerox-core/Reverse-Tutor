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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun FormalWeeklyDashboardScreen(
    dashboardState: WeeklyDashboardUiState,
    sessionOptions: List<FormalWeeklySessionOption> = emptyList(),
    selectedSessionIds: Set<String> = emptySet(),
    onApplySessionScope: (Set<String>) -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onOpenQuestion: (String) -> Unit = {},
    onAddSuggestion: (String) -> Unit = {},
    onQuickSwitchModel: () -> Unit = {},
    onEditWidgetLayout: () -> Unit = {},
    showSpatialIndicator: Boolean = true,
    modifier: Modifier = Modifier
) {
    FormalWeeklyScreen(
        state = dashboardState.toFormalWeeklyUiState(
            sessionOptions = sessionOptions,
            selectedSessionIds = selectedSessionIds
        ),
        onApplySessionScope = onApplySessionScope,
        onOpenSession = onOpenSession,
        onOpenQuestion = onOpenQuestion,
        onAddSuggestion = onAddSuggestion,
        onQuickSwitchModel = onQuickSwitchModel,
        onEditWidgetLayout = onEditWidgetLayout,
        showSpatialIndicator = showSpatialIndicator,
        modifier = modifier
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun FormalWeeklyScreen(
    state: FormalWeeklyUiState,
    onApplySessionScope: (Set<String>) -> Unit = {},
    onOpenSession: (String) -> Unit = {},
    onOpenQuestion: (String) -> Unit = {},
    onAddSuggestion: (String) -> Unit = {},
    onQuickSwitchModel: () -> Unit = {},
    onEditWidgetLayout: () -> Unit = {},
    showSpatialIndicator: Boolean = true,
    initialFirstVisibleItemIndex: Int = 0,
    initialShowScopeSheet: Boolean = false,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialFirstVisibleItemIndex)
    val showCollapsedHeader by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 64
        }
    }
    var showScopeSheet by remember(initialShowScopeSheet) { mutableStateOf(initialShowScopeSheet) }
    var appliedSelection by remember(state.normalizedSelectedSessionIds) {
        mutableStateOf(state.normalizedSelectedSessionIds)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeeklyBackground)
            .testTag("formal-weekly-screen")
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (showSpatialIndicator) 30.dp else 24.dp)
        ) {
            item(key = "weekly-header") {
                WeeklyHeroHeader(
                    periodLabel = state.periodLabel,
                    onEditWidgetLayout = onEditWidgetLayout
                )
            }
            item(key = "weekly-overview") {
                WeeklyOverviewSection(
                    state = state,
                    onOpenSession = onOpenSession
                )
            }
            item(key = "weekly-overview-boundary") {
                Spacer(Modifier.height(64.dp))
            }
            item(key = "weekly-mainlines") {
                WeeklyMainlinesSection(
                    state = state,
                    selectedCount = appliedSelection.size,
                    onOpenScope = { showScopeSheet = true },
                    onOpenQuestion = onOpenQuestion,
                    onAddSuggestion = onAddSuggestion
                )
            }
            item(key = "weekly-rhythm") {
                WeeklyRhythmSection(
                    state = state,
                    onOpenSession = onOpenSession,
                    onQuickSwitchModel = onQuickSwitchModel
                )
            }
        }

        if (showCollapsedHeader) {
            WeeklyCollapsedHeader(
                selectedCount = appliedSelection.size,
                onOpenScope = { showScopeSheet = true },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        if (showSpatialIndicator) {
            WeeklySpatialIndicator(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            )
        }
    }

    if (showScopeSheet) {
        WeeklyScopeSheet(
            sessions = state.sessionOptions,
            selectedSessionIds = appliedSelection,
            onDismiss = { showScopeSheet = false },
            onApply = { selected ->
                appliedSelection = selected
                onApplySessionScope(selected)
                showScopeSheet = false
            }
        )
    }
}

@Composable
private fun WeeklyHeroHeader(
    periodLabel: String,
    onEditWidgetLayout: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(start = 18.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "本周",
                color = WeeklyInk,
                fontSize = 23.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = periodLabel.ifBlank { "暂无周报周期" },
                color = WeeklyMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp
            )
        }
        GlossyArrangeButton(onClick = onEditWidgetLayout)
    }
    WeeklyDivider()
}

@Composable
private fun WeeklyCollapsedHeader(
    selectedCount: Int,
    onOpenScope: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .shadow(3.dp),
        color = WeeklyHeader.copy(alpha = 0.98f),
        contentColor = WeeklyInk
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本周学习",
                modifier = Modifier.weight(1f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
            )
            WeeklyScopeButton(
                label = if (selectedCount > 0) "已选 $selectedCount 个" else "选择会话范围",
                onClick = onOpenScope
            )
        }
    }
}

@Composable
private fun WeeklyOverviewSection(
    state: FormalWeeklyUiState,
    onOpenSession: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WeeklyMetricsCard(state.metrics)
        WeeklyNarrative(state)
        WeeklyPlanCard(state.plans)
        WeeklyCompactWidgets(
            questions = state.questions,
            challenge = state.challenge,
            onOpenSession = onOpenSession
        )
        WeeklyActivity(state.activities)
        WeeklyModelFooter(state.modelUsage)
    }
}

@Composable
private fun WeeklyMetricsCard(metrics: List<FormalWeeklyMetric>) {
    FormalWeeklyCard(modifier = Modifier.height(76.dp)) {
        Row(modifier = Modifier.fillMaxSize()) {
            metrics.take(4).forEachIndexed { index, metric ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .width(1.dp)
                            .height(36.dp)
                            .background(WeeklyDividerColor)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = metric.value ?: "—",
                        color = if (metric.value == null) WeeklyMuted else WeeklyInk,
                        fontSize = 17.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = metric.label,
                        color = WeeklyBody,
                        fontSize = 10.sp,
                        lineHeight = 18.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyNarrative(state: FormalWeeklyUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 108.dp)
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(WeeklyBlue, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "跨会话总结",
                color = Color(0xFF4D6E9E),
                fontSize = 11.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = state.narrative.ifBlank {
                when {
                    state.isLoading -> "正在整理本周学习内容"
                    state.errorMessage != null -> "本周总结暂时无法读取"
                    else -> "本周还没有可汇总的学习记录"
                }
            },
            color = WeeklyInk,
            fontSize = 16.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = when {
                state.unresolvedSummary.isNotBlank() -> state.unresolvedSummary
                !state.isOnline -> "当前离线，显示本地记录"
                state.errorMessage != null -> state.errorMessage
                else -> ""
            },
            color = WeeklyMuted,
            fontSize = 11.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WeeklyPlanCard(plans: List<FormalWeeklyPlanItem>) {
    FormalWeeklyCard(modifier = Modifier.height(190.dp)) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "今日计划",
                    modifier = Modifier.weight(1f),
                    color = WeeklyInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (plans.isEmpty()) "0 / 0" else "${plans.count { it.completed }} / ${plans.size}",
                    color = Color(0xFF4578B8),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            if (plans.isEmpty()) {
                WeeklyEmptyLabel("今天还没有计划")
            } else {
                plans.take(3).forEach { plan ->
                    WeeklyPlanRow(plan)
                }
            }
        }
    }
}

@Composable
private fun WeeklyPlanRow(plan: FormalWeeklyPlanItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WeeklyCheckmark(checked = plan.completed)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOf(plan.timeLabel, plan.title).filter { it.isNotBlank() }.joinToString("  "),
                color = if (plan.completed) WeeklyMuted else Color(0xFF303D4F),
                fontSize = 11.sp,
                lineHeight = 18.sp,
                fontWeight = if (plan.completed) FontWeight.Normal else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (plan.sessionLabel.isNotBlank()) {
                Text(
                    text = plan.sessionLabel,
                    color = Color(0xFF7A8AA1),
                    fontSize = 9.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WeeklyCompactWidgets(
    questions: List<FormalWeeklyQuestion>,
    challenge: FormalWeeklyChallenge?,
    onOpenSession: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormalWeeklyCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(11.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "待解决问题",
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF597396),
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = questions.size.toString(),
                        color = Color(0xFF3363AD),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                questions.take(2).forEach { question ->
                    Text(
                        text = question.title,
                        color = Color(0xFF2E3B4F),
                        fontSize = 11.sp,
                        lineHeight = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (questions.isEmpty()) {
                    Text("暂无待解决问题", color = WeeklyMuted, fontSize = 10.sp, lineHeight = 24.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "查看关联节点",
                    color = Color(0xFF407AC4),
                    fontSize = 9.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            color = Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xBF8ABFB0))
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFDEF5ED), Color(0xFFEDF5FF))
                        )
                    )
                    .clickable(enabled = challenge != null) {
                        challenge?.let { onOpenSession(it.sessionId) }
                    }
                    .padding(11.dp)
            ) {
                Text(
                    text = "当前挑战",
                    color = Color(0xFF337563),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = challenge?.title ?: "暂无进行中的挑战",
                    color = Color(0xFF1C3840),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = challenge?.let { "${it.dayLabel} · ${it.scheduleLabel}" }.orEmpty(),
                    color = Color(0xFF4F7078),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                WeeklyProgress(
                    progress = challenge?.progress ?: 0,
                    total = challenge?.total ?: 0
                )
            }
        }
    }
}

@Composable
private fun WeeklyActivity(activities: List<FormalWeeklyActivity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "最近学习动态",
            color = Color(0xFF40526B),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium
        )
        if (activities.isEmpty()) {
            WeeklyEmptyLabel("暂无学习动态")
        } else {
            activities.take(2).forEach { activity ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(WeeklyBlue, CircleShape))
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = listOf(activity.timeLabel, activity.title)
                            .filter { it.isNotBlank() }
                            .joinToString("  "),
                        color = Color(0xFF3B4A61),
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyModelFooter(usage: FormalWeeklyModelUsage?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        WeeklyDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(if (usage == null) WeeklyDividerColor else WeeklyGreen, CircleShape)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = usage?.modelName ?: "未连接模型",
                modifier = Modifier.weight(1f),
                color = Color(0xFF3D4F69),
                fontSize = 11.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = usage?.totalTokensLabel ?: "—",
                color = Color(0xFF78879E),
                fontSize = 10.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.width(7.dp))
            Text("›", color = Color(0xFF5279A9), fontSize = 15.sp)
        }
    }
}

@Composable
private fun WeeklyMainlinesSection(
    state: FormalWeeklyUiState,
    selectedCount: Int,
    onOpenScope: () -> Unit,
    onOpenQuestion: (String) -> Unit,
    onAddSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本周学习",
                modifier = Modifier.weight(1f),
                color = WeeklyInk,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
            )
            WeeklyScopeButton(
                label = if (selectedCount > 0) "本周活跃 · $selectedCount" else "选择会话范围",
                onClick = onOpenScope
            )
        }
        WeeklyMainlinesCard(state.mainlines)
        WeeklyWeakPoints(state.weakPoints)
        WeeklyQuestionsCard(state.questions, onOpenQuestion)
        WeeklySuggestions(state.suggestions, onAddSuggestion)
    }
}

@Composable
private fun WeeklyMainlinesCard(mainlines: List<FormalWeeklyMainline>) {
    FormalWeeklyCard(modifier = Modifier.heightIn(min = 231.dp)) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "本周学习主线",
                    modifier = Modifier.weight(1f),
                    color = WeeklyInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "根据所选会话整理",
                    color = Color(0xFF7A8AA1),
                    fontSize = 9.sp,
                    lineHeight = 15.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            if (mainlines.isEmpty()) {
                WeeklyEmptyLabel("所选会话还没有形成学习主线")
            } else {
                mainlines.take(3).forEachIndexed { index, mainline ->
                    WeeklyMainlineRow(
                        mainline = mainline,
                        accent = MainlineColors[index % MainlineColors.size]
                    )
                    if (index < minOf(mainlines.lastIndex, 2)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp)
                                .height(1.dp)
                                .background(WeeklyDividerColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyMainlineRow(mainline: FormalWeeklyMainline, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(63.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(42.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(accent, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mainline.title,
                color = Color(0xFF29364A),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = mainline.concepts,
                color = Color(0xFF526178),
                fontSize = 10.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "来自 ${mainline.sourceCount} 个会话",
            color = Color(0xFF8594A8),
            fontSize = 8.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun WeeklyWeakPoints(weakPoints: List<FormalWeeklyWeakPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "跨会话薄弱点",
                modifier = Modifier.weight(1f),
                color = WeeklyInk,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${weakPoints.size} 项反复出现",
                color = Color(0xFF7A8AA1),
                fontSize = 9.sp,
                lineHeight = 15.sp
            )
        }
        if (weakPoints.isEmpty()) {
            WeeklyEmptyLabel("暂无重复出现的薄弱点")
        } else {
            weakPoints.take(3).forEachIndexed { index, point ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 5.dp)
                            .size(8.dp)
                            .background(WeakPointColors[index % WeakPointColors.size], CircleShape)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = point.title,
                            color = Color(0xFF2E3B4F),
                            fontSize = 11.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = point.sourceLabel,
                            color = Color(0xFF808FA6),
                            fontSize = 8.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyQuestionsCard(
    questions: List<FormalWeeklyQuestion>,
    onOpenQuestion: (String) -> Unit
) {
    FormalWeeklyCard {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "尚未完成的问题",
                    modifier = Modifier.weight(1f),
                    color = WeeklyInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = questions.size.toString(),
                    color = WeeklyBlue,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (questions.isEmpty()) {
                WeeklyEmptyLabel("暂无未完成的问题")
            } else {
                questions.take(3).forEachIndexed { index, question ->
                    if (index > 0) WeeklyDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenQuestion(question.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = question.title,
                                color = Color(0xFF2E3B4F),
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = question.sessionLabel,
                                color = Color(0xFF808FA6),
                                fontSize = 8.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Text("›", color = WeeklyBlue, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklySuggestions(
    suggestions: List<FormalWeeklySuggestion>,
    onAddSuggestion: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "下一步学习建议",
                modifier = Modifier.weight(1f),
                color = WeeklyInk,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "确认后才加入计划",
                color = Color(0xFF7A8AA1),
                fontSize = 9.sp,
                lineHeight = 15.sp
            )
        }
        if (suggestions.isEmpty()) {
            WeeklyEmptyLabel("暂无新的学习建议")
        } else {
            suggestions.take(2).forEachIndexed { index, suggestion ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = WeeklyCard,
                    contentColor = WeeklyInk,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, WeeklyBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(38.dp)
                                .background(MainlineColors[index % MainlineColors.size], RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = suggestion.title,
                                color = Color(0xFF2E3B4F),
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = suggestion.relationLabel,
                                color = Color(0xFF808FA6),
                                fontSize = 8.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Surface(
                            onClick = { onAddSuggestion(suggestion.id) },
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFFE8F0FA),
                            contentColor = WeeklyBlue,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+", fontSize = 20.sp, lineHeight = 20.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyRhythmSection(
    state: FormalWeeklyUiState,
    onOpenSession: (String) -> Unit,
    onQuickSwitchModel: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本周学习",
                modifier = Modifier.weight(1f),
                color = WeeklyInk,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "节奏与用量",
                color = Color(0xFF73859E),
                fontSize = 10.sp,
                lineHeight = 17.sp
            )
        }
        WeeklyRhythmCard(
            rhythm = state.rhythm,
            totalLabel = state.rhythmTotalLabel,
            comparisonLabel = state.rhythmComparisonLabel
        )
        WeeklyChallengeDetail(state.challenge, onOpenSession)
        WeeklyMilestones(state.milestones)
        WeeklyModelUsageCard(state.modelUsage, onQuickSwitchModel)
    }
}

@Composable
private fun WeeklyRhythmCard(
    rhythm: List<Float>,
    totalLabel: String?,
    comparisonLabel: String
) {
    FormalWeeklyCard(modifier = Modifier.height(194.dp)) {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "本周学习节奏",
                        color = WeeklyInk,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = comparisonLabel,
                        color = WeeklyMuted,
                        fontSize = 9.sp,
                        lineHeight = 15.sp
                    )
                }
                Text(
                    text = totalLabel ?: "—",
                    color = if (totalLabel == null) WeeklyMuted else Color(0xFF3D73BA),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(10.dp))
            WeeklyRhythmChart(rhythm)
        }
    }
}

@Composable
private fun WeeklyRhythmChart(values: List<Float>) {
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val value = values.getOrNull(index)?.coerceIn(0f, 1f) ?: 0f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(84.dp)
                        .background(Color(0xFFE5EDF7), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (value > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((84f * value).roundToInt().dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF4F8CDB), Color(0xFF6BBAA6))
                                    ),
                                    RoundedCornerShape(9.dp)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(day, color = Color(0xFF78879E), fontSize = 9.sp, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun WeeklyChallengeDetail(
    challenge: FormalWeeklyChallenge?,
    onOpenSession: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        contentColor = WeeklyInk,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xBF8ABFB0))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(Color(0xFFDEF5ED), Color(0xFFEDF5FF)))
                )
                .padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "当前挑战",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF337563),
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = challenge?.dayLabel.orEmpty(),
                    color = Color(0xFF47706E),
                    fontSize = 10.sp,
                    lineHeight = 16.sp
                )
            }
            Text(
                text = challenge?.title ?: "暂无进行中的挑战",
                color = Color(0xFF1C3840),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium
            )
            if (challenge != null) {
                Text("今天的任务", color = Color(0xFF4F7078), fontSize = 9.sp, lineHeight = 14.sp)
                Text(
                    text = challenge.taskTitle,
                    color = Color(0xFF2A454B),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                WeeklyProgress(challenge.progress, challenge.total)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = challenge.scheduleLabel,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF47706E),
                        fontSize = 9.sp,
                        lineHeight = 14.sp
                    )
                    TextButton(
                        onClick = { onOpenSession(challenge.sessionId) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("进入会话", color = Color(0xFF2F735D), fontSize = 10.sp)
                        Spacer(Modifier.width(3.dp))
                        Text("›", color = Color(0xFF2F735D), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyMilestones(milestones: List<FormalWeeklyMilestone>) {
    FormalWeeklyCard {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "本周完成",
                    modifier = Modifier.weight(1f),
                    color = WeeklyInk,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${milestones.size} 项",
                    color = WeeklyGreen,
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (milestones.isEmpty()) {
                WeeklyEmptyLabel("本周还没有已完成事项")
            } else {
                milestones.take(3).forEach { milestone ->
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WeeklyCheckmark(checked = true)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = milestone.title,
                                color = Color(0xFF2E3B4F),
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (milestone.sourceLabel.isNotBlank()) {
                                Text(
                                    text = milestone.sourceLabel,
                                    color = Color(0xFF808FA6),
                                    fontSize = 8.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyModelUsageCard(
    usage: FormalWeeklyModelUsage?,
    onQuickSwitchModel: () -> Unit
) {
    FormalWeeklyCard {
        Column(modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "模型与用量",
                        color = WeeklyInk,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = usage?.totalTokensLabel ?: "暂无 Token 统计",
                        color = WeeklyMuted,
                        fontSize = 9.sp,
                        lineHeight = 15.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = usage?.modelName ?: "未连接模型",
                        color = Color(0xFF3D4F69),
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        enabled = usage != null,
                        onClick = onQuickSwitchModel,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("快速切换", color = WeeklyBlue, fontSize = 9.sp)
                    }
                }
            }
            if (usage != null && usage.segments.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0xFFE5EDF7), RoundedCornerShape(4.dp))
                ) {
                    usage.segments.forEachIndexed { index, segment ->
                        Box(
                            Modifier
                                .weight(segment.fraction.coerceAtLeast(0.01f))
                                .fillMaxHeight()
                                .background(UsageColors[index % UsageColors.size])
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    usage.segments.forEachIndexed { index, segment ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(UsageColors[index % UsageColors.size], CircleShape)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${segment.label} ${segment.tokensLabel}",
                                color = Color(0xFF526178),
                                fontSize = 8.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "用量按会话统计，密钥不在此页面显示",
                color = Color(0xFF808FA6),
                fontSize = 8.sp,
                lineHeight = 14.sp
            )
        }
    }
}

private enum class WeeklyScopeMode(val label: String) {
    Active("本周活跃"),
    All("全部会话"),
    Manual("手动选择")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WeeklyScopeSheet(
    sessions: List<FormalWeeklySessionOption>,
    selectedSessionIds: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(WeeklyScopeMode.Manual) }
    var query by remember { mutableStateOf("") }
    var draftSelection by remember(selectedSessionIds, sessions) {
        mutableStateOf<Set<String>>(
            selectedSessionIds.filterTo(linkedSetOf()) { id -> sessions.any { it.id == id } }
        )
    }
    val visibleSessions = remember(query, sessions) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            sessions
        } else {
            sessions.filter { option ->
                option.title.contains(normalizedQuery, ignoreCase = true) ||
                    option.detail.contains(normalizedQuery, ignoreCase = true)
            }
        }
    }

    LaunchedEffect(mode, sessions) {
        when (mode) {
            WeeklyScopeMode.Active -> draftSelection = sessions
                .filter { it.activeThisWeek }
                .mapTo(linkedSetOf()) { it.id }
            WeeklyScopeMode.All -> draftSelection = sessions.mapTo(linkedSetOf()) { it.id }
            WeeklyScopeMode.Manual -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.widthIn(max = 390.dp).testTag("weekly-scope-sheet"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = WeeklyCard,
        contentColor = WeeklyInk,
        tonalElevation = 0.dp,
        scrimColor = Color(0x421F2B3D),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(616.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 9.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .background(Color(0xA6A8B5C7), RoundedCornerShape(2.dp))
                    .align(Alignment.CenterHorizontally)
            )
            Text(
                text = "选择会话范围",
                modifier = Modifier.padding(start = 20.dp, top = 14.dp),
                color = WeeklyInk,
                fontSize = 19.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "决定本周学习主线的整理来源",
                modifier = Modifier.padding(start = 20.dp, top = 1.dp),
                color = Color(0xFF75859C),
                fontSize = 10.sp,
                lineHeight = 17.sp
            )
            WeeklyScopeSegmentedControl(
                mode = mode,
                onModeChange = { mode = it },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
            WeeklySessionSearch(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择会话",
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF45546B),
                    fontSize = 11.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "已选 ${draftSelection.size} 个",
                    color = Color(0xFF5280BA),
                    fontSize = 9.sp,
                    lineHeight = 16.sp
                )
            }
            if (visibleSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (sessions.isEmpty()) "暂无可选择的会话" else "没有匹配的会话",
                        color = WeeklyMuted,
                        fontSize = 11.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(visibleSessions, key = { it.id }) { option ->
                        WeeklySessionOptionRow(
                            option = option,
                            selected = option.id in draftSelection,
                            onToggle = {
                                mode = WeeklyScopeMode.Manual
                                draftSelection = if (option.id in draftSelection) {
                                    draftSelection - option.id
                                } else {
                                    draftSelection + option.id
                                }
                            }
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFEAF2FB),
                contentColor = Color(0xFF526A87)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFFD8E8F8), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("i", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "仅影响本周学习主线，不修改世界树或会话内容",
                        fontSize = 9.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            WeeklyDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(46.dp)
                ) {
                    Text("取消", color = Color(0xFF65738A), fontSize = 12.sp)
                }
                Button(
                    onClick = { onApply(draftSelection) },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("apply-weekly-session-scope"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E5CDE)),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text(
                        text = "应用 ${draftSelection.size} 个会话",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyScopeSegmentedControl(
    mode: WeeklyScopeMode,
    onModeChange: (WeeklyScopeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(Color(0xFFE8F0FA), RoundedCornerShape(10.dp))
            .padding(3.dp)
    ) {
        WeeklyScopeMode.entries.forEach { option ->
            val selected = option == mode
            Surface(
                onClick = { onModeChange(option) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                color = if (selected) Color(0xFFFEFEFF) else Color.Transparent,
                contentColor = if (selected) Color(0xFF3B6BAD) else Color(0xFF6B7A91),
                shape = RoundedCornerShape(8.dp),
                border = if (selected) BorderStroke(1.dp, Color(0xB8B8CCE5)) else null,
                shadowElevation = if (selected) 2.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = option.label,
                        fontSize = 10.sp,
                        lineHeight = 17.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklySessionSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color(0xFFF0F5FB), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchIcon()
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                cursorBrush = SolidColor(WeeklyBlue),
                textStyle = TextStyle(
                    color = WeeklyInk,
                    fontSize = 11.sp,
                    lineHeight = 18.sp
                )
            )
            if (query.isEmpty()) {
                Text(
                    text = "搜索会话",
                    color = Color(0xFF828FA6),
                    fontSize = 10.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun WeeklySessionOptionRow(
    option: FormalWeeklySessionOption,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onToggle)
            .testTag("weekly-session-${option.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WeeklyCheckbox(selected)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                color = Color(0xFF2E3B4F),
                fontSize = 11.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = option.detail,
                color = Color(0xFF808FA6),
                fontSize = 8.sp,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    WeeklyDivider()
}

@Composable
private fun WeeklyCheckbox(selected: Boolean) {
    Surface(
        modifier = Modifier.size(22.dp),
        color = if (selected) Color(0xFF3E76BC) else Color.Transparent,
        contentColor = Color.White,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) Color(0xFF3E76BC) else Color(0xFFB8C6D9))
    ) {
        if (selected) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.08f, size.height * 0.52f),
                    end = Offset(size.width * 0.40f, size.height * 0.82f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * 0.40f, size.height * 0.82f),
                    end = Offset(size.width * 0.95f, size.height * 0.14f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun WeeklyScopeButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 130.dp)
            .testTag("weekly-scope-button"),
        color = Color(0xFFF4F9FF),
        contentColor = Color(0xFF3B66A3),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xD9B5CCE8)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Spacer(Modifier.width(7.dp))
            Text("⌄", fontSize = 13.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun GlossyArrangeButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        color = Color.Transparent,
        contentColor = Color(0xFF496D9D),
        shape = CircleShape,
        border = BorderStroke(1.dp, Color(0xCCC2D6F0))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(listOf(Color(0xFFE3F0FF), Color(0xFFFAFCFF)))
            ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(16.dp)) {
                val tileSize = 5.dp.toPx()
                val gap = 3.dp.toPx()
                val cornerRadius = CornerRadius(1.4.dp.toPx())
                listOf(
                    Offset.Zero,
                    Offset(tileSize + gap, 0f),
                    Offset(0f, tileSize + gap),
                    Offset(tileSize + gap, tileSize + gap)
                ).forEach { topLeft ->
                    drawRoundRect(
                        color = Color(0xFF496D9D),
                        topLeft = topLeft,
                        size = Size(tileSize, tileSize),
                        cornerRadius = cornerRadius
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchIcon() {
    Canvas(Modifier.size(17.dp)) {
        val color = Color(0xFF71839B)
        drawCircle(
            color = color,
            radius = size.minDimension * 0.28f,
            center = Offset(size.width * 0.43f, size.height * 0.43f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.64f, size.height * 0.64f),
            end = Offset(size.width * 0.88f, size.height * 0.88f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun WeeklyCheckmark(checked: Boolean) {
    Surface(
        modifier = Modifier.size(18.dp),
        color = if (checked) Color(0xFFDDEEE8) else Color.Transparent,
        contentColor = if (checked) WeeklyGreen else Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (checked) WeeklyGreen else Color(0xFFB8C6D9))
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center) {
                Text("✓", fontSize = 11.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WeeklyProgress(progress: Int, total: Int) {
    val fraction = if (total <= 0) 0f else progress.toFloat().div(total).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(Color(0x8CB2D1CC), RoundedCornerShape(3.dp))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(5.dp)
                        .background(Color(0xFF2E9470), RoundedCornerShape(3.dp))
                )
            }
        }
        Text(
            text = if (total > 0) "$progress / $total" else "—",
            modifier = Modifier.align(Alignment.End),
            color = Color(0xFF47706E),
            fontSize = 9.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun WeeklySpatialIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(4.dp)
                .background(Color(0xFFCAD4E3), CircleShape)
        )
        Box(
            Modifier
                .width(12.dp)
                .height(4.dp)
                .background(WeeklyBlue, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun FormalWeeklyCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = WeeklyCard,
        contentColor = WeeklyInk,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, WeeklyBorder),
        content = content
    )
}

@Composable
private fun WeeklyDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WeeklyDividerColor)
    )
}

@Composable
private fun WeeklyEmptyLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, color = WeeklyMuted, fontSize = 10.sp, lineHeight = 17.sp)
    }
}

private val WeeklyBackground = Color(0xFFF4F7FC)
private val WeeklyHeader = Color(0xFFF8FAFE)
private val WeeklyCard = Color(0xFFFBFDFF)
private val WeeklyBorder = Color(0xBFC9D9EB)
private val WeeklyDividerColor = Color(0xCCD6DEEB)
private val WeeklyInk = Color(0xFF1F293B)
private val WeeklyBody = Color(0xFF63738A)
private val WeeklyMuted = Color(0xFF738096)
private val WeeklyBlue = Color(0xFF3B6BAD)
private val WeeklyGreen = Color(0xFF2E9470)
private val MainlineColors = listOf(Color(0xFF4080D6), Color(0xFF339E78), Color(0xFFBD7D33))
private val WeakPointColors = listOf(Color(0xFFD06A63), Color(0xFFB67B35), Color(0xFF637FB7))
private val UsageColors = listOf(Color(0xFF4F8CDB), Color(0xFF6BBAA6), Color(0xFFBD8B54))
