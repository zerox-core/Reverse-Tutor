package com.reversetutor.feature.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.model.LlmProfile

private val ChatInk = Color(0xFF171C27)
private val ChatMuted = Color(0xFF6D778C)

@Composable
internal fun ChatModelSelectorBar(
    profiles: List<LlmProfile>,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (profiles.isEmpty()) return
    val active = profiles.firstOrNull { it.enabled } ?: profiles.first()
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .fillMaxWidth(),
            color = Color(0xFFF3F6FA),
            contentColor = ChatInk,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFDDE3ED))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "模型",
                    color = ChatMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${active.name} · ${active.model}",
                    color = ChatInk,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = "选择模型",
                    tint = Color(0xFF395575),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profiles.groupBy { chatModelFamily(it.model) }.forEach { (family, items) ->
                Text(
                    text = family,
                    color = ChatMuted,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                items.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = profile.name,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = if (profile.enabled) FontWeight.Bold else FontWeight.Normal
                                )
                                Text(
                                    text = profile.model,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    color = ChatMuted
                                )
                            }
                        },
                        trailingIcon = {
                            if (profile.enabled) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "当前使用",
                                    tint = Color(0xFF395575),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = {
                            expanded = false
                            if (!profile.enabled) onActivate(profile.id)
                        }
                    )
                }
            }
        }
    }
}

internal fun chatModelFamily(model: String): String {
    val lower = model.lowercase()
    return when {
        lower.contains("gemini") -> "Gemini"
        lower.contains("deepseek") -> "DeepSeek"
        lower.contains("qwen") -> "Qwen"
        lower.contains("glm") -> "GLM"
        lower.contains("kimi") || lower.contains("moonshot") -> "Kimi"
        lower.contains("gpt") || lower.contains("openai") -> "OpenAI"
        lower.contains("claude") -> "Claude"
        else -> lower.split("-").firstOrNull()?.replaceFirstChar { c -> c.uppercase() } ?: "其他"
    }
}
