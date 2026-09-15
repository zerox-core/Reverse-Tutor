package com.reversetutor.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Immutable
data class FormalSettingsUiState(
    val accountTitle: String = "学习账户",
    val accountStatus: String = "同步正常 · 数据仅本人可见",
    val challengeReminderEnabled: Boolean = true,
    val automaticDownloadLabel: String = "仅 Wi-Fi",
    val llmConfigurationLabel: String = "未配置",
    val layoutSizeLabel: String = "标准",
    val hapticFeedbackEnabled: Boolean = true,
    val backgroundGenerationNotificationEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = true,
    val syncStatusLabel: String = "已同步",
    val storageLabel: String = "1.8 GB"
) {
    companion object {
        fun from(
            llmProfileState: LlmProfileSettingsUiState,
            challengeReminderEnabled: Boolean = true,
            hapticFeedbackEnabled: Boolean = true,
            backgroundGenerationNotificationEnabled: Boolean = false,
            notificationPermissionGranted: Boolean = true
        ): FormalSettingsUiState =
            FormalSettingsUiState(
                challengeReminderEnabled = challengeReminderEnabled,
                hapticFeedbackEnabled = hapticFeedbackEnabled,
                backgroundGenerationNotificationEnabled = backgroundGenerationNotificationEnabled,
                notificationPermissionGranted = notificationPermissionGranted,
                llmConfigurationLabel = if (llmProfileState.profileItems.isEmpty()) {
                    "未配置"
                } else {
                    "已配置"
                }
            )
    }
}

@Composable
fun FormalSettingsScreen(
    llmProfileState: LlmProfileSettingsUiState,
    onBack: () -> Unit,
    onOpenLlmConfiguration: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenAbout: () -> Unit,
    challengeReminderEnabled: Boolean = true,
    hapticFeedbackEnabled: Boolean = true,
    onChallengeReminderChanged: (Boolean) -> Unit = {},
    onHapticFeedbackChanged: (Boolean) -> Unit = {},
    backgroundGenerationNotificationEnabled: Boolean = false,
    notificationPermissionGranted: Boolean = true,
    onBackgroundGenerationNotificationChanged: (Boolean) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    FormalSettingsScreen(
        state = FormalSettingsUiState.from(
            llmProfileState = llmProfileState,
            challengeReminderEnabled = challengeReminderEnabled,
            hapticFeedbackEnabled = hapticFeedbackEnabled,
            backgroundGenerationNotificationEnabled = backgroundGenerationNotificationEnabled,
            notificationPermissionGranted = notificationPermissionGranted
        ),
        onBack = onBack,
        onOpenLlmConfiguration = onOpenLlmConfiguration,
        onOpenStorage = onOpenStorage,
        onOpenImportExport = onOpenImportExport,
        onOpenAbout = onOpenAbout,
        onChallengeReminderChanged = onChallengeReminderChanged,
        onHapticFeedbackChanged = onHapticFeedbackChanged,
        onBackgroundGenerationNotificationChanged = onBackgroundGenerationNotificationChanged,
        onOpenNotificationSettings = onOpenNotificationSettings,
        modifier = modifier
    )
}

@Composable
fun FormalSettingsScreen(
    state: FormalSettingsUiState,
    onBack: () -> Unit,
    onOpenLlmConfiguration: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenImportExport: () -> Unit,
    onOpenAbout: () -> Unit,
    onChallengeReminderChanged: (Boolean) -> Unit = {},
    onHapticFeedbackChanged: (Boolean) -> Unit = {},
    onBackgroundGenerationNotificationChanged: (Boolean) -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = formalSettingsColors()
    val sections = formalSettingsSections(state)
    val callbacks = mapOf(
        FormalSettingsAction.ToggleChallengeReminder to {
            onChallengeReminderChanged(!state.challengeReminderEnabled)
        },
        FormalSettingsAction.OpenLlmConfiguration to onOpenLlmConfiguration,
        FormalSettingsAction.ToggleHapticFeedback to {
            onHapticFeedbackChanged(!state.hapticFeedbackEnabled)
        },
        FormalSettingsAction.ToggleBackgroundGenerationNotification to {
            onBackgroundGenerationNotificationChanged(!state.backgroundGenerationNotificationEnabled)
        },
        FormalSettingsAction.OpenNotificationSettings to onOpenNotificationSettings,
        FormalSettingsAction.OpenStorage to onOpenStorage,
        FormalSettingsAction.OpenImportExport to onOpenImportExport,
        FormalSettingsAction.OpenAbout to onOpenAbout
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        FormalSettingsTopBar(colors = colors, onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 22.dp)
        ) {
            AccountAndSyncCard(state = state, colors = colors)
            Spacer(Modifier.height(22.dp))
            sections.forEachIndexed { index, section ->
                SettingsSection(
                    section = section,
                    colors = colors,
                    onAction = { action -> callbacks.getValue(action).invoke() }
                )
                if (index != sections.lastIndex) {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FormalSettingsTopBar(
    colors: FormalSettingsColors,
    onBack: () -> Unit
) {
    val typeScale = LocalFormalTypeScale.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(colors.topBar)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
                .size(44.dp)
                .clickable(role = Role.Button, onClickLabel = "返回会话首页", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "返回会话首页",
                tint = colors.backIcon,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = "设置",
            color = colors.ink,
            style = typeScale.style(
                sizeSp = 18f,
                lineHeightSp = 28f,
                weight = FontWeight.SemiBold
            ),
            modifier = Modifier.align(Alignment.Center)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.divider)
        )
    }
}

@Composable
private fun AccountAndSyncCard(
    state: FormalSettingsUiState,
    colors: FormalSettingsColors
) {
    val typeScale = LocalFormalTypeScale.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
        color = colors.surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(colors.avatar),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = state.accountTitle,
                    color = colors.ink,
                    style = typeScale.style(
                        sizeSp = 16f,
                        lineHeightSp = 23f,
                        weight = FontWeight.SemiBold,
                        color = colors.ink
                    )
                )
                Text(
                    text = state.accountStatus,
                    color = colors.muted,
                    style = typeScale.style(
                        sizeSp = 11f,
                        lineHeightSp = 16f,
                        weight = FontWeight.Medium,
                        color = colors.muted
                    )
                )
            }
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(52.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                        .size(7.dp)
                        .background(colors.success, CircleShape)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.chevron,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    section: FormalSettingsSectionSpec,
    colors: FormalSettingsColors,
    onAction: (FormalSettingsAction) -> Unit
) {
    val typeScale = LocalFormalTypeScale.current
    Text(
        text = section.title,
        color = colors.sectionLabel,
        style = typeScale.style(
            sizeSp = 12f,
            lineHeightSp = 17f,
            weight = FontWeight.Medium,
            color = colors.sectionLabel
        ),
        modifier = Modifier.padding(start = 2.dp)
    )
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column {
            section.rows.forEachIndexed { index, row ->
                Box {
                    FormalSettingsRow(
                        row = row,
                        colors = colors,
                        onAction = onAction
                    )
                    if (index != section.rows.lastIndex) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(start = 58.dp)
                                .height(1.dp)
                                .background(colors.divider)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormalSettingsRow(
    row: FormalSettingsRowSpec,
    colors: FormalSettingsColors,
    onAction: (FormalSettingsAction) -> Unit
) {
    val typeScale = LocalFormalTypeScale.current
    val interaction = row.action?.let { action ->
        row.toggleEnabled?.let { checked ->
            Modifier.toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = { onAction(action) }
            )
        } ?: Modifier.clickable(
            role = Role.Button,
            onClickLabel = row.label,
            onClick = { onAction(action) }
        )
    } ?: Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .then(interaction)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsFlatIcon(
            imageVector = row.icon.imageVector,
            contentDescription = null,
            colors = colors
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = row.label,
            color = colors.rowLabel,
            style = typeScale.style(
                sizeSp = 14f,
                lineHeightSp = 21f,
                color = colors.rowLabel
            ),
            modifier = Modifier.weight(1f)
        )
        row.value?.let { value ->
            Text(
                text = value,
                color = colors.value,
                style = typeScale.style(sizeSp = 12f, lineHeightSp = 18f)
            )
            Spacer(Modifier.width(8.dp))
        }
        row.toggleEnabled?.let { enabled ->
            SettingsToggle(enabled = enabled, colors = colors)
        }
        if (row.hasChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.chevron,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    enabled: Boolean,
    colors: FormalSettingsColors
) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clearAndSetSemantics { }
            .background(
                color = if (enabled) colors.toggleOn else colors.toggleOff,
                shape = RoundedCornerShape(FormalShapes.PillRadius)
            )
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                .size(20.dp)
                .background(colors.toggleThumb, CircleShape)
        )
    }
}

@Composable
private fun SettingsFlatIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    colors: FormalSettingsColors,
    size: Dp = 30.dp,
    glyphSize: Dp = 17.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.iconChip),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = colors.iconGlyph,
            modifier = Modifier.size(glyphSize)
        )
    }
}

internal fun formalSettingsSections(
    state: FormalSettingsUiState
): List<FormalSettingsSectionSpec> = listOf(
    FormalSettingsSectionSpec(
        title = "外观与布局",
        rows = listOf(
            FormalSettingsRowSpec(
                label = "默认布局大小",
                icon = FormalSettingsIcon.Layout,
                value = state.layoutSizeLabel,
                hasChevron = true
            ),
            FormalSettingsRowSpec(
                label = "触感反馈",
                icon = FormalSettingsIcon.Haptics,
                toggleEnabled = state.hapticFeedbackEnabled,
                action = FormalSettingsAction.ToggleHapticFeedback
            )
        )
    ),
    FormalSettingsSectionSpec(
        title = "模型与连接",
        rows = listOf(
            FormalSettingsRowSpec(
                label = "LLM API 配置",
                icon = FormalSettingsIcon.ApiKey,
                value = state.llmConfigurationLabel,
                hasChevron = true,
                action = FormalSettingsAction.OpenLlmConfiguration
            )
        )
    ),
    FormalSettingsSectionSpec(
        title = "数据与资料",
        rows = listOf(
            FormalSettingsRowSpec(
                label = "同步与备份",
                icon = FormalSettingsIcon.Sync,
                value = state.syncStatusLabel,
                hasChevron = true
            ),
            FormalSettingsRowSpec(
                label = "存储空间",
                icon = FormalSettingsIcon.Storage,
                value = state.storageLabel,
                hasChevron = true,
                action = FormalSettingsAction.OpenStorage
            ),
            FormalSettingsRowSpec(
                label = "导入与导出",
                icon = FormalSettingsIcon.ImportExport,
                hasChevron = true,
                action = FormalSettingsAction.OpenImportExport
            )
        )
    ),
    FormalSettingsSectionSpec(
        title = "权限与系统",
        rows = listOf(
            FormalSettingsRowSpec(
                label = "挑战任务提醒",
                icon = FormalSettingsIcon.Challenge,
                toggleEnabled = state.challengeReminderEnabled,
                action = FormalSettingsAction.ToggleChallengeReminder
            ),
            FormalSettingsRowSpec(
                label = "后台生成通知",
                icon = FormalSettingsIcon.Notifications,
                toggleEnabled = state.backgroundGenerationNotificationEnabled,
                value = if (!state.notificationPermissionGranted) "未授权" else null,
                action = FormalSettingsAction.ToggleBackgroundGenerationNotification
            ),
            FormalSettingsRowSpec(
                label = "通知权限设置",
                icon = FormalSettingsIcon.Notifications,
                hasChevron = true,
                action = FormalSettingsAction.OpenNotificationSettings
            ),
            FormalSettingsRowSpec(
                label = "隐私与权限",
                icon = FormalSettingsIcon.Privacy,
                hasChevron = true
            )
        )
    ),
    FormalSettingsSectionSpec(
        title = "关于与版本",
        rows = listOf(
            FormalSettingsRowSpec(
                label = "帮助与关于",
                icon = FormalSettingsIcon.About,
                hasChevron = true,
                action = FormalSettingsAction.OpenAbout
            ),
        )
    )
)

internal data class FormalSettingsSectionSpec(
    val title: String,
    val rows: List<FormalSettingsRowSpec>
)

internal data class FormalSettingsRowSpec(
    val label: String,
    val icon: FormalSettingsIcon,
    val value: String? = null,
    val toggleEnabled: Boolean? = null,
    val hasChevron: Boolean = false,
    val action: FormalSettingsAction? = null
)

internal enum class FormalSettingsAction {
    ToggleChallengeReminder,
    OpenLlmConfiguration,
    ToggleHapticFeedback,
    ToggleBackgroundGenerationNotification,
    OpenNotificationSettings,
    OpenStorage,
    OpenImportExport,
    OpenAbout
}

internal enum class FormalSettingsIcon(
    val imageVector: ImageVector
) {
    Challenge(Icons.Rounded.EmojiEvents),
    Download(Icons.Rounded.Download),
    ApiKey(Icons.Rounded.Key),
    Layout(Icons.AutoMirrored.Rounded.FormatAlignLeft),
    Haptics(Icons.Rounded.Vibration),
    Notifications(Icons.Rounded.Notifications),
    Sync(Icons.Rounded.Sync),
    Storage(Icons.Rounded.Storage),
    ImportExport(Icons.Rounded.ImportExport),
    Privacy(Icons.Rounded.Shield),
    About(Icons.AutoMirrored.Rounded.HelpOutline)
}

@Immutable
private data class FormalSettingsColors(
    val background: Color,
    val topBar: Color,
    val surface: Color,
    val ink: Color,
    val rowLabel: Color,
    val sectionLabel: Color,
    val muted: Color,
    val value: Color,
    val border: Color,
    val divider: Color,
    val success: Color,
    val chevron: Color,
    val backIcon: Color,
    val avatar: Color,
    val iconChip: Color,
    val iconGlyph: Color,
    val toggleOn: Color,
    val toggleOff: Color,
    val toggleThumb: Color
)

@Composable
private fun formalSettingsColors(): FormalSettingsColors {
    val scheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        FormalSettingsColors(
            background = scheme.background,
            topBar = scheme.surface,
            surface = scheme.surfaceVariant,
            ink = scheme.onSurface,
            rowLabel = scheme.onSurface,
            sectionLabel = scheme.onSurfaceVariant,
            muted = scheme.onSurfaceVariant,
            value = scheme.onSurfaceVariant,
            border = scheme.outlineVariant,
            divider = scheme.outlineVariant,
            success = FormalColors.Success,
            chevron = scheme.onSurfaceVariant,
            backIcon = scheme.onSurface,
            avatar = scheme.onSurface,
            iconChip = scheme.surface,
            iconGlyph = scheme.onSurface,
            toggleOn = FormalColors.Success,
            toggleOff = scheme.outlineVariant,
            toggleThumb = scheme.onPrimary
        )
    } else {
        FormalSettingsColors(
            background = FormalColors.Background,
            topBar = FormalColors.Surface,
            surface = FormalColors.Surface,
            ink = FormalColors.Ink,
            rowLabel = FormalColors.Ink,
            sectionLabel = FormalColors.Muted,
            muted = FormalColors.Muted,
            value = FormalColors.Tertiary,
            border = FormalColors.Border,
            divider = FormalColors.Divider,
            success = FormalColors.Success,
            chevron = FormalColors.Tertiary,
            backIcon = FormalColors.Ink,
            avatar = FormalColors.Ink,
            iconChip = FormalColors.SurfaceSubtle,
            iconGlyph = Color(0xFF3A3A3C),
            toggleOn = FormalColors.Success,
            toggleOff = FormalColors.Border,
            toggleThumb = Color.White
        )
    }
}
