package com.reversetutor.preview.wiring.session

import com.reversetutor.core.data.background.BackgroundGenerationInput
import com.reversetutor.core.data.background.BackgroundGenerationJob
import com.reversetutor.core.model.BackgroundJobStatus
import com.reversetutor.core.domain.ChapterTransitionPolicy
import com.reversetutor.core.domain.ChapterTransitionProposal
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.PathMove
import com.reversetutor.core.domain.TeachingAction
import com.reversetutor.core.domain.TurnPlan
import com.reversetutor.feature.chat.BackgroundTurnPreparationRequest
import com.reversetutor.feature.chat.BackgroundTurnPreparationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R88 两段式章节卡片：提案回合只暂存不发卡；用户认可回合发卡且
 * 提案一次性消费；教学回复/无提案回合绝不发卡。
 */
class BackgroundTurnPreparationCoordinatorTransitionTest {

    private class MemoryChapterTransitionStore : ChapterTransitionProposalStore {
        val saved = linkedMapOf<String, ChapterTransitionProposal>()
        override fun load(sessionId: String): ChapterTransitionProposal? = saved[sessionId]
        override fun save(sessionId: String, proposal: ChapterTransitionProposal) {
            saved[sessionId] = proposal
        }
        override fun clear(sessionId: String) {
            saved.remove(sessionId)
        }
    }

    private val notices = mutableListOf<String>()

    private fun coordinator(store: ChapterTransitionProposalStore) =
        BackgroundTurnPreparationCoordinator(
            isSessionDeleted = { false },
            assembleContext = { _, _, _ -> ConversationContextContract.empty("space-1", "session-1") },
            enqueueJob = { input, now ->
                BackgroundGenerationJob(
                    id = "job-transition",
                    spaceId = input.spaceId,
                    sessionId = input.sessionId,
                    userMessageId = input.userMessageId,
                    userText = input.userText,
                    token = input.token,
                    modelBindingId = null,
                    status = BackgroundJobStatus.Queued,
                    createdAtEpochMillis = now,
                    startedAtEpochMillis = null,
                    completedAtEpochMillis = null,
                    errorMessage = null,
                    capabilities = null,
                    quoteExcerpt = null,
                    imageAttachments = input.imageAttachments,
                    contextEvidence = input.contextEvidence,
                    sessionPolicy = input.sessionPolicy
                )
            },
            nowEpochMillis = { 1000L },
            chapterTransitionStore = store,
            insertTimelineNotice = { _, _, text -> notices.add(text) }
        )

    private fun request(text: String, turnPlan: TurnPlan? = null) = BackgroundTurnPreparationRequest(
        spaceId = "space-1",
        sessionId = "session-1",
        userMessageId = "msg-1",
        userText = text,
        token = "token-1",
        quoteExcerpt = null,
        imageAttachments = emptyList(),
        sessionSnapshot = null,
        turnPlan = turnPlan
    )

    private fun advancePlan() = TurnPlan(
        actionType = TeachingAction.Practice,
        pathMove = PathMove.Advance,
        pathPosition = 1,
        pathSize = 2,
        pathLabel = "电学"
    )

    private fun armedStore() = MemoryChapterTransitionStore().apply {
        save(
            "session-1",
            ChapterTransitionProposal(
                move = PathMove.Advance,
                fromLabel = "力学",
                toLabel = "电学",
                position = 1,
                size = 2
            )
        )
    }

    @Test
    fun transitionTurnArmsProposalWithoutInsertingCard() = runBlocking {
        notices.clear()
        val store = MemoryChapterTransitionStore()
        val result = coordinator(store).prepareAndEnqueue(request("你已经掌握力学了", advancePlan()))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        val proposal = store.saved["session-1"]
        assertTrue(proposal != null)
        assertEquals(PathMove.Advance, proposal!!.move)
        assertEquals("电学", proposal.toLabel)
        assertTrue(notices.isEmpty())
    }

    @Test
    fun userAffirmationConsumesProposalAndInsertsCard() = runBlocking {
        notices.clear()
        val store = armedStore()
        val result = coordinator(store).prepareAndEnqueue(request("对的，马上进入电学章节吧"))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertEquals(1, notices.size)
        assertTrue(notices.single().startsWith(ChapterTransitionPolicy.CARD_PREFIX))
        assertTrue(notices.single().contains("电学"))
        assertNull(store.saved["session-1"])
    }

    @Test
    fun teachingReplyConsumesProposalWithoutCard() = runBlocking {
        notices.clear()
        val store = armedStore()
        val result = coordinator(store).prepareAndEnqueue(request("这道题用串联的思路再讲一遍"))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertTrue(notices.isEmpty())
        assertNull(store.saved["session-1"])
    }

    @Test
    fun affirmationWithoutPendingProposalInsertsNothing() = runBlocking {
        notices.clear()
        val store = MemoryChapterTransitionStore()
        val result = coordinator(store).prepareAndEnqueue(request("好的"))
        assertTrue(result is BackgroundTurnPreparationResult.Queued)
        assertTrue(notices.isEmpty())
        assertTrue(store.saved.isEmpty())
    }
}