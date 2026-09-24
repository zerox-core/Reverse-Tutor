package com.reversetutor.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
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

// 2026-09-21 模型选择入口：联网搜索同款胶囊，放在输入框上方操作行，
// 点开是按型号家族分组的弹窗（用户拍板：不要顶栏长条，太丑）。
@Composable
internal fun ChatModelSelectorChip(
    profiles: List<LlmProfile>,
    onActivate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (profiles.isEmpty()) return
    val active = profiles.firstOrNull { it.enabled } ?: profiles.first()
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier.padding(start = 8.dp, top = 10.dp)) {
        Surface(
            color = Color(0xFFF6F8FD),
            contentColor = ChatMuted,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, Color(0xFFC7D8EA)),
            modifier = Modifier.clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = active.name,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = "选择模型",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            profiles.groupBy { chatModelGroupLabel(it) }.forEach { (family, items) ->
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

/**
 * 2026-09-24 拍板：模型切换窗按「上游」分组，不再按模型家族。
 * 优先级：渠道限定名（丽珠::model）的 :: 前缀 = 上游/渠道显示名；
 * 裸模型名则按 baseUrl 的 host 区分（不同上游同名模型自然分开）；
 * 两者都没有才退回模型家族。
 */
internal fun chatModelGroupLabel(profile: LlmProfile): String {
    val model = profile.model
    if (model.contains("::")) {
        val channel = model.substringBefore("::")
        if (channel.isNotBlank()) return channel
    }
    profile.baseUrl?.let { base ->
        val host = base
            .trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        if (host.isNotBlank()) return host
    }
    return chatModelFamily(model)
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
