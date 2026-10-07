package com.reversetutor.preview.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme

val LocalReverseTutorTokens = staticCompositionLocalOf {
    ReverseTutorThemeTokens.designTokens(darkTheme = false)
}

@Composable
fun ReverseTutorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = ReverseTutorThemeTokens.designTokens(darkTheme)
    CompositionLocalProvider(LocalReverseTutorTokens provides tokens) {
        MaterialTheme(
            colorScheme = tokens.colorScheme,
            typography = tokens.typography,
            shapes = tokens.materialShapes,
            content = content
        )
    }
}

object ReverseTutorDesign {
    val tokens: ReverseTutorDesignTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalReverseTutorTokens.current

    val spacing: ReverseTutorSpacingTokens
        @Composable
        @ReadOnlyComposable
        get() = tokens.spacing

    val shapes: ReverseTutorShapeTokens
        @Composable
        @ReadOnlyComposable
        get() = tokens.shapes

    val elevations: ReverseTutorElevationTokens
        @Composable
        @ReadOnlyComposable
        get() = tokens.elevations

    val semanticColors: ReverseTutorSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = tokens.semanticColors

    val surfaces: ReverseTutorSurfaceTokens
        @Composable
        @ReadOnlyComposable
        get() = tokens.surfaces

    val text: ReverseTutorTextTokens
        @Composable
        @ReadOnlyComposable
        get() = tokens.text
}
