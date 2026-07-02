package com.reversetutor.feature.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.data.llm.ChatGenerationInput
import com.reversetutor.core.data.llm.ChatGenerationOutcome
import com.reversetutor.core.data.llm.ChatGenerationRepository
import com.reversetutor.core.data.memory.MemoryRepository
import com.reversetutor.core.data.memory.NoteInput
import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.llm.LlmGenerationToken
import kotlinx.coroutines.launch

@Composable
fun ChatRoute(
    messageRepository: MessageRepository,
    chatGenerationRepository: ChatGenerationRepository? = null,
    memoryRepository: MemoryRepository? = null,
    sourceRepository: SourceRepository? = null,
    sessionId: String,
    sessionTitle: String,
    pendingImageDraft: ChatImageDraft? = null,
    evidenceTargetMessageId: String? = null,
    onPickImage: () -> Unit = {},
    onImageDraftConsumed: () -> Unit = {},
    onOpenContextHub: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var records by remember(sessionId) { mutableStateOf(emptyList<MessageRecord>()) }
    var composer by remember(sessionId) { mutableStateOf(ChatComposerState(text = "")) }
    var generation by remember(sessionId) { mutableStateOf<ChatGenerationUiState>(ChatGenerationUiState.Idle) }
    var activeGenerationToken by remember(sessionId) { mutableStateOf<LlmGenerationToken?>(null) }
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
                    val outcome = generator.generateReply(
                        input = ChatGenerationInput(
                            sessionId = sessionId,
                            userMessageId = userMessage.id,
                            userText = userMessage.text,
                            token = token,
                            quoteExcerpt = sentComposer.quoteTarget?.excerpt,
                            imageAttachments = sentComposer.toAttachmentDrafts()
                                .mapIndexed { index, attachment ->
                                    attachment.toAttachment(
                                        spaceId = userMessage.spaceId,
                                        messageId = userMessage.id,
                                        index = index
                                    )
                                },
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
                        noticeText = "Note creation is unavailable in this preview."
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
                                "Note was not created because the message is empty."
                            } else {
                                "Note saved to Context hub."
                            }
                        }
                    }
                }
                ChatMessageAction.Regenerate -> {
                    noticeText = "Regenerate is deferred until LLM orchestration lands."
                }
                ChatMessageAction.Delete -> {
                    scope.launch {
                        messageRepository.deleteMessage(item.id)
                        reload()
                    }
                }
            }
        },
        onOpenContextHub = onOpenContextHub,
        evidenceTargetMessageId = evidenceTargetMessageId,
        modifier = modifier
    )

    val currentNotice = noticeText
    if (currentNotice != null) {
        AlertDialog(
            onDismissRequest = { noticeText = null },
            title = { Text("Deferred action") },
            text = { Text(currentNotice) },
            confirmButton = {
                TextButton(onClick = { noticeText = null }) {
                    Text("OK")
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
    onOpenContextHub: () -> Unit = {},
    evidenceTargetMessageId: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        ChatHeader(
            sessionTitle = state.sessionTitle,
            evidenceTargetMessageId = evidenceTargetMessageId,
            onOpenContextHub = onOpenContextHub
        )
        val generationStatusLabel = state.generationStatusLabel
        if (generationStatusLabel != null) {
            ChatGenerationStatus(generationStatusLabel)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (state.messages.isEmpty()) {
                EmptyChatTimeline()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.messages, key = { it.id }) { item ->
                        MessageBubble(
                            item = item,
                            onAction = { action -> onMessageAction(item, action) }
                        )
                    }
                }
            }
        }
        ChatComposer(
            composer = state.composer,
            onTextChange = onComposerTextChange,
            onSend = onSendMessage,
            onCancelQuote = onCancelQuote,
            onCreateImageDraft = onCreateImageDraft,
            onCancelImageDraft = onCancelImageDraft
        )
    }
}

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
                    text = "Local conversation",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (!evidenceTargetMessageId.isNullOrBlank()) {
                    Text(
                        text = "Evidence target: $evidenceTargetMessageId",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            TextButton(onClick = onOpenContextHub) {
                Text("Open context hub")
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
            text = "No messages yet",
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
    val isUser = item.roleLabel == "You"
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
        color = MaterialTheme.colorScheme.surface,
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
                        text = "Replying to: ${quoteTarget.excerpt}",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onCancelQuote) {
                        Text("Cancel")
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
                        Text("Cancel image")
                    }
                }
            }
            OutlinedTextField(
                value = composer.text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 1,
                maxLines = 5,
                label = { Text("Message") }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCreateImageDraft) {
                    Text("Image")
                }
                Button(
                    enabled = composer.canSend,
                    onClick = onSend
                ) {
                    Text("Send")
                }
            }
        }
    }
}

private fun String.toQuoteExcerpt(): String {
    val compact = trim().replace(Regex("\\s+"), " ")
    return compact.take(96).ifEmpty { "Message" }
}

private fun ChatGenerationOutcome.toUiState(): ChatGenerationUiState =
    when (this) {
        is ChatGenerationOutcome.Generated -> ChatGenerationUiState.Idle
        is ChatGenerationOutcome.ProviderFailed -> ChatGenerationUiState.Failure(message)
        ChatGenerationOutcome.NoModelConfigured -> ChatGenerationUiState.NoModel
        ChatGenerationOutcome.UnsupportedVision -> ChatGenerationUiState.Failure("Vision input unsupported")
        ChatGenerationOutcome.BlankPrompt -> ChatGenerationUiState.Failure("Blank prompt")
        ChatGenerationOutcome.Stale -> ChatGenerationUiState.Idle
    }
