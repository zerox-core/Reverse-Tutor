@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.reversetutor.feature.chat

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionHomeViewModelTest {
    @Test
    fun cardLayoutAndActionContractStayStable() {
        assertEquals(80f, HomeSessionLayout.RegularHeight.value)
        assertEquals(92f, HomeSessionLayout.PinnedHeight.value)
        assertEquals(
            listOf(
                HomeSessionAction.Rename,
                HomeSessionAction.Pin,
                HomeSessionAction.Export,
                HomeSessionAction.Delete
            ),
            HomeSessionAction.entries
        )
    }

    @Test
    fun titleValidationAllowsDuplicatesButRequiresOneToThirtyNonWhitespaceCharacters() {
        assertEquals("会话名称不能为空", validateSessionTitle("   "))
        assertNull(validateSessionTitle("同名会话"))
        assertNull(validateSessionTitle("同名会话"))
        assertNull(validateSessionTitle("a".repeat(30)))
        assertNull(validateSessionTitle("  ${"a".repeat(30)}  "))
        assertEquals("会话名称最多 30 个字符", validateSessionTitle("a".repeat(31)))
    }

    @Test
    fun pinnedSessionsUseLatestPinTimeAndGlobalAvatarHiddenRemovesAvatarContent() {
        val items = listOf(
            item("unpinned", updatedAt = 999, pinned = false),
            item("pin-old", updatedAt = 500, pinned = true, pinnedAt = 10),
            item("pin-new", updatedAt = 100, pinned = true, pinnedAt = 20)
        )

        assertEquals(listOf("pin-new", "pin-old", "unpinned"), items.sortedForHome().map { it.id })

        val hidden = SessionListUiState.from(
            sessions = items,
            query = "",
            filter = SessionListFilter.All,
            avatarVisible = false
        )
        assertTrue(hidden.visibleSessions.all { it.avatarLabel.isEmpty() })
        assertTrue(hidden.visibleSessions.all { !it.perSessionAvatarVisible })
    }

    @Test
    fun perSessionAvatarHiddenAlsoRemovesAvatarContentWhileKeepingText() {
        val state = SessionListUiState.from(
            sessions = listOf(item("one").copy(perSessionAvatarVisible = false)),
            query = "",
            filter = SessionListFilter.All,
            avatarVisible = true
        )

        assertEquals("one", state.visibleSessions.single().title)
        assertEquals("", state.visibleSessions.single().avatarLabel)
    }

    @Test
    fun renameAndPinActionsPersistAndUpdateOrdering() = runTest {
        val port = FakeSessionHomePort(
            cards = mutableListOf(item("one", updatedAt = 100), item("two", updatedAt = 200))
        )
        var now = 1_000L
        val viewModel = SessionHomeViewModel(port, this, nowEpochMillis = { now })
        advanceUntilIdle()

        viewModel.rename("one", "  同名会话  ")
        advanceUntilIdle()
        assertEquals("同名会话", port.renamedTitles.single().second)

        now = 2_000L
        viewModel.togglePinned("one")
        advanceUntilIdle()
        assertEquals("one", viewModel.uiState.value.sessions.first().id)
        assertEquals(2_000L, viewModel.uiState.value.sessions.first().pinnedAtEpochMillis)
    }

    @Test
    fun deleteStagesImmediatelyAndUndoWithinFiveSecondsRestoresFullCard() = runTest {
        assertEquals("欢迎来到反转家教", WelcomeMockTitle)
        assertEquals("小六子", WelcomeMockLearner)
        assertEquals("老师老师，第一节课我来教你，以后你就要好好来教我啦。", WelcomeMockOpening)
        val welcome = item(WelcomeMockSessionId).copy(
            title = WelcomeMockTitle,
            learnerRole = WelcomeMockLearner,
            latestMessageSummary = WelcomeMockOpening,
            isWelcomeMock = true
        )
        val port = FakeSessionHomePort(cards = mutableListOf(welcome))
        val viewModel = SessionHomeViewModel(port, this, nowEpochMillis = { testScheduler.currentTime })
        advanceUntilIdle()

        viewModel.requestDelete(WelcomeMockSessionId)
        viewModel.confirmDelete()
        runCurrent()
        assertEquals(listOf(WelcomeMockSessionId), port.staged)
        assertTrue(viewModel.uiState.value.sessions.isEmpty())

        advanceTimeBy(4_999)
        viewModel.undoDelete()
        advanceUntilIdle()

        assertEquals(welcome, viewModel.uiState.value.sessions.single())
        assertEquals(listOf(WelcomeMockSessionId), port.restored)
        assertTrue(port.committed.isEmpty())
    }

    @Test
    fun deleteCommitsAfterFiveSecondsAndSuppressesMockRecreation() = runTest {
        val port = FakeSessionHomePort(cards = mutableListOf(item(WelcomeMockSessionId)))
        val viewModel = SessionHomeViewModel(port, this, nowEpochMillis = { testScheduler.currentTime })
        advanceUntilIdle()

        viewModel.requestDelete(WelcomeMockSessionId)
        viewModel.confirmDelete()
        runCurrent()
        advanceTimeBy(SessionDeleteUndoMillis)
        runCurrent()

        assertEquals(listOf(WelcomeMockSessionId), port.committed)
        assertFalse(shouldCreateWelcomeSession(sessionExists = false, deletionTombstoneExists = true))
        assertFalse(
            shouldCreateWelcomeSession(
                sessionExists = false,
                deletionTombstoneExists = false,
                hasOtherSessions = true
            )
        )
        assertTrue(shouldCreateWelcomeSession(sessionExists = false, deletionTombstoneExists = false))
    }

    private fun item(
        id: String,
        updatedAt: Long = 1,
        pinned: Boolean = false,
        pinnedAt: Long? = null
    ) = SessionListItem(
        id = id,
        title = id,
        updatedAtEpochMillis = updatedAt,
        pinned = pinned,
        statusLabel = "latest",
        unreadCount = 0,
        avatarLabel = id.take(1),
        learnerRole = "learner",
        latestMessageSummary = "latest",
        pinnedAtEpochMillis = pinnedAt
    )
}

private class FakeSessionHomePort(
    val cards: MutableList<SessionListItem>
) : SessionHomePort {
    val renamedTitles = mutableListOf<Pair<String, String>>()
    val staged = mutableListOf<String>()
    val restored = mutableListOf<String>()
    val committed = mutableListOf<String>()

    override suspend fun loadSessionCards(): List<SessionListItem> = cards.toList()

    override suspend fun renameSession(
        sessionId: String,
        title: String,
        nowEpochMillis: Long
    ): Boolean {
        renamedTitles += sessionId to title
        return true
    }

    override suspend fun setPinned(
        sessionId: String,
        pinned: Boolean,
        nowEpochMillis: Long
    ): Boolean = true

    override suspend fun stageDelete(sessionId: String, nowEpochMillis: Long): Boolean {
        staged += sessionId
        return true
    }

    override suspend fun undoDelete(sessionId: String): Boolean {
        restored += sessionId
        return true
    }

    override suspend fun commitDelete(sessionId: String, nowEpochMillis: Long): Boolean {
        committed += sessionId
        return true
    }
}
