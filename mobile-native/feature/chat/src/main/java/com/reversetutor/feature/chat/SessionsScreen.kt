package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.data.session.SessionRepository
import com.reversetutor.core.model.TutorSession
import com.reversetutor.core.protocol.NativeSessionPresetValidator
import kotlinx.coroutines.launch

@Composable
fun SessionsRoute(
    sessionRepository: SessionRepository,
    avatarVisible: Boolean,
    challengeJoined: Boolean = false,
    onOpenSession: (SessionListItem) -> Unit,
    onNewSession: () -> Unit = {},
    onOpenChallenge: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf(emptyList<TutorSession>()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SessionListFilter.All) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var renameTarget by remember { mutableStateOf<SessionListItem?>(null) }
    var deleteTarget by remember { mutableStateOf<SessionListItem?>(null) }
    var noticeText by remember { mutableStateOf<String?>(null) }

    fun reload() {
        refreshKey += 1
    }

    LaunchedEffect(refreshKey) {
        sessionRepository.ensurePreviewSeed(System.currentTimeMillis())
        sessions = sessionRepository.listSessions()
    }

    SessionsScreen(
        state = SessionListUiState.from(
            sessions = sessions.map { it.toSessionListItem(avatarVisible) },
            query = query,
            filter = filter,
            avatarVisible = avatarVisible
        ),
        challengeJoined = challengeJoined,
        onOpenSession = onOpenSession,
        onNewSession = onNewSession,
        onOpenChallenge = onOpenChallenge,
        onRenameSession = { renameTarget = it },
        onTogglePinned = { item ->
            scope.launch {
                sessionRepository.setPinned(
                    id = item.id,
                    pinned = !item.pinned,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
                reload()
            }
        },
        onDeleteSession = { deleteTarget = it },
        onExportSession = {
            noticeText = "会话导出请到“设置 > 导入与导出”中处理。"
        },
        onAvatarSession = {
            noticeText = "单会话头像将在头像与画像工作中处理。"
        },
        modifier = modifier
    )

    val currentRenameTarget = renameTarget
    if (currentRenameTarget != null) {
        RenameSessionDialog(
            item = currentRenameTarget,
            onDismiss = { renameTarget = null },
            onConfirm = { title ->
                scope.launch {
                    sessionRepository.renameSession(
                        id = currentRenameTarget.id,
                        title = title,
                        updatedAtEpochMillis = System.currentTimeMillis()
                    )
                    renameTarget = null
                    reload()
                }
            }
        )
    }

    val currentDeleteTarget = deleteTarget
    if (currentDeleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除会话") },
            text = { Text("删除“${currentDeleteTarget.title}”？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            sessionRepository.archiveSession(
                                id = currentDeleteTarget.id,
                                updatedAtEpochMillis = System.currentTimeMillis()
                            )
                            deleteTarget = null
                            reload()
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    val currentNotice = noticeText
    if (currentNotice != null) {
        AlertDialog(
            onDismissRequest = { noticeText = null },
            title = { Text("延期待办") },
            text = { Text(currentNotice) },
            confirmButton = {
                TextButton(onClick = { noticeText = null }) {
                    Text("知道了")
                }
            }
        )
    }
}

@Composable
fun SessionsScreen(
    state: SessionListUiState,
    challengeJoined: Boolean = false,
    onOpenSession: (SessionListItem) -> Unit,
    onNewSession: () -> Unit,
    onOpenChallenge: () -> Unit,
    onRenameSession: (SessionListItem) -> Unit,
    onTogglePinned: (SessionListItem) -> Unit,
    onDeleteSession: (SessionListItem) -> Unit,
    onExportSession: (SessionListItem) -> Unit,
    onAvatarSession: (SessionListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var pullDistance by remember { mutableStateOf(0f) }
    val firstSession = state.visibleSessions.firstOrNull()
    val secondSession = state.visibleSessions.drop(1).firstOrNull()
    val thirdSession = state.visibleSessions.drop(2).firstOrNull()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HomeBackground)
            .pointerInput(onOpenChallenge) {
                detectVerticalDragGestures(
                    onDragEnd = { pullDistance = 0f },
                    onDragCancel = { pullDistance = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0) {
                            pullDistance += dragAmount
                            if (pullDistance > 84f) {
                                pullDistance = 0f
                                onOpenChallenge()
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .background(Color(0xB8F4F6FB))
        )
        Box(
            modifier = Modifier
                .offset(y = 54.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x8CDCE2EE))
        )
        HomeTopBar(
            onOpenChallenge = onOpenChallenge,
            onNewSession = onNewSession
        )
        FigmaContinueCard(
            onOpen = {
                firstSession?.let(onOpenSession)
            },
            modifier = Modifier.offset(x = 15.dp, y = 83.dp)
        )
        Text(
            text = "最近学习",
            color = HomeInk,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 15.dp, y = 187.dp)
        )
        FigmaSessionCard(
            title = "宏观经济学基础",
            time = "10 分钟前",
            body = "复习了 GDP、国内生产总值和供需关系。",
            onOpen = { (firstSession ?: secondSession ?: thirdSession)?.let(onOpenSession) },
            modifier = Modifier.offset(x = 15.dp, y = 223.dp)
        )
        FigmaSessionCard(
            title = "英语写作专场：议论文结构",
            time = "昨天",
            body = "重点讨论 thesis statement 和反例段落。",
            onOpen = { (secondSession ?: firstSession ?: thirdSession)?.let(onOpenSession) },
            modifier = Modifier.offset(x = 15.dp, y = 305.dp)
        )
        FigmaSessionCard(
            title = "机器学习入门",
            time = "周二",
            body = "整理过拟合、正则化和交叉验证的对比。",
            onOpen = { (thirdSession ?: secondSession ?: firstSession)?.let(onOpenSession) },
            modifier = Modifier.offset(x = 15.dp, y = 387.dp)
        )
    }
}

@Composable
private fun HomeTopBar(
    onOpenChallenge: () -> Unit,
    onNewSession: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.offset(x = 15.dp, y = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(15.dp),
                contentAlignment = Alignment.Center
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .offset(y = ((index - 1) * 5).dp)
                            .width(16.dp)
                            .height(2.dp)
                            .background(Color(0xFF1F2737), RoundedCornerShape(999.dp))
                    )
                }
            }
        }
        Text(
            text = "会话",
            color = Color(0xFF1F2737),
            fontSize = 21.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 47.dp, y = 7.dp)
        )
        Text(
            text = "会话列表 · 本地优先",
            color = Color(0xFF768093),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.offset(x = 47.dp, y = 32.dp)
        )
        HomePillButton(
            label = "挑战",
            onClick = onOpenChallenge,
            modifier = Modifier.offset(x = 271.dp, y = 12.dp),
            width = 48.dp,
            container = Color(0xFFF7F8FF),
            content = Color(0xFF4F55D7),
            border = Color(0xFFDDE2F0),
            shadow = false
        )
        HomePillButton(
            label = "新建",
            onClick = onNewSession,
            modifier = Modifier.offset(x = 327.dp, y = 12.dp),
            width = 46.dp,
            container = PrimaryPurple,
            content = Color.White,
            border = Color.Transparent,
            shadow = true
        )
    }
}

@Composable
private fun HomePillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    width: androidx.compose.ui.unit.Dp,
    container: Color,
    content: Color,
    border: Color,
    shadow: Boolean
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(30.dp)
            .then(
                if (shadow) {
                    Modifier.shadow(7.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000))
                } else {
                    Modifier
                }
            ),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
        border = if (border == Color.Transparent) null else BorderStroke(1.dp, border)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FigmaContinueCard(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .then(modifier)
            .width(358.dp)
            .height(78.dp)
            .shadow(18.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000)),
        color = CardWhite,
        contentColor = HomeInk,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "继续：宏观经济学基础",
                color = HomeInk,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = 13.dp, y = 11.dp)
            )
            Text(
                text = "上次停在 GDP 与财政政策推演，可以直接接着问。",
                color = HomeBody,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier
                    .offset(x = 13.dp, y = 39.dp)
                    .width(260.dp)
            )
            Surface(
                onClick = onOpen,
                modifier = Modifier
                    .offset(x = 285.dp, y = 22.dp)
                    .width(52.dp)
                    .height(32.dp)
                    .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x1F000000), spotColor = Color(0x1F000000)),
                color = PrimaryPurple,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("继续", fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun FigmaSessionCard(
    title: String,
    time: String,
    body: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onOpen,
        modifier = modifier
            .width(358.dp)
            .height(70.dp)
            .shadow(9.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x0D000000), spotColor = Color(0x0D000000)),
        color = CardWhite,
        contentColor = HomeInk,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = title,
                color = HomeInk,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = 13.dp, y = 11.dp)
            )
            Text(
                text = time,
                color = Color(0xFF768093),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .offset(x = 287.dp, y = 12.dp)
                    .width(56.dp)
            )
            Text(
                text = body,
                color = HomeBody,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .offset(x = 13.dp, y = 39.dp)
                    .width(310.dp)
            )
        }
    }
}

@Composable
private fun JoinedChallengeSessionCard(
    onOpenChallenge: () -> Unit
) {
    Surface(
        onClick = onOpenChallenge,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                text = "Python 学习挑战",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "今日任务：完成 1 次打卡 · 距离结束 15 天",
                fontSize = 13.sp
            )
            Text(
                text = "打开挑战",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private val HomeBackground = Color(0xFFEFF2F8)
private val HomeInk = Color(0xFF20283A)
private val HomeBody = Color(0xFF697184)
private val CardWhite = Color(0xEBFFFFFF)
private val CardBorder = Color(0xE6D7DEEA)
private val PrimaryPurple = Color(0xFF575CE6)

private enum class NewSessionStep {
    Template,
    Custom
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun NewSessionRoute(
    sessionRepository: SessionRepository,
    onCreated: (SessionListItem) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(NewSessionStep.Template) }
    var draft by remember {
        mutableStateOf(NewSessionDraft.fromTemplate(BuiltInSessionTemplates.all.first()))
    }
    var presetJson by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var imageTextRatio by remember { mutableStateOf(0.35f) }
    var difficulty by remember { mutableStateOf(0.45f) }
    var followUpStrength by remember { mutableStateOf(0.7f) }
    var selectedTags by remember { mutableStateOf(setOf("拆解", "复盘")) }

    fun createCurrentDraft() {
        val enrichedDraft = draft.copy(
            profileText = buildString {
                append(draft.profileText.trim())
                appendLine()
                append("图文比例：")
                append("${(imageTextRatio * 10).toInt()}:${(10 - imageTextRatio * 10).toInt()}")
                appendLine()
                append("难度：")
                append((difficulty * 100).toInt())
                append("%；追问强度：")
                append((followUpStrength * 100).toInt())
                append("%。")
                if (selectedTags.isNotEmpty()) {
                    appendLine()
                    append("标签：")
                    append(selectedTags.joinToString("、"))
                }
            }
        )
        if (enrichedDraft.validationErrors().isNotEmpty()) {
            errorText = enrichedDraft.validationErrors().joinToString("\n")
            return
        }
        scope.launch {
            val created = sessionRepository.createSession(
                input = enrichedDraft.toCreationInput(),
                nowEpochMillis = System.currentTimeMillis()
            )
            onCreated(created.session.toSessionListItem(avatarVisible = true))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text("返回")
            }
            Text(
                text = "新建会话",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepChip(
                label = "模板/导入",
                selected = step == NewSessionStep.Template,
                onClick = { step = NewSessionStep.Template }
            )
            StepChip(
                label = "自定义",
                selected = step == NewSessionStep.Custom,
                onClick = { step = NewSessionStep.Custom }
            )
        }
        if (step == NewSessionStep.Template) {
            Text(
                text = "选择一个起点",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BuiltInSessionTemplates.all.forEach { template ->
                    TemplateCard(
                        template = template,
                        selected = draft.templateId == template.id,
                        onClick = {
                            draft = NewSessionDraft.fromTemplate(template)
                            errorText = null
                        }
                    )
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
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "导入模板",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = presetJson,
                        onValueChange = {
                            presetJson = it
                            errorText = null
                        },
                        minLines = 4,
                        label = { Text("粘贴预设 JSON") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            enabled = presetJson.isNotBlank(),
                            onClick = {
                                val result = NativeSessionPresetValidator.validate(presetJson)
                                val preset = result.preset
                                if (result.isValid && preset != null) {
                                    draft = NewSessionDraft.fromPreset(preset)
                                    step = NewSessionStep.Custom
                                } else {
                                    errorText = result.errors.joinToString("\n")
                                }
                            }
                        ) {
                            Text("读取模板")
                        }
                        TextButton(onClick = { step = NewSessionStep.Custom }) {
                            Text("进入自定义")
                        }
                    }
                }
            }
            Button(
                onClick = { createCurrentDraft() },
                enabled = draft.validationErrors().isEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("用当前模板创建")
            }
        } else {
            CustomSessionPanel(
                draft = draft,
                onDraftChange = {
                    draft = it
                    errorText = null
                },
                imageTextRatio = imageTextRatio,
                onImageTextRatioChange = { imageTextRatio = it },
                difficulty = difficulty,
                onDifficultyChange = { difficulty = it },
                followUpStrength = followUpStrength,
                onFollowUpStrengthChange = { followUpStrength = it },
                selectedTags = selectedTags,
                onToggleTag = { tag ->
                    selectedTags = if (tag in selectedTags) {
                        selectedTags - tag
                    } else {
                        selectedTags + tag
                    }
                }
            )
            Button(
                onClick = { createCurrentDraft() },
                enabled = draft.validationErrors().isEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("创建并进入会话")
            }
        }
        val currentError = errorText
        if (currentError != null) {
            Text(
                text = currentError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StepChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TemplateCard(
    template: NewSessionTemplate,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (selected) 3.dp else 1.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = template.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = template.goal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CustomSessionPanel(
    draft: NewSessionDraft,
    onDraftChange: (NewSessionDraft) -> Unit,
    imageTextRatio: Float,
    onImageTextRatioChange: (Float) -> Unit,
    difficulty: Float,
    onDifficultyChange: (Float) -> Unit,
    followUpStrength: Float,
    onFollowUpStrengthChange: (Float) -> Unit,
    selectedTags: Set<String>,
    onToggleTag: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChange(draft.copy(title = it)) },
            singleLine = true,
            label = { Text("会话名称") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.role,
            onValueChange = { onDraftChange(draft.copy(role = it)) },
            singleLine = true,
            label = { Text("人格/角色") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.goal,
            onValueChange = { onDraftChange(draft.copy(goal = it)) },
            singleLine = true,
            label = { Text("学习目标") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = draft.profileText,
            onValueChange = { onDraftChange(draft.copy(profileText = it)) },
            minLines = 4,
            label = { Text("学生画像与偏好") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(
                checked = draft.sourceHandoffRequested,
                onCheckedChange = { checked ->
                    onDraftChange(draft.copy(sourceHandoffRequested = checked))
                }
            )
            Text(
                text = "创建后补充文件知识库",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
        SliderSetting(
            label = "图文比例",
            valueLabel = "${(imageTextRatio * 10).toInt()}:${(10 - imageTextRatio * 10).toInt()}",
            value = imageTextRatio,
            onValueChange = onImageTextRatioChange
        )
        SliderSetting(
            label = "难度",
            valueLabel = "${(difficulty * 100).toInt()}%",
            value = difficulty,
            onValueChange = onDifficultyChange
        )
        SliderSetting(
            label = "追问强度",
            valueLabel = "${(followUpStrength * 100).toInt()}%",
            value = followUpStrength,
            onValueChange = onFollowUpStrengthChange
        )
        Text(
            text = "能力标签",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("拆解", "复盘", "严格纠错", "例题", "项目制", "口语").forEach { tag ->
                FilterChip(
                    selected = tag in selectedTags,
                    onClick = { onToggleTag(tag) },
                    label = { Text(tag) }
                )
            }
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = valueLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun NewSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (NewSessionDraft) -> Unit
) {
    var draft by remember {
        mutableStateOf(
            NewSessionDraft(
                title = "",
                role = "",
                goal = "",
                profileText = ""
            )
        )
    }
    var presetJson by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "模板",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BuiltInSessionTemplates.all.forEach { template ->
                        TextButton(
                            onClick = {
                                draft = NewSessionDraft.fromTemplate(template)
                                errorText = null
                            }
                        ) {
                            Text(template.title)
                        }
                    }
                }
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    singleLine = true,
                    label = { Text("会话名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.role,
                    onValueChange = { draft = draft.copy(role = it) },
                    singleLine = true,
                    label = { Text("角色") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.goal,
                    onValueChange = { draft = draft.copy(goal = it) },
                    singleLine = true,
                    label = { Text("目标") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draft.profileText,
                    onValueChange = { draft = draft.copy(profileText = it) },
                    minLines = 3,
                    label = { Text("学生画像") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = draft.sourceHandoffRequested,
                        onCheckedChange = { checked ->
                            draft = draft.copy(sourceHandoffRequested = checked)
                        }
                    )
                    Text(
                        text = "创建后准备导入资料",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                OutlinedTextField(
                    value = presetJson,
                    onValueChange = { presetJson = it },
                    minLines = 3,
                    label = { Text("预设 JSON") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = presetJson.isNotBlank(),
                    onClick = {
                        val result = NativeSessionPresetValidator.validate(presetJson)
                        val preset = result.preset
                        if (result.isValid && preset != null) {
                            onCreate(NewSessionDraft.fromPreset(preset))
                        } else {
                            errorText = result.errors.joinToString("\n")
                        }
                    }
                ) {
                    Text("从预设创建")
                }
                val currentError = errorText
                if (currentError != null) {
                    Text(
                        text = currentError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.validationErrors().isEmpty(),
                onClick = { onCreate(draft) }
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionFilterChips(
    selected: SessionListFilter,
    onFilterChange: (SessionListFilter) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == SessionListFilter.All,
            onClick = { onFilterChange(SessionListFilter.All) },
            label = { Text("全部") }
        )
        FilterChip(
            selected = selected == SessionListFilter.Pinned,
            onClick = { onFilterChange(SessionListFilter.Pinned) },
            label = { Text("置顶") }
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SessionCard(
    item: SessionListItem,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onAvatar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.statusLabel} · ${item.unreadLabel} · ${item.avatarLabel}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                if (item.pinned) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "置顶",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(onClick = onOpen) {
                    Text("打开")
                }
                TextButton(onClick = onRename) {
                    Text("重命名")
                }
                TextButton(onClick = onTogglePinned) {
                    Text(if (item.pinned) "取消置顶" else "置顶")
                }
                TextButton(onClick = onAvatar) {
                    Text("头像")
                }
                TextButton(onClick = onExport) {
                    Text("导出")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun EmptySessions(
    title: String,
    onNewSession: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Button(onClick = onNewSession) {
                Text("新建会话")
            }
        }
    }
}

@Composable
private fun RenameSessionDialog(
    item: SessionListItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名会话") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                label = { Text("会话名称") }
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().isNotEmpty(),
                onClick = { onConfirm(title) }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
