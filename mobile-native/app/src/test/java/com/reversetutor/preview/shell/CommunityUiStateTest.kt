package com.reversetutor.preview.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommunityUiStateTest {
    @Test
    fun formalCommunityIsAStaticFeasibilityPlaceholder() {
        val state = CommunityUiState.formal()

        assertEquals("社区暂未开放", state.title)
        assertEquals("正在进行可行性评估", state.statusLabel)
        assertFalse(state.body.contains("开发中"))
    }
}
