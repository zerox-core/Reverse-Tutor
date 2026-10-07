# Contract-driven Graph Canvas Design

日期：2026-08-19  
状态：待审阅  
范围：Android `feature:memory` 图谱表现层与 `app/shell` 路由适配

## 目标

把当前全局图谱从“有真实数据、但交互状态和视觉叠层不完整”的画布，补齐为可验证的
契约驱动交互界面。画面以当前沉浸式画布为准；交互思路参考 `main` 分支旧图谱的
节点、关系、选中详情、证据链和回退路径。

成功标准不是静态截图相似，而是任何可见节点都来自当前 `KnowledgeGraphUiState`，并能
完成选择、邻域高亮、缩放、平移、拖动、搜索、详情和证据回跳。

## 边界与不变量

- 不修改 `core:model`、`core:protocol`、`core:llm`、`core:data`、Room schema/DAO/migration、
  SecretStore 或 Repository 生产签名。
- 不修改图谱抽取算法、布局算法的领域语义或 Debug 场景数据。
- 图谱页面只消费现有 `GraphRepository` 快照形成的 `KnowledgeGraphUiState`；表现层不得
  额外构造用于替代真实数据的节点和边。
- 画布装饰（背景颗粒、阴影、图例容器）可以是固定表现，但节点、边、搜索结果、详情、
  计数和证据入口必须由状态驱动。
- 节点位置的本地交互状态与领域节点/边分开；未经批准不把位置写回冻结数据层。

## 当前问题与根因

1. `GraphSourceLegend` 以整个全屏 `Box` 的左上角定位，画布模式下覆盖标题区。
2. 画布拖动位置只存在于 `FormalGraphCanvas` 的局部 `GraphGestureState`；离开或刷新后
   会恢复，且 `GlobalGraphRoute` 没有消费位置变更回调。
3. 全局图谱的证据回调只打开目标页面，未携带当前节点对应的 message/source 标识。
4. 图例文案是固定设计词汇，未表达当前图中的真实类别和数量，容易造成静态展示感。
5. 当前点击、搜索、缩放和详情已存在，但其状态分散在屏幕局部状态和画布局部状态，
   缺少一个可单测的页面交互协调层。

## 设计

### 1. 数据和交互边界

保持以下单向数据流：

`GraphRepository snapshot → KnowledgeGraphUiState → GraphCanvasUiState → FormalGraphCanvas`

`GraphCanvasUiEvent → GraphCanvasController → GraphCanvasUiState / route capability`

`KnowledgeGraphUiState` 继续是节点、边、选择和领域证据字段的唯一来源。新增的
`GraphCanvasUiState` 只保存表现状态：viewport、临时节点位置覆盖、搜索查询/面板、帮助
面板、画布模式和选中节点 id。它不复制或修改领域节点、边和证据。

`GraphCanvasController` 是 `feature:memory` 内部的纯 Kotlin 协调器，处理以下事件：

- 进入/退出画布模式；
- 点击节点、点击空白、双击空白适应画布；
- 平移、缩放、长按拖动和节点位置覆盖；
- 打开/关闭搜索和帮助，按当前节点集搜索并选择结果；
- 请求聊天证据或来源证据。

它输出明确的 capability：`OpenChatEvidence(messageId)`、`OpenSourceEvidence(sourceId)`。
`app/shell` 负责把 capability 映射到现有路由；UI 不直接访问 Repository、DAO 或数据库。

### 2. 位置和重建策略

节点拖动写入 `GraphCanvasUiState.positionOverrides`，按 `nodeId → GraphPoint` 保存。相同
全局图谱在旋转、重新组合或从详情返回时保留覆盖；节点从状态中消失时删除对应覆盖。

本切片不把坐标持久化到领域数据层：布局位置不是节点事实，且现有持久化契约没有位置
字段。应用进程重启后回到现有布局算法结果是有意行为。若以后需要跨重启保存位置，另走
冻结层变更申请并定义独立布局偏好契约。

### 3. 顶部安全区和图例

全局图谱屏幕明确划分：

```text
系统栏
└─ 顶部栏：返回 / 标题 / 更多
   └─ 画布内容安全区：图例（左上）             工具栏（右上）
      └─ 画布：节点、边、选择、手势
         └─ 底部：帮助浮层 / 节点详情
```

图例锚定在画布内容区，而不是屏幕根节点：起点位于顶部栏下方并保留 16dp 内边距；搜索面板
从工具栏下方出现；画布模式提示不遮挡图例、标题或工具栏。图例条目按当前可见节点的实际
`GraphNodeKind` 汇总，仅显示出现过的类别，并显示类别名称与数量。

### 4. 节点详情与证据回跳

选择节点后，详情 Sheet 从 `GraphLayoutNode` 和关联边渲染。若节点带 `sourceMessageId`，
发出 `OpenChatEvidence`；若带 `sourceId`，发出 `OpenSourceEvidence`；两者都不存在则不显示
虚假的入口。

`GlobalGraphRoute` 接收 capability 并通过现有 shell 路由进入聊天或来源页面。目标页面先
使用现有选中状态/导航参数定位证据；若现有路由尚不能精确定位，只显示受控的“该证据暂
不能精确定位”状态并记录 capability 缺口，不伪装为成功。

## 验收与测试

### JVM

- Controller：事件状态转移、节点位置覆盖清理、搜索仅命中当前状态节点、证据 capability。
- Legend：只显示当前类别，数量与可见节点一致。
- Layout：图例安全区锚点在顶部栏之后，不与顶部栏或工具栏相交。

### 设备

在 Pixel_8_Pro 模拟器和主力 Android 12 真机各验证：

1. 进入全局图谱，图例不覆盖标题；
2. 点击节点，选中态、邻域高亮和详情一致；
3. 拖动节点后在详情开关、页面重组时位置仍保留；
4. 缩放、平移、双击空白适应画布和工具栏均可用；
5. 搜索真实 Debug 数据节点（如“百炼 API”）并定位/选中；
6. 有证据节点显示正确入口，无证据节点不显示虚假入口；
7. 主应用在测试完成后保持前台。

提交前必须运行受影响 JVM/设备测试、`git diff --check` 和冻结路径 diff 检查。

## 分阶段实施

1. 引入 `GraphCanvasUiState` / `GraphCanvasController` 及 JVM 测试，不改领域契约。
2. 让 `FormalGraphCanvas` 消费协调状态，接通真实事件和位置覆盖。
3. 修复全局图谱安全区、动态图例和 search/help 叠层。
4. 在 route/shell 接通证据 capability，并对不能精确定位的目标显式降级。
5. 两设备回归、截图审阅和验收记录更新。
