package com.reversetutor.feature.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.design.FormalColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 窗口设置页标准组件库（组件库 mock 定案 01/02/03/07/10 的 Compose 落地）。
 *
 * - 01 CardRadio          [CardRadioRow]
 * - 02 SegmentedPills     [SegmentedPillsRow]
 * - 03 MoodSlider         [MoodSliderRow]
 * - 04 RecipePicker       [RecipePickerPanel]（含 08 骰子随机）
 * - 06 ChatField          [ChatFieldQA]
 * - 07 TokenField         [TokenField]
 * - 10 HoldToConfirm      [HoldToConfirmButton]
 *
 * 视觉令牌全部走 [FormalColors]（苹果灰底、白卡细边框、小面积彩色点缀）。
 */

// region 01 卡片单选 CardRadio

data class CardRadioOption(
    val id: String,
    val title: String,
    val tagline: String,
    val emoji: String
)

/** 性格底子预设（窗口设置页 · 基本资料）。 */
internal val PersonalityPresets = listOf(
    CardRadioOption(id = "good-student", title = "乖学生", tagline = "听话好带", emoji = "📖"),
    CardRadioOption(id = "arguer", title = "杠精", tagline = "爱抬杠", emoji = "🗣️"),
    CardRadioOption(id = "curious", title = "好奇宝宝", tagline = "十万个为什么", emoji = "🤔"),
    CardRadioOption(id = "slacker", title = "摸鱼达人", tagline = "能躺绝不坐", emoji = "😴")
)

@Composable
internal fun CardRadioRow(
    options: List<CardRadioOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "card-radio",
    accentColor: Color = FormalColors.Success,
    accentContainerColor: Color = FormalColors.SuccessSoft
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = option.id == selectedId
            val entrance = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(index * 90L)
                entrance.animateTo(
                    1f,
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.05f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .clickable(
                        onClickLabel = option.title,
                        role = Role.RadioButton,
                        onClick = { onSelect(option.id) }
                    )
                    .testTag("$testTag-${option.id}"),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) accentContainerColor else FormalColors.Surface,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accentColor else FormalColors.Divider
                )
            ) {
                Box {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            option.emoji,
                            fontSize = 26.sp,
                            modifier = Modifier.graphicsLayer {
                                scaleX = entrance.value
                                scaleY = entrance.value
                            }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            option.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) accentColor else FormalColors.Ink,
                            maxLines = 1
                        )
                        Text(
                            option.tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = FormalColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selected) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                                .size(18.dp)
                                .background(accentColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "已选中",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region 02 胶囊分段 SegmentedPills

@Composable
internal fun SegmentedPillsRow(
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "segmented-pills"
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .testTag(testTag)
            .background(FormalColors.SurfaceSubtle, CircleShape)
    ) {
        val segmentWidth = maxWidth / values.size
        val targetIndex = values.indexOf(selected).coerceAtLeast(0)
        val offset by animateDpAsState(
            targetValue = segmentWidth * targetIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .fillMaxHeight()
                .padding(3.dp)
                .background(FormalColors.Surface, CircleShape)
                .testTag("$testTag-indicator")
        )
        Row(Modifier.fillMaxWidth()) {
            values.forEach { value ->
                val isSelected = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            onClickLabel = value,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) }
                        )
                        .testTag("$testTag-$value"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        value,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) FormalColors.Ink else FormalColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// endregion

// region 03 表情强度滑杆 MoodSlider

data class MoodLevel(
    val emoji: String,
    val title: String,
    val description: String
)

internal val FeedbackMoodLevels = listOf(
    MoodLevel("🌱", "温柔鼓励", "多肯定、少打击，先建立信心"),
    MoodLevel("🙂", "耐心引导", "给提示，不直接给答案"),
    MoodLevel("🎯", "直接点破", "错误当场指出，简洁明确"),
    MoodLevel("🧐", "严格追问", "揪住模糊处反复确认"),
    MoodLevel("🌶️", "毒舌犀利", "毫不留情，一针见血")
)

internal val ProbingMoodLevels = listOf(
    MoodLevel("💤", "点到为止", "提一句就走，不纠缠"),
    MoodLevel("💬", "适度追问", "关键概念问一下"),
    MoodLevel("❓", "常规追问", "每一步都确认理解"),
    MoodLevel("🔍", "深挖细节", "推导过程逐段检查"),
    MoodLevel("🧲", "步步紧逼", "不弄懂不放过去")
)

internal val ScaffoldingMoodLevels = listOf(
    MoodLevel("🕊️", "放手自学", "先自己试，几乎不插手"),
    MoodLevel("💡", "关键提示", "卡住时给一次提示"),
    MoodLevel("🪜", "逐步引导", "拆成小步骤，一步步来"),
    MoodLevel("🤝", "手把手教", "每一步都带着做"),
    MoodLevel("🧭", "全程陪跑", "从头到尾同步指导")
)

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
internal fun MoodSliderRow(
    value: Int,
    levels: List<MoodLevel>,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "mood-slider"
) {
    val index = (value - 1).coerceIn(0, levels.lastIndex)
    val level = levels[index]
    Column(modifier.fillMaxWidth().testTag(testTag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AnimatedContent(
                targetState = level,
                transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                label = "mood-emoji"
            ) { current ->
                Text(current.emoji, fontSize = 30.sp)
            }
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = level.title,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "mood-title"
                ) { title ->
                    Text(
                        "$title · $value/5",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FormalColors.Ink
                    )
                }
                Text(
                    level.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = FormalColors.Muted
                )
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(1, levels.size)) },
            valueRange = 1f..levels.size.toFloat(),
            steps = levels.size - 2
        )
    }
}

// endregion

// region 07 标签输入器 TokenField

internal fun parseScopeTokens(raw: String): List<String> =
    raw.split('、', '，', ',', ';', '；')
        .map { it.trim() }
        .filter { it.isNotBlank() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TokenField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "token-field"
) {
    val tokens = remember(value) { parseScopeTokens(value) }
    var draft by remember { mutableStateOf("") }
    var duplicate by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(duplicate) {
        if (duplicate != null) {
            delay(1500)
            duplicate = null
        }
    }
    val addDraft = {
        val next = draft.trim()
        if (next.isNotEmpty()) {
            if (tokens.contains(next)) {
                duplicate = next
            } else {
                onValueChange((tokens + next).joinToString("、"))
            }
        }
        draft = ""
    }
    Column(modifier.fillMaxWidth().testTag(testTag), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tokens.isEmpty()) {
            Text(
                "暂无标签，输入内容后回车或点「添加」",
                style = MaterialTheme.typography.bodySmall,
                color = FormalColors.Muted
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth().testTag("$testTag-chips"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tokens.forEach { token ->
                    Surface(
                        shape = CircleShape,
                        color = FormalColors.PrimarySoft,
                        border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = 0.25f))
                    ) {
                        Row(
                            Modifier.padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                token,
                                style = MaterialTheme.typography.labelMedium,
                                color = FormalColors.Ink
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "删除标签 $token",
                                tint = FormalColors.Muted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(
                                        onClickLabel = "删除标签 $token",
                                        role = Role.Button,
                                        onClick = { onValueChange(tokens.minus(token).joinToString("、")) }
                                    )
                                    .testTag("$testTag-remove-$token")
                            )
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().testTag("$testTag-input"),
            placeholder = { Text("输入后回车添加，如：立体几何") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { addDraft() }),
            trailingIcon = {
                TextButton(onClick = { addDraft() }, enabled = draft.isNotBlank()) { Text("添加") }
            }
        )
        duplicate?.let {
            Text(
                "「$it」已经存在了",
                style = MaterialTheme.typography.labelSmall,
                color = FormalColors.Warning
            )
        }
    }
}

// endregion

// region 10 按住确认 HoldToConfirm

/**
 * 组件库 D 类「按住确认」完整交互链：
 * 按下弹性形变 → 进度扫过 + 震动节拍 + 文案变化 → 充满变绿、对勾弹入 → 中途松手进度回弹复位。
 */
@Composable
internal fun HoldToConfirmButton(
    text: String,
    holdingText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Long = 1500L,
    testTag: String = "hold-to-confirm"
) {
    var pressed by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed) {
            completed = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val ticks = launch {
                while (true) {
                    delay(160)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            progress.animateTo(1f, tween(durationMillis.toInt(), easing = LinearEasing))
            ticks.cancel()
            if (pressed) {
                completed = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm()
            }
        } else if (!completed) {
            progress.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
        }
    }
    // 成功态短暂停留后复位（确认弹窗被取消时按钮也能回到初始态）
    LaunchedEffect(completed) {
        if (completed) {
            delay(1600)
            completed = false
            progress.snapTo(0f)
        }
    }
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !completed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
    )
    val checkScale by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    )
    Surface(
        modifier = modifier
            .testTag(testTag)
            .semantics { role = Role.Button }
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                        }
                    }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = if (completed) FormalColors.Success else FormalColors.Danger
    ) {
        Box(Modifier.fillMaxWidth()) {
            // 进度扫过层：按住时从左向右铺满
            if (!completed) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = progress.value)
                        .background(Color.White.copy(alpha = 0.22f))
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (completed) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = checkScale
                                    scaleY = checkScale
                                }
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = progress.value,
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            trackColor = Color.White.copy(alpha = 0.30f)
                        )
                    }
                }
                Text(
                    when {
                        completed -> "已确认"
                        pressed || progress.value > 0f -> holdingText
                        else -> text
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
    }
}

// endregion

// region 11 填充动作按钮 FilledActionButton

/**
 * 组件库动作按钮：Primary / Danger 填充 + 12dp 圆角 + 按压 0.97 形变 + ripple。
 * 导出分享主按钮与删除确认弹窗的确认按钮共用；文字一律白色。
 */
@Composable
internal fun FilledActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    icon: ImageVector? = null,
    testTag: String = "filled-action",
    onClickLabel: String = text
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    )
    val container = if (danger) FormalColors.Danger else FormalColors.Primary
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick
            )
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = container
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// endregion

// region 12 导出装箱单 ExportManifestCard

/** 「装箱单」预览：浅底细边框卡，列出将打包的内容，随选择过渡切换。 */
@Composable
internal fun ExportManifestCard(
    memory: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = FormalColors.SurfaceSubtle,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) {
        Crossfade(targetState = memory, label = "export-manifest") { isMemory ->
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("将打包：", style = MaterialTheme.typography.labelMedium, color = FormalColors.Muted)
                val items = if (isMemory) listOf(
                    "基本资料 · 学习目标 · 对话策略",
                    "当前全部设定快照",
                    "快捷标签"
                ) else listOf(
                    "基本资料",
                    "学习目标与计划",
                    "对话策略"
                )
                items.forEach { line ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(5.dp).background(FormalColors.Primary, CircleShape))
                        Text(line, style = MaterialTheme.typography.bodySmall, color = FormalColors.Ink)
                    }
                }
            }
        }
    }
}

// endregion

// region 13 导出·版式C 卡选+装箱单+主按钮 ExportPickSharePanel

/**
 * 导出面板·版式 C：复用组件库 01 CardRadio（弹性放大 + 高亮描边 + 角标对勾）单选，
 * 下方实时「装箱单」+ 一颗 Primary 分享主按钮（按压形变）。
 */
@Composable
internal fun ExportPickSharePanel(
    onShare: (memory: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedId by remember { mutableStateOf("memory") }
    val memory = selectedId == "memory"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardRadioRow(
            options = listOf(
                CardRadioOption(id = "memory", title = "会话记忆库", tagline = "全部设定打包", emoji = "\uD83D\uDCE6"),
                CardRadioOption(id = "config", title = "当前配置", tagline = "资料·目标·策略", emoji = "\uD83E\uDDFE")
            ),
            selectedId = selectedId,
            onSelect = { selectedId = it },
            testTag = "export-pick",
            accentColor = FormalColors.Primary,
            accentContainerColor = FormalColors.PrimarySoft
        )
        ExportManifestCard(memory = memory)
        FilledActionButton(
            text = if (memory) "分享会话记忆库" else "分享当前配置",
            icon = Icons.Rounded.Share,
            onClick = { onShare(memory) },
            modifier = Modifier.fillMaxWidth(),
            testTag = if (memory) "export-session-memory" else "export-session-config"
        )
    }
}

// endregion

// region 14 导出·版式D 胶囊分段+装箱单+主按钮 ExportSegmentsSharePanel

/**
 * 导出面板·版式 D：复用组件库 02 SegmentedPills（白色滑块滑动迁移）切换，
 * 下方实时「装箱单」+ 一颗 Primary 分享主按钮（按压形变）。
 */
@Composable
internal fun ExportSegmentsSharePanel(
    onShare: (memory: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf("记忆库") }
    val memory = selected == "记忆库"
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SegmentedPillsRow(
            values = listOf("记忆库", "当前配置"),
            selected = selected,
            onSelect = { selected = it },
            testTag = "export-seg"
        )
        ExportManifestCard(memory = memory)
        FilledActionButton(
            text = if (memory) "分享会话记忆库" else "分享当前配置",
            icon = Icons.Rounded.Share,
            onClick = { onShare(memory) },
            modifier = Modifier.fillMaxWidth(),
            testTag = if (memory) "export-session-memory" else "export-session-config"
        )
    }
}

// endregion

// region 15 次级动作按钮 SecondaryActionButton

/**
 * 组件库次级动作按钮：白底细边框 + 按压 0.97 形变 + ripple；
 * [danger] = true 时文字用 Danger 色（撤销删除类轻量操作）。
 */
@Composable
internal fun SecondaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    testTag: String = "secondary-action",
    onClickLabel: String = text
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
    )
    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick
            )
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = FormalColors.Surface,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) {
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = if (danger) FormalColors.Danger else FormalColors.Ink,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// endregion


// region 04 配方组合器 RecipePicker（含 08 骰子随机）

/** 配方选项：三列（底子 / 怪癖 / 口头禅）各选一个，组合出学生人格。 */
data class RecipeOption(
    val id: String,
    val title: String,
    val emoji: String
)

/** 当前配方选择；三项齐全才能出炉（[composeRecipeText] 非空）。 */
data class RecipeSelection(
    val baseId: String?,
    val quirkId: String?,
    val catchphraseId: String?
)

internal val RecipeBases: List<RecipeOption> =
    PersonalityPresets.map { RecipeOption(it.id, it.title, it.emoji) }

internal val RecipeQuirks = listOf(
    RecipeOption(id = "note-taker", title = "爱记笔记", emoji = "📝"),
    RecipeOption(id = "daydreamer", title = "上课走神", emoji = "💭"),
    RecipeOption(id = "digger", title = "刨根问底", emoji = "🔍"),
    RecipeOption(id = "bargainer", title = "爱讲条件", emoji = "🤝"),
    RecipeOption(id = "crammer", title = "临时抱佛脚", emoji = "⏰")
)

internal val RecipeCatchphrases = listOf(
    RecipeOption(id = "aha", title = "原来如此", emoji = "💡"),
    RecipeOption(id = "why", title = "为啥呀", emoji = "🙋"),
    RecipeOption(id = "got-it", title = "我懂了", emoji = "🎉"),
    RecipeOption(id = "again", title = "再来一遍", emoji = "🔁"),
    RecipeOption(id = "so-what", title = "这有啥用", emoji = "🤷"),
    RecipeOption(id = "yes-sir", title = "老师说得对", emoji = "🫡")
)

/** 三项齐全时组合出人格文本（写回 profile.personality 的格式），否则为 null。 */
internal fun composeRecipeText(selection: RecipeSelection): String? {
    val base = RecipeBases.firstOrNull { it.id == selection.baseId } ?: return null
    val quirk = RecipeQuirks.firstOrNull { it.id == selection.quirkId } ?: return null
    val catchphrase = RecipeCatchphrases.firstOrNull { it.id == selection.catchphraseId } ?: return null
    return "${base.title} · ${quirk.title} · ${catchphrase.title}"
}

/** 从人格文本反解配方选择；不是配方格式或某项对不上时该项为 null。 */
internal fun parseRecipeSelection(personality: String): RecipeSelection {
    val parts = personality.split(" · ").map { it.trim() }
    if (parts.size != 3) return RecipeSelection(null, null, null)
    return RecipeSelection(
        baseId = RecipeBases.firstOrNull { it.title == parts[0] }?.id,
        quirkId = RecipeQuirks.firstOrNull { it.title == parts[1] }?.id,
        catchphraseId = RecipeCatchphrases.firstOrNull { it.title == parts[2] }?.id
    )
}

internal fun randomRecipeSelection(): RecipeSelection = RecipeSelection(
    baseId = RecipeBases.random().id,
    quirkId = RecipeQuirks.random().id,
    catchphraseId = RecipeCatchphrases.random().id
)

/**
 * 组件库 B 类主角「配方组合器」：三列各选一个 → 实时配方卡（组合数可见），
 * 右上角 🎲 一键随机一整套（规格 08 的联动入口）。收敛的创造：给配方，不给白板。
 */
@Composable
internal fun RecipePickerPanel(
    selection: RecipeSelection,
    onSelect: (RecipeSelection) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "recipe-picker"
) {
    val haptics = LocalHapticFeedback.current
    var diceTarget by remember { mutableStateOf(0f) }
    val diceAngle by animateFloatAsState(
        targetValue = diceTarget,
        animationSpec = tween(600, easing = LinearEasing),
        label = "recipe-dice"
    )
    Column(modifier.fillMaxWidth().testTag(testTag), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("人格配方", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = FormalColors.Ink)
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(
                        onClickLabel = "随机一套配方",
                        role = Role.Button,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            diceTarget += 720f
                            onSelect(randomRecipeSelection())
                        }
                    )
                    .testTag("$testTag-dice"),
                shape = CircleShape,
                color = FormalColors.SurfaceSubtle,
                border = BorderStroke(1.dp, FormalColors.Divider)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "🎲",
                        fontSize = 18.sp,
                        modifier = Modifier.graphicsLayer { rotationZ = diceAngle }
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RecipeColumn(
                header = "底子",
                options = RecipeBases,
                selectedId = selection.baseId,
                onPick = { onSelect(selection.copy(baseId = it)) },
                modifier = Modifier.weight(1f),
                testTag = "$testTag-base"
            )
            RecipeColumn(
                header = "怪癖",
                options = RecipeQuirks,
                selectedId = selection.quirkId,
                onPick = { onSelect(selection.copy(quirkId = it)) },
                modifier = Modifier.weight(1f),
                testTag = "$testTag-quirk"
            )
            RecipeColumn(
                header = "口头禅",
                options = RecipeCatchphrases,
                selectedId = selection.catchphraseId,
                onPick = { onSelect(selection.copy(catchphraseId = it)) },
                modifier = Modifier.weight(1f),
                testTag = "$testTag-catch"
            )
        }
        RecipePreviewCard(selection = selection, testTag = "$testTag-card")
    }
}

@Composable
private fun RecipeColumn(
    header: String,
    options: List<RecipeOption>,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier.testTag(testTag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            header,
            style = MaterialTheme.typography.labelSmall,
            color = FormalColors.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        options.forEach { option ->
            val selected = option.id == selectedId
            val scale by animateFloatAsState(
                targetValue = if (selected) 1.04f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                label = "recipe-option-scale"
            )
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clickable(
                        onClickLabel = option.title,
                        role = Role.RadioButton,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPick(option.id)
                        }
                    )
                    .testTag("$testTag-${option.id}"),
                shape = RoundedCornerShape(10.dp),
                color = if (selected) FormalColors.PrimarySoft else FormalColors.Surface,
                border = BorderStroke(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) FormalColors.Primary else FormalColors.Divider
                )
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(option.emoji, fontSize = 18.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        option.title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) FormalColors.Primary else FormalColors.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RecipePreviewCard(selection: RecipeSelection, testTag: String) {
    val combo = "${RecipeBases.size}×${RecipeQuirks.size}×${RecipeCatchphrases.size}"
    val total = RecipeBases.size * RecipeQuirks.size * RecipeCatchphrases.size
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = FormalColors.SurfaceSubtle,
        border = BorderStroke(1.dp, FormalColors.Divider)
    ) {
        Crossfade(targetState = selection, label = "recipe-card") { sel ->
            val base = RecipeBases.firstOrNull { it.id == sel.baseId }
            val quirk = RecipeQuirks.firstOrNull { it.id == sel.quirkId }
            val catchphrase = RecipeCatchphrases.firstOrNull { it.id == sel.catchphraseId }
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (base != null && quirk != null && catchphrase != null) {
                    Text(
                        "配方出炉 ${base.emoji}${quirk.emoji}${catchphrase.emoji}",
                        style = MaterialTheme.typography.labelMedium,
                        color = FormalColors.Success,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "一个${base.title}，${quirk.title}，张口就是「${catchphrase.title}」的学生",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FormalColors.Ink
                    )
                    Text(
                        "$combo = $total 种人格，点 🎲 随机换一套",
                        style = MaterialTheme.typography.labelSmall,
                        color = FormalColors.Muted
                    )
                } else {
                    val missing = listOf(base, quirk, catchphrase).count { it == null }
                    Text(
                        "还差 $missing 步：三列各点一个，配方就出炉；或点右上角 🎲 随机一套",
                        style = MaterialTheme.typography.bodySmall,
                        color = FormalColors.Muted
                    )
                }
            }
        }
    }
}

// endregion

// region 06 聊天气泡编辑器 ChatField

/** 一轮问答：AI 学生提问 [question]，已填值 [value]，提交回调 [onCommit]。 */
data class ChatFieldRound(
    val id: String,
    val question: String,
    val value: String,
    val onCommit: (String) -> Unit
)

/**
 * 组件库 B 类「聊天气泡编辑器」：左边 AI 学生气泡提问，右边用户气泡即表单值；
 * 未回答时底部是输入框 + 发送钮，已回答可点「重新回答」再改。
 */
@Composable
internal fun ChatFieldQA(
    rounds: List<ChatFieldRound>,
    modifier: Modifier = Modifier,
    testTag: String = "chat-field"
) {
    Column(modifier.fillMaxWidth().testTag(testTag), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rounds.forEachIndexed { index, round ->
            ChatFieldRoundBlock(
                round = round,
                entranceDelay = index * 120L,
                testTag = "$testTag-${round.id}"
            )
        }
    }
}

@Composable
private fun ChatFieldRoundBlock(
    round: ChatFieldRound,
    entranceDelay: Long,
    testTag: String
) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(entranceDelay)
        entrance.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }
    var editing by remember(round.id) { mutableStateOf(round.value.isBlank()) }
    var draft by remember(round.id) { mutableStateOf(round.value) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .graphicsLayer { alpha = entrance.value },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(28.dp).background(FormalColors.SuccessSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🧑‍🎓", fontSize = 14.sp)
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp),
                color = FormalColors.SurfaceSubtle,
                border = BorderStroke(1.dp, FormalColors.Divider)
            ) {
                Text(
                    round.question,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = FormalColors.Ink
                )
            }
        }
        if (round.value.isNotBlank() && !editing) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp),
                    color = FormalColors.PrimarySoft,
                    border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = 0.25f))
                ) {
                    Text(
                        round.value,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FormalColors.Ink
                    )
                }
                Text(
                    "点这里重新回答",
                    style = MaterialTheme.typography.labelSmall,
                    color = FormalColors.Muted,
                    modifier = Modifier
                        .clickable(
                            onClickLabel = "重新回答",
                            role = Role.Button,
                            onClick = {
                                draft = round.value
                                editing = true
                            }
                        )
                        .padding(top = 3.dp)
                        .testTag("$testTag-reanswer")
                )
            }
        } else {
            val commit = {
                val answer = draft.trim()
                if (answer.isNotEmpty()) {
                    round.onCommit(answer)
                    editing = false
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f).testTag("$testTag-input"),
                    placeholder = { Text("输入你的回答") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { commit() })
                )
                Spacer(Modifier.width(8.dp))
                val canSend = draft.isNotBlank()
                Surface(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            enabled = canSend,
                            onClickLabel = "发送回答",
                            role = Role.Button,
                            onClick = { commit() }
                        )
                        .testTag("$testTag-send"),
                    shape = CircleShape,
                    color = if (canSend) FormalColors.Primary else FormalColors.Divider
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Send,
                            contentDescription = "发送",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// endregion
