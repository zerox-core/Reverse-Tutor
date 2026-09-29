package com.reversetutor.feature.memory

import com.reversetutor.core.domain.LearningFactReceipt
import com.reversetutor.core.model.GraphNode
import com.reversetutor.core.model.GraphNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R103 遗忘曲线接线单测：receipts 事件流 → 节点级 [BlackHoleForgetProfile] 投影。
 *
 * 数值口径全部来自 [com.reversetutor.core.domain.ForgettingCurve] 定稿参数
 * （见 ForgettingCurveTest）；本测试只验证「阶段 → 档案」的换算与联动键规则。
 */
class GraphForgettingProjectionTest {

    private fun receipt(
        knowledgePoint: String,
        evidenceType: String,
        result: String,
        atMillis: Long
    ) = LearningFactReceipt(
        knowledgePoint = knowledgePoint,
        evidenceType = evidenceType,
        result = result,
        confidence = 0.9f,
        sourceWindowId = "w",
        sourceTurnId = "t",
        occurredAtEpochMillis = atMillis
    )

    private fun node(id: String, label: String) = GraphNode(
        id = id,
        spaceId = "s",
        label = label,
        kind = GraphNodeKind.Concept,
        createdAtEpochMillis = 0L
    )

    private val day: Long = 86_400_000L

    private val now: Long = 1_000 * day

    @Test
    fun protection_stage_maps_to_profile_with_elapsed_precredit() {
        // retrieval passed 单事件：S_last = 7.26 × 2 = 14.52 天，保护期 1.0S
        val receipts = listOf(receipt("导数", "retrieval", "passed", now - (5 * day)))
        val profiles = GraphForgettingProjection.profiles(
            nodes = listOf(node("n1", "导数")),
            receipts = receipts,
            nowEpochMillis = now
        )
        val p = profiles["n1"]!!
        assertEquals(14.52f * 86400f, p.protectionSeconds, 0.5f)
        assertEquals(2f * 14.52f * 86400f, p.forgettingFullSeconds, 1f)
        assertEquals(5f * 86400f, p.elapsedProtectionSeconds, 0.5f)
        assertEquals(0f, p.initialForget, 1e-6f)
    }

    @Test
    fun decaying_stage_maps_to_expired_protection_with_initial_forget() {
        // Δt = 2.0S：过保、离散进度 (2S − S) / 2S = 0.5
        val receipts = listOf(receipt("导数", "retrieval", "passed", now - (29.04f.toLong() * day)))
        val profiles = GraphForgettingProjection.profiles(
            nodes = listOf(node("n1", "导数")),
            receipts = receipts,
            nowEpochMillis = now
        )
        val p = profiles["n1"]!!
        assertEquals(0.5f, p.initialForget, 0.01f)
        // 过保：elapsed 预扣 >= 保护期（引擎侧 cooling=false 直接进衰减）
        assertTrue(p.elapsedProtectionSeconds >= p.protectionSeconds)
    }

    @Test
    fun absorbed_far_past_maps_to_initial_forget_full() {
        // Δt > 3.0S：坠入核心，建档即淡出
        val receipts = listOf(receipt("导数", "retrieval", "passed", now - (90 * day)))
        val profiles = GraphForgettingProjection.profiles(
            nodes = listOf(node("n1", "导数")),
            receipts = receipts,
            nowEpochMillis = now
        )
        val p = profiles["n1"]!!
        assertEquals(1f, p.initialForget, 1e-6f)
        assertTrue(p.elapsedProtectionSeconds >= 3f * p.protectionSeconds)
    }

    @Test
    fun label_match_is_trimmed_and_case_exact() {
        val receipts = listOf(receipt(" 导数 ", "retrieval", "passed", now - (5 * day)))
        val profiles = GraphForgettingProjection.profiles(
            nodes = listOf(node("n1", "导数"), node("n2", "另一概念")),
            receipts = receipts,
            nowEpochMillis = now
        )
        assertTrue("trimmed knowledge point should match label", profiles.containsKey("n1"))
        assertFalse("unrelated node must not get a profile", profiles.containsKey("n2"))
    }

    @Test
    fun no_consolidating_events_yields_no_profile() {
        // 只有 failed / 未知证据：NoEvidence → 不产出档案（无证据不遗忘，回落全局时长）
        val receipts = listOf(
            receipt("导数", "retrieval", "failed", now - (5 * day)),
            receipt("导数", "unknown_type", "passed", now - (6 * day))
        )
        val profiles = GraphForgettingProjection.profiles(
            nodes = listOf(node("n1", "导数")),
            receipts = receipts,
            nowEpochMillis = now
        )
        assertNull(profiles["n1"])
    }

    @Test
    fun chain_events_use_latest_stability() {
        // 两次 retrieval passed：S = 7.26 × 2² = 29.04 天，保护期即 29.04 天
        val receipts = listOf(
            receipt("导数", "retrieval", "passed", now - (20 * day)),
            receipt("导数", "retrieval", "passed", now - (2 * day))
        )
        val profiles = GraphForgettingProjection.profiles(
            nodes = listOf(node("n1", "导数")),
            receipts = receipts,
            nowEpochMillis = now
        )
        val p = profiles["n1"]!!
        assertEquals(29.04f * 86400f, p.protectionSeconds, 1f)
        // Δt=2 天，elapsed 预扣 2 天
        assertEquals(2f * 86400f, p.elapsedProtectionSeconds, 1f)
    }
}
