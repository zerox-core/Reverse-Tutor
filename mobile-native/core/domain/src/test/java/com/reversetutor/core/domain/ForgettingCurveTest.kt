package com.reversetutor.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 遗忘曲线纯函数数值验收（设计稿 docs/specs/forgetting-curve-design.md §8.2）：
 * 数值表断言、复习链序列、lapse 回退、三阶段边界、append-only 抢救语义。
 */
class ForgettingCurveTest {

    private val day = ForgettingCurve.MillisPerDay
    private val t0 = 1_700_000_000_000L

    private fun event(
        type: String,
        result: String = ForgettingCurve.ResultPassed,
        dayOffset: Long = 0L
    ): ForgettingEvent = ForgettingEvent(type, result, t0 + dayOffset * day)

    // ---------- S_0 数值表（§3.1） ----------

    @Test
    fun initialStabilityMatchesDesignTable() {
        assertEquals(2.94f, ForgettingCurve.initialStabilityDays(0.35f), 1e-4f)
        assertEquals(7.26f, ForgettingCurve.initialStabilityDays(0.55f), 1e-4f)
        assertEquals(12.4416f, ForgettingCurve.initialStabilityDays(0.72f), 1e-4f)
        assertEquals(16.1376f, ForgettingCurve.initialStabilityDays(0.82f), 1e-4f)
        assertEquals(19.44f, ForgettingCurve.initialStabilityDays(0.90f), 1e-4f)
    }

    @Test
    fun stabilityPerEvidenceTypeMatchesDesignTable() {
        // explanation 基线接触：k=0 → S_0 = 2.94 天
        assertEquals(2.94f, ForgettingCurve.stabilityDays(listOf(event("explanation")))!!, 1e-4f)
        // 首次 retrieval 是成功复习：k=1 → 7.26 × 2 = 14.52 天
        assertEquals(14.52f, ForgettingCurve.stabilityDays(listOf(event("retrieval")))!!, 1e-4f)
        // 首次 delayed_retrieval：k=1 → 16.1376 × 2 = 32.2752 天
        assertEquals(32.2752f, ForgettingCurve.stabilityDays(listOf(event("delayed_retrieval")))!!, 1e-4f)
        // 首次 correction：k=1 → 19.44 × 2 = 38.88 天
        assertEquals(38.88f, ForgettingCurve.stabilityDays(listOf(event("correction")))!!, 1e-4f)
    }

    @Test
    fun partialEvidenceDiscountsStability() {
        // partial retrieval：eff = 0.55 × 0.75 = 0.4125 → S_0 = 24 × 0.4125² = 4.08375；k=1 → ×2
        assertEquals(8.1675f, ForgettingCurve.stabilityDays(listOf(event("retrieval", "partial")))!!, 1e-4f)
    }

    // ---------- 复习链（§3.2） ----------

    @Test
    fun reviewChainDoublesStabilityAndCapsAt180Days() {
        // 1..5 次成功 retrieval 的 S 序列：14.52 → 29.04 → 58.08 → 116.16 → 180（封顶）
        assertEquals(14.52f, chain(1), 1e-4f)
        assertEquals(29.04f, chain(2), 1e-4f)
        assertEquals(58.08f, chain(3), 1e-4f)
        assertEquals(116.16f, chain(4), 1e-4f)
        assertEquals(180f, chain(5), 1e-4f)
        assertEquals(180f, chain(9), 1e-4f)
    }

    private fun chain(successCount: Int): Float {
        val events = (0 until successCount).map { i ->
            event("retrieval", ForgettingCurve.ResultPassed, dayOffset = i.toLong())
        }
        return ForgettingCurve.stabilityDays(events)!!
    }

    @Test
    fun explanationDoesNotBoostChain() {
        // 三条 explanation：都不进链 → S = S_0(explanation) = 2.94
        val events = (0 until 3).map { i -> event("explanation", dayOffset = i.toLong()) }
        assertEquals(2.94f, ForgettingCurve.stabilityDays(events)!!, 1e-4f)
    }

    @Test
    fun chainCountsAcrossEvidenceTypes() {
        // retrieval + delayed_retrieval + correction 各一次 → k=3
        val events = listOf(
            event("retrieval", dayOffset = 0),
            event("delayed_retrieval", dayOffset = 1),
            event("correction", dayOffset = 2)
        )
        val s0 = ForgettingCurve.initialStabilityDays(0.90f)
        assertEquals(s0 * 8f, ForgettingCurve.stabilityDays(events)!!, 1e-4f)
    }

    // ---------- lapse 回退（§3.3） ----------

    @Test
    fun oneLapseCancelsTwoSuccesses() {
        val events = listOf(
            event("retrieval", dayOffset = 0),
            event("retrieval", dayOffset = 1),
            event("retrieval", dayOffset = 2),
            event("retrieval", ForgettingCurve.ResultFailed, dayOffset = 3)
        )
        // k_eff = 3 − 2 = 1 → S_0(retrieval) × 2 = 14.52
        assertEquals(14.52f, ForgettingCurve.stabilityDays(events)!!, 1e-4f)
    }

    @Test
    fun lapsesNeverDriveStabilityNegative() {
        val events = (0 until 3).map { i ->
            event("retrieval", ForgettingCurve.ResultFailed, dayOffset = i.toLong())
        }
        // 只有 failed：无巩固事件 → null（failed 不构成接触）
        assertNull(ForgettingCurve.stabilityDays(events))
        assertTrue(ForgettingCurve.stage(events, t0 + 400 * day) is ForgettingNodeStage.NoEvidence)
    }

    @Test
    fun lapseAndSuccessMixed() {
        val events = listOf(
            event("retrieval", dayOffset = 0),
            event("retrieval", ForgettingCurve.ResultFailed, dayOffset = 1),
            event("retrieval", dayOffset = 2)
        )
        // k_success=2, k_lapse=1 → k_eff=0 → S = S_0(retrieval) = 7.26
        assertEquals(7.26f, ForgettingCurve.stabilityDays(events)!!, 1e-4f)
    }

    // ---------- 三阶段边界（§4） ----------

    @Test
    fun stageBoundariesAroundOneAndThreeStabilities() {
        // 单次 correction（k=1）→ S = 38.88 天；t_last = t0
        val s = 38.88f
        val events = listOf(event("correction"))

        // Δt = 1.0S − ε → Protection
        val justInside = t0 + ((s - 0.001f) * day).toLong()
        assertTrue(ForgettingCurve.stage(events, justInside) is ForgettingNodeStage.Protection)
        // Δt = 1.0S + ε → Decaying，progress ≈ 0
        val justDecaying = t0 + ((s + 0.001f) * day).toLong()
        ForgettingCurve.stage(events, justDecaying).let { stage ->
            assertTrue(stage is ForgettingNodeStage.Decaying)
            stage as ForgettingNodeStage.Decaying
            assertEquals(0.001f / (2f * s), stage.progress, 1e-6f)
            // 断链时刻保持率 ≈ e^-1 ≈ 0.368
            assertEquals(0.368f, stage.retention, 0.01f)
        }
        // Δt = 3.0S − ε → Decaying，progress ≈ 1
        val nearAbsorb = t0 + ((3f * s - 0.001f) * day).toLong()
        ForgettingCurve.stage(events, nearAbsorb).let { stage ->
            assertTrue(stage is ForgettingNodeStage.Decaying)
            assertEquals(1f - 0.001f / (2f * s), (stage as ForgettingNodeStage.Decaying).progress, 1e-5f)
            // 坠入时刻保持率 ≈ e^-3 ≈ 0.0498
            assertEquals(0.05f, (stage as ForgettingNodeStage.Decaying).retention, 0.01f)
        }
        // Δt = 3.0S + ε → Absorbed
        val absorbed = t0 + ((3f * s + 0.001f) * day).toLong()
        assertTrue(ForgettingCurve.stage(events, absorbed) is ForgettingNodeStage.Absorbed)
    }

    @Test
    fun protectionReportsElapsedDays() {
        val events = listOf(event("retrieval")) // S = 14.52
        val now = t0 + (7.26f * day).toLong()
        ForgettingCurve.stage(events, now).let { stage ->
            assertTrue(stage is ForgettingNodeStage.Protection)
            assertEquals(7.26f, (stage as ForgettingNodeStage.Protection).elapsedDays, 1e-4f)
        }
    }

    @Test
    fun clockSkewTreatedAsZeroElapsed() {
        val events = listOf(event("correction", dayOffset = 5))
        val stage = ForgettingCurve.stage(events, t0) // now 早于事件
        assertTrue(stage is ForgettingNodeStage.Protection)
        assertEquals(0f, (stage as ForgettingNodeStage.Protection).elapsedDays, 1e-6f)
    }

    // ---------- 抢救语义（append-only，§3.4 / D7） ----------

    @Test
    fun appendNewConsolidatingEventRescuesAbsorbedNode() {
        val old = listOf(event("explanation", dayOffset = -100)) // 早已坠入
        val longAfter = t0 + 400 * day
        assertTrue(ForgettingCurve.stage(old, longAfter) is ForgettingNodeStage.Absorbed)

        // 抢救 = 追加新事件（不改旧流）
        val rescued = old + event("delayed_retrieval", dayOffset = 400)
        ForgettingCurve.stage(rescued, longAfter).let { stage ->
            assertTrue(stage is ForgettingNodeStage.Protection)
            // k_success = 1 → S_0(0.82) × 2 = 32.2752
            assertEquals(32.2752f, (stage as ForgettingNodeStage.Protection).stabilityDays, 1e-4f)
        }
    }

    // ---------- 无证据 / 未知类型 ----------

    @Test
    fun unknownOrEmptyStreamsYieldNoEvidence() {
        assertTrue(
            ForgettingCurve.stage(emptyList(), t0) is ForgettingNodeStage.NoEvidence
        )
        assertTrue(
            ForgettingCurve.stage(
                listOf(event("mystery_type")),
                t0 + 400 * day
            ) is ForgettingNodeStage.NoEvidence
        )
        assertNull(ForgettingCurve.stabilityDays(emptyList()))
    }

    // ---------- 视觉强度（§5） ----------

    @Test
    fun strengthSumsEventRetentions() {
        // 事件 1：retrieval（eff 0.55，k=1 → S=14.52），Δt=7 天 → R = e^(-7/14.52)
        // 事件 2：transfer（eff 0.72，Δt=0 → R = 1；transfer 不进复习链）
        val events = listOf(
            event("retrieval", dayOffset = 0),
            event("transfer", dayOffset = 7)
        )
        val now = t0 + 7 * day
        val expected = 0.55f * kotlin.math.exp(-7.0 / 14.52).toFloat() + 0.72f
        assertEquals(expected, ForgettingCurve.strength(events, now), 1e-4f)
    }

    @Test
    fun strengthIgnoresFailedEvents() {
        val events = listOf(event("retrieval", ForgettingCurve.ResultFailed, dayOffset = 0))
        assertEquals(0f, ForgettingCurve.strength(events, t0 + day), 1e-6f)
    }

    // ---------- 确定性 ----------

    @Test
    fun inputOrderDoesNotAffectResults() {
        val events = listOf(
            event("retrieval", dayOffset = 3),
            event("correction", dayOffset = 1),
            event("explanation", dayOffset = 2)
        )
        val shuffled = events.reversed()
        assertEquals(
            ForgettingCurve.stabilityDays(events)!!,
            ForgettingCurve.stabilityDays(shuffled)!!,
            1e-6f
        )
        val now = t0 + 10 * day
        assertEquals(
            ForgettingCurve.stage(events, now),
            ForgettingCurve.stage(shuffled, now)
        )
    }
}
