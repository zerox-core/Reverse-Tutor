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
| `py -m pytest -q --ignore=tests/test_project_homepage.py` | 超时（MCP 30s 限制） | 树未变（无 Python 代码改动），基线 320 passed 仍有效 |

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
| 模拟器 | `emulator-5554` | **未运行** | Pixel_8_Pro AVD（未启动） |
| 主力真机 | 未提供 | **未连接** | — |

---

## 2. 模拟器必测组

设备固定：`emulator-5554`。本轮模拟器未运行，全部标记为 `not_run`。

历史设备证据（2026-08-17 P6-001 补测，`emulator-5554` Pixel_8_Pro AVD）：

| 测试类 | 测试数 | 历史结果 | 本轮状态 | 备注 |
|---|---|---|---|---|
| `Phase2CoreLoopDeviceTest` | 3 | 3 passed, 0 failed | not_run | 核心闭环（会话创建→发送→Fake Runtime 回复→重启持久化→会话隔离→返回导航） |
| `Phase5ContextHubDeviceTest` | 4 | 4 passed, 0 failed | not_run | Context Hub 入口、证据跳转、返回 Chat |
| `Phase5GraphDeviceTest` | 5 | 5 passed, 0 failed | not_run | Canvas、空/大/错状态、选择、编辑、Archive/Hide |
| `Phase5MemoryDeviceTest` | 1 | 1 passed, 0 failed | not_run | Chat Note → Context Hub 持久化 |
| `Phase5SourcesDeviceTest` | 1 | 1 passed, 0 failed | not_run | FullyLocal/PartiallyLocal/Failed/FutureAssisted/Unsupported |
| `Phase4ImportExportDeviceTest` | — | 无历史证据 | not_run | 导入 dry-run/append/overwrite/new-space/导出/wipe |
| `BackgroundGenerationSessionDeletionDeviceTest` | — | 无历史证据 | not_run | 删除会话不写消息 |
| `BackgroundGenerationStartupRecoveryDeviceTest` | — | 无历史证据 | not_run | 重启恢复与重新调度 |
| `BackgroundGenerationNotificationDeviceTest` | — | 无历史证据 | not_run | 完成/失败通知和安全文案 |
| `FormalDiagnosticsDeviceTest` | — | 无历史证据 | not_run | ErrorLog、诊断页和剪贴板脱敏 |

历史汇总：14 tests passed, 0 failures（2026-08-17 P6-001 补测）。

---

## 3. 主力真机必测组

主力真机 serial 未提供，全部标记为 `not_run`。

| 测试项 | 状态 | 备注 |
|---|---|---|
| 核心闭环（Phase2CoreLoopDeviceTest） | not_run | 需主力真机 serial |
| Context Hub | not_run | 需主力真机 serial |
| Graph | not_run | 需主力真机 serial |
| Memory | not_run | 需主力真机 serial |
| Sources | not_run | 需主力真机 serial |
| 深色模式（手工） | not_run | 需主力真机 |
| 大字号/动态字体（手工） | not_run | 需主力真机 |
| IME 键盘遮挡（手工） | not_run | 需主力真机 |
| 48dp 触摸目标（手工） | not_run | 需主力真机 |
| TalkBack 人工听测 | not_run | 只在主力真机进行；记录焦点顺序，不用 Compose 语义树冒充语音证据 |

---

## 4. 设备矩阵记录

### 4.1 状态词汇

矩阵只允许以下状态：`pass`、`fail`、`blocked`、`not_run`、`not_available`。

### 4.2 设备矩阵

| 测试类 | emulator-5554 | 主力真机 | 其他设备 |
|---|---|---|---|
| Phase2CoreLoopDeviceTest | not_run | not_run | not_available |
| Phase5ContextHubDeviceTest | not_run | not_run | not_available |
| Phase5GraphDeviceTest | not_run | not_run | not_available |
| Phase5MemoryDeviceTest | not_run | not_run | not_available |
| Phase5SourcesDeviceTest | not_run | not_run | not_available |
| Phase4ImportExportDeviceTest | not_run | not_run | not_available |
| BackgroundGenerationSessionDeletionDeviceTest | not_run | not_run | not_available |
| BackgroundGenerationStartupRecoveryDeviceTest | not_run | not_run | not_available |
| BackgroundGenerationNotificationDeviceTest | not_run | not_run | not_available |
| FormalDiagnosticsDeviceTest | not_run | not_run | not_available |
| 深色模式（手工） | not_run | not_run | not_available |
| 大字号（手工） | not_run | not_run | not_available |
| IME 键盘（手工） | not_run | not_run | not_available |
| 48dp 触摸目标（手工） | not_run | not_run | not_available |
| TalkBack 听测 | not_run | not_run | not_available |

### 4.3 统计

| 统计项 | emulator-5554 | 主力真机 | 其他设备 |
|---|---|---|---|
| 测试总数 | 15 | 15 | 15 |
| pass | 0 | 0 | 0 |
| fail | 0 | 0 | 0 |
| not_run | 15 | 15 | 0 |
| not_available | 0 | 0 | 15 |

### 4.4 记录规则

- 模拟器和主力真机分别记录，不合并为"设备通过"
- 其他设备行统一写 `not_available`，原因写"当前无设备条件"
- 每个失败附日志、测试方法、复现步骤和是否影响生产代码的判断
- 设备测试失败不得通过删断言、放宽等待或改产品文案消除

### 4.5 历史证据说明

2026-08-17 P6-001 补测在 `emulator-5554` 上执行了 5 个测试类共 14 个测试，全部通过。本轮模拟器未运行，无法重跑。历史证据可参考但不替代本轮矩阵——本轮矩阵状态以 `not_run` 为准。

---

## 5. 待办

1. 用户启动 `emulator-5554`（Android Studio → Device Manager → 启动 Pixel_8_Pro AVD）后重跑全部模拟器必测组
2. 用户提供主力真机 ADB serial 后执行主力真机必测组和手工检查
3. TalkBack 听测仅在主力真机执行
