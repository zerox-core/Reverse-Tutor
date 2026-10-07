package com.reversetutor.preview.wiring

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reversetutor.core.data.DataModule
import com.reversetutor.core.data.session.SessionCreationInput
import com.reversetutor.feature.chat.WelcomeMockLearner
import com.reversetutor.feature.chat.WelcomeMockOpening
import com.reversetutor.feature.chat.WelcomeMockSessionId
import com.reversetutor.feature.chat.WelcomeMockTitle
import com.reversetutor.feature.chat.NewSessionConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositorySessionHomePortAdapterTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sessionRepository = DataModule.sessionRepository(context)
    private val messageRepository = DataModule.messageRepository(context)
    private val deletionRepository = DataModule.sessionDeletionRepository(context)
    private val runRepository = DataModule.conversationRunRepository(context)
    private val persistence = SharedPreferencesSessionHomePersistence(context)
    private val newSessionPersistence = SharedPreferencesNewSessionPersistence(context)
    private var now = 1_000L

    @Before
    fun reset() = runBlocking {
        clearFeatureState()
        DataModule.localDataWipeRepository(context).wipeLocalData(now)
    }

    @After
    fun cleanUp() = runBlocking {
        clearFeatureState()
        DataModule.localDataWipeRepository(context).wipeLocalData(now)
    }

    @Test
    fun freshInstallCreatesExactPersistedWelcomeSessionAndOpening() = runBlocking {
        val cards = adapter().loadSessionCards()

        assertEquals(listOf(WelcomeMockTitle), cards.map { it.title })
        assertEquals(WelcomeMockLearner, cards.single().learnerRole)
        assertEquals(
            WelcomeMockOpening,
            messageRepository.listMessages(WelcomeMockSessionId).single().text
        )
        assertEquals(WelcomeMockTitle, sessionRepository.getSession(WelcomeMockSessionId)?.title)
    }

    @Test
    fun createDeleteUndoReloadAndExplicitMockSuppressionUsePersistedState() = runBlocking {
        val first = adapter()
        first.loadSessionCards()
        assertTrue(first.setAvatarVisible(WelcomeMockSessionId, false))
        assertTrue(first.setPinned(WelcomeMockSessionId, true, now))
        assertTrue(first.stageDelete(WelcomeMockSessionId, now))
        assertEquals(false, persistence.avatarVisible(WelcomeMockSessionId))
        assertEquals(now, persistence.pinnedAt(WelcomeMockSessionId))
        assertTrue(first.undoDelete(WelcomeMockSessionId))

        assertEquals(WelcomeMockTitle, adapter().loadSessionCards().single().title)
        assertEquals(false, persistence.avatarVisible(WelcomeMockSessionId))
        assertEquals(now, persistence.pinnedAt(WelcomeMockSessionId))

        assertTrue(first.stageDelete(WelcomeMockSessionId, now))
        assertTrue(first.commitDelete(WelcomeMockSessionId, now + 5_000L))

        assertTrue(adapter().loadSessionCards().isEmpty())
        assertNull(sessionRepository.getSession(WelcomeMockSessionId))
        assertTrue(persistence.isWelcomeDeletionSuppressed())
        assertNull(persistence.avatarVisible(WelcomeMockSessionId))
        assertNull(persistence.pinnedAt(WelcomeMockSessionId))
    }

    @Test
    fun expiredStagedDeletionFinalizesAfterAdapterReload() = runBlocking {
        val first = adapter()
        first.loadSessionCards()
        assertTrue(first.stageDelete(WelcomeMockSessionId, now))

        now += 5_001L
        val reloadedCards = adapter().loadSessionCards()

        assertTrue(reloadedCards.isEmpty())
        assertNull(sessionRepository.getSession(WelcomeMockSessionId))
        assertTrue(persistence.isWelcomeDeletionSuppressed())
    }

    @Test
    fun avatarVisibilityLoadsFromFeaturePersistence() = runBlocking {
        val first = adapter()
        first.loadSessionCards()
        assertTrue(first.setAvatarVisible(WelcomeMockSessionId, false))

        val card = adapter().loadSessionCards().single()

        assertFalse(card.perSessionAvatarVisible)
    }

    @Test
    fun renamePreservesPersistedPinOrderingWithoutReplacingOtherFields() = runBlocking {
        val first = adapter()
        first.loadSessionCards()
        assertTrue(first.setPinned(WelcomeMockSessionId, true, 2_000L))
        val created = sessionRepository.createSession(
            input = SessionCreationInput(
                title = "Second",
                role = "Learner",
                goal = "Goal",
                profileText = "Profile"
            ),
            nowEpochMillis = 1_500L,
            sessionId = "second"
        )
        assertTrue(first.setPinned(created.session.id, true, 3_000L))

        assertTrue(first.renameSession(created.session.id, "Renamed", 4_000L))
        val cards = adapter().loadSessionCards()

        assertEquals(listOf("Renamed", WelcomeMockTitle), cards.map { it.title })
        assertEquals(3_000L, cards.first().pinnedAtEpochMillis)
        assertTrue(sessionRepository.getSession(created.session.id)?.pinned == true)
        assertEquals(false, sessionRepository.getSession(created.session.id)?.archived)
    }

    @Test
    fun challengeSnapshotReloadsAsBadgedSessionListItem() = runBlocking {
        persistence.completeConfirmedDeletion(WelcomeMockSessionId, suppressWelcome = true)
        val created = sessionRepository.createSession(
            input = SessionCreationInput(
                title = "Challenge session",
                role = "Challenge learner",
                goal = "Goal",
                profileText = "Profile"
            ),
            nowEpochMillis = now,
            sessionId = "challenge-session"
        )
        newSessionPersistence.saveSessionSnapshot(
            created.session.id,
            NewSessionConfiguration(sourceSelections = listOf(" ACTIVITY : ACTIVE-2 "))
        )

        val card = adapter().loadSessionCards().single()

        assertEquals("active-2", card.challengeProvenance?.activityId)
    }

    private fun adapter() = RepositorySessionHomePortAdapter(
        sessionRepository = sessionRepository,
        messageRepository = messageRepository,
        sessionDeletionRepository = deletionRepository,
        conversationRunRepository = runRepository,
        nowEpochMillis = { now },
        persistence = persistence,
        loadSessionSnapshot = newSessionPersistence::loadSessionSnapshot,
        deletionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    )

    private fun clearFeatureState() {
        context.getSharedPreferences("session_home_feature_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences("new_session_feature_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
