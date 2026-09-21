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
import kotlin.math.PI

/**
 * 黑洞图谱引擎单元测试（V12~V30 定稿规格的落地验收 + 真机反馈整改）。
 * 覆盖：初始布局、力场稳定性、拖拽语义、alpha 平台、松手回归物理层（不停泊）、
 * 弹簧收敛圆润、黑洞弹开不吞噬、吞噬遗忘门（碰撞误入核心只弹不吞）、
 * R87 开普勒引力模型（真实引力 F=GM/d² + 出生自带圆轨道速度 v=√(GM/d)；
 * 遗忘=切向刹车 → 轨道渐进内旋：活动区 → 遗忘区 → 坠入核心；屏障随遗忘渗透；
 * 衰减节点不脱离力场、可拖回=松手抢救（R81）、点按抢救=重新注入轨道速度）、
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

    @Test
    fun positional_correction_separates_overlap_without_kicking() {
        // R88：位置级碰撞修正——重叠立即分开，且不注入速度（结构上不可能形成能量棘轮；
        // 力式碰撞在密集公转区持续踢动曾两次撕裂图谱，故改为此方案）
        val engine = engineWith(2)
        val a = engine.nodes[0]
        val b = engine.nodes[1]
        val px = engine.holeX + engine.exclusionRadius + 600f
        val py = engine.holeY
        a.mode = BlackHoleNodeMode.Free
        b.mode = BlackHoleNodeMode.Free
        a.simX = px; a.simY = py
        b.simX = px + 4f; b.simY = py // 几乎完全重叠
        a.vx = 0f; a.vy = 0f; b.vx = 0f; b.vy = 0f
        a.dispX = a.simX; a.dispY = a.simY
        b.dispX = b.simX; b.dispY = b.simY
        val minD = a.displayRadius(engine.physics) + b.displayRadius(engine.physics) +
            engine.physics.collisionPadding
        engine.tick(1f / 60f)
        val d1 = hypot(a.simX - b.simX, a.simY - b.simY)
        assertTrue("positional correction did not separate: d=$d1 minD=$minD", d1 >= minD - 1f)
        // 继续跑 2s：保持分开，且速度留在公转量级（零初速下若被踢动会远超此值）
        tickSeconds(engine, 2f)
        val d2 = hypot(a.simX - b.simX, a.simY - b.simY)
        val bodies = a.displayRadius(engine.physics) + b.displayRadius(engine.physics)
        assertTrue("overlap returned: d=$d2 bodies=$bodies", d2 >= bodies - 0.5f)
        val sa = hypot(a.vx, a.vy)
        val sb = hypot(b.vx, b.vy)
        assertTrue("correction kicked node: |va|=$sa |vb|=$sb", sa < 0.5f && sb < 0.5f)
    }

    @Test
    fun kinematic_children_separate_via_orbit_reprojection() {
        // R88b：运动学子节点（绕母节点公转）位置每帧由 advanceChildren 按轨道重算，
        // 直接改 simX 会被覆写——两族卫星交叉穿过彼此时只能靠轨道重投影分开
        // （位移转 orbitAngle/orbitR），这是残留重叠的根因（Free 节点已由位置级修正保护）
        val engine = engineWith(6)
        val p1 = engine.nodes[0]
        val p2 = engine.nodes[1]
        val c1 = engine.nodes[2]
        val c2 = engine.nodes[3]
        val c3 = engine.nodes[4]
        val c4 = engine.nodes[5]
        // 两个家族：母节点同在一条水平线、相距 190（95+95 的轨道半径会让 c1/c2 世界坐标重合）
        val baseX = engine.holeX + engine.exclusionRadius + 500f
        for (p in listOf(p1, p2)) {
            p.mode = BlackHoleNodeMode.Free
            p.simY = engine.holeY; p.dispY = engine.holeY
            p.vx = 0f; p.vy = 0f
        }
        p1.simX = baseX; p1.dispX = baseX
        p2.simX = baseX + 190f; p2.dispX = p2.simX
        listOf(c1 to p1, c2 to p2, c3 to p1, c4 to p2).forEach { (c, p) ->
            c.mode = BlackHoleNodeMode.Free
            c.parentId = p.id
            c.vx = 0f; c.vy = 0f
        }
        // c1 在 p1 正右方 95、c2 在 p2 正左方 95 -> 完全重合；c3/c4 错开放置
        c1.orbitAngle = 0f; c1.orbitR = 95f; c1.orbitRTarget = 95f
        c2.orbitAngle = PI.toFloat(); c2.orbitR = 95f; c2.orbitRTarget = 95f
        c3.orbitAngle = 2.4f; c3.orbitR = 120f; c3.orbitRTarget = 120f
        c4.orbitAngle = 4.0f; c4.orbitR = 120f; c4.orbitRTarget = 120f
        val bodies = c1.displayRadius(engine.physics) + c2.displayRadius(engine.physics)
        engine.tick(1f / 60f)
        val d1 = hypot(c1.simX - c2.simX, c1.simY - c2.simY)
        assertTrue("kinematic children still overlapped: d=$d1 bodies=$bodies", d1 >= bodies - 1f)
        // 轨道重投影持久生效：逐帧采样渲染位置，2 秒内任何时刻都不允许两颗卫星身体重叠
        // （轨道半径回弹会把它们拉近 -> 每帧位置级修正再把重叠拆掉，渲染层永远不叠）
        repeat(120) {
            engine.tick(1f / 60f)
            val d = hypot(c1.simX - c2.simX, c1.simY - c2.simY)
            assertTrue("satellites overlap at frame $it: d=$d bodies=$bodies", d >= bodies - 0.5f)
        }
        // 家族结构未被破坏：子节点仍挂各自母节点、轨道半径有界
        assertEquals(p1.id, c1.parentId)
        assertEquals(p2.id, c2.parentId)
        assertTrue("orbitR out of band: ${c1.orbitR}", c1.orbitR > 0f && c1.orbitR < 200f)
        assertTrue("orbitR out of band: ${c2.orbitR}", c2.orbitR > 0f && c2.orbitR < 200f)
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
            d >= engine.exclusionRadius - 0.5f) // 钳制落点 float 精度容忍
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

    // ---------- R87 开普勒引力 · 冷却保护模型（用户拍板，曲线数值为占位） ----------

    @Test
    fun protected_node_orbits_in_place_without_drifting_inward() {
        // 保护期内：遗忘冻结为 0、保持 Free 原地公转，绝不向黑洞漂移
        val engine = engineWith(6)
        tickSeconds(engine, 5f) // 布局稳定
        val node = engine.nodes.first()
        node.forget = 0f // 新节点语义：保护期内遗忘为 0
        val d0 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        tickSeconds(engine, 6f)
        val d1 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        assertTrue(node.cooling)
        assertEquals(0f, node.forget, 0.0001f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        assertTrue("protected node drifted: d0=$d0 d1=$d1", kotlin.math.abs(d1 - d0) < 20f)
    }

    @Test
    fun root_nodes_are_born_with_circular_orbit_velocity() {
        // R87 开普勒模型：根节点出生自带圆轨道速度——切向 v=√(GM/d)、径向分量为 0
        val engine = BlackHoleGraphEngine()
        engine.populate(
            graphNodes = (1..24).map { Triple("n$it", "节点$it", GraphNodeKind.Concept) },
            edges = emptyList()
        )
        engine.nodes.filter { it.parentId == null }.forEach { node ->
            val dx = node.simX - engine.holeX
            val dy = node.simY - engine.holeY
            val d = hypot(dx, dy)
            val ux = dx / d
            val uy = dy / d
            val vr = node.vx * ux + node.vy * uy
            val vt = node.vx * uy + node.vy * (-ux) // t=(uy,-ux)，与注入方向一致
            assertEquals("root ${node.id} radial speed: $vr", 0f, vr, 0.01f)
            assertEquals("root ${node.id} vt=$vt d=$d",
                engine.circularOrbitSpeedAt(d), vt, 0.01f)
        }
    }

    @Test
    fun cooling_protection_expires_then_decay_begins() {
        // 保护期结束 -> 遗忘开始增长；R87：节点不脱离力场（保持 Free），切向刹车渐进内旋
        val engine = engineWith(1)
        val node = engine.nodes.first()
        engine.timeScale = 10f
        tickSeconds(engine, 10f) // 100s 虚拟时间 > 90s 保护期
        assertFalse(node.cooling)
        assertTrue("forget not advancing after protection: ${node.forget}", node.forget > 0f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
    }

    @Test
    fun decaying_node_brakes_tangential_velocity() {
        // R87 遗忘=刹车：切向速度被缓推向 v_circ(d)×(1-forget)
        val engine = engineWith(1)
        val node = engine.nodes.first()
        node.cooling = false
        node.forget = 0.5f
        val d0 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        val ux0 = (node.simX - engine.holeX) / d0
        val uy0 = (node.simY - engine.holeY) / d0
        val vt0 = node.vx * uy0 + node.vy * (-ux0)
        assertEquals(engine.circularOrbitSpeedAt(d0), vt0, 0.01f) // 出生即圆轨道速度
        tickSeconds(engine, 5f) // 正则化时间常数 ~1.7s -> 5s 已充分收敛向目标
        val d1 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        val ux1 = (node.simX - engine.holeX) / d1
        val uy1 = (node.simY - engine.holeY) / d1
        val vt1 = node.vx * uy1 + node.vy * (-ux1)
        assertTrue("no braking: vt0=$vt0 vt1=$vt1", vt1 < vt0 - 0.01f)
        assertTrue("node flew outward: d0=$d0 d1=$d1", d1 <= d0 + 1f)
    }

    @Test
    fun decaying_orbit_decays_inward_continuously() {
        // R87 轨道衰减：不脱离力场、不瞬移——连续渐进内旋（活动区 -> 遗忘区方向）
        val engine = engineWith(3, edges = listOf("n1" to "n2", "n2" to "n3"))
        val victim = engine.nodes[1] // n2（度数最高 -> 根节点）
        victim.cooling = false
        victim.forget = 0.6f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Free, victim.mode) // 衰减期仍是力场成员
        val d0 = hypot(victim.simX - engine.holeX, victim.simY - engine.holeY)
        tickSeconds(engine, 30f)
        val d1 = hypot(victim.simX - engine.holeX, victim.simY - engine.holeY)
        assertTrue("not decaying inward: d0=$d0 d1=$d1", d1 < d0 - 2f)
        assertFalse("decay must be gradual, not teleport-to-core", victim.absorbed)
    }

    @Test
    fun kicked_node_recovers_orbit_without_escaping() {
        // R87b 踢动保护：逃逸速度仅 √2×v_circ（≈4.2px/s），而碰撞/弹簧踢动可达
        // 60~600px/s——零切向阻尼下任何踢动都是双曲逃逸（真机实测全图节点 8s 飞光）。
        // 保护机制：|vt| > 1.3×v_circ 判定为踢动、改强阻尼放掉异常能量；
        // 节点应被引力重新捕获、留在黑洞附近恢复公转，而不是飞出画布。
        val engine = engineWith(1)
        val node = engine.nodes.first()
        val d0 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        val ux = (node.simX - engine.holeX) / d0
        val uy = (node.simY - engine.holeY) / d0
        val kick = 20f * engine.circularOrbitSpeedAt(d0) // 远超逃逸速度 √2×v_circ
        node.vx += uy * kick
        node.vy += (-ux) * kick
        tickSeconds(engine, 5f)
        val d1 = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        val ux1 = (node.simX - engine.holeX) / d1
        val uy1 = (node.simY - engine.holeY) / d1
        val vt1 = node.vx * uy1 + node.vy * (-ux1)
        assertTrue("node escaped: d0=$d0 d1=$d1", d1 < d0 + 200f)
        assertTrue(
            "orbit not recovered: vt1=$vt1 v_circ=${engine.circularOrbitSpeedAt(d1)}",
            kotlin.math.abs(vt1) < 2.5f * engine.circularOrbitSpeedAt(d1)
        )
    }

    @Test
    fun barrier_clamps_healthy_but_permeable_for_decaying() {
        // R87 屏障渗透：健康节点（保护期/遗忘未过门）放带内 -> 位置钳出；
        // 衰减过门节点（forget>=0.5）放带内 -> 不再钳制、不再外推，可停留在遗忘区
        val engine = engineWith(2)
        val healthy = engine.nodes[0]
        val decaying = engine.nodes[1]
        healthy.simX = engine.holeX + engine.exclusionRadius - 20f
        healthy.simY = engine.holeY
        engine.tick(1f / 60f)
        val dh = hypot(healthy.simX - engine.holeX, healthy.simY - engine.holeY)
        assertTrue("healthy node not clamped out: d=$dh", dh >= engine.exclusionRadius - 0.5f)
        decaying.cooling = false
        decaying.forget = 0.6f
        decaying.simX = engine.holeX - (engine.exclusionRadius - 20f) // 反向放置，远离 healthy 防碰撞干扰
        decaying.simY = engine.holeY
        engine.tick(1f / 60f)
        val dd = hypot(decaying.simX - engine.holeX, decaying.simY - engine.holeY)
        assertTrue("decaying node clamped/pushed out: d=$dd", dd < engine.exclusionRadius)
    }

    @Test
    fun decaying_node_detaches_but_protected_never_cross_ring() {
        // 衰减节点可穿入遗忘区；保护期内的节点（哪怕有连线）永远不进净空带
        val engine = engineWith(16, edges = (1 until 16).map { "n$it" to "n${it + 1}" })
        val decaying = engine.nodes.first()
        decaying.cooling = false
        decaying.forget = 0.3f
        tickSeconds(engine, 10f)
        engine.nodes.filter { it.cooling && !it.gone }.forEach { node ->
            val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
            assertTrue("protected node ${node.id} crossed ring: d=$d exclusion=${engine.exclusionRadius}",
                d >= engine.exclusionRadius - 0.5f)
        }
    }

    @Test
    fun decaying_node_can_be_hit_dragged_and_revives_on_release() {
        // R81 修复「节点没办法拖动」：衰减节点也可命中、可拖拽——松手即抢救（遗忘清零、冷却重置）
        val engine = engineWith(2)
        val node = engine.nodes.first()
        node.cooling = false
        node.forget = 0.3f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Free, node.mode) // R87：衰减期不脱离力场
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

    /** 把节点推进到「可抢救」状态：过保且遗忘过门，直接放入遗忘区（闪烁呼吸段）。 */
    private fun driveToBreathing(engine: BlackHoleGraphEngine, id: String) {
        val node = engine.nodes.first { it.id == id }
        node.cooling = false
        node.forget = 0.6f // 过屏障渗透门（0.5）：可停留遗忘区
        val angle = atan2(node.simY - engine.holeY, node.simX - engine.holeX)
        node.simX = engine.holeX + kotlin.math.cos(angle) * (engine.exclusionRadius - 10f)
        node.simY = engine.holeY + kotlin.math.sin(angle) * (engine.exclusionRadius - 10f)
        node.dispX = node.simX
        node.dispY = node.simY
        node.vx = 0f
        node.vy = 0f
        engine.tick(1f / 60f)
    }

    @Test
    fun rescue_only_when_breathing_inside_hole_zone() {
        val engine = engineWith(3)
        val node = engine.nodes.first()
        node.cooling = false
        node.forget = 0.3f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Free, node.mode) // R87：衰减期不脱离力场
        // 活动区（净空带之外）：不可抢救
        assertFalse(engine.isRescuable(node.id))
        assertFalse(engine.rescueNode(node.id))
        // 进入遗忘区（呼吸段）：可抢救
        driveToBreathing(engine, node.id)
        assertTrue(engine.isRescuable(node.id))
        assertTrue(engine.rescueNode(node.id))
    }

    @Test
    fun rescue_restores_state_and_faded_edges_brighten() {
        val engine = engineWith(3, edges = listOf("n1" to "n2", "n2" to "n3"))
        driveToBreathing(engine, "n1")
        // R87：衰减期连线不再断开——按两端遗忘度渐暗（数据层从未断）
        val dimmed = engine.renderEdges().first { it.fromId == "n1" && it.toId == "n2" }
        assertTrue("edge not faded by forget: ${dimmed.opacityFactor}", dimmed.opacityFactor < 0.5f)
        assertTrue(engine.renderEdges().any { it.fromId == "n2" && it.toId == "n3" })
        assertTrue(engine.rescueNode("n1"))
        val node = engine.nodes.first { it.id == "n1" }
        assertEquals(0f, node.forget, 1e-4f)
        assertTrue(node.cooling)
        assertEquals(0f, node.coolingElapsed, 1e-4f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
        assertTrue("rescued node should be outside breathing zone: d=$d", d >= engine.exclusionRadius)
        // R87 抢救=重新注入轨道速度：恢复公转（根节点）
        if (node.parentId == null) {
            assertTrue("no orbital velocity re-injected", hypot(node.vx, node.vy) > 0.01f)
        }
        // 遗忘清零 -> 连线自然恢复亮度
        val brightened = engine.renderEdges().first { it.fromId == "n1" && it.toId == "n2" }
        assertTrue("edge not brightened after rescue: ${brightened.opacityFactor} vs ${dimmed.opacityFactor}",
            brightened.opacityFactor > dimmed.opacityFactor * 2f)
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
        node.simX = engine.holeX + 5f // 深度遗忘触核 -> 吞噬
        node.simY = engine.holeY
        node.dispX = node.simX
        node.dispY = node.simY
        engine.tick(1f / 60f)
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
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        assertEquals(0f, node.forget, 1e-4f)
        // 重新接入力场：不被甩飞、不坠核心（健康节点净空带硬边界保持）
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
    fun decaying_parent_promotes_children_to_roots() {
        // 母节点进入衰减：子节点不能跟着坠洞——就地晋升为根并注入圆轨道速度，绕洞公转
        val engine = engineWith(4, edges = listOf("n1" to "n2", "n2" to "n3", "n3" to "n4"))
        tickSeconds(engine, 2f)
        val n2 = engine.nodes.first { it.id == "n2" }
        val n3 = engine.nodes.first { it.id == "n3" }
        assertEquals("n2", n3.parentId)
        n2.cooling = false
        n2.forget = 0.4f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Free, n2.mode) // R87：衰减期仍是力场成员
        assertEquals(null, n3.parentId) // 晋升为根
        assertTrue("promoted root has no orbital velocity", hypot(n3.vx, n3.vy) > 0.01f)
        tickSeconds(engine, 2f)
        // 晋升后的根回到力场：绕洞公转、不坠洞、不进净空带
        assertEquals(BlackHoleNodeMode.Free, n3.mode)
        val d = hypot(n3.simX - engine.holeX, n3.simY - engine.holeY)
        assertTrue("promoted root crossed zone: d=$d", d >= engine.exclusionRadius - 1f)
    }

    @Test
    fun decaying_child_promotes_itself_to_root() {
        // R87：子节点自身进入衰减——脱离母节点轨道、晋升为根，受黑洞引力管辖（刹车内旋）
        val engine = engineWith(4, edges = listOf("n1" to "n2", "n2" to "n3", "n3" to "n4"))
        tickSeconds(engine, 2f)
        val n3 = engine.nodes.first { it.id == "n3" }
        assertEquals("n2", n3.parentId)
        n3.cooling = false
        n3.forget = 0.3f
        engine.tick(1f / 60f)
        assertEquals(null, n3.parentId)
        assertEquals(BlackHoleNodeMode.Free, n3.mode)
        assertTrue("self-promoted root has no orbital velocity", hypot(n3.vx, n3.vy) > 0.01f)
    }
}
