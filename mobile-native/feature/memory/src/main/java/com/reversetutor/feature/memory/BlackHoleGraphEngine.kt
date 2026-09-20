package com.reversetutor.feature.memory

import com.reversetutor.core.model.GraphNodeKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
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
 * - 布局 = 斥力 + 连线弹簧 + 净空带硬边界 + 黑洞引力（R87 开普勒模型，见下；引力极弱、
 *   只负责弯曲公转轨迹，远小于布局力，不会重演 R65「全员被吸到中间」）；
 * - 布局：节点盘状分布于黑洞四周（黑洞坐镇盘心），冷启动全部生成在净空带之外；
 * - 净空带硬边界（1 单位=核心半径，遗忘区=6 倍单位，R82 拍板）：健康节点（保护期/低遗忘）
 *   推力 + 积分位置钳制，绝进不来；R87 起屏障随遗忘渗透——遗忘加深屏障渐软，衰减节点可穿入；
 * - 公转是常驻运动：settle 后仍继续旋转（大图走轻量轨道路径，性能闸）；
 * - R83 层级轨道（用户 2026-09-20 拍板）：度数最高的 <=3 个节点为根（绕黑洞公转的行星），
 *   其余节点多源 BFS 挂母——子节点各自以母节点为轨道中心公转（卫星），整族随母节点绕洞转动；
 *   R85（用户拍板）：关系线一律直线，绝不弯曲——圆感靠公转与边数量自然形成，不强制绕行；
 *   R87（用户拍板）：直线几何不动，遗忘区内渲染渐隐遮断（界面层分段 alpha），视觉不穿越；
 * - R87 开普勒引力模型（用户 2026-09-20 拍板，废止 R65「黑洞无引力」）：
 *   黑洞对节点有真实引力 F=GM/d²，节点出生自带圆轨道速度 v=√(GM/d)，引力恰好充当向心力
 *   ——公转不再是脚本切向推进，是引力与初速度的真实平衡；阻尼拆径向（强，吸收扰动）/
 *   切向（零，保住公转），外加弱「潮汐正则化」把切向速度缓推向 v_circ、维持圆轨道；
 * - 遗忘 = 轨道衰减（R87）：过保后遗忘增长，切向速度目标按 (1-forget) 抽走（刹车），
 *   引力占优、轨道自然内旋——活动区 -> 遗忘区 -> 核心，连续渐进、绝无到点瞬移；
 *   进入遗忘区后界面层闪烁呼吸（即将遗忘警告）；触核（遗忘过门）= 真实遗忘（0.15s 淡出）；
 *   抢救 = 重新注入轨道速度（遗忘清零 + 冷却重置 + v=v_circ），可逆；
 *   全程不脱离力场：衰减节点仍参与斥力/碰撞/弹簧，不拖邻居进洞（引力只作用于自身）；
 *   曲线数值仍是占位，等后端记忆遗忘曲线算法接管；
 * - 误入核心的健康节点（碰撞/拖拽）一律径向弹出，绝不算吸收；
 * - 拖动：跟手 + 甩动量 vx=位移*1.1，被拖节点不积分力但全程施力（含 x6 放大连线弹簧，
 *   关联节点被拖着走）；松手惯性滑停后回归物理层（不停泊），弹簧（胡克力+轴向阻尼）
 *   把节点收敛回圆润簇；黑洞上方（净空带内）显示 X、松手弹开不吞噬；
 * - 入场：0.9s 内节点从黑洞中心展开（easeOutCubic，仅渲染层）；
 * - 时间倍率：全局缩放（遗忘/公转/力场步长/螺旋坠入/淡出），惯性不受倍率影响（V20/V23）；
 * - 硬保护：速度 clamp 30 / NaN 重置 / 单帧拖拽位移 clamp 500（V26）。
 */
data class BlackHolePhysics(
    val repulsion: Float = 2200f, // 节点自带斥力（a3d426c 实测稳定值；R88 试 2600/加宽 padding 均撕裂图谱，已回退——重叠改由位置级修正解决）
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
    /** R87 开普勒引力常数（引擎子步单位）：圆轨道条件 v_sub=√(GM/d)。
     *  取值锚点：200px 半径公转周期 ~420s（v≈3px/s=0.05px/子步）→ GM=v²·d=0.5。
     *  引力极弱（200px 处 ~1.25e-5/子步），远小于布局力，只负责弯曲轨迹、不动布局。 */
    val holeGravityGM: Float = 0.5f,
    /** R87 潮汐正则化强度（每子步）：切向速度缓推向 v_circ(d)×(1-forget)。
     *  0.01/子步 ≈ 时间常数 1.7s——扰动后重新圆化；遗忘加深时表现为渐进刹车。 */
    val orbitRegularization: Float = 0.01f,
    /** R87 切向阻尼：1.0=不衰减（公转靠引力+初速度自持）；径向仍用 damping=0.90 强吸收。 */
    val tangentialDamping: Float = 1.0f,
    /** R87 屏障渗透门：遗忘 >= 此值后净空带位置钳制失效，衰减节点可穿入遗忘区。 */
    val barrierForgetGate: Float = 0.5f,
    /** R87 衰减节点径向阻尼：远弱于健康节点（damping=0.90 会把内旋冻成爬行），
     *  让刹车后的轨道能以可见速度渐进内旋、最终坠入核心。 */
    val decayRadialDamping: Float = 0.99f,
    val forgettingFullSeconds: Float = 240f,
    val absorbFadeSeconds: Float = 0.15f,
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
    /** 吞噬遗忘门：触核节点遗忘 >= 此值即吞（R87 正常吸收的终点），健康误闯者弹开。 */
    val absorbForgetGate: Float = 0.85f,
    /** 冷却保护时长（秒，1x；占位值——等用户给记忆遗忘曲线算法后替换）。新节点默认在保护期内。 */
    val protectionSeconds: Float = 90f,
    val entranceSeconds: Float = 0.9f,
    val fullForceNodeCap: Int = 140,
    /** R83 层级轨道：子节点绕母节点公转的周期（秒，1x）——母节点绕黑洞、子节点绕母节点，太阳系式嵌套。 */
    val childOrbitPeriodSeconds: Float = 60f,
    /** R83 层级轨道：子节点绕母节点的基础轨道半径（世界单位）。 */
    val childOrbitRadius: Float = 95f,
    /** R83 连线绕行：折线沿净空带外沿绕行的边距（世界单位），保证关系线绝不横穿黑洞区域。 */
    val edgeZoneMargin: Float = 18f
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
    /** 冷却保护中：新节点默认开——正常力场布局 + 绕洞公转，遗忘冻结为 0、绝不坠入。 */
    var cooling: Boolean = true
    /** 保护期已过的时长（秒，随倍率）；达到 physics.protectionSeconds 即过保、开始遗忘。 */
    var coolingElapsed: Float = 0f

    /** R83 层级轨道：母节点 id（null=根节点，走黑洞力场+绕洞公转；非空=以母节点为轨道中心公转）。 */
    var parentId: String? = null
    /** R83 层级轨道：子节点绕母节点的当前相位角（弧度）。 */
    var orbitAngle: Float = 0f
    /** R83 层级轨道：子节点当前轨道半径（拖拽拉伸后向 orbitRTarget 缓慢回弹）。 */
    var orbitR: Float = 0f
    /** R83 层级轨道：子节点轨道半径的回弹目标。 */
    var orbitRTarget: Float = 0f

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
        dragWasDecaying = false
        dragOverHole = false
        absorbedTotal = 0
        rescuedTotal = 0
        simAccum = 0f
        entrance = 0f
        // R83 层级轨道：按度数选根（<=3 个），多源 BFS 定母节点——
        // 子节点不再挤在净空带外的环带上，而是各自以母节点为中心公转（用户 2026-09-20 拍板）
        assignHierarchy()
        // R87 开普勒初速度：根节点出生自带圆轨道切向速度 v=√(GM/d)（引力充当向心力）
        nodeList.forEach { node ->
            if (node.parentId == null) injectOrbitalVelocity(node)
        }
    }

    /**
     * R87：给节点注入当前半径的圆轨道切向速度（per-子步单位）。
     * 方向与历史公转一致（外向径向 u 下取 t=(uy,-ux)）；抢救/晋升等「重新入轨」场景同用。
     */
    private fun injectOrbitalVelocity(node: BlackHoleNode) {
        val dx = node.simX - holeX
        val dy = node.simY - holeY
        val d = hypot(dx, dy).coerceAtLeast(1f)
        val v = sqrt(physics.holeGravityGM / d)
        node.vx = (dy / d) * v
        node.vy = (-dx / d) * v
    }

    /** R87：节点是否处于衰减期（过保、遗忘增长中）——屏障对其渗透、可点按/拖拽抢救。 */
    private fun isDecaying(node: BlackHoleNode): Boolean =
        !node.cooling && node.forget > 0f && !node.absorbed && !node.gone

    /**
     * R83 层级轨道分配：度数最高的 min(3, max(1, N/6)) 个节点为根（黑洞的直接行星），
     * 其余节点多源 BFS 挂到最近的母节点下（卫星）。子节点初始位置 = 母节点 + 极角(相位, 半径)，
     * 同母节点的兄弟姐妹相位均匀散开；不可达/孤立节点一律为根。子节点同样钳制在净空带外。
     */
    private fun assignHierarchy() {
        if (nodeList.isEmpty()) return
        val byId = nodeList.associateBy { it.id }
        val rootCount = min(3, max(1, nodeList.size / 6))
        val roots = nodeList.sortedWith(
            compareByDescending<BlackHoleNode> { it.degree }.thenBy { it.id }
        ).take(rootCount)
        val parentOf = HashMap<String, String>()
        val queue = ArrayDeque<String>()
        roots.forEach { root ->
            queue.add(root.id)
            parentOf[root.id] = "" // 根标记（空串）
        }
        val bfsOrder = mutableListOf<String>()
        val adj = HashMap<String, MutableList<String>>()
        edgeList.forEach { (a, b) ->
            adj.getOrPut(a) { mutableListOf() }.add(b)
            adj.getOrPut(b) { mutableListOf() }.add(a)
        }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            bfsOrder.add(cur)
            adj[cur]?.forEach { next ->
                if (!parentOf.containsKey(next)) {
                    parentOf[next] = cur
                    queue.add(next)
                }
            }
        }
        nodeList.forEach { node ->
            node.parentId = null
            node.orbitAngle = 0f
            node.orbitR = 0f
            node.orbitRTarget = 0f
        }
        val childrenOf = HashMap<String, MutableList<BlackHoleNode>>()
        nodeList.forEach { node ->
            val p = parentOf[node.id]
            if (!p.isNullOrEmpty()) {
                node.parentId = p
                childrenOf.getOrPut(p) { mutableListOf() }.add(node)
            }
        }
        // BFS 顺序逐层摆放：母节点位置先定，孩子相对母亲计算
        bfsOrder.forEach { pid ->
            val kids = childrenOf[pid] ?: return@forEach
            val parent = byId[pid] ?: return@forEach
            val base = (pid.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7fffffff } % 628) / 100f
            kids.forEachIndexed { i, kid ->
                kid.orbitAngle = base + i * (2f * PI.toFloat() / kids.size)
                kid.orbitRTarget = physics.childOrbitRadius + 14f * (i % 3)
                kid.orbitR = kid.orbitRTarget
                var x = parent.simX + cos(kid.orbitAngle) * kid.orbitR
                var y = parent.simY + sin(kid.orbitAngle) * kid.orbitR
                val d = hypot(x - holeX, y - holeY)
                val minD = exclusionRadius + kid.displayRadius(physics) + 4f
                if (d < minD && d > 0.001f) {
                    val k = minD / d
                    x = holeX + (x - holeX) * k
                    y = holeY + (y - holeY) * k
                }
                kid.simX = x
                kid.simY = y
                kid.dispX = x
                kid.dispY = y
            }
        }
    }

    // ---------- 查询 ----------

    /** R81/R87：衰减期节点同样可命中——拖回来=复习抢救，全程可交互（用户反馈「没办法拖动了」）。 */
    fun hitTest(x: Float, y: Float): BlackHoleNode? =
        nodeList.asReversed().firstOrNull { node ->
            !node.gone && !node.absorbed &&
                hypot(node.dispX - x, node.dispY - y) <= node.displayRadius(physics) * 1.35f
        }

    /**
     * 可抢救命中（R87）：衰减期（过保、遗忘>0）且已进入遗忘区（闪烁呼吸段）的节点。
     * 命中半径额外放宽，方便用户点中持续移动的小节点（用户 2026-09-19 拍板 D7 点击抢救）。
     */
    fun hitTestRescuable(x: Float, y: Float): BlackHoleNode? =
        nodeList.asReversed().firstOrNull { node ->
            isDecaying(node) &&
                hypot(node.simX - holeX, node.simY - holeY) <= exclusionRadius &&
                hypot(node.dispX - x, node.dispY - y) <= node.displayRadius(physics) * 1.35f + 16f
        }

    /** 节点是否处于「可抢救」状态：衰减期、已进入遗忘区闪烁呼吸。 */
    fun isRescuable(nodeId: String): Boolean {
        val node = nodeList.firstOrNull { it.id == nodeId && !it.gone && !it.absorbed } ?: return false
        return isDecaying(node) &&
            hypot(node.simX - holeX, node.simY - holeY) <= exclusionRadius
    }

    /**
     * 抢救 = 用户主动点击、完成复习回顾（用户 2026-09-19 拍板 D7；R87 开普勒化）：
     * 遗忘清零、冷却保护重置、**重新注入轨道速度**（放回遗忘区外沿 + v=v_circ 圆轨道速度），
     * 节点即刻恢复稳定公转。数据层连线在衰减期从未断开，抢救后自然恢复亮度、无需建边。
     */
    fun rescueNode(nodeId: String): Boolean {
        if (!isRescuable(nodeId)) return false
        val node = nodeList.first { it.id == nodeId }
        node.forget = 0f
        node.cooling = true
        node.coolingElapsed = 0f
        node.mode = BlackHoleNodeMode.Free
        // 保持当前方位角，放回遗忘区外沿
        val dx = node.simX - holeX
        val dy = node.simY - holeY
        val angle = atan2(dy, dx)
        val r = exclusionRadius + node.baseRadius + 6f
        node.simX = holeX + cos(angle) * r
        node.simY = holeY + sin(angle) * r
        node.dispX = node.simX
        node.dispY = node.simY
        node.settlingElapsed = 0f
        // R83：被抢救的子节点以母节点为中心重建轨道（从放回点起算相位/半径，向目标回弹归队）
        if (node.parentId != null) {
            val parent = nodeList.firstOrNull { it.id == node.parentId }
            if (parent != null) {
                val pdx = node.simX - parent.dispX
                val pdy = node.simY - parent.dispY
                var d = hypot(pdx, pdy)
                if (d < 24f) d = 24f
                node.orbitAngle = atan2(pdy, pdx)
                if (node.orbitRTarget <= 0.001f) node.orbitRTarget = physics.childOrbitRadius
                node.orbitR = d
            }
            node.vx = 0f
            node.vy = 0f
        } else {
            // R87 抢救 = 重新注入轨道速度：v=v_circ，恢复稳定公转
            injectOrbitalVelocity(node)
        }
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

    /** R87：半径 d 处的圆轨道线速度（px/子步）——v=√(GM/d)，近快远慢天然开普勒差速。 */
    fun circularOrbitSpeedAt(d: Float): Float =
        sqrt(physics.holeGravityGM / d.coerceAtLeast(1f))

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
        dragOverHole = isInExclusionZone(node.dispX, node.dispY)
        iter = min(iter, physics.wakeIterationCap)
    }

    /**
     * 松手：净空带内 -> 弹开（不吞噬）；普通位置 -> 惯性滑停 -> 回归物理层由弹簧收敛（不停泊）。
     * R81/R87：拖动衰减期节点 = 抓回来复习——松手即抢救（遗忘清零、冷却重置、重新入轨），
     * 返回被抢救的节点 id；普通节点返回 null。
     */
    fun endDrag(): String? {
        val node = nodeList.firstOrNull { it.id == draggingId }
        val wasDecaying = dragWasDecaying
        draggingId = null
        dragWasDecaying = false
        dragOverHole = false
        if (node == null) return null
        if (isInExclusionZone(node.dispX, node.dispY) && !wasDecaying) {
            val dx = node.dispX - holeX
            val dy = node.dispY - holeY
            val len = hypot(dx, dy).takeIf { it > 0.001f } ?: 1f
            if (node.parentId != null) {
                // R83：子节点不带洞上弹射惯性——先径向钳出净空带，再回到母节点轨道
                val k = exclusionRadius / len.coerceAtLeast(0.001f)
                node.dispX = holeX + dx * k
                node.dispY = holeY + dy * k
            } else {
                node.vx = dx / len * physics.bounceSpeed
                node.vy = dy / len * physics.bounceSpeed
            }
        }
        if (node.parentId != null) {
            // R83：子节点松手不走惯性滑停——以母节点为中心重建轨道
            // （半径保持当前拉伸量，向 orbitRTarget 缓慢回弹 = 橡皮筋手感）
            val parent = nodeList.firstOrNull { it.id == node.parentId }
            if (parent != null && !parent.gone && !parent.absorbed && !isDecaying(parent)) {
                val dx = node.dispX - parent.dispX
                val dy = node.dispY - parent.dispY
                var d = hypot(dx, dy)
                if (d < 24f) d = 24f
                node.orbitAngle = atan2(dy, dx)
                if (node.orbitRTarget <= 0.001f) node.orbitRTarget = physics.childOrbitRadius
                node.orbitR = d
                node.vx = 0f
                node.vy = 0f
                node.mode = BlackHoleNodeMode.Free
            } else {
                node.parentId = null
                node.mode = BlackHoleNodeMode.InertiaSliding
            }
        } else {
            node.mode = BlackHoleNodeMode.InertiaSliding
        }
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
        dragOverHole = false
        wake()
    }

    fun wake() {
        settled = false
        iter = min(iter, physics.wakeIterationCap)
        simAccum = 0f
    }

    /** R83 层级轨道：子节点在 Free 态是运动学节点（位置由母节点+轨道决定），不积分任何力。 */
    private fun isKinematic(node: BlackHoleNode): Boolean =
        node.parentId != null && node.mode == BlackHoleNodeMode.Free

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
        advanceDisplayModes(dt)
        advanceChildren(dt)
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
     * 遗忘推进（冷却保护模型，用户 R66 拍板；R87 开普勒化）：
     * - 保护期内（新节点默认）：遗忘冻结为 0，节点正常力场布局 + 绕洞公转，绝不坠入；
     * - 保护期结束：遗忘开始增长，节点**不脱离力场**——切向速度目标按 (1-forget) 抽走
     *   （刹车），引力占优、轨道自然内旋（见 simulateForces 第 3 节），连续渐进绝无瞬移；
     *   过保瞬间其子节点晋升为根（断链离散语义：孩子不跟着坠洞）。
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
                    // R83/R87：母节点开始衰减——子节点就地晋升为根并注入圆轨道速度，不跟坠
                    nodeList.forEach { c ->
                        if (c.parentId == node.id) {
                            c.parentId = null
                            c.orbitR = 0f
                            c.orbitRTarget = 0f
                            injectOrbitalVelocity(c)
                        }
                    }
                }
                return@forEach
            }
            node.forget = (node.forget + step).coerceAtMost(1f)
        }
    }

    /**
     * R83 层级轨道推进：子节点以母节点为轨道中心公转（相位等速推进 + 轨道半径向目标回弹），
     * 位置 = 母节点当前位置 + 极角(orbitAngle, orbitR)——母节点绕黑洞公转时整族随之转动。
     * 母节点已离散/被吞（不在表里）-> 子节点晋升为根，直接受黑洞力场管辖（断链离散语义）。
     * 净空带硬边界同样对子节点生效：轨道压进带内时径向钳出到带外。
     */
    private fun advanceChildren(dt: Float) {
        if (nodeList.none { it.parentId != null }) return
        val scaled = dt * timeScale
        val byId = nodeList.associateBy { it.id }
        val childOmega = 2f * PI.toFloat() / physics.childOrbitPeriodSeconds
        nodeList.forEach { node ->
            val pid = node.parentId ?: return@forEach
            if (node.gone || node.absorbed) return@forEach
            if (isDecaying(node)) {
                // R87：子节点自身进入衰减——脱离母节点轨道，归为根受黑洞引力管辖（刹车内旋）
                node.parentId = null
                node.orbitR = 0f
                node.orbitRTarget = 0f
                if (node.mode == BlackHoleNodeMode.Free) injectOrbitalVelocity(node)
                wake()
                return@forEach
            }
            val parent = byId[pid]
            if (parent == null || parent.gone || parent.absorbed || isDecaying(parent)) {
                // 母节点已离散/被吞/进入衰减：晋升为根，回到黑洞力场（位置就地继承 + 注入轨道速度）
                node.parentId = null
                node.orbitR = 0f
                node.orbitRTarget = 0f
                if (node.mode == BlackHoleNodeMode.Free) injectOrbitalVelocity(node)
                wake()
                return@forEach
            }
            if (node.mode != BlackHoleNodeMode.Free) return@forEach // 拖拽/惯性中由手势层管
            node.orbitAngle += childOmega * scaled
            if (node.orbitR != node.orbitRTarget) {
                node.orbitR += (node.orbitRTarget - node.orbitR) * (1f - exp(-2.5f * scaled)).coerceIn(0f, 1f)
            }
            var x = parent.dispX + cos(node.orbitAngle) * node.orbitR
            var y = parent.dispY + sin(node.orbitAngle) * node.orbitR
            val d = hypot(x - holeX, y - holeY)
            val minD = exclusionRadius + node.displayRadius(physics) + 4f
            if (d < minD) {
                if (d <= 0.001f) {
                    x = holeX + minD
                    y = holeY
                } else {
                    val k = minD / d
                    x = holeX + (x - holeX) * k
                    y = holeY + (y - holeY) * k
                }
            }
            node.simX = x
            node.simY = y
            node.dispX = x
            node.dispY = y
            node.vx = 0f
            node.vy = 0f
        }
    }


    // R87：Dying 脚本化漩涡旅程已退役——遗忘衰减全程由开普勒力学驱动（刹车内旋，见
    // simulateForces 第 3 节），触核吸收见 checkAbsorption，呼吸警告由界面层按
    // 「衰减期且位于遗忘区内」判定。

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

    /**
     * settle 后大图的轻量路径（V19 性能闸；R87 开普勒化）：不再脚本摆位，
     * 只做 O(n) 速度积分——引力加速 + 潮汐正则化 + 按速度前进，与全力场路径同一套物理。
     * 衰减节点在此路径同样刹车内旋；健康节点越界（被拖入带）仍唤醒回全力场。
     */
    private fun orbitLightPath(dt: Float) {
        val scaled = dt * timeScale
        val steps = (scaled * 60f).toInt().coerceIn(0, 12)
        if (steps <= 0) return
        nodeList.forEach { node ->
            if (node.gone || node.absorbed || node.mode != BlackHoleNodeMode.Free) return@forEach
            // R83：子节点随母节点整体转动（advanceChildren），自身不绕洞旋转
            if (node.parentId != null) return@forEach
            repeat(steps) {
                val dx = node.simX - holeX
                val dy = node.simY - holeY
                val d = hypot(dx, dy)
                if (d <= 1f) return@repeat
                val ux = dx / d
                val uy = dy / d
                // 引力（向心）
                val g = physics.holeGravityGM / (d * d)
                node.vx -= ux * g
                node.vy -= uy * g
                // 潮汐正则化：切向速度缓推向 v_circ×(1-forget)（衰减=刹车内旋）
                val tx = uy
                val ty = -ux
                val vtTarget = circularOrbitSpeedAt(d) * (1f - node.forget.coerceIn(0f, 1f))
                val vt = node.vx * tx + node.vy * ty
                val reg = (vtTarget - vt) * physics.orbitRegularization
                node.vx += tx * reg
                node.vy += ty * reg
                // R87b 踢动保护：切向速度超出 1.3×v_circ（逃逸余量 √2 以内）说明是
                // 碰撞/拖拽踢动而非公转——强阻尼快速放掉异常能量，防节点逃逸出屏
                val vtAfter = node.vx * tx + node.vy * ty
                if (abs(vtAfter) > 1.3f * circularOrbitSpeedAt(d)) {
                    node.vx += tx * (vtAfter * (physics.damping - 1f))
                    node.vy += ty * (vtAfter * (physics.damping - 1f))
                }
                node.simX += node.vx
                node.simY += node.vy
            }
            node.dispX = node.simX
            node.dispY = node.simY
        }
        nodeList.forEach { node ->
            if (node.parentId == null && !node.gone && !node.absorbed &&
                node.mode == BlackHoleNodeMode.Free && node.forget < physics.barrierForgetGate
            ) {
                val d = hypot(node.simX - holeX, node.simY - holeY)
                if (d < exclusionRadius * 0.95f) wake()
            }
        }
    }

    private fun simulateForces() {
        val alpha = max(physics.alphaFloor, 1f - iter.toFloat() / physics.alphaIterations)
        // R87：衰减期节点仍全程参与力场（受力也施力）——只有被吞/消失者退出
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
                // 并卸掉互相接近的相对速度分量（非弹性、只减能不加能）。
                // 力式碰撞在密集公转区会持续踢动形成能量棘轮（R88 两次调参均撕裂图谱），
                // 位置修正在结构上不可能积累能量，且立刻保证不重叠
                if (d < minD) {
                    val overlap = minD - d
                    val aMovable = a.mode == BlackHoleNodeMode.Free && !isKinematic(a)
                    val bMovable = b.mode == BlackHoleNodeMode.Free && !isKinematic(b)
                    if (aMovable && bMovable) {
                        val half = overlap / 2f
                        a.simX -= ux * half; a.simY -= uy * half
                        b.simX += ux * half; b.simY += uy * half
                        // relV = (vb-va)·u，<0 为互相接近——清零该分量（双方各担一半）
                        val relV = (b.vx - a.vx) * ux + (b.vy - a.vy) * uy
                        if (relV < 0f) {
                            val h = relV / 2f
                            a.vx += ux * h; a.vy += uy * h
                            b.vx -= ux * h; b.vy -= uy * h
                        }
                    } else if (aMovable) {
                        a.simX -= ux * overlap; a.simY -= uy * overlap
                        val vIn = a.vx * ux + a.vy * uy // a 朝向 b 的分量
                        if (vIn > 0f) { a.vx -= ux * vIn; a.vy -= uy * vIn }
                    } else if (bMovable) {
                        b.simX += ux * overlap; b.simY += uy * overlap
                        val vIn = b.vx * ux + b.vy * uy // b 朝向 a 是 -u 方向
                        if (vIn < 0f) { b.vx -= ux * vIn; b.vy -= uy * vIn }
                    }
                }
            }
        }

        // 2) 连线弹簧（双向，只对算法层 Free 节点积分；非 Free 端用展示位置）
        edgeList.forEach { (fromId, toId) ->
            val a = freeNodes.firstOrNull { it.id == fromId } ?: return@forEach
            val b = freeNodes.firstOrNull { it.id == toId } ?: return@forEach
            // R83：树边（母-子）不施弹簧——子节点位置由轨道决定，母节点也不该被孩子拽着跑
            if (b.parentId == a.id || a.parentId == b.id) return@forEach
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

        // 3) R87 开普勒引力 + 潮汐正则化 + 可渗透屏障（只作用算法层 Free 根节点）。
        //    引力 F=GM/d² 向心，与出生自带的 v=√(GM/d) 切向初速度构成真实公转平衡；
        //    正则化把切向速度缓推向 v_circ×(1-forget)——遗忘加深=刹车，引力占优=轨道内旋；
        //    屏障（推力+位置钳制）随遗忘渗透：健康节点硬边界，衰减节点（forget≥门限）可穿入。
        freeNodes.forEach { node ->
            if (node.mode != BlackHoleNodeMode.Free) return@forEach
            // R83：子节点绕母节点公转（advanceChildren），不参与绕洞引力；带钳制同样在轨道层做
            if (isKinematic(node)) return@forEach
            val dx = holeX - node.simX
            val dy = holeY - node.simY
            val d = hypot(dx, dy).takeIf { it > 1f } ?: 1f
            val ux = dx / d
            val uy = dy / d
            // 引力（向心，常力、不随 alpha 冷却）
            val g = physics.holeGravityGM / (d * d)
            fx[node.id] = (fx[node.id] ?: 0f) + ux * g
            fy[node.id] = (fy[node.id] ?: 0f) + uy * g
            // 潮汐正则化：切向速度缓推向 v_circ(d)×(1-forget)（外向径向下 t=(uy,-ux)）
            val tx = uy
            val ty = -ux
            val vtTarget = circularOrbitSpeedAt(d) * (1f - node.forget.coerceIn(0f, 1f))
            val currentT = node.vx * tx + node.vy * ty
            val reg = (vtTarget - currentT) * physics.orbitRegularization
            fx[node.id] = (fx[node.id] ?: 0f) + tx * reg
            fy[node.id] = (fy[node.id] ?: 0f) + ty * reg
            // 4) 屏障随遗忘渗透：仅健康节点外推（推力按 1-forget 软化），遗忘过门后不再外推
            val healthyForBarrier = node.cooling || node.forget < physics.barrierForgetGate
            if (healthyForBarrier && d < exclusionRadius) {
                val barrier = (1f - node.forget.coerceIn(0f, 1f))
                val push = physics.exclusionPush * (exclusionRadius - d) / exclusionRadius * barrier
                fx[node.id] = (fx[node.id] ?: 0f) - ux * push
                fy[node.id] = (fy[node.id] ?: 0f) - uy * push
            }
        }

        // 5) 积分 + 径向/切向分离阻尼 + 速度 clamp + NaN 守卫 + 屏障位置钳制（仅健康节点）
        freeNodes.forEach { node ->
            if (isKinematic(node)) return@forEach // R83：子节点位置只由 advanceChildren 决定
            when (node.mode) {
                BlackHoleNodeMode.Free -> {
                    node.vx += fx[node.id] ?: 0f
                    node.vy += fy[node.id] ?: 0f
                    // R87 阻尼拆分：径向强阻尼（健康 0.90，吸收扰动/圆形化轨道；衰减 0.99，
                    // 让刹车后的轨道渐进内旋），切向零阻尼（保住公转）；
                    // R87b 踢动保护：切向速度超出 1.3×v_circ（逃逸余量 √2 以内）说明是
                    // 碰撞/弹簧踢动而非公转——改强阻尼快速放掉异常能量，防节点逃逸出屏
                    val radialDamp = if (isDecaying(node)) physics.decayRadialDamping else physics.damping
                    val rdx = node.simX - holeX
                    val rdy = node.simY - holeY
                    val rd = hypot(rdx, rdy)
                    if (rd > 1f) {
                        val ux = rdx / rd
                        val uy = rdy / rd
                        val tx = uy
                        val ty = -ux
                        val vr = (node.vx * ux + node.vy * uy) * radialDamp
                        val vtRaw = node.vx * tx + node.vy * ty
                        val tDamp = if (abs(vtRaw) > 1.3f * circularOrbitSpeedAt(rd))
                            physics.damping else physics.tangentialDamping
                        val vt = vtRaw * tDamp
                        node.vx = ux * vr + tx * vt
                        node.vy = uy * vr + ty * vt
                    } else {
                        node.vx *= physics.damping
                        node.vy *= physics.damping
                    }
                    clampVelocity(node)
                    if (guardNaN(node)) return@forEach
                    node.simX += node.vx
                    node.simY += node.vy
                    // 屏障位置钳制（仅健康节点：保护期或遗忘未过门）——衰减节点可穿入遗忘区，
                    // 健康节点永远停留在带外，连线自然不会「接进」黑洞（真机反馈）
                    val healthy = node.cooling || node.forget < physics.barrierForgetGate
                    val cdx = node.simX - holeX
                    val cdy = node.simY - holeY
                    val cd = hypot(cdx, cdy)
                    if (healthy && cd < exclusionRadius && cd > 0.001f) {
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
                // R83：运动学子节点（绕母节点公转）只施力不受力
                if (!isKinematic(node)) {
                    fx[node.id] = (fx[node.id] ?: 0f) + x
                    fy[node.id] = (fy[node.id] ?: 0f) + y
                }
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
     * 吞噬收尾（R87）：absorbed 淡出 -> gone 并清边。触核判定——衰减节点轨道内旋抵核
     * 且遗忘过门（>=absorbForgetGate）即吞 = 真实遗忘（正常吸收的唯一终点）；
     * 健康节点（碰撞/拖拽误闯核心）径向弹出、绝不算吸收（真机反馈）。
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
