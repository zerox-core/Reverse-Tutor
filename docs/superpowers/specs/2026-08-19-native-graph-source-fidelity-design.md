# Native Graph Source-Fidelity Design

**日期：** 2026-08-19  
**状态：** Draft — waiting for user review  
**参考源码：** `C:/Users/Lenovo/Downloads/app_17cafqazgr2.zip`

## 目标

将参考源码中的 `KnowledgeGraphPage` / `GraphCanvas` 视觉效果逐项迁移到 native Android 图谱页。目标是复刻源码的画布构图、颜色、光晕、边线透明度、语义缩放、搜索/相机工具栏、选中态、邻域淡化、节点详情卡和帮助提示；只将桌面指针输入转换为 Android 触摸输入。

## 参考基准

视觉基准来自参考源码运行页面，而不是此前制作的简化示意图。基准状态必须包含：

1. 初始态：`#F7F8FA` 背景、150 个低透明度星点、六类节点图例、右上五个工具按钮、中心力导向节点群、底部操作提示。
2. 选中态：单个节点保留高亮，其余节点与边降低透明度；节点显示标签；右下出现半透明白色详情卡，包含类型、重要度条、`查看关联` 与 `聚焦节点`。
3. 搜索态：右上展开搜索框；命中后聚焦首个节点并高亮一阶邻域；未命中显示安全提示。
4. 帮助态：左下展开交互说明面板。

参考源码文件：

- `src/pages/KnowledgeGraphPage/KnowledgeGraphPage.tsx`
- `src/graph/GraphCanvas.tsx`
- `src/graph/GraphRenderer.ts`
- `src/graph/GraphInteraction.ts`
- `src/graph/GraphCamera.ts`
- `src/graph/GraphConfig.ts`
- `src/graph/graphTypes.ts`

## 架构边界

本任务只改 `mobile-native/feature/memory` 的表现层与测试，不改：

- `mobile-native/core/model`
- `mobile-native/core/protocol`
- `mobile-native/core/llm`
- `mobile-native/core/data`、Room schema/DAO/migration
- Repository、SecretStore、图谱数据生成与持久化语义

原生页面继续消费 `KnowledgeGraphUiState`、`GraphLayoutNode`、`GraphLayoutEdge`、`GraphInteractionPolicy`。参考源码中的 `DEMO_GRAPH_DATA` 只能用于视觉基准和测试 fixture，不能进入生产页面。

## 表现层映射

| 参考源码 | Native 承载 | 迁移规则 |
|---|---|---|
| `GraphRenderer` | `FormalGraphCanvas` 的 DrawScope | 保持边先于节点绘制、背景星点、节点光晕、选中标签与邻域淡化；不引入 Web Canvas 或 d3 依赖 |
| `GraphCamera` | `GraphViewportState` 与现有手势 reducer | 保留缩放范围、以触点为中心缩放、适应画布、聚焦节点；不改变 state/Repository |
| `GraphInteraction` | 现有 Compose pointer input + `GraphInteractionPolicy` | 单指拖动画布/节点、双指缩放；点击选中；长按或明确详情按钮打开节点；不依赖 hover |
| `NODE_TYPE_COLORS` | `GraphCanvasPresentation` | 通过 `GraphNodeKind` 映射颜色；不修改 domain enum wire value |
| `importance` | 纯表现层派生值 | 当前 domain 没有权威 importance；用节点连接度/状态计算展示值，不持久化、不作为业务排序依据 |
| `strength` | 关系绘制表现 | 当前 `GraphLayoutEdge` 没有 strength；默认边保持统一宽度，选中节点的一阶关系只改变透明度 |
| 右下 Node Info | 现有 `FormalGraphNodeSheet` | 视觉采用半透明白色、圆角、轻阴影；内容必须保留证据入口、状态和审核动作 |
| 图例/工具栏/帮助 | `FormalGlobalKnowledgeGraphScreen` overlay | 视觉位置和层级跟随源码；Android 可见图标保持 24dp，点击区域至少 48dp |

## 视觉令牌

从源码 `GraphConfig.ts` 和 `graphTypes.ts` 固定以下值，后续调参必须有截图证据：

- 背景：`#F7F8FA`
- 节点类型色：core `#3B82F6`、knowledge `#22B8CF`、project `#8B5CF6`、resource `#84B547`、person `#F59E0B`、special `#FF6B61`
- 边：RGB `100,116,139`，默认透明度 `0.16`，邻域高亮透明度 `0.70`
- 节点半径：重要度映射 `2.5..18`；Android 触摸命中半径独立扩大，不改变视觉半径
- 选中：节点放大 `1.15`，选中光晕最高透明度 `0.25`，标签保持清晰
- 语义缩放：`0.4 / 0.7 / 0.9 / 1.6` 四个源码阈值；原生实现可因 dp 与 viewport 做等比例换算，但阈值顺序不可改变
- 动效：源码 180ms 高亮过渡、2600ms 呼吸周期；页面首帧不播放入场动画

## Android 平台等价交互

视觉结构保持一比一，输入方式做必要的等价转换：

- 鼠标 hover → 最近一次触摸选择态；不新增悬浮专属业务动作
- 滚轮缩放 → 双指 pinch；工具栏按钮提供显式缩放
- 拖拽空白 → 单指平移画布
- 拖拽节点 → 单指移动节点；释放后遵循现有位置策略
- 单击节点 → 选中并打开现有详情 Sheet
- 双击打开节点 → Android 使用双击/长按等价入口，但必须保持明确的 `onOpenChatEvidence` / `onOpenSourceEvidence` 回调
- 点击空白 → 清除选择

页面模式仍由现有 `canvasModeActive` 控制，避免在外层滚动容器中叠加第二个垂直滚动器。该约束对应已知问题 `RT-2026-003`。

## 数据与安全

- 生产节点和边始终来自当前 `KnowledgeGraphUiState`。
- 不把参考源码中的英文 demo 节点、重要度或类型名写入 Room。
- 不把 API key、Provider、URL 或用户消息内容放入图谱视觉日志、截图说明或测试输出。
- 选中详情仍必须显示现有证据入口与状态，不得因为视觉复刻而删除审核确认或安全失败状态。

## 测试与验收

### JVM

- 扩展 `GraphCanvasPresentationTest`：类型颜色、状态边框、选中放大/透明度、重要度派生值和语义缩放阈值。
- 保持现有 `KnowledgeGraphUiState`、`GraphInteractionPolicy` 和审核确认测试全部通过。

### 设备

在 `emulator-5554` 和主力真机 `9CN0223C27017326` 分别验证：

1. 初始态与源码基准的视觉层级。
2. 节点选择后的邻域淡化、标签和详情 Sheet。
3. 搜索命中/未命中、适应画布、缩放、平移、节点拖动。
4. 节点列表、证据跳转、审核确认仍可用。
5. 深色/大字号/48dp 触控区不回归。

模拟器通过不得代替主力真机证据；Android 12 的 `RT-2026-004` 必须单独记录，不得用视觉改动掩盖。

### 视觉验收门槛

- 每个基准状态各保留一张参考源码截图与一张 Android 截图。
- 逐项核对背景、色板、节点密度、边透明度、光晕、工具栏位置、详情卡层级和文字层级。
- 任何无法一比一复刻的差异必须在验收报告中明确写出原因；未经用户确认不得自行换成另一种设计。

## 不在本轮范围

- 修改图谱抽取算法、布局算法的业务语义或 Repository。
- 将 Web/d3-force 代码直接复制到 Android。
- 添加新的 GraphNode/GraphEdge 协议字段。
- 修改 Context Hub 的证据链、审核动作或冻结层。

