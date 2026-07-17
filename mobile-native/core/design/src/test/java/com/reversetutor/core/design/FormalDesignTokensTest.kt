package com.reversetutor.core.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormalDesignTokensTest {
    @Test
    fun formalPaletteDoesNotReuseObsoletePreviewPurple() {
        assertNotEquals(Color(0xFF575CE6), FormalColors.Primary)
        assertEquals(Color(0xFF2E5CDE), FormalColors.Primary)
        assertEquals(Color(0xFFF4F7FD), FormalColors.Background)
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
}
