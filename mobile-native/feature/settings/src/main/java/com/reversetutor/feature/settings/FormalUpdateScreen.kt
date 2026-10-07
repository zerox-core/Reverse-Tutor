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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Immutable
data class FormalAvailableUpdate(
    val versionLabel: String,
    val sizeLabel: String,
    val changes: List<String>
)

@Immutable
data class FormalUpdateUiState(
    val currentVersionLabel: String,
    val channelLabel: String,
    val lastCheckedLabel: String,
    val isLatest: Boolean,
    val automaticUpdatesEnabled: Boolean,
    val wifiOnlyEnabled: Boolean,
    val currentReleaseSummary: String,
    val archivedVersionLabel: String,
    val availableUpdate: FormalAvailableUpdate? = null
)

@Composable
fun FormalUpdateScreen(
    state: FormalUpdateUiState,
    onBack: () -> Unit,
    onAutomaticUpdatesChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onOpenChannel: () -> Unit,
    onCheckUpdate: () -> Unit,
    onOpenCurrentReleaseNotes: () -> Unit,
    onDismissAvailableUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    FormalBatch6Page("应用更新", "当前版本与检查设置", onBack, modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 24.dp)) {
            item {
                UpdateAppCard(state)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("更新设置")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 2.dp)) {
                    Column {
                        UpdateToggleRow("自动检查更新", "每 24 小时检查一次", state.automaticUpdatesEnabled) {
                            onAutomaticUpdatesChange(!state.automaticUpdatesEnabled)
                        }
                        UpdateDivider()
                        UpdateToggleRow("仅在 Wi-Fi 下载", "避免占用移动数据", state.wifiOnlyEnabled) {
                            onWifiOnlyChange(!state.wifiOnlyEnabled)
                        }
                        UpdateDivider()
                        UpdateValueRow("更新渠道", "接收稳定版本", state.channelLabel, onOpenChannel)
                    }
                }
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("检查状态")
                Spacer(Modifier.height(8.dp))
                UpdateCheckCard(state, onCheckUpdate)
                Spacer(Modifier.height(24.dp))
                FormalSectionLabel("版本记录")
                Spacer(Modifier.height(8.dp))
                FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 2.dp)) {
                    Column {
                        UpdateValueRow("当前版本说明", state.currentReleaseSummary, "查看", onOpenCurrentReleaseNotes)
                        UpdateDivider()
                        UpdateValueRow("已忽略版本", "没有忽略的更新", state.archivedVersionLabel, null)
                    }
                }
                Spacer(Modifier.height(24.dp))
                FormalInfoBanner("下载完成后校验文件完整性", "安装由 Android 系统安装器完成")
            }
        }
    }
    state.availableUpdate?.let { update ->
        FormalAvailableUpdateDialog(update, onDismissAvailableUpdate, onDownloadUpdate)
    }
}

@Composable
private fun UpdateAppCard(state: FormalUpdateUiState) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(Modifier.fillMaxWidth(), padding = PaddingValues(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(Icons.Rounded.Share, null, Color(0xFF4F89C9), size = 47.dp, glyphSize = 22.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("反转家教", color = colors.ink, style = type.style(16f, 22f, FontWeight.SemiBold))
                Text("${state.currentVersionLabel} · ${state.channelLabel}", color = colors.faint, style = type.style(8f, 13f))
                if (state.isLatest) {
                    Text("已是最新", color = colors.success, style = type.style(8f, 13f), modifier = Modifier.background(colors.successSoft, RoundedCornerShape(12.dp)).padding(horizontal = 11.dp, vertical = 4.dp))
                }
            }
            Text(state.lastCheckedLabel, color = colors.faint, style = type.style(8f, 13f))
        }
    }
}

@Composable
private fun UpdateToggleRow(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        Modifier.fillMaxWidth().height(47.dp).clickable(role = Role.Switch, onClickLabel = title, onClick = onClick).padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
            Text(subtitle, color = colors.faint, style = type.style(7.5f, 12f))
        }
        FormalStaticToggle(enabled, title)
    }
}

@Composable
private fun UpdateValueRow(title: String, subtitle: String, value: String, onClick: (() -> Unit)?) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val click = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
    Row(Modifier.fillMaxWidth().height(51.dp).then(click).padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, style = type.style(9f, 14f, FontWeight.Medium))
            Text(subtitle, color = colors.faint, style = type.style(7.5f, 12f))
        }
        Text(value, color = colors.muted, style = type.style(8f, 13f))
        if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun UpdateCheckCard(state: FormalUpdateUiState, onCheck: () -> Unit) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    FormalOutlinedCard(
        modifier = Modifier.fillMaxWidth().height(71.dp).clickable(role = Role.Button, onClickLabel = "检查更新", onClick = onCheck),
        padding = PaddingValues(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormalGlossySquare(Icons.Rounded.Refresh, null, Color(0xFF4F89C9), size = 36.dp, glyphSize = 18.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("检查更新", color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
                Text("上次检查：${state.lastCheckedLabel}", color = colors.faint, style = type.style(8f, 13f))
            }
            Text("立即检查 ›", color = colors.primary, style = type.style(8f, 13f))
        }
    }
}

@Composable
private fun UpdateDivider() {
    val colors = formalBatch6Colors()
    Box(Modifier.fillMaxWidth().padding(start = 11.dp).height(1.dp).background(colors.divider))
}

@Composable
private fun FormalAvailableUpdateDialog(
    update: FormalAvailableUpdate,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            color = colors.surface,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 12.dp
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FormalGlossySquare(Icons.Rounded.Download, null, Color(0xFF4F8DC9), size = 48.dp, glyphSize = 23.dp)
                    Spacer(Modifier.height(16.dp))
                    Text("发现新版本", color = colors.ink, style = type.style(18f, 25f, FontWeight.SemiBold))
                    Text("${update.versionLabel} · ${update.sizeLabel}", color = colors.faint, style = type.style(9f, 14f))
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                    Spacer(Modifier.height(14.dp))
                    Text("本次更新", color = colors.ink, style = type.style(10f, 16f, FontWeight.Medium), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    update.changes.forEach { change ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(16.dp).background(colors.primarySoft, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Check, null, tint = Color(0xFF4F8DC9), modifier = Modifier.size(11.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(change, color = colors.muted, style = type.style(9f, 14f))
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.divider))
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormalSecondaryButton("忽略本次更新", onDismiss, Modifier.weight(1f))
                    FormalPrimaryButton("下载更新", onDownload, Modifier.weight(1.1f))
                }
            }
        }
    }
}
