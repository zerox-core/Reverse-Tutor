# Native Graph Source Fidelity 验收记录

日期：2026-08-19（收尾复验）  
分支：`Android`  
实现提交：`65b481a`

## 范围

本轮只修改 `feature:memory` 表现层和 Android 图谱契约测试，未修改 Repository、协议、数据库、Room、SecretStore、图谱抽取或持久化语义。生产节点与关系仍由 `KnowledgeGraphUiState` 提供；搜索只在当前状态的节点 id/label 上匹配。

## 已实现

- 源码背景、六类节点色板、边透明度、邻域淡化、选中光晕和语义缩放令牌。
- 全屏画布左上图例、右上搜索/缩放/适应/帮助工具栏、左下交互帮助浮层。
- 搜索浮层：当前状态本地匹配、命中结果选择、无结果安全提示；不直接访问 Room。
- 选中节点详情保留证据入口、审核/归档确认，并增加展示层重要度条。
- 工具栏按钮保持至少 48dp 点击区，并置于画布手势层之上。
- 旧 `KnowledgeGraphPanel` 的筛选入口和“回到中心”无障碍描述保持兼容。

## 自动化证据

模拟器：`emulator-5554`（Pixel_8_Pro，Android 16）

```text
GraphInteractionContractDeviceTest + Phase5GraphDeviceTest: OK (9 tests)
```

覆盖：画布进入、源码图例、工具栏尺寸、搜索打开、搜索命中并选择节点、帮助面板、空图、节点详情、证据入口、审核破坏性操作确认、筛选入口和错误/空状态恢复。

构建与 JVM 回归：

```text
:feature:memory:testDebugUnitTest :app:test :app:lint :app:assembleDebug  -> BUILD SUCCESSFUL
:app:assembleDebugAndroidTest                                  -> BUILD SUCCESSFUL
git diff --check                                               -> clean
冻结路径 diff                                                   -> empty
```

## 设备矩阵状态

| 设备 | 图谱交互 | 说明 |
|---|---|---|
| Pixel_8_Pro 模拟器 `emulator-5554` | ✅ 9/9 | 本轮真实安装 APK 后执行 |
| 主力真机 `9CN0223C27017326` | ✅ 9/9 | Android 12 / API 31；本轮更新主 APK 与测试 APK 后完整复跑 |

主力真机本轮已完整重跑 `GraphInteractionContractDeviceTest`（4/4）和
`Phase5GraphDeviceTest`（5/5），`RT-2026-004` 已关闭。详情卡未引入内部滚动，避免重现
`RT-2026-003`。

真机附加证据：

```text
GraphInteractionContractDeviceTest: OK (4 tests)
Phase5GraphDeviceTest: OK (5 tests)
```

本轮第一次模拟器运行发现旧测试仍要求已从沉浸式画布移除的巨大
`graph-canvas-mode-exit` 浮层（8/9）；已将测试契约改为验证页面模式提示消失、旧浮层不再
存在、以及“回到中心”控制仍可用，随后模拟器和主力真机均达到 9/9。未恢复遮挡内容的旧
浮层。

## Debug 结构化图谱场景补充验收

本轮新增的 Debug-only seeder 只位于 `app/wiring`，由 `MainActivity` 在
`BuildConfig.DEBUG` 时异步触发；Release 不会写入这组数据。它通过现有
Repository 写入固定 fixture，不修改 Room、DAO、schema、Repository 签名、协议或图谱算法。

场景数据：4 个会话、8 条消息、8 个证据锚点、20 个可见节点、1 个隐藏幂等标记和
25 条边。包含 Python、知识图谱、百炼 API 与阶段复盘四个会话根节点；所有边端点和
所有证据反向引用均受测试约束。

本轮真实证据：

```text
主力真机 9CN0223C27017326（Android 12）：
DebugGraphScenarioSeedDeviceTest: OK (1 test, 0.387s)

Pixel_8_Pro 模拟器 emulator-5554（Android 16）：
DebugGraphScenarioSeedDeviceTest: OK (1 test, 2.157s)
GraphInteractionContractDeviceTest + Phase5GraphDeviceTest: OK (9 tests)
```

Android 真实数据库测试确认：四个固定会话存在、21 个节点（含隐藏 marker）、25 条边、
8 个 memory item、全部边端点有效；第二次注入结果为 `AlreadySeeded` 且数量不变。
模拟器重新启动主应用后，首页仍显示结构化会话；进入“全局图谱”并切换画布模式后，
已人工确认“Python 入门路线”“知识图谱算法”“百炼 API 接入”等节点及跨会话连线可见。

本轮主力真机已重新接入并完成 9 项图谱矩阵，故该项不再待补。

## 契约与冻结层检查

- `KnowledgeGraphUiState`、`GraphLayoutNode`、`GraphLayoutEdge`、`GraphInteractionPolicy` 签名未修改。
- `mobile-native/core/model`、`core/protocol`、`core/llm`、`core/data` 无 diff。
- 未加入参考源码、demo graph 数据、API key、Provider URL 或用户消息内容。

## 后续门槛

1. 在两台设备各保存初始态、选中态、搜索态、帮助态截图，与 `F:\xw\.design-review` 的源码页面截图逐项比对。
2. 设备证据齐全后，再更新 P6/PWA 退出决策；本轮已同时取得模拟器和 Android 12 真机证据。
