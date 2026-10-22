package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 修复 #2 ④⑤（2026-10-15 用户拍板规格）：创建成功后的 3 秒可撤销窗口。
 * 停留 3 秒（倒计时）提供「撤销 / 改草案」；撤销 = 回到草案页并恢复刚才的草案内容；
 * 3 秒内未操作 = 浮层消失、会话保留（进入会话窗口）。纯逻辑 + 注入时钟，UI 只负责渲染与导航。
 */
class AgentCreationCreateWindowTest {

    private class Clock(var now: Long = 1_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    private fun session(id: String = "session-1") = SessionListItem(
        id = id,
        title = "高中物理电磁感应 · 讲学练",
        updatedAtEpochMillis = 1L,
        pinned = false,
        statusLabel = "开场白",
        unreadCount = 0,
        avatarLabel = "高",
        learnerRole = "高三学生"
    )

    private fun draft() = NewSessionConfiguration(
        title = "高中物理电磁感应 · 讲学练",
        learnerRole = "高三学生",
        goal = "高中物理电磁感应",
        persona = "慢热但较真"
    )

    // (f) 撤销 → 回到草案页并恢复刚才的草案内容（草案不丢）。
    @Test
    fun undoRestoresTheDraftContentWithinTheWindow() {
        val clock = Clock()
        val window = AgentCreationCreateWindow(nowMillis = clock)
        val original = draft()
        window.open(session(), original)

        clock.now += 1_500L
        val decision = window.undo()

        assertTrue(decision is AgentCreationCreateWindow.Decision.RestoreDraft)
        val restore = decision as AgentCreationCreateWindow.Decision.RestoreDraft
        assertEquals(AgentCreationCreateWindow.RestoreReason.Undo, restore.reason)
        assertEquals(original, restore.draft)
        assertEquals("session-1", restore.session.id)
        assertFalse(window.active)
        assertNull(window.undo())
    }

    // (f) 改草案：会话保留，同样把草案内容交回草案页。
    @Test
    fun editDraftKeepsSessionAndHandsBackTheDraft() {
        val clock = Clock()
        val window = AgentCreationCreateWindow(nowMillis = clock)
        val original = draft()
        window.open(session(), original)

        val decision = window.editDraft()
        assertTrue(decision is AgentCreationCreateWindow.Decision.RestoreDraft)
        val restore = decision as AgentCreationCreateWindow.Decision.RestoreDraft
        assertEquals(AgentCreationCreateWindow.RestoreReason.EditDraft, restore.reason)
        assertEquals(original, restore.draft)
    }

    // 2026-10-17 用户拍板居中过渡弹窗：点「确定进入会话」主动确认保留会话
    @Test
    fun confirmKeepClosesWindowAndKeepsSession() {
        val clock = Clock()
        val window = AgentCreationCreateWindow(nowMillis = clock)
        window.open(session(), draft())

        val decision = window.confirmKeep()
        assertTrue(decision is AgentCreationCreateWindow.Decision.KeepSession)
        assertEquals("session-1", decision?.session?.id)
        assertFalse(window.active)
    }

    // (g) 3 秒过期后不再提供撤销，会话保留（进入会话窗口）。
    @Test
    fun expiredWindowNoLongerOffersUndoAndKeepsTheSession() {
        val clock = Clock()
        val window = AgentCreationCreateWindow(nowMillis = clock)
        window.open(session(), draft())

        assertEquals(3_000L, window.remainingMillis())
        clock.now += 2_999L
        assertFalse(window.expired())
        assertEquals(1L, window.remainingMillis())
        assertNull(window.expireIfDue())

        clock.now += 1L
        assertTrue(window.expired())
        // 过期后不再提供撤销 / 改草案
        assertNull(window.undo())
        assertNull(window.editDraft())

        val decision = window.expireIfDue()
        assertTrue(decision is AgentCreationCreateWindow.Decision.KeepSession)
        assertEquals("session-1", (decision as AgentCreationCreateWindow.Decision.KeepSession).session.id)
        assertFalse(window.active)
        assertEquals(0L, window.remainingMillis())
        // 窗口已关闭，重复到期不再产生决定
        assertNull(window.expireIfDue())
    }

    // 窗口内的草案是快照：之后协调器继续改草案，撤销恢复的仍是创建那一刻的内容。
    @Test
    fun openedWindowSnapshotIsNotAffectedByLaterDraftEdits() {
        val clock = Clock()
        val window = AgentCreationCreateWindow(nowMillis = clock)
        val created = window.open(session(), draft())

        val restore = window.undo() as AgentCreationCreateWindow.Decision.RestoreDraft

        assertEquals(created.draft, restore.draft)
        assertEquals("慢热但较真", restore.draft.persona)
    }

    @Test
    fun defaultWindowDurationIsThreeSeconds() {
        assertEquals(3_000L, AgentCreationCreateWindow.DEFAULT_DURATION_MILLIS)
        val clock = Clock()
        val window = AgentCreationCreateWindow(nowMillis = clock)
        window.open(session(), draft())

        clock.now += 3_000L

        assertTrue(window.expired())
    }
}
