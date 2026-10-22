package com.reversetutor.preview.shell

import com.reversetutor.feature.chat.NewSessionConfiguration
import com.reversetutor.feature.chat.NewSessionDraftRecord
import com.reversetutor.feature.chat.NewSessionFavorite
import com.reversetutor.feature.chat.NewSessionPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 2026-10-18 配套门禁：**「进入设置页」不得成为会话获得落盘快照的途径**。
 *
 * 背景：快照是反转家教算法内核的路由键（`RoutingTurnPreparationPort`：有落盘快照 =
 * 应用内创建的会话 → 内核）。若设置页启动时无条件把合成占位快照（画像/目标「未设置」）
 * 回写，则首启欢迎/演示会话、空间导入会话只要被打开过一次设置页，下一回合起就会带着
 * 占位人设静默切进内核——路由分类的前提被自己破坏。
 */
class SessionSettingsStartupSnapshotTest {

    @Test
    fun openingSettingsWithoutStoredSnapshotNeverWritesOne() {
        val persistence = RecordingPersistence()
        var changed = 0

        applyStartupSessionSnapshot(
            persistence = persistence,
            sessionId = "session-legacy",
            storedSnapshot = null,
            appliedSnapshot = NewSessionConfiguration(
                title = "演示会话",
                learnerRole = "学习者",
                learnerProfile = "未设置",
                goal = "未设置"
            ),
            onChanged = { changed++ }
        )

        assertNull(
            "无快照会话不得被设置页写盘——那等于把它改道进内核",
            persistence.loadSessionSnapshot("session-legacy")
        )
        assertEquals(0, persistence.writes)
        assertEquals("刷新通知仍须照发", 1, changed)
    }

    @Test
    fun openingSettingsWithStoredSnapshotKeepsItInSync() {
        val persistence = RecordingPersistence()
        val existing = NewSessionConfiguration(title = "反转家教课堂", goal = "旧目标")
        persistence.saveSessionSnapshot("session-created", existing)
        persistence.writes = 0
        var changed = 0

        applyStartupSessionSnapshot(
            persistence = persistence,
            sessionId = "session-created",
            storedSnapshot = existing,
            appliedSnapshot = existing.copy(goal = "设置页里的最新值"),
            onChanged = { changed++ }
        )

        assertEquals(1, persistence.writes)
        assertEquals(1, changed)
        val stored = persistence.loadSessionSnapshot("session-created")
        assertNotNull(stored)
        assertEquals("设置页里的最新值", stored!!.goal)
    }

    private class RecordingPersistence : NewSessionPersistence {
        private val snapshots = mutableMapOf<String, NewSessionConfiguration>()
        var writes = 0

        override fun loadDrafts(): List<NewSessionDraftRecord> = emptyList()
        override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) = Unit
        override fun loadFavorites(): List<NewSessionFavorite> = emptyList()
        override fun replaceFavorites(favorites: List<NewSessionFavorite>) = Unit
        override fun promoteDraft(
            draftId: String,
            sessionId: String,
            snapshot: NewSessionConfiguration
        ) {
            snapshots[sessionId] = snapshot
        }

        override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? =
            snapshots[sessionId]

        override fun saveSessionSnapshot(sessionId: String, snapshot: NewSessionConfiguration) {
            writes++
            snapshots[sessionId] = snapshot
        }
    }
}
