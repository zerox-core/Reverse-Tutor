package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 知识图谱引擎（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * R94（2026-09-21 用户拍板）：删除黑洞——原黑洞版（吸积盘/绕洞公转/净空带/开普勒引力/
 * 层级卫星轨道/触核吞噬）整体退役，保留 Obsidian 式力导向节点图谱，其余机制全部沿用。
 * （类名/文件名沿用 BlackHole* 仅为控制本轮改动面，后续重构再统一改名。）
 *
 * 核心规则：
 * - 算法层（sim）/展示层（disp）分离：拖动只写展示层，算法状态（遗忘）不受操作影响；
 * - 力场常量：REPUL=2200 / LINK_ATTR=0.0012 / SPRING_IDEAL=110 / DAMP=0.90
 *   （R94 用户拍板「关系线的强度可以减小很多」：连线弹簧刚度 0.0036 -> 0.0012，约 1/3，
 *   布局由斥力主导、连线只做轻牵引），alpha=max(0.04, 1-iter/300)；
 *   拖动期间 iter 钳 150（alpha 平台 0.5，V30 语义）；
 * - 布局 = 两两斥力 + 连线弹簧（胡克力 + 轴向阻尼）+ 位置级碰撞修正（只改位置不注入速度，
 *   R88：结构上不可能形成能量棘轮）；
 * - 冷启动布局：节点以布局中心为圆心黄金角盘状散布（确定性），全员「刚创建状态」
 *   （R82 拍板：暂不做真实遗忘——保护期内、遗忘 0；真实遗忘曲线等后端设计稿）；
 * - 遗忘 = 闪烁消失（R93/R94 拍板，替代黑洞吞噬）：保护期结束 -> 遗忘增长；
 *   遗忘 >= blinkForgetStart（0.35）节点开始闪烁呼吸 = 「即将遗忘」警告，此间可点按/拖拽
 *   抢救（复习语义：遗忘清零 + 冷却重置，R73/R81 语义延续）；遗忘满 1.0 -> 0.15s 淡出消失
 *   = 真实遗忘（连线随节点移除）；全程不脱离力场；
 * - 拖动：跟手 + 甩动量 vx=位移*1.1，被拖节点不积分力但全程施力（含 x2.5 放大连线弹簧，
 *   关联节点被轻带着走，R92 拍板 6->2.5 不再大片粘连）；松手惯性滑停后回归物理层
 *   （不停泊），弹簧把节点收敛回圆润簇；
 * - 入场：0.9s 内节点从布局中心展开（easeOutCubic，仅渲染层）；
 * - 时间倍率：全局缩放（遗忘/力场步长/淡出），惯性不受倍率影响（V20/V23）；
 * - 硬保护：速度 clamp 30 / NaN 重置 / 单帧拖拽位移 clamp 500（V26）。
 */
data class BlackHolePhysics(
    val repulsion: Float = 2200f, // 节点自带斥力（a3d426c 实测稳定值；R88 试 2600/加宽 padding 均撕裂图谱，已回退——重叠改由位置级修正解决）
    /** 连线弹簧刚度。R94 用户拍板「关系线的强度减小很多」：0.0036 -> 0.0012（约 1/3）。 */
    val linkAttraction: Float = 0.0012f,
    val damping: Float = 0.90f,
    val alphaFloor: Float = 0.04f,
    val alphaIterations: Int = 300,
    val settleMinIterations: Int = 60,
    val settleMaxMove: Float = 0.01f,
    val wakeIterationCap: Int = 150,
    val collisionPadding: Float = 6f,
    val collisionPush: Float = 0.5f,
    val springIdeal: Float = 110f,
    val velocityClamp: Float = 30f,
    val dragDeltaClamp: Float = 500f,
    val settlingSeconds: Float = 0.8f,
    val forgettingFullSeconds: Float = 240f,
    val absorbFadeSeconds: Float = 0.15f,
    val minNodeOpacity: Float = 0.28f,
    val minNodeScale: Float = 0.6f,
    val labelHideForget: Float = 0.5f,
    /** 闪烁呼吸门（R94 无黑洞版）：遗忘 >= 此值节点开始闪烁呼吸（即将遗忘警告）且可点按抢救。 */
    val blinkForgetStart: Float = 0.35f,
    val inertiaDamping: Float = 0.84f,
    val dragMomentum: Float = 1.1f,
    val inertiaStopSpeed: Float = 6f,
    /** 拖拽期间连线弹簧放大倍数：让关联节点被带着走。R92 用户拍板「减少关系线的吸附效果」6→2.5——拖动手感保留轻微跟随、不再大片粘连。 */
    val dragLinkBoost: Float = 2.5f,
    /** 弹簧轴向阻尼（相对速度投影系数）：让连线牵引收敛圆润、不振颤（真机反馈「假引力」）。 */
    val springDamping: Float = 0.06f,
    /** 冷却保护时长（秒，1x；占位值——等用户给记忆遗忘曲线算法后替换）。新节点默认在保护期内。 */
    val protectionSeconds: Float = 90f,
    val entranceSeconds: Float = 0.9f
)

enum class BlackHoleNodeMode {
    Free,
    Dragging,
    InertiaSliding,
    Parked,
    Settling
}

class BlackHoleNode(
    val id: String,
    val label: String,
    val kind: GraphNodeKind,
    val degree: Int,
    var simX: Float,
    var simY: Float,
    var forget: Float
) {
    var dispX: Float = simX
    var dispY: Float = simY
    var vx: Float = 0f
    var vy: Float = 0f
    var mode: BlackHoleNodeMode = BlackHoleNodeMode.Free
    var settlingElapsed: Float = 0f
    var absorbed: Boolean = false
    var absorbFade: Float = 0f
    var gone: Boolean = false
    /** 冷却保护中：新节点默认开——正常力场布局，遗忘冻结为 0。 */
    var cooling: Boolean = true
    /** 保护期已过的时长（秒，随倍率）；达到 physics.protectionSeconds 即过保、开始遗忘。 */
    var coolingElapsed: Float = 0f

    /** 节点基础半径：9+2.4√degree → 10.5+2.8√degree（R68 +17%）→ 14+3.9√degree（R92 +35%）
     *  → 20+4.5√degree（R94 用户拍板「增大节点面积」——黑洞删除后画布空间充裕，节点再放大，
     *  面积约翻倍；与 8sp 标签配套，字体不再比节点大、不头重脚轻）。 */
    val baseRadius: Float
        get() = 20f + 4.5f * sqrt(degree.toFloat().coerceAtLeast(0f))

    fun displayRadius(physics: BlackHolePhysics): Float {
        val scale = 1f - (1f - physics.minNodeScale) * forget.coerceIn(0f, 1f)
        return baseRadius * scale
    }

    fun opacity(physics: BlackHolePhysics): Float {
        val base = 1f - (1f - physics.minNodeOpacity) * forget.coerceIn(0f, 1f)
        return if (absorbed) base * (1f - absorbFade) else base
    }
}

data class BlackHoleRenderNode(
    val id: String,
    val label: String,
    val kind: GraphNodeKind,
    val x: Float,
    val y: Float,
    val radius: Float,
    val opacity: Float,
    val forget: Float,
    val showLabel: Boolean,
    val mode: BlackHoleNodeMode
)

data class BlackHoleRenderEdge(
    val fromId: String,
    val toId: String,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val opacityFactor: Float
)

class BlackHoleGraphEngine(
    /** 布局中心锚点（R94：黑洞删除后仅作冷启动散布圆心 / 入场展开原点 / 背景散布原点）。 */
    val holeX: Float = 0f,
    val holeY: Float = 0f,
    val physics: BlackHolePhysics = BlackHolePhysics()
) {
    private val nodeList = mutableListOf<BlackHoleNode>()
    private val edgeList = mutableListOf<Pair<String, String>>()
    private var iter: Int = 0
    private var settled: Boolean = false
    private var draggingId: String? = null
    /** R87：按下时节点是否处于衰减期（过保且遗忘>0）——松手即复习抢救（R81 语义延续）。 */
    private var dragWasDecaying: Boolean = false
    private var absorbedTotal: Int = 0
    private var rescuedTotal: Int = 0
    private var simAccum: Float = 0f

    /** 入场进度 0->1（真实时间，不随倍率）。 */
    var entrance: Float = 0f
        private set

    /** 累计力场子步数（测试用）。 */
    var simulationSteps: Long = 0L
        private set

    /** R91：布局是否已收敛（供界面层自动取景锁定——首帧 extent 是未收敛瞬态，取景需跟到收敛）。 */
    val isSettled: Boolean get() = settled

    /** R92：当前被拖拽节点 id（供界面层把「拖动中」也视为聚焦态、高亮其关联连线）。 */
    val draggingNodeId: String? get() = draggingId

    var timeScale: Float = 1f
        set(value) {
            field = value.coerceIn(0.05f, 10f)
        }

    /** 已彻底遗忘（淡出消失）的节点总数（渲染「已遗忘 N」）。 */
    val absorbedCount: Int
        get() = absorbedTotal

    /** 已被用户抢救回归的节点总数（D7 点击抢救复习）。 */
    val rescuedCount: Int
        get() = rescuedTotal

    val nodes: List<BlackHoleNode>
        get() = nodeList

    // ---------- 初始化 ----------

    /**
     * 冷启动布局：节点以布局中心为圆心黄金角盘状散布（确定性）。
     * R82 用户拍板：暂不做真实遗忘——全员「刚创建状态」：保护期内、遗忘 0
     * （真实遗忘曲线等后端设计稿，届时恢复分阶段播种）。
     */
    fun populate(
        graphNodes: List<Triple<String, String, GraphNodeKind>>,
        edges: List<Pair<String, String>>
    ) {
        nodeList.clear()
        edgeList.clear()
        val degreeById = mutableMapOf<String, Int>()
        edges.forEach { (a, b) ->
            degreeById[a] = (degreeById[a] ?: 0) + 1
            degreeById[b] = (degreeById[b] ?: 0) + 1
        }
        val golden = 2.399963f
        val ring0 = 200f // 原「净空带+34」的散布基圈，黑洞删除后取等效常量
        graphNodes.forEachIndexed { index, (id, label, kind) ->
            val hash = id.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff }
            val angle = index * golden + (hash % 360) * 0.01f
            val ring = ring0 + 30f * sqrt((index + 1).toFloat()) + (hash % 40)
            val node = BlackHoleNode(
                id = id,
                label = label,
                kind = kind,
                degree = degreeById[id] ?: 0,
                simX = holeX + cos(angle) * ring,
                simY = holeY + sin(angle) * ring,
                forget = 0f
            )
            nodeList.add(node)
        }
        val ids = nodeList.mapTo(HashSet()) { it.id }
        edges.filter { it.first in ids && it.second in ids }.forEach { edgeList.add(it) }
        iter = 0
        settled = false
        draggingId = null
        dragWasDecaying = false
        absorbedTotal = 0
        rescuedTotal = 0
        simAccum = 0f
        entrance = 0f
    }

    /** R87：节点是否处于衰减期（过保、遗忘增长中）——可点按/拖拽抢救。 */
    private fun isDecaying(node: BlackHoleNode): Boolean =
        !node.cooling && node.forget > 0f && !node.absorbed && !node.gone

    // ---------- 查询 ----------

    /** R81/R87：衰减期节点同样可命中——拖回来=复习抢救，全程可交互（用户反馈「没办法拖动了」）。 */
    fun hitTest(x: Float, y: Float): BlackHoleNode? =
        nodeList.asReversed().firstOrNull { node ->
            !node.gone && !node.absorbed &&
                hypot(node.dispX - x, node.dispY - y) <= node.displayRadius(physics) * 1.35f
        }

    /**
     * 可抢救命中（R94 无黑洞版）：衰减期且遗忘已过闪烁呼吸门（闪烁中的濒死节点）。
     * 命中半径额外放宽，方便用户点中小节点（用户 2026-09-19 拍板 D7 点击抢救）。
     */
    fun hitTestRescuable(x: Float, y: Float): BlackHoleNode? =
        nodeList.asReversed().firstOrNull { node ->
            isDecaying(node) && node.forget >= physics.blinkForgetStart &&
                hypot(node.dispX - x, node.dispY - y) <= node.displayRadius(physics) * 1.35f + 16f
        }

    /** 节点是否处于「可抢救」状态：衰减期、遗忘已过闪烁呼吸门（正在闪烁呼吸）。 */
    fun isRescuable(nodeId: String): Boolean {
        val node = nodeList.firstOrNull { it.id == nodeId && !it.gone && !it.absorbed } ?: return false
        return isDecaying(node) && node.forget >= physics.blinkForgetStart
    }

    /**
     * 抢救 = 用户主动点击、完成复习回顾（用户 2026-09-19 拍板 D7；R94 无黑洞版）：
     * 遗忘清零、冷却保护重置、原地回归力场（不再 reposition 到遗忘区外沿——没有黑洞了）。
     * 数据层连线在衰减期从未断开，抢救后自然恢复亮度、无需建边。
     */
    fun rescueNode(nodeId: String): Boolean {
        if (!isRescuable(nodeId)) return false
        val node = nodeList.first { it.id == nodeId }
        node.forget = 0f
        node.cooling = true
        node.coolingElapsed = 0f
        node.mode = BlackHoleNodeMode.Free
        node.vx = 0f
        node.vy = 0f
        node.settlingElapsed = 0f
        rescuedTotal++
        wake()
        return true
    }

    fun centroidOfCluster(): Pair<Float, Float> {
        val alive = nodeList.filter { !it.gone }
        if (alive.isEmpty()) return holeX to holeY
        val cx = alive.sumOf { it.dispX.toDouble() } / alive.size
        val cy = alive.sumOf { it.dispY.toDouble() } / alive.size
        return cx.toFloat() to cy.toFloat()
    }

    /** 节点群包围半径（质心到最远节点边缘），供相机「舒适入画」。 */
    fun clusterExtent(): Float {
        val c = centroidOfCluster()
        var m = 0f
        nodeList.forEach { node ->
            if (!node.gone) {
                m = max(m, hypot(node.dispX - c.first, node.dispY - c.second) + node.displayRadius(physics))
            }
        }
        return m.coerceAtLeast(1f)
    }

    // ---------- 拖动 ----------

    fun startDrag(nodeId: String) {
        val node = nodeList.firstOrNull {
            it.id == nodeId && !it.gone && !it.absorbed
        } ?: return
        dragWasDecaying = isDecaying(node)
        draggingId = node.id
        node.mode = BlackHoleNodeMode.Dragging
        node.vx = 0f
        node.vy = 0f
        wake()
    }

    /**
     * 拖动到指针世界坐标。单帧位移 clamp 500（V26 硬保护）。
     * V30：按住期间 alpha 平台不得到期——每帧把 iter 钳在平台对应值。
     */
    fun dragTo(x: Float, y: Float) {
        val node = nodeList.firstOrNull { it.id == draggingId } ?: return
        var dx = x - node.dispX
        var dy = y - node.dispY
        val len = hypot(dx, dy)
        if (len > physics.dragDeltaClamp) {
            val k = physics.dragDeltaClamp / len
            dx *= k
            dy *= k
        }
        node.dispX += dx
        node.dispY += dy
        node.vx = dx * physics.dragMomentum
        node.vy = dy * physics.dragMomentum
        iter = min(iter, physics.wakeIterationCap)
    }

    /**
     * 松手：惯性滑停 -> 回归物理层由弹簧收敛（不停泊）。
     * R81/R87：拖动衰减期节点 = 抓回来复习——松手即抢救（遗忘清零、冷却重置），
     * 返回被抢救的节点 id；普通节点返回 null。
     */
    fun endDrag(): String? {
        val node = nodeList.firstOrNull { it.id == draggingId }
        val wasDecaying = dragWasDecaying
        draggingId = null
        dragWasDecaying = false
        if (node == null) return null
        node.mode = BlackHoleNodeMode.InertiaSliding
        if (wasDecaying) {
            node.forget = 0f
            node.cooling = true
            node.coolingElapsed = 0f
            rescuedTotal++
            wake()
            return node.id
        }
        wake()
        return null
    }

    /** 页面关闭/重进回归：全部展示位置回归算法层（V23-4）。 */
    fun resetDisplayToSimulation() {
        nodeList.forEach { node ->
            if (!node.gone) {
                node.dispX = node.simX
                node.dispY = node.simY
                node.vx = 0f
                node.vy = 0f
                if (node.mode != BlackHoleNodeMode.Free) node.mode = BlackHoleNodeMode.Free
                node.settlingElapsed = 0f
            }
        }
        draggingId = null
        wake()
    }

    fun wake() {
        settled = false
        iter = min(iter, physics.wakeIterationCap)
        simAccum = 0f
    }

    // ---------- 主循环 ----------

    /**
     * 每帧调用。dtSeconds 为真实帧间隔（秒）。
     * 时间倍率全局生效（V20）：遗忘/淡出按 dt*timeScale，力场按 60fps 规范帧子步
     * （每秒 60*timeScale 个子步）。惯性/沉淀/入场按真实时间（V23：惯性不受倍率影响）。
     */
    fun tick(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.0005f, 0.1f)

        if (entrance < 1f) {
            entrance = min(1f, entrance + dt / physics.entranceSeconds)
        }
        advanceForgetting(dt)
        advanceDisplayModes(dt)
        simAccum += dt * 60f * timeScale
        var guard = 0
        while (simAccum >= 1f && guard < 12) {
            simulateForces()
            simAccum -= 1f
            guard++
            simulationSteps++
        }
        if (simAccum > 12f) simAccum = 0f
        advanceFades(dt)
    }

    /**
     * 遗忘推进（冷却保护模型，用户 R66 拍板；R94 无黑洞版）：
     * - 保护期内（新节点默认）：遗忘冻结为 0，节点正常力场布局；
     * - 保护期结束：遗忘开始增长，节点**不脱离力场**——正常参与斥力/碰撞/弹簧；
     *   遗忘 >= blinkForgetStart 进入闪烁呼吸段（界面层渲染，可点按/拖拽抢救）；
     * - 遗忘满 1.0：标记 absorbed -> 0.15s 淡出 -> gone 并清边 = 真实遗忘（R94 闪烁消失，
     *   替代黑洞触核吞噬）。
     * 具体曲线数值为占位，等用户给记忆遗忘曲线算法后替换。
     */
    private fun advanceForgetting(dt: Float) {
        val scaled = dt * timeScale
        val step = scaled / physics.forgettingFullSeconds
        nodeList.forEach { node ->
            if (node.gone || node.absorbed) return@forEach
            if (node.cooling) {
                node.coolingElapsed += scaled
                if (node.coolingElapsed >= physics.protectionSeconds) {
                    node.cooling = false
                }
                return@forEach
            }
            node.forget = (node.forget + step).coerceAtMost(1f)
            if (node.forget >= 1f) {
                // R94：遗忘走满 = 真实遗忘（闪烁旅程的终点），进入淡出流程
                node.absorbed = true
                node.absorbFade = 0f
                absorbedTotal++
            }
        }
    }

    /**
     * 淡出收尾（R94）：absorbed 淡出 -> gone 并清边。
     * 每帧、全体、淡出随倍率（V17/V23）。
     */
    private fun advanceFades(dt: Float) {
        if (nodeList.none { it.gone || it.absorbed }) return
        val fadeStep = dt * timeScale / physics.absorbFadeSeconds
        val iterator = nodeList.iterator()
        while (iterator.hasNext()) {
            val node = iterator.next()
            if (node.gone) {
                iterator.remove()
                continue
            }
            if (node.absorbed) {
                node.absorbFade += fadeStep
                if (node.absorbFade >= 1f) {
                    node.gone = true
                    iterator.remove()
                    edgeList.removeAll { it.first == node.id || it.second == node.id }
                }
            }
        }
    }

    private fun advanceDisplayModes(dt: Float) {
        nodeList.forEach { node ->
            when (node.mode) {
                BlackHoleNodeMode.InertiaSliding -> {
                    node.dispX += node.vx * dt
                    node.dispY += node.vy * dt
                    val decay = Math.pow(physics.inertiaDamping.toDouble(), (dt * 60).toDouble()).toFloat()
                    node.vx *= decay
                    node.vy *= decay
                    if (hypot(node.vx, node.vy) <= physics.inertiaStopSpeed) {
                        // 滑停不停泊：回归物理层，弹簧/斥力把节点收敛回圆润簇（真机反馈「假引力」）
                        node.simX = node.dispX
                        node.simY = node.dispY
                        node.vx = 0f
                        node.vy = 0f
                        node.mode = BlackHoleNodeMode.Free
                        wake()
                    }
                }
                BlackHoleNodeMode.Settling -> {
                    node.settlingElapsed += dt
                    if (node.settlingElapsed >= physics.settlingSeconds) {
                        node.mode = BlackHoleNodeMode.Parked
                    }
                }
                else -> Unit
            }
        }
    }

    private fun simulateForces() {
        val alpha = max(physics.alphaFloor, 1f - iter.toFloat() / physics.alphaIterations)
        // R87：衰减期节点仍全程参与力场（受力也施力）——只有被遗忘/消失者退出
        val freeNodes = nodeList.filter {
            !it.gone && !it.absorbed
        }
        val fx = HashMap<String, Float>(freeNodes.size * 2)
        val fy = HashMap<String, Float>(freeNodes.size * 2)
        freeNodes.forEach { fx[it.id] = 0f; fy[it.id] = 0f }

        // 1) 两两斥力 + 碰撞（全体节点对；非 Free 节点以展示位置施力但不受力——V29/V30）
        for (i in freeNodes.indices) {
            val a = freeNodes[i]
            for (j in i + 1 until freeNodes.size) {
                val b = freeNodes[j]
                val ax = if (a.mode == BlackHoleNodeMode.Free) a.simX else a.dispX
                val ay = if (a.mode == BlackHoleNodeMode.Free) a.simY else a.dispY
                val bx = if (b.mode == BlackHoleNodeMode.Free) b.simX else b.dispX
                val by = if (b.mode == BlackHoleNodeMode.Free) b.simY else b.dispY
                var dx = bx - ax
                var dy = by - ay
                var d2 = dx * dx + dy * dy
                if (d2 < 0.01f) {
                    dx = 0.1f * (if (a.id < b.id) 1 else -1)
                    dy = 0.05f
                    d2 = dx * dx + dy * dy
                }
                val d = sqrt(d2)
                val ra = a.displayRadius(physics)
                val rb = b.displayRadius(physics)
                var f = physics.repulsion * alpha / d2
                val minD = ra + rb + physics.collisionPadding
                if (d < minD) f += (minD - d) * physics.collisionPush
                val ux = dx / d
                val uy = dy / d
                applyForce(a, fx, fy, -ux * f, -uy * f, fromCollision = d < minD)
                applyForce(b, fx, fy, ux * f, uy * f, fromCollision = d < minD)
                // R88 位置级碰撞修正：重叠节点沿轴线直接分开（只改位置、不注入速度），
                // 并卸掉互相接近的速度分量（非弹性、只减能不加能）。
                // 力式碰撞会持续踢动形成能量棘轮（R88 两次调参均撕裂图谱），
                // 位置修正在结构上不可能积累能量，且立刻保证不重叠。
                if (d < minD) {
                    val overlap = minD - d
                    val aFree = a.mode == BlackHoleNodeMode.Free
                    val bFree = b.mode == BlackHoleNodeMode.Free
                    if (aFree && bFree) {
                        val half = overlap / 2f
                        a.simX -= ux * half
                        a.simY -= uy * half
                        b.simX += ux * half
                        b.simY += uy * half
                    } else if (aFree) {
                        a.simX -= ux * overlap
                        a.simY -= uy * overlap
                    } else if (bFree) {
                        b.simX += ux * overlap
                        b.simY += uy * overlap
                    }
                    // 卸掉可动 Free 节点朝向对方的速度分量（非弹性、只减能）
                    if (aFree) {
                        val vIn = a.vx * ux + a.vy * uy
                        if (vIn > 0f) { a.vx -= ux * vIn; a.vy -= uy * vIn }
                    }
                    if (bFree) {
                        val vIn = b.vx * ux + b.vy * uy
                        if (vIn < 0f) { b.vx -= ux * vIn; b.vy -= uy * vIn }
                    }
                }
            }
        }

        // 2) 连线弹簧（双向，只对算法层 Free 节点积分；非 Free 端用展示位置）
        edgeList.forEach { (fromId, toId) ->
            val a = freeNodes.firstOrNull { it.id == fromId } ?: return@forEach
            val b = freeNodes.firstOrNull { it.id == toId } ?: return@forEach
            val ax = if (a.mode == BlackHoleNodeMode.Free) a.simX else a.dispX
            val ay = if (a.mode == BlackHoleNodeMode.Free) a.simY else a.dispY
            val bx = if (b.mode == BlackHoleNodeMode.Free) b.simX else b.dispX
            val by = if (b.mode == BlackHoleNodeMode.Free) b.simY else b.dispY
            val dx = bx - ax
            val dy = by - ay
            val d = hypot(dx, dy).takeIf { it > 0.001f } ?: 0.001f
            val boost = if (a.mode == BlackHoleNodeMode.Dragging || b.mode == BlackHoleNodeMode.Dragging)
                physics.dragLinkBoost else 1f
            val ux = dx / d
            val uy = dy / d
            // 真弹簧 = 胡克力 + 轴向阻尼（相对速度投影）：连线牵引收敛圆润、不振颤
            val relV = (b.vx - a.vx) * ux + (b.vy - a.vy) * uy
            val f = (d - physics.springIdeal) * physics.linkAttraction * alpha * boost +
                relV * physics.springDamping * alpha
            applyForce(a, fx, fy, ux * f, uy * f, fromCollision = false)
            applyForce(b, fx, fy, -ux * f, -uy * f, fromCollision = false)
        }

        // 3) 积分 + 阻尼 + 速度 clamp + NaN 守卫（R94：径向/切向拆分随黑洞引力一同退役，
        //    恢复全向均匀阻尼；弹簧/斥力收敛布局，不再有引力中心）
        freeNodes.forEach { node ->
            when (node.mode) {
                BlackHoleNodeMode.Free -> {
                    node.vx += fx[node.id] ?: 0f
                    node.vy += fy[node.id] ?: 0f
                    node.vx *= physics.damping
                    node.vy *= physics.damping
                    clampVelocity(node)
                    if (guardNaN(node)) return@forEach
                    node.simX += node.vx
                    node.simY += node.vy
                    node.dispX = node.simX
                    node.dispY = node.simY
                }
                BlackHoleNodeMode.Settling -> {
                    node.dispX += (fx[node.id] ?: 0f)
                    node.dispY += (fy[node.id] ?: 0f)
                }
                else -> Unit
            }
        }

        iter += 1
        if (iter > physics.alphaIterations && draggingId == null) {
            settled = true
        }
    }

    private fun applyForce(
        node: BlackHoleNode,
        fx: MutableMap<String, Float>,
        fy: MutableMap<String, Float>,
        x: Float,
        y: Float,
        fromCollision: Boolean
    ) {
        when (node.mode) {
            BlackHoleNodeMode.Free -> {
                fx[node.id] = (fx[node.id] ?: 0f) + x
                fy[node.id] = (fy[node.id] ?: 0f) + y
            }
            BlackHoleNodeMode.Settling -> {
                if (fromCollision) {
                    fx[node.id] = (fx[node.id] ?: 0f) + x
                    fy[node.id] = (fy[node.id] ?: 0f) + y
                }
            }
            else -> Unit
        }
    }

    private fun clampVelocity(node: BlackHoleNode) {
        val speed = hypot(node.vx, node.vy)
        if (speed > physics.velocityClamp) {
            val k = physics.velocityClamp / speed
            node.vx *= k
            node.vy *= k
        }
    }

    private fun guardNaN(node: BlackHoleNode): Boolean {
        if (node.simX.isNaN() || node.simY.isNaN() || node.simX.isInfinite() || node.simY.isInfinite()) {
            node.simX = holeX + 200f
            node.simY = holeY
            node.dispX = node.simX
            node.dispY = node.simY
            node.vx = 0f
            node.vy = 0f
            return true
        }
        return false
    }

    // ---------- 渲染快照 ----------

    private fun entranceEase(): Float {
        val t = entrance.coerceIn(0f, 1f)
        return 1f - (1f - t) * (1f - t) * (1f - t)
    }

    fun renderNodes(): List<BlackHoleRenderNode> {
        val ease = entranceEase()
        return nodeList.filter { !it.gone }.map { node ->
            BlackHoleRenderNode(
                id = node.id,
                label = node.label,
                kind = node.kind,
                x = holeX + (node.dispX - holeX) * ease,
                y = holeY + (node.dispY - holeY) * ease,
                radius = node.displayRadius(physics),
                opacity = node.opacity(physics),
                forget = node.forget,
                showLabel = node.forget < physics.labelHideForget && !node.absorbed && entrance >= 1f,
                mode = node.mode
            )
        }
    }

    fun renderEdges(): List<BlackHoleRenderEdge> {
        val ease = entranceEase()
        val byId = nodeList.filter { !it.gone }.associateBy { it.id }
        return edgeList.mapNotNull { (fromId, toId) ->
            val a = byId[fromId] ?: return@mapNotNull null
            val b = byId[toId] ?: return@mapNotNull null
            // R87：衰减期连线不再断开——按两端遗忘度渐暗（数据层从未断，抢救后自然恢复亮度）
            val fade = (1f - a.forget) * (1f - b.forget)
            val absorbedFade = (1f - a.absorbFade) * (1f - b.absorbFade)
            BlackHoleRenderEdge(
                fromId = fromId,
                toId = toId,
                fromX = holeX + (a.dispX - holeX) * ease,
                fromY = holeY + (a.dispY - holeY) * ease,
                toX = holeX + (b.dispX - holeX) * ease,
                toY = holeY + (b.dispY - holeY) * ease,
                opacityFactor = fade * absorbedFade * ease
            )
        }
    }
}
