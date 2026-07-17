package com.reversetutor.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Immutable
data class FormalImportExportUiState(
    val localSessionCount: Int,
    val localMessageCount: Int,
    val localMaterialCount: Int,
    val localKnowledgeNodeCount: Int,
    val recentRecords: List<FormalTransferRecord>
)

@Immutable
data class FormalTransferRecord(
    val id: String,
    val title: String,
    val subtitle: String,
    val status: FormalTransferStatus
)

enum class FormalTransferStatus { Completed, Warning }

@Composable
fun FormalImportExportScreen(
    state: FormalImportExportUiState,
    onBack: () -> Unit,
    onChooseImportFile: () -> Unit,
    onSelectSessions: () -> Unit,
    onExportAllLocalData: () -> Unit,
    onExportGlobalGraph: () -> Unit,
    onOpenRecord: (String) -> Unit,
    onOpenAllRecords: () -> Unit,
    modifier: Modifier = Modifier
) {
    FormalBatch6Page("导入与导出", "数据管理", onBack, modifier) { colors ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 28.dp)
        ) {
            item {
                ImportBackupHero(onChooseImportFile)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("导出")
                Spacer(Modifier.height(10.dp))
                FormalOutlinedCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 2.dp)) {
                    Column {
                        TransferActionRow(
                            title = "选择会话",
                            subtitle = "单选或多选会话 · 选中后导出",
                            icon = Icons.Rounded.ChatBubbleOutline,
                            color = Color(0xFF5B92D0),
                            onClick = onSelectSessions
                        )
                        DividerInset()
                        TransferActionRow(
                            title = "全部本地数据",
                            subtitle = "${state.localSessionCount} 个会话 · ${state.localMessageCount} 条消息 · ${state.localMaterialCount} 份资料",
                            icon = Icons.Rounded.Storage,
                            color = Color(0xFF44A17F),
                            onClick = onExportAllLocalData,
                            upload = true
                        )
                        DividerInset()
                        TransferActionRow(
                            title = "全局知识图谱",
                            subtitle = "${state.localKnowledgeNodeCount} 个节点 · JSON 与预览图",
                            icon = Icons.Rounded.Share,
                            color = Color(0xFFB7802E),
                            onClick = onExportGlobalGraph,
                            upload = true
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                FormalInfoBanner(
                    title = "API Key 与密钥引用不会进入导出文件",
                    body = "导入模型配置后需要重新填写 Key"
                )
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("最近记录")
                Spacer(Modifier.height(8.dp))
            }
            items(state.recentRecords, key = { it.id }) { record ->
                TransferRecordRow(record, onOpenRecord)
            }
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "查看全部记录", onClick = onOpenAllRecords).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("查看全部记录", color = colors.primary, style = LocalFormalTypeScale.current.style(9f, 14f))
                    Spacer(Modifier.width(28.dp))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ImportBackupHero(onClick: () -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(103.dp).clickable(role = Role.Button, onClickLabel = "导入备份", onClick = onClick),
        color = Color(0xFFE8F2FF),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF83B3E9))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(Icons.Rounded.Download, null, Color(0xFF438AD8), size = 48.dp, glyphSize = 24.dp)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("导入备份", color = colors.ink, style = type.style(17f, 24f, FontWeight.Medium))
                Text("选择备份文件后预览内容、冲突与兼容性", color = colors.faint, style = type.style(8f, 13f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun TransferActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    upload: Boolean = false
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth().height(65.dp).clickable(role = Role.Button, onClickLabel = title, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormalGlossySquare(icon, null, color, size = 34.dp, glyphSize = 16.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
            Text(subtitle, color = colors.faint, style = type.style(8f, 13f))
        }
        Icon(
            if (upload) Icons.Rounded.Upload else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = colors.muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DividerInset(start: androidx.compose.ui.unit.Dp = 48.dp) {
    val colors = formalBatch6Colors()
    Box(Modifier.fillMaxWidth().padding(start = start).height(1.dp).background(colors.divider))
}

@Composable
private fun TransferRecordRow(record: FormalTransferRecord, onClick: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val statusColor = if (record.status == FormalTransferStatus.Completed) colors.success else colors.warning
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClickLabel = record.title) { onClick(record.id) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormalGlossySquare(
            if (record.status == FormalTransferStatus.Completed) Icons.Rounded.Check else Icons.Rounded.ErrorOutline,
            null,
            statusColor,
            size = 30.dp,
            glyphSize = 15.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(record.title, color = colors.ink, style = type.style(10f, 16f, FontWeight.Medium))
            Text(record.subtitle, color = colors.faint, style = type.style(8f, 13f))
        }
        Text(
            if (record.status == FormalTransferStatus.Completed) "完成" else "警告",
            color = statusColor,
            style = type.style(8f, 13f)
        )
    }
    DividerInset(42.dp)
}

enum class FormalImportMode { Append, Overwrite, NewSpace }

@Immutable
data class FormalImportIssue(
    val id: String,
    val title: String,
    val subtitle: String,
    val severity: FormalImportIssueSeverity
)

enum class FormalImportIssueSeverity { Warning, Blocking, Secret }

@Immutable
data class FormalImportPreviewUiState(
    val fileName: String,
    val fileMeta: String,
    val isValidated: Boolean,
    val sessionCount: Int,
    val messageCount: Int,
    val materialCount: Int,
    val planCount: Int,
    val issues: List<FormalImportIssue>,
    val selectedMode: FormalImportMode,
    val targetSpaceLabel: String,
    val estimatedSizeLabel: String,
    val summaryLabel: String,
    val canImport: Boolean = true
)

@Composable
fun FormalImportPreviewScreen(
    state: FormalImportPreviewUiState,
    onBack: () -> Unit,
    onModeSelected: (FormalImportMode) -> Unit,
    onSelectTargetSpace: () -> Unit,
    onCancel: () -> Unit,
    onStartImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    FormalBatch6Page(
        title = "导入预览",
        subtitle = "确认内容与冲突",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            ImportBottomBar(state, onCancel, onStartImport)
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 18.dp)
        ) {
            item {
                ImportFileCard(state)
                Spacer(Modifier.height(16.dp))
                ImportCountsCard(state)
                Spacer(Modifier.height(18.dp))
                FormalSectionLabel("需要确认", trailing = "${state.issues.size} 项")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 2.dp)) {
                    Column {
                        state.issues.forEachIndexed { index, issue ->
                            ImportIssueRow(issue)
                            if (index != state.issues.lastIndex) DividerInset()
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                FormalSectionLabel("导入方式")
                Spacer(Modifier.height(8.dp))
                ImportModeSelector(state.selectedMode, onModeSelected)
                Spacer(Modifier.height(12.dp))
                ImportModeDescription(state.selectedMode)
                Spacer(Modifier.height(12.dp))
                FormalOutlinedCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 2.dp)) {
                    Column {
                        SimpleValueRow("目标空间", state.targetSpaceLabel, Icons.Rounded.FolderOpen, onSelectTargetSpace)
                        DividerInset()
                        SimpleValueRow("预计新增占用", state.estimatedSizeLabel, Icons.Rounded.Storage, null)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportFileCard(state: FormalImportPreviewUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(Icons.Rounded.Description, null, Color(0xFF659AD2), size = 48.dp, glyphSize = 23.dp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(state.fileName, color = colors.ink, style = type.style(10f, 16f, FontWeight.Medium))
                Text(state.fileMeta, color = colors.faint, style = type.style(8f, 13f))
                if (state.isValidated) {
                    Text(
                        "校验通过",
                        color = colors.success,
                        style = type.style(8f, 13f),
                        modifier = Modifier.background(colors.successSoft, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCountsCard(state: FormalImportPreviewUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf(
                state.sessionCount to "会话",
                state.messageCount to "消息",
                state.materialCount to "资料",
                state.planCount to "计划"
            ).forEachIndexed { index, value ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value.first.toString(), color = colors.ink, style = type.style(18f, 24f, FontWeight.Medium))
                    Text(value.second, color = colors.faint, style = type.style(8f, 13f))
                }
                if (index != 3) Box(Modifier.width(1.dp).height(49.dp).background(colors.divider))
            }
        }
    }
}

@Composable
private fun ImportIssueRow(issue: FormalImportIssue) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val color = when (issue.severity) {
        FormalImportIssueSeverity.Warning -> colors.warning
        FormalImportIssueSeverity.Blocking -> Color(0xFFE8794A)
        FormalImportIssueSeverity.Secret -> Color(0xFF5B87C4)
    }
    val icon = when (issue.severity) {
        FormalImportIssueSeverity.Warning -> Icons.Rounded.ContentCopy
        FormalImportIssueSeverity.Blocking -> Icons.Rounded.Description
        FormalImportIssueSeverity.Secret -> Icons.Rounded.Key
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        FormalGlossySquare(icon, null, color, size = 28.dp, glyphSize = 14.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(issue.title, color = colors.ink, style = type.style(10f, 15f, FontWeight.Medium))
            Text(issue.subtitle, color = colors.faint, style = type.style(8f, 12f))
        }
    }
}

@Composable
private fun ImportModeSelector(selected: FormalImportMode, onSelected: (FormalImportMode) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).background(Color(0xFFE5EDF8), RoundedCornerShape(8.dp)).padding(3.dp)
    ) {
        FormalImportMode.values().forEach { mode ->
            Box(
                modifier = Modifier.weight(1f).fillMaxSize().background(
                    if (selected == mode) colors.surface else Color.Transparent,
                    RoundedCornerShape(6.dp)
                ).clickable(role = Role.Tab, onClickLabel = mode.label) { onSelected(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(mode.label, color = if (selected == mode) colors.ink else colors.faint, style = type.style(9f, 14f, if (selected == mode) FontWeight.Medium else FontWeight.Normal))
            }
        }
    }
}

@Composable
private fun ImportModeDescription(mode: FormalImportMode) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val lines = when (mode) {
        FormalImportMode.Append -> listOf("保留现有数据，不覆盖原会话", "同名会话会复制并标记‘导入’", "跳过不兼容附件，完成后给出清单")
        FormalImportMode.Overwrite -> listOf("替换当前空间内的同类记录", "导入前需要再次确认", "建议先导出一份完整备份")
        FormalImportMode.NewSpace -> listOf("新建独立空间并写入数据", "当前空间保持不变", "导入后可在空间列表切换")
    }
    Column(Modifier.fillMaxWidth().background(colors.primarySoft, RoundedCornerShape(8.dp)).padding(12.dp)) {
        Text("${mode.label}导入", color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
        lines.forEach { Text("•  $it", color = colors.muted, style = type.style(8f, 13f)) }
    }
}

private val FormalImportMode.label: String
    get() = when (this) {
        FormalImportMode.Append -> "追加"
        FormalImportMode.Overwrite -> "覆盖"
        FormalImportMode.NewSpace -> "新空间"
    }

@Composable
private fun SimpleValueRow(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)?
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val click = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
    Row(Modifier.fillMaxWidth().height(47.dp).then(click).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FormalGlossySquare(icon, null, Color(0xFF6B95BF), size = 25.dp, glyphSize = 13.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, color = colors.muted, style = type.style(9f, 14f), modifier = Modifier.weight(1f))
        Text(value, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ImportBottomBar(state: FormalImportPreviewUiState, onCancel: () -> Unit, onStartImport: () -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.summaryLabel, color = colors.muted, style = type.style(8f, 13f))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormalSecondaryButton("取消", onCancel, Modifier.weight(1f))
            FormalPrimaryButton("开始导入", onStartImport, Modifier.weight(2f), state.canImport, icon = Icons.Rounded.Download)
        }
    }
}

@Immutable
data class FormalExportSessionItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val selected: Boolean,
    val color: Color
)

@Immutable
data class FormalSessionExportUiState(
    val searchQuery: String,
    val totalCount: Int,
    val selectedCount: Int,
    val selectedSizeLabel: String,
    val sessions: List<FormalExportSessionItem>,
    val canExport: Boolean = selectedCount > 0
)

@Composable
fun FormalSessionExportSelectionScreen(
    state: FormalSessionExportUiState,
    onBack: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSelectAll: () -> Unit,
    onToggleSession: (String) -> Unit,
    onCancel: () -> Unit,
    onExportSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalBatch6Page(
        title = "选择会话",
        subtitle = "选择需要导出的会话",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已选择 ${state.selectedCount} 个会话 · ${state.selectedSizeLabel}", color = colors.muted, style = type.style(8f, 13f))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormalSecondaryButton("取消", onCancel, Modifier.weight(1f))
                    FormalPrimaryButton("导出 ${state.selectedCount} 个会话", onExportSelected, Modifier.weight(2f), state.canExport)
                }
            }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp)) {
            item {
                FormalOutlinedCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FormalGlossySquare(Icons.Rounded.ChatBubbleOutline, null, Color(0xFF5B92D0), size = 42.dp, glyphSize = 19.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("已选择 ${state.selectedCount} 个会话", color = colors.ink, style = type.style(12f, 18f, FontWeight.Medium))
                            Text("${state.selectedSizeLabel} · 可继续添加", color = colors.muted, style = type.style(9f, 14f))
                        }
                        Text("全选", color = colors.primary, style = type.style(12f, 18f), modifier = Modifier.clickable(onClickLabel = "全选", onClick = onToggleSelectAll))
                    }
                }
                Spacer(Modifier.height(16.dp))
                SessionSearchField(state.searchQuery, onSearchQueryChange)
                Spacer(Modifier.height(16.dp))
                FormalSectionLabel("会话列表", trailing = "${state.totalCount} 个")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp)) {
                    Column {
                        state.sessions.forEachIndexed { index, session ->
                            ExportSessionRow(session, onToggleSession)
                            if (index != state.sessions.lastIndex) DividerInset(0.dp)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                FormalInfoBanner("导出会保留消息、会话资料与引用关系", "不包含 API Key，也不会全局知识图谱")
            }
        }
    }
}

@Composable
private fun SessionSearchField(value: String, onChange: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Surface(Modifier.fillMaxWidth().height(40.dp), color = colors.surface, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, colors.border)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = colors.muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = type.style(9f, 14f, color = colors.ink),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isBlank()) Text("搜索会话", color = colors.faint, style = type.style(9f, 14f))
                        inner()
                    }
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExportSessionRow(item: FormalExportSessionItem, onToggle: (String) -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        modifier = Modifier.fillMaxWidth().height(61.dp).clickable(role = Role.Checkbox, onClickLabel = item.title) { onToggle(item.id) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(23.dp).background(if (item.selected) Color(0xFF5F9BD4) else colors.surface, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Transparent, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, colors.border)) {
                if (item.selected) Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(15.dp)) }
            }
        }
        Spacer(Modifier.width(9.dp))
        FormalGlossySquare(Icons.Rounded.ChatBubbleOutline, null, item.color, size = 30.dp, glyphSize = 14.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(item.title, color = colors.ink, style = type.style(10f, 16f, FontWeight.Medium))
            Text(item.subtitle, color = colors.faint, style = type.style(8f, 13f))
        }
    }
}
