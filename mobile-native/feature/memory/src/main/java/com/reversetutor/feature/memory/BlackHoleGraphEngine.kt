package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 黑洞图谱引擎（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 设计来源：妙搭图谱 mock V1~V30 定稿规格（物理克隆自 main 分支 PWA）。
 * 核心规则：
 * - 算法层（sim）/展示层（disp）分离：拖动只写展示层，算法状态（遗忘/轨道）不受操作影响；
 * - 力场常量：REPUL=2200 / LINK_ATTR=0.0036 / SPRING_IDEAL=110 / DAMP=0.90
 *   （2026-09-19 用户反馈「节点离得太近、标签全重叠」——斥力与理想连长加大，布局拉开），
 *   alpha=max(0.04, 1-iter/300)；拖动期间 iter 钳 150（alpha 平台 0.5，V30 语义）；
 * - 布局 = 斥力 + 连线弹簧 + 净空带硬边界：**黑洞没有任何引力**（真机反馈「全员被吸到中间太丑、
 *   没有松弛感」——R65 起删除 holeGravity/遗忘引力倍率/深度遗忘向心漂移）；
 * - 布局：节点盘状分布于黑洞四周（黑洞坐镇盘心），冷启动全部生成在净空带之外；
 * - 净空带是永久硬边界（1 单位=核心半径，保护区=3 倍单位，用户 2026-09-19 拍板）：推力不再随遗忘衰减 + 积分位置钳制，
 *   任何 Free 节点都进不来，连线永远不会「接进」黑洞；
 * - 公转是常驻运动：settle 后仍按轨道角速度继续旋转（大图走轻量轨道路径，性能闸）；
 * - 遗忘生命周期 = 冷却保护模型（用户 R66 拍板，曲线数值为占位、等记忆遗忘曲线算法替换）：
 *   新节点默认在保护期（cooling，占位 90s）内——正常节点状态 + 缓慢绕洞公转，遗忘冻结为 0；
 *   过保后遗忘开始增长，节点即刻脱离力场、独自保持旋转并向黑洞中心漩涡靠拢；
 *   完全进入黑洞区域后由界面层做闪烁呼吸（即将遗忘的警告）；
 *   坠入核心 = 真实遗忘（0.15s 淡出后消失）；
 * - 漩涡旅程（Dying）：r = 起点半径 * (1-forget)^0.8，角速度 = 轨道角速度 * 1.6（越近越快），
 *   吸收只来自这段旅程的终点，且只动自己——脱离弹簧/斥力后不会拖着邻居进洞，
 *   全局布局全程不变；碰撞/拖拽误入核心的普通节点一律径向弹出，绝不算吸收；
 * - 拖动：跟手 + 甩动量 vx=位移*1.1，被拖节点不积分力但全程施力（含 x6 放大连线弹簧，
 *   关联节点被拖着走）；松手惯性滑停后回归物理层（不停泊），弹簧（胡克力+轴向阻尼）
 *   把节点收敛回圆润簇；黑洞上方（净空带内）显示 X、松手弹开不吞噬；
 * - 入场：0.9s 内节点从黑洞中心展开（easeOutCubic，仅渲染层）；
 * - 时间倍率：全局缩放（遗忘/公转/力场步长/螺旋坠入/淡出），惯性不受倍率影响（V20/V23）；
 * - 硬保护：速度 clamp 30 / NaN 重置 / 单帧拖拽位移 clamp 500（V26）。
 */
data class BlackHolePhysics(
    val repulsion: Float = 2200f,
    val linkAttraction: Float = 0.0036f,
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
    /** 净空带（遗忘区）半径比：1 单位=黑洞核心半径。R82 用户拍板：黑洞与节点之间的「遗忘区」半径翻倍 → 6 倍单位（2026-09-20，「光盘半径再增大一倍」所指即此区）。 */
    val exclusionRadiusRatio: Float = 6.0f,
    val exclusionPush: Float = 6.0f,
    val settlingSeconds: Float = 0.8f,
    val orbitPeriodSeconds: Float = 420f,
    val orbitDifferential: Float = 0.5f,
    val orbitReferenceRadius: Float = 200f,
    val orbitConvergence: Float = 0.5f,
    val forgettingFullSeconds: Float = 240f,
    val absorbFadeSeconds: Float = 0.15f,
    val deepForgetTangentialRatio: Float = 0.15f,
    val minNodeOpacity: Float = 0.28f,
    val minNodeScale: Float = 0.6f,
    val labelHideForget: Float = 0.5f,
    val bounceSpeed: Float = 260f,
    val inertiaDamping: Float = 0.84f,
    val dragMomentum: Float = 1.1f,
    val inertiaStopSpeed: Float = 6f,
    /** 拖拽期间连线弹簧放大倍数：让关联节点被带着走（真机反馈「关联的拖动性不够」）。 */
    val dragLinkBoost: Float = 6f,
    /** 弹簧轴向阻尼（相对速度投影系数）：让连线牵引收敛圆润、不振颤（真机反馈「假引力」）。 */
    val springDamping: Float = 0.06f,
    /** 吞噬遗忘门（兜底）：异常出现在核心的节点，遗忘 >= 此值即吞、否则弹开。正常吸收走漩涡旅程终点。 */
    val absorbForgetGate: Float = 0.85f,
    /** 冷却保护时长（秒，1x；占位值——等用户给记忆遗忘曲线算法后替换）。新节点默认在保护期内。 */
    val protectionSeconds: Float = 90f,
    /** 漩涡靠拢角提速倍率：过保节点保持绕黑洞旋转，且越靠近转得越快（漩涡感）。 */
    val decayOrbitBoost: Float = 1.6f,
    /** 半径-遗忘映射曲率：radius = startR * (1-forget)^pow；pow<1 时前段慢、后段加速坠入。 */
    val decayRadiusPow: Float = 0.8f,
    val entranceSeconds: Float = 0.9f,
    val fullForceNodeCap: Int = 140
)

enum class BlackHoleNodeMode {
    Free,
    Dragging,
    InertiaSliding,
    /** 螺旋坠入中：遗忘过门、脱离力场、独自旋进黑洞，不参与任何力/碰撞/命中。 */
    Dying,
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
    /** 冷却保护中：新节点默认开——正常力场布局 + 缓慢绕洞公转，遗忘冻结为 0、绝不坠入。 */
    var cooling: Boolean = true
    /** 保护期已过的时长（秒，随倍率）；达到 physics.protectionSeconds 即过保、开始遗忘。 */
    var coolingElapsed: Float = 0f
    /** 漩涡旅程起点半径（进入 Dying 瞬间到黑洞中心的距离，作为收缩基准）。 */
    var decayStartR: Float = 0f
    /** 漩涡旅程当前角度（保持公转方向继续加速旋转）。 */
    var decayAngle: Float = 0f

    /** 节点基础半径：9+2.4√degree → 10.5+2.8√degree（R68 用户反馈节点太小太淡，整体放大 ~17%）。 */
    val baseRadius: Float
        get() = 10.5f + 2.8f * sqrt(degree.toFloat().coerceAtLeast(0f))

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
    val holeX: Float = 0f,
    val holeY: Float = 0f,
    val holeCoreRadius: Float = 28f,
    val physics: BlackHolePhysics = BlackHolePhysics()
) {
    private val nodeList = mutableListOf<BlackHoleNode>()
    private val edgeList = mutableListOf<Pair<String, String>>()
    private var iter: Int = 0
    private var settled: Boolean = false
    private var draggingId: String? = null
    private var dragWasDying: Boolean = false
    private var absorbedTotal: Int = 0
    private var rescuedTotal: Int = 0
    private var simAccum: Float = 0f

    /** 入场进度 0->1（真实时间，不随倍率）。 */
    var entrance: Float = 0f
        private set

    /** 累计力场子步数（测试用）。 */
    var simulationSteps: Long = 0L
        private set

    var timeScale: Float = 1f
        set(value) {
            field = value.coerceIn(0.05f, 10f)
        }

    /** 拖动中指针是否位于净空带（渲染 X 标记用）。 */
    var dragOverHole: Boolean = false
        private set

    /** 已被黑洞吞噬的节点总数（渲染「已遗忘 N」）。 */
    val absorbedCount: Int
        get() = absorbedTotal

    /** 已被用户抢救回归的节点总数（D7 点击抢救复习）。 */
    val rescuedCount: Int
        get() = rescuedTotal

    val exclusionRadius: Float
        get() = holeCoreRadius * physics.exclusionRadiusRatio

    val nodes: List<BlackHoleNode>
        get() = nodeList

    // ---------- 初始化 ----------

    /**
     * 冷启动布局：节点盘状分布于黑洞四周（黑洞坐镇盘心，黄金角散布，确定性），
     * 全部生成在净空带之外；初始遗忘度按 id 错开，保证随时可见不同遗忘阶段（V21-5）。
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
        val ring0 = exclusionRadius + 34f
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
                // R82 用户拍板：暂不做真实遗忘——全员「刚创建状态」：保护期内、遗忘 0、
                // 正常绕黑洞公转（真实遗忘曲线等后端设计稿，届时恢复分阶段播种）。
                forget = 0f
            )
            nodeList.add(node)
        }
        val ids = nodeList.mapTo(HashSet()) { it.id }
        edges.filter { it.first in ids && it.second in ids }.forEach { edgeList.add(it) }
        iter = 0
        settled = false
        draggingId = null
        dragWasDying = false
        dragOverHole = false
        absorbedTotal = 0
        rescuedTotal = 0
        simAccum = 0f
        entrance = 0f
    }

    // ---------- 查询 ----------

    /** R81：濒死（Dying）节点同样可命中——拖回来=复习抢救，不再整段旅程不可交互（用户反馈「没办法拖动了」）。 */
    fun hitTest(x: Float, y: Float): BlackHoleNode? =
        nodeList.asReversed().firstOrNull { node ->
            !node.gone && !node.absorbed &&
                hypot(node.dispX - x, node.dispY - y) <= node.displayRadius(physics) * 1.35f
        }

    /**
     * 濒死可抢救命中：漩涡旅程（Dying）且已进入黑洞区域（闪烁呼吸段）的节点。
     * 命中半径额外放宽，方便用户点中持续移动的小节点（用户 2026-09-19 拍板 D7 点击抢救）。
     */
    fun hitTestRescuable(x: Float, y: Float): BlackHoleNode? =
        nodeList.asReversed().firstOrNull { node ->
            !node.gone && !node.absorbed && node.mode == BlackHoleNodeMode.Dying &&
                hypot(node.simX - holeX, node.simY - holeY) <= exclusionRadius &&
                hypot(node.dispX - x, node.dispY - y) <= node.displayRadius(physics) * 1.35f + 16f
        }

    /** 节点是否处于「可抢救」状态：断链离散期、已进入黑洞区域闪烁呼吸。 */
    fun isRescuable(nodeId: String): Boolean {
        val node = nodeList.firstOrNull { it.id == nodeId && !it.gone && !it.absorbed } ?: return false
        return node.mode == BlackHoleNodeMode.Dying &&
            hypot(node.simX - holeX, node.simY - holeY) <= exclusionRadius
    }

    /**
     * 抢救 = 用户主动点击、完成复习回顾（用户 2026-09-19 拍板 D7 断链离散方案）：
     * 遗忘清零、冷却保护重置、放回漩涡旅程起点半径（净空带外沿）、重新接入力场布局。
     * 数据层连线在离散期从未删除（仅渲染断链），抢救后自动重连、无需建边。
     */
    fun rescueNode(nodeId: String): Boolean {
        if (!isRescuable(nodeId)) return false
        val node = nodeList.first { it.id == nodeId }
        node.forget = 0f
        node.cooling = true
        node.coolingElapsed = 0f
        node.mode = BlackHoleNodeMode.Free
        val r = node.decayStartR.coerceAtLeast(exclusionRadius + node.baseRadius + 6f)
        node.simX = holeX + cos(node.decayAngle) * r
        node.simY = holeY + sin(node.decayAngle) * r
        node.dispX = node.simX
        node.dispY = node.simY
        node.vx = 0f
        node.vy = 0f
        node.decayStartR = 0f
        node.decayAngle = 0f
        node.settlingElapsed = 0f
        rescuedTotal++
        wake()
        return true
    }

    fun isInExclusionZone(x: Float, y: Float): Boolean =
        hypot(x - holeX, y - holeY) <= exclusionRadius

    fun isOverCore(x: Float, y: Float, radius: Float = 0f): Boolean =
        hypot(x - holeX, y - holeY) <= holeCoreRadius + radius

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

    /** 轨道角速度（rad/s，1x）：近快远慢差速 omega = base * (d0/d)^differential。 */
    fun orbitOmegaAt(d: Float): Float {
        val base = 2f * PI.toFloat() / physics.orbitPeriodSeconds
        val ratio = physics.orbitReferenceRadius / d.coerceAtLeast(1f)
        return base * Math.pow(ratio.toDouble(), physics.orbitDifferential.toDouble()).toFloat()
    }

    // ---------- 拖动 ----------

    fun startDrag(nodeId: String) {
        val node = nodeList.firstOrNull {
            it.id == nodeId && !it.gone && !it.absorbed
        } ?: return
        dragWasDying = node.mode == BlackHoleNodeMode.Dying
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
        dragOverHole = isInExclusionZone(node.dispX, node.dispY)
        iter = min(iter, physics.wakeIterationCap)
    }

    /**
     * 松手：净空带内 -> 弹开（不吞噬）；普通位置 -> 惯性滑停 -> 回归物理层由弹簧收敛（不停泊）。
     * R81：拖动濒死（Dying）节点 = 抓回来复习——松手即抢救（遗忘清零、冷却重置、回到力场），
     * 返回被抢救的节点 id；普通节点返回 null。
     */
    fun endDrag(): String? {
        val node = nodeList.firstOrNull { it.id == draggingId }
        val wasDying = dragWasDying
        draggingId = null
        dragWasDying = false
        dragOverHole = false
        if (node == null) return null
        if (isInExclusionZone(node.dispX, node.dispY)) {
            val dx = node.dispX - holeX
            val dy = node.dispY - holeY
            val len = hypot(dx, dy).takeIf { it > 0.001f } ?: 1f
            node.vx = dx / len * physics.bounceSpeed
            node.vy = dy / len * physics.bounceSpeed
        }
        node.mode = BlackHoleNodeMode.InertiaSliding
        if (wasDying) {
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
        dragOverHole = false
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
     * （每秒 60*timeScale 个子步），公转在 settle 后的轻量路径里同样按 timeScale 缩放。
     * 惯性/沉淀/入场按真实时间（V23：惯性不受倍率影响）。
     */
    fun tick(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.0005f, 0.1f)

        if (entrance < 1f) {
            entrance = min(1f, entrance + dt / physics.entranceSeconds)
        }
        advanceForgetting(dt)
        advanceDying(dt)
        advanceDisplayModes(dt)
        if (settled && nodeList.size > physics.fullForceNodeCap) {
            orbitLightPath(dt)
        } else {
            simAccum += dt * 60f * timeScale
            var guard = 0
            while (simAccum >= 1f && guard < 12) {
                simulateForces()
                simAccum -= 1f
                guard++
                simulationSteps++
            }
            if (simAccum > 12f) simAccum = 0f
        }
        checkAbsorption(dt)
    }

    /**
     * 遗忘推进（冷却保护模型，用户 R66 拍板）：
     * - 保护期内（新节点默认）：遗忘冻结为 0，节点正常力场布局 + 缓慢绕洞公转，绝不坠入；
     * - 保护期结束：遗忘开始增长，该节点即刻脱离力场、独自沿漩涡轨道向黑洞中心靠拢
     *   （保持旋转、逐渐加速），直到坠入核心 = 真实遗忘（消失）。
     * 具体曲线数值为占位，等用户给记忆遗忘曲线算法后替换。
     */
    private fun advanceForgetting(dt: Float) {
        val scaled = dt * timeScale
        val step = scaled / physics.forgettingFullSeconds
        nodeList.forEach { node ->
            if (node.gone || node.absorbed) return@forEach
            if (node.cooling) {
                node.coolingElapsed += scaled
                if (node.coolingElapsed >= physics.protectionSeconds) node.cooling = false
                return@forEach
            }
            node.forget = (node.forget + step).coerceAtMost(1f)
            if (node.mode == BlackHoleNodeMode.Free) enterDying(node)
        }
    }

    /** 进入漩涡旅程：脱离力场、清零速度，记录当前半径/角度作为收缩与旋转基准。 */
    private fun enterDying(node: BlackHoleNode) {
        node.mode = BlackHoleNodeMode.Dying
        node.vx = 0f
        node.vy = 0f
        if (node.decayStartR <= 0.001f) {
            val dx = node.simX - holeX
            val dy = node.simY - holeY
            node.decayStartR = hypot(dx, dy).coerceAtLeast(exclusionRadius)
            node.decayAngle = atan2(dy, dx)
        }
    }

    /**
     * 漩涡旅程（Dying）：过保节点独自旋进黑洞——保持公转方向加速旋转（越近越快），
     * 半径按遗忘进度从起点平滑收缩：r = startR * (1-forget)^pow，抵核（0.6x 核心半径）
     * 或遗忘打满即被吞 = 真实遗忘。不参与力场/碰撞/命中，不扰动其他任何节点；
     * 全局布局全程不变（真机反馈算法层重构 + 用户冷却保护设计）。
     */
    private fun advanceDying(dt: Float) {
        val scaled = dt * timeScale
        nodeList.forEach { node ->
            if (node.gone || node.absorbed || node.mode != BlackHoleNodeMode.Dying) return@forEach
            if (node.decayStartR <= 0.001f) enterDying(node)
            val dNow = hypot(node.simX - holeX, node.simY - holeY).coerceAtLeast(1f)
            node.decayAngle += orbitOmegaAt(dNow) * physics.decayOrbitBoost * scaled
            val remaining = (1f - node.forget).coerceIn(0f, 1f)
            val r = node.decayStartR * Math.pow(remaining.toDouble(), physics.decayRadiusPow.toDouble()).toFloat()
            // R82：物理位置已抵核同样即吞——净空带翻倍（半径比 3→6）后，深遗忘节点若在带内
            // 进入 Dying，decayStartR 被钳到净空带半径会把它「从核心弹回带外」、旅程凭空变长。
            if (r <= holeCoreRadius * 0.6f || node.forget >= 1f || dNow <= holeCoreRadius * 0.6f) {
                // 抵达核心：吞噬 = 真实遗忘（漩涡旅程的唯一终点）
                node.absorbed = true
                node.absorbFade = 0f
                absorbedTotal++
                return@forEach
            }
            node.simX = holeX + cos(node.decayAngle) * r
            node.simY = holeY + sin(node.decayAngle) * r
            node.dispX = node.simX
            node.dispY = node.simY
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

    /** settle 后大图的轻量路径：刚性公转 + 净空带违规唤醒（V19；R65 起无向心漂移——遗忘不再牵引位置）。 */
    private fun orbitLightPath(dt: Float) {
        val scaled = dt * timeScale
        nodeList.forEach { node ->
            if (node.gone || node.absorbed || node.mode != BlackHoleNodeMode.Free) return@forEach
            val dx = node.simX - holeX
            val dy = node.simY - holeY
            val d = hypot(dx, dy)
            if (d <= 0.001f) return@forEach
            val omega = orbitOmegaAt(d) * scaled
            val cosA = cos(omega)
            val sinA = sin(omega)
            val nx = dx * cosA - dy * sinA
            val ny = dx * sinA + dy * cosA
            node.simX = holeX + nx
            node.simY = holeY + ny
            node.dispX = node.simX
            node.dispY = node.simY
        }
        nodeList.forEach { node ->
            if (!node.gone && !node.absorbed && node.mode == BlackHoleNodeMode.Free && node.forget < 0.5f) {
                val d = hypot(node.simX - holeX, node.simY - holeY)
                if (d < exclusionRadius * 0.95f) wake()
            }
        }
    }

    private fun simulateForces() {
        val alpha = max(physics.alphaFloor, 1f - iter.toFloat() / physics.alphaIterations)
        // Dying（螺旋坠入中）彻底脱离力场：不受力也不施力
        val freeNodes = nodeList.filter {
            !it.gone && !it.absorbed && it.mode != BlackHoleNodeMode.Dying
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

        // 3) 公转切向 + 净空带硬边界（只作用算法层 Free 节点）。
        //    R65 起黑洞没有任何引力（真机反馈「全员被吸到中间太丑」）：布局只由斥力/弹簧/净空带构成，
        //    遗忘只让公转冷却（切向 ->15%），不再牵引位置。
        freeNodes.forEach { node ->
            if (node.mode != BlackHoleNodeMode.Free) return@forEach
            val dx = holeX - node.simX
            val dy = holeY - node.simY
            val d = hypot(dx, dy).takeIf { it > 0.001f } ?: 0.001f
            val ux = dx / d
            val uy = dy / d
            // 公转：切向速度向轨道目标收敛（常驻运动，不随 alpha 冷却消失；遗忘加深 -> 冷却变慢）
            val tangentialRatio = 1f - (1f - physics.deepForgetTangentialRatio) * node.forget
            val targetT = orbitOmegaAt(d) * d * tangentialRatio / 60f
            val tx = -uy
            val ty = ux
            val currentT = node.vx * tx + node.vy * ty
            val nudge = (targetT - currentT) * physics.orbitConvergence
            fx[node.id] = (fx[node.id] ?: 0f) + tx * nudge
            fy[node.id] = (fy[node.id] ?: 0f) + ty * nudge
            // 4) 净空带永久硬边界：推力不随遗忘衰减——任何 Free 节点都不得靠近黑洞
            if (d < exclusionRadius) {
                val push = physics.exclusionPush * (exclusionRadius - d) / exclusionRadius
                fx[node.id] = (fx[node.id] ?: 0f) - ux * push
                fy[node.id] = (fy[node.id] ?: 0f) - uy * push
            }
        }

        // 5) 积分 + 阻尼 + 速度 clamp + NaN 守卫 + 净空带位置钳制
        freeNodes.forEach { node ->
            when (node.mode) {
                BlackHoleNodeMode.Free -> {
                    node.vx = (node.vx + (fx[node.id] ?: 0f)) * physics.damping
                    node.vy = (node.vy + (fy[node.id] ?: 0f)) * physics.damping
                    clampVelocity(node)
                    if (guardNaN(node)) return@forEach
                    node.simX += node.vx
                    node.simY += node.vy
                    // 净空带位置钳制：任何 Free 节点都不得停留在净空带内，
                    // 连线端点永远在带外，连线自然不会「接进」黑洞（真机反馈）
                    val cdx = node.simX - holeX
                    val cdy = node.simY - holeY
                    val cd = hypot(cdx, cdy)
                    if (cd < exclusionRadius && cd > 0.001f) {
                        val k = exclusionRadius / cd
                        node.simX = holeX + cdx * k
                        node.simY = holeY + cdy * k
                        // 去掉径向向内速度分量，防钳制抖动
                        val ux = cdx / cd
                        val uy = cdy / cd
                        val vr = node.vx * ux + node.vy * uy
                        if (vr < 0f) {
                            node.vx -= ux * vr
                            node.vy -= uy * vr
                        }
                    }
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
            node.simX = holeX + exclusionRadius + 120f
            node.simY = holeY
            node.dispX = node.simX
            node.dispY = node.simY
            node.vx = 0f
            node.vy = 0f
            return true
        }
        return false
    }

    /**
     * 吞噬收尾： absorbed 淡出 -> gone 并清边；触核判定兜底——正常吸收走 Dying 螺旋坠入
     * （advanceDying 抵核置 absorbed），这里只处理「直接出现在核心」的异常情形：
     * 遗忘过门者即吞，普通节点（碰撞/拖拽误入）径向弹出、绝不算吸收（真机反馈）。
     * 每帧、全体、拖拽豁免（V17/V23；淡出随倍率）。
     */
    private fun checkAbsorption(dt: Float) {
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
                continue
            }
            if (node.mode == BlackHoleNodeMode.Dragging) continue
            // 漩涡旅程中的节点豁免触核兜底——旅程后段本来就要穿越核心邻域，
            // 它的终点只由 advanceDying 判定（抵核/遗忘打满 -> absorbed）；
            // 若在这里按「误闯核心」弹开，会把末段旅程的节点甩回净空带、旅程永远走不完。
            if (node.mode == BlackHoleNodeMode.Dying) continue
            val r = node.displayRadius(physics)
            if (hypot(node.simX - holeX, node.simY - holeY) <= holeCoreRadius + r) {
                if (node.forget >= physics.absorbForgetGate) {
                    // 真·算法吸收：深度遗忘节点触核 -> 吞噬
                    node.absorbed = true
                    node.absorbFade = 0f
                    absorbedTotal++
                } else {
                    // 误闯核心（碰撞推入/拖拽惯性）-> 径向弹出到净空带外，不计入吸收
                    val dx = node.simX - holeX
                    val dy = node.simY - holeY
                    val len = hypot(dx, dy).takeIf { it > 0.001f } ?: 1f
                    val ux = if (len > 0.001f) dx / len else 1f
                    val uy = if (len > 0.001f) dy / len else 0f
                    node.simX = holeX + ux * (exclusionRadius + 10f)
                    node.simY = holeY + uy * (exclusionRadius + 10f)
                    node.dispX = node.simX
                    node.dispY = node.simY
                    node.vx = ux * physics.bounceSpeed
                    node.vy = uy * physics.bounceSpeed
                    node.mode = BlackHoleNodeMode.Free
                    wake()
                }
            }
        }
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
            // D7 断链离散：漩涡旅程中的节点视觉上断开与母节点的连线
            // （数据层 edgeList 未删，抢救回归后自动重连）
            if (a.mode == BlackHoleNodeMode.Dying || b.mode == BlackHoleNodeMode.Dying) return@mapNotNull null
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
