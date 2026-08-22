@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalElevations
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.ErrorReferenceContract
import com.reversetutor.core.domain.MemoryReferenceContract
import com.reversetutor.core.domain.SourceReferenceContract
import kotlin.math.roundToInt

/**
 * Visual-agnostic interaction surface emitted by the session assistant panel.
 *
 * The panel never re-derives action/evaluation/context state — it only renders
 * the immutable [SessionConversationContract] and dispatches these intents.
 * RETRY routes back to the Facade/Coordinator; OPEN_* are navigation/display
 * intents; DISMISS only changes local panel visibility and never cancels,
 * deletes, or rewrites a backend generation.
 */
enum class SessionAssistantInteraction {
    RETRY,
    OPEN_CONTEXT,
    OPEN_SOURCE,
    SHOW_EVALUATION,
    DISMISS
}

/**
 * Pure projection of [SessionConversationContract] into display-safe values.
 * No strategy, mastery, or action derivation happens here — every field is a
 * 1:1 projection of the contract. Null [contract] means the panel is hidden.
 *
 * Warnings are mapped via [toSafeWarningText] to fixed Chinese labels —
 * [com.reversetutor.core.domain.ContextWarning.source] and
 * [com.reversetutor.core.domain.ContextWarning.message] are never surfaced.
 */
data class SessionAssistantPanelState(
    val sessionId: String,
    val turnId: String?,
    val visible: Boolean,
    val generationState: GenerationState,
    val generationLabel: String?,
    val assistantMessageId: String?,
    val assistantText: String?,
    val safeError: String?,
    val evaluationSummary: String?,
    val actionLabel: String?,
    val processSummary: String?,
    val currentKnowledgePoint: String?,
    val nextStepHint: String?,
    val prerequisiteGaps: List<String>,
    val relatedMemory: List<MemoryReferenceContract>,
    val sourceEvidence: List<SourceReferenceContract>,
    val historicalErrors: List<ErrorReferenceContract>,
    val pendingReviewKnowledgePoints: List<String>,
    val warnings: List<String>,
    val retryable: Boolean,
    val eventInteractions: List<SessionAssistantInteraction>
) {
    val hasContext: Boolean
        get() = prerequisiteGaps.isNotEmpty() ||
            relatedMemory.isNotEmpty() ||
            sourceEvidence.isNotEmpty() ||
            historicalErrors.isNotEmpty() ||
            pendingReviewKnowledgePoints.isNotEmpty()
}

internal fun SessionConversationContract.toPanelState(): SessionAssistantPanelState {
    val label = generation.state.generationLabel()
    val eval = evaluation?.let { e ->
        buildString {
            append("正确 ${(e.correctness * 100).roundToInt()}%")
            append(" · 深度 ${(e.depth * 100).roundToInt()}%")
            append(" · ${e.entryStatus.toEntryStatusLabel()}")
        }
    }
    val act = action?.let { a ->
        "${a.type.toActionTypeLabel()} · ${a.knowledgePoint.ifBlank { "当前知识点" }}".ifBlank { null }
    }
    val retryable = events.any { it.type == ConversationUiEventType.RETRY } &&
        generation.state == GenerationState.ERROR
    val interactions = events
        .map { it.type.toInteraction() }
        .distinct()
        .ifEmpty { listOf(SessionAssistantInteraction.DISMISS) }
    // B1 fix: warnings mapped to safe fixed Chinese — never expose source/message
    val warns = context.warnings.toSafeWarningTexts()
    return SessionAssistantPanelState(
        sessionId = sessionId,
        turnId = turnId,
        visible = true,
        generationState = generation.state,
        generationLabel = label,
        assistantMessageId = generation.assistantMessageId,
        assistantText = generation.assistantText,
        safeError = generation.safeError,
        evaluationSummary = eval,
        actionLabel = act,
        processSummary = processSummary,
        currentKnowledgePoint = currentKnowledgePoint,
        nextStepHint = nextStep?.hint,
        prerequisiteGaps = context.prerequisiteGaps,
        relatedMemory = context.relatedMemory,
        sourceEvidence = context.sourceEvidence,
        historicalErrors = context.historicalErrors,
        pendingReviewKnowledgePoints = context.pendingReviewKnowledgePoints,
        warnings = warns,
        retryable = retryable,
        eventInteractions = interactions
    )
}

private fun GenerationState.generationLabel(): String? = when (this) {
    GenerationState.IDLE -> null
    GenerationState.LOADING -> "正在生成回复…"
    GenerationState.READY -> null
    GenerationState.ERROR -> "生成失败"
    GenerationState.NO_MODEL -> "未配置模型"
    GenerationState.UNSUPPORTED -> "暂不支持该输入"
    GenerationState.BLANK -> "请输入内容后再发送"
}

private fun String.toEntryStatusLabel(): String = when (this) {
    "has_entry" -> "已有入口"
    "no_entry" -> "尚无入口"
    "recall_decay" -> "记忆衰退"
    else -> this
}

private fun String.toActionTypeLabel(): String = when (this) {
    "ask" -> "提问"
    "probe" -> "探查"
    "challenge" -> "挑战"
    "clue" -> "线索"
    "scaffold_example" -> "脚手架示例"
    "small_lecture" -> "微讲"
    "examiner_verify" -> "核验"
    "emote" -> "共情"
    "persuade" -> "劝说"
    "next" -> "推进"
    "recap" -> "复盘"
    "decompose" -> "拆解目标"
    "advance" -> "推进目标"
    "verify_done" -> "核验完成"
    "unblock" -> "解阻塞"
    "empathize" -> "共情"
    "observe" -> "观察"
    "soft_guide" -> "轻引导"
    else -> this
}

private fun ConversationUiEventType.toInteraction(): SessionAssistantInteraction = when (this) {
    ConversationUiEventType.RETRY -> SessionAssistantInteraction.RETRY
    ConversationUiEventType.OPEN_CONTEXT -> SessionAssistantInteraction.OPEN_CONTEXT
    ConversationUiEventType.OPEN_SOURCE -> SessionAssistantInteraction.OPEN_SOURCE
    ConversationUiEventType.SHOW_EVALUATION -> SessionAssistantInteraction.SHOW_EVALUATION
    ConversationUiEventType.DISMISS -> SessionAssistantInteraction.DISMISS
}

/**
 * Session assistant side-panel. Renders the current [SessionConversationContract]
 * and dispatches [SessionAssistantInteraction] intents. Pure presentation: the
 * host owns the contract and routes RETRY to the Facade/Coordinator; DISMISS only
 * toggles local visibility.
 *
 * Pass `contract = null` to render the dismissed/empty state (nothing shown).
 *
 * Uses [com.reversetutor.core.design.FormalColors] and
 * [com.reversetutor.core.design.LocalFormalTypeScale] for consistent theming.
 */
@Composable
fun SessionAssistantPanel(
    contract: SessionConversationContract?,
    onInteraction: (SessionAssistantInteraction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (contract == null) return
    val state = remember(contract) { contract.toPanelState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { onInteraction(SessionAssistantInteraction.DISMISS) },
        sheetState = sheetState,
        modifier = modifier.widthIn(max = 480.dp),
        containerColor = FormalColors.Background,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PanelHeader(state = state, onDismiss = { onInteraction(SessionAssistantInteraction.DISMISS) })
            GenerationSection(state = state, onRetry = { onInteraction(SessionAssistantInteraction.RETRY) })
            EvaluationSection(state = state, onShowEvaluation = {
                onInteraction(SessionAssistantInteraction.SHOW_EVALUATION)
            })
            ActionSection(state = state)
            ContextSection(
                state = state,
                onOpenContext = { onInteraction(SessionAssistantInteraction.OPEN_CONTEXT) },
                onOpenSource = { onInteraction(SessionAssistantInteraction.OPEN_SOURCE) }
            )
        }
    }
}

@Composable
private fun PanelHeader(state: SessionAssistantPanelState, onDismiss: () -> Unit) {
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "会话辅助",
                style = type.style(16f, 23f, FontWeight.SemiBold, FormalColors.Ink)
            )
            Text(
                text = state.currentKnowledgePoint ?: "当前知识点",
                style = type.style(11f, 16f, color = FormalColors.Muted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            onClick = onDismiss,
            color = Color.Transparent,
            contentColor = FormalColors.Muted,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .size(48.dp)
                .testTag("session_assistant_dismiss")
                .semantics { contentDescription = "关闭会话辅助面板" }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("×", style = type.style(18f, 24f, color = FormalColors.Muted))
            }
        }
    }
}

@Composable
private fun GenerationSection(state: SessionAssistantPanelState, onRetry: () -> Unit) {
    val type = LocalFormalTypeScale.current
    val label = state.generationLabel
    val text = state.assistantText
    if (label == null && text.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!text.isNullOrBlank()) {
            PanelCard {
                Text(
                    text = text,
                    style = type.style(13f, 20f, color = FormalColors.Ink)
                )
            }
        }
        if (label != null) {
            val isError = state.generationState == GenerationState.ERROR ||
                state.generationState == GenerationState.NO_MODEL
            PanelCard(
                tint = if (isError) FormalColors.Danger.copy(alpha = 0.08f) else FormalColors.SurfaceSubtle,
                border = if (isError) FormalColors.Danger else FormalColors.Border
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = type.style(12f, 17f, color = if (isError) FormalColors.Danger else FormalColors.Muted),
                        modifier = Modifier.weight(1f)
                    )
                    if (state.retryable) {
                        TextButton(
                            onClick = onRetry,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("session_assistant_retry")
                                .semantics { contentDescription = "重试生成回复" }
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "重试",
                                style = type.style(12f, 17f, color = FormalColors.Primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvaluationSection(state: SessionAssistantPanelState, onShowEvaluation: () -> Unit) {
    val type = LocalFormalTypeScale.current
    val summary = state.evaluationSummary ?: return
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary,
                style = type.style(11f, 16f, color = FormalColors.Muted),
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onShowEvaluation,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("session_assistant_show_evaluation")
                    .semantics { contentDescription = "查看评估详情" }
            ) {
                Text(
                    "查看评估",
                    style = type.style(11f, 16f, color = FormalColors.Primary)
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = FormalColors.Primary
                )
            }
        }
    }
}

@Composable
private fun ActionSection(state: SessionAssistantPanelState) {
    val type = LocalFormalTypeScale.current
    val label = state.actionLabel ?: return
    PanelCard(tint = FormalColors.PrimarySoft, border = FormalColors.Primary.copy(alpha = 0.3f)) {
        Text(
            text = label,
            style = type.style(12f, 17f, FontWeight.Medium, color = FormalColors.Primary)
        )
    }
}

@Composable
private fun ContextSection(
    state: SessionAssistantPanelState,
    onOpenContext: () -> Unit,
    onOpenSource: () -> Unit
) {
    val type = LocalFormalTypeScale.current
    if (!state.hasContext && state.warnings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (state.prerequisiteGaps.isNotEmpty()) {
            ContextRow(label = "前置缺口", values = state.prerequisiteGaps)
        }
        if (state.pendingReviewKnowledgePoints.isNotEmpty()) {
            ContextRow(label = "待复习", values = state.pendingReviewKnowledgePoints)
        }
        if (state.relatedMemory.isNotEmpty()) {
            ContextRow(
                label = "关联记忆",
                values = state.relatedMemory.map { it.summary },
                onOpen = onOpenContext
            )
        }
        if (state.sourceEvidence.isNotEmpty()) {
            ContextRow(
                label = "来源证据",
                values = state.sourceEvidence.map { it.title },
                onOpen = onOpenSource
            )
        }
        if (state.historicalErrors.isNotEmpty()) {
            ContextRow(
                label = "历史错误",
                values = state.historicalErrors.map { it.errorType }
            )
        }
        // B2: non-blocking warnings — mapped to safe Chinese text
        state.warnings.take(3).forEach { warning ->
            Text(
                text = warning,
                style = type.style(10f, 14f, color = FormalColors.Muted),
                modifier = Modifier.testTag("session_assistant_warning")
            )
        }
    }
}

@Composable
private fun ContextRow(
    label: String,
    values: List<String>,
    onOpen: (() -> Unit)? = null
) {
    val type = LocalFormalTypeScale.current
    PanelCard {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = type.style(11f, 16f, FontWeight.Medium, color = FormalColors.Muted),
                modifier = Modifier.width(64.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                values.forEach { value ->
                    Text(
                        text = value.ifBlank { "—" },
                        style = type.style(11f, 16f, color = FormalColors.Ink),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onOpen != null) {
                Surface(
                    onClick = onOpen,
                    color = Color.Transparent,
                    contentColor = FormalColors.Primary,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "打开${label}" }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            "打开",
                            style = type.style(11f, 16f, color = FormalColors.Primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelCard(
    tint: Color = FormalColors.SurfaceSubtle,
    border: Color = FormalColors.Border,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = tint,
        shape = RoundedCornerShape(FormalShapes.CompactRadius),
        border = BorderStroke(1.dp, border)
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            content()
        }
    }
}
