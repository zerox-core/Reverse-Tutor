package com.reversetutor.preview.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class ReverseTutorTokensTest {
    @Test
    fun lightThemeMatchesFigmaHandoff() {
        val tokens = ReverseTutorThemeTokens.designTokens(darkTheme = false)

        assertEquals(Color(0xFF575CE6), tokens.colorScheme.primary)
        assertEquals(Color(0xFFEEF0F6), tokens.colorScheme.background)
        assertEquals(Color(0xFFFCFCFF), tokens.colorScheme.surface)
        assertEquals(18.dp, tokens.shapes.radiusCard)
        assertEquals(16.dp, tokens.spacing.space4)
        assertEquals(44.dp, tokens.spacing.minTouchTarget)
        assertEquals(430.dp, tokens.spacing.contentMaxWidth)

        assertEquals(Color(0xFFFCFCFF), tokens.surfaces.card)
        assertEquals(Color(0xFFDDE1ED), tokens.surfaces.cardBorder)
        assertEquals(Color(0xFF202637), tokens.text.ink)
        assertEquals(Color(0xFF687186), tokens.text.muted)
        assertEquals(Color(0xFF575CE6), tokens.text.highlight)
    }

    @Test
    fun darkThemeSurfacesAndTextFlip() {
        val tokens = ReverseTutorThemeTokens.designTokens(darkTheme = true)

        assertEquals(Color(0xFF1D1F2C), tokens.surfaces.card)
        assertEquals(Color(0xFF3F4946), tokens.surfaces.cardBorder)
        assertEquals(Color(0xFFE9EAF2), tokens.text.ink)
        assertEquals(Color(0xFFC4C7D5), tokens.text.muted)
        assertEquals(Color(0xFFBFC3FF), tokens.text.highlight)
    }
}
