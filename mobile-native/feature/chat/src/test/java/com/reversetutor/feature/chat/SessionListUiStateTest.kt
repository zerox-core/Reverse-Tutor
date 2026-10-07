package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionListUiStateTest {
    @Test
    fun visibleSessionsApplySearchAndPinnedFirstOrdering() {
        val state = SessionListUiState.from(
            sessions = listOf(
                item(id = "old", title = "Algebra review", updatedAt = 10L),
                item(id = "fresh", title = "Geometry", updatedAt = 30L),
                item(id = "pinned", title = "Pinned algebra", updatedAt = 20L, pinned = true)
            ),
            query = "algebra",
            filter = SessionListFilter.All,
            avatarVisible = true
        )

        assertEquals(listOf("pinned", "old"), state.visibleSessions.map { it.id })
        assertEquals("2 个会话", state.summary)
    }

    @Test
    fun pinnedFilterShowsOnlyPinnedSessions() {
        val state = SessionListUiState.from(
            sessions = listOf(
                item(id = "one", title = "One", updatedAt = 1L),
                item(id = "two", title = "Two", updatedAt = 2L, pinned = true)
            ),
            query = "",
            filter = SessionListFilter.Pinned,
            avatarVisible = false
        )

        assertEquals(listOf("two"), state.visibleSessions.map { it.id })
        assertEquals("", state.visibleSessions.single().avatarLabel)
    }

    @Test
    fun emptySearchResultExplainsThatNoSessionMatches() {
        val state = SessionListUiState.from(
            sessions = listOf(item(id = "one", title = "One", updatedAt = 1L)),
            query = "missing",
            filter = SessionListFilter.All,
            avatarVisible = true
        )

        assertTrue(state.visibleSessions.isEmpty())
        assertEquals("没有匹配的会话", state.emptyStateTitle)
    }

    private fun item(
        id: String,
        title: String,
        updatedAt: Long,
        pinned: Boolean = false
    ): SessionListItem = SessionListItem(
        id = id,
        title = title,
        updatedAtEpochMillis = updatedAt,
        pinned = pinned,
        statusLabel = "Proactive deferred",
        unreadCount = 0,
        avatarLabel = "Avatar"
    )
}
