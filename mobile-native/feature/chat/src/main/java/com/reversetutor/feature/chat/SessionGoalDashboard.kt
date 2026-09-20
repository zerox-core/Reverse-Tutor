package com.reversetutor.feature.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.PathMeasure
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.sin

// region 18 · R68 目标看板（方向A 卡片仪表盘）
//
// R78 迭代（按用户反馈）：
// - 英雄卡小径入场描绘完成后，加三点波浪脉冲沿路径缓缓流动（哑光低存在感，
//   保留设计感不加复杂动画）；
// - 分类标签按「方案A + E + F 结合」落地：里程碑站卡/本周清单行加彩色填充
//   状态胶囊（A），本周聚焦加分段筛选器「全部/待做/已勾」（E），两个区块的
//   进度条升级为「名称 + 完成数/总数 · 百分比 + 进度条」的标签行（F）。
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
// R70 迭代（按用户反馈，视觉重做）：
// - 里程碑站点卡重做：编号圆点 + 连接线的「路线」造型取代光秃秃的方框卡；
//   ≤3 站时整行等宽拉伸铺满（消灭右侧大留白），>3 站改 106dp 窄卡一屏
//   能同时看到 2~3 站；
// - 添加按钮改为随行小圆钮（＋ 添加 pill），不再是大号文字按钮；
// - 英雄卡再升级：旅程小径上有呼吸的柔光圆与沿路径行进的光点（克制的
//   微动效），分区用细发线隔开，层级更清；
// - 区块标题统一加 3dp 色条引导，计数改为胶囊徽章，全页排版对齐收紧。
//
// R76 迭代（按用户反馈）：
// - 页签简称「学习目标」（原「学习目标与计划」显示不下被省略号截断）；
// - 英雄卡移除「当前状态」区（表情素材与动画将整体重制，方案见
//   docs/specs/animated-emoji-redesign-plan.md，暂缓执行）；
// - 截止时间改为日历点选（可「不设置时间」），不再手动输入；
// - 英雄卡背景动效重做：去掉呼吸光晕与行进光点，改为入场一次性描绘、
//   之后静止的旅程小径；
// - 移除「拆到这一周」连接符；设置页禁用左右滑动翻页，杜绝误翻页。
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

/** 里程碑站点状态：已完成 / 进行中（首站未完成的那一站）/ 未开始。 */
fun goalMilestoneStage(items: List<GoalChecklistItem>, index: Int): String = when {
    items.getOrNull(index)?.done == true -> "已完成"
    items.take(index).all { it.done } -> "进行中"
    else -> "未开始"
}

/** 清单完成百分比（0..100），空清单为 0。 */
fun goalChecklistPercent(items: List<GoalChecklistItem>): Int =
    if (items.isEmpty()) 0 else (goalChecklistProgress(items) * 100).toInt()

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

/** 日历选择的毫秒值（UTC 当日零点）→ 存储格式 yyyy-MM-dd。 */
fun formatGoalDeadlineMillis(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(millis))

fun goalDeadlineTone(daysLeft: Int): GoalDeadlineTone = when {
    daysLeft < 0 -> GoalDeadlineTone.Overdue
    daysLeft <= 3 -> GoalDeadlineTone.Soon
    else -> GoalDeadlineTone.Calm
}

internal fun goalDeadlineToneColor(tone: GoalDeadlineTone): Color = when (tone) {
    GoalDeadlineTone.Calm -> FormalColors.Success
    GoalDeadlineTone.Soon -> FormalColors.Warning
    GoalDeadlineTone.Overdue -> FormalColors.Danger
}

// —— 通用小部件 ——

/** 分区标题：3dp 色条 + 标题文字。 */
@Composable
internal fun GoalSectionTitle(text: String, tick: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(tick)
        )
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Ink)
    }
}

/** 计数胶囊徽章。 */
@Composable
internal fun GoalCountBadge(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}

/** 细发线分隔。 */
@Composable
internal fun GoalHairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(FormalColors.Divider))
}

// —— 主目标英雄卡 ——

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDashboardHero(
    goal: String,
    deadline: String,
    onGoalCommit: (String) -> Unit,
    onDeadlineCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    todayMillis: Long = System.currentTimeMillis(),
    testTag: String = "goal-hero"
) {
    var editingGoal by remember { mutableStateOf(false) }
    // 数据层把「没设目标」存成字符串「未设置」——展示/编辑前归一化为空，编辑框永远预填真实目标而不是「未设置」。
    val goalText = if (goal == "未设置") "" else goal
    var goalDraft by remember(goal) { mutableStateOf(goalText) }
    var showDeadlinePicker by remember { mutableStateOf(false) }
    val daysLeft = parseGoalDeadlineDaysLeft(deadline, todayMillis)
    val deadlineText = if (deadline.isBlank() || deadline == "未设置") "不设置时间" else deadline

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE3EBFF), Color(0xFFF0F5FF), Color(0xFFFBFCFF))
                )
            )
            .border(1.dp, FormalColors.Primary.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .testTag(testTag)
    ) {
        GoalHeroBackdrop(Modifier.matchParentSize())
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(FormalColors.Primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    "主要目标",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FormalColors.Muted,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    if (editingGoal) goalDraft = goalText
                    editingGoal = !editingGoal
                }) { Text(if (editingGoal) "取消" else "修改", fontSize = 13.sp) }
            }
            if (editingGoal) {
                OutlinedTextField(
                    value = goalDraft,
                    onValueChange = { goalDraft = it },
                    modifier = Modifier.fillMaxWidth().testTag("$testTag-goal-input"),
                    placeholder = { Text("用自己的话写下这次要攻克的目标") },
                    shape = RoundedCornerShape(10.dp),
                    // 目标是一句话：单行输入，IME 的 Done/回车才真正触发提交（多行会插换行）。
                    singleLine = true,
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
                // 微调主入口：直接点目标文字进入编辑，输入框预填现值，改的永远是「在原目标上微调」。
                Text(
                    text = goalText.ifBlank { "点这里，一句话说出这次要攻克的目标" },
                    fontSize = 20.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (goalText.isBlank()) FormalColors.Muted else FormalColors.Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            // 预填用归一化文本：没设过目标时是空串，绝不能把「未设置」这个占位字面量带进输入框。
                            goalDraft = goalText
                            editingGoal = true
                        }
                        .testTag("$testTag-goal-display")
                )
            }
            Text(
                text = "写下终点，这一页会把它逐层拆成几站里程碑和这周的行动。",
                fontSize = 12.sp,
                color = FormalColors.Muted
            )

            GoalHairline()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (daysLeft != null) {
                    GoalCountdownRing(daysLeft)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("截止时间", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = deadlineText,
                            fontSize = 15.sp,
                            color = FormalColors.Ink
                        )
                        TextButton(
                            onClick = { showDeadlinePicker = true },
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
                            text = "暂时认不出这个日期，点「改」从日历里重新选",
                            fontSize = 12.sp,
                            color = FormalColors.Muted
                        )
                        else -> Text(
                            text = "点「改」从日历里挑一天，左边的圆环会替你倒数",
                            fontSize = 12.sp,
                            color = FormalColors.Muted
                        )
                    }
                }
            }

            if (showDeadlinePicker) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = daysLeft?.let { todayMillis + it * 86_400_000L }
                )
                DatePickerDialog(
                    onDismissRequest = { showDeadlinePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                pickerState.selectedDateMillis?.let { onDeadlineCommit(formatGoalDeadlineMillis(it)) }
                                showDeadlinePicker = false
                            },
                            enabled = pickerState.selectedDateMillis != null,
                            modifier = Modifier.testTag("$testTag-deadline-confirm")
                        ) { Text("确定") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    onDeadlineCommit("")
                                    showDeadlinePicker = false
                                },
                                modifier = Modifier.testTag("$testTag-deadline-clear")
                            ) { Text("不设置时间") }
                            TextButton(onClick = { showDeadlinePicker = false }) { Text("取消") }
                        }
                    }
                ) {
                    DatePicker(state = pickerState)
                }
            }
        }
    }
}

/** 英雄卡背景：哑光渐变底上的「学习旅程」小径，入场一次性描绘，之后静止。 */
@Composable
internal fun GoalHeroBackdrop(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    val drawT by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "goal-hero-draw"
    )
    LaunchedEffect(Unit) { started = true }
    // 波浪流动：入场描绘完成后，三点脉冲沿小径缓缓流动（哑光低存在感）
    val flowTransition = rememberInfiniteTransition(label = "goal-hero-flow")
    val flowT by flowTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "goal-hero-flow-t"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val p0 = Offset(w * 0.05f, h * 0.96f)
        val c1 = Offset(w * 0.32f, h * 0.78f)
        val c2 = Offset(w * 0.55f, h * 1.04f)
        val p1 = Offset(w * 0.82f, h * 0.66f)
        val trail = Path().apply {
            moveTo(p0.x, p0.y)
            cubicTo(c1.x, c1.y, c2.x, c2.y, p1.x, p1.y)
        }
        val measure = PathMeasure()
        measure.setPath(trail, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * drawT, segment, true)
        drawPath(
            path = segment,
            color = FormalColors.Primary.copy(alpha = 0.14f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        val stationColor = FormalColors.Primary.copy(alpha = 0.26f * drawT)
        drawCircle(stationColor, 3.dp.toPx(), Offset(w * 0.17f, h * 0.86f))
        drawCircle(stationColor, 3.dp.toPx(), Offset(w * 0.46f, h * 0.90f))
        drawCircle(stationColor, 3.dp.toPx(), Offset(w * 0.71f, h * 0.76f))
        // 终点小旗环
        drawCircle(
            color = FormalColors.Primary.copy(alpha = 0.30f * drawT),
            radius = 5.dp.toPx(),
            center = p1,
            style = Stroke(width = 1.5.dp.toPx())
        )
        if (drawT > 0.98f) {
            for (i in 0..2) {
                val t = (flowT + i / 3f) % 1f
                val distance = t * measure.length
                val pos = measure.getPosition(distance)
                val tangent = measure.getTangent(distance)
                val perp = Offset(-tangent.y, tangent.x)
                val wobble = sin(t * 4f * PI.toFloat()) * 3.dp.toPx()
                val alpha = (sin(t * PI.toFloat()) * 0.20f).coerceAtLeast(0f)
                drawCircle(
                    color = FormalColors.Primary.copy(alpha = alpha),
                    radius = 2.6.dp.toPx(),
                    center = pos + perp * wobble
                )
            }
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
    var weeklyFilter by remember { mutableStateOf("全部") }

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
        shape = RoundedCornerShape(16.dp),
        color = FormalColors.Surface,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // —— 阶段里程碑：编号圆点 + 连接线的路线旅程 ——
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalSectionTitle("阶段里程碑", tick = FormalColors.Success)
                Spacer(Modifier.weight(1f))
                if (milestones.isNotEmpty()) {
                    GoalCountBadge(
                        text = "${goalChecklistDoneCount(milestones)}/${milestones.size} 站",
                        bg = FormalColors.SuccessSoft,
                        fg = FormalColors.Success
                    )
                }
            }
            Text(
                text = "把大目标拆成几站；点卡片勾掉一站，长按可以删掉。",
                fontSize = 12.sp,
                color = FormalColors.Muted
            )
            if (milestones.isNotEmpty()) {
                GoalProgressLabel(
                    label = "站点进度",
                    items = milestones,
                    accent = FormalColors.Success,
                    testTag = "$testTag-milestone-progress"
                )
                if (milestones.size <= 3) {
                    // 站少时整行等宽拉伸铺满，不留右侧空白
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag("$testTag-milestone-journey"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        milestones.forEachIndexed { index, item ->
                            GoalMilestoneStation(
                                index = index,
                                item = item,
                                stage = goalMilestoneStage(milestones, index),
                                isLast = index == milestones.lastIndex,
                                onToggle = {
                                    onMilestonesChange(
                                        milestones.mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it }
                                    )
                                },
                                onRemove = {
                                    onMilestonesChange(milestones.filterIndexed { i, _ -> i != index })
                                },
                                modifier = Modifier.weight(1f),
                                testTag = "$testTag-milestone-item-$index"
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().testTag("$testTag-milestone-journey")
                    ) {
                        itemsIndexed(milestones) { index, item ->
                            GoalMilestoneStation(
                                index = index,
                                item = item,
                                stage = goalMilestoneStage(milestones, index),
                                isLast = index == milestones.lastIndex,
                                onToggle = {
                                    onMilestonesChange(
                                        milestones.mapIndexed { i, it -> if (i == index) it.copy(done = !it.done) else it }
                                    )
                                },
                                onRemove = {
                                    onMilestonesChange(milestones.filterIndexed { i, _ -> i != index })
                                },
                                modifier = Modifier.width(106.dp),
                                testTag = "$testTag-milestone-item-$index"
                            )
                        }
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

            // —— 本周聚焦：寄托在里程碑之下 ——
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalSectionTitle("本周聚焦", tick = FormalColors.Primary)
                Spacer(Modifier.weight(1f))
                if (weekly.isNotEmpty()) {
                    GoalCountBadge(
                        text = "${goalChecklistDoneCount(weekly)}/${weekly.size}",
                        bg = FormalColors.PrimarySoft,
                        fg = FormalColors.Primary
                    )
                }
            }
            Text(
                text = "从里程碑里挑出这周够得着的小事，做完就勾。",
                fontSize = 12.sp,
                color = FormalColors.Muted
            )
            if (weekly.isNotEmpty()) {
                GoalSegmentedFilter(
                    options = listOf("全部", "待做", "已勾"),
                    selected = weeklyFilter,
                    onSelect = { weeklyFilter = it },
                    testTag = "$testTag-weekly-filter"
                )
            }
            GoalWeeklyNextBanner(weekly = weekly, testTag = "$testTag-weekly-next")
            if (weekly.isNotEmpty()) {
                GoalProgressLabel(
                    label = "本周进度",
                    items = weekly,
                    accent = FormalColors.Primary,
                    testTag = "$testTag-weekly-progress"
                )
                val shown = weekly.withIndex().filter { (_, it) ->
                    when (weeklyFilter) {
                        "待做" -> !it.done
                        "已勾" -> it.done
                        else -> true
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    shown.forEach { (index, item) ->
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
                    if (shown.isEmpty()) {
                        Text(
                            text = if (weeklyFilter == "待做") "这周的都勾完了，切到「已勾」看看成果。" else "还没有勾完的，切到「待做」继续。",
                            fontSize = 12.sp,
                            color = FormalColors.Muted
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

/** 阶段状态小胶囊：彩色填充标签（设计 A）。 */
@Composable
internal fun GoalStageCapsule(stage: String) {
    val (bg, fg) = when (stage) {
        "已完成", "已勾" -> FormalColors.Success to Color.White
        "进行中" -> FormalColors.Primary to Color.White
        "待做" -> FormalColors.PrimarySoft to FormalColors.Primary
        else -> FormalColors.SurfaceSubtle to FormalColors.Muted
    }
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(
            text = stage,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

/** 里程碑站点：上方编号圆点 + 连接线，下方小卡；点按勾选、长按删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GoalMilestoneStation(
    index: Int,
    item: GoalChecklistItem,
    stage: String,
    isLast: Boolean,
    onToggle: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Column(modifier = modifier.testTag(testTag)) {
        val doing = stage == "进行中"
        // 路线行：编号圆点 + 通往下一站的渐变连接线
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val dotBg = when {
                item.done -> FormalColors.Success
                doing -> FormalColors.Primary
                else -> FormalColors.Surface
            }
            val dotBorder = when {
                item.done -> FormalColors.Success
                doing -> FormalColors.Primary
                else -> FormalColors.Primary.copy(alpha = 0.55f)
            }
            val dotTextColor = if (item.done || doing) Color.White else FormalColors.Primary
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(dotBg)
                    .border(1.5.dp, dotBorder, CircleShape)
                    .testTag("$testTag-check"),
                contentAlignment = Alignment.Center
            ) {
                if (item.done) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "已完成",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                } else {
                    Text(
                        text = "${index + 1}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = dotTextColor
                    )
                }
            }
            if (!isLast) {
                val lineBrush = when {
                    item.done -> Brush.horizontalGradient(
                        listOf(
                            FormalColors.Success.copy(alpha = 0.75f),
                            FormalColors.Success.copy(alpha = 0.75f)
                        )
                    )
                    doing -> Brush.horizontalGradient(
                        listOf(FormalColors.Primary.copy(alpha = 0.55f), FormalColors.Divider)
                    )
                    else -> Brush.horizontalGradient(listOf(FormalColors.Divider, FormalColors.Divider))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(lineBrush)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(6.dp))
        val bg by animateColorAsState(
            targetValue = when {
                item.done -> FormalColors.SuccessSoft
                doing -> FormalColors.PrimarySoft
                else -> FormalColors.SurfaceSubtle
            },
            animationSpec = tween(250),
            label = "$testTag-bg"
        )
        val stageBar = when {
            item.done -> FormalColors.Success
            doing -> FormalColors.Primary
            else -> FormalColors.Divider
        }
        Surface(
            modifier = Modifier.fillMaxWidth().height(82.dp),
            shape = RoundedCornerShape(10.dp),
            color = bg,
            border = BorderStroke(
                1.dp,
                when {
                    item.done -> FormalColors.Success.copy(alpha = 0.55f)
                    doing -> FormalColors.Primary.copy(alpha = 0.45f)
                    else -> FormalColors.Divider
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .combinedClickable(onClick = onToggle, onLongClick = onRemove)
            ) {
                Box(
                    Modifier
                        .width(3.5.dp)
                        .fillMaxHeight()
                        .background(stageBar)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                Text(
                    text = item.text,
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    color = if (item.done) FormalColors.Muted else FormalColors.Ink,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                GoalStageCapsule(stage = stage)
                }
            }
        }
    }
}

/** 分段筛选器：互斥切换（设计 E）。 */
@Composable
internal fun GoalSegmentedFilter(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(999.dp),
        color = FormalColors.SurfaceSubtle
    ) {
        Row(Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            options.forEach { option ->
                val active = option == selected
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelect(option) }
                        .testTag("$testTag-$option"),
                    shape = RoundedCornerShape(999.dp),
                    color = if (active) FormalColors.Ink else Color.Transparent
                ) {
                    Box(
                        Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) Color.White else FormalColors.Muted
                        )
                    }
                }
            }
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
        shape = RoundedCornerShape(12.dp),
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (item.done) FormalColors.SuccessSoft else FormalColors.SurfaceSubtle)
            .combinedClickable(onClick = onToggle, onLongClick = onRemove)
            .padding(vertical = 8.dp, horizontal = 10.dp)
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
        GoalStageCapsule(stage = if (item.done) "已勾" else "待做")
    }
}

/** 添加条目行：输入框 + 随行小圆钮。 */
@Composable
internal fun GoalAddRow(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
    inputTag: String,
    addTag: String
) {
    val enabled = draft.isNotBlank()
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
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() })
        )
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(enabled = enabled, onClick = onSubmit)
                .testTag(addTag),
            shape = RoundedCornerShape(999.dp),
            color = if (enabled) FormalColors.Primary else FormalColors.SurfaceSubtle
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = if (enabled) Color.White else FormalColors.Muted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "添加",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (enabled) Color.White else FormalColors.Muted
                )
            }
        }
    }
}

/** 进度标签行：名称 + 「完成数/总数 · 百分比」+ 进度条（设计 F，有数据）。 */
@Composable
internal fun GoalProgressLabel(
    label: String,
    items: List<GoalChecklistItem>,
    accent: Color,
    testTag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FormalColors.Muted)
            Spacer(Modifier.weight(1f))
            Text(
                text = "${goalChecklistDoneCount(items)}/${items.size} · ${goalChecklistPercent(items)}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                modifier = Modifier.testTag("$testTag-value")
            )
        }
        GoalProgressBar(progress = goalChecklistProgress(items), accent = accent, testTag = testTag)
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
            .height(5.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(FormalColors.Divider)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(5.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accent)
        )
    }
}
