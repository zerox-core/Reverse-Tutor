package com.reversetutor.feature.chat

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPresentationContractsTest {
    @Test
    fun composerKeepsSendButtonInsideStableBounds() {
        assertEquals(60.dp, ChatComposerLayout.Height)
        assertEquals(52.dp, ChatComposerLayout.SendSize)
        assertEquals(4.dp, ChatComposerLayout.Gap)
        assertTrue(ChatComposerLayout.SendSize < ChatComposerLayout.Height)
    }

    @Test
    fun overflowProvidesNonDestructiveSessionActions() {
        assertEquals(
            listOf(
                ChatOverflowAction.SessionSettings,
                ChatOverflowAction.Sources,
                ChatOverflowAction.Export
            ),
            ChatOverflowAction.entries
        )
        assertEquals(
            listOf("会话设置", "资料库", "导出会话"),
            ChatOverflowAction.entries.map(ChatOverflowAction::label)
        )
    }
}
