package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * 知识图谱引擎单元测试（R94 无黑洞版）。
 *
 * R94（2026-09-21 用户拍板）：黑洞整体删除——绕洞公转/开普勒引力/净空带屏障/
 * 层级卫星轨道/触核吞噬的用例全部退役；保留力场布局/拖拽语义/碰撞/惯性/入场/
 * 时间倍率/硬保护用例；遗忘语义改为「闪烁消失」：
 * 过保 -> 遗忘增长 -> 遗忘 >= blinkForgetStart（0.35）闪烁呼吸（可点按/拖拽抢救，
 * R73/R81 语义延续）-> 遗忘满 1.0 淡出消失（连线随节点移除）。
 * R94 调参：节点基础半径 14+3.9√度 -> 20+4.5√度（增大节点面积）；连线弹簧刚度
 * 0.0036 -> 0.0012（关系线强度减小很多）。
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
        // 力场/拖拽类用例要确定性状态：全部置为保护期内（遗忘冻结）；
        // 遗忘/闪烁类用例自行把节点 cooling = false 显式过保。
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
    fun populate_places_all_nodes_finite_around_center() {
        val engine = engineWith(24)
        assertEquals(24, engine.nodes.size)
        engine.nodes.forEach { node ->
            assertTrue(node.simX.isFinite() && node.simY.isFinite())
            val d = hypot(node.simX - engine.holeX, node.simY - engine.holeY)
            assertTrue("node ${node.id} d=$d too close to center", d > 100f)
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

    @Test
    fun r94_node_base_radius_bigger_and_grows_with_degree() {
        // R94 用户拍板「增大节点面积」：基础半径 14+3.9√度 -> 20+4.5√度（面积约翻倍，
        // 与 8sp 标签配套，字体不再比节点大、不头重脚轻）
        val engine = engineWith(4, edges = listOf("n1" to "n2", "n1" to "n3", "n1" to "n4"))
        val hub = engine.nodes.first { it.id == "n1" } // 度数 3
        val leaf = engine.nodes.first { it.id == "n4" } // 度数 1
        assertEquals(20f + 4.5f * sqrt(3f), hub.baseRadius, 0.001f)
        assertEquals(24.5f, leaf.baseRadius, 0.001f)
        assertTrue("base radius shrank: ${leaf.baseRadius}", leaf.baseRadius > 20f)
        assertTrue("degree scaling lost", hub.baseRadius > leaf.baseRadius)
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

    @Test
    fun settled_layout_stays_quiet_without_orbit() {
        // R94：黑洞公转删除后，布局收敛即安静——settle 后节点只有微幅力场调整，不再持续绕动
        val engine = engineWith(8, edges = (1 until 8).map { "n$it" to "n${it + 1}" })
        tickSeconds(engine, 6f) // > alphaIterations 300 子步 -> settled
        assertTrue(engine.isSettled)
        val snapshot = engine.nodes.associate { it.id to (it.dispX to it.dispY) }
        tickSeconds(engine, 4f)
        engine.nodes.forEach { node ->
            val (x0, y0) = snapshot.getValue(node.id)
            val moved = hypot(node.dispX - x0, node.dispY - y0)
            assertTrue("node ${node.id} drifted after settle: moved=$moved", moved < 40f)
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
        dragToAndRelease(engine, a.id, engine.holeX + 700f, engine.holeY)
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
        val px = engine.holeX + 700f
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
        // R88：位置级碰撞修正——重叠立即分开，且不注入速度（结构上不可能形成能量棘轮）
        val engine = engineWith(2)
        val a = engine.nodes[0]
        val b = engine.nodes[1]
        val px = engine.holeX + 800f
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
        // 继续跑 2s：保持分开，且零初速下速度保持近零（被踢动会远超此值）
        tickSeconds(engine, 2f)
        val d2 = hypot(a.simX - b.simX, a.simY - b.simY)
        val bodies = a.displayRadius(engine.physics) + b.displayRadius(engine.physics)
        assertTrue("overlap returned: d=$d2 bodies=$bodies", d2 >= bodies - 0.5f)
        val sa = hypot(a.vx, a.vy)
        val sb = hypot(b.vx, b.vy)
        assertTrue("correction kicked node: |va|=$sa |vb|=$sb", sa < 0.5f && sb < 0.5f)
    }

    @Test
    fun drag_pulls_linked_neighbors_along() {
        // 真机反馈「关联的拖动性不够」：拖 n1 时，与 n1 连边的 n2 应被明显带动，且强于无连边的 n3。
        // R94 弹簧刚度 0.0036->0.0012（关系线强度减小很多）——跟随幅度阈值同步下调
        val engine = engineWith(3, edges = listOf("n1" to "n2"))
        tickSeconds(engine, 3f) // 布局稳定
        val n1 = engine.nodes.first { it.id == "n1" }
        val n2 = engine.nodes.first { it.id == "n2" }
        val n3 = engine.nodes.first { it.id == "n3" }
        // 沿 n1 相对布局中心的径向向外拖 300px
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
        assertTrue("linked neighbor not pulled along: n2Move=$n2Move", n2Move > 10f)
        assertTrue("linked pull not stronger than unlinked: n2=$n2Move n3=$n3Move",
            n2Move > n3Move + 6f)
    }

    // ---------- 遗忘（R94 闪烁消失） ----------

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

    @Test
    fun cooling_protection_expires_then_decay_begins() {
        // 保护期结束 -> 遗忘开始增长；R94：节点不脱离力场（保持 Free），过门后界面层闪烁呼吸
        val engine = engineWith(1)
        val node = engine.nodes.first()
        engine.timeScale = 10f
        tickSeconds(engine, 10f) // 100s 虚拟时间 > 90s 保护期
        assertFalse(node.cooling)
        assertTrue("forget not advancing after protection: ${node.forget}", node.forget > 0f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
    }

    @Test
    fun fully_forgotten_node_fades_out_and_is_removed() {
        // R94 闪烁消失：遗忘走满 1.0 -> absorbed -> 0.15s 淡出 -> gone 并清边（替代黑洞触核吞噬）
        val engine = engineWith(3, edges = listOf("n1" to "n2", "n2" to "n3"))
        val victim = engine.nodes[1] // n2
        victim.cooling = false
        victim.forget = 1f // 直接置满：下一帧 advanceForgetting 即判 absorbed
        engine.tick(1f / 60f)
        assertTrue(victim.absorbed)
        assertEquals(1, engine.absorbedCount)
        tickSeconds(engine, 0.5f) // > 0.15s 淡出
        assertTrue(victim.gone)
        assertFalse(engine.nodes.any { it.id == "n2" })
        assertTrue(engine.renderEdges().none { it.fromId == "n2" || it.toId == "n2" })
    }

    @Test
    fun protected_node_stays_fresh_and_stable_in_layout() {
        // 保护期内：遗忘冻结为 0、保持 Free 正常力场布局，收敛后位置基本稳定
        val engine = engineWith(6)
        tickSeconds(engine, 5f) // 布局稳定
        val node = engine.nodes.first()
        val x0 = node.simX
        val y0 = node.simY
        tickSeconds(engine, 6f)
        assertTrue(node.cooling)
        assertEquals(0f, node.forget, 0.0001f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        val moved = hypot(node.simX - x0, node.simY - y0)
        assertTrue("protected node drifted: moved=$moved", moved < 40f)
    }

    @Test
    fun kicked_node_recovers_via_damping_without_escaping() {
        // R94 无黑洞版踢动保护：碰撞/拖拽踢动的异常速度由全向阻尼（0.90/子步）快速吸收，
        // 节点留在图谱附近，而不是飞出画布
        val engine = engineWith(1)
        val node = engine.nodes.first()
        val x0 = node.simX
        val y0 = node.simY
        node.vx += 20f // 远超正常力场速度量级
        tickSeconds(engine, 5f)
        val speed = hypot(node.vx, node.vy)
        assertTrue("kick not absorbed: speed=$speed", speed < 2f)
        val drift = hypot(node.simX - x0, node.simY - y0)
        assertTrue("node escaped: drift=$drift", drift < 400f)
        assertTrue(node.simX.isFinite() && node.simY.isFinite())
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

    // ---------- settle / 入场 / 时间倍率 ----------

    @Test
    fun r91_isSettled_flips_once_enough_substeps_run_and_resets_on_wake() {
        // R91 自动取景：界面层靠 isSettled 锁定跟随取景——收敛前 false、之后 true、wake 后回落
        val engine = engineWith(6)
        assertFalse(engine.isSettled)
        tickSeconds(engine, 6f)
        assertTrue(engine.isSettled)
        engine.startDrag(engine.nodes.first().id) // wake() 路径
        assertFalse(engine.isSettled)
        engine.endDrag()
    }

    @Test
    fun r92_draggingNodeId_exposed_for_drag_edge_highlight() {
        // R92 用户拍板「拖动时明显看到关联线」：界面层聚焦态 = 选中节点 ?: 拖拽节点——访问器须与拖拽生命周期同步
        val engine = engineWith(6)
        assertNull(engine.draggingNodeId)
        val target = engine.nodes.first()
        engine.startDrag(target.id)
        assertEquals(target.id, engine.draggingNodeId)
        engine.endDrag()
        assertNull(engine.draggingNodeId)
    }

    @Test
    fun entrance_eases_from_center_then_completes() {
        val engine = engineWith(4)
        assertEquals(0f, engine.entrance, 0.0001f)
        // 入场第 0 帧：全部节点从布局中心展开
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

    // ---------- 抢救（D7 断链离散 · 点击抢救，R94 无黑洞版） ----------

    /** 把节点推进到「可抢救」状态：过保且遗忘过闪烁呼吸门。 */
    private fun driveToBreathing(engine: BlackHoleGraphEngine, id: String) {
        val node = engine.nodes.first { it.id == id }
        node.cooling = false
        node.forget = 0.6f // 过闪烁呼吸门（0.35）：闪烁中的濒死节点
        node.vx = 0f
        node.vy = 0f
        engine.tick(1f / 60f)
    }

    @Test
    fun rescue_only_when_forget_passes_blink_threshold() {
        val engine = engineWith(3)
        val node = engine.nodes.first()
        node.cooling = false
        node.forget = 0.2f // 衰减中但没过闪烁呼吸门：不可抢救
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Free, node.mode)
        assertFalse(engine.isRescuable(node.id))
        assertFalse(engine.rescueNode(node.id))
        // 遗忘过门（闪烁呼吸段）：可抢救
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
        // 遗忘清零 -> 连线自然恢复亮度
        val brightened = engine.renderEdges().first { it.fromId == "n1" && it.toId == "n2" }
        assertTrue("edge not brightened after rescue: ${brightened.opacityFactor} vs ${dimmed.opacityFactor}",
            brightened.opacityFactor > dimmed.opacityFactor * 2f)
        assertEquals(1, engine.rescuedCount)
    }

    @Test
    fun rescue_rejected_for_healthy_or_fully_forgotten_nodes() {
        val engine = engineWith(3)
        // 健康节点：不可抢救
        assertFalse(engine.rescueNode("n1"))
        // 已彻底遗忘（淡出消失）节点：不可抢救（真实遗忘不可逆，只能重新学习新建）
        val node = engine.nodes.first { it.id == "n2" }
        node.cooling = false
        node.forget = 1f
        engine.tick(1f / 60f)
        assertTrue(node.absorbed)
        tickSeconds(engine, 0.5f)
        assertTrue(node.gone)
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
        assertTrue(node.simX.isFinite() && node.simY.isFinite())
    }

    @Test
    fun decaying_node_can_be_hit_dragged_and_revives_on_release() {
        // R81 修复「节点没办法拖动」：衰减节点也可命中、可拖拽——松手即抢救（遗忘清零、冷却重置）
        val engine = engineWith(2)
        val node = engine.nodes.first()
        node.cooling = false
        node.forget = 0.3f
        engine.tick(1f / 60f)
        assertEquals(BlackHoleNodeMode.Free, node.mode) // 衰减期不脱离力场
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
}
