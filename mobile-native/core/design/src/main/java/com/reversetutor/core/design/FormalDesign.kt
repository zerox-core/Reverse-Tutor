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
    val Background = Color(0xFFF4F7FD)
    val Surface = Color(0xFFFBFCFF)
    val SurfaceElevated = Color(0xFFFFFFFF)
    val Primary = Color(0xFF2E5CDE)
    val PrimarySoft = Color(0xFFEAF1FF)
    val Ink = Color(0xFF172033)
    val Muted = Color(0xFF73809A)
    val Border = Color(0xFFD6DFEF)
    val Divider = Color(0xFFE1E7F1)
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
    fontWeight = weight
)
