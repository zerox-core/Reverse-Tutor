package com.reversetutor.feature.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChallengeSessionProvenanceTest {
    @Test
    fun canonicalMarkerDerivesFeatureOwnedProvenance() {
        val configuration = NewSessionConfiguration(
            sourceSelections = listOf("source:notes", " ACTIVITY : Python-21 ")
        )

        val provenance = configuration.challengeSessionProvenance()

        assertEquals("python-21", provenance?.activityId)
        assertEquals("activity:python-21", provenance?.canonicalSource)
        assertNull(
            NewSessionConfiguration(sourceSelections = listOf("source:notes"))
                .challengeSessionProvenance()
        )
    }

    @Test
    fun successfulCreateCarriesChallengeBadgeProvenanceFromPromotedSnapshot() = runBlocking {
        val persistence = CapturingChallengePersistence()
        val coordinator = NewSessionLifecycleCoordinator(
            persistence = persistence,
            createPort = NewSessionCreatePort { request ->
                NewSessionCreated(
                    session = SessionListItem(
                        id = "session-a",
                        title = request.snapshot.title,
                        updatedAtEpochMillis = 10L,
                        pinned = false,
                        statusLabel = "Ready",
                        unreadCount = 0,
                        avatarLabel = "C"
                    ),
                    learnerRole = request.snapshot.learnerRole,
                    openingMessage = request.snapshot.openingMessage
                )
            },
            nowEpochMillis = { 10L },
            idFactory = { "challenge-create" }
        )
        coordinator.load()
        coordinator.startPrefilledDraft(
            NewSessionPrefillRequest(
                requestId = "challenge-a",
                configuration = NewSessionConfiguration(
                    title = "Challenge session",
                    learnerRole = "Challenge learner",
                    sourceSelections = listOf("activity:a")
                )
            )
        )

        val outcome = coordinator.createSession() as CreateSessionOutcome.Success

        assertEquals("a", outcome.created.session.challengeProvenance?.activityId)
        assertNotNull(persistence.snapshot?.challengeSessionProvenance())
    }
}

private class CapturingChallengePersistence : NewSessionPersistence {
    var snapshot: NewSessionConfiguration? = null

    override fun loadDrafts(): List<NewSessionDraftRecord> = emptyList()
    override fun replaceDrafts(drafts: List<NewSessionDraftRecord>) = Unit
    override fun loadFavorites(): List<NewSessionFavorite> = emptyList()
    override fun replaceFavorites(favorites: List<NewSessionFavorite>) = Unit
    override fun promoteDraft(
        draftId: String,
        sessionId: String,
        snapshot: NewSessionConfiguration
    ) {
        this.snapshot = snapshot
    }

    override fun loadSessionSnapshot(sessionId: String): NewSessionConfiguration? = snapshot
}
