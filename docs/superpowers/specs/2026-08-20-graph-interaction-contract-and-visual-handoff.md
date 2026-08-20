# Reverse Tutor 图谱交互契约与 Aily 视觉适配规格

日期：2026-08-20  
状态：已确认，作为本轮图谱最终设计输入  
范围：`mobile-native/feature/memory` 图谱交互层、图谱页面表现适配、`app/shell` capability 路由  
协作者：Codex（交互契约与代码边界）/ Aily（视觉重设计与 Compose 表现实现）

## 0. 最终决策

本轮不再继续堆设备演练，也不再围绕现有画面做零散修补。图谱模块进入一次“交互契约锁定 + 视觉表现重做”阶段：

- Codex 负责交互状态、事件语义、手势仲裁、命中规则、详情生命周期、证据 capability、代码所有权和测试契约。
- Aily 负责节点/边/详情/工具栏/图例/动效/空状态的视觉重新设计，并在不破坏本规格契约的前提下适配实现。
- 节点、边、证据和状态仍只来自现有 `KnowledgeGraphUiState`；不得用静态节点、截图、演示数据替代真实状态。
- 不修改 `core:model`、`core:protocol`、`core:llm`、`core:data`、Room、DAO、migration、SecretStore、Repository 签名或图谱领域算法。
- 本规格是本轮最后一次图谱设计输入。视觉验收若仍不达标，直接将图谱模块标记为 `deferred`，暂停继续开发，不再追加第三套 UI。

## 1. 当前问题定义

当前图谱“有数据但不好用”，问题核心不是测试缺少，而是交互模型未形成稳定闭环：

1. 点击节点、拖动节点、平移画布和缩放画布共享同一手势层，操作意图容易互相抢占。
2. 选中节点由页面回调维护，拖动位置由画布局部状态维护，详情由另一个局部状态维护，三者可能短暂不同步。
3. 节点拖动后，节点、关联边、邻域高亮和详情更新缺乏同一帧内的统一来源。
4. 详情打开/关闭后，画布视口、选中节点和临时位置覆盖的保留规则不够明确。
5. 搜索命中、节点选择和证据跳转之间缺少明确的状态转换；“找到”不等于“选中”，更不等于“打开正确证据”。
6. 视觉层可以更换，但当前代码没有给视觉重做提供足够窄且稳定的适配接口。

## 2. 不可破坏的架构边界

数据流必须保持单向：

```text
GraphRepository snapshot
        ↓
KnowledgeGraphUiState                 ← 领域节点、边、状态、证据唯一来源
        ↓
GraphCanvasInteractionState            ← 仅保存表现和交互状态
        ↓
GraphCanvasInteractionController      ← 纯 Kotlin 事件归约与 capability 输出
        ↓
GraphCanvasRenderer                    ← Aily 可重做的视觉层
        ↓
AppShell capability router             ← 聊天/来源/返回路由
```

### 2.1 领域层禁止事项

- 不在 UI 中创建替代真实数据的 `GraphNode`、`GraphLayoutNode` 或 `GraphLayoutEdge`。
- 不在 UI 中直接访问 DAO、Entity、Database、SecretStore 或 Repository。
- 不将节点拖动坐标写入 Room 或图谱领域模型。本轮位置是进程内表现状态。
- 不为了视觉展示修改图谱算法、聚合规则、边过滤规则或 Debug seeder。
- 不把 API key、Provider URL、用户消息全文写入图谱详情、测试断言、日志或截图。

### 2.2 文件责任边界

| 文件/区域 | 唯一责任 |
|---|---|
| `GraphCanvasInteractionState.kt` | 纯 Kotlin 状态、事件、reducer、capability；不可依赖 Android UI |
| `FormalGraphCanvas` 或拆分后的 renderer 文件 | 绘制节点/边、报告指针事件、提供语义目标；不可自行决定路由 |
| `FormalGlobalKnowledgeGraphScreen` | 组合状态与 renderer，连接 reducer；不可拼装 Repository 调用 |
| `ContextHubScreen.kt` / `GlobalGraphRoute` | 获取已有快照并转换为 `KnowledgeGraphUiState`；不可保存视觉状态到领域层 |
| `AppShell.kt` | 处理 capability 到 Chat/Sources 的路由和返回目标；不可实现图谱手势 |
| `GraphCanvasInteractionTest` / AndroidTest | 锁定交互契约；不以截图相似替代行为断言 |

如果单文件同时包含 reducer、renderer 和 route 装配，先拆分；拆分前指定单一 owner，禁止 Aily 与 Codex 同时编辑同一热点文件。

## 3. 交互状态模型

交互状态必须能由一个对象完整描述，不允许把关键状态散落在多个 `remember` 中。

```kotlin
data class GraphCanvasInteractionState(
    val mode: GraphCanvasMode = GraphCanvasMode.Page,
    val selectedNodeId: String? = null,
    val detail: GraphDetailState = GraphDetailState.Closed,
    val viewport: GraphViewportState = GraphViewportState(),
    val nodePositionOverrides: Map<String, GraphPoint> = emptyMap(),
    val search: GraphSearchState = GraphSearchState.Closed,
    val helpOpen: Boolean = false,
    val gesture: GraphGesturePhase = GraphGesturePhase.Idle
)
```

实际命名可以按现有代码调整，但语义必须保持一致。状态定义如下：

- `mode`：普通页面模式或沉浸式画布模式。
- `selectedNodeId`：当前唯一选中节点；空白点击后为 `null`。
- `detail`：关闭、打开某节点、关闭动画中；详情节点必须与 `selectedNodeId` 一致。
- `viewport`：缩放比例、平移偏移、语义缩放模式；不包含领域数据。
- `nodePositionOverrides`：`nodeId → GraphPoint`，只允许当前快照中的节点存在。
- `search`：关闭、打开、输入 query、命中结果；命中结果必须来自当前节点集合。
- `helpOpen`：交互帮助是否打开，不影响节点/边状态。
- `gesture`：空闲、点击候选、拖动节点、平移画布、缩放画布；用于仲裁而不是视觉装饰。

## 4. 事件与状态转换契约

所有交互都归约为事件。视觉层不得直接修改 `mutableStateOf` 中的领域状态。

```kotlin
sealed interface GraphCanvasInteractionEvent {
    data object EnterCanvas : GraphCanvasInteractionEvent
    data object ExitCanvas : GraphCanvasInteractionEvent
    data class PointerDown(val screenPoint: Offset) : GraphCanvasInteractionEvent
    data class NodePressed(val nodeId: String, val screenPoint: Offset) : GraphCanvasInteractionEvent
    data class NodeDragged(val nodeId: String, val graphPoint: GraphPoint) : GraphCanvasInteractionEvent
    data object PointerUp : GraphCanvasInteractionEvent
    data object BlankTapped : GraphCanvasInteractionEvent
    data object BlankDoubleTapped : GraphCanvasInteractionEvent
    data class PanChanged(val delta: Offset) : GraphCanvasInteractionEvent
    data class ZoomChanged(val scale: Float, val focalPoint: Offset) : GraphCanvasInteractionEvent
    data class SearchQueryChanged(val query: String) : GraphCanvasInteractionEvent
    data class SearchResultChosen(val nodeId: String) : GraphCanvasInteractionEvent
    data object DetailOpened : GraphCanvasInteractionEvent
    data object DetailClosed : GraphCanvasInteractionEvent
    data object FitRequested : GraphCanvasInteractionEvent
    data object HelpToggled : GraphCanvasInteractionEvent
    data class ReconcileNodes(val ids: Set<String>) : GraphCanvasInteractionEvent
}
```

### 4.1 选择

- 节点点击只选择一个节点，并立即更新 `selectedNodeId`。
- 选择节点后，邻域高亮由当前节点和当前边集合计算，不由 renderer 自己猜测。
- 同一节点再次点击可以保持选择，不得误触发详情关闭。
- 空白单击清除选择并关闭详情；空白双击清除选择并执行 `FitRequested`。
- 选择已不存在的节点时，reducer 必须将选择和详情清空，而不是显示陈旧详情。

### 4.2 拖拽

- 节点拖拽开始前必须先命中节点；节点拖拽优先级高于画布平移。
- 拖拽过程中，节点位置写入 `nodePositionOverrides`，关联边实时使用覆盖后坐标绘制。
- 拖拽结束后保留位置覆盖，直到节点离开快照、用户执行重置布局或页面生命周期结束。
- 拖拽不改变节点领域状态、标签、证据或边关系。
- 拖拽阈值必须大于点击抖动阈值；未超过阈值的按下/抬起仍视为点击。

### 4.3 平移与缩放

- 空白区域单指拖动才平移画布；节点区域拖动不平移画布。
- 双指缩放只修改 `viewport.scale` 和以焦点为中心的平移，不改变节点相对关系。
- 缩放范围必须有限且连续，禁止跳变或反向缩放。
- `FitRequested` 将当前可见节点和边整体放入安全可视区域；空图时不产生 NaN/无穷坐标。
- 工具栏按钮、搜索框、详情面板和图例必须位于手势层之上，拥有独立语义点击目标。

### 4.4 详情

- 详情只能由当前 `selectedNodeId` 打开，不能由视觉组件自行传入一份复制节点。
- 详情显示节点标题、种类、状态、关系摘要和真实 evidence capability。
- 详情打开期间，画布选择和视口状态保留；关闭详情后仍保持原节点选中，除非用户明确清除。
- evidence 不存在时不显示伪入口；存在 message evidence 才输出 `OpenChatEvidence(messageId)`，存在 source evidence 才输出 `OpenSourceEvidence(sourceId)`。
- 路由返回后恢复同一个 `GraphCanvasInteractionState`，不得重新生成一套静态节点。

## 5. 手势仲裁顺序

为避免“点击、拖拽、平移、缩放互相抢事件”，实现必须遵循此优先级：

1. 语义控件命中：工具栏、搜索、详情按钮、图例、返回按钮优先消费事件。
2. 多指手势：进入缩放/平移组合态，禁止触发节点点击。
3. 节点命中：先进入点击候选；超过拖拽阈值后升级为节点拖拽。
4. 空白单指：超过平移阈值后进入画布平移；未超过则触发空白点击。
5. 空白双击：执行清除选择 + 适配画布。

不得用“全屏 pointer input 覆盖后再靠坐标猜测按钮”实现。视觉层可以自定义触摸反馈，但必须保留可测试的语义 action 和稳定 test tag。

## 6. 给 Aily 的视觉适配接口

Aily 可以完全重做以下表现，不受当前颜色、卡片形状、背景、节点图形和动画限制：

- 节点形状、尺寸、标签排版、状态徽标、选中光晕、拖拽反馈；
- 边的颜色、宽度、箭头、标签、选中/非选中透明度；
- 画布背景、网格、粒子、渐变、阴影和安全区；
- 顶部栏、工具栏、搜索面板、图例、帮助面板；
- 详情面板布局、展开方式、证据入口样式和返回动效；
- 空图、错误图、加载图、单节点图和大图的表现；
- 进入画布、选中、拖动、缩放、详情开关的动效。

Aily 不得改变以下接口语义：

- 事件名称和状态含义；
- 节点/边来自 `KnowledgeGraphUiState` 的单向数据流；
- `nodeId`、`sourceMessageId`、`sourceId` 等真实标识的传递；
- capability 只由 `feature:memory` 输出、由 `app/shell` 路由；
- 语义控件最小 48dp 点击区；
- 不新增对冻结层的直接依赖；
- 不以静态 fixture 代替真实 state。

Aily 交付给 Codex 的最小接口说明必须包含：

1. 视觉组件接收的 state 字段；
2. 视觉组件发出的 interaction event；
3. 每个交互控件的 test tag/content description；
4. 节点、边、详情、工具栏的 z-index/手势层关系；
5. 动效开始/结束时 state 是否改变；
6. 空数据、错误、长标题、超大图的降级表现。

## 7. 视觉验收不是截图相似，而是交互可用性

最终验收只保留一次最小设备验证，不再进行多轮大规模矩阵：

### 7.1 模拟器最小闭环

- 真实 Debug 图谱进入画布；
- 点击一个节点并打开详情；
- 拖动同一节点，确认边跟随；
- 缩放和平移，确认节点不跳变；
- 搜索一个真实节点并选中；
- 从详情返回，确认节点和视口状态保留；
- 选择无 evidence 节点，确认不显示虚假入口。

### 7.2 主力真机最小闭环

只复验同一条核心路径，不再重复完整旧矩阵。设备测试包运行结束后立即卸载，主应用恢复前台。

### 7.3 代码门禁

- 受影响 JVM 测试通过；
- 一次 Debug 编译通过；
- `git diff --check` 清洁；
- 冻结路径 diff 为空；
- 不得因为测试不稳定而删除断言、延长等待或恢复遮挡性 UI。

## 8. 最终失败处理

视觉验收失败时，必须先归类：

- `contract_failure`：交互状态、事件、路由或真实数据链路错误，继续修复；
- `presentation_failure`：视觉层未达到 Aily 目标，但交互契约正确，允许 Aily 做一次集中修正；
- `architecture_failure`：视觉实现无法在不破坏契约的情况下接入，停止该方案并记录原因。

如果集中修正后仍然属于 `presentation_failure` 或 `architecture_failure`，将图谱模块标记为：

```text
deferred: graph module paused
```

此后不再为图谱添加第三套视觉方案、不再继续堆测试、不再修改冻结层；转做其他模块，待有明确的新设计输入后再恢复。

## 9. 交付清单

### Codex

- [ ] 提供/维护 `GraphCanvasInteractionState`、事件和 reducer；
- [ ] 保证 renderer 与 route 之间只有明确的 state/event/capability 接口；
- [ ] 补齐纯 Kotlin 交互测试；
- [ ] 审查 Aily 改动是否触碰冻结层和 Repository；
- [ ] 执行一次最小设备闭环并记录结果；
- [ ] 失败时按 `contract_failure` / `presentation_failure` / `architecture_failure` 分类。

### Aily

- [ ] 依据本规格重做图谱视觉，不复用当前失败的叠层方案；
- [ ] 保证节点选择、拖拽、缩放、平移、详情、搜索和返回状态接入上述事件；
- [ ] 交付视觉组件输入输出说明和稳定语义标识；
- [ ] 不添加静态演示节点、边或截图替代真实状态；
- [ ] 不修改冻结层、Room、Repository 签名或图谱算法；
- [ ] 交付后等待一次最小验收，不要求反复设备演练。

## 10. 关联文件

- 现有交互实现：`mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/GraphCanvasInteractionState.kt`
- 现有画布实现：`mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/KnowledgeGraphPanel.kt`
- 全局图谱组合：`mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/FormalKnowledgeGraphScreens.kt`
- 路由适配：`mobile-native/feature/memory/src/main/java/com/reversetutor/feature/memory/ContextHubScreen.kt`
- Shell capability：`mobile-native/app/src/main/java/com/reversetutor/preview/shell/AppShell.kt`
- 旧版参考：`main` 分支对应图谱实现，仅作交互思路参考，不直接复制耦合代码
- 开发护栏：`F:/CodexHome/skills/reverse-tutor-development-guard/SKILL.md`
