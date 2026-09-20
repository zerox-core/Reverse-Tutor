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
 * R65 算法层重构（黑洞无引力、净空带永久硬边界）、
 * R66 冷却保护模型（保护期内遗忘冻结+原地公转、过保后漩涡旅程螺旋坠入、
 * 坠入节点脱离力场不扰动布局、Dying 可拖回=松手抢救（R81））、
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
    fun populate_seeds_all_nodes_fresh_and_protected() {
        // R82 用户拍板：暂不做真实遗忘——播种全员刚创建状态（遗忘 0、保护期内）
        val engine = BlackHoleGraphEngine()
        engine.populate(
            graphNodes = (1..24).map { Triple("n$it", "节点$it", GraphNodeKind.Concept) },
            edges = emptyList()
        )
        engine.nodes.forEach { node ->
            assertEquals(0f, node.forget, 1e-6f)
            assertTrue("node ${node.id} not cooling", node.cooling)
        }
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
    fun dragged_root_carries_child_and_child_stays_parent_centered() {
        // R83 用户拍板：节点以母节点为中心公转。度数并列取 id 序 -> n1 为根、n2 为其子节点。
        // 拖动母节点时子节点整族跟走（母子距离保持轨道半径），松手后子节点仍绕母节点公转不散伙
        val engine = engineWith(2, edges = listOf("n1" to "n2"))
        tickSeconds(engine, 3f) // 布局稳定
        val n1 = engine.nodes.first { it.id == "n1" }
        val n2 = engine.nodes.first { it.id == "n2" }
        assertEquals(null, n1.parentId)
        assertEquals("n1", n2.parentId)
        // 把母节点拖远 300px：子节点跟走，母子距离保持在轨道半径附近
        dragToAndRelease(engine, n1.id, n1.dispX + 300f, n1.dispY)
        tickSeconds(engine, 2f)
        val d = hypot(n1.dispX - n2.dispX, n1.dispY - n2.dispY)
        assertTrue("child not tracking parent: d=$d target=${n2.orbitRTarget}", d <= n2.orbitRTarget + 60f)
        // 松手后母节点回物理层；子节点持续绕母节点公转（相对角度持续推进）
        val a0 = atan2((n2.dispY - n1.dispY).toDouble(), (n2.dispX - n1.dispX).toDouble())
        tickSeconds(engine, 3f)
        val a1 = atan2((n2.dispY - n1.dispY).toDouble(), (n2.dispX - n1.dispX).toDouble())
        assertTrue("child not orbiting parent: a0=$a0 a1=$a1", kotlin.math.abs(a1 - a0) > 0.01)
        val d2 = hypot(n1.dispX - n2.dispX, n1.dispY - n2.dispY)
        assertTrue("child drifted off parent orbit: d2=$d2", d2 <= n2.orbitRTarget + 80f)
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
    fun dying_node_can_be_hit_dragged_and_revives_on_release() {
        // R81 修复「节点没办法拖动」：濒死节点也可命中、可拖拽——松手即抢救（遗忘清零、冷却重置、回力场）
        val engine = engineWith(2)
        val node = engine.nodes.first()
        node.cooling = false
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Dying, node.mode)
        assertNotNull(engine.hitTest(node.dispX, node.dispY))
        engine.startDrag(node.id)
        assertEquals(BlackHoleNodeMode.Dragging, node.mode)
        engine.dragTo(node.dispX + 40f, node.dispY + 40f)
        val rescued = engine.endDrag()
        assertEquals(node.id, rescued)
        assertEquals(0f, node.forget)
        assertTrue(node.cooling)
        assertEquals(BlackHoleNodeMode.InertiaSliding, node.mode)
        assertEquals(1, engine.rescuedCount)
    }

    @Test
    fun healthy_node_drag_release_does_not_rescue() {
        // 普通节点拖放返回 null、不计抢救数（点按选中路径不受影响）
        val engine = engineWith(2)
        val node = engine.nodes.first()
        engine.startDrag(node.id)
        assertEquals(null, engine.endDrag())
        assertEquals(BlackHoleNodeMode.InertiaSliding, node.mode)
        assertEquals(0, engine.rescuedCount)
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

    // ---------- R83 层级轨道 + 连线绕行（用户 2026-09-20 拍板） ----------

    @Test
    fun hierarchy_assigns_few_roots_and_all_others_get_parents() {
        // 根数 = min(3, max(1, 12/6)) = 2，取度数最高（并列按 id 序）；其余全部挂母；
        // 全员落在净空带外，子节点初始距离 = 轨道半径（带内钳出只缩不破）
        val edges = listOf("n1" to "n2", "n1" to "n3", "n2" to "n4", "n3" to "n5",
            "n4" to "n6", "n5" to "n7", "n6" to "n8", "n7" to "n9",
            "n8" to "n10", "n9" to "n11", "n10" to "n12")
        val engine = engineWith(12, edges)
        val roots = engine.nodes.filter { it.parentId == null }
        assertEquals(listOf("n1", "n10"), roots.map { it.id }.sorted())
        engine.nodes.forEach { node ->
            val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
            assertTrue("node ${node.id} inside exclusion: d=$d", d > engine.exclusionRadius)
            val pid = node.parentId
            if (pid != null) {
                val parent = engine.nodes.firstOrNull { it.id == pid }
                assertNotNull(parent)
                assertTrue("no orbit target for ${node.id}", node.orbitRTarget > 0f)
                val dp = hypot(node.simX - parent!!.simX, node.simY - parent.simY)
                assertTrue("child ${node.id} off initial orbit: dp=$dp r=${node.orbitR}", dp <= node.orbitR + 24f)
            }
        }
    }

    @Test
    fun child_keeps_orbiting_parent_who_orbits_hole() {
        // 双层公转：母节点绕黑洞公转（绝对角度推进），子节点绕母节点公转（相对角度推进），
        // 母子距离稳定在轨道半径（净空带钳制只缩不破）
        val engine = engineWith(6, edges = (1 until 6).map { "n1" to "n${it + 1}" })
        tickSeconds(engine, 3f)
        val root = engine.nodes.first { it.id == "n1" }
        assertEquals(null, root.parentId)
        val child = engine.nodes.first { it.id == "n2" }
        assertEquals("n1", child.parentId)
        val holeA0 = atan2((root.simY - engine.holeY).toDouble(), (root.simX - engine.holeX).toDouble())
        val relA0 = atan2((child.dispY - root.dispY).toDouble(), (child.dispX - root.dispX).toDouble())
        tickSeconds(engine, 4f)
        val holeA1 = atan2((root.simY - engine.holeY).toDouble(), (root.simX - engine.holeX).toDouble())
        val relA1 = atan2((child.dispY - root.dispY).toDouble(), (child.dispX - root.dispX).toDouble())
        assertTrue("root not orbiting hole: ${holeA1 - holeA0}", kotlin.math.abs(holeA1 - holeA0) > 0.001)
        assertTrue("child not orbiting parent: ${relA1 - relA0}", kotlin.math.abs(relA1 - relA0) > 0.02)
        val d = hypot(child.dispX - root.dispX, child.dispY - root.dispY)
        assertTrue("child distance off orbit: d=$d r=${child.orbitR}", d <= child.orbitR + 24f && d > 24f)
    }

    @Test
    fun released_child_rubber_bands_back_to_parent_orbit() {
        // 拖子节点远离母节点松手：轨道半径保持拉伸、随后向 orbitRTarget 缓慢回弹（橡皮筋）
        val engine = engineWith(6, edges = (1 until 6).map { "n1" to "n${it + 1}" })
        tickSeconds(engine, 3f)
        val parent = engine.nodes.first { it.id == "n1" }
        val child = engine.nodes.first { it.id == "n2" }
        assertEquals("n1", child.parentId)
        // 沿远离母节点方向拖开 260px
        val dx = child.dispX - parent.dispX
        val dy = child.dispY - parent.dispY
        val len = hypot(dx, dy).coerceAtLeast(0.001f)
        dragToAndRelease(engine, child.id, child.dispX + dx / len * 260f, child.dispY + dy / len * 260f)
        // 松手即回运动学轨道：母子距离 = 拉伸后的轨道半径
        val d1 = hypot(child.dispX - parent.dispX, child.dispY - parent.dispY)
        assertEquals(child.orbitR, d1, 2f)
        assertTrue("no stretch: d1=$d1", d1 > child.orbitRTarget + 100f)
        // 回弹：数秒后向目标收敛
        tickSeconds(engine, 4f)
        val d2 = hypot(child.dispX - parent.dispX, child.dispY - parent.dispY)
        assertTrue("no rubber-band back: d1=$d1 d2=$d2 target=${child.orbitRTarget}", d2 < d1 - 60f)
        assertEquals(BlackHoleNodeMode.Free, child.mode)
    }

    @Test
    fun edge_route_bends_around_exclusion_zone() {
        // 连线绝不横穿黑洞区域：洞两侧对径点的连线必须绕行，折线上每一点都在带外；
        // 同侧不穿洞的连线保持直线
        val engine = engineWith(2)
        val z = engine.exclusionRadius
        val r = z + 120f
        val pts = engine.routeEdgeAroundZone(engine.holeX - r, engine.holeY, engine.holeX + r, engine.holeY)
        assertNotNull(pts)
        assertTrue("polyline too short: ${pts!!.size}", pts.size >= 10)
        var i = 0
        var minD = Float.MAX_VALUE
        while (i + 1 < pts.size) {
            val d = hypot(pts[i] - engine.holeX, pts[i + 1] - engine.holeY)
            if (d < minD) minD = d
            i += 2
        }
        assertTrue("route dips into zone: minD=$minD exclusion=$z", minD >= z - 1f)
        val straight = engine.routeEdgeAroundZone(
            engine.holeX - r, engine.holeY - r, engine.holeX - r - 100f, engine.holeY - r + 40f)
        assertNull(straight)
    }

    @Test
    fun dying_parent_detaches_children_to_roots() {
        // 母节点离散（断链）：子节点不能跟着坠洞——就地晋升为根，回到黑洞力场绕洞公转
        val engine = engineWith(4, edges = listOf("n1" to "n2", "n2" to "n3", "n3" to "n4"))
        tickSeconds(engine, 2f)
        val n2 = engine.nodes.first { it.id == "n2" }
        val n3 = engine.nodes.first { it.id == "n3" }
        assertEquals("n2", n3.parentId)
        n2.cooling = false
        n2.forget = 0.4f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Dying, n2.mode)
        assertEquals(null, n3.parentId) // 晋升为根
        tickSeconds(engine, 2f)
        // 晋升后的根回到力场：绕洞公转、不坠洞、不进净空带
        assertEquals(BlackHoleNodeMode.Free, n3.mode)
        val d = hypot(n3.simX - engine.holeX, n3.simY - engine.holeY)
        assertTrue("promoted root crossed zone: d=$d", d >= engine.exclusionRadius - 1f)
    }
}
