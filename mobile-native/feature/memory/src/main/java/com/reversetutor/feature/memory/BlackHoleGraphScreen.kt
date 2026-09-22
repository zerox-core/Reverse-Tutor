package com.reversetutor.feature.memory

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reversetutor.core.model.GraphNodeKind
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

/** 主操作色（双主题共用）。 */
private val GraphAccent = Color(0xFF2F5DDF)
/** 背景平移灵敏度（真机反馈「背景的拖动灵敏度太高」）。 */
private const val CameraPanFactor = 0.72f

/**
 * 黑洞图谱主题令牌：V1 iOS 浅色（默认）+ 深色（历史暗色背景回归入口）。
 * 浅色暖米白纸感底 + 彩色点缀点 + 粉彩马卡龙；深色银河星景（三层视差星点 + 银河带 + 星云辉光）+ 高饱和节点。
 */
class BlackHoleGraphPalette(
    val background: Color,
    val edge: Color,
    val label: Color,
    val dust: Color,
    val dustAlphaBoost: Float,
    val topBarText: Color,
    val topBarSub: Color,
    val panel: Color,
    val panelBorder: Color,
    val panelText: Color,
    val panelSub: Color,
    val chipActiveBg: Color,
    val chipActiveText: Color,
    val chipIdleBg: Color,
    val chipIdleText: Color,
    val nodeGlow: Color,
    val nodeGlowAlpha: Float,
    val nodeBorder: Color,
    val isDark: Boolean,
    val nodeColors: Map<GraphNodeKind, Color>,
    /** 背景点缀点配色（浅色用）：刻意与节点图谱色拉开色相/明度，避免误认成节点（用户 2026-09-19 拍板）。 */
    val dustColors: List<Color>
) {
    fun nodeColor(kind: GraphNodeKind): Color =
        nodeColors[kind] ?: nodeColors.getValue(GraphNodeKind.Other)
}

object BlackHoleGraphThemes {
    /** V1 iOS 浅色风：暖米白纸感底 + 彩色点缀（真机反馈「不要单调白」）；节点饱和度加深版。 */
    val Light = BlackHoleGraphPalette(
        background = Color(0xFFF7F1E4),
        edge = Color(0xFF8E9AB8),
        label = Color(0xFF46506B),
        dust = Color(0xFF93A2C4),
        dustAlphaBoost = 1f,
        topBarText = Color(0xFF1C2433),
        topBarSub = Color(0xFF6D778C),
        panel = Color(0xFFFFFFFF),
        panelBorder = Color(0xFFE9E1D0),
        panelText = Color(0xFF121722),
        panelSub = Color(0xFF6D778C),
        chipActiveBg = Color(0xFF1C2433),
        chipActiveText = Color(0xFFFFFFFF),
        chipIdleBg = Color(0x66FFFFFF),
        chipIdleText = Color(0xFF46506B),
        nodeGlow = Color.White,
        nodeGlowAlpha = 0.72f, // R95：浅色下同色晕看不见——强度 0.55→0.72 + 末层全释放绘制
        nodeBorder = Color.White,
        isDark = false,
        nodeColors = mapOf(
            GraphNodeKind.Concept to Color(0xFF3E96E0),
            GraphNodeKind.Requirement to Color(0xFFF2607A),
            GraphNodeKind.Source to Color(0xFF34C77B),
            GraphNodeKind.Session to Color(0xFFB678E8),
            GraphNodeKind.Person to Color(0xFFEE9E4A),
            GraphNodeKind.Other to Color(0xFF1FB6C9)
        ),
        // 点缀点：大地暖调四色（琥珀金/赭红/橄榄/可可）——与六色节点色相明度都拉开，丰富画面但不抢戏
        dustColors = listOf(
            Color(0xFFC99227),
            Color(0xFFB5542F),
            Color(0xFF7A8C3F),
            Color(0xFF8C6239)
        )
    )

    /** 深色回归版：夜空底 + 银河星景（视差星点/银河带/星云）+ 高饱和节点（R94：黑洞已删除）。 */
    val Dark = BlackHoleGraphPalette(
        background = Color(0xFF0B1026),
        edge = Color(0xFF8FA3C8),
        label = Color(0xFFE8ECF4),
        dust = Color(0xFFAFC3E8),
        dustAlphaBoost = 1.5f,
        topBarText = Color(0xFFE8ECF4),
        topBarSub = Color(0xFF8FA3C8),
        panel = Color(0xE6141B33),
        panelBorder = Color(0x33202A4A),
        panelText = Color(0xFFE8ECF4),
        panelSub = Color(0xFF9AA7C7),
        chipActiveBg = Color(0xFFE8ECF4),
        chipActiveText = Color(0xFF0B1026),
        chipIdleBg = Color(0x33141B33),
        chipIdleText = Color(0xFF9AA7C7),
        nodeGlow = Color(0xFF3A466E),
        nodeGlowAlpha = 0.62f, // R95：末层全释放绘制后任何元素不再遮挡光晕，强度微调 0.55→0.62
        nodeBorder = Color(0xFF1A2242),
        isDark = true,
        nodeColors = mapOf(
            GraphNodeKind.Concept to Color(0xFF5EC8D8),
            GraphNodeKind.Requirement to Color(0xFFE8B45A),
            GraphNodeKind.Source to Color(0xFF9B8CE8),
            GraphNodeKind.Session to Color(0xFF6FA8F0),
            GraphNodeKind.Person to Color(0xFF7BC98A),
            GraphNodeKind.Other to Color(0xFFD87B7B)
        ),
        dustColors = listOf(
            Color(0xFFC99227),
            Color(0xFFB5542F),
            Color(0xFF7A8C3F),
            Color(0xFF8C6239)
        )
    )
}

/** 彩色点缀点（浅色主题，世界空间，确定性种子，正弦漂移 + 闪烁）。整画布背景散布，取大地暖调四色（与节点图谱色拉开——用户拍板「彩点颜色不要和图谱相似、画面要丰富」）。 */
private data class DustDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val driftPhase: Float,
    val driftAmp: Float,
    val color: Color
)

private fun buildDust(engine: BlackHoleGraphEngine, colors: List<Color>): List<DustDot> {
    val rng = Random(20260918)
    val extent = engine.clusterExtent().coerceAtLeast(400f)
    // R94：黑洞删除——散布锚点由「净空带」改为等效常量（原 168），仍以布局中心为原点
    val field = 170f + extent * 2.2f
    // R95 用户拍板「彩点密度增加、再大一点、再多一点」：200→320 颗，半径整体 ×~1.5
    val count = 320
    val dotColors = colors.ifEmpty { listOf(Color(0xFF93A2C4)) }
    return List(count) {
        val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
        val dist = 85f + rng.nextFloat() * field
        // 大小分档：85% 小点 + 15% 大颗粒点缀，画面更有层次
        val big = rng.nextFloat() < 0.15f
        DustDot(
            x = engine.holeX + kotlin.math.cos(angle) * dist,
            y = engine.holeY + kotlin.math.sin(angle) * dist,
            radius = if (big) 3.6f + rng.nextFloat() * 1.8f else 1.6f + rng.nextFloat() * 2.0f,
            alpha = if (big) 0.30f + rng.nextFloat() * 0.16f else 0.34f + rng.nextFloat() * 0.28f,
            driftPhase = rng.nextFloat() * 2f * Math.PI.toFloat(),
            driftAmp = 3f + rng.nextFloat() * 7f,
            color = dotColors[rng.nextInt(dotColors.size)]
        )
    }
}

/** 银河星点（深色主题）：三层视差 + 正弦闪烁 + 少量暖色星（真机反馈「银河里看星球」）。 */
private data class StarDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val parallax: Float,
    val twinklePhase: Float,
    val twinkleSpeed: Float,
    val warm: Boolean
)

private fun buildStarfield(engine: BlackHoleGraphEngine): List<StarDot> {
    val rng = Random(20260919)
    val extent = engine.clusterExtent().coerceAtLeast(400f)
    // R94：黑洞删除——散布锚点由「净空带」改为等效常量（原 168），仍以布局中心为原点
    val field = 170f + extent * 2.2f
    val stars = ArrayList<StarDot>(200)
    // 三层：远（多而小暗、视差最慢）/ 中 / 近（少而大亮、随前景）
    val layers = listOf(
        Triple(96, 0.35f, 0.6f),
        Triple(64, 0.65f, 1.0f),
        Triple(30, 1.0f, 1.5f)
    )
    layers.forEach { (count, parallax, baseR) ->
        repeat(count) {
            val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
            val dist = 100f + rng.nextFloat() * field
            stars += StarDot(
                x = engine.holeX + kotlin.math.cos(angle) * dist,
                y = engine.holeY + kotlin.math.sin(angle) * dist,
                radius = baseR + rng.nextFloat() * baseR,
                alpha = (0.22f + rng.nextFloat() * 0.5f) * (0.5f + parallax * 0.5f),
                parallax = parallax,
                twinklePhase = rng.nextFloat() * 2f * Math.PI.toFloat(),
                twinkleSpeed = 0.5f + rng.nextFloat() * 1.1f,
                warm = rng.nextFloat() < 0.18f
            )
        }
    }
    return stars
}

/** 星云辉光（深色银河）：世界坐标定位 + 中景视差，大面积低透明径向渐变。 */
private data class NebulaGlow(
    val x: Float,
    val y: Float,
    val radius: Float,
    val color: Color,
    val parallax: Float
)

private fun buildNebulae(engine: BlackHoleGraphEngine): List<NebulaGlow> {
    val extent = engine.clusterExtent().coerceAtLeast(400f)
    return listOf(
        NebulaGlow(
            x = engine.holeX - extent * 0.9f,
            y = engine.holeY - extent * 0.55f,
            radius = extent * 0.95f,
            color = Color(0x145A6BF0),
            parallax = 0.5f
        ),
        NebulaGlow(
            x = engine.holeX + extent * 1.05f,
            y = engine.holeY + extent * 0.7f,
            radius = extent * 0.8f,
            color = Color(0x103FC8C0),
            parallax = 0.5f
        )
    )
}

/** R100 浅色星云辉光：fx/fy/fradius 均为屏幕宽/高比例（屏幕空间定位，跟随窗口），phase 为漂移相位。 */
private data class LightNebula(
    val fx: Float,
    val fy: Float,
    val fradius: Float,
    val color: Color,
    val phase: Float
)

/** 相机：世界坐标中心 + 缩放；平移降敏（x0.72）、松手滑移惯性衰减 0.93（Obsidian 式微惯性）、缩放范围 0.45~3.2。 */
private class BlackHoleCamera {
    var centerX = 0f
    var centerY = 0f
    var scale = 1f
    var vx = 0f
    var vy = 0f
    var initialized = false

    /** R91 自动取景：布局收敛前每帧向目标缓动（星团收缩/漂移时画面跟着调），收敛即锁定；用户手势一旦操作立即接管。 */
    var autoFit = true

    fun tick() {
        centerX += vx
        centerY += vy
        vx *= 0.93f
        vy *= 0.93f
        if (hypot(vx, vy) < 0.05f) {
            vx = 0f
            vy = 0f
        }
    }

    /**
     * 舒适入画系数（R91：0.44→0.36，真机反馈「太密集、要松弛感」——星团占短边 72% 而非 88%，留足呼吸边距）。
     * 0.44 是 R89 定稿值，本系数变更已同步开发文档修订记录。
     */
    private fun fitScale(extent: Float, viewportW: Float, viewportH: Float): Float =
        (min(viewportW, viewportH) * 0.36f / extent).coerceIn(0.45f, 3.2f)

    /** 初始化：质心居中 + 包围半径舒适入画（fit-zoom）。 */
    fun initializeFit(engine: BlackHoleGraphEngine, viewportW: Float, viewportH: Float) {
        if (viewportW <= 0f || viewportH <= 0f) return
        val centroid = engine.centroidOfCluster()
        val extent = engine.clusterExtent()
        centerX = centroid.first
        centerY = centroid.second
        scale = fitScale(extent, viewportW, viewportH)
        vx = 0f
        vy = 0f
        initialized = true
        autoFit = true
    }

    /**
     * R91 自动跟随取景：修复真机「整体过大、太密」根因——首帧的 clusterExtent() 是未收敛瞬态
     * （真机首帧比模拟器晚，星团已被弹簧收缩到更小，fit-zoom 被算大 ~2 倍，实测手机 2.93× vs
     * 模拟器 1.36×，且初始化后永不修正）。收敛前每帧向「质心 + 收敛 extent」目标缓动，
     * 收敛落定一次（snap=true）即停；用户平移/缩放/拖动即 autoFit=false 交还手势。
     */
    fun trackFit(engine: BlackHoleGraphEngine, viewportW: Float, viewportH: Float, snap: Boolean) {
        if (viewportW <= 0f || viewportH <= 0f) return
        val centroid = engine.centroidOfCluster()
        val target = fitScale(engine.clusterExtent(), viewportW, viewportH)
        if (snap) {
            centerX = centroid.first
            centerY = centroid.second
            scale = target
        } else {
            centerX += (centroid.first - centerX) * 0.12f
            centerY += (centroid.second - centerY) * 0.12f
            scale += (target - scale) * 0.08f
        }
    }

    fun zoomBy(factor: Float, focusWorldX: Float, focusWorldY: Float) {
        val next = (scale * factor).coerceIn(0.45f, 3.2f)
        if (next == scale) return
        // 缩放朝向焦点：保持焦点世界坐标在屏上位置不变
        centerX = focusWorldX - (focusWorldX - centerX) * (scale / next)
        centerY = focusWorldY - (focusWorldY - centerY) * (scale / next)
        scale = next
    }
}

@Composable
fun BlackHoleGraphScreen(
    state: KnowledgeGraphUiState,
    onSelectedNodeChange: (String?) -> Unit,
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onMore: (() -> Unit)? = null,
    onOpenChatEvidence: ((GraphLayoutNode) -> Unit)? = null,
    onOpenSourceEvidence: ((GraphLayoutNode) -> Unit)? = null,
    onRetry: () -> Unit = {},
    onGraphInteractionChanged: (Boolean) -> Unit = {},
    onCanvasModeChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var darkTheme by remember { mutableStateOf(false) }
    val palette = if (darkTheme) BlackHoleGraphThemes.Dark else BlackHoleGraphThemes.Light
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag("blackhole-graph-screen")
    ) {
        when (state.status) {
            GraphRenderStatus.Ready, GraphRenderStatus.Large -> BlackHoleGraphReadyContent(
                state = state,
                palette = palette,
                darkTheme = darkTheme,
                onToggleTheme = { darkTheme = !darkTheme },
                onSelectedNodeChange = onSelectedNodeChange,
                onOpenChatEvidence = onOpenChatEvidence,
                onOpenSourceEvidence = onOpenSourceEvidence,
                onGraphInteractionChanged = onGraphInteractionChanged,
                onCanvasModeChange = onCanvasModeChange
            )
            GraphRenderStatus.Loading -> BlackHoleGraphMessage("正在加载图谱…", palette = palette, showSpinner = true)
            GraphRenderStatus.Empty -> BlackHoleGraphMessage("这里还没有图谱节点", palette = palette, actionLabel = null, onAction = null)
            GraphRenderStatus.Error, GraphRenderStatus.Invalid -> BlackHoleGraphMessage(
                text = state.summary.ifBlank { "图谱加载失败，请重试。" },
                palette = palette,
                actionLabel = "重试",
                onAction = onRetry
            )
        }
        BlackHoleGraphTopBar(
            title = title,
            subtitle = subtitle,
            palette = palette,
            onBack = onBack,
            onMore = onMore
        )
    }
}

@Composable
private fun BlackHoleGraphReadyContent(
    state: KnowledgeGraphUiState,
    palette: BlackHoleGraphPalette,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onSelectedNodeChange: (String?) -> Unit,
    onOpenChatEvidence: ((GraphLayoutNode) -> Unit)?,
    onOpenSourceEvidence: ((GraphLayoutNode) -> Unit)?,
    onGraphInteractionChanged: (Boolean) -> Unit,
    onCanvasModeChange: (Boolean) -> Unit
) {
    val snapshot = state.renderSnapshot(true)
    val layoutNodes = state.allNodes
    val engine = remember(snapshot) {
        // R82 用户拍板：暂不做真实遗忘——保护期不过期，全部节点保持刚创建状态正常公转（待后端遗忘曲线）。
        BlackHoleGraphEngine(physics = BlackHolePhysics(protectionSeconds = Float.MAX_VALUE)).apply {
            populate(
                graphNodes = layoutNodes.map { Triple(it.id, it.label, it.kind) },
                edges = snapshot.edges.map { it.fromNodeId to it.toNodeId }
            )
            // R96 演示种子（用户要看遗忘闪烁实况）：把「参数方程」直接置为遗忘中途
            // （遗忘 0.5 > 闪烁门 0.35，入场即呼吸闪烁），1× 倍率约 2 分钟走满淡出消失；
            // 点按/拖拽闪烁节点 = 抢救回归（保护期无限，救回后不再遗忘）。
            // 空安全查找：真实 App 数据没有该 id 时自动跳过，不影响生产页面。
            nodes.firstOrNull { it.id == "c9" }?.let { demo ->
                demo.cooling = false
                demo.forget = 0.5f
            }
        }
    }
    val camera = remember(engine) { BlackHoleCamera() }
    val dust = remember(engine, palette) { buildDust(engine, palette.dustColors) }
    val starfield = remember(engine) { buildStarfield(engine) }
    val nebulae = remember(engine) { buildNebulae(engine) }
    // R100 浅色版银河（用户决策卡拍板：深色银河结构翻成暖白调）：
    // 三团暖色星云辉光，屏幕宽/高比例定位（跟随窗口、永不消失）+ 缓慢漂移
    val lightNebulae = remember {
        listOf(
            LightNebula(0.24f, 0.30f, 0.62f, Color(0x11F0A860), 0.0f),
            LightNebula(0.80f, 0.68f, 0.55f, Color(0x0FE8907A), 2.1f),
            LightNebula(0.58f, 0.12f, 0.45f, Color(0x0CB8C078), 4.2f)
        )
    }
    var frame by remember { mutableLongStateOf(0L) }
    var timeScaleIndex by remember { mutableIntStateOf(0) }
    val timeScales = remember { listOf(1f, 0.3f, 3f) }
    val timeScaleLabels = remember { listOf("1×", "0.3×", "3×") }
    var viewportWidth by remember { mutableStateOf(0f) }
    var viewportHeight by remember { mutableStateOf(0f) }
    var interacting by remember { mutableStateOf(false) }
    // 抢救反馈 toast（D7 点击抢救复习；非空即显示，2.4s 后自动消失）
    var rescueToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(rescueToast) {
        if (rescueToast != null) {
            delay(2400)
            rescueToast = null
        }
    }

    LaunchedEffect(timeScaleIndex) {
        engine.timeScale = timeScales[timeScaleIndex]
    }

    LaunchedEffect(engine) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceIn(0.0005f, 0.1f)
                last = now
                engine.tick(dt)
                camera.tick()
                frame = now
            }
        }
    }

    fun screenToWorld(p: Offset): Offset {
        if (viewportWidth <= 0f) return p
        return Offset(
            camera.centerX + (p.x - viewportWidth / 2f) / camera.scale,
            camera.centerY + (p.y - viewportHeight / 2f) / camera.scale
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (!camera.initialized && viewportWidth > 0f) {
                        camera.initializeFit(engine, viewportWidth, viewportHeight)
                    }
                    // 触点落在画布即申请手势所有权（克隆旧屏 onRequestInteraction 语义）
                    onCanvasModeChange(true)
                    val downWorld = screenToWorld(down.position)
                    // R81/R87：所有可见节点（含衰减期节点）都可命中——拖动/点按衰减节点按「复习」抢救处理，
                    // 修复「节点没办法拖动」（衰减节点 hitTest 豁免时手势全部落到平移分支）
                    val hit = engine.hitTest(downWorld.x, downWorld.y)
                    if (hit != null) {
                        // 节点拖动路径（含点按判定）
                        engine.startDrag(hit.id)
                        interacting = true
                        onGraphInteractionChanged(true)
                        var totalMove = 0f
                        var dragging = true
                        while (dragging) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                dragging = false
                            } else if (change.positionChanged()) {
                                totalMove += change.positionChange().getDistance()
                                if (totalMove > viewConfiguration.touchSlop) camera.autoFit = false
                                val world = screenToWorld(change.position)
                                engine.dragTo(world.x, world.y)
                                change.consume()
                            }
                        }
                        // 松手即判定：拖的是濒死节点 -> 抢救复活（点按松手同样算复习；R81 统一原抢救分支与拖动）
                        val rescuedId = engine.endDrag()
                        interacting = false
                        onGraphInteractionChanged(false)
                        if (rescuedId != null) {
                            val label = layoutNodes.firstOrNull { it.id == rescuedId }?.label ?: rescuedId
                            rescueToast = "已抢救回归「$label」：复习完成，遗忘清零"
                        } else if (totalMove < viewConfiguration.touchSlop) {
                            onSelectedNodeChange(if (state.selectedNodeId == hit.id) null else hit.id)
                        }
                    } else {
                        // 空白：平移 / 双指缩放；位移极小视为点按取消选中
                        // R68 修复「双指缩放不在原位、会乱跳」：
                        // ① 手指数变化（1↔2）时重置质心/指距基线，跳过本帧位移——否则抬一指瞬间质心跳到剩指位置，画面猛跳；
                        // ② 先平移（旧 scale）再缩放，缩放锚定平移后的当前质心——旧实现先按旧相机锚定新质心缩放、再叠加质心位移平移，位移被重复计算导致漂移。
                        var prevCentroid = down.position
                        var prevSpan = 0f
                        var prevCount = 1
                        var totalMove = 0f
                        var active = true
                        camera.vx = 0f
                        camera.vy = 0f
                        while (active) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) {
                                active = false
                            } else {
                                val centroid = Offset(
                                    pressed.map { it.position.x }.average().toFloat(),
                                    pressed.map { it.position.y }.average().toFloat()
                                )
                                val span = if (pressed.size >= 2) {
                                    var maxD = 0f
                                    for (i in pressed.indices) for (j in i + 1 until pressed.size) {
                                        maxD = max(maxD, (pressed[i].position - pressed[j].position).getDistance())
                                    }
                                    maxD
                                } else 0f
                                if (pressed.size != prevCount) {
                                    // 手指数变化：只重置基线，不做任何平移/缩放
                                    prevCentroid = centroid
                                    prevSpan = span
                                    prevCount = pressed.size
                                } else {
                                    val delta = centroid - prevCentroid
                                    // 先平移（用缩放前的 scale）
                                    camera.centerX -= delta.x / camera.scale * CameraPanFactor
                                    camera.centerY -= delta.y / camera.scale * CameraPanFactor
                                    camera.vx = -delta.x / camera.scale * CameraPanFactor
                                    camera.vy = -delta.y / camera.scale * CameraPanFactor
                                    // 再缩放：锚定平移后质心正下方的世界点，缩放过程该点不动
                                    if (pressed.size >= 2 && prevSpan > 0f && span > 0f) {
                                        val focusWorld = screenToWorld(centroid)
                                        camera.zoomBy(span / prevSpan, focusWorld.x, focusWorld.y)
                                    }
                                    totalMove += delta.getDistance()
                                    // R91：用户实际平移/缩放即接管相机，停掉自动取景
                                    if (totalMove > viewConfiguration.touchSlop || pressed.size >= 2) {
                                        camera.autoFit = false
                                    }
                                    prevCentroid = centroid
                                    prevSpan = span
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                        if (totalMove < viewConfiguration.touchSlop) {
                            onSelectedNodeChange(null)
                        }
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("blackhole-graph-canvas")
        ) {
            viewportWidth = size.width
            viewportHeight = size.height
            @Suppress("UNUSED_EXPRESSION") frame // 帧驱动重组

            if (!camera.initialized && size.width > 0f) {
                camera.initializeFit(engine, size.width, size.height)
            } else if (camera.autoFit) {
                // R91：收敛前自动跟随取景（首帧 extent 未收敛会把 scale 算大且永不修正），收敛即落定锁定
                camera.trackFit(engine, size.width, size.height, engine.isSettled)
                if (engine.isSettled) camera.autoFit = false
            }

            fun worldToScreen(wx: Float, wy: Float): Offset = Offset(
                (wx - camera.centerX) * camera.scale + size.width / 2f,
                (wy - camera.centerY) * camera.scale + size.height / 2f
            )

            val seconds = frame / 1_000_000_000f

            // 视差映射：p=1 与前景一致；p<1 相机平移时移动更慢（远景），初始与前景对齐（深浅背景共用）
            fun bgToScreen(wx: Float, wy: Float, p: Float): Offset = Offset(
                size.width / 2f + ((wx - engine.holeX) - (camera.centerX - engine.holeX) * p) * camera.scale,
                size.height / 2f + ((wy - engine.holeY) - (camera.centerY - engine.holeY) * p) * camera.scale
            )

            if (palette.isDark) {
                // 银河背景（深色）：银河带 -> 星云辉光 -> 三层视差星点，营造「身处银河看星球」

                // 银河带：斜向柔光带（随相机 0.2 倍慢漂）
                withTransform({
                    rotate(degrees = -22f, pivot = Offset(size.width / 2f, size.height / 2f))
                }) {
                    val bandH = size.height * 0.62f
                    val bandDrift = -(camera.centerY - engine.holeY) * 0.2f * camera.scale
                    val bandTop = size.height * 0.5f - bandH / 2f - size.height * 0.08f + bandDrift
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.34f to Color(0x089AB8E8),
                            0.5f to Color(0x14B8D0F8),
                            0.66f to Color(0x089AB8E8),
                            1f to Color.Transparent,
                            startY = bandTop,
                            endY = bandTop + bandH
                        ),
                        topLeft = Offset(-size.width * 0.5f, bandTop),
                        size = Size(size.width * 2f, bandH)
                    )
                }

                // 星云辉光（中景视差）
                nebulae.forEach { neb ->
                    val c = bgToScreen(neb.x, neb.y, neb.parallax)
                    val r = neb.radius * camera.scale
                    if (r > 1f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(neb.color, Color.Transparent),
                                center = c,
                                radius = r
                            ),
                            radius = r,
                            center = c
                        )
                    }
                }

                // 三层视差星点：远小暗、近大亮，正弦闪烁
                starfield.forEach { star ->
                    val c = bgToScreen(star.x, star.y, star.parallax)
                    if (c.x < -8f || c.x > size.width + 8f || c.y < -8f || c.y > size.height + 8f) {
                        return@forEach
                    }
                    val twinkle = 0.62f + 0.38f * sin(seconds * star.twinkleSpeed + star.twinklePhase)
                    drawCircle(
                        color = if (star.warm) Color(0xFFF8E0B8) else Color(0xFFEAF2FF),
                        radius = (star.radius * camera.scale).coerceAtLeast(0.5f),
                        center = c,
                        alpha = (star.alpha * twinkle).coerceIn(0f, 1f)
                    )
                }
            } else {
                // R100 浅色版银河（用户决策卡拍板）：深色银河结构翻成暖白调——
                // 斜向晨光带 -> 暖色星云辉光 -> 彩点（= 浅色星点）-> 边角轻压；
                // 带与星云均屏幕空间定位，跟随窗口铺满，平移到哪都在，且带缓慢自漂动态。

                // 晨光银河带：与深色银河带同构（-22°、相机 0.2 倍慢漂）+ 自身缓慢起伏呼吸
                withTransform({
                    rotate(degrees = -22f, pivot = Offset(size.width / 2f, size.height / 2f))
                }) {
                    val bandH = size.height * 0.62f
                    val bandDrift = -(camera.centerY - engine.holeY) * 0.2f * camera.scale +
                        sin(seconds * 0.05f) * size.height * 0.03f
                    val bandTop = size.height * 0.5f - bandH / 2f - size.height * 0.08f + bandDrift
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.34f to Color(0x12FFDFA8),
                            0.5f to Color(0x1CFFEDC4),
                            0.66f to Color(0x12FFDFA8),
                            1f to Color.Transparent,
                            startY = bandTop,
                            endY = bandTop + bandH
                        ),
                        topLeft = Offset(-size.width * 0.5f, bandTop),
                        size = Size(size.width * 2f, bandH)
                    )
                }

                // 暖色星云辉光：屏幕比例定位（跟随窗口）+ 缓慢漂移 + 相机 0.3 倍轻推制造纵深
                lightNebulae.forEach { neb ->
                    val drift = sin(seconds * 0.03f + neb.phase) * size.width * 0.02f
                    val c = Offset(
                        size.width * neb.fx + drift - (camera.centerX - engine.holeX) * 0.3f * camera.scale,
                        size.height * neb.fy + drift * 0.6f - (camera.centerY - engine.holeY) * 0.3f * camera.scale
                    )
                    val r = size.height * neb.fradius
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(neb.color, Color.Transparent),
                            center = c,
                            radius = r
                        ),
                        radius = r,
                        center = c
                    )
                }

                // 彩色点缀点：漂移 + 闪烁（浅色主题，取大地暖调四色）
                dust.forEach { dot ->
                    val dx = sin(seconds * 0.4f + dot.driftPhase) * dot.driftAmp
                    val dy = kotlin.math.cos(seconds * 0.3f + dot.driftPhase * 1.3f) * dot.driftAmp
                    val c = worldToScreen(dot.x + dx, dot.y + dy)
                    val r = dot.radius * camera.scale
                    if (r > 0.4f) {
                        val twinkle = 0.7f + 0.3f * sin(seconds * 0.8f + dot.driftPhase * 2f)
                        drawCircle(
                            color = dot.color,
                            radius = r,
                            center = c,
                            alpha = (dot.alpha * twinkle * palette.dustAlphaBoost).coerceIn(0f, 1f)
                        )
                    }
                }

                // 边角轻压（屏幕空间径向渐隐）：视线收拢到中央，像旧纸微微泛暗的边
                drawRect(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color(0x14000000),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = kotlin.math.hypot(size.width, size.height) / 2f
                    ),
                    topLeft = Offset.Zero,
                    size = size
                )
            }

            // R94：黑洞已删除——吸积盘/湍流条纹/黑核/光子环/螺旋内流/透镜弧光整体退役，
            // 画布只保留背景（银河星景/彩点）+ 连线 + 节点。
            // 选中态：邻接保持高亮，其余压暗（聚焦模式）
            // R92 用户拍板「拖动中也要明显看到关联线」：聚焦态 = 选中节点 ?: 正在拖拽的节点——拖动同样触发聚焦高亮
            val selectedId = state.selectedNodeId ?: engine.draggingNodeId
            val neighborIds = if (selectedId == null) {
                emptySet()
            } else {
                val set = HashSet<String>()
                engine.renderEdges().forEach { edge ->
                    if (edge.fromId == selectedId) set.add(edge.toId)
                    if (edge.toId == selectedId) set.add(edge.fromId)
                }
                set.add(selectedId)
                set
            }

            // 连线：R92 用户拍板「降低亮度、只有点击/拖动才明显看到」——全局档 opacity 0.25→0.10（默认几乎隐入背景），
            // 聚焦节点的关联边 ×4 亮度 + 1.6× 粗细（拖动/点选时明显浮现）；缩放渐进增强与 1.2px 粗细下限保留（V24/V25）
            val edgeGate = when {
                camera.scale < 0.7f -> 1f
                else -> (1f + (camera.scale - 0.7f) / 0.8f * 0.8f).coerceAtMost(1.8f)
            }
            val edgeBaseWidthPx = max(1.2f, 1.dp.toPx())
            engine.renderEdges().forEach { edge ->
                val incident = selectedId != null &&
                    (edge.fromId == selectedId || edge.toId == selectedId)
                val dim = if (selectedId != null && !incident) 0.22f else 1f
                val edgeStroke = edgeBaseWidthPx * (1f + (edgeGate - 1f) * 0.5f) *
                    (if (incident) 1.6f else 1f)
                val edgeAlpha = (0.10f * edgeGate * edge.opacityFactor * dim *
                    (if (incident) 4f else 1f)).coerceIn(0f, 1f)
                // R85 用户拍板：关系线优先保持直线，绝不为「成圆」而弯曲；
                // R94：黑洞删除——遗忘区分段渐隐遮断随之退役，恢复整段直线绘制。
                drawLine(
                    color = palette.edge,
                    start = worldToScreen(edge.fromX, edge.fromY),
                    end = worldToScreen(edge.toX, edge.toY),
                    strokeWidth = edgeStroke,
                    alpha = edgeAlpha
                )
            }

            // 节点：填充 -> 细边 -> 内环设计层 -> 标签（光晕 R95 改末层统一释放）
            val labelColor = palette.label
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    225,
                    (labelColor.red * 255).toInt(),
                    (labelColor.green * 255).toInt(),
                    (labelColor.blue * 255).toInt()
                )
                // R90：11sp→9sp（整体放大节点×2 的方案被否——卫星会被 ×2 后的净空带吞掉、环距不够必抖；字体单独缩小零风险）
                // R92 用户拍板「字体再进行缩小」：9sp→8sp（与节点半径 +35% 配套，药丸底衬随 measureText 自动缩小）
                textSize = 8.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val borderPx = max(1.5f, 1.8.dp.toPx())
            // 标签候选：循环内只收集，循环后做防重叠剔除再统一绘制
            // （用户反馈「字体全都重复在一起」——选中节点最优先，其余按节点大小，矩形相交则跳过；
            //  R88 再加缩放分级预算 + 半透底衬：默认视角只留最重要的少数标签，放大才显示更多）
            // R91：候选携带节点圆心（nodeY）而非固定下方锚点——绘制时按与节点圆的避让关系选上/下侧
            class LabelCandidate(val text: String, val x: Float, val nodeY: Float, val selected: Boolean, val neighbor: Boolean, val r: Float)
            val labelCandidates = mutableListOf<LabelCandidate>()
            // R91 错位修复：标签药丸与「节点圆」的避让检测需要全部节点圆的屏幕位置（此前只查标签间重叠，
            // 纵向堆叠时下方药丸直接盖住下一个节点的上半圆，看起来像标签接错了节点）
            class ScreenCircle(val x: Float, val y: Float, val r: Float)
            val nodeCircles = mutableListOf<ScreenCircle>()
            // R95 光晕全释放：循环内只收集光晕参数，标签画完后统一最末层绘制，
            // 任何元素（标签药丸/连线/尘埃）都不再截断光晕（用户反馈深色下光晕被字体遮挡、浅色下看不见）
            class GlowSpec(val x: Float, val y: Float, val r: Float, val color: Color, val alpha: Float)
            val glowSpecs = mutableListOf<GlowSpec>()
            engine.renderNodes().forEach { node ->
                val center = worldToScreen(node.x, node.y)
                val r = node.radius * camera.scale
                val dim = if (selectedId != null && node.id !in neighborIds) 0.22f else 1f
                // 衰减节点遗忘过门后做闪烁呼吸 = 「即将遗忘」的警告（R66 设计；
                // R94 无黑洞版：不再按遗忘区位置判定，遗忘 >= blinkForgetStart 即闪烁）
                val breathing = node.forget >= engine.physics.blinkForgetStart
                // R97 用户反馈「呼吸太局促」：频率 5.5→2.0（周期 ~1.1s→~3.1s），下限 0.45→0.5 更平缓
                val breathAlpha = if (breathing) {
                    0.5f + 0.5f * (0.5f + 0.5f * kotlin.math.sin(seconds * 2.0f))
                } else 1f
                val alpha = (node.opacity * dim * breathAlpha).coerceIn(0f, 1f)
                if (r <= 0.5f) return@forEach
                nodeCircles.add(ScreenCircle(center.x, center.y, r))
                val base = palette.nodeColor(node.kind)
                // 选中/抢救提示环用同色相压暗描边（用户 2026-09-19 拍板：不要黑色线框），与米白底和同色光晕都有区分度
                val ringColor = Color(base.red * 0.72f, base.green * 0.72f, base.blue * 0.72f)
                // 柔光晕：R95 改为收集制——此处不再即画，标签绘制完成后统一末层释放（见下方 glowSpecs 循环）
                glowSpecs.add(GlowSpec(center.x, center.y, r, base, alpha))
                drawCircle(color = base, radius = r, center = center, alpha = alpha)
                // 细边
                drawCircle(
                    color = palette.nodeBorder.copy(alpha = 0.95f * alpha),
                    radius = r,
                    center = center,
                    alpha = alpha,
                    style = Stroke(width = borderPx)
                )
                // R95 节点设计层（用户反馈「光秃秃的有点丑」）：内环细描边，
                // 白色半透与彩色填充成「徽章」层次，简单一层不抢戏
                drawCircle(
                    color = Color.White.copy(alpha = 0.45f * alpha),
                    radius = r * 0.68f,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
                if (state.selectedNodeId == node.id) {
                    drawCircle(
                        color = ringColor,
                        radius = r + 3.dp.toPx(),
                        center = center,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                if (breathing) {
                    // 可抢救提示环：闪烁呼吸的濒死节点外圈描边，提示「点我抢救」（D7 断链离散设计）
                    drawCircle(
                        color = ringColor,
                        radius = r + 5.dp.toPx(),
                        center = center,
                        alpha = breathAlpha,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
                // 标签：遗忘未过半且缩放达到标签阈值且入场完成（引擎已 gating entrance）——先收集，统一剔除后绘制
                if (node.showLabel && camera.scale >= 0.8f && alpha > 0.05f) {
                    labelCandidates.add(
                        LabelCandidate(
                            node.label.take(12),
                            center.x,
                            center.y,
                            state.selectedNodeId == node.id,
                            node.id != state.selectedNodeId && node.id in neighborIds,
                            r
                        )
                    )
                }
            }
            // 标签绘制：R88 缩放分级预算（默认视角只留 6 个最重要的，1.4× 以上才全量），
            // 选中 > 选中节点的邻居 > 节点半径；矩形相交跳过；半透底衬保证文字不被连线/尘埃/光晕干扰
            val labelBudget = when {
                camera.scale < 1.0f -> 6
                camera.scale < 1.4f -> 12
                else -> Int.MAX_VALUE
            }
            val drawnLabelRects = mutableListOf<android.graphics.RectF>()
            var labelsDrawn = 0
            labelCandidates
                .sortedWith(
                    compareByDescending<LabelCandidate> { it.selected }
                        .thenByDescending { it.neighbor }
                        .thenByDescending { it.r }
                )
                .forEach { c ->
                    if (labelsDrawn >= labelBudget) return@forEach
                    val w = labelPaint.measureText(c.text)
                    val padX = 5.dp.toPx()
                    val gapY = 13.dp.toPx()
                    // R91 错位修复：默认画在节点下方；若下方药丸与其它节点圆相撞则翻到上方，
                    // 两侧都撞则保留下方（交给下方既有的标签间矩形剔除兜底）。
                    // 此前固定画在下方，节点纵向贴近时药丸盖住下一个节点的上半圆——正是真机反馈的「错位」。
                    fun rectAt(anchorY: Float) = android.graphics.RectF(
                        c.x - w / 2f - padX,
                        anchorY - 12.dp.toPx(),
                        c.x + w / 2f + padX,
                        anchorY + 3.dp.toPx()
                    )
                    fun blocked(rect: android.graphics.RectF): Boolean {
                        nodeCircles.forEach { sc ->
                            if (sc.x == c.x && sc.y == c.nodeY) return@forEach // 自己的圆
                            val nx = sc.x.coerceIn(rect.left, rect.right)
                            val ny = sc.y.coerceIn(rect.top, rect.bottom)
                            if (kotlin.math.hypot(sc.x - nx, sc.y - ny) < sc.r + 2.dp.toPx()) return true
                        }
                        return false
                    }
                    var rect = rectAt(c.nodeY + c.r + gapY)
                    if (blocked(rect)) {
                        val above = rectAt(c.nodeY - c.r - gapY)
                        if (!blocked(above)) rect = above
                    }
                    if (drawnLabelRects.none { android.graphics.RectF.intersects(it, rect) }) {
                        drawnLabelRects.add(rect)
                        drawRoundRect(
                            color = palette.panel,
                            topLeft = Offset(rect.left, rect.top),
                            size = Size(rect.width(), rect.height()),
                            cornerRadius = CornerRadius(6.dp.toPx()),
                            alpha = 0.78f
                        )
                        drawContext.canvas.nativeCanvas.drawText(c.text, c.x, rect.bottom - 3.dp.toPx(), labelPaint)
                        labelsDrawn++
                    }
                }

            // R95 光晕全释放：最末层统一绘制——半径外扩 2.4x、三段渐隐，
            // 覆盖在标签/连线上方，光晕连续不再被任何元素截断（靠近节点的标签仅被极淡染色，文字可读性不受影响）
            glowSpecs.forEach { g ->
                val glowR = g.r * 2.4f
                val gc = Offset(g.x, g.y)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            g.color.copy(alpha = palette.nodeGlowAlpha * g.alpha),
                            g.color.copy(alpha = palette.nodeGlowAlpha * 0.45f * g.alpha),
                            Color.Transparent
                        ),
                        center = gc,
                        radius = glowR
                    ),
                    radius = glowR,
                    center = gc
                )
            }
        }

        // 右上：主题切换 + 时间倍率 chips + 已遗忘计数（每帧随重组刷新）
        Surface(
            color = palette.panel,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, palette.panelBorder),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                // 深色/浅色主题切换（暗色背景回归入口）
                Surface(
                    color = palette.chipIdleBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable(onClick = onToggleTheme)
                        .testTag("blackhole-theme-toggle")
                ) {
                    Text(
                        text = if (darkTheme) "深色" else "浅色",
                        color = palette.chipIdleText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = "已遗忘 ${engine.absorbedCount}",
                    color = palette.panelSub,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 10.dp, end = 8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.testTag("blackhole-timescale-toggle")
                ) {
                    timeScaleLabels.forEachIndexed { index, label ->
                        val active = index == timeScaleIndex
                        Surface(
                            color = if (active) palette.chipActiveBg else palette.chipIdleBg,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { timeScaleIndex = index }
                        ) {
                            Text(
                                text = label,
                                color = if (active) palette.chipActiveText else palette.chipIdleText,
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // 左下：局内小窗（minimap）——全览节点位置，拖动小窗即移动主视角
        BlackHoleMinimap(
            engine = engine,
            camera = camera,
            palette = palette,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            frame = frame,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp)
        )

        // 抢救反馈 toast（底部居中、自动消失；D7 点击抢救复习）
        rescueToast?.let { msg ->
            Surface(
                color = palette.panel,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.panelBorder),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .testTag("blackhole-rescue-toast")
            ) {
                Text(
                    text = msg,
                    color = palette.panelText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        // 选中节点卡（底部，白卡/深卡细边框，保留证据入口）
        val selected = state.selectedNode
        if (selected != null && !interacting) {
            Surface(
                color = palette.panel,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, palette.panelBorder),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .testTag("blackhole-node-card")
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                palette.nodeColor(selected.kind),
                                CircleShape
                            )
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(
                            text = selected.label,
                            color = palette.panelText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${selected.kindLabel} · ${selected.statusLabel}",
                            color = palette.panelSub,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            if (onOpenChatEvidence != null && selected.sourceMessageId != null) {
                                TextButton(onClick = { onOpenChatEvidence(selected) }) {
                                    Text("会话证据", color = GraphAccent)
                                }
                            }
                            if (onOpenSourceEvidence != null && selected.sourceId != null) {
                                TextButton(onClick = { onOpenSourceEvidence(selected) }) {
                                    Text("资料证据", color = GraphAccent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 左下小窗：世界全览 + 视口框；点按/拖动小窗 -> 主视角跳转跟随。 */
@Composable
private fun BlackHoleMinimap(
    engine: BlackHoleGraphEngine,
    camera: BlackHoleCamera,
    palette: BlackHoleGraphPalette,
    viewportWidth: Float,
    viewportHeight: Float,
    frame: Long,
    modifier: Modifier = Modifier
) {
    var mmSize by remember { mutableStateOf(IntSize.Zero) }
    @Suppress("UNUSED_EXPRESSION") frame // 帧驱动重组（小窗内容每帧跟随）

    // 世界包围盒（黑洞 + 全部存活节点），外扩 15%
    var minX = engine.holeX
    var minY = engine.holeY
    var maxX = engine.holeX
    var maxY = engine.holeY
    engine.nodes.forEach { n ->
        if (!n.gone) {
            minX = min(minX, n.dispX)
            minY = min(minY, n.dispY)
            maxX = max(maxX, n.dispX)
            maxY = max(maxY, n.dispY)
        }
    }
    val padX = ((maxX - minX) * 0.15f).coerceAtLeast(80f)
    val padY = ((maxY - minY) * 0.15f).coerceAtLeast(80f)
    minX -= padX
    maxX += padX
    minY -= padY
    maxY += padY
    val worldW = (maxX - minX).coerceAtLeast(1f)
    val worldH = (maxY - minY).coerceAtLeast(1f)

    fun mmScale(w: Float, h: Float) = min(w / worldW, h / worldH)
    fun worldToMm(wx: Float, wy: Float, w: Float, h: Float): Offset {
        val s = mmScale(w, h)
        val ox = (w - worldW * s) / 2f
        val oy = (h - worldH * s) / 2f
        return Offset((wx - minX) * s + ox, (wy - minY) * s + oy)
    }
    fun mmToWorld(px: Float, py: Float, w: Float, h: Float): Offset {
        val s = mmScale(w, h)
        val ox = (w - worldW * s) / 2f
        val oy = (h - worldH * s) / 2f
        return Offset(minX + (px - ox) / s, minY + (py - oy) / s)
    }

    Surface(
        color = palette.panel.copy(alpha = if (palette.isDark) 0.85f else 0.92f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.panelBorder),
        shadowElevation = 2.dp,
        modifier = modifier
            .size(width = 200.dp, height = 140.dp)
            .onSizeChanged { mmSize = it }
            .testTag("blackhole-minimap")
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (mmSize.width > 0) {
                        val w0 = mmToWorld(
                            down.position.x, down.position.y,
                            mmSize.width.toFloat(), mmSize.height.toFloat()
                        )
                        camera.centerX = w0.x
                        camera.centerY = w0.y
                        camera.vx = 0f
                        camera.vy = 0f
                    }
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            active = false
                        } else if (change.positionChanged()) {
                            val w0 = mmToWorld(
                                change.position.x, change.position.y,
                                mmSize.width.toFloat(), mmSize.height.toFloat()
                            )
                            camera.centerX = w0.x
                            camera.centerY = w0.y
                            camera.vx = 0f
                            camera.vy = 0f
                            change.consume()
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") frame
            val w = size.width
            val h = size.height
            // 节点点
            val s = mmScale(w, h)
            engine.renderNodes().forEach { node ->
                val c = worldToMm(node.x, node.y, w, h)
                drawCircle(
                    color = palette.nodeColor(node.kind),
                    radius = (node.radius * s).coerceIn(1.8f, 4.6f),
                    center = c,
                    alpha = node.opacity
                )
            }
            // 视口框（主视角所见范围）
            if (viewportWidth > 0f && camera.scale > 0f) {
                val halfW = viewportWidth / 2f / camera.scale
                val halfH = viewportHeight / 2f / camera.scale
                val tl = worldToMm(camera.centerX - halfW, camera.centerY - halfH, w, h)
                val br = worldToMm(camera.centerX + halfW, camera.centerY + halfH, w, h)
                drawRect(
                    color = palette.topBarText,
                    topLeft = tl,
                    size = androidx.compose.ui.geometry.Size(br.x - tl.x, br.y - tl.y),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun BlackHoleGraphMessage(
    text: String,
    palette: BlackHoleGraphPalette,
    showSpinner: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showSpinner) {
            CircularProgressIndicator(
                color = palette.topBarSub,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = text,
            color = palette.topBarSub,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = GraphAccent),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(actionLabel, color = Color.White)
            }
        }
    }
}

@Composable
private fun BlackHoleGraphTopBar(
    title: String,
    subtitle: String,
    palette: BlackHoleGraphPalette,
    onBack: () -> Unit,
    onMore: (() -> Unit)?
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
    ) {
        Text(
            text = "‹",
            color = palette.topBarText,
            fontSize = 28.sp,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp)
                .testTag("blackhole-graph-back")
        )
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.topBarText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = palette.topBarSub, fontSize = 12.sp)
            }
        }
        if (onMore != null) {
            Text(
                text = "⋯",
                color = palette.topBarText,
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable(onClick = onMore)
                    .padding(horizontal = 12.dp)
            )
        }
    }
}
