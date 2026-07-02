package com.reversetutor.preview.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReverseTutorTokenTest {
    @Test
    fun spacingTokensFollowFourDpScaleAndTouchTargetMinimum() {
        val spacing = ReverseTutorThemeTokens.spacing

        assertEquals(4f, spacing.space1.value, 0f)
        assertEquals(8f, spacing.space2.value, 0f)
        assertEquals(12f, spacing.space3.value, 0f)
        assertEquals(16f, spacing.space4.value, 0f)
        assertEquals(20f, spacing.space5.value, 0f)
        assertEquals(24f, spacing.space6.value, 0f)
        assertEquals(32f, spacing.space8.value, 0f)
        assertEquals(48f, spacing.minTouchTarget.value, 0f)
    }

    @Test
    fun shapeTokensKeepCardsAtEightDpOrLess() {
        val shapes = ReverseTutorThemeTokens.shapes

        assertEquals(4f, shapes.radiusSmall.value, 0f)
        assertEquals(6f, shapes.radiusMedium.value, 0f)
        assertEquals(8f, shapes.radiusCard.value, 0f)
        assertEquals(16f, shapes.radiusSheet.value, 0f)
    }

    @Test
    fun lightAndDarkColorSchemesUseExplicitProductPalettes() {
        val light = ReverseTutorThemeTokens.lightColorScheme
        val dark = ReverseTutorThemeTokens.darkColorScheme

        assertEquals(Color(0xFF1E6B5E), light.primary)
        assertEquals(Color(0xFFF7FAF8), light.surface)
        assertEquals(Color(0xFF9FD8CC), dark.primary)
        assertEquals(Color(0xFF101513), dark.surface)
        assertNotEquals(light.surface, dark.surface)
        assertNotEquals(light.primaryContainer, dark.primaryContainer)
    }

    @Test
    fun semanticColorsCoverEveryReusableStatusTone() {
        val semanticColors = ReverseTutorThemeTokens.lightSemanticColors

        ReverseTutorStatusTone.entries.forEach { tone ->
            val colors = semanticColors.forTone(tone)

            assertNotEquals(Color.Unspecified, colors.container)
            assertNotEquals(Color.Unspecified, colors.content)
            assertNotEquals(Color.Unspecified, colors.accent)
        }

        assertEquals(
            semanticColors.successContainer,
            semanticColors.forTone(ReverseTutorStatusTone.ParserSupported).container
        )
        assertEquals(
            semanticColors.warningContainer,
            semanticColors.forTone(ReverseTutorStatusTone.ParserPartial).container
        )
        assertEquals(
            semanticColors.infoContainer,
            semanticColors.forTone(ReverseTutorStatusTone.GenerationActive).container
        )
    }
}
