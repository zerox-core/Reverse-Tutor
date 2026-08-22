@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.reversetutor.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

/**
 * Visual anchor for the inline "本轮学习提示" entry. Resolved purely from
 * [SessionConversationContract] already-published fields — never re-derives
 * mastery, action severity, graph edges, token use, or generation outcome.
 *
 * - [AfterAssistantMessage]: there is a current AI reply to anchor under.
 * - [TimelineEnd]: learning info exists but no reply to anchor under (e.g. a
 *   generation failure); Track A mounts the entry in the end-of-timeline system
 *   slot in this case.
 */
internal sealed interface SessionAssistantHintAnchor {
    data class AfterAssistantMessage(val messageId: String) : SessionAssistantHintAnchor
    data object TimelineEnd : SessionAssistantHintAnchor
}

/**
 * True when the contract carries any learning information worth surfacing as an
 * inline hint. Checks only already-published fields; performs no derivation.
 */
internal fun SessionConversationContract.hasInlineLearningHint(): Boolean =
    generation.assistantMessageId != null ||
        !generation.safeError.isNullOrBlank() ||
        generation.state !in setOf(GenerationState.IDLE, GenerationState.READY) ||
        action != null || evaluation != null || nextStep != null ||
        context.prerequisiteGaps.isNotEmpty() ||
        context.relatedMemory.isNotEmpty() ||
        context.sourceEvidence.isNotEmpty() ||
        context.historicalErrors.isNotEmpty() ||
        context.pendingReviewKnowledgePoints.isNotEmpty() ||
        context.warnings.isNotEmpty()

/**
 * Resolves where the inline hint should be mounted in the chat timeline.
 * Returns null when there is nothing to show ([hasInlineLearningHint] false).
 */
internal fun SessionConversationContract.inlineHintAnchor(): SessionAssistantHintAnchor? =
    if (!hasInlineLearningHint()) null
    else generation.assistantMessageId?.let(SessionAssistantHintAnchor::AfterAssistantMessage)
        ?: SessionAssistantHintAnchor.TimelineEnd

/**
 * Default-collapsed, italic light-gray "本轮学习提示" inline entry anchored to
 * the current AI reply. A tap opens [SessionAssistantPanel] as the detail
 * micro-panel without changing conversation business behavior.
 *
 * - Owns only local expanded state, keyed by (sessionId, turnId) and starting
 *   `false` for every new turn.
 * - All interaction intents are forwarded to [onInteraction]. For DISMISS the
 *   entry first collapses locally, then forwards DISMISS; all other intents are
 *   forwarded unchanged.
 * - Real placement in the chat timeline is owned by Track A. This composable
 *   does not touch AppShell, wiring, core, Repository, Room, or LLM code.
 */
@Composable
fun SessionAssistantReplyHint(
    contract: SessionConversationContract?,
    onInteraction: (SessionAssistantInteraction) -> Unit,
    modifier: Modifier = Modifier
) {
    if (contract == null || !contract.hasInlineLearningHint()) return
    var expanded by rememberSaveable(contract.sessionId, contract.turnId) {
        mutableStateOf(false)
    }
    if (expanded) {
        SessionAssistantPanel(
            contract = contract,
            onInteraction = { interaction ->
                if (interaction == SessionAssistantInteraction.DISMISS) {
                    expanded = false
                }
                onInteraction(interaction)
            },
            modifier = modifier
        )
        return
    }
    val type = LocalFormalTypeScale.current
    Surface(
        onClick = { expanded = true },
        color = Color.Transparent,
        contentColor = FormalColors.Muted,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag("session_assistant_inline_hint")
            .semantics { contentDescription = "查看本轮学习提示" }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "本轮学习提示",
                style = type.style(12f, 17f, color = FormalColors.Muted),
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = FormalColors.Muted.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
    }
}
