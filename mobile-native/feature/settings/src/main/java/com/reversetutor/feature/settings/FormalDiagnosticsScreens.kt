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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Immutable
data class FormalDiagnosticsUiState(
    val appVersionLabel: String,
    val deviceLabel: String,
    val currentSpaceLabel: String,
    val localUsageLabel: String,
    val modelLabel: String,
    val modelStatusLabel: String,
    val systemRows: List<FormalDiagnosticStatusRow>,
    val storageUsedLabel: String,
    val storageSegments: List<FormalStorageSegment>,
    val showWipeConfirmation: Boolean = false,
    val wipeSummary: String
)

@Immutable
data class FormalDiagnosticStatusRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val tone: FormalDiagnosticTone,
    val icon: FormalDiagnosticIcon
)

enum class FormalDiagnosticTone { Success, Info, Warning }
enum class FormalDiagnosticIcon { Database, Parser, Model, Warning }

@Immutable
data class FormalStorageSegment(
    val label: String,
    val valueLabel: String,
    val fraction: Float,
    val color: Color
)

@Composable
fun FormalDiagnosticsOverviewScreen(
    state: FormalDiagnosticsUiState,
    onBack: () -> Unit,
    onCheckNow: () -> Unit,
    onClearCache: () -> Unit,
    onGenerateReport: () -> Unit,
    onRequestWipe: () -> Unit,
    onDismissWipe: () -> Unit,
    onExportBeforeWipe: () -> Unit,
    onConfirmWipe: () -> Unit,
    modifier: Modifier = Modifier
) {
    FormalBatch6Page("关于与诊断", "版本、存储与运行状态", onBack, modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp)
        ) {
            item {
                DiagnosticsAppCard(state)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("运行概览")
                Spacer(Modifier.height(8.dp))
                DiagnosticsOverviewCard(state)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel(
                    "系统状态",
                    modifier = Modifier.clickable(role = Role.Button, onClickLabel = "立即检查", onClick = onCheckNow),
                    trailing = "刚刚检查"
                )
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 2.dp)) {
                    Column {
                        state.systemRows.forEachIndexed { index, row ->
                            DiagnosticsStatusRow(row)
                            if (index != state.systemRows.lastIndex) DiagnosticsDivider()
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("本地存储")
                Spacer(Modifier.height(8.dp))
                DiagnosticsStorageCard(state, onClearCache)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("诊断与数据")
                Spacer(Modifier.height(8.dp))
                DiagnosticsActionCard(
                    title = "生成诊断报告",
                    subtitle = "仅包含脱敏运行信息",
                    icon = Icons.Rounded.Description,
                    color = Color(0xFF5B92D0),
                    danger = false,
                    onClick = onGenerateReport
                )
                Spacer(Modifier.height(10.dp))
                DiagnosticsActionCard(
                    title = "清空全部本地数据",
                    subtitle = "不可撤销",
                    icon = Icons.Rounded.DeleteOutline,
                    color = Color(0xFFD55A5C),
                    danger = true,
                    onClick = onRequestWipe
                )
            }
        }
    }
    if (state.showWipeConfirmation) {
        FormalWipeConfirmationDialog(
            summary = state.wipeSummary,
            onDismiss = onDismissWipe,
            onExportFirst = onExportBeforeWipe,
            onConfirm = onConfirmWipe
        )
    }
}

@Composable
private fun DiagnosticsAppCard(state: FormalDiagnosticsUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(Icons.Rounded.Share, null, Color(0xFF4F89C9), size = 52.dp, glyphSize = 24.dp)
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("反转家教", color = colors.ink, style = type.style(17f, 23f, FontWeight.SemiBold))
                Text(state.appVersionLabel, color = colors.faint, style = type.style(8f, 13f))
                Text(
                    "本地优先",
                    color = colors.success,
                    style = type.style(8f, 13f, FontWeight.Medium),
                    modifier = Modifier.background(colors.successSoft, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsOverviewCard(state: FormalDiagnosticsUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf(
                state.currentSpaceLabel to "当前空间",
                state.localUsageLabel to "本地占用",
                state.modelStatusLabel to "默认模型"
            ).forEachIndexed { index, value ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(value.first, color = if (index == 2) colors.success else colors.ink, style = type.style(13f, 20f, FontWeight.SemiBold))
                    Text(value.second, color = colors.faint, style = type.style(8f, 13f))
                }
                if (index != 2) Box(Modifier.width(1.dp).height(52.dp).background(colors.divider))
            }
        }
    }
}

@Composable
private fun DiagnosticsStatusRow(row: FormalDiagnosticStatusRow) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val color = when (row.tone) {
        FormalDiagnosticTone.Success -> colors.success
        FormalDiagnosticTone.Info -> Color(0xFF4D83BF)
        FormalDiagnosticTone.Warning -> colors.warning
    }
    Row(Modifier.fillMaxWidth().height(47.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FormalGlossySquare(row.icon.vector, null, row.icon.color, size = 28.dp, glyphSize = 14.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
            Text(row.subtitle, color = colors.faint, style = type.style(7.5f, 12f))
        }
        Text(row.statusLabel, color = color, style = type.style(8f, 13f, FontWeight.Medium))
    }
}

private val FormalDiagnosticIcon.vector: ImageVector
    get() = when (this) {
        FormalDiagnosticIcon.Database -> Icons.Rounded.Storage
        FormalDiagnosticIcon.Parser -> Icons.Rounded.Description
        FormalDiagnosticIcon.Model -> Icons.Rounded.CloudDone
        FormalDiagnosticIcon.Warning -> Icons.Rounded.WarningAmber
    }

private val FormalDiagnosticIcon.color: Color
    get() = when (this) {
        FormalDiagnosticIcon.Database -> Color(0xFF43A17D)
        FormalDiagnosticIcon.Parser -> Color(0xFF5A91CD)
        FormalDiagnosticIcon.Model -> Color(0xFF557DB7)
        FormalDiagnosticIcon.Warning -> Color(0xFFD0912E)
    }

@Composable
private fun DiagnosticsStorageCard(state: FormalDiagnosticsUiState, onClearCache: () -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("应用数据 ${state.storageUsedLabel}", color = colors.ink, style = type.style(10f, 15f, FontWeight.Medium), modifier = Modifier.weight(1f))
                Text("清理缓存", color = colors.primary, style = type.style(8f, 13f), modifier = Modifier.clickable(onClickLabel = "清理缓存", onClick = onClearCache))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(8.dp).background(colors.divider, RoundedCornerShape(4.dp))) {
                state.storageSegments.forEach { segment ->
                    Box(Modifier.weight(segment.fraction.coerceAtLeast(0.01f)).fillMaxSize().background(segment.color))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.storageSegments.forEach { segment ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(segment.color, RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(4.dp))
                        Text("${segment.label} ${segment.valueLabel}", color = colors.faint, style = type.style(7f, 11f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("密钥存储不计入此处，也不会写入诊断报告", color = colors.faint, style = type.style(7.5f, 12f))
        }
    }
}

@Composable
private fun DiagnosticsActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    danger: Boolean,
    onClick: () -> Unit
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(57.dp).clickable(role = Role.Button, onClickLabel = title, onClick = onClick),
        color = if (danger) colors.dangerSoft else colors.surface,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (danger) Color(0xFFF0B8B8) else colors.border)
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(icon, null, color, size = 35.dp, glyphSize = 17.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = if (danger) colors.danger else colors.ink, style = type.style(10f, 15f, FontWeight.Medium))
                Text(subtitle, color = if (danger) colors.danger.copy(alpha = .72f) else colors.faint, style = type.style(8f, 13f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = if (danger) colors.danger else colors.muted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun FormalWipeConfirmationDialog(
    summary: String,
    onDismiss: () -> Unit,
    onExportFirst: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            color = colors.surface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 12.dp
        ) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                FormalGlossySquare(Icons.Rounded.DeleteOutline, null, Color(0xFFD65D5D), size = 48.dp, glyphSize = 23.dp)
                Spacer(Modifier.height(16.dp))
                Text("清空全部本地数据", color = colors.ink, style = type.style(17f, 24f, FontWeight.Medium))
                Spacer(Modifier.height(10.dp))
                Text(summary, color = colors.muted, style = type.style(9f, 16f))
                Spacer(Modifier.height(20.dp))
                Text("此操作无法撤销。", color = colors.danger, style = type.style(9f, 14f, FontWeight.Medium))
                Spacer(Modifier.height(10.dp))
                Text("先导出备份 ›", color = colors.primary, style = type.style(9f, 14f), modifier = Modifier.clickable(onClickLabel = "先导出备份", onClick = onExportFirst))
                Spacer(Modifier.height(16.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Spacer(Modifier.height(13.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormalSecondaryButton("取消", onDismiss, Modifier.weight(1f))
                    FormalPrimaryButton("清空数据", onConfirm, Modifier.weight(1f), danger = true)
                }
            }
        }
    }
}

@Immutable
data class FormalDiagnosticReportUiState(
    val reportId: String,
    val createdAtLabel: String,
    val appVersionLabel: String,
    val deviceLabel: String,
    val spaceLabel: String,
    val databaseLabel: String,
    val localDataLabel: String,
    val parserLabel: String,
    val recentEvents: List<FormalDiagnosticEvent>
)

@Immutable
data class FormalDiagnosticEvent(
    val id: String,
    val title: String,
    val subtitle: String,
    val statusLabel: String,
    val warning: Boolean
)

@Composable
fun FormalDiagnosticReportScreen(
    state: FormalDiagnosticReportUiState,
    onBack: () -> Unit,
    onRegenerate: () -> Unit,
    onCopySummary: () -> Unit,
    onExportReport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = formalBatch6Colors()
    FormalBatch6Page(
        title = "诊断报告",
        subtitle = "已脱敏 · 仅含运行信息",
        onBack = onBack,
        modifier = modifier,
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormalSecondaryButton("复制摘要", onCopySummary, Modifier.weight(1f))
                FormalPrimaryButton("导出报告", onExportReport, Modifier.weight(1.35f), icon = Icons.Rounded.Download)
            }
        }
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp)) {
            item {
                DiagnosticReportHero(state, onRegenerate)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("设备与应用")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 3.dp)) {
                    Column {
                        ReportValueRow("应用版本", state.appVersionLabel, Icons.Rounded.Android)
                        DiagnosticsDivider()
                        ReportValueRow("设备系统", state.deviceLabel, Icons.Rounded.PhoneAndroid)
                        DiagnosticsDivider()
                        ReportValueRow("当前数据空间", state.spaceLabel, Icons.Rounded.FolderOpen)
                    }
                }
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("数据与能力")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 3.dp)) {
                    Column {
                        ReportValueRow("数据库", state.databaseLabel, Icons.Rounded.Storage, true)
                        DiagnosticsDivider()
                        ReportValueRow("本地数据", state.localDataLabel, Icons.Rounded.FolderOpen)
                        DiagnosticsDivider()
                        ReportValueRow("资料解析", state.parserLabel, Icons.Rounded.Description)
                    }
                }
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("最近事件")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 10.dp)) {
                    Column {
                        state.recentEvents.forEachIndexed { index, event ->
                            DiagnosticEventRow(event)
                            if (index != state.recentEvents.lastIndex) DiagnosticsDivider()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                FormalInfoBanner("不包含 API Key、完整请求或用户隐私正文", "导出前仍可再次检查报告内容")
            }
        }
    }
}

@Composable
private fun DiagnosticReportHero(state: FormalDiagnosticReportUiState, onRegenerate: () -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(Modifier.fillMaxWidth(), colors.successSoft, Color(0xFFB8DEC9)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(Icons.Rounded.Security, null, Color(0xFF43A17D), size = 44.dp, glyphSize = 21.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("诊断报告已生成", color = colors.ink, style = type.style(13f, 19f, FontWeight.Medium))
                Text("${state.createdAtLabel} · ${state.reportId}", color = colors.faint, style = type.style(8f, 13f))
                Text("已脱敏", color = colors.success, style = type.style(8f, 13f), modifier = Modifier.background(Color.White.copy(alpha = .6f), RoundedCornerShape(11.dp)).padding(horizontal = 10.dp, vertical = 3.dp))
            }
            Text("重新生成", color = colors.primary, style = type.style(8f, 13f), modifier = Modifier.clickable(onClickLabel = "重新生成", onClick = onRegenerate))
        }
    }
}

@Composable
private fun ReportValueRow(title: String, value: String, icon: ImageVector, success: Boolean = false) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth().height(42.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        FormalGlossySquare(icon, null, Color(0xFF5B8DBE), size = 25.dp, glyphSize = 13.dp)
        Spacer(Modifier.width(10.dp))
        Text(title, color = colors.muted, style = type.style(8.5f, 13f), modifier = Modifier.weight(1f))
        Text(value, color = if (success) colors.success else colors.ink, style = type.style(8.5f, 13f, FontWeight.Medium))
    }
}

@Composable
private fun DiagnosticEventRow(event: FormalDiagnosticEvent) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        FormalGlossySquare(
            if (event.warning) Icons.Rounded.WarningAmber else Icons.Rounded.CheckCircleOutline,
            null,
            if (event.warning) colors.warning else colors.success,
            size = 28.dp,
            glyphSize = 14.dp
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
            Text(event.subtitle, color = colors.faint, style = type.style(8f, 12f))
        }
        Text(
            event.statusLabel,
            color = if (event.warning) colors.warning else colors.success,
            style = type.style(8f, 13f)
        )
    }
}

@Composable
private fun DiagnosticsDivider() {
    val colors = formalBatch6Colors()
    Box(Modifier.fillMaxWidth().padding(start = 38.dp).height(1.dp).background(colors.divider))
}
