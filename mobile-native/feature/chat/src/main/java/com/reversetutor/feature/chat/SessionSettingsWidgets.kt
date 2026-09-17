package com.reversetutor.feature.chat

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.material.icons.rounded.Close
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

/**
 * 窗口设置页标准组件库（组件库 mock 定案 01/02/03/07/10 的 Compose 落地）。
 *
 * - 01 CardRadio          [CardRadioRow]
 * - 02 SegmentedPills     [SegmentedPillsRow]
 * - 03 MoodSlider         [MoodSliderRow]
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
    testTag: String = "card-radio"
) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val selected = option.id == selectedId
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
                color = if (selected) FormalColors.SuccessSoft else FormalColors.Surface,
                border = BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) FormalColors.Success else FormalColors.Divider
                )
            ) {
                Box {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(option.emoji, fontSize = 26.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            option.title,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) FormalColors.Success else FormalColors.Ink,
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
                                .background(FormalColors.Success, CircleShape),
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
            progress.animateTo(1f, tween(durationMillis.toInt(), easing = LinearEasing))
            if (pressed) {
                completed = true
                onConfirm()
            }
        } else {
            progress.snapTo(0f)
        }
    }
    Surface(
        modifier = modifier
            .testTag(testTag)
            .semantics { role = Role.Button }
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
        color = FormalColors.Danger
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                progress = progress.value,
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.5.dp,
                trackColor = Color.White.copy(alpha = 0.30f)
            )
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

// endregion
