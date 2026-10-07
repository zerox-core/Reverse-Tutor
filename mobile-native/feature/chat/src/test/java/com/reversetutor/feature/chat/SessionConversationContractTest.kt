package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract
import com.reversetutor.core.domain.SessionTurnContracts
import com.reversetutor.core.domain.SessionTurnResult
import com.reversetutor.core.domain.SessionPolicyOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [SessionConversationContract] completeness and redaction.
 * Verifies that the contract contains all required fields and never
 * exposes sensitive data (sk-, Authorization, Bearer, Provider URL).
 */
class SessionConversationContractTest {

    private val facade = SessionConversationFacade()

    private fun emptyContext() = ConversationContextContract.empty("s1", "se1")

    // --- Contract completeness ---------------------------------------------

    @Test
    fun successContractContainsAllFields() {
        val result = SessionTurnResult.Success(
            assistantMessageId = "asst1",
            assistantText = "Let's think about this step by step",
            evaluation = SessionEvaluationContract(correctness = 0.5f),
            action = SessionActionContract(type = "probe", knowledgePoint = "导数"),
            context = emptyContext(),
            processSummary = "探测理解：导数"
        )
        val contract = facade.mapResult(result, "se1", "t1")

        assertEquals("se1", contract.sessionId)
        assertEquals("t1", contract.turnId)
        assertEquals(GenerationState.READY, contract.generation.state)
        assertEquals("asst1", contract.generation.assistantMessageId)
        assertEquals("Let's think about this step by step", contract.generation.assistantText)
        assertNotNull(contract.evaluation)
        assertNotNull(contract.action)
        assertEquals("probe", contract.action!!.type)
        assertEquals("探测理解：导数", contract.processSummary)
        assertEquals("导数", contract.currentKnowledgePoint)
        assertNotNull(contract.nextStep)
        assertEquals("导数", contract.nextStep!!.knowledgePoint)
        assertNotNull(contract.context)
        assertTrue(contract.events.isNotEmpty())
    }

    @Test
    fun noModelContractHasCorrectState() {
        val result = SessionTurnResult.NoModel(emptyContext(), "no model")
        val contract = facade.mapResult(result, "se1", "t1")

        assertEquals(GenerationState.NO_MODEL, contract.generation.state)
        assertEquals("no model", contract.processSummary)
        assertNull(contract.evaluation)
        assertNull(contract.action)
    }

    @Test
    fun providerErrorContractHasSafeError() {
        val result = SessionTurnResult.ProviderError("Authorization: Bearer sk-secret https://provider.example", emptyContext())
        val contract = facade.mapResult(result, "se1", "t1")

        assertEquals(GenerationState.ERROR, contract.generation.state)
        assertEquals(SessionTurnContracts.SAFE_GENERATION_FAILURE, contract.generation.safeError)
    }

    @Test
    fun staleTokenContractHasSafeError() {
        val result = SessionTurnResult.StaleToken(emptyContext())
        val contract = facade.mapResult(result, "se1", "t1")

        assertEquals(GenerationState.ERROR, contract.generation.state)
        assertNotNull(contract.generation.safeError)
    }

    @Test
    fun sessionDeletedContractHasSafeError() {
        val result = SessionTurnResult.SessionDeleted(emptyContext())
        val contract = facade.mapResult(result, "se1", "t1")

        assertEquals(GenerationState.ERROR, contract.generation.state)
        assertNotNull(contract.generation.safeError)
    }

    @Test
    fun discardedContractHasReason() {
        val result = SessionTurnResult.Discarded("duplicate turn", emptyContext())
        val contract = facade.mapResult(result, "se1", "t1")

        assertEquals(GenerationState.IDLE, contract.generation.state)
        assertEquals("duplicate turn", contract.processSummary)
    }

    @Test
    fun eventsHaveStableIds() {
        val result = SessionTurnResult.Success(
            assistantMessageId = "a1",
            assistantText = "text",
            evaluation = SessionEvaluationContract(),
            action = SessionActionContract(),
            context = emptyContext(),
            processSummary = "summary"
        )
        val contract = facade.mapResult(result, "se1", "t1")

        assertTrue(contract.events.any { it.eventId == "retry_t1" })
        assertTrue(contract.events.any { it.eventId == "open_ctx_t1" })
    }

    @Test
    fun emptyContractHasCorrectDefaults() {
        val empty = SessionConversationContract.empty("se1")
        assertEquals("se1", empty.sessionId)
        assertNull(empty.turnId)
        assertEquals(GenerationState.IDLE, empty.generation.state)
        assertNull(empty.evaluation)
        assertNull(empty.action)
        assertNull(empty.processSummary)
    }

    // --- Redaction tests ----------------------------------------------------

    @Test
    fun contractFieldsDoNotContainSecrets() {
        val result = SessionTurnResult.Success(
            assistantMessageId = "asst1",
            assistantText = "normal response text",
            evaluation = SessionEvaluationContract(),
            action = SessionActionContract(),
            context = emptyContext(),
            processSummary = "summary"
        )
        val contract = facade.mapResult(result, "se1", "t1")

        val allStrings = buildList {
            add(contract.sessionId)
            contract.turnId?.let { add(it) }
            contract.generation.assistantMessageId?.let { add(it) }
            contract.generation.assistantText?.let { add(it) }
            contract.generation.safeError?.let { add(it) }
            contract.processSummary?.let { add(it) }
            contract.currentKnowledgePoint?.let { add(it) }
            contract.nextStep?.let { addAll(listOf(it.hint, it.knowledgePoint, it.actionType)) }
            contract.events.forEach { e -> e.payload?.let { add(it) } }
        }

        for (s in allStrings) {
            assertFalse("no sk-: $s", s.contains("sk-"))
            assertFalse("no Authorization: $s", s.contains("Authorization"))
            assertFalse("no Bearer: $s", s.contains("Bearer"))
            assertFalse("no http key url: $s", s.contains("api.openai"))
            assertFalse("no secret key pattern: $s", s.contains("secret"))
        }
    }

    @Test
    fun providerErrorDoesNotLeakRawException() {
        val result = SessionTurnResult.ProviderError("com.reversetutor.ProviderException: Authorization: Bearer sk-secret", emptyContext())
        val contract = facade.mapResult(result, "se1", "t1")

        val err = contract.generation.safeError ?: ""
        assertFalse("no stacktrace", err.contains("Exception"))
        assertFalse("no stacktrace", err.contains("StackTrace"))
        assertFalse("no class path", err.contains("com.reversetutor"))
        assertFalse("no sk-", err.contains("sk-"))
    }

    private fun policyOutput(type: String) = SessionPolicyOutput(
        evaluation = SessionEvaluationContract(correctness = 0.5f),
        action = SessionActionContract(type = type, knowledgePoint = "导数"),
        processSummary = "探测理解：导数",
        correctionTiming = "即时",
        normalizationWarnings = emptyList()
    )

    @Test
    fun queued_turn_is_loading_and_keeps_policy_context() {
        val actual = facade.mapQueued(
            "s-1", "turn-1", policyOutput("probe"),
            ConversationContextContract.empty("space-1", "s-1"), emptyList()
        )
        assertEquals(GenerationState.LOADING, actual.generation.state)
        assertEquals("probe", actual.action?.type)
        assertEquals("open_ctx_turn-1", actual.events.single().eventId)
    }
}
