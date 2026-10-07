package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSessionPrefillTest {
    @Test
    fun acceptingChallengePrefillDoesNotCreateASessionUntilExplicitConfirmation() = runBlocking {
        var createCalls = 0
        val coordinator = NewSessionLifecycleCoordinator(
            persistence = PrefillTestPersistence(),
            createPort = NewSessionCreatePort {
                createCalls += 1
                error("Creation must require explicit confirmation")
            },
            nowEpochMillis = { 10L },
            idFactory = { "id" }
        )
        coordinator.load()

        val accepted = coordinator.startPrefilledDraft(
            NewSessionPrefillRequest(
                requestId = "challenge-a-1",
                configuration = NewSessionConfiguration(
                    title = "挑战会话",
                    learnerRole = "挑战学习伙伴",
                    sourceSelections = listOf("activity:a")
                )
            )
        )

        assertTrue(accepted)
        assertEquals(NewSessionHubTab.Custom, coordinator.state.tab)
        assertEquals(listOf("activity:a"), coordinator.state.currentDraft?.configuration?.sourceSelections)
        assertEquals(0, createCalls)
    }
}

private class PrefillTestPersistence : NewSessionPersistence {
    override fun loadDrafts(): List<NewSessionDraftRecord> = emptyList()
    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) = Unit
    override fun loadFavorites(): List<NewSessionFavorite> = emptyList()
    override fun replaceFavorites(favorites: List<NewSessionFavorite>) = Unit
    override fun promoteDraft(
        draftId: String,
        sessionId: String,
        snapshot: NewSessionConfiguration
    ) = Unit

    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? = null
}
