package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ActionTypeWire
import com.reversetutor.core.domain.StudentRoleWire
import com.reversetutor.core.domain.UserEmotionWire
import com.reversetutor.feature.chat.GenerationState
import com.reversetutor.feature.reversetutor.loop.TurnReport
import com.reversetutor.feature.reversetutor.loop.TurnResult
import com.reversetutor.feature.reversetutor.session.RTSession
import com.reversetutor.feature.reversetutor.tools.ToolResult
import com.reversetutor.feature.reversetutor.tools.ToolTraceRecord
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseTutorContractMapperTest {

    @Test
    fun `mapToContract maps session and turnResult to SessionConversationContract correctly`() {
        val session = RTSession(
            sessionId = "session-123",
            topic = "一元二次方程",
            understandingLevel = 3,
            emotion = "confused",
            targetBlindSpot = "配方法常数项符号",
            confusedConcepts = listOf("配方法"),
            masteredConcepts = listOf("因式分解")
        )
        val report = TurnReport(
            round = 1,
            toolCallCount = 1,
            finalReply = "我还是不太懂为什么两边要同时加 9？"
        )
        report.toolCalls.add(
            ToolTraceRecord(
                tool = "design_counterexample",
                category = "cognitive",
                input = JSONObject(),
                output = ToolResult(
                    success = true,
                    metadata = com.reversetutor.feature.reversetutor.tools.ToolMetadata("design_counterexample", 25L)
                ),
                success = true,
                error = null,
                durationMs = 25L,
                attempt = 1
            )
        )
        val turnResult = TurnResult(
            updatedSession = session,
            updatedMessages = emptyList(),
            report = report
        )

        val contract = ReverseTutorContractMapper.mapToContract(
            session = session,
            turnResult = turnResult,
            assistantMessageId = "msg-assistant-1",
            turnId = "turn-1",
            messages = emptyList()
        )

        assertEquals("session-123", contract.sessionId)
        assertEquals("turn-1", contract.turnId)
        assertEquals(GenerationState.READY, contract.generation.state)
        assertEquals("msg-assistant-1", contract.generation.assistantMessageId)
        assertEquals("我还是不太懂为什么两边要同时加 9？", contract.generation.assistantText)

        val eval = contract.evaluation
        assertNotNull(eval)
        assertEquals(0.5f, eval!!.correctness, 0.01f) // (3 - 1) / 4.0 = 0.5
        assertEquals(UserEmotionWire.FRUSTRATED, eval.userEmotion)
        assertEquals("配方法常数项符号", eval.misconception)

        val action = contract.action
        assertNotNull(action)
        assertEquals(ActionTypeWire.PROBE, action!!.type) // design_counterexample -> PROBE
        assertEquals("一元二次方程", action.knowledgePoint)

        assertNotNull(contract.nextStep)
        assertTrue(contract.nextStep!!.hint.contains("配方法常数项符号"))
        assertEquals(listOf("配方法"), contract.context.prerequisiteGaps)
    }

    @Test
    fun `mapQueuedContract maps session to LOADING contract correctly`() {
        val session = RTSession(
            sessionId = "session-queued-1",
            topic = "导数几何意义",
            understandingLevel = 2,
            emotion = "neutral",
            targetBlindSpot = "切线斜率极限定义",
            confusedConcepts = listOf("割线与切线"),
            masteredConcepts = listOf("平均变化率")
        )

        val contract = ReverseTutorContractMapper.mapQueuedContract(
            session = session,
            turnId = "turn-queued-1",
            messages = emptyList()
        )

        assertEquals("session-queued-1", contract.sessionId)
        assertEquals("turn-queued-1", contract.turnId)
        assertEquals(GenerationState.LOADING, contract.generation.state)
        assertEquals(null, contract.generation.assistantMessageId)
        assertEquals(null, contract.generation.assistantText)

        val eval = contract.evaluation
        assertNotNull(eval)
        assertEquals(0.25f, eval!!.correctness, 0.01f) // (2 - 1) / 4.0 = 0.25
        assertEquals("切线斜率极限定义", eval.misconception)

        val action = contract.action
        assertNotNull(action)
        assertEquals(ActionTypeWire.ASK, action!!.type)
        assertEquals("导数几何意义", action.knowledgePoint)

        assertNotNull(contract.nextStep)
        assertTrue(contract.nextStep!!.hint.contains("切线斜率极限定义"))
        assertEquals(listOf("割线与切线"), contract.context.prerequisiteGaps)
        assertTrue(contract.processSummary?.contains("等待学生自主思考") == true)
    }
}
