package com.reversetutor.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.design.FormalColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// region 18 · R68 目标看板（方向A 卡片仪表盘）
//
// 学习目标与计划页 redesign：主目标英雄卡（倒计时圆环 + 状态 chip）+
// 目标拆解卡（阶段里程碑横向旅程 + 本周聚焦）。
//
// R69 迭代（按用户反馈）：
// - 英雄卡加哑光浅色背景（渐变底 + 学习旅程小径图案），副文本说明整页
//   「主目标 → 里程碑 → 本周」的逐层拆解关系；
// - 阶段里程碑改横向排版（LazyRow 站点卡，左右拖动），去掉打叉删除，
//   点卡片勾选、长按删除；
// - 本周聚焦收进里程碑同一张卡、位于其下，视觉上体现「寄托在里程碑下」；
//   「本周第一件事」高亮框让人第一眼看到这周最该做的任务；
// - 状态 chip 不再是摆设：currentState / deadline / stageMilestones 已接线进
//   生成链路的会话模板证据（见 app 模块 SessionPolicyInputMapper），
//   每条消息发出时 AI 都能收到。
//
// 数据结构不变：SessionGoalPlan 七个 String 字段原样保留，清单勾选态用
// GitHub task-list 风格编码进文本（"[x] 已做 / [ ] 未做"，逐行一项），
// 旧数据（无前缀纯文本、"未设置"占位）全自动兼容，导出与快照镜像不受影响。
// 点选类操作（勾选 / 状态 chip / 截止时间）走 coordinator.applyGoalPlanImmediate
// 即时落盘；主目标文本仍走暂存 + commitTextBoundary 的受保护确认流程。

// —— 纯逻辑（可单测）——

data class GoalChecklistItem(val text: String, val done: Boolean = false)

/** 清单 → 文本：每项一行，"[x] "/"[ ] " 前缀表示勾选态。 */
fun encodeGoalChecklist(items: List<GoalChecklistItem>): String =
    items.filter { it.text.isNotBlank() }
        .joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text.trim() }

/** 文本 → 清单：兼容旧数据——无前缀的行视为未完成；常见列表符号（- • · *）自动剥掉。 */
fun decodeGoalChecklist(raw: String): List<GoalChecklistItem> {
    val text = raw.trim()
    if (text.isEmpty() || text == "未设置") return emptyList()
    return text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            when {
                line.startsWith("[x]", ignoreCase = true) ->
                    GoalChecklistItem(line.substring(3).trim(), done = true)
                line.startsWith("[ ]") ->
                    GoalChecklistItem(line.substring(3).trim(), done = false)
                else ->
                    GoalChecklistItem(line.trimStart('-', '•', '·', '*', ' ').trim(), done = false)
            }
        }
        .filter { it.text.isNotBlank() }
}

fun goalChecklistDoneCount(items: List<GoalChecklistItem>): Int = items.count { it.done }

fun goalChecklistProgress(items: List<GoalChecklistItem>): Float =
    if (items.isEmpty()) 0f else goalChecklistDoneCount(items).toFloat() / items.size

/** 本周聚焦里第一件还没做的事——第一眼要看到的那件。 */
fun goalNextPendingItem(items: List<GoalChecklistItem>): GoalChecklistItem? =
    items.firstOrNull { !it.done }

enum class GoalDeadlineTone { Calm, Soon, Overdue }

private val GOAL_DEADLINE_FULL_PATTERNS = listOf("yyyy-MM-dd", "yyyy/M/d", "yyyy.MM.dd", "yyyy年M月d日")
private val GOAL_DEADLINE_SHORT_PATTERNS = listOf("M月d日", "M-d", "M/d", "M.d")
private val GOAL_DEADLINE_REGEX = Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})""")

private fun goalStartOfDay(millis: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/**
 * 解析截止时间文本，返回相对 [todayMillis] 的剩余天数（负数 = 已超期），无法解析返回 null。
 * 支持 2026-10-01 / 2026/10/1 / 2026.10.01 / 2026年10月1日 / 10月1日 / 10-01 等写法；
 * 无年份写法默认取今年，已过去则自动顺延到明年；前导文字（"截止：…"）用正则兜底。
 */
fun parseGoalDeadlineDaysLeft(raw: String, todayMillis: Long): Int? {
    val text = raw.trim()
    if (text.isEmpty() || text == "未设置") return null
    val today = goalStartOfDay(todayMillis)

    fun daysFrom(targetMillis: Long): Int =
        ((goalStartOfDay(targetMillis).timeInMillis - today.timeInMillis) / 86_400_000L).toInt()

    for (pattern in GOAL_DEADLINE_FULL_PATTERNS) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }.parse(text)
        }.getOrNull()
        if (parsed != null) return daysFrom(parsed.time)
    }
    for (pattern in GOAL_DEADLINE_SHORT_PATTERNS) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.getDefault()).apply { isLenient = false }.parse(text)
        }.getOrNull() ?: continue
        val target = Calendar.getInstance().apply { time = parsed }
        target.set(Calendar.YEAR, today.get(Calendar.YEAR))
        if (goalStartOfDay(target.timeInMillis).before(today)) target.add(Calendar.YEAR, 1)
        return daysFrom(target.timeInMillis)
    }
    GOAL_DEADLINE_REGEX.find(text)?.let { match ->
        val (year, month, day) = match.destructured
        val m = month.toInt()
        val d = day.toInt()
        if (m in 1..12 && d in 1..31) {
            val target = Calendar.getInstance().apply {
                clear()
                set(year.toInt(), m - 1, d)
            }
            return daysFrom(target.timeInMillis)
        }
    }
    return null
}

fun goalDeadlineLabel(daysLeft: Int): String = when {
    daysLeft < 0 -> "已超期 ${-daysLeft} 天"
    daysLeft == 0 -> "今天截止"
    daysLeft <= 3 -> "仅剩 $daysLeft 天"
    else -> "还剩 $daysLeft 天"
}

fun goalDeadlineTone(daysLeft: Int): GoalDeadlineTone = when {
    daysLeft < 0 -> GoalDeadlineTone.Overdue
    daysLeft <= 3 -> GoalDeadlineTone.Soon
    else -> GoalDeadlineTone.Calm
}

internal data class GoalStatusOption(val label: String, val imageRes: Int)

/** 当前状态三选项，复用表情库素材。 */
internal val GoalStatusOptions = listOf(
    GoalStatusOption("进行中", R.drawable.emo_playful),
    GoalStatusOption("卡住了", R.drawable.emo_sweat),
    GoalStatusOption("已完成", R.drawable.emo_starry)
)

internal fun goalDeadlineToneColor(tone: GoalDeadlineTone): Color = when (tone) {
    GoalDeadlineTone.Calm -> FormalColors.Success
    GoalDeadlineTone.Soon -> FormalColors.Warning
    GoalDeadlineTone.Overdue -> FormalColors.Danger
}

// —— 主目标英雄卡 ——

@Composable
fun GoalDashboardHero(
    goal: String,
    deadline: String,
    currentState: String,
    onGoalCommit: (String) -> Unit,
    onDeadlineCommit: (String) -> Unit,
    onStatusSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    todayMillis: Long = System.currentTimeMillis(),
    testTag: String = "goal-hero"
) {
    var editingGoal by remember { mutableStateOf(false) }
    var goalDraft by remember(goal) { mutableStateOf(goal) }
    var editingDeadline by remember { mutableStateOf(false) }
    var deadlineDraft by remember(deadline) { mutableStateOf(if (deadline == "未设置") "" else deadline) }
    val daysLeft = parseGoalDeadlineDaysLeft(deadline, todayMillis)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFEFF3FF), Color(0xFFFBFCFF))
                )
            )
            .border(1.dp, FormalColors.Divider, RoundedCornerShape(14.dp))
            .testTag(testTag)
    ) {
        GoalHeroBackdrop(Modifier.matchParentSize())
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("主要目标", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Muted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    if (editingGoal) goalDraft = goal
                    editingGoal = !editingGoal
                }) { Text(if (editingGoal) "取消" else "修改", fontSize = 13.sp) }
            }
            Text(
                text = "先写下终点——这一页会把它逐层拆成几站里程碑和这周的行动。",
                fontSize = 12.sp,
                color = FormalColors.Muted
            )
            if (editingGoal) {
                OutlinedTextField(
                    value = goalDraft,
                    onValueChange = { goalDraft = it },
                    modifier = Modifier.fillMaxWidth().testTag("$testTag-goal-input"),
                    placeholder = { Text("用自己的话写下这次要攻克的目标") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onGoalCommit(goalDraft)
                        editingGoal = false
                    })
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = {
                            onGoalCommit(goalDraft)
                            editingGoal = false
                        },
                        enabled = goalDraft.isNotBlank(),
                        modifier = Modifier.testTag("$testTag-goal-done")
                    ) { Text("完成") }
                }
            } else {
                Text(
                    text = goal.ifBlank { "点右上角「修改」，写下这次要攻克的目标" },
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (goal.isBlank()) FormalColors.Muted else FormalColors.Ink
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (daysLeft != null) {
                    GoalCountdownRing(daysLeft)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("截止时间", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Muted)
                    if (editingDeadline) {
                        OutlinedTextField(
                            value = deadlineDraft,
                            onValueChange = { deadlineDraft = it },
                            modifier = Modifier.fillMaxWidth().testTag("$testTag-deadline-input"),
                            placeholder = { Text("如 2026-10-01 或 10月1日") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                onDeadlineCommit(deadlineDraft)
                                editingDeadline = false
                            })
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    onDeadlineCommit(deadlineDraft)
                                    editingDeadline = false
                                },
                                enabled = deadlineDraft.isNotBlank(),
                                modifier = Modifier.testTag("$testTag-deadline-done")
                            ) { Text("保存") }
                            TextButton(onClick = {
                                deadlineDraft = if (deadline == "未设置") "" else deadline
                                editingDeadline = false
                            }) { Text("取消") }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = deadline.ifBlank { "未设置" },
                                fontSize = 15.sp,
                                color = FormalColors.Ink
                            )
                            TextButton(
                                onClick = { editingDeadline = true },
                                modifier = Modifier.testTag("$testTag-deadline-edit")
                            ) { Text("改", fontSize = 13.sp) }
                        }
                        when {
                            daysLeft != null -> Text(
                                text = goalDeadlineLabel(daysLeft),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = goalDeadlineToneColor(goalDeadlineTone(daysLeft))
                            )
                            deadline.isNotBlank() && deadline != "未设置" -> Text(
                                text = "暂时认不出这个日期，试试 2026-10-01 这种写法",
                                fontSize = 12.sp,
                                color = FormalColors.Muted
                            )
                            else -> Text(
                                text = "定个日期，左边的圆环会替你倒数",
                                fontSize = 12.sp,
                                color = FormalColors.Muted
                            )
                        }
                    }
                }
            }

            Text("当前状态", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Muted)
            Text(
                text = "选好后会随每条消息发给 AI，它会照着调整讲课方式。",
                fontSize = 12.sp,
                color = FormalColors.Muted
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoalStatusOptions.forEachIndexed { index, option ->
                    GoalStatusChip(
                        option = option,
                        selected = currentState == option.label,
                        wiggleSeed = index,
                        onClick = { onStatusSelect(option.label) },
                        testTag = "$testTag-status-${option.label}"
                    )
                }
                val custom = currentState.trim()
                if (custom.isNotEmpty() && custom != "未设置" && GoalStatusOptions.none { it.label == custom }) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = FormalColors.PrimarySoft,
                        border = BorderStroke(1.dp, FormalColors.Primary)
                    ) {
                        Text(
                            text = custom,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FormalColors.Primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 英雄卡背景：哑光浅蓝渐变底上的「学习旅程」小径与站点，克制不抢字。 */
@Composable
internal fun GoalHeroBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawCircle(
            color = FormalColors.PrimarySoft.copy(alpha = 0.5f),
            radius = h * 0.62f,
            center = Offset(w * 0.94f, h * 0.04f)
        )
        drawCircle(
            color = FormalColors.SuccessSoft.copy(alpha = 0.45f),
            radius = h * 0.34f,
            center = Offset(w * 0.10f, h * 1.02f)
        )
        val trail = Path().apply {
            moveTo(w * 0.05f, h * 0.96f)
            cubicTo(w * 0.32f, h * 0.78f, w * 0.55f, h * 1.04f, w * 0.82f, h * 0.66f)
        }
        drawPath(
            path = trail,
            color = FormalColors.Primary.copy(alpha = 0.14f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        val stationColor = FormalColors.Primary.copy(alpha = 0.26f)
        drawCircle(stationColor, 3.dp.toPx(), Offset(w * 0.17f, h * 0.86f))
        drawCircle(stationColor, 3.dp.toPx(), Offset(w * 0.46f, h * 0.90f))
        drawCircle(stationColor, 3.dp.toPx(), Offset(w * 0.71f, h * 0.76f))
    }
}

@Composable
internal fun GoalStatusChip(
    option: GoalStatusOption,
    selected: Boolean,
    wiggleSeed: Int,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier.testTag(testTag),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) FormalColors.PrimarySoft else FormalColors.SurfaceSubtle,
        border = BorderStroke(1.dp, if (selected) FormalColors.Primary else FormalColors.Divider)
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onClick)
                .padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            WigglingEmoji(
                imageRes = option.imageRes,
                contentDescription = option.label,
                onClick = onClick,
                selected = selected,
                size = 24.dp,
                wiggleSeed = wiggleSeed,
                testTag = "$testTag-emoji"
            )
            Text(
                text = option.label,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) FormalColors.Primary else FormalColors.Muted
            )
        }
    }
}

/** 倒计时圆环：30 天窗口收拢，超期整圈示警。 */
@Composable
internal fun GoalCountdownRing(
    daysLeft: Int,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp
) {
    val tone = goalDeadlineTone(daysLeft)
    val accent = goalDeadlineToneColor(tone)
    val fraction = if (daysLeft < 0) 1f else (1f - daysLeft / 30f).coerceIn(0.04f, 1f)
    val animatedFraction by animateFloatAsState(targetValue = fraction, animationSpec = tween(600), label = "goal-countdown")
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val diameter = this.size.minDimension - strokeWidth
            val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            drawArc(
                color = FormalColors.Divider,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * animatedFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${if (daysLeft < 0) -daysLeft else daysLeft}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
            Text(
                text = if (daysLeft < 0) "超期" else "天",
                fontSize = 9.sp,
                color = FormalColors.Muted
            )
        }
    }
}

// —— 目标拆解卡：阶段里程碑（横向旅程）→ 本周聚焦（寄托其下）——

@Composable
fun GoalBreakdownCard(
    milestonesRaw: String,
    weeklyRaw: String,
    onMilestonesChange: (List<GoalChecklistItem>) -> Unit,
    onWeeklyChange: (List<GoalChecklistItem>) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "goal-breakdown"
) {
    val milestones = remember(milestonesRaw) { decodeGoalChecklist(milestonesRaw) }
    val weekly = remember(weeklyRaw) { decodeGoalChecklist(weeklyRaw) }
    var milestoneDraft by remember { mutableStateOf("") }
    var weeklyDraft by remember { mutableStateOf("") }

    fun submitMilestone() {
        val text = milestoneDraft.trim()
        if (text.isEmpty()) return
        onMilestonesChange(milestones + GoalChecklistItem(text))
        milestoneDraft = ""
    }

    fun submitWeekly() {
        val text = weeklyDraft.trim()
        if (text.isEmpty()) return
        onWeeklyChange(weekly + GoalChecklistItem(text))
        weeklyDraft = ""
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = FormalColors.Surface,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // —— 阶段里程碑：横向站点旅程 ——
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("阶段里程碑", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Ink)
                Spacer(Modifier.weight(1f))
                if (milestones.isNotEmpty()) {
                    Text(
                        text = "${goalChecklistDoneCount(milestones)}/${milestones.size} 站",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FormalColors.Success
                    )
                }
            }
            Text(
                text = "把大目标拆成几站，左右滑动看全程；点卡片勾掉一站，长按可以删掉。",
                fontSize = 12.sp,
                color = FormalColors.Muted
            )
            if (milestones.isNotEmpty()) {
                GoalProgressBar(
                    progress = goalChecklistProgress(milestones),
                    accent = FormalColors.Success,
                    testTag = "$testTag-milestone-progress"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("$testTag-milestone-journey")
                ) {
                    itemsIndexed(milestones) { index, item ->
                        GoalMilestoneStation(
                            index = index,
                            item = item,
                            onToggle = {
                                onMilestonesChange(
                                    milestones.mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it }
                                )
                            },
                            onRemove = {
                                onMilestonesChange(milestones.filterIndexed { i, _ -> i != index })
                            },
                            testTag = "$testTag-milestone-item-$index"
                        )
                    }
                }
            } else {
                Text(
                    text = "还没有站点，先在下面添上第一站。",
                    fontSize = 13.sp,
                    color = FormalColors.Muted
                )
            }
            GoalAddRow(
                draft = milestoneDraft,
                onDraftChange = { milestoneDraft = it },
                onSubmit = { submitMilestone() },
                placeholder = "加一站，如「刷完导数基础题」",
                inputTag = "$testTag-milestone-input",
                addTag = "$testTag-milestone-add"
            )

            // —— 拆解连接符 ——
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = FormalColors.Tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text("拆到这一周", fontSize = 11.sp, color = FormalColors.Muted)
            }

            // —— 本周聚焦：寄托在里程碑之下 ——
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本周聚焦", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Ink)
                Spacer(Modifier.weight(1f))
                if (weekly.isNotEmpty()) {
                    Text(
                        text = "${goalChecklistDoneCount(weekly)}/${weekly.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FormalColors.Primary
                    )
                }
            }
            Text(
                text = "从里程碑里挑出这周够得着的小事，做完就勾。",
                fontSize = 12.sp,
                color = FormalColors.Muted
            )
            GoalWeeklyNextBanner(weekly = weekly, testTag = "$testTag-weekly-next")
            if (weekly.isNotEmpty()) {
                GoalProgressBar(
                    progress = goalChecklistProgress(weekly),
                    accent = FormalColors.Primary,
                    testTag = "$testTag-weekly-progress"
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    weekly.forEachIndexed { index, item ->
                        GoalWeeklyRow(
                            item = item,
                            onToggle = {
                                onWeeklyChange(
                                    weekly.mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it }
                                )
                            },
                            onRemove = {
                                onWeeklyChange(weekly.filterIndexed { i, _ -> i != index })
                            },
                            testTag = "$testTag-weekly-item-$index"
                        )
                    }
                }
            }
            GoalAddRow(
                draft = weeklyDraft,
                onDraftChange = { weeklyDraft = it },
                onSubmit = { submitWeekly() },
                placeholder = "加一件这周能做的小事…",
                inputTag = "$testTag-weekly-input",
                addTag = "$testTag-weekly-add"
            )
        }
    }
}

/** 里程碑站点卡：横向旅程里的一站，点按勾选、长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GoalMilestoneStation(
    index: Int,
    item: GoalChecklistItem,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    testTag: String
) {
    val bg by animateColorAsState(
        targetValue = if (item.done) FormalColors.SuccessSoft else FormalColors.SurfaceSubtle,
        animationSpec = tween(250),
        label = "$testTag-bg"
    )
    Surface(
        modifier = Modifier
            .width(150.dp)
            .height(102.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = BorderStroke(1.dp, if (item.done) FormalColors.Success else FormalColors.Divider)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(onClick = onToggle, onLongClick = onRemove)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 ${index + 1} 站",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (item.done) FormalColors.Success else FormalColors.Primary
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (item.done) FormalColors.Success else Color.Transparent)
                        .border(1.5.dp, if (item.done) FormalColors.Success else FormalColors.BorderStrong, CircleShape)
                        .testTag("$testTag-check"),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.done) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "已完成",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Text(
                text = item.text,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = if (item.done) FormalColors.Muted else FormalColors.Ink,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 「本周第一件事」高亮框：第一眼就看到这周最该做的任务。 */
@Composable
internal fun GoalWeeklyNextBanner(
    weekly: List<GoalChecklistItem>,
    testTag: String
) {
    val next = goalNextPendingItem(weekly)
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(10.dp),
        color = FormalColors.PrimarySoft,
        border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = 0.35f))
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "本周第一件事",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = FormalColors.Primary
            )
            Text(
                text = when {
                    next != null -> next.text
                    weekly.isEmpty() -> "还没有安排，先在下面添一件这周能做的小事。"
                    else -> "这周的都勾完了，可以往下一周排了。"
                },
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = FormalColors.Ink
            )
        }
    }
}

/** 本周聚焦清单行：点按勾选、长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GoalWeeklyRow(
    item: GoalChecklistItem,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    testTag: String
) {
    val accent = FormalColors.Primary
    val checkboxBg by animateColorAsState(
        targetValue = if (item.done) accent else Color.Transparent,
        animationSpec = tween(250),
        label = "$testTag-check-bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (item.done) FormalColors.Muted else FormalColors.Ink,
        animationSpec = tween(250),
        label = "$testTag-text-color"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onToggle, onLongClick = onRemove)
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(checkboxBg)
                .border(1.5.dp, if (item.done) accent else FormalColors.Divider, CircleShape)
                .testTag("$testTag-checkbox"),
            contentAlignment = Alignment.Center
        ) {
            if (item.done) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已完成",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(
            text = item.text,
            fontSize = 14.sp,
            color = textColor,
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 添加条目行：输入框 + 添加按钮。 */
@Composable
internal fun GoalAddRow(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    inputTag: String,
    addTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f).testTag(inputTag),
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        TextButton(
            onClick = onSubmit,
            enabled = draft.isNotBlank(),
            modifier = Modifier.testTag(addTag)
        ) { Text("添加") }
    }
}

/** 进度条。 */
@Composable
internal fun GoalProgressBar(
    progress: Float,
    accent: Color,
    testTag: String
) {
    val animated by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "$testTag-anim"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(FormalColors.Divider)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent)
        )
    }
}
