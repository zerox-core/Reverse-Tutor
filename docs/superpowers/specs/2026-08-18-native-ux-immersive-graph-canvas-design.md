# NATIVE-UX 图谱沉浸式画布视觉设计

## 目标

在不修改图谱数据、关系语义、布局算法、Repository、协议或 Room 的前提下，将 Context Hub 的知识图谱改造成浅色“知识星图”沉浸式画布：节点与关系是首要内容，详情与操作成为按需浮层。

## 已确认决策

- 视觉方向：浅色学习风格，不采用深色宇宙主题。
- 交互重点：节点和关系优先，画布占据主要屏幕空间。
- 数据边界：继续消费现有 KnowledgeGraphUiState、GraphLayoutNode、GraphLayoutEdge。
- 算法边界：不替换 HierarchicalGraphLayout，不重新计算节点关系，不改图谱 Repository。
- 设备范围：emulator-5554 与主力真机 9CN0223C27017326；不虚构其他设备证据。

## 契约与所有权

本切片属于 Track B 表现层，修改范围限定为：

- mobile-native/feature/memory 中的 Compose Screen、纯 UI 组件和视觉状态映射；
- 必要时新增同模块的 Canvas/Overlay/Controls 文件；
- core:design 中已有 token/icon 的消费，不修改冻结模块。

Track B 只能通过不可变 UI 状态和语义回调消费能力。以下内容保持不变：

- core:model、core:protocol、core:llm、core:data、Room、SecretStore；
- GraphRepository 的方法和返回语义；
- GraphNodeReviewAction、证据动作、聊天/来源跳转的业务含义；
- GraphLayoutNode 中的业务字段与节点 ID。

现有 KnowledgeGraphPanel 是视觉 hotspot：本轮由 Track B 单独负责，Track A 不同时编辑该文件。若拆分组件，先在 Track B 内完成文件拆分，再分别认领。

## 视觉结构

### 1. 画布层

- 画布使用全宽、稳定的有限高度容器，避免在外层滚动容器中再叠加垂直滚动。
- 背景采用低对比的浅色底与极轻网格/渐变，不使用影响节点辨识的强纹理。
- 边默认细、低透明度；选中节点的邻接边提升透明度和宽度。
- 节点按现有 kind、status 映射颜色与形状；不把 UI 颜色写回业务模型。
- 节点保持可点击与可拖拽的现有语义，触控命中区至少 48dp。

### 2. 顶部层

- 返回入口。
- 当前图谱范围/上下文名称。
- 图例入口；图例解释颜色和状态，不新增业务状态。
- 不放置高频操作按钮，避免压缩画布。

### 3. 底部控制层

- 缩小、放大、适配视图。
- 筛选入口只改变本地展示过滤，不改变 Repository 查询。
- “浏览节点”入口作为无障碍和大图谱的替代通道。
- 控件有明确语义标签和 48dp 触控区。

### 4. 选中与详情浮层

- 选中节点放大并显示光环，其他节点适度降噪。
- 详情以轻量浮层出现，包含标题、类型、状态、证据数量和已有语义操作。
- 查看聊天证据、查看来源证据、编辑/审查仍调用现有回调，不在 UI 中拼装 Repository 调用。
- Archive/Hide 继续走现有确认策略，不能通过新视觉入口绕过确认。

### 5. 空、错、超大图状态

继续消费现有 GraphRenderStatus 与 GraphPresentation：

- Empty：提供创建证据/浏览建议；
- Error：提供重试；
- Invalid：提供审查入口；
- Large：优先显示浏览节点入口，避免一次性渲染全部细节。

## 文件拆分建议

首选拆分为以下 Track B 文件，降低后续视觉迭代冲突：

- KnowledgeGraphPanel.kt：对外入口、状态连接、语义事件转发；
- GraphCanvas.kt：画布、边、节点绘制与命中测试；
- GraphCanvasControls.kt：缩放、适配、筛选和图例；
- GraphNodeDetailOverlay.kt：选中详情和操作浮层；
- GraphCanvasPresentation.kt：颜色、尺寸、透明度和状态到视觉 token 的纯映射。

拆分不改变公开 Composable 的业务回调签名；若需要新增回调，优先新增 UI 事件类型并保持旧参数默认值兼容。

## 测试与验收

### JVM

- 保持既有 KnowledgeGraphModelsTest、GraphInteractionPolicyTest、确认动作测试全部通过。
- 新增纯映射测试：节点类型/状态映射稳定、选中态只影响视觉属性、筛选不改变原始节点关系。
- 新增命中测试：节点命中区包含 48dp 目标，边缘点击不误选相邻节点。

### Compose/设备

两台目标设备均验证：

1. 画布首屏可见，节点/边不被外层滚动裁切；
2. 点击节点显示选中光环与详情浮层；
3. 缩放、适配视图、筛选和浏览节点可操作；
4. Archive/Hide 仍弹确认，取消无副作用；
5. Chat/Source 证据入口回调携带同一节点 ID；
6. 深色系统设置、大字号和 TalkBack 的语义标签不丢失。

必须针对已知 RT-2026-003/004：

- 不新增嵌套 verticalScroll；
- 不用延长等待替代状态同步；
- Android 12 真机的证据节点 sourceMessageId 必须与画布选中节点一致；
- 模拟器通过不能替代真机证据。

## 非目标

- 不实现新的图谱算法、力导向布局、语义聚类或自动推理；
- 不修改后端/Python/PWA；
- 不把 API Key、Provider 信息或网络错误原文显示在图谱 UI；
- 不在本切片实现自动模型切换或图谱数据迁移。

