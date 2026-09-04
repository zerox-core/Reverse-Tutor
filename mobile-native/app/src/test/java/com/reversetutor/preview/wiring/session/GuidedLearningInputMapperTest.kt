package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.domain.ErrorReferenceContract
import com.reversetutor.core.domain.MemoryReferenceContract
import com.reversetutor.core.domain.SourceReferenceContract
import com.reversetutor.core.domain.UserIntent
import com.reversetutor.feature.chat.NewSessionConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NEWMP-V1-002 Task 2.3 · [GuidedLearningInputMapper] red tests.
 *
 * The mapper must project session-template fields and the *already-safe*
 * window-visible context into [com.reversetutor.core.domain.GuidedLearningTurnInput]
 * without ever carrying raw text (message bodies, memory summaries, source
 * excerpts, error descriptions) into the turn input, and must degrade to a safe
 * empty context when the space or session identity does not match.
 */
class GuidedLearningInputMapperTest {

    private val trustedContext = ConversationContextContract(
        spaceId = "space-a",
        sessionId = "child-2",
        prerequisiteGaps = listOf("函数符号"),
        relatedMemory = listOf(
            MemoryReferenceContract(id = "m-1", summary = "原文记忆摘要", relevanceScore = 1f, updatedAtEpochMillis = 0L)
        ),
        sourceEvidence = listOf(
            SourceReferenceContract(id = "s-1", title = "资料", excerpt = "资料原文片段", sourceType = "doc", relevanceScore = 1f)
        ),
        historicalErrors = listOf(
            ErrorReferenceContract(id = "e-1", errorType = "符号混淆", description = "历史错误原文", timestampEpochMillis = 0L)
        ),
        pendingReviewKnowledgePoints = listOf("复合函数"),
        recentMessages = listOf(
            ContextMessage(messageId = "root-before", role = "user", text = "消息正文甲", timestampEpochMillis = 1L),
            ContextMessage(messageId = "child-local", role = "assistant", text = "消息正文乙", timestampEpochMillis = 2L)
        ),
        warnings = emptyList()
    )

    private fun templateConfig() = NewSessionConfiguration(
        title = "函数专题",
        goal = "掌握函数单调性",
        learnerRole = "高中学习者",
        learnerProfile = "基础薄弱",
        dialogueStrategy = "probe",
        correctionPersistence = "严格",
        speakingTone = "平静"
    )

    // ---- 1. 模板字段映射 ----

    @Test
    fun templateFieldsMapOntoSessionTemplateAndLearnerProfile() {
        val input = templateConfig().toGuidedLearningTurnInput(
            spaceId = "space-a",
            sessionId = "child-2",
            context = trustedContext
        ).normalized()

        assertEquals("函数专题", input.sessionTemplate.subject)
        assertEquals("掌握函数单调性", input.sessionTemplate.learningGoal)
        assertEquals("高中学习者", input.sessionTemplate.learnerRole)
        assertEquals("probe", input.sessionTemplate.dialogueStrategy)
        assertEquals("严格", input.sessionTemplate.correctionTiming)
        assertEquals("平静", input.learnerProfile.preferredTone)
        assertEquals("基础薄弱", input.learnerProfile.declaredLevel)
        assertEquals("掌握函数单调性", input.conceptKey)
        assertEquals("space-a", input.spaceId)
        assertEquals("child-2", input.sessionId)
        assertEquals("child-2", input.windowId)
    }

    @Test
    fun learningGoalFallsBackToTitleWhenBlank() {
        val input = templateConfig().copy(goal = "  ").toGuidedLearningTurnInput(
            spaceId = "space-a", sessionId = "child-2", context = trustedContext
        ).normalized()
        assertEquals("函数专题", input.sessionTemplate.learningGoal)
    }

    // ---- 2. 可见消息只传 ID、保序、不带正文 ----

    @Test
    fun visibleMessagesProjectIdsOnlyInOrder() {
        val input = templateConfig().toGuidedLearningTurnInput(
            spaceId = "space-a", sessionId = "child-2", context = trustedContext
        ).normalized()

        assertEquals(listOf("root-before", "child-local"), input.visibleContext.visibleMessageIds)
        assertFalse(
            input.visibleContext.visibleMessageIds.any { it.contains("消息正文") }
        )
    }

    // ---- 3. 结构化学习状态仅来自 gaps 与待复习知识点 ----

    @Test
    fun structuredConceptFactsComeOnlyFromGapsAndPendingReview() {
        val input = templateConfig().toGuidedLearningTurnInput(
            spaceId = "space-a", sessionId = "child-2", context = trustedContext
        ).normalized()

        assertEquals(listOf("函数符号", "复合函数"), input.visibleContext.currentConceptKeys)
        assertEquals(listOf("函数符号", "复合函数"), input.learnerProfile.knownGaps)
        assertTrue(input.conceptStates.isEmpty())
    }

    // ---- 4. 任何原文不得复制到 turn input 的文本字段 ----

    @Test
    fun rawContextTextNeverLeaksIntoTurnInput() {
        val input = templateConfig().toGuidedLearningTurnInput(
            spaceId = "space-a", sessionId = "child-2", context = trustedContext
        ).normalized()

        val dump = input.toString()
        for (forbidden in listOf("消息正文甲", "消息正文乙", "原文记忆摘要", "资料原文片段", "历史错误原文")) {
            assertFalse("原文泄露到 GuidedLearningTurnInput: $forbidden", dump.contains(forbidden))
        }
        assertEquals(UserIntent.Ambiguous, input.visibleContext.recentUserIntent)
        assertEquals(0, input.visibleContext.unresolvedQuestionCount)
        assertEquals(input.recentSignals, com.reversetutor.core.domain.RecentTurnSignals())
    }

    // ---- 5. 空间或会话不匹配 → 安全空上下文 ----

    @Test
    fun spaceMismatchDegradesToSafeEmptyContext() {
        val input = templateConfig().toGuidedLearningTurnInput(
            spaceId = "space-B", sessionId = "child-2", context = trustedContext
        ).normalized()

        assertTrue(input.visibleContext.visibleMessageIds.isEmpty())
        assertTrue(input.visibleContext.currentConceptKeys.isEmpty())
        assertTrue(input.learnerProfile.knownGaps.isEmpty())
        // 模板字段仍可信映射，不受上下文降级影响
        assertEquals("函数专题", input.sessionTemplate.subject)
    }

    @Test
    fun sessionMismatchDegradesToSafeEmptyContext() {
        val input = templateConfig().toGuidedLearningTurnInput(
            spaceId = "space-a", sessionId = "other-session", context = trustedContext
        ).normalized()

        assertTrue(input.visibleContext.visibleMessageIds.isEmpty())
        assertTrue(input.visibleContext.currentConceptKeys.isEmpty())
        assertTrue(input.learnerProfile.knownGaps.isEmpty())
    }

    // ---- 6. 空模板/空上下文得到合法默认，不抛异常 ----

    @Test
    fun nullTemplateAndEmptyContextProduceSafeDefaults() {
        val input = null.toGuidedLearningTurnInput(
            spaceId = "space-a",
            sessionId = "child-2",
            context = ConversationContextContract.empty("space-a", "child-2")
        ).normalized()

        assertEquals("unknown", input.conceptKey)
        assertEquals("", input.sessionTemplate.subject)
        assertEquals("", input.sessionTemplate.learningGoal)
        assertTrue(input.visibleContext.visibleMessageIds.isEmpty())
        assertTrue(input.learnerProfile.knownGaps.isEmpty())
        assertTrue(input.conceptStates.isEmpty())
    }
}
