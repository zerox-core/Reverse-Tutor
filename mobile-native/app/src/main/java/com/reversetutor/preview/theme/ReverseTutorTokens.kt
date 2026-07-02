package com.reversetutor.preview.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class ReverseTutorSpacingTokens(
    val space1: Dp = 4.dp,
    val space2: Dp = 8.dp,
    val space3: Dp = 12.dp,
    val space4: Dp = 16.dp,
    val space5: Dp = 20.dp,
    val space6: Dp = 24.dp,
    val space8: Dp = 32.dp,
    val minTouchTarget: Dp = 48.dp,
    val contentMaxWidth: Dp = 720.dp
)

@Immutable
data class ReverseTutorShapeTokens(
    val radiusSmall: Dp = 4.dp,
    val radiusMedium: Dp = 6.dp,
    val radiusCard: Dp = 8.dp,
    val radiusSheet: Dp = 16.dp
)

@Immutable
data class ReverseTutorElevationTokens(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 2.dp,
    val level3: Dp = 3.dp,
    val level4: Dp = 6.dp
)

@Immutable
data class ReverseTutorMotionTokens(
    val fastMillis: Int = 120,
    val normalMillis: Int = 180,
    val slowMillis: Int = 280
)

enum class ReverseTutorStatusTone {
    Neutral,
    Info,
    Success,
    Warning,
    Error,
    Disabled,
    GenerationIdle,
    GenerationActive,
    ParserSupported,
    ParserPartial,
    ParserDeferred,
    ParserUnsupported,
    ParserFailed
}

@Immutable
data class ReverseTutorToneColors(
    val container: Color,
    val content: Color,
    val accent: Color
)

@Immutable
data class ReverseTutorSemanticColors(
    val neutralContainer: Color,
    val onNeutralContainer: Color,
    val neutralAccent: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val infoAccent: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val successAccent: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val warningAccent: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val errorAccent: Color,
    val disabledContainer: Color,
    val onDisabledContainer: Color,
    val disabledAccent: Color
) {
    fun forTone(tone: ReverseTutorStatusTone): ReverseTutorToneColors = when (tone) {
        ReverseTutorStatusTone.Neutral -> ReverseTutorToneColors(
            neutralContainer,
            onNeutralContainer,
            neutralAccent
        )
        ReverseTutorStatusTone.Info,
        ReverseTutorStatusTone.GenerationActive -> ReverseTutorToneColors(
            infoContainer,
            onInfoContainer,
            infoAccent
        )
        ReverseTutorStatusTone.Success,
        ReverseTutorStatusTone.ParserSupported -> ReverseTutorToneColors(
            successContainer,
            onSuccessContainer,
            successAccent
        )
        ReverseTutorStatusTone.Warning,
        ReverseTutorStatusTone.ParserPartial,
        ReverseTutorStatusTone.ParserDeferred -> ReverseTutorToneColors(
            warningContainer,
            onWarningContainer,
            warningAccent
        )
        ReverseTutorStatusTone.Error,
        ReverseTutorStatusTone.ParserFailed -> ReverseTutorToneColors(
            errorContainer,
            onErrorContainer,
            errorAccent
        )
        ReverseTutorStatusTone.Disabled,
        ReverseTutorStatusTone.GenerationIdle,
        ReverseTutorStatusTone.ParserUnsupported -> ReverseTutorToneColors(
            disabledContainer,
            onDisabledContainer,
            disabledAccent
        )
    }
}

@Immutable
data class ReverseTutorDesignTokens(
    val colorScheme: ColorScheme,
    val semanticColors: ReverseTutorSemanticColors,
    val typography: Typography,
    val materialShapes: Shapes,
    val spacing: ReverseTutorSpacingTokens,
    val shapes: ReverseTutorShapeTokens,
    val elevations: ReverseTutorElevationTokens,
    val motion: ReverseTutorMotionTokens
)

object ReverseTutorThemeTokens {
    val spacing = ReverseTutorSpacingTokens()
    val shapes = ReverseTutorShapeTokens()
    val elevations = ReverseTutorElevationTokens()
    val motion = ReverseTutorMotionTokens()

    val lightColorScheme = lightColorScheme(
        primary = Color(0xFF1E6B5E),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFC7EAE1),
        onPrimaryContainer = Color(0xFF06201A),
        inversePrimary = Color(0xFF86D6C6),
        secondary = Color(0xFF7B5E2E),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3DFB5),
        onSecondaryContainer = Color(0xFF281900),
        tertiary = Color(0xFF315F8A),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFD0E4FF),
        onTertiaryContainer = Color(0xFF001D33),
        background = Color(0xFFF7FAF8),
        onBackground = Color(0xFF18211F),
        surface = Color(0xFFF7FAF8),
        onSurface = Color(0xFF18211F),
        surfaceVariant = Color(0xFFE0E9E5),
        onSurfaceVariant = Color(0xFF46534F),
        surfaceTint = Color(0xFF1E6B5E),
        inverseSurface = Color(0xFF2D3230),
        inverseOnSurface = Color(0xFFEEF2EF),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF71817B),
        outlineVariant = Color(0xFFC0CAC5),
        scrim = Color.Black
    )

    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF9FD8CC),
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF005145),
        onPrimaryContainer = Color(0xFFBAF4E7),
        inversePrimary = Color(0xFF1E6B5E),
        secondary = Color(0xFFE2C48E),
        onSecondary = Color(0xFF432C00),
        secondaryContainer = Color(0xFF5F4617),
        onSecondaryContainer = Color(0xFFFFDEA6),
        tertiary = Color(0xFF9FCBFA),
        onTertiary = Color(0xFF003354),
        tertiaryContainer = Color(0xFF124A71),
        onTertiaryContainer = Color(0xFFD0E4FF),
        background = Color(0xFF101513),
        onBackground = Color(0xFFDEE4E1),
        surface = Color(0xFF101513),
        onSurface = Color(0xFFDEE4E1),
        surfaceVariant = Color(0xFF3F4946),
        onSurfaceVariant = Color(0xFFC0CAC5),
        surfaceTint = Color(0xFF9FD8CC),
        inverseSurface = Color(0xFFDEE4E1),
        inverseOnSurface = Color(0xFF2B3130),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = Color(0xFF8B9993),
        outlineVariant = Color(0xFF3F4946),
        scrim = Color.Black
    )

    val lightSemanticColors = ReverseTutorSemanticColors(
        neutralContainer = Color(0xFFE0E9E5),
        onNeutralContainer = Color(0xFF18211F),
        neutralAccent = Color(0xFF71817B),
        infoContainer = Color(0xFFD0E4FF),
        onInfoContainer = Color(0xFF001D33),
        infoAccent = Color(0xFF315F8A),
        successContainer = Color(0xFFC5EFD6),
        onSuccessContainer = Color(0xFF00391E),
        successAccent = Color(0xFF1E7D4F),
        warningContainer = Color(0xFFFFE0A8),
        onWarningContainer = Color(0xFF2A1800),
        warningAccent = Color(0xFF8C5F00),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        errorAccent = Color(0xFFBA1A1A),
        disabledContainer = Color(0xFFD7DFDB),
        onDisabledContainer = Color(0xFF66736E),
        disabledAccent = Color(0xFF7A8580)
    )

    val darkSemanticColors = ReverseTutorSemanticColors(
        neutralContainer = Color(0xFF2E3835),
        onNeutralContainer = Color(0xFFDEE4E1),
        neutralAccent = Color(0xFF8B9993),
        infoContainer = Color(0xFF124A71),
        onInfoContainer = Color(0xFFD0E4FF),
        infoAccent = Color(0xFF9FCBFA),
        successContainer = Color(0xFF0B4F2B),
        onSuccessContainer = Color(0xFFC5EFD6),
        successAccent = Color(0xFF74D99D),
        warningContainer = Color(0xFF5E4300),
        onWarningContainer = Color(0xFFFFE0A8),
        warningAccent = Color(0xFFEBC060),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        errorAccent = Color(0xFFFFB4AB),
        disabledContainer = Color(0xFF28302E),
        onDisabledContainer = Color(0xFF98A39E),
        disabledAccent = Color(0xFF7D8984)
    )

    val typography = Typography(
        displaySmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp
        )
    )

    val materialShapes = Shapes(
        extraSmall = RoundedCornerShape(shapes.radiusSmall),
        small = RoundedCornerShape(shapes.radiusMedium),
        medium = RoundedCornerShape(shapes.radiusCard),
        large = RoundedCornerShape(shapes.radiusSheet),
        extraLarge = RoundedCornerShape(shapes.radiusSheet)
    )

    fun designTokens(darkTheme: Boolean): ReverseTutorDesignTokens {
        return ReverseTutorDesignTokens(
            colorScheme = if (darkTheme) darkColorScheme else lightColorScheme,
            semanticColors = if (darkTheme) darkSemanticColors else lightSemanticColors,
            typography = typography,
            materialShapes = materialShapes,
            spacing = spacing,
            shapes = shapes,
            elevations = elevations,
            motion = motion
        )
    }
}
