package com.reversetutor.feature.chat

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalElevations
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import kotlinx.coroutines.launch

/**
 * C1 · Agent 对话式创建屏（设计方案 v3 · 第二章）。
 *
 * 顶部极简行（返回 + 右上「创建并进入聊天」）+「了解程度」进度条
 * + 对话流（助手/用户/文件卡/草案卡）+ 底部消息输入框（附件按钮 + 发送）。
 * R-A 由 FakeAgentCreationGateway 驱动；R-B 起换生产网关。
 */
@Composable
fun AgentCreationRoute(
    createPort: NewSessionCreatePort,
    persistence: NewSessionPersistence,
    onCreated: (SessionListItem) -> Unit,
    onBack: () -> Unit = {},
    openPickerOnStart: Boolean = false,
    onOpenPickerConsumed: () -> Unit = {},
    gateway: AgentCreationGateway? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activeGateway = remember(gateway) { gateway ?: FakeAgentCreationGateway() }
    val coordinator = remember(activeGateway) { AgentCreationCoordinator(activeGateway) }
    val lifecycle = remember(createPort, persistence) {
        NewSessionLifecycleCoordinator(persistence = persistence, createPort = createPort)
    }
    var state by remember { mutableStateOf(coordinator.state) }
    var input by remember { mutableStateOf("") }
    var pendingLowUnderstandingCreate by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var createError by remember { mutableStateOf<String?>(null) }
    val feedState = rememberLazyListState()

    fun sync() {
        state = coordinator.state
    }

    LaunchedEffect(coordinator) {
        coordinator.start()
        sync()
    }

    fun attachPicked(uri: android.net.Uri?) {
        if (uri == null) return
        var name = "文档"
        var size = ""
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    val bytes = cursor.getLong(sizeIndex)
                    size = if (bytes >= 1024 * 1024) "%.1f MB".format(bytes / 1024f / 1024f)
                    else "${bytes / 1024.coerceAtLeast(1)} KB"
                }
            }
        }
        scope.launch {
            coordinator.attachDocument(name, size.ifBlank { "大小未知" })
            sync()
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> attachPicked(uri) }

    fun launchPicker() {
        runCatching { documentPicker.launch(arrayOf("*/*")) }
    }

    LaunchedEffect(openPickerOnStart) {
        if (openPickerOnStart) {
            onOpenPickerConsumed()
            launchPicker()
        }
    }

    fun performCreate() {
        if (creating) return
        creating = true
        createError = null
        scope.launch {
            if (lifecycle.state.currentDraft == null && !lifecycle.startBlankDraft()) {
                creating = false
                createError = "草稿箱已满，无法保存这份会话。"
                return@launch
            }
            lifecycle.updateConfiguration { coordinator.configuration() }
            lifecycle.saveBoundary()
            val outcome = lifecycle.createSession()
            if (outcome is CreateSessionOutcome.Success) {
                coordinator.markCreated()
                sync()
                creating = false
                onCreated(outcome.created.session)
            } else {
                creating = false
                createError = when (outcome) {
                    is CreateSessionOutcome.Invalid -> outcome.errors.joinToString("；")
                    is CreateSessionOutcome.Duplicate -> "正在创建中，请稍候。"
                    else -> "暂时无法创建会话，所有编辑均已保留。请重试。"
                }
            }
        }
    }

    fun handleCreateClick() {
        if (!state.canCreate) return
        if (!state.understandingHigh) {
            pendingLowUnderstandingCreate = true
            return
        }
        performCreate()
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || state.busy) return
        input = ""
        scope.launch {
            coordinator.sendUserText(text)
            sync()
        }
    }

    BackHandler(onBack = onBack)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(FormalColors.Background)
            .testTag("agent_creation_screen")
    ) {
        Column(
            modifier = Modifier
                .width(maxWidth.coerceAtMost(390.dp))
                .fillMaxHeight()
                .statusBarsPadding()
        ) {
            CreationTopRow(
                onBack = onBack,
                canCreate = state.canCreate,
                creating = creating,
                onCreate = ::handleCreateClick
            )
            UnderstandingBar(understanding = state.displayedUnderstanding)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = feedState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("agent_creation_feed"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.feed, key = { it.id }) { entry ->
                        when (entry) {
                            is AgentCreationFeedEntry.Assistant -> AssistantBubble(entry.text)
                            is AgentCreationFeedEntry.User -> UserBubble(entry.text)
                            is AgentCreationFeedEntry.FileCard -> FileCard(
                                card = entry,
                                onRetry = {
                                    scope.launch {
                                        coordinator.retryDocumentAnalysis(entry.id)
                                        sync()
                                    }
                                }
                            )
                            is AgentCreationFeedEntry.DraftCard -> DraftSummaryCard(entry.configuration)
                        }
                    }
                }
                LaunchedEffect(state.feed.size, state.busy) {
                    if (state.feed.isNotEmpty()) {
                        feedState.animateScrollToItem(state.feed.size - 1)
                    }
                }
            }
            // R84：生成中状态条固定在输入框上方、不随对话流滚走——
            // 发出消息后用户始终看得到动态反馈，不会再觉得卡在页面上。
            if (state.busy) {
                WorkingStatusBar()
            }
            BottomComposer(
                input = input,
                busy = state.busy,
                requestDocumentActive = state.requestDocumentActive,
                onInputChange = { input = it },
                onSend = ::send,
                onAttach = ::launchPicker,
                createError = createError
            )
        }
    }

    if (pendingLowUnderstandingCreate) {
        AlertDialog(
            onDismissRequest = { pendingLowUnderstandingCreate = false },
            title = { Text("了解程度还不高") },
            text = { Text("可以直接创建碰碰运气，也可以再聊几句让草案更准。") },
            confirmButton = {
                TextButton(onClick = {
                    pendingLowUnderstandingCreate = false
                    performCreate()
                }) { Text("仍然创建") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLowUnderstandingCreate = false }) { Text("再聊几句") }
            }
        )
    }
}

@Composable
private fun CreationTopRow(
    onBack: () -> Unit,
    canCreate: Boolean,
    creating: Boolean,
    onCreate: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .testTag("agent_creation_back"),
            color = FormalColors.Surface,
            shape = CircleShape,
            border = BorderStroke(1.dp, FormalColors.Border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = FormalColors.Ink
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "新建会话",
            style = type.style(15f, 20f, FontWeight.Bold, FormalColors.Ink)
        )
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = onCreate,
            enabled = canCreate && !creating,
            modifier = Modifier
                .height(40.dp)
                .testTag("agent_creation_create"),
            color = if (canCreate) FormalColors.Primary else FormalColors.SurfaceSubtle,
            shape = RoundedCornerShape(FormalShapes.PillRadius),
            border = if (canCreate) null else BorderStroke(1.dp, FormalColors.Border)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = if (creating) "创建中…" else "创建并进入聊天",
                    style = type.style(
                        12f, 16f, FontWeight.Bold,
                        if (canCreate) androidx.compose.ui.graphics.Color.White else FormalColors.Muted
                    )
                )
            }
        }
    }
}

@Composable
private fun UnderstandingBar(understanding: Int) {
    val type = LocalFormalTypeScale.current
    val animated by animateFloatAsState(
        targetValue = understanding / 100f,
        animationSpec = tween(durationMillis = 420),
        label = "understanding"
    )
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "了解程度",
                    style = type.style(13f, 18f, FontWeight.SemiBold, FormalColors.Ink)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "$understanding",
                    style = type.style(15f, 20f, FontWeight.Bold, FormalColors.Primary)
                )
                Spacer(Modifier.weight(1f))
                if (understanding >= 70) {
                    Surface(
                        color = FormalColors.SuccessSoft,
                        shape = RoundedCornerShape(FormalShapes.PillRadius)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = FormalColors.Success,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "推荐创建",
                                style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Success)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .testTag("agent_creation_understanding"),
                color = if (understanding >= 70) FormalColors.Success else FormalColors.Primary,
                trackColor = FormalColors.Divider
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "进度越高，对设定的任务执行的越准确；低置信度下也有出乎意料的效果哦",
                style = type.style(10f, 14f, color = FormalColors.Muted)
            )
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    val type = LocalFormalTypeScale.current
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(
            topStart = 4.dp, topEnd = FormalShapes.CardRadius,
            bottomStart = FormalShapes.CardRadius, bottomEnd = FormalShapes.CardRadius
        ),
        border = BorderStroke(1.dp, FormalColors.Border),
        modifier = Modifier
            .widthIn(max = 320.dp)
            .testTag("agent_creation_assistant")
    ) {
        Text(
            text = text,
            style = type.style(13f, 20f, color = FormalColors.Ink),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    val type = LocalFormalTypeScale.current
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = FormalColors.PrimarySoft,
            shape = RoundedCornerShape(
                topStart = FormalShapes.CardRadius, topEnd = 4.dp,
                bottomStart = FormalShapes.CardRadius, bottomEnd = FormalShapes.CardRadius
            ),
            modifier = Modifier
                .widthIn(max = 320.dp)
                .testTag("agent_creation_user")
        ) {
            Text(
                text = text,
                style = type.style(13f, 20f, color = FormalColors.Ink),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun FileCard(
    card: AgentCreationFeedEntry.FileCard,
    onRetry: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    val statusColor = when (card.status) {
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzing -> FormalColors.Warning
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzed -> FormalColors.Success
        AgentCreationFeedEntry.FileCard.FileStatus.Failed -> FormalColors.Danger
    }
    val statusContainer = when (card.status) {
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzing -> FormalColors.WarningSoft
        AgentCreationFeedEntry.FileCard.FileStatus.Analyzed -> FormalColors.SuccessSoft
        AgentCreationFeedEntry.FileCard.FileStatus.Failed -> FormalColors.SurfaceSubtle
    }
    Surface(
        onClick = if (card.status == AgentCreationFeedEntry.FileCard.FileStatus.Failed) onRetry else ({ }),
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border),
        modifier = Modifier
            .widthIn(max = 330.dp)
            .testTag("agent_creation_file_card")
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = FormalColors.PrimarySoft,
                shape = RoundedCornerShape(FormalShapes.CompactRadius),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = FormalColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = card.fileName,
                    style = type.style(13f, 18f, FontWeight.SemiBold, FormalColors.Ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${card.sizeLabel} · ${card.status.label}",
                    style = type.style(10f, 14f, color = statusColor)
                )
            }
            Spacer(Modifier.width(10.dp))
            Surface(color = statusContainer, shape = RoundedCornerShape(FormalShapes.PillRadius)) {
                Text(
                    text = card.status.label,
                    style = type.style(9f, 13f, FontWeight.Medium, statusColor),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun DraftSummaryCard(configuration: NewSessionConfiguration) {
    // R84：档案卡视觉——色带头 + 白身 + 字段表，与聊天气泡/输入框拉开辨识度。
    val type = LocalFormalTypeScale.current
    Surface(
        color = FormalColors.Surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_creation_draft_card")
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        FormalColors.PrimarySoft,
                        RoundedCornerShape(
                            topStart = FormalShapes.CardRadius,
                            topEnd = FormalShapes.CardRadius
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = FormalColors.Primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "会话草案 · 实时更新",
                    style = type.style(12f, 17f, FontWeight.SemiBold, FormalColors.Primary)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    color = FormalColors.Surface,
                    shape = RoundedCornerShape(FormalShapes.PillRadius)
                ) {
                    Text(
                        text = "完成度 ${configuration.completionPercent}%",
                        style = type.style(9f, 13f, FontWeight.Medium, FormalColors.Primary),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            DraftRow("会话名称", configuration.title.ifBlank { "待补充" })
            DraftRow("AI 学生", configuration.learnerDisplayName)
            DraftRow("学习者角色", configuration.learnerRole.ifBlank { "待补充" })
            DraftRow("人物性格", configuration.persona.ifBlank { "待补充" })
            DraftRow("目标", configuration.goal.ifBlank { "待补充" })
            if (configuration.plan.isNotBlank()) DraftRow("计划", configuration.plan)
            if (configuration.stageMilestones != "未设置") DraftRow("阶段里程碑", configuration.stageMilestones)
            if (configuration.openingMessage.isNotBlank() &&
                configuration.openingMessage != "准备好后，请开始讲给我听吧。"
            ) DraftRow("开场消息", configuration.openingMessage)
            }
        }
    }
}

@Composable
private fun DraftRow(label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            text = label,
            style = type.style(10f, 15f, color = FormalColors.Muted),
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = value,
            style = type.style(11f, 16f, color = FormalColors.Ink),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WorkingStatusBar() {
    val type = LocalFormalTypeScale.current
    val stages = listOf("正在理解你说的…", "正在更新会话草案…", "正在琢磨怎么接话…")
    var stageIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1600)
            stageIndex = (stageIndex + 1) % stages.size
        }
    }
    val dotsTransition = rememberInfiniteTransition(label = "working-dots")
    val dotAlphas = List(3) { index ->
        dotsTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600),
                repeatMode = RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * 200)
            ),
            label = "working-dot-$index"
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(FormalColors.Surface)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("agent_creation_working"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stages[stageIndex],
            style = type.style(11f, 16f, color = FormalColors.Muted)
        )
        Spacer(Modifier.width(8.dp))
        dotAlphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(FormalColors.Primary.copy(alpha = alpha.value), CircleShape)
            )
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun BottomComposer(
    input: String,
    busy: Boolean,
    requestDocumentActive: Boolean,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    createError: String?
) {
    val type = LocalFormalTypeScale.current
    val attachHighlight by animateDpAsState(
        targetValue = if (requestDocumentActive) 2.dp else 1.dp,
        animationSpec = tween(240),
        label = "attach-border"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FormalColors.Surface)
            .imePadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FormalColors.Divider)
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            if (createError != null) {
                Text(
                    text = createError,
                    style = type.style(10f, 15f, color = FormalColors.Danger),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onAttach,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("agent_creation_attach"),
                    color = if (requestDocumentActive) FormalColors.WarningSoft else FormalColors.SurfaceSubtle,
                    shape = CircleShape,
                    border = BorderStroke(attachHighlight, if (requestDocumentActive) FormalColors.Warning else FormalColors.Border)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "发送文件",
                            tint = if (requestDocumentActive) FormalColors.Warning else FormalColors.Muted
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("agent_creation_input"),
                    placeholder = { Text("说说你想学什么…", style = type.style(13f, 18f, color = FormalColors.Tertiary)) },
                    maxLines = 4,
                    shape = RoundedCornerShape(FormalShapes.CardRadius)
                )
                Spacer(Modifier.width(8.dp))
                Surface(
                    onClick = onSend,
                    enabled = input.isNotBlank() && !busy,
                    modifier = Modifier.size(44.dp),
                    color = FormalColors.Primary,
                    shape = CircleShape,
                    shadowElevation = FormalElevations.Panel
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.testTag("agent_creation_send")) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (input.isNotBlank() && !busy) androidx.compose.ui.graphics.Color.White else FormalColors.Border
                        )
                    }
                }
            }
        }
    }
}
