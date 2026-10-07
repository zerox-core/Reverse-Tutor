@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalElevations
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import com.reversetutor.core.domain.LearningOverviewScope
import com.reversetutor.core.domain.LearningThreadContract
import com.reversetutor.core.domain.WeakPointContract

/**
 * Home learning overview side-panel. Pure presentation of
 * [LearningOverviewUiState]: no mastery, mainline, or weak-point derivation —
 * every value is projected from the read model. Dispatches scope switches,
 * refresh, and navigation intents only.
 *
 * B3: [currentSessionId] controls the "当前会话" scope chip visibility.
 * When null/blank, only "全部会话" is shown. When non-blank, clicking
 * "当前会话" dispatches [LearningOverviewScope.copy] with
 * `sessionIds = listOf(currentSessionId)` — never `emptyList()`.
 */
@Composable
fun LearningOverviewPanel(
    state: LearningOverviewUiState,
    onRefresh: () -> Unit,
    onChangeScope: (LearningOverviewScope) -> Unit,
    onOpenWeekly: () -> Unit,
    onOpenWeakPoint: (WeakPointContract) -> Unit = {},
    currentSessionId: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = FormalColors.Background,
        shape = RoundedCornerShape(FormalShapes.CardRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OverviewHeader(state = state, onRefresh = onRefresh)
            ScopeSwitcher(
                state = state,
                onChangeScope = onChangeScope,
                currentSessionId = currentSessionId
            )
            when {
                state.isLoading -> LoadingBlock()
                state.errorMessage != null -> ErrorBlock(message = state.errorMessage, onRetry = onRefresh)
                state.isNoData -> EmptyBlock(onRefresh = onRefresh)
                else -> OverviewBody(
                    state = state,
                    onOpenWeekly = onOpenWeekly,
                    onOpenWeakPoint = onOpenWeakPoint
                )
            }
        }
    }
}

@Composable
private fun OverviewHeader(state: LearningOverviewUiState, onRefresh: () -> Unit) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "学习概览",
                style = type.style(15f, 21f, FontWeight.Bold, FormalColors.Ink)
            )
            if (state.generatedAtLabel.isNotBlank()) {
                Text(
                    text = "更新于 ${state.generatedAtLabel}",
                    style = type.style(10f, 14f, color = FormalColors.Muted)
                )
            }
        }
        Surface(
            onClick = onRefresh,
            color = Color.Transparent,
            contentColor = FormalColors.Muted,
            modifier = Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .testTag("learning_overview_refresh")
                .semantics { contentDescription = "刷新学习概览" }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("刷新", style = type.style(11f, 16f, color = FormalColors.Muted))
            }
        }
    }
}

@Composable
private fun ScopeSwitcher(
    state: LearningOverviewUiState,
    onChangeScope: (LearningOverviewScope) -> Unit,
    currentSessionId: String?
) {
    val isAll = state.scope.sessionIds == null
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScopeChip(
            label = "全部会话",
            selected = isAll,
            onClick = { onChangeScope(state.scope.copy(sessionIds = null)) },
            testTag = "scope_all_sessions"
        )
        // B3: only show "当前会话" when a real session id is available.
        // Never dispatch emptyList() — null = all sessions, [] = zero sessions.
        if (!currentSessionId.isNullOrBlank()) {
            ScopeChip(
                label = "当前会话",
                selected = !isAll,
                onClick = {
                    onChangeScope(state.scope.copy(sessionIds = listOf(currentSessionId)))
                },
                testTag = "scope_current_session"
            )
        }
    }
}

@Composable
private fun ScopeChip(label: String, selected: Boolean, onClick: () -> Unit, testTag: String) {
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(FormalShapes.PillRadius),
        color = if (selected) FormalColors.Primary else FormalColors.SurfaceElevated,
        border = if (selected) null else BorderStroke(1.dp, FormalColors.BorderStrong),
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = type.style(11f, 16f, FontWeight.Medium, if (selected) Color.White else FormalColors.Ink),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun OverviewBody(
    state: LearningOverviewUiState,
    onOpenWeekly: () -> Unit,
    onOpenWeakPoint: (WeakPointContract) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // B2: non-blocking warning banner — does not degrade to error page
        if (state.warnings.isNotEmpty()) {
            WarningBanner(warnings = state.warnings)
        }
        ProgressCard(state = state)
        TodayPlanCard(state = state)
        WeeklyMainlineCard(state = state, onOpenWeekly = onOpenWeekly)
        WeakPointCard(state = state, onOpenWeakPoint = onOpenWeakPoint)
        TokenUsageCard(state = state)
    }
}

@Composable
private fun WarningBanner(warnings: List<String>) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FormalColors.WarningSoft,
        shape = RoundedCornerShape(FormalShapes.CompactRadius),
        border = BorderStroke(1.dp, FormalColors.Warning)
    ) {
        Text(
            text = if (warnings.isNotEmpty()) warnings.first() else "部分学习数据暂不可用",
            style = type.style(11f, 16f, FontWeight.Medium, FormalColors.Warning),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ProgressCard(state: LearningOverviewUiState) {
    val type = LocalFormalTypeScale.current
    val (masteryLabel, changeLabel) = formatMasteryDisplay(
        state.masteryPercent, state.weeklyChangePercent
    )
    OverviewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("掌握度", style = type.style(11f, 16f, color = FormalColors.Muted))
                Text(
    masteryLabel,
                    style = type.style(22f, 30f, FontWeight.Bold, FormalColors.Primary)
                )
                Text(changeLabel, style = type.style(10f, 14f, color = FormalColors.Muted))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${state.progress.masteredCount}/${state.progress.totalKnowledgePoints}",
                    style = type.style(13f, 18f, FontWeight.Medium, FormalColors.Ink)
                )
                Text("已掌握知识点", style = type.style(10f, 14f, color = FormalColors.Muted))
                Spacer(Modifier.height(6.dp))
                Text(
                    "活跃会话 ${state.activeSessionCount}",
                    style = type.style(10f, 14f, color = FormalColors.Muted)
                )
            }
        }
    }
}

@Composable
private fun TodayPlanCard(state: LearningOverviewUiState) {
    val type = LocalFormalTypeScale.current
    val plan = state.todayPlan
    OverviewCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今日计划",
                    style = type.style(12f, 17f, FontWeight.Bold, FormalColors.Ink),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${plan.completedCount}/${plan.totalCount}",
                    style = type.style(11f, 16f, color = FormalColors.Muted)
                )
            }
            if (plan.isEmpty) {
                Text(
                    "今天还没有安排学习计划",
                    style = type.style(11f, 16f, color = FormalColors.Muted)
                )
            } else {
                plan.tasks.take(4).forEach { task ->
                    val status = TodayTaskStatus.from(task.status)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(status.dotColor, RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = task.title,
                            style = type.style(11f, 16f, color = FormalColors.Ink),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyMainlineCard(state: LearningOverviewUiState, onOpenWeekly: () -> Unit) {
    val type = LocalFormalTypeScale.current
    if (state.weeklyMainline.isEmpty()) return
    OverviewCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("本周主线", style = type.style(12f, 17f, FontWeight.Bold, FormalColors.Ink))
            state.weeklyMainline.take(3).forEach { thread ->
                ThreadRow(thread = thread)
            }
            TextButton(
                onClick = onOpenWeekly,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("learning_overview_open_weekly")
                    .semantics { contentDescription = "查看周报" }
            ) {
                Text("查看周报", style = type.style(11f, 16f, color = FormalColors.Primary))
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = FormalColors.Primary
                )
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: LearningThreadContract) {
    val type = LocalFormalTypeScale.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = thread.title,
            style = type.style(11f, 16f, color = FormalColors.Ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(FormalColors.BorderStrong)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(thread.progress.coerceIn(0f, 1f))
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FormalColors.Primary)
            )
        }
    }
}

@Composable
private fun WeakPointCard(state: LearningOverviewUiState, onOpenWeakPoint: (WeakPointContract) -> Unit) {
    val type = LocalFormalTypeScale.current
    if (state.weakPoints.isEmpty()) return
    OverviewCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("薄弱点", style = type.style(12f, 17f, FontWeight.Bold, FormalColors.Ink))
            state.weakPoints.take(4).forEach { weak ->
                Surface(
                    onClick = { onOpenWeakPoint(weak) },
                    color = Color.Transparent,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("learning_overview_weak_point_${weak.id}")
                        .semantics { contentDescription = "薄弱点：${weak.knowledgePoint}" }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = weak.knowledgePoint,
                            style = type.style(11f, 16f, color = FormalColors.Ink),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "错误 ${weak.errorCount}",
                            style = type.style(10f, 14f, color = FormalColors.Muted)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenUsageCard(state: LearningOverviewUiState) {
    val type = LocalFormalTypeScale.current
    val usage = state.tokenUsage
    OverviewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Token 用量",
                style = type.style(12f, 17f, FontWeight.Bold, FormalColors.Ink),
                modifier = Modifier.weight(1f)
            )
            Text(
                usage.totalTokens.toTokenDisplayLabel(usage.isEstimated),
                style = type.style(13f, 18f, FontWeight.Medium, FormalColors.Ink)
            )
        }
    }
}

@Composable
private fun LoadingBlock() {
    val type = LocalFormalTypeScale.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "正在加载学习概览…",
            style = type.style(12f, 17f, color = FormalColors.Muted),
            modifier = Modifier.testTag("learning_overview_loading")
        )
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    val type = LocalFormalTypeScale.current
    OverviewCard(tint = Color(0xFFFDECEC), border = Color(0xFFE8B4B4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = message,
                style = type.style(11f, 16f, color = Color(0xFF9D3340)),
                modifier = Modifier
                    .weight(1f)
                    .testTag("learning_overview_error")
            )
            TextButton(
                onClick = onRetry,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("learning_overview_retry")
                    .semantics { contentDescription = "重试加载学习概览" }
            ) {
                Text("重试", style = type.style(11f, 16f, color = FormalColors.Primary))
            }
        }
    }
}

@Composable
private fun EmptyBlock(onRefresh: () -> Unit) {
    val type = LocalFormalTypeScale.current
    OverviewCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "还没有学习数据",
                style = type.style(13f, 18f, FontWeight.Medium, FormalColors.Ink),
                modifier = Modifier.testTag("learning_overview_empty")
            )
            Text(
                "开始一段会话后，这里会展示本周学习概览",
                style = type.style(11f, 16f, color = FormalColors.Muted)
            )
            TextButton(
                onClick = onRefresh,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("learning_overview_empty_refresh")
                    .semantics { contentDescription = "刷新获取学习数据" }
            ) {
                Text("刷新", style = type.style(11f, 16f, color = FormalColors.Primary))
            }
        }
    }
}

@Composable
private fun OverviewCard(
    tint: Color = FormalColors.SurfaceElevated,
    border: Color = FormalColors.BorderStrong,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tint,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, border),
        shadowElevation = FormalElevations.Raised
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            content()
        }
    }
}
