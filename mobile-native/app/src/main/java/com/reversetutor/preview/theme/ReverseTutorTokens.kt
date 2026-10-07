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
    val minTouchTarget: Dp = 44.dp,
    val contentMaxWidth: Dp = 430.dp
)

@Immutable
data class ReverseTutorShapeTokens(
    val radiusSmall: Dp = 12.dp,
    val radiusMedium: Dp = 16.dp,
    val radiusCard: Dp = 18.dp,
    val radiusSheet: Dp = 24.dp
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
data class ReverseTutorSurfaceTokens(
    val home: Color,
    val homeGradientEnd: Color,
    val card: Color,
    val cardBorder: Color,
    val elevated: Color,
    val elevatedBorder: Color,
    val input: Color,
    val inputBorder: Color,
    val overlay: Color
)

@Immutable
data class ReverseTutorTextTokens(
    val ink: Color,
    val body: Color,
    val muted: Color,
    val highlight: Color,
    val onPrimary: Color,
    val onError: Color
)

@Immutable
data class ReverseTutorDesignTokens(
    val colorScheme: ColorScheme,
    val semanticColors: ReverseTutorSemanticColors,
    val surfaces: ReverseTutorSurfaceTokens,
    val text: ReverseTutorTextTokens,
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
        primary = Color(0xFF575CE6),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEEF1FF),
        onPrimaryContainer = Color(0xFF252B3A),
        inversePrimary = Color(0xFFBFC3FF),
        secondary = Color(0xFFF2B544),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFF4DA),
        onSecondaryContainer = Color(0xFF4A3510),
        tertiary = Color(0xFF34B79B),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFEAF7F3),
        onTertiaryContainer = Color(0xFF17443A),
        background = Color(0xFFEEF0F6),
        onBackground = Color(0xFF202637),
        surface = Color(0xFFFCFCFF),
        onSurface = Color(0xFF202637),
        surfaceVariant = Color(0xFFF7F8FC),
        onSurfaceVariant = Color(0xFF687186),
        surfaceTint = Color(0xFF575CE6),
        inverseSurface = Color(0xFF252B3A),
        inverseOnSurface = Color(0xFFFCFCFF),
        error = Color(0xFFC53F3F),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFFC8CDDF),
        outlineVariant = Color(0xFFDDE1ED),
        scrim = Color.Black
    )

    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFBFC3FF),
        onPrimary = Color(0xFF202052),
        primaryContainer = Color(0xFF3C409F),
        onPrimaryContainer = Color(0xFFEEF1FF),
        inversePrimary = Color(0xFF575CE6),
        secondary = Color(0xFFE2C48E),
        onSecondary = Color(0xFF432C00),
        secondaryContainer = Color(0xFF5F4617),
        onSecondaryContainer = Color(0xFFFFDEA6),
        tertiary = Color(0xFF9FCBFA),
        onTertiary = Color(0xFF003354),
        tertiaryContainer = Color(0xFF124A71),
        onTertiaryContainer = Color(0xFFD0E4FF),
        background = Color(0xFF171925),
        onBackground = Color(0xFFE9EAF2),
        surface = Color(0xFF1D1F2C),
        onSurface = Color(0xFFE9EAF2),
        surfaceVariant = Color(0xFF292C3C),
        onSurfaceVariant = Color(0xFFC4C7D5),
        surfaceTint = Color(0xFFBFC3FF),
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
        neutralContainer = Color(0xFFF7F8FC),
        onNeutralContainer = Color(0xFF202637),
        neutralAccent = Color(0xFF7B8394),
        infoContainer = Color(0xFFEEF1FF),
        onInfoContainer = Color(0xFF252B3A),
        infoAccent = Color(0xFF575CE6),
        successContainer = Color(0xFFEAF7F3),
        onSuccessContainer = Color(0xFF17443A),
        successAccent = Color(0xFF34B79B),
        warningContainer = Color(0xFFFFF4DA),
        onWarningContainer = Color(0xFF4A3510),
        warningAccent = Color(0xFFF2B544),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        errorAccent = Color(0xFFBA1A1A),
        disabledContainer = Color(0xFFE9EBF2),
        onDisabledContainer = Color(0xFF7B8394),
        disabledAccent = Color(0xFF9BA2B2)
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

    val lightSurfaceTokens = ReverseTutorSurfaceTokens(
        home = Color(0xFFEEF0F6),
        homeGradientEnd = Color(0xFFE8EDFF),
        card = Color(0xFFFCFCFF),
        cardBorder = Color(0xFFDDE1ED),
        elevated = Color(0xFFFCFCFF),
        elevatedBorder = Color(0xFFDDE1ED),
        input = Color(0xFFF7F8FC),
        inputBorder = Color(0xFFC8CDDF),
        overlay = Color(0xFFFFFFFF)
    )

    val darkSurfaceTokens = ReverseTutorSurfaceTokens(
        home = Color(0xFF171925),
        homeGradientEnd = Color(0xFF1D1F2C),
        card = Color(0xFF1D1F2C),
        cardBorder = Color(0xFF3F4946),
        elevated = Color(0xFF252B3A),
        elevatedBorder = Color(0xFF3F4946),
        input = Color(0xFF292C3C),
        inputBorder = Color(0xFF8B9993),
        overlay = Color(0xFF000000)
    )

    val lightTextTokens = ReverseTutorTextTokens(
        ink = Color(0xFF202637),
        body = Color(0xFF202637),
        muted = Color(0xFF687186),
        highlight = Color(0xFF575CE6),
        onPrimary = Color.White,
        onError = Color.White
    )

    val darkTextTokens = ReverseTutorTextTokens(
        ink = Color(0xFFE9EAF2),
        body = Color(0xFFE9EAF2),
        muted = Color(0xFFC4C7D5),
        highlight = Color(0xFFBFC3FF),
        onPrimary = Color(0xFF202052),
        onError = Color(0xFF690005)
    )

    val typography = Typography(
        displaySmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            lineHeight = 26.sp,
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
            surfaces = if (darkTheme) darkSurfaceTokens else lightSurfaceTokens,
            text = if (darkTheme) darkTextTokens else lightTextTokens,
            typography = typography,
            materialShapes = materialShapes,
            spacing = spacing,
            shapes = shapes,
            elevations = elevations,
            motion = motion
        )
    }
}
