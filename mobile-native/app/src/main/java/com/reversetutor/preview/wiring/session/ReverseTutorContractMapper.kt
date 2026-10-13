package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ActionTypeWire
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.EntryStatusWire
import com.reversetutor.core.domain.MasteryEvidenceContract
import com.reversetutor.core.domain.MasteryEvidenceStatusWire
import com.reversetutor.core.domain.MasteryEvidenceTypeWire
import com.reversetutor.core.domain.SessionActionContract
import com.reversetutor.core.domain.SessionEvaluationContract
import com.reversetutor.core.domain.StudentRoleWire
import com.reversetutor.core.domain.UserEmotionWire
import com.reversetutor.feature.chat.ConversationMessageContract
import com.reversetutor.feature.chat.ConversationUiEvent
import com.reversetutor.feature.chat.ConversationUiEventType
import com.reversetutor.feature.chat.GenerationState
import com.reversetutor.feature.chat.GenerationUiContract
import com.reversetutor.feature.chat.NextStepContract
import com.reversetutor.feature.chat.SessionConversationContract
import com.reversetutor.feature.reversetutor.loop.TurnResult
import com.reversetutor.feature.reversetutor.session.RTSession

/**
 * 将反转家教算法内核产物（[RTSession]、[TurnResult]）投影为原生 UI 消费的
 * [SessionConversationContract] 和思考抽屉 Markdown 独白。
 */
object ReverseTutorContractMapper {

    /**
     * 将算法运行结果投射为 UI 契约对象。
     */
    fun mapToContract(
        session: RTSession,
        turnResult: TurnResult,
        assistantMessageId: String,
        turnId: String,
        messages: List<ConversationMessageContract>
    ): SessionConversationContract {
        val report = turnResult.report
        val replyText = report.finalReply.orEmpty()

        val correctness = ((session.understandingLevel - 1) / 4.0f).coerceIn(0.0f, 1.0f)
        val depth = (0.2f * session.masteredConcepts.size + 0.1f * session.understandingLevel)
            .coerceIn(0.1f, 1.0f)

        val userEmotion = when (session.emotion) {
            "confused" -> UserEmotionWire.FRUSTRATED
            "excited" -> UserEmotionWire.ENGAGED
            "tired" -> UserEmotionWire.TIRED
            else -> UserEmotionWire.NEUTRAL
        }

        val lastTool = report.toolCalls.lastOrNull()?.tool
        val actionType = when (lastTool) {
            "challenge_teacher_explanation" -> ActionTypeWire.CHALLENGE
            "design_counterexample" -> ActionTypeWire.PROBE
            "scaffold_problem" -> ActionTypeWire.SCAFFOLD_EXAMPLE
            "plan_session_closure" -> ActionTypeWire.RECAP
            else -> ActionTypeWire.ASK
        }

        val studentRole = when {
            report.toolCalls.any { it.tool == "speak_as_confused_student" } ->
                StudentRoleWire.CONFUSED_STUDENT
            report.toolCalls.any { it.tool == "make_deliberate_mistake" } ->
                StudentRoleWire.CLUE_STUDENT
            else -> StudentRoleWire.PROBING_STUDENT
        }

        val topicName = session.topic.ifBlank {
            session.knowledgeGraphNodes.firstOrNull()?.id ?: "当前主题"
        }

        val targetBlindSpot = session.targetBlindSpot.ifBlank {
            session.confusedConcepts.firstOrNull() ?: ""
        }

        val evaluation = SessionEvaluationContract(
            correctness = correctness,
            depth = depth,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            evidence = MasteryEvidenceContract(
                type = if (session.masteredConcepts.isNotEmpty()) MasteryEvidenceTypeWire.TRANSFER else MasteryEvidenceTypeWire.EXPLANATION,
                status = if (correctness >= 0.7f) MasteryEvidenceStatusWire.PASSED else MasteryEvidenceStatusWire.PARTIAL
            ),
            misconception = targetBlindSpot,
            userEmotion = userEmotion
        )

        val action = SessionActionContract(
            type = actionType,
            studentRole = studentRole,
            knowledgePoint = topicName,
            difficulty = (session.understandingLevel * 0.2f).coerceIn(0.1f, 1.0f),
            note = lastTool?.let { "学生动作: $it" } ?: "学生自主思考与追问"
        )

        val processSummary = buildString {
            append("本轮完成 ${report.toolCallCount} 次工具调用，")
            append("理解掌握度: L${session.understandingLevel}，")
            if (session.masteredConcepts.isNotEmpty()) {
                append("已掌握: ${session.masteredConcepts.joinToString()}；")
            }
            if (targetBlindSpot.isNotBlank()) {
                append("当前盲区聚焦: $targetBlindSpot")
            }
        }.trimEnd('；', '，')

        val nextStepHint = if (targetBlindSpot.isNotBlank()) {
            "建议针对【$targetBlindSpot】举具体例子或用反例追问，引导学生发现盲区"
        } else {
            "继续向学生深入讲解【$topicName】，考察其迁移应用能力"
        }

        val nextStep = NextStepContract(
            hint = nextStepHint,
            knowledgePoint = topicName,
            actionType = actionType
        )

        val context = ConversationContextContract.empty("", session.sessionId).copy(
            prerequisiteGaps = session.confusedConcepts,
            warnings = emptyList()
        )

        val events = listOf(
            ConversationUiEvent(
                eventId = "retry_$turnId",
                type = ConversationUiEventType.RETRY
            ),
            ConversationUiEvent(
                eventId = "open_ctx_$turnId",
                type = ConversationUiEventType.OPEN_CONTEXT
            )
        )

        return SessionConversationContract(
            sessionId = session.sessionId,
            turnId = turnId,
            messages = messages,
            generation = GenerationUiContract(
                state = GenerationState.READY,
                assistantMessageId = assistantMessageId,
                assistantText = replyText
            ),
            evaluation = evaluation,
            action = action,
            processSummary = processSummary,
            currentKnowledgePoint = topicName,
            nextStep = nextStep,
            context = context,
            events = events
        )
    }

    /**
     * 将排队/执行中的会话状态投射为 LOADING 状态的 UI 契约对象。
     */
    fun mapQueuedContract(
        session: RTSession,
        turnId: String,
        messages: List<ConversationMessageContract> = emptyList()
    ): SessionConversationContract {
        val correctness = ((session.understandingLevel - 1) / 4.0f).coerceIn(0.0f, 1.0f)
        val depth = (0.2f * session.masteredConcepts.size + 0.1f * session.understandingLevel)
            .coerceIn(0.1f, 1.0f)

        val userEmotion = when (session.emotion) {
            "confused" -> UserEmotionWire.FRUSTRATED
            "excited" -> UserEmotionWire.ENGAGED
            "tired" -> UserEmotionWire.TIRED
            else -> UserEmotionWire.NEUTRAL
        }

        val topicName = session.topic.ifBlank {
            session.knowledgeGraphNodes.firstOrNull()?.id ?: "当前主题"
        }

        val targetBlindSpot = session.targetBlindSpot.ifBlank {
            session.confusedConcepts.firstOrNull() ?: ""
        }

        val evaluation = SessionEvaluationContract(
            correctness = correctness,
            depth = depth,
            entryStatus = EntryStatusWire.HAS_ENTRY,
            evidence = MasteryEvidenceContract(
                type = if (session.masteredConcepts.isNotEmpty()) MasteryEvidenceTypeWire.TRANSFER else MasteryEvidenceTypeWire.EXPLANATION,
                status = if (correctness >= 0.7f) MasteryEvidenceStatusWire.PASSED else MasteryEvidenceStatusWire.PARTIAL
            ),
            misconception = targetBlindSpot,
            userEmotion = userEmotion
        )

        val action = SessionActionContract(
            type = ActionTypeWire.ASK,
            studentRole = StudentRoleWire.PROBING_STUDENT,
            knowledgePoint = topicName,
            difficulty = (session.understandingLevel * 0.2f).coerceIn(0.1f, 1.0f),
            note = "准备进行反转家教探究与诊断"
        )

        val processSummary = buildString {
            append("等待学生自主思考与调用工具决策，")
            append("当前掌握度: L${session.understandingLevel}")
            if (targetBlindSpot.isNotBlank()) {
                append("，聚焦盲区: $targetBlindSpot")
            }
        }

        val nextStepHint = if (targetBlindSpot.isNotBlank()) {
            "建议针对【$targetBlindSpot】举具体例子或用反例追问，引导学生发现盲区"
        } else {
            "继续向学生深入讲解【$topicName】，考察其迁移应用能力"
        }

        val nextStep = NextStepContract(
            hint = nextStepHint,
            knowledgePoint = topicName,
            actionType = ActionTypeWire.ASK
        )

        val context = ConversationContextContract.empty("", session.sessionId).copy(
            prerequisiteGaps = session.confusedConcepts,
            warnings = emptyList()
        )

        val events = listOf(
            ConversationUiEvent(
                eventId = "retry_$turnId",
                type = ConversationUiEventType.RETRY
            ),
            ConversationUiEvent(
                eventId = "open_ctx_$turnId",
                type = ConversationUiEventType.OPEN_CONTEXT
            )
        )

        return SessionConversationContract(
            sessionId = session.sessionId,
            turnId = turnId,
            messages = messages,
            generation = GenerationUiContract(
                state = GenerationState.LOADING,
                assistantMessageId = null,
                assistantText = null
            ),
            evaluation = evaluation,
            action = action,
            processSummary = processSummary,
            currentKnowledgePoint = topicName,
            nextStep = nextStep,
            context = context,
            events = events
        )
    }
}
