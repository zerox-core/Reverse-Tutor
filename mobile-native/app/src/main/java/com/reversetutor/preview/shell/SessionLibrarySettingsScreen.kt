package com.reversetutor.preview.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.preview.ui.RtIcon
import com.reversetutor.preview.ui.RtIconKey

@Composable
internal fun SessionLibrarySettingsScreen(
    sessionTitle: String,
    onBack: () -> Unit,
    onSelectDestination: (AppDestination) -> Unit,
    onPickSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(sessionTitle) { mutableStateOf(SessionLibrarySettingsState.reference()) }
    var renameSource by remember { mutableStateOf<SessionLibrarySource?>(null) }
    var removeSource by remember { mutableStateOf<SessionLibrarySource?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LibraryPageBackground)
    ) {
        LibrarySettingsHeader(
            sessionTitle = sessionTitle,
            onBack = onBack
        )
        LibrarySettingsTabs(onSelectDestination)
        LibraryIntroPanel(state.sources.size)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 30.dp)
        ) {
            item {
                AddSourceRow(onClick = onPickSource)
                Spacer(Modifier.height(10.dp))
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, LibraryDividerColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFF3F7FD), Color(0xFFEFF4FC))
                                )
                            )
                    ) {
                        state.sources.forEachIndexed { index, source ->
                            SessionLibrarySourceRow(
                                source = source,
                                onEnabledChange = { enabled ->
                                    state = state.toggle(source.id, enabled)
                                },
                                onRetry = {
                                    state = state.retry(source.id)
                                },
                                onRename = { renameSource = source },
                                onRemove = { removeSource = source }
                            )
                            if (index != state.sources.lastIndex) {
                                LibraryDivider()
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(22.dp))
                EnabledSourcesSummary(state.enabledSummary)
            }
        }
    }

    renameSource?.let { source ->
        RenameSourceDialog(
            source = source,
            onDismiss = { renameSource = null },
            onConfirm = { title ->
                state = state.copy(
                    sources = state.sources.map {
                        if (it.id == source.id) it.copy(title = title) else it
                    }
                )
                renameSource = null
            }
        )
    }
    removeSource?.let { source ->
        AlertDialog(
            onDismissRequest = { removeSource = null },
            title = { Text("移除资料") },
            text = { Text("从当前会话移除“${source.title}”？原始文件不会被删除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        state = state.copy(sources = state.sources.filterNot { it.id == source.id })
                        removeSource = null
                    }
                ) {
                    Text("移除", color = Color(0xFFB34F59))
                }
            },
            dismissButton = {
                TextButton(onClick = { removeSource = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun LibrarySettingsHeader(
    sessionTitle: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF7FAFE),
        contentColor = LibraryInk,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, Color(0xFFE4EAF3))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(74.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(7.dp, CircleShape, ambientColor = Color(0x18000000), spotColor = Color(0x18000000)),
                color = Color(0xFFFCFDFE),
                contentColor = LibraryInk,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFDCE1E9))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RtIcon(RtIconKey.ArrowBack, size = 22.dp)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "会话设置",
                    color = LibraryInk,
                    fontSize = 18.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = sessionTitle,
                    color = LibraryMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "已自动保存",
                modifier = Modifier.width(72.dp),
                color = LibraryMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LibrarySettingsTabs(onSelectDestination: (AppDestination) -> Unit) {
    val tabs = listOf(
        AppDestination.SessionSettingsLibrary to "资料库",
        AppDestination.SessionSettingsGraph to "世界树",
        AppDestination.SessionSettingsPersona to "角色与目标",
        AppDestination.SessionSettingsPersonalization to "个性化"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color(0xFFF6F9FE))
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        tabs.forEachIndexed { index, (destination, label) ->
            Surface(
                onClick = { onSelectDestination(destination) },
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp),
                color = Color.Transparent,
                contentColor = if (index == 0) Color(0xFF0F59C7) else Color(0xFF404759)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    LibraryDivider(Color(0xFFE8EDF4))
}

@Composable
private fun LibraryIntroPanel(sourceCount: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF6F9FE)
    ) {
        Column(
            modifier = Modifier.padding(
                start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp
            )
        ) {
            Text(
                text = "当前会话已连接 $sourceCount 份资料",
                color = LibraryInk,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "PDF、课堂笔记、错题图片和代码文件",
                color = LibraryMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AddSourceRow(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color(0xFFFAFCFE),
        contentColor = Color(0xFF084CB0),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE4ED))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(8.dp, RoundedCornerShape(11.dp), ambientColor = Color(0x35306FD3), spotColor = Color(0x35306FD3)),
                color = Color(0xFF62A9FF),
                contentColor = Color.White,
                shape = RoundedCornerShape(11.dp),
                border = BorderStroke(1.dp, Color(0xFF3E8AF2))
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.verticalGradient(listOf(Color(0xFF7DB9FF), Color(0xFF3E8AF2)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    RtIcon(RtIconKey.UploadFile, tint = Color.White, size = 24.dp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Text("添加资料", modifier = Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
            RtIcon(RtIconKey.ArrowForward, tint = LibraryMuted, size = 20.dp)
        }
    }
}

@Composable
private fun SessionLibrarySourceRow(
    source: SessionLibrarySource,
    onEnabledChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember(source.id) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SourceTypeIcon(source.type)
        Spacer(Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.title,
                color = LibraryInk,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            SourceStatusLine(source, onRetry)
        }
        Switch(
            checked = source.enabled,
            onCheckedChange = onEnabledChange,
            enabled = source.status !is SessionSourceStatus.ReadFailed,
            modifier = Modifier.scale(0.78f),
            colors = SwitchDefaults.colors(
                checkedTrackColor = Color(0xFF55A1FF),
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFD9DEE7),
                uncheckedThumbColor = Color.White
            )
        )
        Box {
            Surface(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(42.dp),
                color = Color(0xFFE9ECF2),
                contentColor = Color(0xFF3E485B),
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RtIcon(RtIconKey.More, size = 20.dp)
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("重命名") },
                    onClick = {
                        menuExpanded = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("移除资料", color = Color(0xFFB34F59)) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
            }
        }
    }
}

@Composable
private fun SourceTypeIcon(type: SessionLibrarySourceType) {
    val (background, foreground) = when (type) {
        SessionLibrarySourceType.Pdf -> Color(0xFFFF6D70) to Color.White
        SessionLibrarySourceType.Note -> Color(0xFF67E6BC) to Color.White
        SessionLibrarySourceType.Image -> Color(0xFF8EA5FF) to Color.White
        SessionLibrarySourceType.Python -> Color(0xFFFFFFFF) to Color(0xFF4E9DD8)
        SessionLibrarySourceType.Markdown -> Color(0xFFF2F5F8) to Color(0xFF374151)
    }
    Surface(
        modifier = Modifier
            .size(44.dp)
            .shadow(7.dp, RoundedCornerShape(10.dp), ambientColor = foreground.copy(alpha = 0.18f), spotColor = foreground.copy(alpha = 0.18f)),
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.08f))
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))
            ),
            contentAlignment = Alignment.Center
        ) {
            when (type) {
                SessionLibrarySourceType.Pdf -> Text("PDF", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                SessionLibrarySourceType.Note -> RtIcon(RtIconKey.Edit, tint = foreground, size = 22.dp)
                SessionLibrarySourceType.Image -> RtIcon(RtIconKey.Image, tint = foreground, size = 22.dp)
                SessionLibrarySourceType.Python -> Box {
                    Text("Py", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(5.dp)
                            .background(Color(0xFFFFE069), CircleShape)
                    )
                }
                SessionLibrarySourceType.Markdown -> Text("M↓", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SourceStatusLine(source: SessionLibrarySource, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(source.detail, color = LibraryMuted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.width(9.dp))
        when (val status = source.status) {
            SessionSourceStatus.Active -> {
                Box(Modifier.size(7.dp).background(Color(0xFF1FBD5C), CircleShape))
                Spacer(Modifier.width(6.dp))
                Text("参与当前会话", color = LibraryMuted, fontSize = 11.sp)
            }
            is SessionSourceStatus.Importing -> {
                CircularProgressIndicator(
                    progress = { status.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.size(14.dp),
                    color = Color(0xFF55A1FF),
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(6.dp))
                Text("正在导入 ${status.progress}%", color = LibraryMuted, fontSize = 11.sp)
            }
            SessionSourceStatus.ReadFailed -> {
                Text("▲", color = Color(0xFFF59A18), fontSize = 11.sp)
                Spacer(Modifier.width(5.dp))
                Text("读取失败 · ", color = Color(0xFF8F6633), fontSize = 11.sp)
                TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                    Text("重试", color = Color(0xFF0F63D1), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun EnabledSourcesSummary(summary: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF1F6FC),
        contentColor = LibraryInk,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFDCE4ED))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(Color(0xFF1FBD5C), CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(summary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("已自动保存", color = LibraryMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun RenameSourceDialog(
    source: SessionLibrarySource,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var title by remember(source.id) { mutableStateOf(source.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名资料") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onConfirm(title.trim()) }
            ) {
                Text("完成")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LibraryDivider(color: Color = LibraryDividerColor) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

private val LibraryPageBackground = Color(0xFFEDF2F9)
private val LibraryInk = Color(0xFF0D121C)
private val LibraryMuted = Color(0xFF6E7585)
private val LibraryDividerColor = Color(0xFFDCE4ED)
