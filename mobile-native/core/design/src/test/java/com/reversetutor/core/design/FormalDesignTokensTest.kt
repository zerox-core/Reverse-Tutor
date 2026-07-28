package com.reversetutor.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalDesignTokensTest {
    @Test
    fun formalPaletteUsesNeutralGroupedSurfacesWithoutObsoletePreviewPurple() {
        assertNotEquals(Color(0xFF575CE6), FormalColors.Primary)
        assertEquals(Color(0xFF2E5CDE), FormalColors.Primary)
        assertEquals(Color(0xFFF2F2F7), FormalColors.Background)
        assertEquals(Color.White, FormalColors.Surface)
        assertEquals(Color(0xFFF7F7FA), FormalColors.SurfaceSubtle)
        assertEquals(Color(0xFF1C1C1E), FormalColors.Ink)
        assertEquals(Color(0xFF6E6E73), FormalColors.Muted)
        assertEquals(Color(0xFF8E8E93), FormalColors.Tertiary)
        assertEquals(Color(0xFFD1D1D6), FormalColors.Border)
    }

    @Test
    fun formalCardsStayWithinEightDpRadius() {
        assertTrue(FormalShapes.CardRadius <= 8.dp)
        assertTrue(FormalShapes.CompactRadius <= FormalShapes.CardRadius)
    }

    @Test
    fun typographyScaleKeepsAStableGlobalAdjustmentInterface() {
        assertEquals(16f, FormalTypeScale().size(16f).value)
        assertEquals(17.6f, FormalTypeScale(1.1f).size(16f).value, 0.001f)
    }

    @Test
    fun semanticTypographyDefinesStableHierarchyWithoutForcingAFontFamily() {
        val scale = FormalTypeScale()

        assertEquals(28.sp, FormalTypography.pageTitle(scale).fontSize)
        assertEquals(FontWeight.Bold, FormalTypography.pageTitle(scale).fontWeight)
        assertEquals(20.sp, FormalTypography.sectionTitle(scale).fontSize)
        assertEquals(16.sp, FormalTypography.cardTitle(scale).fontSize)
        assertEquals(14.sp, FormalTypography.body(scale).fontSize)
        assertEquals(12.sp, FormalTypography.metadata(scale).fontSize)
        assertEquals(11.sp, FormalTypography.status(scale).fontSize)
        assertEquals(14.sp, FormalTypography.control(scale).fontSize)
        assertEquals(0.sp, FormalTypography.body(scale).letterSpacing)
        assertEquals(null, FormalTypography.body(scale).fontFamily)
    }

    @Test
    fun panelsHaveVisibleBordersAndThreeRestrainedDepthLevels() {
        assertNotEquals(FormalColors.Border, FormalColors.BorderStrong)
        assertTrue(FormalElevations.Panel > 0.dp)
        assertTrue(FormalElevations.Raised > FormalElevations.Panel)
        assertTrue(FormalElevations.Sheet > FormalElevations.Raised)
    }
}
