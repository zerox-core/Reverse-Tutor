package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationRepository
import com.reversetutor.core.data.llm.ChatGenerationInput
import com.reversetutor.core.data.llm.ChatGenerationOutcome
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.memory.NoteInput
import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.MessageRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatRoute(
    messageRepository: MessageRepository,
    chatGenerationRepository: ChatGenerationRepository? = null,
    backgroundGenerationRepository: BackgroundGenerationRepository? = null,
    memoryRepository: MemoryRepository? = null,
    sourceRepository: SourceRepository? = null,
    sessionId: String,
    sessionTitle: String,
    pendingImageDraft: ChatImageDraft? = null,
    evidenceTargetMessageId: String? = null,
    onPickImage: () -> Unit = {},
    onImageDraftConsumed: () -> Unit = {},
    onBackgroundGenerationQueued: (String) -> Unit = {},
    onComposerFocusChanged: (Boolean) -> Unit = {},
    onOpenContextHub: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var records by remember(sessionId) { mutableStateOf(emptyList<MessageRecord>()) }
    var composer by remember(sessionId) { mutableStateOf(ChatComposerState(text = "")) }
    var generation by remember(sessionId) { mutableStateOf<ChatGenerationUiState>(ChatGenerationUiState.Idle) }
    var activeGenerationToken by remember(sessionId) { mutableStateOf<LlmGenerationToken?>(null) }
    var activeBackgroundJobId by remember(sessionId) { mutableStateOf<String?>(null) }
    var refreshKey by remember(sessionId) { mutableIntStateOf(0) }
    var noticeText by remember { mutableStateOf<String?>(null) }

    fun reload() {
        refreshKey += 1
    }

    LaunchedEffect(sessionId, refreshKey) {
        records = messageRepository.listMessageRecords(sessionId)
    }

    LaunchedEffect(pendingImageDraft?.requestId) {
        val imageDraft = pendingImageDraft ?: return@LaunchedEffect
        composer = composer.copy(imageDraft = imageDraft)
        onImageDraftConsumed()
    }

    LaunchedEffect(activeBackgroundJobId, sessionId) {
        val jobId = activeBackgroundJobId ?: return@LaunchedEffect
        val repository = backgroundGenerationRepository ?: return@LaunchedEffect
        while (activeBackgroundJobId == jobId) {
            delay(250L)
            val job = repository.getJob(jobId) ?: return@LaunchedEffect
            if (job.status in TerminalGenerationStatuses) {
                if (activeGenerationToken == job.token) {
                    activeGenerationToken = null
                }
                generation = job.status.toUiState(job.errorMessage)
                activeBackgroundJobId = null
                reload()
                return@LaunchedEffect
            }
        }
    }

    ChatScreen(
        state = ChatUiState.from(
            sessionTitle = sessionTitle,
            records = records,
            composer = composer,
            generation = generation
        ),
        onComposerTextChange = { composer = composer.copy(text = it) },
        onSendMessage = {
            if (composer.canSend) {
                val sentComposer = composer
                scope.launch {
                    val userMessage = messageRepository.sendUserMessage(
                        sessionId = sessionId,
                        text = sentComposer.text,
                        nowEpochMillis = System.currentTimeMillis(),
                        quote = sentComposer.toQuoteDraft(),
                        attachments = sentComposer.toAttachmentDrafts()
                    ) ?: return@launch
                    composer = ChatComposerState(text = "")
                    reload()
                    val generator = chatGenerationRepository ?: return@launch
                    val token = LlmGenerationToken("${userMessage.id}-${System.currentTimeMillis()}")
                    activeGenerationToken = token
                    generation = ChatGenerationUiState.Pending
                    val contextEvidence = buildChatContextEvidence(
                        userText = userMessage.text,
                        memoryRepository = memoryRepository,
                        sourceRepository = sourceRepository
                    )
                    val imageAttachments = sentComposer.toAttachmentDrafts()
                        .mapIndexed { index, attachment ->
                            attachment.toAttachment(
                                spaceId = userMessage.spaceId,
                                messageId = userMessage.id,
                                index = index
                            )
                        }
                    val backgroundRepository = backgroundGenerationRepository
                    if (backgroundRepository != null) {
                        val job = backgroundRepository.enqueueGenerationJob(
                            input = BackgroundGenerationInput(
                                spaceId = userMessage.spaceId,
                                sessionId = sessionId,
                                userMessageId = userMessage.id,
                                userText = userMessage.text,
                                token = token,
                                quoteExcerpt = sentComposer.quoteTarget?.excerpt,
                                imageAttachments = imageAttachments,
                                contextEvidence = contextEvidence
                            ),
                            nowEpochMillis = System.currentTimeMillis()
                        )
                        activeBackgroundJobId = job.id
                        onBackgroundGenerationQueued(job.id)
                        return@launch
                    }
                    val outcome = generator.generateReply(
                        input = ChatGenerationInput(
                            sessionId = sessionId,
                            userMessageId = userMessage.id,
                            userText = userMessage.text,
                            token = token,
                            quoteExcerpt = sentComposer.quoteTarget?.excerpt,
                            imageAttachments = imageAttachments,
                            contextEvidence = contextEvidence
                        ),
                        nowEpochMillis = System.currentTimeMillis(),
                        isTokenCurrent = { it == activeGenerationToken }
                    )
                    if (activeGenerationToken == token) {
                        activeGenerationToken = null
                    }
                    generation = outcome.toUiState()
                    reload()
                }
            }
        },
        onCancelQuote = {
            composer = composer.copy(quoteTarget = null)
        },
        onCreateImageDraft = {
            onPickImage()
        },
        onCancelImageDraft = {
            composer = composer.copy(imageDraft = null)
        },
        onMessageAction = { item, action ->
            when (action) {
                ChatMessageAction.Quote -> {
                    composer = composer.copy(
                        quoteTarget = ChatQuoteTarget(
                            messageId = item.id,
                            excerpt = item.text.toQuoteExcerpt()
                        )
                    )
                }
                ChatMessageAction.Note -> {
                    val repository = memoryRepository
                    if (repository == null) {
                        noticeText = "当前预览暂不能创建随笔。"
                    } else {
                        scope.launch {
                            val note = repository.createNote(
                                input = NoteInput(
                                    title = item.text.toQuoteExcerpt(),
                                    body = item.text,
                                    sourceMessageId = item.id
                                ),
                                nowEpochMillis = System.currentTimeMillis(),
                                spaceId = item.spaceId
                            )
                            noticeText = if (note == null) {
                                "这条消息为空，未创建随笔。"
                            } else {
                                "随笔已保存到学习脉络。"
                            }
                        }
                    }
                }
                ChatMessageAction.Regenerate -> {
                    noticeText = "重新生成会在 LLM 编排完成后启用。"
                }
                ChatMessageAction.Delete -> {
                    scope.launch {
                        messageRepository.deleteMessage(item.id)
                        reload()
                    }
                }
            }
        },
        onComposerFocusChanged = onComposerFocusChanged,
        onOpenContextHub = onOpenContextHub,
        onBack = onBack,
        evidenceTargetMessageId = evidenceTargetMessageId,
        modifier = modifier
    )

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
fun ChatScreen(
    state: ChatUiState,
    onComposerTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onCancelQuote: () -> Unit,
    onCreateImageDraft: () -> Unit,
    onCancelImageDraft: () -> Unit,
    onMessageAction: (ChatTimelineItem, ChatMessageAction) -> Unit,
    onComposerFocusChanged: (Boolean) -> Unit = {},
    onOpenContextHub: () -> Unit = {},
    onBack: () -> Unit = {},
    evidenceTargetMessageId: String? = null,
    modifier: Modifier = Modifier
) {
    ReverseTeachingChatScreen(
        state = state,
        onComposerTextChange = onComposerTextChange,
        onSendMessage = onSendMessage,
        onCancelQuote = onCancelQuote,
        onCreateImageDraft = onCreateImageDraft,
        onCancelImageDraft = onCancelImageDraft,
        onMessageAction = onMessageAction,
        onComposerFocusChanged = onComposerFocusChanged,
        onOpenSettings = onOpenContextHub,
        onBack = onBack,
        evidenceTargetMessageId = evidenceTargetMessageId,
        modifier = modifier
    )
}

@Composable
private fun FigmaChatTopBar(
    sessionTitle: String,
    contextLabel: String,
    onOpenContextHub: () -> Unit,
    onBack: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFFFCFCFF))
            .border(1.dp, Color(0xFFE6E1F1))
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier
                .offset(x = 0.dp, y = 6.dp)
                .size(44.dp),
            color = Color.Transparent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("‹", color = Color(0xFF202637), fontSize = 22.sp, lineHeight = 24.sp)
            }
        }
        Text(
            text = sessionTitle,
            color = Color(0xFF202637),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = 47.dp, y = 7.dp)
                .width((maxWidth - 112.dp).coerceAtLeast(120.dp))
        )
        Text(
            text = contextLabel,
            color = Color(0xFF687186),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .offset(x = 47.dp, y = 31.dp)
                .width((maxWidth - 112.dp).coerceAtLeast(120.dp))
        )
        Surface(
            onClick = onOpenContextHub,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 16.dp)
                .size(44.dp),
            color = Color(0xB8E5F5FF),
            contentColor = Color(0xFF506078),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, Color(0x476EB8EB))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⚙", fontSize = 24.sp, lineHeight = 24.sp)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFDDE1ED))
        )
    }
}

@Composable
private fun FigmaChatBubble(
    bubble: FigmaChatBubbleData,
    viewportWidth: androidx.compose.ui.unit.Dp,
    onQuote: () -> Unit
) {
    val target = bubble.target
    val availableWidth = (viewportWidth - 32.dp).coerceAtLeast(120.dp)
    val bubbleWidth = target.width.coerceAtMost(availableWidth)
    val baselineRightMargin = 390.dp - target.x - target.width
    val bubbleX = if (bubble.isUser) {
        (viewportWidth - baselineRightMargin - bubbleWidth).coerceAtLeast(16.dp)
    } else {
        target.x.coerceAtMost((viewportWidth - bubbleWidth - 16.dp).coerceAtLeast(16.dp))
    }
    Surface(
        onClick = onQuote,
        modifier = Modifier
            .offset(x = bubbleX, y = target.y)
            .width(bubbleWidth)
            .height(target.height)
            .shadow(
                elevation = if (bubble.isUser) 12.dp else 24.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (bubble.isUser) Color(0x143E7A64) else Color(0x1717203A),
                spotColor = if (bubble.isUser) Color(0x143E7A64) else Color(0x1717203A)
            ),
        color = if (bubble.isUser) Color(0xFFE2F4EC) else Color.White,
        contentColor = Color(0xFF202637),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (bubble.isUser) Color(0xFFA7DCC9) else Color(0xFFC8CDDF))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Text(
                text = bubble.text,
                color = Color(0xFF202637),
                fontSize = 12.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .offset(x = 13.dp, y = 11.dp)
                    .widthIn(
                        max = target.textWidth.coerceAtMost(
                            (bubbleWidth - 26.dp).coerceAtLeast(80.dp)
                        )
                    )
            )
            val source = bubble.source
            if (!source.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .offset(x = 13.dp, y = 61.dp)
                        .width((bubbleWidth - 26.dp).coerceAtLeast(80.dp))
                        .height(21.dp),
                    color = Color(0xFFF7F8FC),
                    contentColor = Color(0xFF687186),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFC8CDDF))
                ) {
                    Text(
                        text = "来源提示：${source.take(32)}",
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 10.dp, top = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FigmaComposerChip(
    text: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xF2FFFFFF),
        contentColor = Color(0xFF5057D8),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFD7DEEA))
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = text, modifier = Modifier.weight(1f), fontSize = 11.sp, lineHeight = 15.sp)
            TextButton(onClick = onAction) {
                Text(action, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FigmaChatComposer(
    text: String,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onCreateImageDraft: () -> Unit,
    onSend: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp)
            .navigationBarsPadding()
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(45.dp)
                .shadow(12.dp, RoundedCornerShape(22.5.dp), ambientColor = Color(0x0F17203A), spotColor = Color(0x0F17203A)),
            color = Color.White.copy(alpha = 0.58f),
            contentColor = Color(0xFF202637),
            shape = RoundedCornerShape(22.5.dp),
            border = BorderStroke(1.dp, Color(0xE0C7CEDF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onCreateImageDraft,
                    modifier = Modifier.size(33.dp),
                    color = Color.Transparent,
                    contentColor = Color(0xFF253047),
                    shape = CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("+", fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.Light)
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(25.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFF202637),
                            fontSize = 16.sp,
                            lineHeight = 16.sp
                        ),
                        cursorBrush = SolidColor(Color(0xFF575CE6)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { onFocusChanged(it.isFocused) }
                    )
                    if (text.isBlank()) {
                        Text(
                            text = "输入你的理解、问题或例子",
                            color = Color(0xC76F7689),
                            fontSize = 16.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier.size(33.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "语音输入预留",
                        tint = Color(0xFF253047),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Surface(
                    onClick = {
                        if (canSend) onSend()
                    },
                    modifier = Modifier
                        .size(33.dp)
                        .shadow(13.dp, CircleShape, ambientColor = Color(0x33323C72), spotColor = Color(0x33323C72)),
                    color = Color(0xFA575CE6),
                    contentColor = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, Color(0xFF474DD0))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "发送",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class FigmaChatBubbleTarget(
    val x: androidx.compose.ui.unit.Dp,
    val y: androidx.compose.ui.unit.Dp,
    val width: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp,
    val textWidth: androidx.compose.ui.unit.Dp
)

private data class FigmaChatBubbleData(
    val text: String,
    val isUser: Boolean,
    val source: String?,
    val target: FigmaChatBubbleTarget
)

private val FigmaChatBubbleDefaults = listOf(
    FigmaChatBubbleData(
        text = "你好，今天我们复习宏观经济学。你能先解释一下什么是 GDP 吗？",
        isUser = false,
        source = null,
        target = FigmaChatBubbleTarget(16.dp, 112.dp, 306.dp, 64.dp, 272.dp)
    ),
    FigmaChatBubbleData(
        text = "GDP 就是在一个国家里，一段时间内生产的所有东西的价值总和吧？",
        isUser = true,
        source = null,
        target = FigmaChatBubbleTarget(89.dp, 185.dp, 285.dp, 58.dp, 254.dp)
    ),
    FigmaChatBubbleData(
        text = "很接近了。你的回答抓住了“国家”和“时间”两个关键词。",
        isUser = false,
        source = "国内生产总值强调地域范围和最终产品价值。",
        target = FigmaChatBubbleTarget(16.dp, 258.dp, 321.dp, 92.dp, 288.dp)
    ),
    FigmaChatBubbleData(
        text = "我想应该是财政政策吧？因为 LM 曲线是不平的，财政政策带来的 IS 曲线右移不会引起利率上升。",
        isUser = true,
        source = null,
        target = FigmaChatBubbleTarget(75.dp, 365.dp, 299.dp, 64.dp, 274.dp)
    )
)

private val ChatFigmaBackground = Color(0xFFEEF0F6)

@Composable
private fun ChatGenerationStatus(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ChatHeader(
    sessionTitle: String,
    evidenceTargetMessageId: String?,
    onOpenContextHub: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sessionTitle,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "本地会话",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (!evidenceTargetMessageId.isNullOrBlank()) {
                    Text(
                        text = "证据定位：$evidenceTargetMessageId",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            TextButton(onClick = onOpenContextHub) {
                Text("打开脉络")
            }
        }
    }
}

@Composable
private fun EmptyChatTimeline() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "还没有消息",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MessageBubble(
    item: ChatTimelineItem,
    onAction: (ChatMessageAction) -> Unit
) {
    val isUser = item.role == MessageRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 520.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.roleLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                val quoteLabel = item.quoteLabel
                if (quoteLabel != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = quoteLabel,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (item.text.isNotBlank()) {
                    Text(
                        text = item.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    )
                }
                item.attachmentLabels.forEach { label ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ChatMessageAction.entries.forEach { action ->
                        TextButton(onClick = { onAction(action) }) {
                            Text(action.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatComposer(
    composer: ChatComposerState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancelQuote: () -> Unit,
    onCreateImageDraft: () -> Unit,
    onCancelImageDraft: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val quoteTarget = composer.quoteTarget
            if (quoteTarget != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "正在回复：${quoteTarget.excerpt}",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onCancelQuote) {
                        Text("取消")
                    }
                }
            }
            val imageDraft = composer.imageDraft
            if (imageDraft != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = imageDraft.displayLabel,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = onCancelImageDraft) {
                        Text("取消图片")
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCreateImageDraft) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "添加图片"
                        )
                    }
                    OutlinedTextField(
                        value = composer.text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f),
                        minLines = 1,
                        maxLines = 5,
                        placeholder = { Text("发消息") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                    IconButton(
                        enabled = false,
                        onClick = {}
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "语音输入预留"
                        )
                    }
                    Button(
                        enabled = composer.canSend,
                        onClick = onSend,
                        modifier = Modifier.size(42.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "发送"
                        )
                    }
                }
            }
        }
    }
}

private fun String.toQuoteExcerpt(): String {
    val compact = trim().replace(Regex("\\s+"), " ")
    return compact.take(96).ifEmpty { "消息" }
}

private fun ChatGenerationOutcome.toUiState(): ChatGenerationUiState =
    when (this) {
        is ChatGenerationOutcome.Generated -> ChatGenerationUiState.Idle
        is ChatGenerationOutcome.ProviderFailed -> ChatGenerationUiState.Failure(message)
        ChatGenerationOutcome.NoModelConfigured -> ChatGenerationUiState.NoModel
        ChatGenerationOutcome.UnsupportedVision -> ChatGenerationUiState.Failure("图片输入暂不支持")
        ChatGenerationOutcome.BlankPrompt -> ChatGenerationUiState.Failure("不能发送空内容")
        ChatGenerationOutcome.Stale -> ChatGenerationUiState.Idle
    }

private val TerminalGenerationStatuses = setOf(
    BackgroundJobStatus.Completed,
    BackgroundJobStatus.Failed,
    BackgroundJobStatus.Cancelled,
    BackgroundJobStatus.Discarded
)

private fun BackgroundJobStatus.toUiState(errorMessage: String?): ChatGenerationUiState =
    when (this) {
        BackgroundJobStatus.Failed -> if (errorMessage == "No model configured") {
            ChatGenerationUiState.NoModel
        } else {
            ChatGenerationUiState.Failure(errorMessage ?: "后台生成失败")
        }
        BackgroundJobStatus.Cancelled,
        BackgroundJobStatus.Discarded,
        BackgroundJobStatus.Completed -> ChatGenerationUiState.Idle
        BackgroundJobStatus.Queued,
        BackgroundJobStatus.Running -> ChatGenerationUiState.Pending
    }
