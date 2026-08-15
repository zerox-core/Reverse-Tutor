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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.reversetutor.core.data.message.MessageRecord
import com.reversetutor.core.data.message.MessageRepository
import com.reversetutor.core.data.sources.SourceRepository
import com.reversetutor.core.llm.LlmGenerationToken
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.model.MessageRole
import com.reversetutor.core.model.SourceParserStatus
import com.reversetutor.core.model.SourceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatRoute(
    messageRepository: MessageRepository,
    chatGenerationRepository: ChatGenerationRepository? = null,
    backgroundGenerationRepository: BackgroundGenerationRepository? = null,
    memoryRepository: MemoryRepository? = null,
    sourceRepository: SourceRepository? = null,
    sourceUsagePort: ChatSourceUsagePort = ChatSourceUsagePort.None,
    messageActionPort: ChatMessageActionPort = ChatMessageActionPort.Unavailable,
    messageDeletePort: ChatMessageDeletePort = ChatMessageDeletePort.Unavailable,
    pendingDeletionStore: ChatPendingDeletionStore = ChatPendingDeletionStore.None,
    clipboardPort: ChatClipboardPort = ChatClipboardPort.Unavailable,
    imageMediaPort: ChatImageMediaPort = ChatImageMediaPort.Unavailable,
    rememberedMessageStore: ChatRememberedMessageStore = ChatRememberedMessageStore.None,
    sessionId: String,
    sessionTitle: String,
    learnerRole: String = "学习者",
    sessionSnapshot: NewSessionConfiguration? = null,
    pendingImageDraft: ChatImageDraft? = null,
    pendingAttachment: ChatDraftAttachment? = null,
    attachmentNotice: String? = null,
    availableSourceAttachments: List<ChatDraftAttachment> = emptyList(),
    draftStore: ChatDraftStore = ChatDraftStore.None,
    attachmentOrderStore: ChatAttachmentOrderStore = ChatAttachmentOrderStore.None,
    cameraPermissionState: ChatPermissionState = ChatPermissionState.Requestable,
    evidenceTargetMessageId: String? = null,
    onPickImage: () -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onRequestCameraPermission: () -> Unit = {},
    onOpenCameraSettings: () -> Unit = {},
    onRetryAttachment: (ChatDraftAttachment) -> Unit = {},
    onImageDraftConsumed: () -> Unit = {},
    onAttachmentConsumed: () -> Unit = {},
    onAttachmentNoticeConsumed: () -> Unit = {},
    onBackgroundGenerationQueued: (String) -> Unit = {},
    onProviderGenerationFailed: (String) -> Unit = {},
    onComposerFocusChanged: (Boolean) -> Unit = {},
    onOpenContextHub: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSessionSources: () -> Unit = {},
    onOpenSessionSource: (String?) -> Unit = {},
    onReselectInvalidSource: (ChatInvalidSourceReselectRequest) -> Unit = {},
    onOpenExternalLink: (String) -> Unit = {},
    onOpenMediaSettings: () -> Unit = {},
    onPendingDeletionChanged: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    onExport: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {},
    onOpenSessionSettings: () -> Unit = {},
    initialScrollPosition: ChatScrollPosition = ChatScrollPosition(),
    onScrollPositionChanged: (ChatScrollPosition) -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var records by remember(sessionId) { mutableStateOf(emptyList<MessageRecord>()) }
    val restoredDraft = remember(sessionId, draftStore) {
        draftStore.load(sessionId) ?: ChatComposerDraft()
    }
    var clientRequestId by remember(sessionId) { mutableStateOf(restoredDraft.clientRequestId) }
    var composer by remember(sessionId) { mutableStateOf(ChatComposerState.from(restoredDraft)) }
    var generation by remember(sessionId) { mutableStateOf<ChatGenerationUiState>(ChatGenerationUiState.Idle) }
    val latestProviderGenerationFailed by rememberUpdatedState(onProviderGenerationFailed)
    val directGenerationCoordinator = remember(sessionId, chatGenerationRepository) {
        ChatGenerationCoordinator(
            executor = ChatGenerationExecutor { input, nowEpochMillis, isTokenCurrent ->
                val outcome = chatGenerationRepository?.generateReply(
                    input = input,
                    nowEpochMillis = nowEpochMillis,
                    isTokenCurrent = isTokenCurrent
                ) ?: ChatGenerationOutcome.NoModelConfigured
                if (outcome is ChatGenerationOutcome.ProviderFailed) {
                    latestProviderGenerationFailed(input.userMessageId)
                }
                outcome
            }
        )
    }
    DisposableEffect(directGenerationCoordinator) {
        onDispose { directGenerationCoordinator.invalidate() }
    }
    var activeGenerationToken by remember(sessionId) { mutableStateOf<LlmGenerationToken?>(null) }
    var activeBackgroundJobId by remember(sessionId) { mutableStateOf<String?>(null) }
    var refreshKey by remember(sessionId) { mutableIntStateOf(0) }
    var noticeText by remember { mutableStateOf<String?>(null) }
    var noticeSettingsAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var sourceItems by remember(sessionId) { mutableStateOf(emptyList<ChatSourceUi>()) }
    var rememberedMessageIds by remember(sessionId, rememberedMessageStore) {
        mutableStateOf(rememberedMessageStore.loadRememberedMessageIds())
    }
    var pendingDeletion by remember(sessionId, pendingDeletionStore) {
        mutableStateOf(pendingDeletionStore.load(sessionId))
    }
    var pendingDeletionRetryRequired by remember(sessionId) { mutableStateOf(false) }
    var memoryDraft by remember(sessionId) { mutableStateOf<ChatMemoryDraft?>(null) }
    var memoryError by remember(sessionId) { mutableStateOf<String?>(null) }
    var deleteConfirmation by remember(sessionId) { mutableStateOf<ChatDeleteConfirmation?>(null) }
    val deletionCoordinator = remember(sessionId, messageDeletePort, pendingDeletionStore) {
        ChatMessageDeletionCoordinator(
            store = pendingDeletionStore,
            deletePort = messageDeletePort
        )
    }
    val sendCoordinator = remember(sessionId, messageRepository, draftStore, attachmentOrderStore) {
        ChatSendCoordinator(
            draftStore = draftStore,
            sendPort = ChatRepositorySendAdapter(
                sessionId = sessionId,
                submitter = ChatRepositoryMessageSubmitter { request ->
                    messageRepository.sendUserMessage(
                        sessionId = request.sessionId,
                        text = request.text,
                        nowEpochMillis = request.nowEpochMillis,
                        messageId = request.messageId,
                        quote = request.quote,
                        attachments = request.attachments
                    ) != null
                }
            ),
            attachmentOrderStore = attachmentOrderStore
        )
    }

    fun reload() {
        refreshKey += 1
    }

    fun updateComposer(next: ChatComposerState, persist: Boolean = true) {
        composer = next
        if (persist) {
            draftStore.save(sessionId, next.toPersistentDraft(clientRequestId))
        }
    }

    fun addAttachment(attachment: ChatDraftAttachment) {
        val draft = composer.toPersistentDraft(clientRequestId)
        when (val result = ChatAttachmentPolicy.add(draft, attachment)) {
            is ChatAttachmentMutation.Accepted -> updateComposer(
                ChatComposerState.from(result.draft).copy(notice = null)
            )
            is ChatAttachmentMutation.Rejected -> updateComposer(
                composer.copy(
                    notice = when (result.reason) {
                        ChatAttachmentRejection.TooMany -> "每条消息最多添加 9 个附件。"
                        ChatAttachmentRejection.ImageTooLarge -> "单张图片不能超过 20 MB。"
                    }
                )
            )
        }
    }

    LaunchedEffect(sessionId, refreshKey) {
        val loadedRecords = applyStoredAttachmentOrder(
            messageRepository.listMessageRecords(sessionId),
            attachmentOrderStore
        )
        val restoredPending = pendingDeletionStore.load(sessionId)
        if (restoredPending != null && System.currentTimeMillis() >= restoredPending.expiresAtEpochMillis) {
            pendingDeletionRetryRequired = when (
                deletionCoordinator.finalize(sessionId, System.currentTimeMillis())
            ) {
                ChatFinalizeDeletionResult.RetryableFailure -> true
                else -> false
            }
            pendingDeletion = pendingDeletionStore.load(sessionId)
            records = loadedRecords.filterNot { it.message.id == restoredPending.messageId }
        } else {
            pendingDeletion = restoredPending
            pendingDeletionRetryRequired = false
            records = loadedRecords.filterNot { it.message.id == restoredPending?.messageId }
        }
        sourceItems = sourceRepository?.listSourcesWithChunks()?.map { source ->
            ChatSourceUi(
                id = source.source.id,
                displayName = source.source.title,
                typeLabel = source.source.type.toChatTypeLabel(),
                stateLabel = when (source.source.parserStatus) {
                    SourceParserStatus.FullyLocal -> "可用"
                    SourceParserStatus.PartiallyLocal -> "部分可用"
                    SourceParserStatus.FutureAssisted -> "等待解析"
                    SourceParserStatus.Unsupported -> "不支持解析"
                    SourceParserStatus.Failed -> "解析失败"
                },
                valid = source.source.parserStatus != SourceParserStatus.Failed
            )
        }.orEmpty()
        rememberedMessageIds = rememberedMessageStore.loadRememberedMessageIds()
    }

    LaunchedEffect(pendingDeletion?.messageId, pendingDeletion?.expiresAtEpochMillis) {
        val current = pendingDeletion ?: return@LaunchedEffect
        val remaining = current.expiresAtEpochMillis - System.currentTimeMillis()
        if (remaining > 0L) delay(remaining)
        when (deletionCoordinator.finalize(sessionId, System.currentTimeMillis())) {
            ChatFinalizeDeletionResult.Success,
            ChatFinalizeDeletionResult.AlreadyAbsent,
            ChatFinalizeDeletionResult.NothingPending -> {
                pendingDeletion = null
                pendingDeletionRetryRequired = false
                reload()
            }
            ChatFinalizeDeletionResult.RetryableFailure -> {
                pendingDeletionRetryRequired = true
                noticeText = "删除消息失败，请重试。"
            }
            ChatFinalizeDeletionResult.NotDue -> Unit
        }
    }

    LaunchedEffect(pendingImageDraft?.requestId) {
        val imageDraft = pendingImageDraft ?: return@LaunchedEffect
        addAttachment(imageDraft.toChatDraftAttachment())
        onImageDraftConsumed()
    }

    LaunchedEffect(pendingAttachment?.id) {
        val attachment = pendingAttachment ?: return@LaunchedEffect
        if (composer.orderedAttachments.any { it.id == attachment.id }) {
            updateComposer(
                ChatComposerState.from(
                    composer.toPersistentDraft(clientRequestId).copy(
                        attachments = composer.orderedAttachments.map {
                            if (it.id == attachment.id) attachment else it
                        }
                    )
                )
            )
        } else {
            addAttachment(attachment)
        }
        onAttachmentConsumed()
    }

    LaunchedEffect(attachmentNotice) {
        val notice = attachmentNotice ?: return@LaunchedEffect
        updateComposer(composer.copy(notice = notice))
        onAttachmentNoticeConsumed()
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
        state = buildChatRouteUiState(
            sessionTitle = sessionTitle,
            records = records,
            composer = composer,
            generation = generation,
            learnerRoleFallback = learnerRole,
            sessionSnapshot = sessionSnapshot,
            sources = sourceItems,
            currentSessionSourceIds = availableSourceAttachments.mapNotNullTo(linkedSetOf()) { it.sourceId },
            rememberedMessageIds = rememberedMessageIds,
            pendingDeletion = pendingDeletion,
            pendingDeletionRetryRequired = pendingDeletionRetryRequired
        ),
        onComposerTextChange = { updateComposer(composer.copy(text = it, sendFailure = null, notice = null)) },
        onSendMessage = {
            if (composer.canSend) {
                val sentComposer = composer.copy(isSending = true, sendFailure = null, notice = null)
                updateComposer(sentComposer)
                scope.launch {
                    val sentDraft = sentComposer.toPersistentDraft(clientRequestId)
                    val attempt = sendCoordinator.send(sessionId, sentDraft)
                    when (attempt) {
                        is ChatSendAttempt.Failed -> {
                            updateComposer(
                                ChatComposerState.from(sentDraft).copy(
                                    isSending = false,
                                    sendFailure = attempt.message
                                )
                            )
                            return@launch
                        }
                        ChatSendAttempt.DuplicateBlocked -> return@launch
                        is ChatSendAttempt.Sent -> Unit
                    }
                    clientRequestId = ChatComposerDraft().clientRequestId
                    composer = ChatComposerState(text = "")
                    reload()
                    val userMessage = messageRepository.listMessages(sessionId)
                        .firstOrNull { it.id == attempt.messageId }
                        ?: return@launch
                    val token = LlmGenerationToken("${userMessage.id}-${System.currentTimeMillis()}")
                    val contextEvidence = buildGenerationChatContextEvidence(
                        userText = userMessage.text,
                        memoryRepository = memoryRepository,
                        sourceRepository = sourceRepository,
                        sessionSnapshot = sessionSnapshot,
                        sessionId = sessionId,
                        sourceUsagePort = sourceUsagePort,
                        usedAtEpochMillis = System.currentTimeMillis()
                    )
                    val imageAttachments = sentComposer.toAttachmentDrafts()
                        .filter { it.mimeType?.startsWith("image/") == true }
                        .mapIndexed { index, attachment ->
                            attachment.toAttachment(
                                spaceId = userMessage.spaceId,
                                messageId = userMessage.id,
                                index = index
                            )
                        }
                    val backgroundRepository = backgroundGenerationRepository
                    if (backgroundRepository != null) {
                        activeGenerationToken = token
                        generation = ChatGenerationUiState.Pending
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
                    directGenerationCoordinator.generate(
                        input = ChatGenerationInput(
                            sessionId = sessionId,
                            userMessageId = userMessage.id,
                            userText = userMessage.text,
                            token = token,
                            quoteExcerpt = sentComposer.quoteTarget?.excerpt,
                            imageAttachments = imageAttachments,
                            contextEvidence = contextEvidence
                        ),
                        onStateChanged = { generation = it }
                    )
                    reload()
                }
            }
        },
        onCancelQuote = {
            updateComposer(composer.copy(quoteTarget = null))
        },
        onCreateImageDraft = {
            onPickImage()
        },
        onCancelImageDraft = {
            val imageId = composer.orderedAttachments.lastOrNull { it.kind == ChatAttachmentKind.Image }?.id
            if (imageId != null) {
                updateComposer(ChatComposerState.from(ChatAttachmentPolicy.remove(composer.toPersistentDraft(clientRequestId), imageId)))
            }
        },
        onMessageAction = { item, action ->
            when (action) {
                ChatMessageAction.Copy -> {
                    noticeText = when (clipboardPort.copyPlainText(item.text)) {
                        ChatClipboardResult.Copied -> "已复制消息正文。"
                        ChatClipboardResult.Unavailable -> "当前设备不能使用剪贴板。"
                        ChatClipboardResult.Failed -> "复制失败，请重试。"
                    }
                }
                ChatMessageAction.Quote -> {
                    updateComposer(composer.copy(
                        quoteTarget = ChatQuoteTarget(
                            messageId = item.id,
                            excerpt = item.text.toQuoteExcerpt(),
                            sourceIdentity = item.roleLabel
                        )
                    ))
                }
                ChatMessageAction.Remember -> {
                    memoryError = null
                    memoryDraft = ChatMemoryDraft(item.id, item.spaceId, item.text)
                }
                ChatMessageAction.LocateSource -> {
                    val sourceIds = item.attachments.mapNotNull { it.sourceId }.distinct()
                    when (sourceIds.size) {
                        0 -> noticeText = "这条消息没有关联资料。"
                        1 -> onOpenSessionSource(sourceIds.single())
                        else -> onOpenSessionSource(null)
                    }
                }
                ChatMessageAction.Delete -> {
                    scope.launch {
                        deleteConfirmation = ChatDeleteConfirmation(
                            messageId = item.id,
                            spaceId = item.spaceId,
                            messagePreview = item.text.toQuoteExcerpt(),
                            impact = messageActionPort.loadDeleteImpact(item.id, item.spaceId)
                        )
                    }
                }
            }
        },
        memoryDraft = memoryDraft,
        memoryError = memoryError,
        onMemoryDraftChange = {
            memoryDraft = it
            memoryError = null
        },
        onDismissMemory = {
            memoryDraft = null
            memoryError = null
        },
        onConfirmMemory = {
            val current = memoryDraft ?: return@ChatScreen
            scope.launch {
                when (val result = messageActionPort.remember(current)) {
                    ChatMemoryCommitResult.Saved -> {
                        val saved = ChatRememberedMessageMetadata(
                            messageId = current.messageId,
                            category = current.category,
                            rememberedAtEpochMillis = System.currentTimeMillis()
                        )
                        rememberedMessageStore.save(saved)
                        rememberedMessageIds = rememberedMessageIds + current.messageId
                        memoryDraft = null
                        memoryError = null
                        noticeText = "已保存到学习脉络。"
                    }
                    is ChatMemoryCommitResult.Unavailable -> memoryError = result.message
                    is ChatMemoryCommitResult.Failed -> memoryError = result.message
                }
            }
        },
        deleteConfirmation = deleteConfirmation,
        onDismissDelete = { deleteConfirmation = null },
        onConfirmDelete = {
            val current = deleteConfirmation ?: return@ChatScreen
            if (!current.impact.canDeleteMessageOnly) {
                noticeText = current.impact.deletionBoundary
                return@ChatScreen
            }
            when (val result = deletionCoordinator.request(
                sessionId = sessionId,
                messageId = current.messageId,
                nowEpochMillis = System.currentTimeMillis()
            )) {
                is ChatDeletionRequestResult.Accepted -> {
                    pendingDeletion = result.pending
                    pendingDeletionRetryRequired = false
                    records = records.filterNot { it.message.id == current.messageId }
                    onPendingDeletionChanged()
                }
                is ChatDeletionRequestResult.AlreadyPending -> {
                    noticeText = "先处理当前撤销窗口，再删除其他消息。"
                }
            }
            deleteConfirmation = null
        },
        onUndoDelete = {
            when (deletionCoordinator.undo(sessionId, System.currentTimeMillis())) {
                ChatUndoDeletionResult.Undone -> {
                    pendingDeletion = null
                    pendingDeletionRetryRequired = false
                    reload()
                }
                ChatUndoDeletionResult.Expired -> scope.launch {
                    when (deletionCoordinator.finalize(sessionId, System.currentTimeMillis())) {
                        ChatFinalizeDeletionResult.Success,
                        ChatFinalizeDeletionResult.AlreadyAbsent,
                        ChatFinalizeDeletionResult.NothingPending -> {
                            pendingDeletion = null
                            pendingDeletionRetryRequired = false
                            reload()
                        }
                        ChatFinalizeDeletionResult.RetryableFailure -> {
                            pendingDeletionRetryRequired = true
                            noticeText = "删除消息失败，请重试。"
                        }
                        ChatFinalizeDeletionResult.NotDue -> Unit
                    }
                }
                ChatUndoDeletionResult.NothingPending -> Unit
            }
        },
        onRetryDelete = {
            scope.launch {
                when (deletionCoordinator.finalize(sessionId, System.currentTimeMillis())) {
                    ChatFinalizeDeletionResult.Success,
                    ChatFinalizeDeletionResult.AlreadyAbsent,
                    ChatFinalizeDeletionResult.NothingPending -> {
                        pendingDeletion = null
                        pendingDeletionRetryRequired = false
                        reload()
                    }
                    ChatFinalizeDeletionResult.RetryableFailure -> {
                        pendingDeletionRetryRequired = true
                        noticeText = "删除消息失败，请重试。"
                    }
                    ChatFinalizeDeletionResult.NotDue -> Unit
                }
            }
        },
        onCopyRichSource = { source -> clipboardPort.copyPlainText(source) },
        onSaveImage = { attachment ->
            scope.launch {
                val uri = attachment.uri
                noticeText = if (uri == null) {
                    "图片地址无效，无法保存。"
                } else {
                    when (val result = imageMediaPort.save(uri, attachment.name, attachment.mimeType)) {
                        ChatMediaResult.Success -> "图片已保存。"
                        is ChatMediaResult.PermissionDenied -> {
                            noticeSettingsAction = onOpenMediaSettings
                            result.message
                        }
                        is ChatMediaResult.Failure -> result.message
                    }
                }
            }
        },
        onShareImage = { attachment ->
            scope.launch {
                val uri = attachment.uri
                noticeText = if (uri == null) {
                    "图片地址无效，无法分享。"
                } else {
                    when (val result = imageMediaPort.share(uri, attachment.name, attachment.mimeType)) {
                        ChatMediaResult.Success -> "已打开系统分享。"
                        is ChatMediaResult.PermissionDenied -> {
                            noticeSettingsAction = onOpenMediaSettings
                            result.message
                        }
                        is ChatMediaResult.Failure -> result.message
                    }
                }
            }
        },
        onOpenSessionSource = onOpenSessionSource,
        onReselectInvalidSource = { sourceId, originalName ->
            onReselectInvalidSource(ChatInvalidSourceReselectRequest(sessionId, sourceId, originalName))
        },
        onOpenExternalLink = onOpenExternalLink,
        onComposerFocusChanged = onComposerFocusChanged,
        onOpenContextHub = onOpenContextHub,
        onOpenSearch = onOpenSearch,
        onOpenSources = onOpenSources,
        onExport = onExport,
        onOpenModelSettings = onOpenModelSettings,
        availableSourceAttachments = availableSourceAttachments,
        cameraPermissionState = cameraPermissionState,
        onPickImages = onPickImage,
        onSelectSource = ::addAttachment,
        onTakePhoto = onTakePhoto,
        onRequestCameraPermission = onRequestCameraPermission,
        onOpenCameraSettings = onOpenCameraSettings,
        onOpenSessionSources = onOpenSessionSources,
        onRemoveAttachment = { id ->
            updateComposer(
                ChatComposerState.from(
                    ChatAttachmentPolicy.remove(composer.toPersistentDraft(clientRequestId), id)
                )
            )
        },
        onRetryAttachment = { id ->
            val attachment = composer.orderedAttachments.firstOrNull { it.id == id }
            if (attachment != null) {
                updateComposer(
                    ChatComposerState.from(
                        ChatAttachmentPolicy.retry(composer.toPersistentDraft(clientRequestId), id)
                    )
                )
                onRetryAttachment(attachment)
            }
        },
        onMoveAttachment = { from, to ->
            updateComposer(
                ChatComposerState.from(
                    ChatAttachmentPolicy.move(composer.toPersistentDraft(clientRequestId), from, to)
                )
            )
        },
        onOpenSessionSettings = onOpenSessionSettings,
        initialScrollPosition = initialScrollPosition,
        onScrollPositionChanged = onScrollPositionChanged,
        onBack = onBack,
        evidenceTargetMessageId = evidenceTargetMessageId,
        modifier = modifier
    )

    val currentNotice = noticeText
    if (currentNotice != null) {
        AlertDialog(
            onDismissRequest = {
                noticeText = null
                noticeSettingsAction = null
            },
            title = { Text("提示") },
            text = { Text(currentNotice) },
            confirmButton = {
                TextButton(onClick = {
                    val action = noticeSettingsAction
                    noticeText = null
                    noticeSettingsAction = null
                    action?.invoke()
                }) {
                    Text(if (noticeSettingsAction == null) "知道了" else "打开设置")
                }
            }
        )
    }
}

private fun SourceType.toChatTypeLabel(): String = when (this) {
    SourceType.JsonExport -> "JSON"
    SourceType.Pdf -> "PDF"
    SourceType.Docx -> "Word"
    SourceType.Text -> "文本"
    SourceType.Markdown -> "Markdown"
    SourceType.Html -> "网页"
    SourceType.Pptx -> "演示文稿"
    SourceType.Epub -> "电子书"
    SourceType.Image -> "图片"
    SourceType.Other -> "资料"
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
    memoryDraft: ChatMemoryDraft? = null,
    memoryError: String? = null,
    onMemoryDraftChange: (ChatMemoryDraft) -> Unit = {},
    onDismissMemory: () -> Unit = {},
    onConfirmMemory: () -> Unit = {},
    deleteConfirmation: ChatDeleteConfirmation? = null,
    onDismissDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onUndoDelete: () -> Unit = {},
    onRetryDelete: () -> Unit = {},
    onCopyRichSource: (String) -> ChatClipboardResult = { ChatClipboardResult.Unavailable },
    onSaveImage: (ChatAttachmentUi) -> Unit = {},
    onShareImage: (ChatAttachmentUi) -> Unit = {},
    onOpenSessionSource: (String?) -> Unit = {},
    onReselectInvalidSource: (String, String) -> Unit = { _, _ -> },
    onOpenExternalLink: (String) -> Unit = {},
    onComposerFocusChanged: (Boolean) -> Unit = {},
    onOpenContextHub: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenSources: () -> Unit = {},
    onExport: () -> Unit = {},
    onOpenModelSettings: () -> Unit = {},
    availableSourceAttachments: List<ChatDraftAttachment> = emptyList(),
    cameraPermissionState: ChatPermissionState = ChatPermissionState.Requestable,
    onPickImages: () -> Unit = onCreateImageDraft,
    onSelectSource: (ChatDraftAttachment) -> Unit = {},
    onTakePhoto: () -> Unit = {},
    onRequestCameraPermission: () -> Unit = {},
    onOpenCameraSettings: () -> Unit = {},
    onOpenSessionSources: () -> Unit = {},
    onRemoveAttachment: (String) -> Unit = {},
    onRetryAttachment: (String) -> Unit = {},
    onMoveAttachment: (Int, Int) -> Unit = { _, _ -> },
    onRetrySend: () -> Unit = onSendMessage,
    onOpenSessionSettings: () -> Unit = {},
    initialScrollPosition: ChatScrollPosition = ChatScrollPosition(),
    onScrollPositionChanged: (ChatScrollPosition) -> Unit = {},
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
        memoryDraft = memoryDraft,
        memoryError = memoryError,
        onMemoryDraftChange = onMemoryDraftChange,
        onDismissMemory = onDismissMemory,
        onConfirmMemory = onConfirmMemory,
        deleteConfirmation = deleteConfirmation,
        onDismissDelete = onDismissDelete,
        onConfirmDelete = onConfirmDelete,
        onUndoDelete = onUndoDelete,
        onRetryDelete = onRetryDelete,
        onCopyRichSource = onCopyRichSource,
        onSaveImage = onSaveImage,
        onShareImage = onShareImage,
        onOpenSessionSource = onOpenSessionSource,
        onReselectInvalidSource = onReselectInvalidSource,
        onOpenExternalLink = onOpenExternalLink,
        onComposerFocusChanged = onComposerFocusChanged,
        onOpenContextHub = onOpenContextHub,
        onOpenModelSettings = onOpenModelSettings,
        onOpenSources = onOpenSources,
        onExport = onExport,
        onOpenSearch = onOpenSearch,
        availableSourceAttachments = availableSourceAttachments,
        cameraPermissionState = cameraPermissionState,
        onPickImages = onPickImages,
        onSelectSource = onSelectSource,
        onTakePhoto = onTakePhoto,
        onRequestCameraPermission = onRequestCameraPermission,
        onOpenCameraSettings = onOpenCameraSettings,
        onOpenSessionSources = onOpenSessionSources,
        onRemoveAttachment = onRemoveAttachment,
        onRetryAttachment = onRetryAttachment,
        onMoveAttachment = onMoveAttachment,
        onRetrySend = onRetrySend,
        onOpenSessionSettings = onOpenSessionSettings,
        initialScrollPosition = initialScrollPosition,
        onScrollPositionChanged = onScrollPositionChanged,
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
                        modifier = Modifier.size(44.dp),
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
