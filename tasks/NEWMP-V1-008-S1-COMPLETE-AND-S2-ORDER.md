# NEWMP-V1-008 · S1 通知精确路由 完成记录 + S2 模拟器验收工单

> 负责：本地开发搭档（主导规划执行）｜协助：Codex（bug 修复、验收）
> 日期：2026-09-09（周三）｜分支：newmp｜工作树未提交（按 AGENTS.md：无用户明确要求不提交）

## 一、S1 通知精确路由 —— 已完成（JVM 全绿）

**问题**：后台生成完成/失败通知点击后只打开 MainActivity，不进入产生该任务的会话（V1-006 验收报告已知缺口）。

**改动（6 个文件）**：

| 文件 | 改动 |
|---|---|
| `app/.../background/BackgroundGenerationNotificationDispatcher.kt` | 新建。纯 JVM 协作类，lambda 依赖（通知开关/权限探针/通知器工厂/会话解析），按既有 NotificationPolicy 决策并携带 sessionId 发通知 |
| `app/.../background/BackgroundGenerationNotifier.kt` | 接口 `notifyCompleted/notifyFailed` 增加 `sessionId: String? = null`（默认参数，向后兼容既有实现）；`post()` 向 PendingIntent Intent 写入 `EXTRA_OPEN_SESSION_ID`；Intent 增加 `FLAG_ACTIVITY_SINGLE_TOP`（保住应用内状态，走 onNewIntent） |
| `app/.../background/BackgroundGenerationOutcomeHandler.kt` | `notifyIfNeeded` 委托给 Dispatcher；会话解析默认走 `DataModule.backgroundGenerationRepository(appContext).getJob(jobId)?.sessionId` |
| `app/.../MainActivity.kt` | 新增 `pendingOpenSessionId` 状态；onCreate / onNewIntent 均读取 `EXTRA_OPEN_SESSION_ID`；传给 AppShell 并在消费后清空 |
| `app/.../shell/AppShell.kt` | 新增 `pendingOpenSessionId` / `onOpenSessionConsumed` 参数；LaunchedEffect 经 `getSession(id).toSessionListItem()` 解析后设置 activeSessionId/Title/LearnerRole 并导航到 Chat（复用 1834 行既有解析模式） |
| `app/src/test/.../BackgroundGenerationNotificationDispatcherTest.kt` | 新建 4 条测试：完成路由 sessionId、失败缺 Job 路由 null、开关关闭跳过、权限未授予跳过 |

**测试证据**：`gradle_test`（mobile-native 全模块）BUILD SUCCESSFUL in 26s，`:app:testDebugUnitTest` 实际执行（含新 4 条测试）。中途修复 1 个编译错误：域模型 `Session` 无 `learnerRole` 字段，改用 `toSessionListItem()` 转换（与既有 TokenUsage 路由一致）。

**诚实声明**：TDD Red 未单独跑（首次运行即被 AppShell 编译错误挡住，该错误已当场修复）；Green 证据为上述全量构建。接口默认参数保证 `FormalDiagnosticsDeviceTest.NoOpNotifier` 等 androidTest 既有实现源码兼容，无需改动。

> **2026-09-09 更新**：用户已授权自主推进，S2 设备验收不再单独派单，并入最终统一验收（清单保留供届时使用）。

## 二、S0 收口

- F1（V1-006 修改冻结层）：用户已裁决——以快速推进 MVP 为主，速度指令优先，冻结约束由用户指令豁免。不再追补 capability-requests。
- 临时截图清理（.tmp-chat.png / .tmp-emulator.png → F:\.aily-trash）：上轮已完成。

## 三、S4–S7「Codex 已完成」声明核查结论

对仓库做证据扫描（66 处命中）：**脚手架真实存在**——CompanionMemoryEvolutionPolicy、LearningScopeGuard、WindowBranchPort/Coordinator、InitiativePlan/InitiativeEligibilityPolicy、HeartbeatTurnDispatchContracts/WindowHeartbeatCoordinator、LocalLearningEvidenceVerifier/PostTurnProjector、CompanionMemoryRepository、LearningLedgerRepository 及配套测试均在仓库里。**没有发现撒谎迹象**。但「存在代码」≠「按主线宪章完成」：设备验收、能力审批记录是否齐备尚未逐条核对（列入 S3 对齐时一并处理，不阻塞当前）。

## 四、S2 模拟器验收工单（交 Codex 执行）

**背景**：MCP 设备通道全部被 ANDROID_TOOLCHAIN_UNAVAILABLE 门禁拦截（审计已证），模拟器实机操作只能由 Codex 在 Windows 本地执行。模拟器：Pixel_8_Pro / API 36 / emulator-5554。

**前置**：`gradle_assemble_debug` 或直接 `installDebug`（工作树未提交，直接用当前代码构建）。

**验收清单（含 NEWMP-V1-006-TASK7 未闭环项）**：

1. 冷启动 App，创建会话 A，发送一条消息（真实模型或 Fake 运行时均可，但本轮至少一条真实 Provider 调用）。
2. 把 App 退到后台，在会话 A 触发一次后台生成；等待完成通知出现。
3. 点击完成通知 → **预期：直接进入会话 A 的 Chat 界面（本次 S1 新行为），而不是首页**。
4. 再触发一次会失败的后台生成（如断网），点击失败通知 → 预期同样路由进对应会话。
5. 通知点击时 App 在前台（SINGLE_TOP 路径）→ 预期走 onNewIntent 路由且不丢失当前界面状态。
6. 设置中关闭「后台生成通知」→ 后台完成不再弹通知。
7. 通知内容不含用户消息文本 / 会话标题 / 模型名（安全不变量，抄送自 V1-006）。
8. TASK7 遗留 8 项人工检查单（见 NEWMP-V1-006-TASK7-DEVICE-REPORT.md）中尚未逐条闭环的部分。

**发现 bug 时**：小 bug 直接修复（你是修复责任人），修复后把现象/根因/改法追加到本文件第五节；修不动的列到「反复出现无法修复」清单，等待统一处置。

## 五、验收结果（Codex 回填区）

（待回填：每项通过/失败 + 截图或日志证据 + 修复记录）
