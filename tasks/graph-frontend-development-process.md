# 知识图谱前端开发流程 · 从设计到真实交互

> 基于"设计稿不是程序说明书"的方法论，针对 reverse-tutor Android 分支图谱功能重新定义的前端开发流程。
> 分支：`Android`（F:\xw\reverse-tutor）| 模块：`feature/memory`

---

## 零、诊断：当前图谱为什么"无法把设计转换成真实交互"

读完 `KnowledgeGraphPanel.kt` 和 `GraphCanvasInteractionState.kt` 后，问题定位到三层断裂：

### 断裂 1：渲染层用像素绘制，不是组件模型

图谱的一切——节点、边、标签、工具栏图标——都是 Canvas 上的 `drawCircle()` / `drawPath()` / `drawText()` 像素操作。

这带来的直接后果：
- 节点没有"状态"概念——你不能给一个 `drawCircle` 加 pressed / loading / disabled 状态
- 节点没有无障碍语义——`contentDescription` 只能挂在包裹 Canvas 的 Box 上，无法到单个节点
- 节点没有响应式——屏幕变了、字体放大了，Canvas 里的像素不会自适应
- 你无法对单个节点做组件级动效（scale / fade / spring）

**根因**：用了"截图思维"（画出来像就行）而不是"规则思维"（每个节点是一个有状态、有交互的组件）。

### 断裂 2：状态机存在但与渲染脱节

`GraphCanvasInteractionState` 其实设计得不错：
- 有完整的 event-reducer 纯函数模式（`reduceGraphCanvasInteraction`）
- 有手势阶段枚举（Idle / Pressed / Panning / Scaling / DraggingNode）
- 有仲裁优先级注释（语义控件 > 多指手势 > 节点命中 > 空白单指 > 空白双击）

但问题是：**状态变了，Canvas 不会自动响应**。从 `reducer 返回新状态` 到 `画布重绘成对应样子` 之间，没有声明式绑定——全靠 `pointerInput` 里手动读 state 然后调 draw。这就导致：
- 拖拽节点时位置更新可能跳帧（Canvas 重绘和状态更新不同步）
- 缩放焦点计算和实际视觉效果可能错位
- 搜索选中节点后画布不会自动 pan 到该节点

**根因**：缺少一个"状态 → 视觉"的声明式映射层。

### 断裂 3：视觉值硬编码，没有 Design Token 层

```
GraphInk = Color(0xFF141C29)      // 硬编码
GraphMuted = Color(0xFF738099)    // 硬编码
fontSize = 14.sp                  // 硬编码
fontSize = 10.sp                  // 硬编码
shape = RoundedCornerShape(8.dp)  // 硬编码
```

这些散落在文件各处。有的地方用了 `FormalColors`，有的没用。结果：
- 改一个颜色要全局搜索替换
- 间距节奏不统一（有的 8dp，有的 10dp，有的 12dp，有的 14dp）
- 每个页面"单独看都能看，放到一起就很 AI"

**根因**：没有产品级 Design Token 体系约束视觉一致性。

### 断裂 4：没有动效/交互规格

reducer 只管"状态 A → 状态 B"，但不管：
- A 到 B 的视觉怎么过渡（spring? tween? 多少 ms? 什么 easing?）
- 拖拽松手后靠 velocity 决定 snap 到哪个停靠点
- 节点点击的 scale 反馈（1.0 → 0.96 → 1.02 → 1.0）
- 详情面板的展开/收起动画曲线

这些"运动逻辑"完全没有规格定义，只有零星 `animateFloatAsState` 调用。

---

## 一、重新定义的开发流程

核心原则：**设计稿不直接跳到 Compose 代码**，而是经过 6 个阶段，每阶段产出明确的中间制品（artifact），前一阶段是后一阶段的唯一输入。

```
设计意图
  ↓ 阶段 1：设计解释
Graph Visual Contract（视觉契约）
  ↓ 阶段 2：UI 架构
Graph UI Specification（界面规格）
  ↓ 阶段 3：设计令牌
Graph Design Tokens（设计令牌）
  ↓ 阶段 4：组件实现
Graph Components（语义组件）
  ↓ 阶段 5：交互接线
Interaction Wiring（交互接线 + 动效规格）
  ↓ 阶段 6：验证
Verification（多层级验证）
```

每个阶段都有**输入、输出、质量门禁**。门禁不过，不进下一阶段。

---

## 阶段 1：设计解释（Design Interpretation）

### 目标
把"图谱应该长什么样、能做什么"的模糊意图，转成结构化的视觉契约。**不写代码。**

### 输入
- 用户对图谱功能的描述（要展示什么、交互意图）
- 现有代码中的领域模型（`GraphNodeKind` / `GraphNodeStatus` / `GraphLayoutNode` / `GraphLayoutEdge`）

### 输出：Graph Visual Contract
一份结构化文档，回答以下问题：

**1.1 区域清单**
图谱界面包含哪些独立区域？例如：
- 画布区（Canvas Area）
- 工具栏（Toolbar：缩放/居中/筛选/搜索/帮助）
- 节点详情面板（Node Detail Sheet）
- 节点列表（Node List Control）
- 状态面板（Status Panel：空/错误/加载/过大）
- 图例（Legend）

**1.2 组件清单**
每个区域需要哪些组件？例如：
- GraphNodeView（节点：圆形/方形、不同 kind 不同色、不同 status 不同边框）
- GraphEdgeView（边：实线/虚线、有向/无向、带标签）
- GraphToolbar（浮动工具栏）
- GraphDetailSheet（底部详情面板）
- GraphSearchBar（搜索栏）
- GraphLegendChip（图例标签）

**1.3 设计语言**
- 颜色语义：节点 kind → 颜色映射规则；状态 → 边框/底色映射规则
- 间距节奏：统一使用哪几档间距（如 xs=4 / sm=8 / md=12 / lg=16 / xl=24）
- 字体层级：标题/正文/标注各用什么字号行高
- 圆角逻辑：卡片/标签/按钮各用什么圆角

**1.4 交互意图**
- 用户可以做什么？列出来：平移画布、缩放、点击节点选中、双击节点打开详情、拖拽节点移动位置、搜索定位、工具栏操作

### 质量门禁
- [ ] 每个区域和组件都有明确的业务语义命名（不是 Row/Column/Box）
- [ ] 颜色/间距/字体有统一规则，不允许"差不多就行"
- [ ] 交互意图覆盖所有用户可达路径

---

## 阶段 2：UI 架构（UI Architecture）

### 目标
把视觉契约转成可实现的界面规格——语义树、布局约束、状态机、交互规则、动效规则。**仍不写 Compose 代码。**

### 输入
Graph Visual Contract

### 输出：Graph UI Specification

**2.1 语义 UI 树**
```
GraphScreen
├─ GraphCanvasRegion
│  ├─ GraphNodeLayer（所有节点）
│  ├─ GraphEdgeLayer（所有边）
│  └─ GraphViewportTransform（视口变换：pan/zoom/semantic mode）
├─ GraphToolbar（浮动）
├─ GraphDetailSheet（底部面板，可展开/收起）
├─ GraphSearchBar（可展开/收起）
├─ GraphNodeListControl（可展开/收起）
├─ GraphStatusPanel（条件渲染：非 Ready 状态时显示）
└─ GraphLegend（条件渲染）
```

**2.2 布局约束**
- CanvasRegion：fillMaxSize，受 toolbar/detail 覆盖
- Toolbar：浮动在 CanvasRegion 底部居中，不遮挡 DetailSheet
- DetailSheet：底部锚定，展开时上推 CanvasRegion（或覆盖）
- SearchBar：顶部锚定，展开时下推内容
- 每个面板的高度规则：固定 / 自适应 / 比例

**2.3 组件模型**
每个组件定义：
- Props（输入参数）
- States（至少 5 个：default / pressed / loading / error / empty）
- Events（发出的事件）
- 内部状态（如果需要）

示例 — GraphNodeView：
- Props: `node: GraphLayoutNode`, `viewport: GraphViewportState`, `isSelected: Boolean`
- States: default / pressed / selected / locked / dragPreview
- Events: `onNodePressed(nodeId)`, `onNodeDragged(nodeId, position)`, `onNodeReleased(nodeId)`
- 内部状态: 无（纯函数于 props）

**2.4 状态机**
当前 `GraphCanvasInteractionState` 已有基础，需补完：
- 手势仲裁状态机：明确每个手势阶段允许的下一个阶段
- 详情面板状态机：Collapsed → Dragging → HalfExpanded → Dragging → Expanded（带 snap points）
- 搜索状态机：Closed → Opening(query="") → Typing(query≠"") → ResultSelected → Closed
- 确定每个状态转换的触发条件和副作用

**2.5 交互规则**
- 手势优先级：语义控件（toolbar/search/detail 上的手势）> 多指缩放 > 节点拖拽 > 节点点击 > 空白平移 > 空白双击
- 阈值定义：拖拽超过多少 dp 才算"拖动"而非"点击"（如 8dp touch slop）
- snap points：视口缩放的停靠点（如 0.55x / 1.0x / 2.0x / 3.2x）
- velocity 规则：松手时速度 > 阈值则继续滑，否则 snap 到最近停靠点

**2.6 动效规格**
为每个状态转换定义运动规则：
- 节点选中：scale 1.0 → 1.08，spring(dampingRatio=0.6, stiffness=Spring.StiffnessMedium)
- 节点松手回弹：scale 1.08 → 1.0，spring(dampingRatio=0.4)
- 视口缩放：跟随手指，松手后 snap 到最近停靠点，spring
- 详情面板展开：height animate，tween(280ms, EaseOut)
- 搜索栏展开：translateY + fade，tween(200ms, EaseInOut)

**2.7 系统适配规则**
- Insets：处理状态栏（顶部）、导航栏（底部）、IME（键盘弹出时详情面板不被遮挡）
- Edge-to-edge：背景延伸到系统栏下方，内容避让
- 字体缩放：支持 150% 系统字体，节点标签不溢出（compactLabel 策略）
- 屏幕宽度断点：< 360dp 紧凑模式 / 360-600dp 标准 / > 600dp 宽屏

### 质量门禁
- [ ] 语义树每个节点有业务语义名（不是 Compose 原语）
- [ ] 每个组件至少定义 5 个状态
- [ ] 手势优先级有明确顺序，无歧义
- [ ] 每个状态转换有动效规格（spring/tween + 参数）
- [ ] 系统适配覆盖 insets / edge-to-edge / 字体缩放 / 屏幕断点

---

## 阶段 3：设计令牌（Design Tokens）

### 目标
消灭所有硬编码颜色/尺寸/圆角/动效参数，统一到令牌层。

### 输入
Graph UI Specification 的设计语言部分 + 现有 `FormalColors` / `FormalShapes` / `FormalTypeScale`

### 输出：Graph Design Tokens

定义图谱专属令牌，映射到 core:design 的基础令牌：

```kotlin
// 颜色令牌——映射到 FormalColors
object GraphColors {
    val NodeDefault = FormalColors.Primary        // 节点默认色
    val NodeSelected = FormalColors.Primary       // 选中态（可加描边区分）
    val NodeLocked = FormalColors.Tertiary         // 锁定态
    val Edge = FormalColors.Border                 // 边线色
    val EdgeDirected = FormalColors.BorderStrong   // 有向边
    val CanvasBackground = FormalColors.SurfaceSubtle
    val Ink = FormalColors.Ink
    val Muted = FormalColors.Muted
    // 不再出现 0xFF141C29 这种硬编码
}

// 间距令牌——统一节奏
object GraphSpacing {
    val xs = 4.dp   // 节点内文字间距
    val sm = 8.dp   // 紧凑元素间距
    val md = 12.dp  // 标准间距
    val lg = 16.dp  // 宽松间距
    val xl = 24.dp  // 区域间距
}

// 尺寸令牌
object GraphSizes {
    val NodeRadius = 20.dp
    val NodeRadiusSelected = 22.dp
    val ToolbarButton = 48.dp       // 触控最小尺寸
    val ToolbarHeight = 52.dp
    val DetailSheetMinHeight = 120.dp
    val DetailSheetMaxHeight = 320.dp
}

// 动效令牌
object GraphMotion {
    val NodePressScale = SpringSpec(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    val ViewportSnap = SpringSpec(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
    val SheetExpand = TweenSpec(durationMillis = 280, easing = EaseOut)
    val SearchReveal = TweenSpec(durationMillis = 200, easing = EaseInOut)
}
```

### 质量门禁
- [ ] 代码中搜索不到 `Color(0x` 硬编码（除 Design Token 定义文件外）
- [ ] 代码中搜索不到 `.sp` 硬编码（除通过 FormalTypeScale 或 GraphTypography 间接引用）
- [ ] 间距值只来自 GraphSpacing 枚举

---

## 阶段 4：组件实现（Component Implementation）

### 目标
把每个语义组件实现为自包含的 Composable，有完整状态机和，消费 Design Token。

### 输入
Graph UI Specification + Graph Design Tokens

### 输出：Graph Components

**关键架构决策：节点用 Compose 组件还是 Canvas？**

推荐方案：**混合架构**
- 节点用 `Modifier.offset` + `Box` + 组件内容（不用 drawCircle）
  - 优点：有 semantics、有 state、有动画、有 accessibility
  - 节点内容（图标/文字/状态指示）用子 Composable
- 边用 Canvas 绘制（边是纯视觉连接，不需要交互语义）
- 视口变换用 `Modifier.graphicsLayer`（scale/translation）包裹整个节点层

这样每个节点就是：
```kotlin
@Composable
fun GraphNodeView(
    node: GraphLayoutNode,
    viewport: GraphViewportState,
    isSelected: Boolean,
    isLocked: Boolean,
    gesturePhase: GraphGesturePhase,
    onNodePressed: (String) -> Unit,
    onNodeDragged: (String, GraphPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    // 状态映射
    val nodeState = when {
        isLocked -> NodeState.Locked
        isSelected && gesturePhase == DraggingNode -> NodeState.DragPreview
        isSelected -> NodeState.Selected
        gesturePhase == Pressed -> NodeState.Pressed
        else -> NodeState.Default
    }
    // 动效
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.08f else 1.0f,
        animationSpec = GraphMotion.NodePressScale,
        label = "nodeScale"
    )
    // 布局
    Box(
        modifier = modifier
            .offset { IntOffset(
                (node.position.x * viewport.scale + viewport.pan.x).roundToInt(),
                (node.position.y * viewport.scale + viewport.pan.y).roundToInt()
            ) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(GraphSizes.NodeRadius * 2 * viewport.scale)
            .semantics { contentDescription = "${node.kindLabel}：${node.label}" }
            .testTag("graph-node-${node.id}")
            .pointerInput(node.id) { /* gesture detection → events */ }
    ) {
        // 节点视觉内容——子组件，不是 drawCircle
        NodeVisualContent(node, nodeState)
    }
}
```

**4.1 组件清单与实现顺序**
1. GraphNodeView（核心，5 状态）
2. GraphEdgeLayer（Canvas，纯绘制）
3. GraphViewportContainer（graphicsLayer 包裹，管 pan/zoom）
4. GraphToolbar（6 按钮，各自有 testTag + contentDescription）
5. GraphDetailSheet（底部面板，状态机驱动展开/收起）
6. GraphSearchBar（搜索栏，IME 适配）
7. GraphNodeListControl（列表控制）
8. GraphStatusPanel（状态面板，5 状态）
9. GraphLegend（图例）

**4.2 每个组件的状态覆盖矩阵**

| 组件 | Default | Pressed | Selected | Loading | Error | Empty | Disabled/Locked |
|------|---------|---------|----------|---------|-------|-------|-----------------|
| GraphNodeView | ✓ | ✓ | ✓ | — | — | — | ✓ |
| GraphToolbar | ✓ | ✓ | — | — | — | — | ✓ |
| GraphDetailSheet | Collapsed | — | Expanded | ✓ | ✓ | ✓ | — |
| GraphStatusPanel | — | — | — | ✓ | ✓ | ✓ | — |
| GraphSearchBar | Closed | — | Typing | Searching | Error | NoResult | — |

### 质量门禁
- [ ] 每个组件有至少 5 个状态的视觉实现
- [ ] 每个交互组件有 testTag + contentDescription
- [ ] 所有颜色/尺寸来自 Design Token，无硬编码
- [ ] 节点有 semantics，不是纯 Canvas 像素
- [ ] 组件可独立预览（@Preview）

---

## 阶段 5：交互接线（Interaction Wiring）

### 目标
连接：手势检测 → 事件 → reducer → 新状态 → 重组 → 视觉更新。加 động效规格落地。

### 输入
Graph Components + GraphCanvasInteractionState（已有 reducer）

### 输出：Interaction Wiring

**5.1 手势 → 事件映射**

在 `pointerInput` 中，把手势检测转成 `GraphCanvasInteractionEvent`：

```
pointerDown + hitTest(node) → NodePressed(nodeId, screenPoint)
pointerMove + nodeSelected + distance > slop → NodeDragged(nodeId, graphPoint)
pointerMove + noNodeSelected → PanChanged(delta)
multi-pointer → calculateZoom → ZoomChanged(scale, focalPoint)
pointerUp + distance < slop + time < tapTimeout → BlankTapped
double pointerUp within 300ms → BlankDoubleTapped
```

**5.2 事件 → 状态 → 重组**

```
事件派发 → reduceGraphCanvasInteraction(state, event) → 新 state
→ Compose 重组 → 组件读新 state → 视觉自动更新
```

关键：**不允许在 pointerInput 里直接 mutableState 修改视觉**。所有视觉变化必须经过 reducer。

**5.3 手势仲裁层**

在派发事件前，按优先级判断当前手势归谁：
```
fun arbitrateGesture(
    phase: GraphGesturePhase,
    pointerEvent: PointerEvent,
    hitTest: (Offset) -> String?
): GraphCanvasInteractionEvent? {
    // 1. 如果 toolbar/search/detail 正在交互 → 不拦截
    // 2. 如果多指 → ZoomChanged
    // 3. 如果命中节点 → NodePressed / NodeDragged
    // 4. 如果空白 → PanChanged / BlankTapped / BlankDoubleTapped
}
```

**5.4 动效规格落地**

把阶段 2.6 的动效规格实现到组件中：
- spring 参数来自 `GraphMotion` 令牌
- snap points 在视口松手时计算最近停靠点
- velocity 用 `detectDragGestures` 的 `dragAnalytics.velocity` 判断
- 详情面板用 `SheetState` + `anchoredDraggable` 实现三档 snap

**5.5 系统适配落地**
- `WindowInsets.statusBars` / `navigationBars` / `ime` 处理
- `Modifier.imePadding()` 给详情面板
- `LocalDensity.current.fontScale` 检查，超阈值启用 compactLabel
- `BoxWithConstraints` 做屏幕宽度断点

### 质量门禁
- [ ] 手势 → 事件 → reducer → 重组 链路完整，无直接 mutableState 跳过 reducer
- [ ] 手势仲裁优先级在代码中有明确实现，不是注释
- [ ] spring/snap/threshold 参数来自 Design Token
- [ ] insets / IME / fontScale 在真机上验证

---

## 阶段 6：验证（Verification）

### 目标
多层级验证，确保"设计→交互"真的落地。

### 输入
Interaction Wiring + 组件代码

### 输出：验证报告

**6.1 单元测试**
- reducer 状态转换测试（已有 `GraphCanvasInteractionStateTest`，补完手势仲裁用例）
- 组件状态矩阵测试（每个组件的每个状态渲染正确）

**6.2 交互测试**
- 手势 → 事件 映射测试
- snap point 计算测试
- velocity → snap 决策测试
- 手势仲裁优先级测试

**6.3 系统适配测试**
- insets 处理（状态栏/导航栏/键盘）
- 字体缩放 150% 不溢出
- 屏幕宽度 360dp / 411dp / 600dp 三档布局

**6.4 真机验收**
- 在真机上跑：拖拽节点、缩放、搜索、详情展开
- 检查：动画是否流畅、snap 是否准确、手势冲突是否解决
- 检查：无障碍服务能否读出每个节点

### 质量门禁
- [ ] reducer 测试覆盖率 > 90%
- [ ] 6.2 交互测试全部通过
- [ ] 真机验收无手势冲突 / 无错位 / 无跳帧

---

## 二、落地到图谱功能的具体映射

### 当前状态 → 目标状态

| 维度 | 当前 | 目标 |
|------|------|------|
| 节点渲染 | `drawCircle` 像素 | `GraphNodeView` Compose 组件，有 semantics/state/animation |
| 节点状态 | 无状态概念 | 5 状态：Default/Pressed/Selected/Locked/DragPreview |
| 颜色 | `Color(0xFF141C29)` 硬编码 | `GraphColors` 令牌，映射到 FormalColors |
| 间距 | 8/10/12/14 混用 | GraphSpacing 统一 5 档 |
| 手势→视觉 | pointerInput 直接 mutate | 事件→reducer→重组 声明式 |
| 动效 | 零星 animateFloatAsState | GraphMotion 令牌 + snap + velocity |
| 无障碍 | 仅 Box 级 testTag | 每个节点有 contentDescription |
| 系统适配 | 未处理 insets | insets + IME + fontScale + 屏幕断点 |

### 实施顺序

1. **先做阶段 3（Design Token）**——成本最低、收益最快：消灭硬编码，统一视觉语言
2. **再做阶段 4 的 GraphNodeView**——核心组件从 Canvas 像素升级为语义组件
3. **然后阶段 5 的交互接线**——把已有的 reducer 和新组件连起来
4. **阶段 1-2 可以并行**——在改代码的同时补完视觉契约和 UI 规格（文档）
5. **阶段 6 贯穿始终**——每改一个组件就跑对应测试

### 与现有代码的关系

- `GraphCanvasInteractionState.kt`：**保留**，它是阶段 2.4 的基础，reducer 模式正确
- `GraphCanvasPresentation.kt`：**重构**，从"计算绘制参数"改为"计算组件 props"
- `GraphInteractionPolicy.kt`：**保留并补完**，它是阶段 5.3 手势仲裁的基础
- `KnowledgeGraphPanel.kt`：**重构**，Canvas 绘制 → 语义组件树
- `FormalKnowledgeGraphScreens.kt`：**重构**，接入新组件 + Design Token
- `KnowledgeGraphModels.kt`：**保留**，领域模型不动

### 冻结层

- `core/model`、`core/protocol`、`core/llm`、`core/data`：零修改
- `GraphCanvasInteractionState.kt` 的 reducer 签名不变（只加新事件，不改已有事件语义）
