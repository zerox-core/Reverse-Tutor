package com.reversetutor.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** R100 方案B 状态机：合法转移轨道 + 非法转移抛错 + 恢复 + 修订重开。 */
class AgentCreationGraphTest {

    @Test
    fun happyPathWalksAllStates() {
        val graph = AgentCreationGraph()
        assertEquals(CreationState.CollectGoal, graph.state)
        graph.noteUserSpoke()
        assertEquals(CreationState.Interview, graph.state)
        graph.transitionTo(CreationState.Interview) // 访谈循环
        graph.transitionTo(CreationState.DraftProposal)
        graph.transitionTo(CreationState.ConfirmDraft)
        graph.transitionTo(CreationState.GenerateProfile)
        graph.transitionTo(CreationState.GenerateLearningFlow)
        graph.transitionTo(CreationState.Done)
        assertEquals(CreationState.Done, graph.state)
    }

    @Test
    fun annotationReturnsToInterviewAndReproposes() {
        val graph = AgentCreationGraph()
        graph.noteUserSpoke()
        graph.transitionTo(CreationState.DraftProposal)
        graph.transitionTo(CreationState.ConfirmDraft)
        // 批注打回访谈
        graph.transitionTo(CreationState.Interview)
        // 再次提案
        graph.transitionTo(CreationState.DraftProposal)
        graph.transitionTo(CreationState.ConfirmDraft)
        assertEquals(CreationState.ConfirmDraft, graph.state)
    }

    @Test
    fun illegalTransitionThrows() {
        val graph = AgentCreationGraph()
        try {
            graph.transitionTo(CreationState.ConfirmDraft)
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // 预期抛错
        }
        assertEquals(CreationState.CollectGoal, graph.state)
    }

    @Test
    fun doneReopensForRevision() {
        val graph = AgentCreationGraph()
        graph.noteUserSpoke()
        graph.transitionTo(CreationState.DraftProposal)
        graph.transitionTo(CreationState.ConfirmDraft)
        graph.transitionTo(CreationState.GenerateProfile)
        graph.transitionTo(CreationState.GenerateLearningFlow)
        graph.transitionTo(CreationState.Done)
        graph.reopenForRevision()
        assertEquals(CreationState.Interview, graph.state)
    }

    @Test
    fun snapshotRestoreFallsBackToInterviewTrack() {
        val empty = AgentCreationGraph()
        empty.restoreFromSnapshot(hasConversation = false)
        assertEquals(CreationState.CollectGoal, empty.state)
        val withHistory = AgentCreationGraph()
        withHistory.restoreFromSnapshot(hasConversation = true)
        assertEquals(CreationState.Interview, withHistory.state)
    }
}
