package com.reversetutor.feature.chat

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract
import com.reversetutor.core.domain.SessionPolicyOutput
import com.reversetutor.core.domain.CorrectionTimingWire
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTurnDispatchTest {

    private fun request(text: String = "解释一下") = BackgroundTurnPreparationRequest(
        spaceId = "space-1",
        sessionId = "session-1",
        userMessageId = "msg-1",
        userText = text,
        token = "token-1",
        quoteExcerpt = null,
        imageAttachments = emptyList(),
        sessionSnapshot = null
    )

    private fun minimalPolicy() = SessionPolicyOutput(
        evaluation = SessionEvaluationContract(),
        action = SessionActionContract(
            type = "probe",
            studentRole = "learner",
            knowledgePoint = "",
            difficulty = 0.5f,
            note = ""
        ),
        processSummary = "",
        correctionTiming = CorrectionTimingWire.IMMEDIATE,
        normalizationWarnings = emptyList()
    )

    @Test
    fun queued_result_triggers_one_worker_callback() = runBlocking {
        val port = BackgroundTurnPreparationPort { req ->
            BackgroundTurnPreparationResult.Queued(
                jobId = "job-1",
                contract = SessionConversationFacade().mapQueued(
                    sessionId = req.sessionId,
                    turnId = req.userMessageId,
                    policy = minimalPolicy(),
                    context = ConversationContextContract.empty(req.spaceId, req.sessionId),
                    messages = emptyList()
                )
            )
        }

        val callbacks = mutableListOf<String>()
        when (val prepared = port.prepareAndEnqueue(request())) {
            is BackgroundTurnPreparationResult.Queued -> {
                callbacks.add(prepared.jobId)
            }
            BackgroundTurnPreparationResult.BlankInput,
            BackgroundTurnPreparationResult.SessionUnavailable,
            BackgroundTurnPreparationResult.Unavailable,
            BackgroundTurnPreparationResult.Failed -> { }
        }

        assertEquals(1, callbacks.size)
        assertEquals("job-1", callbacks[0])
    }

    @Test
    fun rejected_result_triggers_no_callback_and_no_direct_generation() = runBlocking {
        val callbacks = mutableListOf<String>()
        val result: BackgroundTurnPreparationResult =
            BackgroundTurnPreparationPort.Unavailable.prepareAndEnqueue(request())
        assertEquals(BackgroundTurnPreparationResult.Unavailable, result)
        assertTrue(callbacks.isEmpty())
    }
}
