package com.reversetutor.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object FormalColors {
    val Background = Color(0xFFF2F2F7)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSubtle = Color(0xFFF7F7FA)
    val SurfaceElevated = Color(0xFFFFFFFF)
    val Primary = Color(0xFF2E5CDE)
    val PrimarySoft = Color(0xFFE8EEFF)
    val Ink = Color(0xFF1C1C1E)
    val Muted = Color(0xFF6E6E73)
    val Tertiary = Color(0xFF8E8E93)
    val Border = Color(0xFFD1D1D6)
    val BorderStrong = Color(0xFFB9BAC0)
    val Divider = Color(0xFFE5E5EA)
    val Shadow = Color(0x1F000000)
    val Success = Color(0xFF27A77B)
    val SuccessSoft = Color(0xFFE5F6F1)
    val Warning = Color(0xFFE89A3D)
    val WarningSoft = Color(0xFFFFF3E3)
    val Danger = Color(0xFFD85C6F)
}

object FormalShapes {
    val CardRadius: Dp = 8.dp
    val CompactRadius: Dp = 6.dp
    val IconRadius: Dp = 12.dp
    val PillRadius: Dp = 999.dp
}

object FormalElevations {
    val Panel: Dp = 2.dp
    val Raised: Dp = 5.dp
    val Sheet: Dp = 10.dp
}

@Immutable
data class FormalTypeScale(
    val multiplier: Float = 1f
) {
    init {
        require(multiplier in 0.85f..1.30f)
    }

    fun size(baseSp: Float): TextUnit = (baseSp * multiplier).sp
}

val LocalFormalTypeScale = staticCompositionLocalOf { FormalTypeScale() }

fun FormalTypeScale.style(
    sizeSp: Float,
    lineHeightSp: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified
): TextStyle = TextStyle(
    color = color,
    fontSize = size(sizeSp),
    lineHeight = size(lineHeightSp),
    fontWeight = weight,
    letterSpacing = 0.sp
)

object FormalTypography {
    fun pageTitle(scale: FormalTypeScale, color: Color = Color.Unspecified): TextStyle =
        scale.style(28f, 36f, FontWeight.Bold, color)

    fun sectionTitle(scale: FormalTypeScale, color: Color = Color.Unspecified): TextStyle =
        scale.style(20f, 28f, FontWeight.Bold, color)

    fun cardTitle(scale: FormalTypeScale, color: Color = Color.Unspecified): TextStyle =
        scale.style(16f, 23f, FontWeight.SemiBold, color)

    fun body(scale: FormalTypeScale, color: Color = Color.Unspecified): TextStyle =
        scale.style(14f, 21f, FontWeight.Normal, color)

    fun metadata(scale: FormalTypeScale, color: Color = Color.Unspecified): TextStyle =
        scale.style(12f, 18f, FontWeight.Normal, color)

    fun status(scale: FormalTypeScale, color: Color = Color.Unspecified): TextStyle =
        scale.style(11f, 16f, FontWeight.Medium, color)

    fun control(scale: FormalTypeScale, color: Color = Color.Unspecified): TextStyle =
        scale.style(14f, 20f, FontWeight.SemiBold, color)
}
