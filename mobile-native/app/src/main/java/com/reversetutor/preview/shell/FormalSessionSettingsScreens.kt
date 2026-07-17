package com.reversetutor.preview.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style
import com.reversetutor.feature.chat.R as ChatR

internal enum class FormalRoleGoalOverlay {
    None,
    HighImpactConfirmation,
    AutomaticEvolutionNotice
}

@Composable
internal fun FormalRoleGoalScreen(
    sessionTitle: String,
    onBack: () -> Unit,
    onSelectDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    initialOverlay: FormalRoleGoalOverlay = FormalRoleGoalOverlay.None
) {
    val type = LocalFormalTypeScale.current
    var overlay by rememberSaveable(initialOverlay) { mutableStateOf(initialOverlay) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FormalSessionBackground)
            .testTag("formal-role-goal-717-1895")
    ) {
        FormalSessionHeader(sessionTitle, onBack)
        FormalSessionTabs(AppDestination.SessionSettingsPersona, onSelectDestination)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
        ) {
            item {
                Text(
                    "当前会话的角色与目标",
                    style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink)
                )
                Text(
                    "这里设定学生角色如何与你互动，以及本次学习最终需要完成什么。",
                    style = type.style(10f, 17f, color = FormalColors.Muted)
                )
                Spacer(Modifier.height(20.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(ChatR.drawable.formal_preset_python_avatar),
                        contentDescription = "学生小P",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(78.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("小P", style = type.style(20f, 27f, FontWeight.Bold, FormalColors.Ink))
                        Text("好奇但谨慎的高中生", style = type.style(11f, 18f, color = FormalColors.Muted))
                        Text("正在学习 Python 基础", style = type.style(11f, 18f, color = FormalColors.Muted))
                    }
                    FormalEditButton("编辑学生角色") {
                        overlay = FormalRoleGoalOverlay.HighImpactConfirmation
                    }
                }
                Spacer(Modifier.height(16.dp))
                FormalDivider()
                RoleAttribute(Icons.Filled.PersonOutline, "核心性格：", "好奇、谨慎、愿意承认不理解")
                RoleAttribute(Icons.Filled.AutoStories, "当前基础：", "掌握基础变量，函数理解不稳定")
                RoleAttribute(Icons.Filled.ChatBubbleOutline, "互动习惯：", "先复述，再提出一个关键问题")
                RoleAttribute(Icons.AutoMirrored.Filled.ArrowBack, "回复策略：", "通俗由浅入深，优先满足用户举例")
                Spacer(Modifier.height(10.dp))
                FormalDivider()
                Spacer(Modifier.height(18.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "主目标",
                        modifier = Modifier.weight(1f),
                        style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink)
                    )
                    FormalEditButton("编辑主目标") {
                        overlay = FormalRoleGoalOverlay.HighImpactConfirmation
                    }
                }
                Text(
                    "独立完成一个 Python 基础项目",
                    style = type.style(18f, 27f, FontWeight.Bold, FormalColors.Ink)
                )
                Spacer(Modifier.height(16.dp))
                GoalAttribute("目标期限", "6 周")
                GoalAttribute("当前范围", "基础语法、函数、数据结构、文件操作")
                GoalAttribute("验收条件", "能够独立讲解并完成项目封装")
            }
        }
    }

    when (overlay) {
        FormalRoleGoalOverlay.HighImpactConfirmation -> HighImpactDialog(
            onDismiss = { overlay = FormalRoleGoalOverlay.None },
            onConfirm = { overlay = FormalRoleGoalOverlay.None }
        )
        FormalRoleGoalOverlay.AutomaticEvolutionNotice -> EvolutionNoticeDialog(
            onDismiss = { overlay = FormalRoleGoalOverlay.None }
        )
        FormalRoleGoalOverlay.None -> Unit
    }
}

@Composable
internal fun FormalPersonalizationScreen(
    sessionTitle: String,
    onBack: () -> Unit,
    onSelectDestination: (AppDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val type = LocalFormalTypeScale.current
    var notificationEnabled by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FormalSessionBackground)
            .testTag("formal-personalization-717-2140")
    ) {
        FormalSessionHeader(sessionTitle, onBack)
        FormalSessionTabs(AppDestination.SessionSettingsPersonalization, onSelectDestination)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
        ) {
            item {
                Text("当前会话的个性化", style = type.style(17f, 24f, FontWeight.Bold, FormalColors.Ink))
                Text(
                    "只调整这个会话的外观、称呼、布局和反馈方式。",
                    style = type.style(10f, 17f, color = FormalColors.Muted)
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFEBF3FD),
                    shape = RoundedCornerShape(FormalShapes.CardRadius),
                    border = BorderStroke(1.dp, FormalColors.Border)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(ChatR.drawable.formal_preset_python_avatar),
                                contentDescription = "学生小P",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(58.dp).clip(CircleShape)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sessionTitle, style = type.style(15f, 22f, FontWeight.Bold, FormalColors.Ink))
                                Text("小P · 正在整理你的知识线索", style = type.style(10f, 16f, color = FormalColors.Muted))
                            }
                            FormalEditButton("编辑会话信息") {}
                        }
                        Spacer(Modifier.height(10.dp))
                        Surface(color = FormalColors.Surface, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "你刚才的例子里，我还有一个地方没理解。",
                                modifier = Modifier.padding(14.dp),
                                style = type.style(11f, 18f, color = Color(0xFF3B4658))
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                SettingsSectionLabel("会话身份与外观")
                FormalSettingsGroup {
                    PersonalizationRow(Icons.Filled.PersonOutline, "学生称呼", "小P")
                    PersonalizationRow(Icons.Filled.ChatBubbleOutline, "对你的称呼", "老师")
                    PersonalizationRow(Icons.Filled.AutoStories, "会话主题", "雾蓝")
                }
                Spacer(Modifier.height(22.dp))
                SettingsSectionLabel("显示与反馈")
                FormalSettingsGroup {
                    PersonalizationRow(Icons.Filled.ViewAgenda, "布局大小", "标准")
                    Row(
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FormalMiniIcon(Icons.Filled.Notifications)
                        Spacer(Modifier.width(12.dp))
                        Text("单会话通知", modifier = Modifier.weight(1f), style = type.style(12f, 18f, color = FormalColors.Ink))
                        Switch(
                            checked = notificationEnabled,
                            onCheckedChange = { notificationEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FormalColors.Primary
                            )
                        )
                    }
                }
                Text(
                    "这些设置只作用于当前会话，不会改变角色设定与学习策略。",
                    modifier = Modifier.padding(top = 16.dp),
                    style = type.style(9f, 15f, color = FormalColors.Muted)
                )
            }
        }
    }
}

@Composable
private fun FormalSessionHeader(sessionTitle: String, onBack: () -> Unit) {
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFAFCFE),
        border = BorderStroke(1.dp, Color(0xFFE0E5ED))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(onClick = onBack, modifier = Modifier.size(44.dp), color = Color.Transparent, shape = CircleShape) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Color(0xFF435875), modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("会话设置", style = type.style(18f, 24f, FontWeight.Bold, FormalColors.Ink))
                Text(sessionTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = type.style(10f, 15f, color = FormalColors.Muted))
            }
            Text("设定已同步", modifier = Modifier.width(78.dp), style = type.style(10f, 15f, color = FormalColors.Muted))
        }
    }
}

@Composable
private fun FormalSessionTabs(current: AppDestination, onSelect: (AppDestination) -> Unit) {
    val type = LocalFormalTypeScale.current
    val tabs = listOf(
        AppDestination.SessionSettingsLibrary to "资料库",
        AppDestination.SessionSettingsGraph to "世界树",
        AppDestination.SessionSettingsPersona to "角色与目标",
        AppDestination.SessionSettingsPersonalization to "个性化"
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).background(Color(0xFFFAFCFE)),
        verticalAlignment = Alignment.Bottom
    ) {
        tabs.forEach { (destination, label) ->
            val selected = current == destination
            Surface(
                onClick = { onSelect(destination) },
                modifier = Modifier.weight(1f).height(56.dp),
                color = Color.Transparent
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        label,
                        maxLines = 1,
                        style = type.style(
                            12f,
                            18f,
                            if (selected) FontWeight.Bold else FontWeight.Normal,
                            if (selected) FormalColors.Primary else Color(0xFF414C5F)
                        )
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.width(if (selected) 48.dp else 0.dp).height(3.dp)
                            .background(FormalColors.Primary, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleAttribute(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color(0xFF5B7292), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = type.style(10f, 17f, FontWeight.Bold, Color(0xFF445168)))
        Text(value, style = type.style(10f, 17f, color = Color(0xFF566174)))
    }
}

@Composable
private fun GoalAttribute(label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp).background(Color(0xFFEAF2FF), CircleShape))
        Spacer(Modifier.width(12.dp))
        Text("$label：", style = type.style(10f, 17f, color = FormalColors.Muted))
        Text(value, style = type.style(10f, 17f, color = Color(0xFF4C586B)))
    }
}

@Composable
private fun FormalEditButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.size(40.dp), color = Color.Transparent, shape = CircleShape) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Edit, contentDescription = label, tint = Color(0xFF526984), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HighImpactDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val type = LocalFormalTypeScale.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这会改变当前会话的世界观", style = type.style(19f, 27f, FontWeight.Bold, FormalColors.Ink)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "你修改了角色核心性格、学习目标和学习周期。这些变化会影响后续对话方式、未来学习计划和长期指导点。",
                    style = type.style(11f, 18f, color = FormalColors.Muted)
                )
                Surface(color = Color(0xFFF4F7FC), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConfirmChangeRow("核心性格", "主动质疑")
                        ConfirmChangeRow("学习目标", "数据分析项目")
                        ConfirmChangeRow("学习周期", "10 周")
                    }
                }
                Text("将受到影响\n· 后续回复与追问方式\n· 世界树未来分支\n· 目标验收条件\n· 未来预计时间点", style = type.style(10f, 18f, color = Color(0xFF485467)))
                FormalDivider()
                Text("保持不变\n· 历史对话\n· 已完成知识节点\n· 资料与引用\n· 已产生的学习记录", style = type.style(10f, 18f, color = Color(0xFF485467)))
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) { Text("确认修改") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color(0xFFFAFCFE)
    )
}

@Composable
private fun EvolutionNoticeDialog(onDismiss: () -> Unit) {
    val type = LocalFormalTypeScale.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("互动方式已调整", style = type.style(18f, 25f, FontWeight.Bold, FormalColors.Ink)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("根据近期对话，学生角色会更主动地追问，并减少重复确认。", style = type.style(11f, 18f, color = FormalColors.Muted))
                ConfirmChangeRow("追问倾向", "适度提高")
                ConfirmChangeRow("重复确认", "减少")
                ConfirmChangeRow("核心角色与学习目标", "保持不变")
                FormalDivider()
                Text("此提示仅出现一次，关闭后不再重复显示。", style = type.style(9f, 15f, color = FormalColors.Muted))
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FormalColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) { Text("明白") }
        },
        shape = RoundedCornerShape(18.dp),
        containerColor = Color(0xFFFAFCFE)
    )
}

@Composable
private fun ConfirmChangeRow(label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = type.style(9f, 15f, color = FormalColors.Muted))
        Text(value, style = type.style(9f, 15f, FontWeight.Medium, Color(0xFF405068)))
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    val type = LocalFormalTypeScale.current
    Text(text, modifier = Modifier.padding(bottom = 8.dp), style = type.style(11f, 18f, FontWeight.Medium, Color(0xFF536178)))
}

@Composable
private fun FormalSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFFAFCFE),
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, FormalColors.Border)
    ) {
        Column(Modifier.padding(horizontal = 12.dp), content = content)
    }
}

@Composable
private fun PersonalizationRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        FormalMiniIcon(icon)
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = type.style(12f, 18f, color = FormalColors.Ink))
        Text(value, style = type.style(10f, 16f, color = FormalColors.Muted))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = FormalColors.Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FormalMiniIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.size(30.dp).shadow(4.dp, RoundedCornerShape(8.dp)),
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.background(Brush.verticalGradient(listOf(Color(0xFF74AAFA), Color(0xFF2766D3)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun FormalDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFDDE4ED)))
}

private val FormalSessionBackground = Color(0xFFF4F7FC)
