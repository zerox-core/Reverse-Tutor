# NATIVE-P2-006 · Phase 2 核心教学闭环集成验证

**执行时间**: 2026-08-16
**基线**: origin/Android, commit 0d26dbd
**执行人**: Aily Agent

---

## 1. 实际修改文件清单

| 文件 | 理由 |
|---|---|
| `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt` | 注入 `FakeLlmGenerationRuntime` 替代生产运行时。commit d28fb0b 将 DataModule 默认运行时从 Fake 改为生产 `CompositeLlmGenerationRuntime.production(UrlConnectionProviderHttpTransport(...))`，但 HybridAppGraph 未传 runtime 参数导致使用了生产运行时。在非冻结的 app/wiring 层注入 FakeLlmGenerationRuntime 恢复预览模式假运行时。 |

**修改内容**: HybridAppGraph.kt 新增 `import FakeLlmGenerationRuntime`，创建 `val previewRuntime = FakeLlmGenerationRuntime()`，将其传入 `DataModule.chatGenerationRepository(appContext, runtime = previewRuntime)` 和 `DataModule.backgroundGenerationRepository(appContext, runtime = previewRuntime)`。

## 2. 未修改的冻结层清单

以下路径经 `git diff --name-only` 验证为空（零修改）:

- `mobile-native/core/model` — 未修改
- `mobile-native/core/protocol` — 未修改
- `mobile-native/core/llm` — 未修改
- `mobile-native/core/data` — 未修改（含 *Repository、local、preferences、Room schema/entity/DAO/migration、SecretStore）

## 3. 验收场景测试方式、结果与证据

### 场景 1: 新建会话
- **测试方式**: JVM 测试 `ChatGenerationRepositoryTest` + 设备测试 `Phase2CoreLoopDeviceTest`（尝试运行）
- **结果**: JVM 层通过。Session 创建路径经 `SessionRepository.createSession` → Room 持久化，425 个 JVM 测试全部通过。
- **证据**: `:core:data:testDebugUnitTest` 95 tests passed, 0 failures
- **设备测试状态**: 设备测试因测试代码文本不匹配（测试期望英文 "Sessions"，应用 UI 使用中文 "会话"）在第 31 行 `waitForText("Sessions")` 处超时失败。**非生产代码问题**，测试代码需更新为中文文本。

### 场景 2: 无模型配置
- **测试方式**: JVM 测试 `ChatGenerationCoordinatorTest.noModelConfiguredPublishesPendingThenNoModel`
- **结果**: 通过。Coordinator 正确映射 `NoModelConfigured` outcome 到 `UiState.NoModel`，显示 "未配置模型"。
- **证据**: `:feature:chat:testDebugUnitTest` 113 tests passed, 0 failures
- **安全降级**: 无活跃 Profile 时 `LlmGenerationPlanner` 返回 `NoModelConfigured`，不产生网络请求。`ProductionLlmGenerationRuntime.generate()` 在 secretRef 为空时返回 `MissingCredential`，不发起 HTTP 调用。

### 场景 3: Fake Runtime 回复
- **测试方式**: JVM 测试 `ChatGenerationRepositoryTest`（assistant 消息持久化、流式回复聚合）
- **结果**: 通过。`FakeLlmGenerationRuntime` 默认返回 "Mock generation ready"，消息正确写入当前会话，状态从 Pending → Idle。
- **证据**: `:core:data:testDebugUnitTest` 95 tests passed; `:core:llm:testDebugUnitTest` 20 tests passed
- **装配验证**: HybridAppGraph.kt 修改后，`compileDebugKotlin` UP-TO-DATE + `assembleDebug` BUILD SUCCESSFUL，证明 FakeLlmGenerationRuntime 注入路径编译正确。

### 场景 4: 重启持久化
- **测试方式**: JVM 测试 `ChatGenerationRepositoryTest`（消息持久化到 Room）+ 设备测试 `scenario.recreate()` 验证（代码存在但因文本不匹配未执行到）
- **结果**: JVM 层通过。消息经 `MessageRecord` → Room DAO 持久化，重新加载后时间线可读取。
- **证据**: `:core:data:testDebugUnitTest` 95 tests passed, 0 failures
- **设备测试状态**: 测试代码包含 `scenario.recreate()` 后验证消息持久性的逻辑，但因前置步骤（文本匹配）失败未执行到。

### 场景 5: 会话隔离
- **测试方式**: JVM 测试 `ChatGenerationCoordinatorTest.staleSessionGenerationPublishesNoTerminalStateAfterInvalidation` + `ChatGenerationRepositoryTest`（stale token 拒绝）
- **结果**: 通过。`isTokenCurrent` 回调在 runtime 执行前后均被检查，stale token 的结果不会发布终态。`invalidate()` 防止旧会话的生成结果写入错误会话。
- **证据**: `:feature:chat:testDebugUnitTest` 113 tests passed; `:core:data:testDebugUnitTest` 95 tests passed

### 场景 6: 失败安全
- **测试方式**: JVM 测试 `ChatGenerationCoordinatorTest.providerFailureAndTimeoutPublishFailureThenAllowLaterSuccess` + 源码审查 `ProductionLlmGenerationRuntime.toSafeFailure()`
- **结果**: 通过。失败时 Coordinator 发布 `UiState.Failure`，显示 "生成失败：$message" 安全提示。`toSafeFailure()` 按 HTTP 状态码映射为固定安全消息，**不包含** Provider 原文、URL、Authorization 或 `sk-` 前缀。
- **证据**: `:feature:chat:testDebugUnitTest` 113 tests passed; 源码审查确认 `ProductionLlmGenerationRuntime.kt` 的 `toSafeFailure()` 方法
- **Fake Runtime 失败**: `FakeLlmGenerationRuntime` 默认返回 Success（"Mock generation ready"），不模拟失败场景。生产运行时的失败路径经 `toSafeFailure()` 安全映射。

### 场景 7: 返回导航
- **测试方式**: 源码审查 `AppNavigation.kt` 的 `handleSystemBack()` 方法
- **结果**: 通过（源码审查）。`AppNavigationState.handleSystemBack()` 正确处理：
  - ChatReferences/SessionSettings → 返回 Chat
  - Chat → 返回 Sessions
  - ContextHub → 返回 Chat → Sessions
  - Drawer/Modal → 先关闭再返回
- **证据**: 源码审查 `AppNavigation.kt` 第 80-130 行
- **设备测试状态**: 设备测试未覆盖返回导航场景，标记为 in_progress。

## 4. JVM、设备、构建、Lint 原始结果摘要

### JVM 测试
```
:core:data:testDebugUnitTest — 95 tests, 0 failures
:feature:chat:testDebugUnitTest — 113 tests, 0 failures
:core:llm:testDebugUnitTest — 20 tests, 0 failures
:app:testDebugUnitTest — 172 tests, 0 failures
总计: 400+ tests, 0 failures (含其他模块)
```

### 设备测试
```
Phase2CoreLoopDeviceTest — 1 test, 1 failure
失败原因: ComposeTimeoutException at line 31 (waitForText("Sessions"))
根因: 测试代码期望英文文本 "Sessions"，应用 UI 使用中文 "会话"
影响: 测试代码问题，非生产代码问题
需要: 更新测试文本为中文（会话/新建会话/考前冲刺/创建/打开/还没有消息/发消息/我/未配置模型/林澈）
```

### 构建 + Lint 回归
```
命令: .\gradlew.bat :app:test :app:lint :app:assembleDebug --console=plain --no-daemon
结果: BUILD SUCCESSFUL in 1m 48s
390 actionable tasks: 15 executed, 375 up-to-date
```

## 5. 契约缺口

**未发现契约缺口。**

HybridAppGraph.kt 的修改利用了 DataModule 既有设计：`chatGenerationRepository(context, runtime: LlmGenerationRuntime? = null)` 的可选参数模式。传入 FakeLlmGenerationRuntime 不改变任何接口契约，仅影响运行时实例选择。

设备测试的文本不匹配是测试代码维护问题，不构成契约缺口。

## 6. LEG 行变化

| LEG ID | 描述 | 变化 | 不关闭原因 |
|---|---|---|---|
| LEG-005 | 会话列表渲染 | evidence 更新: JVM 测试通过 | 设备测试未通过（文本不匹配），保持 in_progress |
| LEG-008 | 创建会话 | evidence 更新: JVM 测试通过 + assembleDebug | 设备测试未通过，保持 in_progress |
| LEG-009 | Profile 持久化 | evidence 更新: JVM 测试通过 | 设备测试未验证完整流程，保持 in_progress |
| LEG-012 | 时间线渲染 | evidence 更新: JVM 测试通过 | 设备测试未通过，保持 in_progress |
| LEG-016 | 生成队列/stale-token/流式 | evidence 更新: JVM 测试通过 (Coordinator + Repository) | 设备测试未验证流式，保持 in_progress |
| LEG-042 | 无配置行为 | evidence 更新: JVM 测试通过 (NoModel state) | 设备测试未通过，保持 in_progress |

## 7. Git 状态

```
git status --short --branch:
## Android...origin/Android
 M mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt
?? .aily_tmp/
```

- 分支: Android (tracking origin/Android)
- 修改: 1 个文件 (HybridAppGraph.kt)
- 未跟踪: .aily_tmp/ (临时目录，非项目文件)

## 8. 明确声明

- **未 commit**: 本次修改未提交到 Git
- **未 push**: 未推送到远程仓库
- **未调用真实 Provider/网络**: 全程使用 FakeLlmGenerationRuntime，未发起任何真实 HTTP 请求
- **未修改冻结层**: core/model, core/protocol, core/llm, core/data 均未修改
- **未操作物理设备**: 仅使用 emulator-5554

## 9. 待跟进项

1. **设备测试文本修复**: Phase2CoreLoopDeviceTest 需更新为中文文本以匹配应用 UI。文本映射: "Sessions"→"会话", "New session"→"新建会话"/"新建", "Exam sprint"→"考前冲刺", "Create"→"创建", "Open"→"打开", "No messages yet"→"还没有消息", "Send"→"发消息", "You"→"我", "No model configured"→"未配置模型", "Assistant"→"林澈", "OK"→"关闭"(ActivityAnnouncementDialog)
2. **设备测试扩展**: 当前测试未覆盖会话隔离（切换/删除/归档）和返回导航场景
3. **设备测试重建**: 修复文本后需 `assembleDebugAndroidTest` 重建测试 APK 并在 emulator-5554 上重跑

---

## 10. 2026-08-17 收尾复验与修复证据

本轮在不触碰冻结层的前提下完成了 P2-006 设备验收收尾。

### 修复项

| 文件 | 修复 |
|---|---|
| `mobile-native/app/src/main/java/com/reversetutor/preview/background/BackgroundGenerationWorker.kt` | Worker 显式使用 `FakeLlmGenerationRuntime`，与 `HybridAppGraph` 的预览装配一致，避免后台任务回退到生产 HTTP Runtime。 |
| `mobile-native/app/src/androidTest/java/com/reversetutor/preview/Phase2CoreLoopDeviceTest.kt` | 使用中文真实 UI 契约与稳定 testTag；每个测试前清理本地数据；无模型分支清理 debug bootstrap Profiles；ContextHub 返回前等待导航提交；覆盖核心闭环、会话隔离、返回导航。 |
| `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FormalHomeScreen.kt` | 为新建会话入口补充 `formal-home-new-session` 稳定 testTag。 |

### emulator-5554 结果

```text
Phase2CoreLoopDeviceTest — 3 tests, 0 failures
a_coreLoopPersistsNoModelAndMockReplyAfterRestart — PASS
b_sessionIsolationDoesNotLeakAcrossSessions — PASS
c_backNavigationPreservesChatState — PASS
```

### 回归结果

```text
:app:test :app:lint :app:assembleDebug — BUILD SUCCESSFUL
594 actionable tasks: 59 executed, 535 up-to-date
git diff --check — PASS
冻结层路径 diff — 空（core:model / core:protocol / core:llm / core:data）
```

仍未调用真实 Provider、URL 或 API Key；设备测试只使用 `emulator-5554`。P2-006 本轮设备阻塞已解除，P6 更广泛设备矩阵仍按原计划保留为后续工作。
