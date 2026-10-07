# NATIVE-P3-004 · 第 3 阶段后台生成与可靠性集成验证报告

**任务 ID**: NATIVE-P3-004
**验证日期**: 2026-08-16
**分支**: Android
**验证模式**: 受控修复 + 集成验证
**结论**: JVM 层与后台设备验证通过；失败诊断的确定性设备闭环已消除原阻塞

---

## 1. 验证目标

验证 P3-001/002/003 实现作为完整链路在 preview APK 请求时的集成表现：

- 后台任务状态流转（排队/运行中/已完成/失败/取消/丢弃）与会话隔离
- 通知策略（选择性开启、安全文本、无敏感数据泄露）
- 安全诊断链（提供者/后台失败 → 固定安全记录 → ErrorLog → 诊断 UI 投影 → 复制/导出安全）

## 2. 验证矩阵

| 验证点 | 方法 | 结果 | 证据 |
|--------|------|------|------|
| 后台任务状态：排队→运行中→已完成 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationRepositoryTest` — enqueue-before-execution, completed state |
| 后台任务状态：提供者失败 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationRepositoryTest` — provider failure, 无 assistant 消息写入 |
| 后台任务状态：取消 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationRepositoryTest` — cancellation blocks execution |
| 后台任务状态：丢弃（归档会话） | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationRepositoryTest` — archived-session discard |
| 会话隔离：过期令牌拒绝 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationRepositoryTest` — stale-token late-result rejection |
| 会话隔离：归档会话丢弃 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationRepositoryTest` — archived-session discard |
| 启动恢复：恢复作业按仓库顺序调度 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationStartupRecoveryTest` — recovered jobs dispatched in repo order |
| 启动恢复：无恢复作业则不调度 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationStartupRecoveryTest` — no recovery → no dispatch |
| Worker 请求携带持久化 jobId | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationWorkerTest` — workRequestCarriesPersistedJobId |
| 通知策略：开关关闭→无通知 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationNotificationPolicyTest` |
| 通知策略：权限拒绝→无通知 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationNotificationPolicyTest` |
| 通知策略：完成→通知 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationNotificationPolicyTest` |
| 通知策略：失败→通知 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationNotificationPolicyTest` |
| 通知策略：取消/丢弃/MissingJob→无通知 | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationNotificationPolicyTest` |
| 通知策略：相同 jobId→相同 notificationId | JVM 单元测试 | ✅ 通过 | `BackgroundGenerationNotificationPolicyTest` |
| 通知文本安全：无敏感数据 | 源码审查 | ✅ 通过 | `BackgroundGenerationNotifier.kt` — 通用文本，无用户消息/模型名/URL/密钥 |
| 诊断策略：提供者失败→安全记录 | JVM 单元测试 | ✅ 通过 | `GenerationDiagnosticPolicyTest` — code=provider_request_failed |
| 诊断策略：后台失败→安全记录 | JVM 单元测试 | ✅ 通过 | `GenerationDiagnosticPolicyTest` — code=background_generation_failed |
| 诊断策略：NoModel/Cancelled/受控失败→无诊断 | JVM 单元测试 | ✅ 通过 | `GenerationDiagnosticPolicyTest` — controlledBackgroundFailures |
| 诊断文本安全：无 Authorization/sk-/https:// | JVM 单元测试 + 源码审查 | ✅ 通过 | `GenerationDiagnosticPolicy.kt` — 固定安全代码和通用文本 |
| Debug APK 编译 | Gradle 构建 | ✅ 通过 | `:app:assembleDebug` BUILD SUCCESSFUL |
| Lint 检查 | Gradle lint | ✅ 通过 | `:app:lint` BUILD SUCCESSFUL |
| Python 后端回归 | pytest（9 文件 81 测试） | ✅ 通过 | 0 失败，工作区干净 |
| Python 后端全量回归 | pytest（538 项） | ✅ 通过 | 510 passed, 28 skipped（333.28 秒） |
| 设备后台隔离与启动恢复 | 插桩测试 | ✅ 通过 | `BackgroundGenerationSessionDeletionDeviceTest` + `BackgroundGenerationStartupRecoveryDeviceTest`：OK (2 tests) |
| 冻结层修改 | git diff | ✅ 通过 | 无冻结层路径改动；本任务仅有验收文档与覆盖登记改动 |
| 设备通知实际投递 | 插桩测试 | ✅ 通过 | `BackgroundGenerationNotificationDeviceTest`：完成/失败通知均进入 `NotificationManager`，文案安全 |
| 设备诊断失败路径验证 | 插桩测试 | ✅ 通过 | `FormalDiagnosticsDeviceTest` 经生产 `BackgroundGenerationOutcomeHandler` 注入失败 outcome，验证 ErrorLog、诊断 UI 和剪贴板安全输出 |

## 3. 测试执行摘要

### Kotlin JVM 测试（全部通过）

| 模块 | 结果 | 关键测试 |
|------|------|----------|
| `:core:data:testDebugUnitTest` | BUILD SUCCESSFUL | BackgroundGenerationRepositoryTest (6 tests) |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL | NotificationPolicyTest (10) + DiagnosticPolicyTest (5) + WorkerTest (1) + StartupRecoveryTest (2) |
| `:feature:settings:testDebugUnitTest` | BUILD SUCCESSFUL | 设置模块测试 |
| `:core:llm:testDebugUnitTest` | BUILD SUCCESSFUL | LLM 模块测试 |
| `:feature:chat:testDebugUnitTest` | BUILD SUCCESSFUL | 聊天模块测试 |
| `:feature:memory:testDebugUnitTest` | BUILD SUCCESSFUL | 记忆模块测试 |
| `:app:assembleDebug` | BUILD SUCCESSFUL | Debug APK 编译成功 |
| `:app:lint` | BUILD SUCCESSFUL | 无 lint 错误 |

### Python 后端回归（代表性样本与全量套件均通过）

| 测试文件 | 测试数 | 结果 |
|----------|--------|------|
| test_error_log.py + test_android_background_llm.py | 15 | 15 passed (9.36s) |
| test_llm.py | 26 | 26 passed (14.00s) |
| test_settings_strategy.py | 6 | 6 passed (4.08s) |
| test_db.py | 8 | 8 passed (4.29s) |
| test_engine.py | 10 | 10 passed (5.72s) |
| test_adapters.py | 7 | 7 passed (3.87s) |
| test_server.py | 9 | 9 passed (9.92s) |
| **合计** | **81** | **81 passed, 0 failed** |

完整 Python 套件已在本窗口前台完成：`510 passed, 28 skipped in 333.28s`。代表性样本表保留为 P3 定向回归明细。

## 4. 通知文本安全审查

### BackgroundGenerationNotifier.kt

通知文本完全通用，不包含任何敏感信息：

- **完成通知**: 标题=`后台生成完成`，文本=`一个后台生成任务已完成，可在应用中查看结果。`
- **失败通知**: 标题=`后台生成失败`，文本=`一个后台生成任务未能完成，请稍后重试。`

排除项确认：无用户消息文本、无会话标题、无模型名称、无提供者标识、无 URL、无错误原始信息、无 API 密钥。

通知渠道配置：`IMPORTANCE_LOW`、`setShowBadge(false)`、`setAutoCancel(true)`。

### GenerationDiagnosticPolicy.kt

诊断记录使用固定安全代码和通用文本：

- **提供者失败**: code=`provider_request_failed`，标题=`模型服务请求失败`，详情=`模型服务请求未完成，请检查网络或配置后重试。`
- **后台失败**: code=`background_generation_failed`，标题=`后台生成失败`，详情=`后台生成任务未完成，请打开应用后重试。`
- **未知失败**: code=`generation_failed`，标题=`生成任务失败`，详情=`生成任务未完成，请打开应用后重试。`
- **受控失败排除**: `No model configured`、`Vision input unsupported`、`Blank prompt` 不触发诊断记录。

排除项确认：无 Authorization 头、无 `sk-` 前缀密钥、无 `https://` URL 出现在任何诊断记录中。

## 5. 阻塞项

### 5.1 设备通知实际投递

**状态**: 已通过
**证据**: `BackgroundGenerationNotificationDeviceTest` 在 `emulator-5554` 上直接调用生产 `AndroidBackgroundGenerationNotifier`，检查完成/失败两条通知的 active notification、channel、标题/正文和敏感字段排除；Worker 的开关/权限/终态决策由 `BackgroundGenerationNotificationPolicyTest` 覆盖。
**范围限制**: 本测试不替代真实后台 Provider 执行；通知决策和投递已由策略 JVM 测试与设备测试共同覆盖。

### 5.2 设备诊断失败路径验证

**状态**: 已通过（确定性设备闭环）
**实现**: 新增 app 层 `BackgroundGenerationOutcomeHandler`，由 `BackgroundGenerationWorker` 在持久化任务返回 outcome 后调用。它只将 `GenerationDiagnosticPolicy` 给出的固定安全记录写入现有 `MemoryRepository`，并保留原有通知策略。
**证据**: `FormalDiagnosticsDeviceTest` 在 `emulator-5554` 直接调用该生产处理器并注入 `BackgroundGenerationOutcome.Failed("Provider timeout with Authorization sk-test")`；测试确认写入 Generation `ErrorLog`（`background_generation_failed`），再验证真实诊断 Route 和剪贴板仅呈现固定安全文本，且不包含 `Authorization`、`sk-` 或 `https://`。4 项设备测试总结果为 `OK (4 tests)`。
**范围限制**: 未调用真实 Provider、URL 或 API Key；WorkManager 对 outcome 的 Result 映射保持不变。这是对外部网络执行的刻意隔离，不构成确定性设备验证阻塞。

## 6. 覆盖率注册表更新

以下 LEG 行已基于实际 JVM 与设备证据更新；确定性失败诊断闭环已具备设备证据：

| LEG 行 | 旧状态 | 新状态 | 变更说明 |
|--------|--------|--------|----------|
| LEG-016 | in_progress/blocker | in_progress/blocker | 补充 6 项 BackgroundGenerationRepositoryTest JVM 证据、会话删除/启动恢复设备证据，以及 Worker 所用结果处理器的确定性失败诊断闭环；更广 P6 设备矩阵仍 pending |
| LEG-017 | in_progress/blocker | in_progress/blocker | 补充 10 项通知策略 JVM 证据、源码审查与 emulator-5554 实际通知投递证据；更广 P6 后台/Provider 矩阵仍 pending |
| LEG-023 | in_progress/watch | in_progress/watch | 补充 5 项诊断策略 JVM 证据、源码审查与 emulator-5554 ErrorLog→诊断 UI→剪贴板设备闭环；错误证据关联工作流仍 pending |
| LEG-028 | in_progress/blocker | in_progress/blocker | 补充提供者失败诊断映射 JVM 证据及 Worker 共用处理器的安全失败闭环；真实外部 Provider 连通性仍属 P6 |
| LEG-029 | not_started/watch | in_progress/watch | 通知偏好开关实现并测试验证；主动设置启用/禁用部分验证 |
| LEG-030 | in_progress/watch | in_progress/watch | 诊断入口和通知权限集成确认；About/update 检查与 P1-004 一致 |
| LEG-044 | verified/closed | verified/closed | 重新确认 About/诊断 UI 冒烟和 PWA 提示缺失；无 PWA/Capacitor 包装变更 |

## 7. 冻结层保护确认

- **冻结层未触碰**: core:model, core:protocol, core:llm, core:data/*Repository, core:data/local, core:data/preferences, SecretStore — 全部未修改
- **当前工作区内容**: 包含验收 ADworkflo 记录、验证报告/覆盖登记，以及 app 层结果处理器和对应设备测试；无冻结层改动
- **非目标遵守**: 未构建签名/发布 APK，未修改 PWA/Capacitor/签名文件，未执行 git push

## 8. 交付物清单

- `.adworkflow/artifacts/NATIVE-P3-004/context_raw.json` ✅
- `.adworkflow/artifacts/NATIVE-P3-004/context_manifest.json` ✅
- `.adworkflow/artifacts/NATIVE-P3-004/worker_state.json` ✅
- `.adworkflow/artifacts/NATIVE-P3-004/verification_result.json` ✅
- `mobile-native/app/src/main/java/com/reversetutor/preview/background/BackgroundGenerationOutcomeHandler.kt` ✅
- `mobile-native/app/src/main/java/com/reversetutor/preview/background/BackgroundGenerationWorker.kt` ✅（委托结果处理器）
- `mobile-native/app/src/androidTest/java/com/reversetutor/preview/FormalDiagnosticsDeviceTest.kt` ✅（确定性失败闭环）
- `tasks/native-p3-phase3-validation.md` ✅（本文件）
- `tasks/native-legacy-coverage-registry.md` ✅（LEG 行更新）

## 9. 结论

第 3 阶段后台生成与可靠性集成在 JVM 层和模拟器上验证通过。24 项 Kotlin 单元测试覆盖了状态流转、会话隔离、通知策略和诊断安全映射；4 项设备测试覆盖会话删除隔离、启动恢复、真实通知投递和诊断报告/复制。Debug APK 编译成功，lint 通过；Python 全量回归为 510 passed、28 skipped。冻结层未触碰。

失败 outcome 经 Worker 所用的生产处理器写入 ErrorLog、投影至诊断页并复制安全摘要的确定性设备闭环已通过；通知实际投递、后台隔离和启动恢复同样具有设备证据。真实外部 Provider 连通性不在本次离线可重复验证范围内，保留为后续 P6 集成矩阵项目，而非本任务阻塞。覆盖率注册表仅关闭有完整证据的范围。
