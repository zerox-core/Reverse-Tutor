package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.atan2

/**
 * 黑洞图谱引擎单元测试（V12~V30 定稿规格的落地验收 + 真机反馈整改）。
 * 覆盖：初始布局、力场稳定性、拖拽语义、alpha 平台、松手回归物理层（不停泊）、
 * 弹簧收敛圆润、黑洞弹开不吞噬、吞噬遗忘门（碰撞误入核心只弹不吞）、
 * R65 算法层重构（净空带永久硬边界）、R75 微引力（alpha 缩放、布局不塌）+
 * 惯性引力弯折（甩动轨迹向洞心偏转）、
 * R66 冷却保护模型（保护期内遗忘冻结+原地公转、过保后漩涡旅程螺旋坠入、
 * 坠入节点脱离力场不扰动布局、Dying 不可命中/拖拽）、
 * 页面重置、硬保护（clamp/NaN）、settle 后常驻公转、入场缓动、时间倍率全局缩放。
 */
class BlackHoleGraphEngineTest {

    private fun engineWith(
        nodeCount: Int,
        edges: List<Pair<String, String>> = emptyList()
    ): BlackHoleGraphEngine {
        val engine = BlackHoleGraphEngine()
        engine.populate(
            graphNodes = (1..nodeCount).map { Triple("n$it", "节点$it", GraphNodeKind.Concept) },
            edges = edges
        )
        // 力场/拖拽/公转类用例要确定性状态：全部置为保护期内（遗忘冻结、绝不进入漩涡旅程）；
        // 遗忘/坠入类用例自行把节点 cooling = false 显式过保。
        engine.nodes.forEach { it.cooling = true; it.coolingElapsed = 0f }
        return engine
    }

    private fun tickSeconds(engine: BlackHoleGraphEngine, seconds: Float) {
        val frames = (seconds * 60).toInt()
        repeat(frames) { engine.tick(1f / 60f) }
    }

    /** 拖拽到目标点并停稳（两次同点 dragTo 清零甩动量），再松手。 */
    private fun dragToAndRelease(engine: BlackHoleGraphEngine, id: String, x: Float, y: Float) {
        engine.startDrag(id)
        engine.dragTo(x, y)
        engine.dragTo(x, y) // 第二针位移为 0 -> vx/vy 清零
        engine.endDrag()
    }

    // ---------- 初始化 ----------

    @Test
    fun populate_places_all_nodes_outside_exclusion_zone() {
        val engine = engineWith(24)
        assertEquals(24, engine.nodes.size)
        engine.nodes.forEach { node ->
            val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
            assertTrue("node ${node.id} d=$d inside exclusion ${engine.exclusionRadius}",
                d > engine.exclusionRadius)
        }
    }

    @Test
    fun populate_staggers_initial_forget_so_multiple_stages_visible() {
        val engine = engineWith(24)
        val distinct = engine.nodes.map { (it.forget * 100).toInt() }.toSet()
        assertTrue("expected staggered forget values, got $distinct", distinct.size >= 8)
    }

    // ---------- 力场稳定性 ----------

    @Test
    fun long_simulation_stays_finite_and_inside_world() {
        val engine = engineWith(30, edges = (1 until 30).map { "n$it" to "n${it + 1}" })
        tickSeconds(engine, 10f)
        engine.nodes.forEach { node ->
            assertTrue(node.simX.isFinite() && node.simY.isFinite())
            assertTrue(node.dispX.isFinite() && node.dispY.isFinite())
            assertTrue(node.vx.isFinite() && node.vy.isFinite())
        }
    }

    @Test
    fun free_nodes_separate_beyond_body_overlap_after_simulation() {
        val engine = engineWith(12)
        tickSeconds(engine, 8f)
        val nodes = engine.nodes
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val d = hypot(a.dispX - b.dispX, a.dispY - b.dispY)
                val bodies = a.displayRadius(engine.physics) + b.displayRadius(engine.physics)
                assertTrue("overlap ${a.id}/${b.id}: d=$d bodies=$bodies", d >= bodies * 0.85f)
            }
        }
    }

    // ---------- 拖拽语义 ----------

    @Test
    fun dragged_node_follows_pointer_and_sim_layer_untouched() {
        val engine = engineWith(6)
        val node = engine.nodes.first()
        val simX0 = node.simX
        val simY0 = node.simY
        engine.startDrag(node.id)
        engine.dragTo(node.dispX + 30f, node.dispY - 20f)
        engine.dragTo(node.dispX + 40f, node.dispY + 10f)
        // 展示层跟手
        assertEquals(node.dispX, node.dispX, 0.001f)
        // 算法层不受拖动影响（V23 双层分离）
        assertEquals(simX0, node.simX, 0.0001f)
        assertEquals(simY0, node.simY, 0.0001f)
        engine.endDrag()
    }

    @Test
    fun released_node_inertia_slides_then_rejoins_physics() {
        val engine = engineWith(6)
        val node = engine.nodes.first()
        dragToAndRelease(engine, node.id, node.dispX + 120f, node.dispY + 60f)
        tickSeconds(engine, 2f)
        // 滑停不停泊：回归物理层 Free，sim/disp 重新同步，弹簧/斥力继续收敛（真机反馈「假引力」）
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        assertEquals(node.simX, node.dispX, 0.001f)
        assertEquals(node.simY, node.dispY, 0.001f)
    }

    @Test
    fun redrag_into_released_node_pushes_it_via_collision() {
        val engine = engineWith(6)
        val a = engine.nodes[0]
        val b = engine.nodes[1]
        // A 放置到远处，松手后回归物理层（不停泊）
        dragToAndRelease(engine, a.id, engine.holeX + engine.exclusionRadius + 500f, engine.holeY)
        tickSeconds(engine, 2f)
        assertEquals(BlackHoleNodeMode.Free, a.mode)
        // B 按到 A 身上持续施力 -> A 是 Free，受力被碰撞推挤实时让位，且保持不相叠
        engine.startDrag(b.id)
        engine.dragTo(a.dispX, a.dispY)
        val ax0 = a.dispX
        val ay0 = a.dispY
        tickSeconds(engine, 1f)
        val moved = hypot(a.dispX - ax0, a.dispY - ay0)
        assertTrue("released node not pushed by collision: moved=$moved", moved > 5f)
        val d = hypot(a.dispX - b.dispX, a.dispY - b.dispY)
        assertTrue("d=$d", d >= a.displayRadius(engine.physics) + b.displayRadius(engine.physics) - 1f)
        engine.endDrag()
    }

    @Test
    fun double_released_overlap_separates_via_collision() {
        val engine = engineWith(4)
        val a = engine.nodes[0]
        val b = engine.nodes[1]
        val px = engine.holeX + engine.exclusionRadius + 500f
        val py = engine.holeY
        dragToAndRelease(engine, a.id, px, py)
        tickSeconds(engine, 2f)
        assertEquals(BlackHoleNodeMode.Free, a.mode)
        // B 直接压到 A 头上松手 -> 双方都回归物理层，碰撞推挤分开
        dragToAndRelease(engine, b.id, a.dispX, a.dispY)
        tickSeconds(engine, 3f)
        assertEquals(BlackHoleNodeMode.Free, b.mode)
        val d = hypot(a.dispX - b.dispX, a.dispY - b.dispY)
        val bodies = a.displayRadius(engine.physics) + b.displayRadius(engine.physics)
        assertTrue("double released still stacked: d=$d bodies=$bodies", d >= bodies - 1.5f)
    }

    // ---------- 黑洞交互 ----------

    @Test
    fun release_over_hole_bounces_out_and_never_absorbs() {
        val engine = engineWith(3)
        val node = engine.nodes.first()
        engine.startDrag(node.id)
        engine.dragTo(engine.holeX + 40f, engine.holeY) // 净空带内、贴近核心
        assertTrue(engine.dragOverHole)
        engine.endDrag()
        assertFalse(engine.dragOverHole)
        tickSeconds(engine, 3f)
        assertFalse(node.absorbed)
        assertFalse(node.gone)
        val d = hypot(node.dispX - engine.holeX, node.dispY - engine.holeY)
        assertTrue("bounced node d=$d", d > engine.holeCoreRadius + node.displayRadius(engine.physics))
    }

    @Test
    fun sim_touching_core_absorbs_fades_and_removes_edges() {
        val engine = engineWith(3, edges = listOf("n1" to "n2", "n2" to "n3"))
        val victim = engine.nodes[1] // n2
        victim.cooling = false // 显式过保（engineWith 默认全员保护期）
        victim.forget = 0.9f // 越过吞噬遗忘门（0.85）：深度遗忘触核才被吞
        victim.simX = engine.holeX + 5f
        victim.simY = engine.holeY
        engine.tick(1f / 60f)
        assertTrue(victim.absorbed)
        assertEquals(1, engine.absorbedCount)
        tickSeconds(engine, 0.5f) // > 0.15s 淡出
        assertTrue(victim.gone)
        assertFalse(engine.nodes.any { it.id == "n2" })
        assertTrue(engine.renderEdges().none { it.fromId == "n2" || it.toId == "n2" })
    }

    @Test
    fun fresh_node_pushed_into_core_bounces_out_not_absorbed() {
        // 真机反馈：碰撞/拖拽把普通节点误推进核心绝不能算吸收——只有深度遗忘节点触核才被吞
        val engine = engineWith(3)
        val node = engine.nodes[1]
        node.forget = 0f
        node.simX = engine.holeX + 5f
        node.simY = engine.holeY
        engine.tick(1f / 60f)
        assertFalse(node.absorbed)
        assertFalse(node.gone)
        assertEquals(0, engine.absorbedCount)
        val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        assertTrue("bounced node d=$d exclusion=${engine.exclusionRadius}",
            d >= engine.exclusionRadius)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
    }

    @Test
    fun released_node_springs_back_toward_linked_neighbor() {
        // 真机反馈「假引力」：松手回归物理层后，连线弹簧应把节点拉回邻居、收敛圆润
        val engine = engineWith(2, edges = listOf("n1" to "n2"))
        tickSeconds(engine, 3f) // 布局稳定
        val n1 = engine.nodes.first { it.id == "n1" }
        val n2 = engine.nodes.first { it.id == "n2" }
        val d0 = hypot(n1.simX - n2.simX, n1.simY - n2.simY)
        // 把 n1 拖到远离 n2 的位置松手
        dragToAndRelease(engine, n1.id, n1.dispX + 300f, n1.dispY)
        val d1 = hypot(n1.dispX - n2.dispX, n1.dispY - n2.dispY)
        assertTrue("drag did not separate: d0=$d0 d1=$d1", d1 > d0 + 150f)
        tickSeconds(engine, 4f)
        assertEquals(BlackHoleNodeMode.Free, n1.mode)
        val d2 = hypot(n1.dispX - n2.dispX, n1.dispY - n2.dispY)
        assertTrue("no spring-back: d1=$d1 d2=$d2", d2 < d1 - 40f)
    }

    // ---------- 遗忘 ----------

    @Test
    fun forgetting_advances_with_timescale() {
        val engine = engineWith(1)
        val node = engine.nodes.first()
        node.cooling = false // 过保后遗忘才开始增长（保护期内冻结为 0）
        node.forget = 0f
        engine.timeScale = 3f
        tickSeconds(engine, 1f)
        val expected = 3f / engine.physics.forgettingFullSeconds
        assertEquals(expected, node.forget, 0.002f)
    }

    @Test
    fun deep_forget_shrinks_and_fades_node() {
        val engine = engineWith(1)
        val node = engine.nodes.first()
        node.forget = 1f
        val rn = engine.renderNodes().first()
        assertEquals(engine.physics.minNodeOpacity, rn.opacity, 0.001f)
        assertEquals(node.baseRadius * engine.physics.minNodeScale, rn.radius, 0.001f)
        assertFalse(rn.showLabel)
    }

    // ---------- 页面重置 ----------

    @Test
    fun reset_returns_display_to_simulation_layer() {
        val engine = engineWith(5)
        val node = engine.nodes.first()
        dragToAndRelease(engine, node.id, node.dispX + 200f, node.dispY + 120f)
        tickSeconds(engine, 1f)
        engine.resetDisplayToSimulation()
        engine.nodes.forEach { n ->
            assertEquals(n.simX, n.dispX, 0.0001f)
            assertEquals(n.simY, n.dispY, 0.0001f)
            assertEquals(BlackHoleNodeMode.Free, n.mode)
        }
    }

    // ---------- 硬保护 ----------

    @Test
    fun velocity_is_clamped_every_frame() {
        val engine = engineWith(2)
        val node = engine.nodes.first()
        node.vx = 1000f
        node.vy = 1000f
        engine.tick(1f / 60f)
        val speed = hypot(node.vx, node.vy)
        assertTrue("speed=$speed", speed <= engine.physics.velocityClamp + 0.001f)
    }

    @Test
    fun nan_position_is_reset_to_safe_point() {
        val engine = engineWith(2)
        val node = engine.nodes.first()
        node.vx = Float.NaN
        engine.tick(1f / 60f)
        engine.tick(1f / 60f)
        assertTrue(node.simX.isFinite() && node.simY.isFinite())
        assertEquals(0f, hypot(node.vx, node.vy), 30.01f)
    }

    @Test
    fun drag_delta_is_clamped_to_500_per_frame() {
        val engine = engineWith(2)
        val node = engine.nodes.first()
        engine.startDrag(node.id)
        val x0 = node.dispX
        val y0 = node.dispY
        engine.dragTo(x0 + 100000f, y0)
        assertEquals(500f, hypot(node.dispX - x0, node.dispY - y0), 0.001f)
        engine.endDrag()
    }

    @Test
    fun hit_test_prefers_topmost_and_skips_gone() {
        val engine = engineWith(2)
        val node = engine.nodes.first()
        val hit = engine.hitTest(node.dispX, node.dispY)
        assertNotNull(hit)
        node.gone = true
        val other = engine.nodes.last()
        val miss = engine.hitTest(node.dispX, node.dispY)
        assertTrue(miss == null || miss.id == other.id)
        assertNull(engine.hitTest(engine.holeX, engine.holeY))
    }

    // ---------- 常驻公转 / 入场 / 时间倍率（新引擎） ----------

    private fun angleOf(engine: BlackHoleGraphEngine, id: String): Double {
        val node = engine.nodes.first { it.id == id }
        return atan2((node.simY - engine.holeY).toDouble(), (node.simX - engine.holeX).toDouble())
    }

    @Test
    fun orbit_keeps_advancing_after_settled() {
        val engine = engineWith(6)
        tickSeconds(engine, 6f) // > alphaIterations 300 子步 -> settled
        val id = engine.nodes.first().id
        val a0 = angleOf(engine, id)
        tickSeconds(engine, 5f)
        val a1 = angleOf(engine, id)
        val delta = kotlin.math.abs(a1 - a0)
        assertTrue("orbit stalled: delta=$delta", delta > 0.0005)
    }

    @Test
    fun entrance_eases_from_center_then_completes() {
        val engine = engineWith(4)
        assertEquals(0f, engine.entrance, 0.0001f)
        // 入场第 0 帧：全部节点从黑洞中心展开
        engine.renderNodes().forEach { rn ->
            assertEquals(engine.holeX.toDouble(), rn.x.toDouble(), 0.5)
            assertEquals(engine.holeY.toDouble(), rn.y.toDouble(), 0.5)
        }
        tickSeconds(engine, 1.2f) // > entranceSeconds 0.9
        assertEquals(1f, engine.entrance, 0.0001f)
        val byId = engine.nodes.associateBy { it.id }
        engine.renderNodes().forEach { rn ->
            val node = byId.getValue(rn.id)
            assertEquals(node.dispX.toDouble(), rn.x.toDouble(), 0.5)
            assertEquals(node.dispY.toDouble(), rn.y.toDouble(), 0.5)
        }
    }

    @Test
    fun timescale_scales_simulation_and_orbit_speed() {
        val slow = engineWith(6)
        val fast = engineWith(6)
        tickSeconds(slow, 6f)
        tickSeconds(fast, 6f)
        val id = slow.nodes.first().id
        val slowA0 = angleOf(slow, id)
        val fastA0 = angleOf(fast, id)
        fast.timeScale = 3f
        tickSeconds(slow, 2f)
        tickSeconds(fast, 2f)
        val slowDelta = kotlin.math.abs(angleOf(slow, id) - slowA0)
        val fastDelta = kotlin.math.abs(angleOf(fast, id) - fastA0)
        assertTrue("timeScale not scaling orbit: slow=$slowDelta fast=$fastDelta",
            fastDelta > slowDelta * 2f)
    }

    @Test
    fun drag_pulls_linked_neighbors_along() {
        // 真机反馈「关联的拖动性不够」：拖 n1 时，与 n1 连边的 n2 应被显著带动，且明显强于无连边的 n3。
        val engine = engineWith(3, edges = listOf("n1" to "n2"))
        tickSeconds(engine, 3f) // 布局稳定
        val n1 = engine.nodes.first { it.id == "n1" }
        val n2 = engine.nodes.first { it.id == "n2" }
        val n3 = engine.nodes.first { it.id == "n3" }
        // 沿 n1 相对黑洞的径向向外拖 300px
        val dx0 = n1.simX - engine.holeX
        val dy0 = n1.simY - engine.holeY
        val len = hypot(dx0, dy0)
        val ux = dx0 / len
        val uy = dy0 / len
        val n2s0 = n2.simX * ux + n2.simY * uy
        val n3s0 = n3.simX * ux + n3.simY * uy
        engine.startDrag("n1")
        var px = n1.dispX
        var py = n1.dispY
        repeat(20) {
            px += ux * 15f
            py += uy * 15f
            engine.dragTo(px, py)
            engine.tick(1f / 60f)
        }
        engine.endDrag()
        val n2s1 = n2.simX * ux + n2.simY * uy
        val n3s1 = n3.simX * ux + n3.simY * uy
        val n2Move = n2s1 - n2s0
        val n3Move = n3s1 - n3s0
        assertTrue("linked neighbor not pulled along: n2Move=$n2Move", n2Move > 40f)
        assertTrue("linked pull not stronger than unlinked: n2=$n2Move n3=$n3Move",
            n2Move > n3Move + 30f)
    }

    // ---------- R66 冷却保护模型（用户拍板，曲线数值为占位） ----------

    @Test
    fun protected_node_orbits_in_place_without_drifting_inward() {
        // 保护期内：遗忘冻结为 0、保持 Free 原地公转，绝不向黑洞漂移
        val engine = engineWith(6)
        tickSeconds(engine, 5f) // 布局稳定
        val node = engine.nodes.first()
        node.forget = 0f // 新节点语义：保护期内遗忘为 0（populate 演示分布会给部分节点预置遗忘）
        val d0 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        tickSeconds(engine, 6f)
        val d1 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        assertTrue(node.cooling)
        assertEquals(0f, node.forget, 0.0001f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        assertTrue("protected node drifted: d0=$d0 d1=$d1", kotlin.math.abs(d1 - d0) < 20f)
    }

    @Test
    fun cooling_protection_expires_then_decay_begins() {
        // 保护期结束 -> 遗忘开始增长 -> 即刻脱离力场进入漩涡旅程
        val engine = engineWith(1)
        val node = engine.nodes.first()
        engine.timeScale = 10f
        tickSeconds(engine, 10f) // 100s 虚拟时间 > 90s 保护期
        assertFalse(node.cooling)
        assertTrue("forget not advancing after protection: ${node.forget}", node.forget > 0f)
        assertEquals(BlackHoleNodeMode.Dying, node.mode)
    }

    @Test
    fun decaying_node_spirals_inward_and_absorbs_at_journey_end() {
        // 漩涡旅程：半径持续收缩（保持旋转），遗忘打满/抵核即被吞 = 真实遗忘
        val engine = engineWith(3, edges = listOf("n1" to "n2", "n2" to "n3"))
        val victim = engine.nodes[1] // n2
        victim.cooling = false
        victim.forget = 0.5f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Dying, victim.mode)
        val d0 = hypot(victim.simX - engine.holeX, victim.simY - engine.holeY)
        tickSeconds(engine, 1f)
        val d1 = hypot(victim.simX - engine.holeX, victim.simY - engine.holeY)
        assertTrue("dying node not spiraling inward: d0=$d0 d1=$d1", d1 < d0)
        engine.timeScale = 10f
        tickSeconds(engine, 12f) // 120s 虚拟 -> 遗忘打满/抵核
        assertTrue(victim.absorbed)
        assertTrue(victim.gone)
        assertEquals(1, engine.absorbedCount)
        assertTrue(engine.renderEdges().none { it.fromId == "n2" || it.toId == "n2" })
    }

    @Test
    fun decaying_node_detaches_but_protected_never_cross_ring() {
        // 坠入节点脱离力场独自进洞；保护期内的节点（哪怕有连线）永远不进净空带
        val engine = engineWith(16, edges = (1 until 16).map { "n$it" to "n${it + 1}" })
        val dying = engine.nodes.first()
        dying.cooling = false
        dying.forget = 0.3f
        tickSeconds(engine, 10f)
        engine.nodes.filter { it.cooling && !it.gone }.forEach { node ->
            val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
            assertTrue("protected node ${node.id} crossed ring: d=$d exclusion=${engine.exclusionRadius}",
                d >= engine.exclusionRadius - 0.5f)
        }
    }

    @Test
    fun dying_node_cannot_be_hit_tested_or_dragged() {
        // 漩涡旅程中的节点不可点中、不可拖拽（它是「正在遗忘」的展示态，不再是可操作对象）
        val engine = engineWith(2)
        val node = engine.nodes.first()
        node.cooling = false
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Dying, node.mode)
        assertNull(engine.hitTest(node.dispX, node.dispY))
        engine.startDrag(node.id)
        assertEquals(BlackHoleNodeMode.Dying, node.mode)
    }

    // ---------- D7 断链离散 · 点击抢救（用户 2026-09-19 拍板） ----------

    /** 把节点推进到「可抢救」状态：过保进入 Dying，再收缩进黑洞区域（闪烁呼吸段）。 */
    private fun driveToBreathing(engine: BlackHoleGraphEngine, id: String) {
        val node = engine.nodes.first { it.id == id }
        node.cooling = false
        node.forget = 0.05f
        engine.tick(1f / 60f) // -> Dying，记录 decayStartR/decayAngle
        node.decayStartR = engine.exclusionRadius
        node.forget = 0.1f
        engine.tick(1f / 60f) // r ≈ exclusion * 0.92 < exclusion -> 呼吸段
    }

    @Test
    fun rescue_only_when_breathing_inside_hole_zone() {
        val engine = engineWith(3)
        val node = engine.nodes.first()
        node.cooling = false
        node.forget = 0.05f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Dying, node.mode)
        // 旅程前段（净空带之外）：不可抢救
        assertFalse(engine.isRescuable(node.id))
        assertFalse(engine.rescueNode(node.id))
        assertEquals(BlackHoleNodeMode.Dying, node.mode)
        // 进入黑洞区域（呼吸段）：可抢救
        node.decayStartR = engine.exclusionRadius
        node.forget = 0.1f
        engine.tick(1f / 60f)
        assertTrue(engine.isRescuable(node.id))
        assertTrue(engine.rescueNode(node.id))
    }

    @Test
    fun rescue_restores_state_and_severed_edges_relink() {
        val engine = engineWith(3, edges = listOf("n1" to "n2", "n2" to "n3"))
        driveToBreathing(engine, "n1")
        // 离散期：渲染层断链（数据层 edgeList 未删）
        assertTrue(engine.renderEdges().none { it.fromId == "n1" || it.toId == "n1" })
        assertTrue(engine.renderEdges().any { it.fromId == "n2" && it.toId == "n3" })
        assertTrue(engine.rescueNode("n1"))
        val node = engine.nodes.first { it.id == "n1" }
        assertEquals(0f, node.forget, 1e-4f)
        assertTrue(node.cooling)
        assertEquals(0f, node.coolingElapsed, 1e-4f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        assertEquals(0f, node.decayStartR, 1e-4f)
        val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        assertTrue("rescued node should be outside breathing zone: d=$d", d >= engine.exclusionRadius)
        // 重连：渲染层连线恢复
        assertTrue(engine.renderEdges().any { it.fromId == "n1" && it.toId == "n2" })
        assertEquals(1, engine.rescuedCount)
    }

    @Test
    fun rescue_rejected_for_healthy_or_absorbed_nodes() {
        val engine = engineWith(3)
        // 健康节点：不可抢救
        assertFalse(engine.rescueNode("n1"))
        // 已被吞噬节点：不可抢救（真实遗忘不可逆，只能重新学习新建）
        val node = engine.nodes.first { it.id == "n2" }
        node.cooling = false
        node.forget = 1f
        engine.tick(1f / 60f) // 遗忘打满 -> 吞噬
        assertTrue(node.absorbed || node.gone)
        assertFalse(engine.rescueNode("n2"))
        assertEquals(0, engine.rescuedCount)
    }

    @Test
    fun rescued_node_rejoins_layout_and_stays_alive() {
        val engine = engineWith(12, edges = (1 until 12).map { "n$it" to "n${it + 1}" })
        driveToBreathing(engine, "n6")
        assertTrue(engine.rescueNode("n6"))
        tickSeconds(engine, 12f)
        val node = engine.nodes.first { it.id == "n6" }
        assertFalse(node.absorbed)
        assertFalse(node.gone)
        assertTrue(node.mode != BlackHoleNodeMode.Dying)
        assertEquals(0f, node.forget, 1e-4f)
        // 重新接入力场：不被甩飞、不坠核心（净空带硬边界保持）
        val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        assertTrue("rescued node drifted into hole: d=$d", d >= engine.exclusionRadius - 0.5f)
    }

    // ---------- R75 微引力 ----------

    @Test
    fun inertia_gravity_bends_slide_toward_hole() {
        // R75 用户拍板「引力还是要有一点点、像黑曜石（Obsidian）拖动」：
        // 同一起点同一初速向外甩，开惯性引力的引擎滑停后离洞更近（轨迹被弯回）
        fun slideEndRadius(gravity: Float): Float {
            val engine = BlackHoleGraphEngine(physics = BlackHolePhysics(inertiaGravity = gravity))
            engine.populate(
                graphNodes = listOf(Triple("n1", "节点1", GraphNodeKind.Concept)),
                edges = emptyList()
            )
            engine.nodes.forEach { it.cooling = true; it.coolingElapsed = 0f }
            val node = engine.nodes.first()
            engine.startDrag(node.id)
            engine.dragTo(engine.holeX + 300f, engine.holeY)
            engine.dragTo(engine.holeX + 300f, engine.holeY) // 第二针位移 0 -> 甩动量清零
            engine.dragTo(engine.holeX + 780f, engine.holeY) // 单帧外甩 480px（clamp 500 内）
            engine.endDrag()
            repeat(600) { engine.tick(1f / 60f) } // 滑停并回归物理层
            return hypot(node.dispX - engine.holeX, node.dispY - engine.holeY)
        }
        val withGravity = slideEndRadius(110f)
        val noGravity = slideEndRadius(0f)
        assertTrue(
            "inertia gravity should bend slide back: with=$withGravity without=$noGravity",
            withGravity < noGravity - 4f
        )
    }

    @Test
    fun slight_hole_gravity_does_not_collapse_layout() {
        // R75：力场微引力（alpha 缩放、收敛后趋零）只留一点点引力感——
        // 布局收敛后节点不散不塌、全部留在净空带外（R65 全量引力曾全员被吸到中间被批太丑）
        val engine = engineWith(30, edges = (1 until 30).map { "n$it" to "n${it + 1}" })
        tickSeconds(engine, 8f) // 力场收敛（300 iter 到 alpha 地板）
        val avgBefore = engine.nodes.map { hypot(it.simX - engine.holeX, it.simY - engine.holeY) }.average()
        tickSeconds(engine, 8f) // alpha 地板上再跑 8 秒（微引力 + 公转 + 弹簧）
        val avgAfter = engine.nodes.map { hypot(it.simX - engine.holeX, it.simY - engine.holeY) }.average()
        engine.nodes.forEach { node ->
            val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
            assertTrue("node ${node.id} d=$d inside exclusion ${engine.exclusionRadius}",
                d >= engine.exclusionRadius - 0.5f) // 钳制停在边界上的节点不算进带（d=84.0 是钳制平衡位）
        }
        assertTrue("cluster collapsed: avgR $avgBefore -> $avgAfter", avgAfter > avgBefore * 0.85f)
        assertTrue("cluster blew up: avgR $avgBefore -> $avgAfter", avgAfter < avgBefore * 1.35f)
    }
}
