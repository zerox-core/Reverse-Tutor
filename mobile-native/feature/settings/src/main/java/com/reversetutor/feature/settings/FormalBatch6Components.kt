package com.reversetutor.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reversetutor.core.design.FormalColors
import com.reversetutor.core.design.FormalShapes
import com.reversetutor.core.design.LocalFormalTypeScale
import com.reversetutor.core.design.style

@Immutable
internal data class FormalBatch6Colors(
    val background: Color,
    val surface: Color,
    val surfaceQuiet: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val divider: Color,
    val primary: Color,
    val primarySoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color
)

@Composable
internal fun formalBatch6Colors(): FormalBatch6Colors {
    val scheme = MaterialTheme.colorScheme
    return if (isSystemInDarkTheme()) {
        FormalBatch6Colors(
            background = scheme.background,
            surface = scheme.surface,
            surfaceQuiet = scheme.surfaceVariant,
            ink = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            faint = scheme.onSurfaceVariant.copy(alpha = 0.72f),
            border = scheme.outlineVariant,
            divider = scheme.outlineVariant,
            primary = scheme.primary,
            primarySoft = scheme.primaryContainer,
            success = FormalColors.Success,
            successSoft = FormalColors.SuccessSoft.copy(alpha = 0.18f),
            warning = FormalColors.Warning,
            warningSoft = FormalColors.WarningSoft.copy(alpha = 0.18f),
            danger = FormalColors.Danger,
            dangerSoft = FormalColors.Danger.copy(alpha = 0.14f)
        )
    } else {
        FormalBatch6Colors(
            background = FormalColors.Background,
            surface = Color(0xFFFBFCFF),
            surfaceQuiet = Color(0xFFF1F5FC),
            ink = Color(0xFF243047),
            muted = Color(0xFF70809A),
            faint = Color(0xFF91A0B5),
            border = Color(0xFFCBD9EC),
            divider = Color(0xFFD8E2F0),
            primary = FormalColors.Primary,
            primarySoft = Color(0xFFE9F2FF),
            success = Color(0xFF329A78),
            successSoft = Color(0xFFE8F6F0),
            warning = Color(0xFFC9872C),
            warningSoft = Color(0xFFFFF4E2),
            danger = Color(0xFFC94D55),
            dangerSoft = Color(0xFFFFEEEE)
        )
    }
}

@Composable
internal fun FormalBatch6Page(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    content: @Composable (FormalBatch6Colors) -> Unit
) {
    val colors = formalBatch6Colors()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        FormalBatch6TopBar(
            title = title,
            subtitle = subtitle,
            onBack = onBack,
            colors = colors,
            trailing = trailing
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content(colors)
        }
        bottomBar?.invoke()
    }
}

@Composable
internal fun FormalBatch6TopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    colors: FormalBatch6Colors,
    trailing: (@Composable () -> Unit)? = null
) {
    val type = LocalFormalTypeScale.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(colors.surface)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 7.dp)
                .size(40.dp)
                .clickable(role = Role.Button, onClickLabel = "返回", onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBackIosNew,
                contentDescription = "返回",
                tint = colors.ink,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 52.dp, end = if (trailing == null) 20.dp else 88.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = title,
                color = colors.ink,
                style = type.style(18f, 25f, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = colors.faint,
                    style = type.style(9f, 14f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.let {
            Row(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { it() }
        }
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
internal fun FormalOutlinedCard(
    modifier: Modifier = Modifier,
    background: Color? = null,
    border: Color? = null,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit
) {
    val colors = formalBatch6Colors()
    Surface(
        modifier = modifier,
        color = background ?: colors.surface,
        shape = RoundedCornerShape(FormalShapes.CardRadius),
        border = BorderStroke(1.dp, border ?: colors.border)
    ) {
        Box(modifier = Modifier.padding(padding)) { content() }
    }
}

@Composable
internal fun FormalGlossySquare(
    imageVector: ImageVector,
    contentDescription: String?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    glyphSize: Dp = 20.dp
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(size)
            .shadow(5.dp, shape, ambientColor = Color(0x241B3F73), spotColor = Color(0x241B3F73))
            .background(
                brush = Brush.verticalGradient(
                    listOf(
                        color.copy(red = (color.red + 0.16f).coerceAtMost(1f)),
                        color
                    )
                ),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(glyphSize)
        )
    }
}

@Composable
internal fun FormalSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            color = colors.ink,
            style = type.style(13f, 20f, FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        trailing?.let {
            Text(text = it, color = colors.faint, style = type.style(9f, 14f))
        }
    }
}

@Composable
internal fun FormalInfoBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    success: Boolean = false
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (success) colors.successSoft else colors.primarySoft,
                RoundedCornerShape(FormalShapes.CardRadius)
            )
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (success) Icons.Rounded.Check else Icons.Rounded.Info,
            contentDescription = null,
            tint = if (success) colors.success else Color(0xFF4D8BC9),
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = colors.muted, style = type.style(9f, 14f, FontWeight.Medium))
            if (body.isNotBlank()) {
                Text(text = body, color = colors.faint, style = type.style(8f, 13f))
            }
        }
    }
}

@Composable
internal fun FormalChevronRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    value: String? = null
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val click = if (onClick == null) Modifier else Modifier.clickable(
        role = Role.Button,
        onClickLabel = title,
        onClick = onClick
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(click)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FormalGlossySquare(icon, null, iconColor, size = 36.dp, glyphSize = 17.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
            Text(text = subtitle, color = colors.faint, style = type.style(8f, 13f), maxLines = 1)
        }
        value?.let {
            Text(text = it, color = colors.muted, style = type.style(9f, 14f))
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(19.dp)
        )
    }
}

@Composable
internal fun FormalPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    icon: ImageVector? = null
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    val color = when {
        !enabled -> colors.faint
        danger -> colors.danger
        else -> Color(0xFF4F8CCB)
    }
    Row(
        modifier = modifier
            .height(48.dp)
            .background(color, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = text, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon?.let {
            Icon(it, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, color = Color.White, style = type.style(11f, 17f, FontWeight.Medium))
    }
}

@Composable
internal fun FormalSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = formalBatch6Colors()
    val type = LocalFormalTypeScale.current
    Box(
        modifier = modifier
            .height(48.dp)
            .background(colors.surface, RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = text, onClick = onClick)
            .then(Modifier),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.border)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = text, color = colors.ink, style = type.style(11f, 17f, FontWeight.Medium))
            }
        }
    }
}

@Composable
internal fun FormalStaticToggle(enabled: Boolean, label: String, modifier: Modifier = Modifier) {
    val colors = formalBatch6Colors()
    Box(
        modifier = modifier
            .width(42.dp)
            .height(24.dp)
            .clearAndSetSemantics {
                contentDescription = "$label：${if (enabled) "开启" else "关闭"}"
            }
            .background(if (enabled) colors.success else colors.faint, CircleShape)
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}
