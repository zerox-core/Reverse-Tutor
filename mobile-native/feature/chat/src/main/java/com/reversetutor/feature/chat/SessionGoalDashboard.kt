package com.reversetutor.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
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
// 阶段里程碑 / 本周聚焦两张可勾选清单卡。
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

    Surface(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = FormalColors.Surface,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("主要目标", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Muted)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    if (editingGoal) goalDraft = goal
                    editingGoal = !editingGoal
                }) { Text(if (editingGoal) "取消" else "修改", fontSize = 13.sp) }
            }
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
                                text = "设置后会显示倒计时圆环",
                                fontSize = 12.sp,
                                color = FormalColors.Muted
                            )
                        }
                    }
                }
            }

            Text("当前状态", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Muted)
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

// —— 可勾选清单卡（阶段里程碑 / 本周聚焦共用）——

@Composable
fun GoalChecklistCard(
    title: String,
    subtitle: String,
    rawText: String,
    onItemsChange: (List<GoalChecklistItem>) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = FormalColors.Success,
    testTag: String = "goal-checklist"
) {
    val items = remember(rawText) { decodeGoalChecklist(rawText) }
    var draft by remember { mutableStateOf("") }

    fun submitDraft() {
        val text = draft.trim()
        if (text.isEmpty()) return
        onItemsChange(items + GoalChecklistItem(text))
        draft = ""
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = FormalColors.Surface,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Ink)
                Spacer(Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    Text(
                        text = "${goalChecklistDoneCount(items)}/${items.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                }
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = FormalColors.Muted)
            }
            if (items.isNotEmpty()) {
                val progress by animateFloatAsState(
                    targetValue = goalChecklistProgress(items),
                    animationSpec = tween(500),
                    label = "$testTag-progress"
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
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent)
                    )
                }
            }
            if (items.isEmpty()) {
                Text(
                    text = "还没有条目，在下面添一项吧。",
                    fontSize = 13.sp,
                    color = FormalColors.Muted
                )
            }
            items.forEachIndexed { index, item ->
                GoalChecklistRow(
                    item = item,
                    accent = accent,
                    onToggle = {
                        onItemsChange(items.mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it })
                    },
                    onRemove = {
                        onItemsChange(items.filterIndexed { i, _ -> i != index })
                    },
                    testTag = "$testTag-item-$index"
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).testTag("$testTag-input"),
                    placeholder = { Text("加一项…", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitDraft() })
                )
                TextButton(
                    onClick = { submitDraft() },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("$testTag-add")
                ) { Text("添加") }
            }
        }
    }
}

@Composable
internal fun GoalChecklistRow(
    item: GoalChecklistItem,
    accent: Color,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    testTag: String
) {
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
            .clickable(onClick = onToggle)
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
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "删除「${item.text}」",
            tint = FormalColors.Muted,
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                .testTag("$testTag-remove")
        )
    }
}
