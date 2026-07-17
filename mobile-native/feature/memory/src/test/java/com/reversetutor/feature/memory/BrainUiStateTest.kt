package com.reversetutor.feature.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainUiStateTest {
    @Test
    fun formalBrainStateDoesNotInjectPreviewLearningData() {
        val state = BrainUiState.preview()

        assertTrue(state.reviews.isEmpty())
        assertTrue(state.memories.isEmpty())
        assertEquals(GraphScope.Global, state.graphState.scope)
        assertEquals(GraphRenderStatus.Empty, state.graphState.status)
    }
}
