package com.reversetutor.preview.wiring.session

import com.reversetutor.core.domain.CorrectionPersistenceWire
import com.reversetutor.core.domain.ContextMessage
import com.reversetutor.core.domain.ConversationContextContract
import com.reversetutor.core.domain.MasterySnapshot
import com.reversetutor.core.domain.SessionModeWire
import com.reversetutor.feature.chat.NewSessionConfiguration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPolicyInputMapperTest {

    @Test
    fun configuration_always_maps_to_study() {
        val input = NewSessionConfiguration(
            title = "\u4ee3\u6570",
            goal = "\u56e0\u5f0f\u5206\u89e3",
            dialogueStrategy = "\u966a\u4f34\u5f0f\u8bb2\u89e3",
            probingIntensity = 5,
            correctionPersistence = "\u4e25\u683c"
        ).toSessionPolicyInput("\u6211\u4e0d\u4f1a")
        assertEquals(SessionModeWire.STUDY, input.mode)
        assertEquals(5, input.settings.probingIntensity)
        assertEquals(CorrectionPersistenceWire.PERSISTENT, input.settings.correctionPersistence)
    }

    @Test
    fun null_snapshot_defaults_to_balanced() {
        val input = null.toSessionPolicyInput("\u95ee\u9898")
        assertEquals(SessionModeWire.STUDY, input.mode)
        assertEquals(3, input.settings.probingIntensity)
        assertEquals(CorrectionPersistenceWire.BALANCED, input.settings.correctionPersistence)
    }

    @Test
    fun goal_blanks_fall_back_to_title() {
        val input = NewSessionConfiguration(
            title = "\u51e0\u4f55",
            goal = ""
        ).toSessionPolicyInput("\u4ec0\u4e48\u662f\u89d2")
        assertEquals("\u51e0\u4f55", input.knowledgePoint)
    }

    @Test
    fun template_persona_goal_plan_and_dialogue_are_preserved_in_policy_input() {
        val input = NewSessionConfiguration(
            title = "高中数学",
            learnerRole = "谨慎的追问型学生",
            learnerProfile = "容易漏步骤，喜欢反例",
            goal = "掌握函数单调性",
            plan = "先诊断，再例题，最后迁移",
            dialogueStrategy = "每次只追问一个为什么",
            speakingTone = "自然"
        ).toSessionPolicyInput("解释单调性")

        assertEquals("谨慎的追问型学生", input.learnerRole)
        assertEquals("容易漏步骤，喜欢反例", input.learnerProfile)
        assertEquals("掌握函数单调性", input.knowledgePoint)
        assertEquals("先诊断，再例题，最后迁移", input.learningPlan)
        assertEquals("每次只追问一个为什么", input.dialogueStrategy)
        assertEquals("自然", input.speakingTone)
    }

    @Test
    fun blank_template_fields_use_bounded_safe_defaults() {
        val input = NewSessionConfiguration().toSessionPolicyInput("问题")

        assertEquals("学习者", input.learnerRole)
        assertEquals("未设置", input.learnerProfile)
        assertEquals("未设置", input.learningPlan)
        assertEquals("自然", input.speakingTone)
        assertEquals("未设置", input.dialogueStrategy)
    }

    @Test
    fun context_evidence_ids_are_derived_from_their_source_values() {
        val evidence = ConversationContextContract(
            spaceId = "space-1",
            sessionId = "session-1",
            prerequisiteGaps = listOf("先掌握定义"),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = listOf("函数单调性"),
            recentMessages = listOf(
                ContextMessage("message-a", "user", "第一条", 1L),
                ContextMessage("message-b", "assistant", "第二条", 2L)
            ),
            warnings = emptyList()
        ).toLlmContextEvidence()

        assertEquals("gaps-session-1", evidence[0].id)
        assertEquals("review-session-1", evidence[1].id)
        assertEquals("msg-message-a", evidence[2].id)
        assertEquals("msg-message-b", evidence[3].id)
    }

    @Test
    fun review_evidence_survives_cap_and_carries_the_soft_hint() {
        val messages = (1..10).map { index ->
            ContextMessage("message-$index", "user", "第$index 条", index.toLong())
        }
        val evidence = ConversationContextContract(
            spaceId = "space-1",
            sessionId = "session-1",
            prerequisiteGaps = emptyList(),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = listOf("因式分解", "勾股定理"),
            recentMessages = messages,
            warnings = emptyList()
        ).toLlmContextEvidence()

        // 学习路径只保留最近两条消息证据（提速拍板 2026-09-20）
        assertEquals(3, evidence.size)
        assertEquals(
            listOf("msg-message-1", "msg-message-2"),
            evidence.filter { it.kind == "Message" }.map { it.id }
        )
        val review = evidence.first { it.kind == "Review" }
        assertEquals("review-session-1", review.id)
        assertTrue(review.body.contains("因式分解"))
        assertTrue(review.body.contains("勾股定理"))
        assertTrue(review.body.contains("软提示"))
        assertTrue(review.body.contains("不强制打断"))
    }

    @Test
    fun mastery_projections_are_folded_into_the_turn_note_not_evidence() {
        val evidence = ConversationContextContract(
            spaceId = "space-1",
            sessionId = "session-1",
            prerequisiteGaps = emptyList(),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = emptyList(),
            recentMessages = listOf(ContextMessage("message-a", "user", "第一条", 1L)),
            warnings = emptyList(),
            masteryProjections = listOf(
                MasterySnapshot(
                    knowledgePoint = "因式分解",
                    score = 51.98f,
                    reviewIntervalDays = 3,
                    nextReviewAtEpochMillis = 10L,
                    attempts = 2,
                    band = "basic_application"
                )
            )
        ).toLlmContextEvidence()

        // 表达层提速（2026-09-20 拍板）：掌握度不再进证据，由确定性便签承载
        assertEquals(1, evidence.size)
        assertEquals("msg-message-a", evidence[0].id)
        assertTrue(evidence.none { it.kind == "Mastery" })
    }

    @Test
    fun lightweight_evidence_carries_only_two_recent_messages() {
        val evidence = ConversationContextContract(
            spaceId = "space-1",
            sessionId = "session-1",
            prerequisiteGaps = listOf("先掌握定义"),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = listOf("函数单调性"),
            recentMessages = listOf(
                ContextMessage("message-a", "user", "第一条", 1L),
                ContextMessage("message-b", "assistant", "第二条", 2L),
                ContextMessage("message-c", "user", "第三条", 3L)
            ),
            warnings = emptyList(),
            earlyHistoryDigest = "早期摘要"
        ).toLlmLightweightContextEvidence()

        assertEquals(listOf("msg-message-a", "msg-message-b"), evidence.map { it.id })
        assertTrue(evidence.all { it.kind == "Message" })
        assertTrue(evidence.none { it.kind == "Summary" || it.kind == "Gaps" || it.kind == "Review" })
    }

    @Test
    fun lightweight_message_bodies_are_capped_tighter() {
        val evidence = ConversationContextContract(
            spaceId = "space-1",
            sessionId = "session-1",
            prerequisiteGaps = emptyList(),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = emptyList(),
            recentMessages = listOf(
                ContextMessage("message-a", "assistant", "长".repeat(600), 1L)
            ),
            warnings = emptyList()
        ).toLlmLightweightContextEvidence()

        assertTrue(evidence.single().body.length <= 400)
    }

    @Test
    fun knowledge_point_is_bounded_and_sensitive_text_is_redacted() {
        val input = NewSessionConfiguration(
            title = "https://secret.example/key",
            goal = "sk-test-secret-value"
        ).toSessionPolicyInput("问题")

        assertFalse(input.knowledgePoint.contains("sk-test"))
        assertTrue(input.knowledgePoint.length <= 320)
    }

    @Test
    fun early_history_digest_is_emitted_first_as_summary_evidence() {
        val evidence = ConversationContextContract(
            spaceId = "space-1",
            sessionId = "session-1",
            prerequisiteGaps = listOf("先掌握定义"),
            relatedMemory = emptyList(),
            sourceEvidence = emptyList(),
            historicalErrors = emptyList(),
            pendingReviewKnowledgePoints = listOf("函数单调性"),
            recentMessages = listOf(ContextMessage("message-a", "user", "第一条", 1L)),
            warnings = emptyList(),
            earlyHistoryDigest = "用户已掌握因式分解基础"
        ).toLlmContextEvidence()

        assertEquals(4, evidence.size)
        assertEquals("summary-session-1", evidence[0].id)
        assertEquals("Summary", evidence[0].kind)
        assertTrue(evidence[0].body.contains("已压缩"))
        assertTrue(evidence[0].body.contains("用户已掌握因式分解基础"))
        assertEquals("gaps-session-1", evidence[1].id)
        assertEquals("review-session-1", evidence[2].id)
        assertEquals("msg-message-a", evidence[3].id)
    }
    @Test
    fun template_evidence_includes_state_deadline_and_milestones() {
        val evidence = NewSessionConfiguration(
            title = "代数",
            goal = "因式分解",
            plan = "[ ] 刷十道题",
            deadline = "2026-10-01",
            currentState = "卡住了",
            stageMilestones = "[x] 看完讲义\n[ ] 刷题"
        ).toLlmTemplateEvidence()
        requireNotNull(evidence)
        assertTrue(evidence.body.contains("当前状态=卡住了"))
        assertTrue(evidence.body.contains("截止=2026-10-01"))
        assertTrue(evidence.body.contains("阶段里程碑=[x] 看完讲义"))
    }

    @Test
    fun template_evidence_omits_unset_state_deadline_and_milestones() {
        val evidence = NewSessionConfiguration(
            title = "代数",
            goal = "因式分解"
        ).toLlmTemplateEvidence()
        requireNotNull(evidence)
        assertFalse(evidence.body.contains("当前状态="))
        assertFalse(evidence.body.contains("截止="))
        assertFalse(evidence.body.contains("阶段里程碑="))
    }
}
