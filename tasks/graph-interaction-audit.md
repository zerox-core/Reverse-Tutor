# 图谱交互改动只读拆分与验收边界确认

> 日期：2026-08-22
> 分支：Android @ 007d519（origin/Android 同步）
> 审阅范围：工作树未提交改动（8 个文件，+927 / -136）
> 方法：纯只读 git diff + 文件直读，未修改任何文件

## 一、改动文件清单

| # | 文件 | 变更量 | 类别 |
|---|------|--------|------|
| 1 | `feature/memory/.../GraphCanvasInteractionState.kt` | +315/-59 | 契约·状态机 |
| 2 | `feature/memory/.../FormalKnowledgeGraphScreens.kt` | +105/-55 | 集成·Screen |
| 3 | `feature/memory/.../KnowledgeGraphPanel.kt` | +191/-36 | 渲染·Canvas |
| 4 | `feature/memory/.../GraphCanvasPresentation.kt` | +7 | 常量 |
| 5 | `feature/memory/.../GraphInteractionPolicy.kt` | -8 | 重构·迁移 |
| 6 | `feature/memory/.../GraphCanvasInteractionStateTest.kt` | +405/-18 | 单元测试 |
| 7 | `app/.../GraphWorkspaceGestureContractDeviceTest.kt` | +8/-4 | 设备测试 |
| 8 | `tasks/native-graph-source-fidelity-validation.md` | +24 | 验收文档 |

## 二、冻结层校验

- `mobile-native/core/`（model / protocol / llm / data）：**无改动**（`git diff --stat -- mobile-native/core/` 输出为空）
- Room / DAO / Migration / SecretStore：**无改动**（不在变更文件列表中）
- 无新增 import 指向冻结层（`GraphNodeKind` 仅为已有 import，非新增）

## 三、逐文件拆分

### 3.1 GraphCanvasInteractionState.kt — 交互状态模型（核心）

**新增类型：**
- `GraphCanvasMode` enum（Page / Canvas）— 普通页面 vs 沉浸式画布
- `GraphDetailState` sealed interface（Closed / Open(nodeId) / Closing）— 详情生命周期
- `GraphSearchState` data class（open + query）— 搜索状态，替代旧的 `searchOpen: Boolean` + `searchQuery: String`
- `GraphGesturePhase` enum — 从 `GraphInteractionPolicy.kt` 迁移至此（Idle/Pressed/Panning/Scaling/DraggingNode）
- `GraphViewportState` — 从 `KnowledgeGraphPanel.kt` 迁移至此，`pan` 类型从 Compose `Offset` 改为纯 Kotlin `GraphPoint`

**GraphCanvasInteractionState 扩展：**
- 新增字段：`mode`、`selectedNodeId`、`detail`、`gesture`
- `searchOpen` / `searchQuery` 合并为 `search: GraphSearchState`
- 新增计算属性 `detailNodeId`（从 detail.Open 提取）

**事件体系从 6 → 19 个：**
- Mode：EnterCanvas, ExitCanvas
- Pointer：PointerDown, NodePressed, NodeDragged, PointerUp, BlankTapped, BlankDoubleTapped
- Viewport：PanChanged, ZoomChanged, ViewportSet, FitRequested
- Search：SearchToggled, SearchQueryChanged, SearchResultChosen
- Detail：DetailOpened, DetailClosed
- Help：HelpToggled
- Reconcile：ReconcileNodes（增强）

**Reducer 关键逻辑：**
- NodePressed → 选中 + 打开详情（同一节点再次点击保持）
- BlankTapped → 清除选择 + 关闭详情
- BlankDoubleTapped → 清除选择 + 重置视口
- ZoomChanged → 焦点缩放数学（保持焦点下世界坐标不变）+ clamp(0.55f, 3.2f)
- SearchResultChosen → 只选中不打开详情（避免遮挡工具栏）
- DetailOpened → 需先有 selectedNodeId，否则 no-op
- DetailClosed → 保持选中
- ReconcileNodes → 清理无效的 overrides/selection/detail
- ExitCanvas → 重置 search + help

**设计原则声明（代码注释）：**
- 纯 Kotlin，不可依赖 Android UI
- 所有交互归约为事件
- 视觉层不得直接修改 mutableStateOf 中的领域状态
- 仲裁优先级由调用方保证，reducer 只负责状态转换语义

### 3.2 FormalKnowledgeGraphScreens.kt — Screen 层集成

**新增 `dispatchGraphInteraction(event)` 函数：**
- 调用 reducer 得到 next state
- selectedNodeId 变化时回调 `onSelectedNodeChange`（同步父级）
- EnterCanvas/ExitCanvas 时回调 `onCanvasModeChange`

**两个新 LaunchedEffect：**
- `canvasModeActive`：父级模式变化时同步 interactionState.mode
- `state.selectedNodeId`：父级选择变化时同步 interactionState.selectedNodeId + detail

**渲染状态分离：**
- `renderedState = state.withSelection(interactionState.selectedNodeId)` — 用交互状态的选择覆盖 UI 状态的选择
- `FormalGraphCanvas` 接收 `renderedState` 而非原始 `state`

**事件派发迁移：**
- 搜索切换 → SearchToggled
- 帮助切换 → HelpToggled
- 背景点击 → ExitCanvas
- 请求交互 → EnterCanvas
- 搜索查询 → SearchQueryChanged
- 搜索选择 → SearchResultChosen
- 空白点击 → BlankTapped
- 详情关闭 → DetailClosed
- ViewportChanged/NodeMoved 的 reducer 调用被移除（直接回调，不再经过 reducer）

**详情面板：**
- 从 `interactionState.detailNodeId` 驱动，不再从 `state.selectedNode` 驱动
- 关闭 scrim → BlankTapped（清除选择）
- 关闭详情按钮 → DetailClosed（保留选择）

### 3.3 KnowledgeGraphPanel.kt — Canvas 渲染器

**参数变更：**
- 新增 `interactionState: GraphCanvasInteractionState? = null`
- 新增 `onInteractionEvent: (GraphCanvasInteractionEvent) -> Unit = {}`
- `GraphViewportState` 定义删除（迁移到 InteractionState.kt）

**状态来源：**
- `selectedNodeId = interactionState?.selectedNodeId ?: state.selectedNodeId`
- `effectiveNodePositionOverrides = interactionState?.nodePositionOverrides ?: nodePositionOverrides`
- `controlledViewport = interactionState?.viewport` — 通过 LaunchedEffect 同步到 gestureState

**事件发射（pointer input 内）：**
- PointerDown — 手指落下
- NodeDragged — 节点拖拽中
- NodePressed — 节点点击命中
- BlankTapped — 空白单击
- BlankDoubleTapped — 空白双击
- PointerUp — 手指抬起

**视口同步：**
- `LaunchedEffect(gestureState)` 发射 `ViewportSet` 事件 + 调用 `onViewportChanged`
- `LaunchedEffect(controlledViewport)` 将交互状态视口同步回 gestureState（带防循环 guard）

**视觉改动（⚠️ 超出"不负责视觉效果"边界）：**
- 背景星点从 150 → 80 个，更小更暗
- 边线渲染：选中边改为 source→target 颜色渐变；普通边也改渐变
- 选中节点脉动动画（`animateFloatAsState` + `PulseScaleAmplitude`）
- 选择环：从单层改为双层（outer + mid）
- 节点填充：从纯色改为径向渐变
- 节点高光：半径 0.35→0.30，使用 `NodeHighlightAlpha`
- 卡片节点：新增阴影层

**向后兼容：**
- `interactionState == null` 时走旧路径（`onSelectedNodeChange`）
- 旧调用方不受影响

### 3.4 GraphCanvasPresentation.kt — 视觉常量

新增 7 个常量：
- `SelectionRingAlphaOuter = 0.08f`
- `SelectionRingAlphaMid = 0.15f`
- `NodeGradientEdgeAlphaFactor = 0.75f`
- `NodeHighlightAlpha = 0.40f`
- `NonNeighborAlpha = 0.12f`
- `PulseDurationMs = 1400`
- `PulseScaleAmplitude = 0.04f`

### 3.5 GraphInteractionPolicy.kt — 类型迁移

- `GraphGesturePhase` enum 定义删除（迁移到 GraphCanvasInteractionState.kt）
- `GraphGestureState` 仍引用 `GraphGesturePhase`（同包，编译不受影响）
- `GraphPoint` 定义保留在此文件（未改动）

### 3.6 GraphCanvasInteractionStateTest.kt — 单元测试

**从 3 个测试扩展到 ~25 个测试，覆盖：**
- §4.1 Selection：node_pressed 选择+开详情、同节点保持、blank_tapped 清除、blank_double_tapped 清除+重置视口
- §4.2 Drag：位置更新、空白ID忽略、累积
- §4.3 Pan & Zoom：pan 累加、zoom clamp(min/max)、ViewportSet、FitRequested
- §4.4 Detail：需选择才能打开、关闭后保持选择
- Search：toggle open/close、query 更新、结果选择不开详情
- Mode：enter/exit canvas
- Help：toggle
- Reconcile：清除已删除节点的 selection/detail、保留有效节点
- Pointer：down→pressed、up→idle
- Capability：空白ID忽略
- 原有测试重命名适配新 API

### 3.7 GraphWorkspaceGestureContractDeviceTest.kt — 设备测试适配

- 新增 `graphPointDistance` helper（`GraphPoint` 无 `getDistance()`）
- 两处 viewport pan 比较从 `(before.pan - after.pan).getDistance()` 改为 `graphPointDistance(before.pan, after.pan)`

### 3.8 native-graph-source-fidelity-validation.md — 验收文档

新增 2026-08-20 段落：
- 描述交互状态收敛：从"画布局部镜像+页面局部状态"→ `GraphCanvasInteractionState` 唯一持有
- 记录脉动动画导致模拟器无法空闲的问题及修复
- 设备测试结果：`GraphInteractionContractDeviceTest: OK (4 tests), 9.459s`（emulator-5554, Pixel_8_Pro, Android 16）
- 注明视觉审阅仍需用户主观确认

## 四、依赖完整性验证

| 引用 | 定义位置 | 是否在改动范围内 | 状态 |
|------|----------|-----------------|------|
| `GraphPoint` | `GraphInteractionPolicy.kt` | 否（仅删除了 GraphGesturePhase，GraphPoint 保留） | ✅ 可用 |
| `GraphSemanticMode` | `KnowledgeGraphModels.kt` | 否（未改动） | ✅ 可用 |
| `GraphLayoutNode` | `KnowledgeGraphModels.kt` | 否（未改动） | ✅ 可用 |
| `withSelection()` | `KnowledgeGraphUiState` in `KnowledgeGraphModels.kt` | 否（未改动） | ✅ 可用 |
| `GraphNodeKind` | `core.model` | 否（冻结层） | ✅ 仅 import |
| `graphKindFillArgb()` | `KnowledgeGraphPanel.kt` 内 | 是（在改动文件内，非新增引用） | ✅ 可用 |

## 五、验收边界确认

### 5.1 契约合规性
- ✅ 节点/边/证据仍只来自 `KnowledgeGraphUiState`，交互状态不引入拓扑
- ✅ 无静态 demo 数据写入生产图谱
- ✅ evidence 不存在时不显示伪入口（`graphEvidenceCapability` 逻辑保留）
- ✅ 纯函数 reducer，状态转换语义明确
- ✅ 搜索结果只定位不打开详情（避免遮挡工具栏）
- ✅ 详情关闭后保留选择（用户可继续操作同一节点）
- ✅ ReconcileNodes 清理无效 selection/detail（节点删除后状态收敛）

### 5.2 冻结层合规性
- ✅ core/model 无 diff
- ✅ core/protocol 无 diff
- ✅ core/llm 无 diff
- ✅ core/data 无 diff
- ✅ Room / DAO / Migration / SecretStore 不在变更列表

### 5.3 测试覆盖
- ✅ 单元测试 25 个，覆盖所有新事件和 reducer 分支
- ✅ 设备测试 4 个通过（模拟器，2026-08-20）
- ✅ 设备测试已适配 GraphPoint 类型变更
- ⚠️ 视觉审阅仍需用户主观确认（文档已注明）

### 5.4 潜在风险

1. **视口双向同步**：`LaunchedEffect(gestureState)` 发射 `ViewportSet` → interactionState.viewport 变化 → `LaunchedEffect(controlledViewport)` 回写 gestureState。有防循环 guard（`if scale != ... || pan != ...`），但极端时序下仍有理论上的抖动风险。**建议**：在设备测试中快速连续缩放+平移后验证 composeRule.waitForIdle() 能收敛。

2. **视觉改动超出声明边界**：KnowledgeGraphPanel.kt 和 GraphCanvasPresentation.kt 包含视觉呈现改动（渐变边线、径向渐变节点、脉动动画、双层选择环、星点数量调整、卡片阴影）。用户声明"不负责重新设计视觉效果"，但这些改动已存在于工作树中。**建议**：在交给 Aily 的执行计划中明确标注这些视觉改动的来源和审批状态。

3. **CRLF/LF 混合**：多个文件有 CRLF 警告。与已知仓库问题一致，不影响编译，但可能在 diff 审阅时产生噪音。

4. **Closing 状态未使用**：`GraphDetailState.Closing` 已定义但 reducer 中无任何分支产出此状态。可能是预留的关闭动画中间态。**建议**：如暂不实现关闭动画，考虑移除以避免死代码；如计划实现，在执行计划中标注。

## 六、交给 Aily 的正式执行计划

### 前置条件
- 工作树改动保持现状，不提交、不重置
- Aily 在此基础上做视觉呈现层面的审阅和调整

### 执行步骤

**Step 1：视觉审阅（Aily 主导）**
- Aily 审阅 KnowledgeGraphPanel.kt 中的视觉改动（渐变边线、径向渐变节点、脉动动画、双层选择环、星点、卡片阴影）
- 确认或调整 GraphCanvasPresentation.kt 中的 7 个新常量值
- 如需调整，仅修改视觉参数和绘制逻辑，不触碰交互状态模型和事件契约

**Step 2：设备回归（共同执行）**
- 在模拟器或真机上运行 `GraphInteractionContractDeviceTest`（4 个测试）
- 快速连续缩放+平移后验证 `composeRule.waitForIdle()` 收敛
- 搜索命中后验证画布可空闲（已知修复点，需复验）
- 测试后卸载 instrumentation 包，恢复主应用前台

**Step 3：Closing 状态决策**
- 确认 `GraphDetailState.Closing` 是否需要实现关闭动画
- 如不需要 → 移除该分支（避免死代码）
- 如需要 → 在 reducer 中增加 Closing → Closed 的定时或动画驱动转换

**Step 4：提交准备（本地开发搭档执行）**
- 确认所有测试通过后
- 按 GraphCanvasInteractionState → FormalKnowledgeGraphScreens → KnowledgeGraphPanel → GraphCanvasPresentation → GraphInteractionPolicy → Test → DeviceTest → Docs 的顺序准备提交
- 提交信息需注明：交互状态收敛、事件驱动架构、纯 Kotlin 状态模型、冻结层零修改

### 约束
- Aily 只负责视觉呈现层面的审阅和调整
- 不修改 GraphCanvasInteractionState.kt 中的状态模型、事件定义和 reducer 逻辑
- 不修改冻结层（core/model, core/protocol, core/llm, core/data, Room, DAO, Migration, SecretStore）
- 不写入静态 demo 数据
- 动画必须可收敛（`animateFloatAsState` 或等价方式，禁用无限循环动画）
- 测试后卸载 instrumentation 包并恢复主应用前台
