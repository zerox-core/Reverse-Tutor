# Native Graph Source Fidelity 验收记录

日期：2026-08-19  
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
| 主力真机 `9CN0223C27017326` | `not_run` | 本轮 ADB 未发现该 serial，不能用模拟器结果替代 Android 12 证据 |

主力真机接入后必须单独重跑 `Phase5GraphDeviceTest`，并继续关注 `RT-2026-004` 的 Android 12 证据缺口。

## 契约与冻结层检查

- `KnowledgeGraphUiState`、`GraphLayoutNode`、`GraphLayoutEdge`、`GraphInteractionPolicy` 签名未修改。
- `mobile-native/core/model`、`core/protocol`、`core/llm`、`core/data` 无 diff。
- 未加入参考源码、demo graph 数据、API key、Provider URL 或用户消息内容。

## 后续门槛

1. 主力真机接入后重跑同一图谱矩阵，单独记录 Android 12 结果。
2. 在两台设备各保存初始态、选中态、搜索态、帮助态截图，与 `F:\xw\.design-review` 的源码页面截图逐项比对。
3. 设备证据齐全后，再更新 P6/PWA 退出决策；模拟器通过不得单独关闭 Android 12 阻塞项。
