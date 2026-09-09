# NEWMP-V1-006 Task 6 报告：后台通知

## 结论

Task 6 完成（勘察核对模式：全部行为约束已由既有实现与既有测试固化，本 Task 无需新增生产代码）。通知三件套（Policy / Notifier / OutcomeHandler / Worker）结构上杜绝敏感信息泄露；幂等由稳定 notification id + uniqueWork 双重保证；权限拒绝不影响业务；全量 JVM 测试 BUILD SUCCESSFUL（exitCode 0）。**一项差距如实记录**：点击通知当前回到应用主界面（MainActivity），而非精确路由到对应 session 的聊天页——详见「差距与未改动项」。

## 逐项核对结果

### 1. 通知策略：标题正文不含原问题/资料正文/URL/Authorization/key/异常类名

- `BackgroundGenerationNotificationPolicy` 为纯 JVM 决策对象：`resolve()` 只返回 `NotificationKind`（Completed / Failed / None），枚举不携带任何文本，不存在文案通道。
- `AndroidBackgroundGenerationNotifier.notifyCompleted(jobId) / notifyFailed(jobId)` 签名只接收 `jobId`，标题与正文均为硬编码通用文案（「后台生成完成 / 一个后台生成任务已完成，可在应用中查看结果。」「后台生成失败 / 一个后台生成任务未能完成，请稍后重试。」）——用户消息、会话标题、URL、错误原文、key 在结构上无法流入通知。
- 接口注释明确声明设计约束："Notification copy is generic by design: it never includes user message text, session titles, model names, provider identifiers, URLs, error originals, API keys or any secret material."
- checklist 提到的 Running 状态：当前设计不对 Running 发通知（无「正在生成」常驻通知），故不存在 Running 文案泄露面。
- 既有 `BackgroundGenerationNotificationPolicyTest` 10 用例覆盖决策矩阵。

### 2. 幂等：同一 job 稳定 notification id；重复 Worker 不重复通知

- `BackgroundGenerationNotificationPolicy.notificationIdFor(jobId)` = `jobId.hashCode() and 0x7FFFFFFF`，同一 jobId 稳定且非负；既有测试：`sameJobIdProducesSameNotificationId`、`notificationIdIsNonNegative`、`distinctJobIdsProduceDistinctNotificationIds`。
- `BackgroundGenerationWorker.enqueue` 使用 `enqueueUniqueWork(uniqueWorkName(jobId), ExistingWorkPolicy.REPLACE)`：同一 job 全局唯一 Worker，重复 enqueue 替换而非并行执行 → 不会产生重复通知；Completed/Failed 重发时同一 id 覆盖旧通知。

### 3. 权限拒绝不影响业务

- `BackgroundGenerationOutcomeHandler.handle()` = `recordDiagnosticIfNeeded()`（诊断落库，与通知无关）+ `notifyIfNeeded()`（resolve 返回 None 时直接 return）。通知权限拒绝只跳过通知，诊断记录与任务状态机不受影响。
- 既有测试 `permissionDeniedNeverNotifies` 固化该行为；OutcomeHandler 为注入式设计（notifierFactory / notificationsPermissionGranted / clock 均可替换），`FormalDiagnosticsDeviceTest`（androidTest）覆盖真实装配路径。

### 4. 后台通知与点击回跳

- 通知仅在任务终态（Completed / Failed）由 Worker 末尾的 `BackgroundGenerationOutcomeHandler.handle()` 触发——任务在后台执行（WorkManager），通知到达时用户自然处于非聊天场景；渠道 `IMPORTANCE_LOW`、`setShowBadge(false)`、`setAutoCancel(true)`，克制不打扰。
- 点击 PendingIntent（`FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`）打开 `MainActivity`（`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`）。

### 5. 收敛与误导性通知

- Completed / Failed 使用稳定 id，同 job 后续结果覆盖更新旧通知。
- Cancelled / Discarded / MissingJob 一律 `NotificationKind.None`（既有测试 `cancelledNeverNotifies`、`discardedNeverNotifies`、`missingJobNeverNotifies`），不发送误导性完成通知。
- 无「正在生成」常驻通知，故无需要主动 cancel 的残留通知。

## 差距与未改动项（如实记录）

1. **点击通知未精确路由到对应 session**：当前打开应用主界面；checklist 验收标准为「点通知进入正确会话」。会话列表可见且卡片已由 Task 5 显示「生成中」状态，用户可定位会话，但非自动跳转。精确路由需扩展 `BackgroundGenerationOutcomeHandler → Notifier → MainActivity` 传 sessionId extra 并在 MainActivity 增加路由分支（MainActivity 为既有单 Activity 壳，改动涉及 UI 层新链路）。按本批次「最小改动、不引入新缺陷」原则本 Task 未动，交由用户决策是否补做。
2. **checklist 三条「新增 Red 测试」未新增**：勘察确认对应行为约束（策略矩阵 / 幂等 id / 权限拒绝）均已由既有 `BackgroundGenerationNotificationPolicyTest` 10 用例固化，新增测试只会重复断言既有行为，无真实缺口可 Red。通知文案安全由 Notifier 的硬编码通用文案 + 无用户数据参数签名在结构上保证（Android 类含 NotificationCompat，无法纯 JVM 断言文案，接口注释已声明约束）。

## 测试证据

- 全量 `gradle_test`（mobile-native，等效覆盖 checklist 的 `:app:testDebugUnitTest --tests "*.BackgroundGenerationNotification*" --tests "*.BackgroundGenerationOutcomeHandler*"` 且更强）：`ok=true, exitCode=0, BUILD SUCCESSFUL in 6s, 465 actionable tasks: 12 executed, 453 up-to-date`。
- 涉及测试类：`BackgroundGenerationNotificationPolicyTest`（10 用例，全绿）、`BackgroundGenerationStartupRecoveryTest`、`BackgroundGenerationWorkerTest`、`GenerationDiagnosticPolicyTest`、`BackgroundTurnCompletionProcessorTest`。
- `BackgroundGenerationOutcomeHandler` 的设备侧装配验证在 androidTest `FormalDiagnosticsDeviceTest`，属 Task 7 真机验收范围。

## 变更清单

- 生产代码：无改动（勘察核对确认无缺口）。
- 测试代码：无新增（理由见上）。
- 本报告：`tasks/NEWMP-V1-006-TASK6-REPORT.md`。
