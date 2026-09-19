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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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
    val holeCore: Color,
    /** 光子环：贴事件视界边缘的亮白窄环（用户 2026-09-19 拍板：线条/光晕一律纯白）。 */
    val photonRing: Color,
    /** 吸积盘内缘色（最亮的白）。 */
    val diskHot: Color,
    /** 吸积盘中段色（白，靠透明度分层）。 */
    val diskMid: Color,
    /** 吸积盘外缘色（白，向外渐隐）。 */
    val diskOuter: Color,
    val holeExclusionHint: Color,
    val edge: Color,
    val label: Color,
    val dust: Color,
    val dustAlphaBoost: Float,
    val bounceMark: Color,
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
        holeCore = Color(0xFF0B0E14),
        photonRing = Color(0xFFFFFFFF),
        diskHot = Color(0xFFFFFFFF),
        diskMid = Color(0xFFFFFFFF),
        diskOuter = Color(0xFFFFFFFF),
        holeExclusionHint = Color(0x14273A5E),
        edge = Color(0xFF8E9AB8),
        label = Color(0xFF46506B),
        dust = Color(0xFF93A2C4),
        dustAlphaBoost = 1f,
        bounceMark = Color(0xFFD84C4C),
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
        nodeGlowAlpha = 0.55f,
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

    /** 深色回归版：夜空底 + 银河星景（视差星点/银河带/星云）+ 高饱和节点 + 吸积盘黑洞。 */
    val Dark = BlackHoleGraphPalette(
        background = Color(0xFF0B1026),
        holeCore = Color(0xFF05060D),
        photonRing = Color(0xFFFFFFFF),
        diskHot = Color(0xFFFFFFFF),
        diskMid = Color(0xFFEDF1F8),
        diskOuter = Color(0xFFC9D2E4),
        holeExclusionHint = Color(0x336FA8F0),
        edge = Color(0xFF8FA3C8),
        label = Color(0xFFE8ECF4),
        dust = Color(0xFFAFC3E8),
        dustAlphaBoost = 1.5f,
        bounceMark = Color(0xFFD84C4C),
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
        nodeGlowAlpha = 0.35f,
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
/** 吸积盘湍流条纹：开普勒差速自转（内快外慢，omega ~ r^-1.5）。 */
private data class DiskStreak(
    /** 半径（核心半径的倍数，1.2~2.9——铺满遗忘渐进区）。 */
    val rNorm: Float,
    val angle0: Float,
    val omega: Float,
    val sweep: Float,
    val alpha: Float,
    /** 线宽（核心半径的倍数，分辨率无关）。 */
    val width: Float
)

private fun buildDiskStreaks(): List<DiskStreak> {
    val rng = Random(20260919L xor 0xD15C)
    return List(72) {
        val rNorm = 1.2f + 1.7f * rng.nextFloat()
        val kepler = 1.1f / kotlin.math.sqrt(rNorm * rNorm * rNorm)
        DiskStreak(
            rNorm = rNorm,
            angle0 = rng.nextFloat() * 6.2832f,
            omega = kepler * (0.8f + 0.4f * rng.nextFloat()),
            sweep = 18f + 66f * rng.nextFloat(),
            alpha = 0.10f + 0.30f * rng.nextFloat(),
            width = 0.030f + 0.060f * rng.nextFloat()
        )
    }
}

/** 内流光束（仅浅色主题）：光从盘口线向黑洞中心汇聚——用户 2026-09-20 拍板「光芒往中心射」。 */
private data class InflowRay(
    /** 固定方位角（弧度，抖动非均布）。 */
    val angle: Float,
    /** 脉冲相位（0~1，各束错峰）。 */
    val phase: Float,
    /** 脉冲速度（圈/秒）。 */
    val speed: Float,
    /** 束身基础 alpha。 */
    val alpha: Float,
    /** 束宽（dp）。 */
    val widthDp: Float
)

private fun buildInflowRays(): List<InflowRay> {
    val rng = Random(20260920L xor 0xBEA4)
    return List(18) { i ->
        InflowRay(
            angle = i * 6.2832f / 18f + (rng.nextFloat() - 0.5f) * 0.22f,
            phase = rng.nextFloat(),
            speed = 0.10f + 0.10f * rng.nextFloat(),
            alpha = 0.20f + 0.16f * rng.nextFloat(),
            widthDp = 1.2f + 1.8f * rng.nextFloat()
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = a.alpha + (b.alpha - a.alpha) * t
)

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
    val field = engine.exclusionRadius + extent * 2.2f
    val count = 200
    val dotColors = colors.ifEmpty { listOf(Color(0xFF93A2C4)) }
    return List(count) {
        val angle = rng.nextFloat() * 2f * Math.PI.toFloat()
        val dist = engine.exclusionRadius * 0.5f + rng.nextFloat() * field
        // 大小分档：88% 小点 + 12% 大颗粒点缀，画面更有层次
        val big = rng.nextFloat() < 0.12f
        DustDot(
            x = engine.holeX + kotlin.math.cos(angle) * dist,
            y = engine.holeY + kotlin.math.sin(angle) * dist,
            radius = if (big) 2.6f + rng.nextFloat() * 1.2f else 1.0f + rng.nextFloat() * 1.6f,
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
    val field = engine.exclusionRadius + extent * 2.2f
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
            val dist = engine.exclusionRadius * 0.6f + rng.nextFloat() * field
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

/** 相机：世界坐标中心 + 缩放；平移降敏（x0.72）、松手滑移惯性衰减 0.93（Obsidian 式微惯性）、缩放范围 0.45~3.2。 */
private class BlackHoleCamera {
    var centerX = 0f
    var centerY = 0f
    var scale = 1f
    var vx = 0f
    var vy = 0f
    var initialized = false

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

    /** 初始化：质心居中 + 包围半径舒适入画（fit-zoom）。 */
    fun initializeFit(engine: BlackHoleGraphEngine, viewportW: Float, viewportH: Float) {
        if (viewportW <= 0f || viewportH <= 0f) return
        val centroid = engine.centroidOfCluster()
        val extent = engine.clusterExtent()
        centerX = centroid.first
        centerY = centroid.second
        val fit = min(viewportW, viewportH) * 0.44f / extent
        scale = fit.coerceIn(0.45f, 3.2f)
        vx = 0f
        vy = 0f
        initialized = true
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
        BlackHoleGraphEngine().apply {
            populate(
                graphNodes = layoutNodes.map { Triple(it.id, it.label, it.kind) },
                edges = snapshot.edges.map { it.fromNodeId to it.toNodeId }
            )
        }
    }
    val camera = remember(engine) { BlackHoleCamera() }
    val dust = remember(engine, palette) { buildDust(engine, palette.dustColors) }
    val diskStreaks = remember { buildDiskStreaks() }
    val inflowRays = remember { buildInflowRays() }
    val starfield = remember(engine) { buildStarfield(engine) }
    val nebulae = remember(engine) { buildNebulae(engine) }
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
                    val hit = engine.hitTest(downWorld.x, downWorld.y)
                    // 普通命中未中时，再探「可抢救」的濒死节点（闪烁呼吸段；普通 hitTest 对 Dying 豁免）
                    val rescueHit = if (hit == null) engine.hitTestRescuable(downWorld.x, downWorld.y) else null
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
                                val world = screenToWorld(change.position)
                                engine.dragTo(world.x, world.y)
                                change.consume()
                            }
                        }
                        engine.endDrag()
                        interacting = false
                        onGraphInteractionChanged(false)
                        if (totalMove < viewConfiguration.touchSlop) {
                            onSelectedNodeChange(if (state.selectedNodeId == hit.id) null else hit.id)
                        }
                    } else if (rescueHit != null) {
                        // 点按闪烁的濒死节点 = 抢救复习（用户 2026-09-19 拍板 D7 断链离散方案）：
                        // 不做拖动/平移，等抬手且位移小于阈值即触发抢救——遗忘清零、冷却重置、
                        // 节点回归净空带外沿、与母节点的连线自动重接。
                        var totalMove = 0f
                        var active = true
                        while (active) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                active = false
                            } else if (change.positionChanged()) {
                                totalMove += change.positionChange().getDistance()
                                change.consume()
                            }
                        }
                        if (totalMove < viewConfiguration.touchSlop && engine.rescueNode(rescueHit.id)) {
                            rescueToast = "已抢救回归「${rescueHit.label}」：复习完成，遗忘清零"
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
            }

            fun worldToScreen(wx: Float, wy: Float): Offset = Offset(
                (wx - camera.centerX) * camera.scale + size.width / 2f,
                (wy - camera.centerY) * camera.scale + size.height / 2f
            )

            val seconds = frame / 1_000_000_000f

            if (palette.isDark) {
                // 银河背景（深色）：银河带 -> 星云辉光 -> 三层视差星点，营造「身处银河看星球」
                // 视差映射：p=1 与前景一致；p<1 相机平移时移动更慢（远景），初始与前景对齐
                fun bgToScreen(wx: Float, wy: Float, p: Float): Offset = Offset(
                    size.width / 2f + ((wx - engine.holeX) - (camera.centerX - engine.holeX) * p) * camera.scale,
                    size.height / 2f + ((wy - engine.holeY) - (camera.centerY - engine.holeY) * p) * camera.scale
                )

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
                // 彩色点缀点：漂移 + 闪烁（浅色主题，取节点粉彩色）
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
            }

            // 吸积盘黑洞（参考开源社区主流设计：ebruneton black_hole_shader / Shadertoy lstSRS / threejs-blackhole）：
            // 事件视界黑核 + 光子环 + 吸积盘（温度梯度 + 湍流条纹 + 开普勒差速 + 多普勒亮边），无额外光环。
            // 盘面正好铺满遗忘渐进区（1.15x 核心 → 3x 净空带），盘外缘即渐进区边界。
            // 盘画在连线下层；黑核本体移到连线之后再画——任何跨洞连线都会被黑核盖住，绝不「接进」黑洞。
            val holeCenter = worldToScreen(engine.holeX, engine.holeY)
            val coreR = engine.holeCoreRadius * camera.scale
            val diskR = engine.exclusionRadius * camera.scale
            // 盘底色：径向亮度梯度（内缘最亮 → 外缘渐隐；用户拍板纯白盘）
            val diskStops = if (palette.isDark) arrayOf(
                0f to Color.Transparent,
                (coreR * 1.05f / diskR) to Color.Transparent,
                (coreR * 1.25f / diskR) to palette.diskHot.copy(alpha = palette.diskHot.alpha * 0.75f),
                (coreR * 1.9f / diskR) to palette.diskMid.copy(alpha = palette.diskMid.alpha * 0.5f),
                (coreR * 2.7f / diskR) to palette.diskOuter.copy(alpha = palette.diskOuter.alpha * 0.22f),
                1f to Color.Transparent
            ) else arrayOf(
                // 浅色：外缘渐隐改陡——白盘收得果断，像白瓷盘的肩部而不是一团雾（真机反馈「外盘边缘再明确一点」）
                0f to Color.Transparent,
                (coreR * 1.05f / diskR) to Color.Transparent,
                (coreR * 1.25f / diskR) to palette.diskHot,
                (coreR * 1.9f / diskR) to palette.diskMid.copy(alpha = 0.85f),
                (coreR * 2.55f / diskR) to palette.diskOuter.copy(alpha = 0.55f),
                (coreR * 2.9f / diskR) to palette.diskOuter.copy(alpha = 0.2f),
                1f to Color.Transparent
            )
            drawCircle(
                brush = Brush.radialGradient(*diskStops, center = holeCenter, radius = diskR),
                radius = diskR,
                center = holeCenter
            )
            // 湍流条纹：开普勒差速（内快外慢）+ 多普勒亮边（一侧更亮，亮边方向极缓慢摆动）
            val beamAngle = 3.5779f + 0.25f * sin(seconds * 0.11f)
            diskStreaks.forEach { streak ->
                val r = streak.rNorm * coreR
                val theta = streak.angle0 + seconds * streak.omega
                val rel = kotlin.math.cos(theta - beamAngle)
                val beaming = 0.62f + 0.55f * rel * rel
                val tt = (streak.rNorm - 1.2f) / 1.7f
                val streakColor = if (tt < 0.5f) {
                    lerpColor(palette.diskHot, palette.diskMid, tt * 2f)
                } else {
                    lerpColor(palette.diskMid, palette.diskOuter, (tt - 0.5f) * 2f)
                }
                drawArc(
                    color = streakColor,
                    startAngle = theta * 57.29578f,
                    sweepAngle = streak.sweep,
                    useCenter = false,
                    topLeft = Offset(holeCenter.x - r, holeCenter.y - r),
                    size = Size(r * 2f, r * 2f),
                    alpha = (streak.alpha * beaming * if (palette.isDark) 1f else 2.2f).coerceIn(0f, 1f),
                    style = Stroke(width = streak.width * coreR)
                )
            }
            // 浅色外盘收边：1px 细「盘口线」，比米白底深半度的暖灰——设计语言「细边框分层」，
            // 白盘靠这道细边被裱出来，同时也正是遗忘渐进区（净空带）的边界
            if (!palette.isDark) {
                drawCircle(
                    color = Color(0xFFE0D4BC),
                    radius = diskR * 0.985f,
                    center = holeCenter,
                    alpha = 0.9f,
                    style = Stroke(width = 1f.dp.toPx())
                )
            }

            // 内流光束（仅浅色，用户 2026-09-20 拍板）：光从盘口线向中心汇聚——
            // 束身外淡内亮（光被黑洞吸入的蓄能感），脉冲包沿束向核心行进、没入光子环
            if (!palette.isDark) {
                val rayColor = Color(0xFFFFD98F)
                val rOut = diskR * 0.985f
                val rIn = coreR * 1.12f
                inflowRays.forEach { ray ->
                    val dirX = kotlin.math.cos(ray.angle)
                    val dirY = kotlin.math.sin(ray.angle)
                    val p0 = Offset(holeCenter.x + dirX * rOut, holeCenter.y + dirY * rOut)
                    val p1 = Offset(holeCenter.x + dirX * rIn, holeCenter.y + dirY * rIn)
                    drawLine(
                        brush = Brush.linearGradient(
                            0f to rayColor.copy(alpha = 0f),
                            0.55f to rayColor.copy(alpha = ray.alpha * 0.55f),
                            1f to rayColor.copy(alpha = ray.alpha),
                            start = p0,
                            end = p1
                        ),
                        start = p0,
                        end = p1,
                        strokeWidth = ray.widthDp.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // 脉冲包：从盘口向核心行进的一段亮斑，行至核心即没入（循环）
                    val travel = (seconds * ray.speed + ray.phase) % 1f
                    val headT = 0.08f + 0.92f * travel
                    val tailT = (headT - 0.16f).coerceAtLeast(0f)
                    val glow = kotlin.math.sin(travel * 3.1416f)
                    val h0 = Offset(
                        holeCenter.x + dirX * (rOut + (rIn - rOut) * tailT),
                        holeCenter.y + dirY * (rOut + (rIn - rOut) * tailT)
                    )
                    val h1 = Offset(
                        holeCenter.x + dirX * (rOut + (rIn - rOut) * headT),
                        holeCenter.y + dirY * (rOut + (rIn - rOut) * headT)
                    )
                    drawLine(
                        color = rayColor,
                        start = h0,
                        end = h1,
                        alpha = (ray.alpha * 1.6f * glow).coerceIn(0f, 0.85f),
                        strokeWidth = (ray.widthDp * 1.5f).dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 选中态：邻接保持高亮，其余压暗（聚焦模式）
            val selectedId = state.selectedNodeId
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

            // 连线：全局档 opacity 0.25，缩放超过标签阈值渐进增强；屏上粗细下限 1.2px（V24/V25）
            val edgeGate = when {
                camera.scale < 0.7f -> 1f
                else -> (1f + (camera.scale - 0.7f) / 0.8f * 0.8f).coerceAtMost(1.8f)
            }
            val edgeBaseWidthPx = max(1.2f, 1.dp.toPx())
            engine.renderEdges().forEach { edge ->
                val a = worldToScreen(edge.fromX, edge.fromY)
                val b = worldToScreen(edge.toX, edge.toY)
                val dim = if (selectedId != null &&
                    edge.fromId != selectedId && edge.toId != selectedId
                ) 0.22f else 1f
                drawLine(
                    color = palette.edge,
                    start = a,
                    end = b,
                    strokeWidth = edgeBaseWidthPx * (1f + (edgeGate - 1f) * 0.5f),
                    alpha = (0.25f * edgeGate * edge.opacityFactor * dim).coerceIn(0f, 1f)
                )
            }

            // 黑核本体：压在连线之上（真机反馈「线连进黑洞」）——跨洞连线到核边界为止，核面绝无线条穿过
            drawCircle(
                color = palette.holeCore,
                radius = coreR,
                center = holeCenter
            )

            // 光子环：贴视界边缘的窄亮环（三层描边假高斯）+ 极轻闪烁，让黑核「有边界」而非一坨黑
            val ringShimmer = 0.85f + 0.15f * sin(seconds * 1.7f)
            val photonR = coreR * 1.08f
            drawCircle(color = palette.photonRing, radius = photonR, center = holeCenter,
                alpha = 0.16f * ringShimmer, style = Stroke(width = 5.5f.dp.toPx()))
            drawCircle(color = palette.photonRing, radius = photonR, center = holeCenter,
                alpha = 0.38f * ringShimmer, style = Stroke(width = 2.8f.dp.toPx()))
            drawCircle(color = palette.photonRing, radius = photonR, center = holeCenter,
                alpha = 0.92f * ringShimmer, style = Stroke(width = 1.4f.dp.toPx()))

            // 拖到黑洞上方：净空带警示圈 + 节点 X 标记（松手弹开、不吞噬）
            if (engine.dragOverHole) {
                drawCircle(
                    color = palette.holeExclusionHint,
                    radius = engine.exclusionRadius * camera.scale,
                    center = holeCenter,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }

            // 节点：柔光晕 -> 填充 -> 细边 -> 标签
            val labelColor = palette.label
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    225,
                    (labelColor.red * 255).toInt(),
                    (labelColor.green * 255).toInt(),
                    (labelColor.blue * 255).toInt()
                )
                textSize = 11.sp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }
            val borderPx = max(1.5f, 1.8.dp.toPx())
            // 标签候选：循环内只收集，循环后做防重叠剔除再统一绘制
            // （用户反馈「字体全都重复在一起」——选中节点最优先，其余按节点大小，矩形相交则跳过）
            class LabelCandidate(val text: String, val x: Float, val y: Float, val selected: Boolean, val r: Float)
            val labelCandidates = mutableListOf<LabelCandidate>()
            engine.renderNodes().forEach { node ->
                val center = worldToScreen(node.x, node.y)
                val r = node.radius * camera.scale
                val dim = if (selectedId != null && node.id !in neighborIds) 0.22f else 1f
                // 漩涡旅程末段（完全进入黑洞区域）做闪烁呼吸 = 「即将遗忘」的警告（用户 R66 设计）
                val dToHole = kotlin.math.hypot(node.x - engine.holeX, node.y - engine.holeY)
                val breathing = node.mode == BlackHoleNodeMode.Dying && dToHole <= engine.exclusionRadius
                val breathAlpha = if (breathing) {
                    0.45f + 0.55f * (0.5f + 0.5f * kotlin.math.sin(seconds * 5.5f))
                } else 1f
                val alpha = (node.opacity * dim * breathAlpha).coerceIn(0f, 1f)
                if (r <= 0.5f) return@forEach
                val base = palette.nodeColor(node.kind)
                // 选中/抢救提示环用同色相压暗描边（用户 2026-09-19 拍板：不要黑色线框），与米白底和同色光晕都有区分度
                val ringColor = Color(base.red * 0.72f, base.green * 0.72f, base.blue * 0.72f)
                // 柔光晕（半径外扩 ~2.1x，渐隐）：浅色用节点同色晕——白色晕在米白底上不可见还会把节点洗淡（R68 用户反馈「太淡」）
                val glowColor = if (palette.isDark) palette.nodeGlow else base
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = palette.nodeGlowAlpha * alpha),
                            Color.Transparent
                        ),
                        center = center,
                        radius = r * 2.1f
                    ),
                    radius = r * 2.1f,
                    center = center
                )
                drawCircle(color = base, radius = r, center = center, alpha = alpha)
                // 细边
                drawCircle(
                    color = palette.nodeBorder.copy(alpha = 0.95f * alpha),
                    radius = r,
                    center = center,
                    alpha = alpha,
                    style = Stroke(width = borderPx)
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
                if (node.mode == BlackHoleNodeMode.Dragging && engine.dragOverHole) {
                    val markR = r + 8.dp.toPx()
                    val w = 2.dp.toPx()
                    drawLine(palette.bounceMark, Offset(center.x - markR, center.y - markR), Offset(center.x + markR, center.y + markR), strokeWidth = w)
                    drawLine(palette.bounceMark, Offset(center.x - markR, center.y + markR), Offset(center.x + markR, center.y - markR), strokeWidth = w)
                }
                // 标签：遗忘未过半且缩放达到标签阈值且入场完成（引擎已 gating entrance）——先收集，统一剔除后绘制
                if (node.showLabel && camera.scale >= 0.8f && alpha > 0.05f) {
                    labelCandidates.add(
                        LabelCandidate(
                            node.label.take(12),
                            center.x,
                            center.y + r + 13.dp.toPx(),
                            state.selectedNodeId == node.id,
                            r
                        )
                    )
                }
            }
            // 标签防重叠剔除：选中节点最优先，其余按节点半径从大到小；与已画矩形相交则跳过
            val drawnLabelRects = mutableListOf<android.graphics.RectF>()
            labelCandidates
                .sortedWith(compareByDescending<LabelCandidate> { it.selected }.thenByDescending { it.r })
                .forEach { c ->
                    val w = labelPaint.measureText(c.text)
                    val rect = android.graphics.RectF(
                        c.x - w / 2f - 4.dp.toPx(),
                        c.y - 12.dp.toPx(),
                        c.x + w / 2f + 4.dp.toPx(),
                        c.y + 4.dp.toPx()
                    )
                    if (drawnLabelRects.none { android.graphics.RectF.intersects(it, rect) }) {
                        drawnLabelRects.add(rect)
                        drawContext.canvas.nativeCanvas.drawText(c.text, c.x, c.y, labelPaint)
                    }
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
            // 黑洞
            val hc = worldToMm(engine.holeX, engine.holeY, w, h)
            drawCircle(
                color = palette.holeCore,
                radius = 4.dp.toPx(),
                center = hc
            )
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
