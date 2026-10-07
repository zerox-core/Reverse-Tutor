# NATIVE-P6-003 · 双设备回归矩阵

> 创建时间：2026-08-17（周一）
> 执行人：本地开发搭档（feishu_mcp）
> 分支：Android | 基线提交：b6c3307 | 冻结层：零修改
> 参考：`tasks/native-legacy-coverage-registry.md`、`tasks/native-legacy-entry-inventory.md`

---

## 1. 构建与安装基线

### 1.1 Gradle 构建（2026-08-17 本轮执行）

| 任务 | 结果 | 耗时 | 备注 |
|---|---|---|---|
| `:app:test` | BUILD SUCCESSFUL | 28s | 351 actionable tasks: 12 executed, 339 UP-TO-DATE |
| `:app:lint` | BUILD SUCCESSFUL | 8s | 390 actionable tasks: 13 executed, 377 UP-TO-DATE, 无 lint 错误 |
| `:app:assembleDebug` | BUILD SUCCESSFUL | 6s | 264 actionable tasks: 12 executed, 252 UP-TO-DATE |
| `:app:assembleDebugAndroidTest` | BUILD SUCCESSFUL | 6s | 同上（与 assembleDebug 一起执行） |

所有任务 exitCode=0，无编译错误、无测试失败、无 lint 错误。

### 1.2 Python 后端回归

| 任务 | 结果 | 备注 |
|---|---|---|
| `py -m pytest -q --ignore=tests/test_project_homepage.py` | 本轮未完成 | MCP 30s 限制下未取得结果；不得以旧的“320 passed”作为本轮通过证据。最近已记录的完整基线见 P3-004：510 passed、28 skipped；如需 P6 发布证据必须重新执行并记录实际输出。 |

### 1.3 冻结层与代码检查

| 检查项 | 结果 |
|---|---|
| `git diff --check` | 清洁（仅 .md 文件 CRLF/LF 警告） |
| `git diff --name-only -- core/model core/protocol core/llm core/data` | **空** ✅ |

### 1.4 APK SHA-256

| APK | SHA-256 |
|---|---|
| `app-debug.apk` | `541E2669D54FA7B42F169EA4B9677353B750953891606FA109D3DD520008BD5C` |
| `app-debug-androidTest.apk` | `E74F8B97345C6800B3DF39EB20F6A2C03287460EB22641C7427912441255FA2B` |

### 1.5 设备状态

| 设备 | ADB Serial | 状态 | Android 版本 |
|---|---|---|---|
| 模拟器 | `emulator-5554` | 已连接 | Android 16（API 36），Pixel_8_Pro AVD |
| 主力真机 | `9CN0223C27017326` | 已连接 | Android 12（API 31），BRA_AL00 |

---

## 2. 模拟器必测组

设备固定：`emulator-5554`。本轮使用本节列出的 APK 重新安装并执行。

| 测试类 | 测试数 | 本轮状态 | 备注 |
|---|---:|---|---|
| `Phase2CoreLoopDeviceTest` | 3 | pass | 核心闭环、会话隔离与返回导航。 |
| `Phase5ContextHubDeviceTest` | 4 | pass | Context Hub、证据跳转与返回 Chat。 |
| `Phase5GraphDeviceTest` | 5 | pass | Canvas、状态、选择、编辑及 Archive/Hide 确认。 |
| `Phase5MemoryDeviceTest` | 1 | pass | Chat Note → Context Hub 持久化。 |
| `Phase5SourcesDeviceTest` | 1 | pass | 五种解析状态及恢复动作。 |
| `Phase4ImportExportDeviceTest` | 1 | pass | 已通过正式 `ACTION_SEND` 导入入口改为验证中文导入预览、三种模式、导入记录及会话/消息持久化；`emulator-5554` 重测 1/1 通过。 |
| `BackgroundGenerationSessionDeletionDeviceTest` | 1 | pass | 删除会话不写入延迟消息。 |
| `BackgroundGenerationStartupRecoveryDeviceTest` | 1 | pass | 重启恢复并重新交给 WorkManager。 |
| `BackgroundGenerationNotificationDeviceTest` | 1 | pass | 完成/失败通知与安全文案。 |
| `FormalDiagnosticsDeviceTest` | 1 | pass | 诊断记录和剪贴板脱敏。 |

本轮汇总：Phase4 修复并重测后，19 tests run，19 passed。

---

## 3. 主力真机必测组

主力真机：`9CN0223C27017326`（BRA_AL00，Android 12）。已重新安装本节 APK 并执行规定的五个自动化测试类。

| 测试项 | 状态 | 备注 |
|---|---|---|
| 核心闭环（Phase2CoreLoopDeviceTest） | pass | 3 tests passed。 |
| Context Hub | pass | 4 tests passed。 |
| Graph | fail | 5 tests run，2 passed、3 failed；失败可稳定复现，见 §4.5。 |
| Memory | pass | 1 test passed。 |
| Sources | pass | 1 test passed。 |
| 深色模式（手工） | not_run | 自动化不能替代人工视觉检查。 |
| 大字号/动态字体（手工） | not_run | 自动化不能替代人工视觉检查。 |
| IME 键盘遮挡（手工） | not_run | 自动化不能替代真实输入法检查。 |
| 48dp 触摸目标（手工） | not_run | 尚未人工点检。 |
| TalkBack 人工听测 | not_run | 只在主力真机进行；记录焦点顺序，不用 Compose 语义树冒充语音证据。 |

---

## 4. 设备矩阵记录

### 4.1 状态词汇

矩阵只允许以下状态：`pass`、`fail`、`blocked`、`not_run`、`not_available`。

### 4.2 设备矩阵

| 测试类 | emulator-5554 | 主力真机 | 其他设备 |
|---|---|---|---|
| Phase2CoreLoopDeviceTest | pass | pass | not_available |
| Phase5ContextHubDeviceTest | pass | pass | not_available |
| Phase5GraphDeviceTest | pass | fail | not_available |
| Phase5MemoryDeviceTest | pass | pass | not_available |
| Phase5SourcesDeviceTest | pass | pass | not_available |
| Phase4ImportExportDeviceTest | pass | not_run | not_available |
| BackgroundGenerationSessionDeletionDeviceTest | pass | not_run | not_available |
| BackgroundGenerationStartupRecoveryDeviceTest | pass | not_run | not_available |
| BackgroundGenerationNotificationDeviceTest | pass | not_run | not_available |
| FormalDiagnosticsDeviceTest | pass | not_run | not_available |
| 深色模式（手工） | not_run | not_run | not_available |
| 大字号（手工） | not_run | not_run | not_available |
| IME 键盘（手工） | not_run | not_run | not_available |
| 48dp 触摸目标（手工） | not_run | not_run | not_available |
| TalkBack 听测 | not_run | not_run | not_available |

### 4.3 统计

| 统计项 | emulator-5554 | 主力真机 | 其他设备 |
|---|---|---|---|
| 测试总数 | 15 | 15 | 15 |
| pass | 10 | 4 | 0 |
| fail | 0 | 1 | 0 |
| not_run | 5 | 10 | 0 |
| not_available | 0 | 0 | 15 |

### 4.4 记录规则

- 模拟器和主力真机分别记录，不合并为"设备通过"
- 其他设备行统一写 `not_available`，原因写"当前无设备条件"
- 每个失败附日志、测试方法、复现步骤和是否影响生产代码的判断
- 设备测试失败不得通过删断言、放宽等待或改产品文案消除

### 4.5 本轮失败记录（2026-08-17）

1. `Phase4ImportExportDeviceTest` 原英文 UI 契约漂移已修复：测试现在在受管理的 `MainActivity` 上以正式 `ACTION_SEND` / `Intent.EXTRA_TEXT` 入口启动，保持 5 秒同步上限且不吞滚动失败；模拟器重测 1/1 通过。
2. `9CN0223C27017326` 的 `Phase5GraphDeviceTest` 稳定复现 3/5 失败：
   - `archiveAndHideRequireConfirmationWhileApproveIsImmediate`：`graph-review-confirm-archive` 未显示；
   - `nativeCanvasKeepsFitControlAndOnlyShowsAvailableEvidenceActions`：期望聊天证据 `message-evidence`，实际回调为 `null`；
   - `invalidAndLargeGraphsProvideAccessibleNodeListAndDetail`：选择节点后 `graph-node-detail` 未显示。

   同一 APK 在 `emulator-5554` 的该类 5/5 通过，因此这是 Android 12 / 真机布局或状态传播差异，需单独定位并修复，不能据模拟器结果将真机标记为通过。

---

## 5. 待办

1. 定位并修复 Android 12 真机上图谱详情、确认对话框和聊天证据回调差异；修复后重跑真机图谱类。
2. 在主力真机完成深色模式、大字号、IME、48dp 与 TalkBack 人工检查；未执行前保持 `not_run`。

## 6. 2026-08-19 图谱切片复验增量

本节是对上方历史矩阵快照的增量记录，不改写未在本轮执行的其他测试类。

| 设备 | 测试类 | 结果 | 说明 |
|---|---|---|---|
| `emulator-5554`（Pixel_8_Pro，Android 16） | `GraphInteractionContractDeviceTest` + `Phase5GraphDeviceTest` | **9/9 pass** | 使用最新 Debug APK 与 AndroidTest APK；覆盖动态图例、安全区、画布交互、搜索/帮助、详情、证据入口及破坏性操作确认。 |
| `9CN0223C27017326`（BRA-AL00，Android 12） | `GraphInteractionContractDeviceTest` + `Phase5GraphDeviceTest` | **9/9 pass** | 通过语义点击与 Android 12 详情存在性契约；未引入嵌套滚动；测试结束已卸载测试包并恢复主应用前台。 |

本轮发现并修复一处测试契约漂移：旧测试仍要求已从沉浸式画布移除的巨大退出浮层。修复提交为
`f6eb59e`，产品 UI 未恢复该遮挡浮层。该增量只更新图谱证据，不代表 P6 全量设备矩阵或 PWA 退出条件已满足。
