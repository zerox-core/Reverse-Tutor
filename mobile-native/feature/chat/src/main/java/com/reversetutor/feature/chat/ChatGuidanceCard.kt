package com.reversetutor.feature.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * v1 死循环干预底部引导卡（2026-09-28 拍板）：检测命中后从底部滑出，
 * 不打断对话。三个动作：教AI一句 / 换条路 / 看示例。
 * 「教AI一句」「换条路」把预置文案填进输入框由老师确认发送；
 * 「看示例」在卡内展开可直接照抄的最小操作步骤。
 */
enum class ChatGuidanceAction { APPLY_SCAFFOLD, APPLY_DETOUR }

private val GuidanceCardBorder = Color(0xFFE3E7F0)
private val GuidanceTextPrimary = Color(0xFF121722)
private val GuidanceTextMuted = Color(0xFF6D778C)
private val GuidanceActionBlue = Color(0xFF2F5DDF)

@Composable
internal fun ChatGuidanceCard(
    guidance: ChatGuidanceUiState,
    onAction: (ChatGuidanceAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var examplesExpanded by remember(guidance.signal.triggerMessageId) { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("guidance-card"),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GuidanceCardBorder)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "好像卡住了？",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GuidanceTextPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (guidance.signal.escalated) "已升级到更强的引导" else "",
                    fontSize = 11.sp,
                    color = GuidanceActionBlue
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("guidance-dismiss"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text(
                        text = "收起",
                        fontSize = 12.sp,
                        color = GuidanceTextMuted
                    )
                }
            }
            Text(
                text = guidance.reasonSummary + "。",
                fontSize = 12.sp,
                color = GuidanceTextMuted
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onAction(ChatGuidanceAction.APPLY_SCAFFOLD) },
                    modifier = Modifier.testTag("guidance-teach"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GuidanceActionBlue),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp
                    )
                ) {
                    Text("教AI一句", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { onAction(ChatGuidanceAction.APPLY_DETOUR) },
                    modifier = Modifier.testTag("guidance-detour"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 4.dp
                    )
                ) {
                    Text("换条路", fontSize = 13.sp, color = GuidanceActionBlue)
                }
                TextButton(
                    onClick = { examplesExpanded = !examplesExpanded },
                    modifier = Modifier.testTag("guidance-example"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text(
                        if (examplesExpanded) "收起示例" else "看示例",
                        fontSize = 13.sp,
                        color = GuidanceActionBlue
                    )
                }
            }
            AnimatedVisibility(visible = examplesExpanded) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "可以直接照抄的步骤：",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GuidanceTextPrimary
                    )
                    guidance.examples.forEach { step ->
                        Text(
                            text = step,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = GuidanceTextPrimary
                        )
                    }
                }
            }
        }
    }
}
