# 学习概览与会话辅助面板：前端协同实施计划

> **给执行代理：** 请按任务逐项实施。前端实现、状态/装配实现分别由唯一责任人完成；不得为了页面效果越过契约访问数据层。

**目标：** 将会话算法与学习概览读模型以可替换的 Compose 表现层接入首页副屏和会话辅助面板；页面只投影稳定契约，不重算教学算法、不直连数据库。

**架构：** Track A 持有状态、端口、装配和真实数据聚合；Track B 持有 Compose 页面、视觉、动效与无障碍。两轨只通过 `LearningOverviewPort`、`LearningOverviewContract`、`SessionConversationContract` 对接。生产数据只能从 `HybridAppGraph.sessionConversationAssembly` 的装配链路取得。

**技术栈：** Kotlin、Jetpack Compose、StateFlow、协程、现有 `core:domain` 契约、现有 `core:design` 设计系统、JUnit/Compose 测试。

---

## 1. 事实基线、责任边界与禁止事项

### 1.1 工作树中已存在的并行工作（必须保留）

分支为 `newmp`。下列文件是本计划的起点；不得删除、覆盖或用 `git add .` 混入无关提交。

| 路径 | 当前状态 | 唯一责任人 | 处理方式 |
|---|---|---|---|
| `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt` | 已修改，提供 Factory | Track A | 仅 Track A 审核和提交 |
| `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridFrontendPortAdapters.kt` | 已修改，提供 `RepositoryLearningOverviewPortAdapter` | Track A | 仅 Track A 审核和提交 |
| `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewViewModel.kt` | 新增，Port 与 UI state | Track A | 先补安全错误映射和测试 |
| `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPanel.kt` | 新增，学习概览 Compose 面板 | Track B | 可重做视觉，不得改数据语义 |
| `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt` | 新增，会话辅助 Compose 面板 | Track B | 可重做视觉，不得改会话算法结果 |
| `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/LearningOverviewViewModelTest.kt` | 新增，状态测试 | Track A | 与 ViewModel 同一提交 |
| `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt` | 新增，投影测试 | Track B | 与会话辅助面板同一提交 |

Track A 与 Track B 不修改同一文件，也不共用一个提交。`app/shell/AppShell.kt` 是 hotspot，Track B 不得直接修改。

### 1.2 唯一可消费的同步面

| 前端用途 | 允许使用的类型/入口 | 源码路径 |
|---|---|---|
| 首页学习副屏 | `LearningOverviewPort.loadOverview(scope)` → `LearningOverviewContract` | `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewViewModel.kt`；`mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewContracts.kt` |
| 会话辅助面板 | `SessionConversationContract` | `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationContract.kt` |
| 生产 Factory | `HybridAppGraph.frontend.learningOverviewViewModelFactory` | `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt` |
| 数据编排 | `SessionConversationAssembly.overview(scope)` | `mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/SessionConversationAssembly.kt` |

### 1.3 硬性禁止

- 不得从 Compose、ViewModel 或 Route 直接调用 Repository、DAO、Entity、`ReverseTutorDatabase`、SQL、Room migration、`SecretStore`、`ChatGenerationInput` 或 Provider DTO。
- 不得由 UI 推导掌握度、周主线、薄弱点严重度、token 总量或教学策略；只能展示契约已给出的字段。
- 不得显示 `Throwable.message`、Provider 名、URL、请求内容、Authorization、密钥、模型名或原始错误。
- 不得把 `emptyList()` 解释为“当前会话”：`LearningOverviewScope.sessionIds=null` 是全部会话，空列表是明确选择零个会话。
- 不得修改 `core:model`、`core:protocol`、`core:llm`、`core:data/*Repository`、Room/DAO/migration、preferences 或 `SecretStore`。数据不够时提交 capability request，不伪造卡片数据。

## 2. Track B 前端任务（可独立安排）

### 任务 B1：固化可替换的表现层

**文件：**

- 修改：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPanel.kt`
- 修改：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt`
- 测试：`mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt`
- 可新增：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPresentation.kt`

- [ ] 将 `LearningOverviewUiState`、`SessionConversationContract` 纯投影为展示状态；页面入参保持不可变，事件只通过回调向外发送。
- [ ] 视觉 token、格式化函数和 UI 专用枚举均放在 `LearningOverviewPresentation.kt`；不得写回 `core:domain` 契约。
- [ ] 使用 `core:design` 的颜色、形状和字号；交互控件最小触控区域 48dp。
- [ ] 保留并补齐会话辅助测试：成功、无模型、Provider 失败、陈旧 token、删除会话、空会话六种契约都只能映射出安全中文文案。
- [ ] 显式提交 Track B 文件，建议提交信息：`feat(chat-ui): add contract-driven learning panels`。

**验收：** 任何视觉改造不需要改 `LearningOverviewContract`、`SessionConversationContract`、Coordinator、Repository 或 app/wiring；仅通过 Fake contract 即可预览与测试。

### 任务 B2：完整呈现学习概览的六种状态

**文件：**

- 修改：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPanel.kt`
- 新增测试：`mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/LearningOverviewPanelStateTest.kt`

- [ ] 完整态显示进度、今日计划、周主线、薄弱点、token、生成时间和活跃会话数；字段为空时显示“暂无记录”或隐藏区块，绝不编造内容。
- [ ] `isLoading=true` 显示骨架/加载；`isNoData=true` 显示首次使用空状态和刷新；`errorMessage!=null` 显示安全错误和重试。
- [ ] `warnings.isNotEmpty()` 但其他数据存在时显示非阻塞“部分学习数据暂不可用”；不可把整页降级为错误页。
- [ ] 警告字段只能映射为固定中文；不得直接显示 `ContextWarning.source` 或 `ContextWarning.message`。
- [ ] 为刷新、重试、范围切换、周主线和薄弱点入口提供稳定 `testTag` 与中文 `contentDescription`；测试应使用语义点击。

**状态测试至少包含：**

```kotlin
// Fake Port 以部分数据返回时，真实卡片与非阻塞提示都必须存在。
assertTrue(state.warnings.isNotEmpty())
assertFalse(state.isNoData)
```

### 任务 B3：修复“当前会话”范围语义

**文件：**

- 修改：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPanel.kt`
- 测试：`mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/LearningOverviewPanelStateTest.kt`

- [ ] 面板新增可选输入 `currentSessionId: String?`；仅当该值非空白时显示“当前会话”。
- [ ] 点击“当前会话”时回调 `state.scope.copy(sessionIds = listOf(currentSessionId))`；没有有效 id 时隐藏该项，绝不发出 `emptyList()`。
- [ ] 点击“全部会话”时回调 `state.scope.copy(sessionIds = null)`。
- [ ] 首页默认没有当前会话，只显示“全部会话”；会话页在提供真实 session id 后才显示该切换项。

**必须覆盖：**

```kotlin
assertEquals(listOf("s-42"), capturedScope.sessionIds) // 有 currentSessionId
// currentSessionId = null 时，不存在“当前会话”控件，也不派发空列表 scope。
```

### 任务 B4：无障碍与表现回归

- [ ] 图标按钮具有中文 `contentDescription`；纯装饰图标为 `null`。
- [ ] 深色模式与大字号下文本可换行不裁剪，内容区域可滚动；屏幕阅读器能区分加载、错误、空状态与重试操作。
- [ ] 不修改 `AppShell.kt`、`FormalBatch6RuntimeRoutes.kt`、`FormalSearchArticleRuntimeRoutes.kt`。需要挂载点时向 Track A 发出请求。

**Track B 验收命令：**

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :feature:chat:testDebugUnitTest --console=plain
.\gradlew.bat :feature:chat:lint --console=plain
git diff --check
```

预期：测试、lint 成功；冻结路径无改动；提交中没有 app/wiring 或 core 文件。

## 3. Codex / Track A 同步任务（由 Codex 负责）

### 任务 A1：先锁定安全状态，再给 UI 接入（已完成：2026-08-22）

**文件：**

- 修改：`mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewViewModel.kt`
- 修改：`mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/LearningOverviewViewModelTest.kt`

- [x] 将 `Throwable.toOverviewErrorMessage()` 替换为固定文案 `"暂时无法加载学习概览，请稍后重试"`；不返回或拼接 `Throwable.message`。
- [x] 将 `ContextWarning` 映射为白名单中文文案；未知来源统一为 `"部分学习数据暂不可用"`。
- [x] Fake Port 抛出包含 `https://`、`Authorization`、`sk-` 的异常；断言 UI state 不包含任意原文片段。
- [x] `CancellationException` 保持重新抛出，不能显示为错误。

```kotlin
assertEquals("暂时无法加载学习概览，请稍后重试", state.errorMessage)
assertFalse(state.errorMessage.orEmpty().contains("https://"))
assertFalse(state.errorMessage.orEmpty().contains("sk-"))
```

### 任务 A2：审核并单独提交现有生产装配（已完成审核，待与前端分离后提交）

**文件：**

- 审核并提交：`mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridAppGraph.kt`
- 审核并提交：`mobile-native/app/src/main/java/com/reversetutor/preview/wiring/HybridFrontendPortAdapters.kt`
- 审核并提交：A1 的 ViewModel 与测试

- [x] 确认 Adapter 只委托 `SessionConversationAssembly.overview(scope)`。
- [x] 确认 `HybridAppGraph` 只创建 Factory，Compose 层不持有 Repository。
- [x] 现有装配契约测试证明 `SessionConversationAssembly` 没有 Compose 类型且前端入口可用 Fake Port 替换。
- [ ] 当前与 Track B 共享未提交工作树，待分离提交范围后提交；建议信息：`feat(app): provide learning overview presentation port`。

### 任务 A3：真实 token 聚合与诚实能力缺口（已完成：2026-08-22）

**文件：**

- 修改：`mobile-native/app/src/main/java/com/reversetutor/preview/wiring/session/LearningOverviewPortAdapters.kt`
- 新增测试：`mobile-native/app/src/test/java/com/reversetutor/preview/wiring/session/LearningOverviewPortAdaptersTest.kt`

- [x] 使用现有 `LearningRepositoryImpl.listTokenUsage(spaceId)` 聚合最近 7 天 token；通过已有 turn→session 查询按 `sessionIds` 过滤；无记录返回 0 与 `isEstimated=false`。
- [x] 不将 `listWeeklySummaries(spaceId)` 的纯文本猜成 `LearningThreadContract`；缺少结构化知识点/进度时周主线保持空。
- [x] 已在 `tasks/capability-requests/P6-learning-overview-mastery-progress.md` 创建冻结变更申请；未批准前 progress 保持默认值。

### 任务 A4：选择唯一宿主并完成联调

- [ ] 先依据现有路由确认首页/会话的真实挂载点，不默认塞入任一页面。
- [ ] 只能由 Track A 改 hotspot。必要时从 `AppShell.kt` 抽取一个新的纯 Compose 宿主文件到 `feature/chat`，让 Track B 后续独立迭代视觉。
- [ ] 宿主只能使用 `graph.frontend.learningOverviewViewModelFactory`；会话页额外提供真实 `currentSessionId`。
- [ ] 先 Fake Port UI 测试，再真实 app 装配回归。

**Track A 验收命令：**

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :core:domain:testDebugUnitTest :feature:chat:testDebugUnitTest :app:testDebugUnitTest --console=plain
.\gradlew.bat test :app:lint :app:assembleDebug --console=plain
py -m pytest -q --ignore=tests/test_project_homepage.py
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
```

预期：Android/Python 测试均通过；最后一条冻结路径检查无输出。

## 4. 联调准入与顺序

### 联调准入

- [ ] UI 用 Fake `LearningOverviewPort` / 固定 `SessionConversationContract` 覆盖加载、空、部分告警、安全错误、完整数据和刷新。
- [ ] ViewModel 不透传异常原文，且保留取消语义。
- [ ] “当前会话”只在真实 session id 存在时可用，绝不派发空列表。
- [ ] UI 未直连 Repository/DAO/Entity/Database/SecretStore；生产 Port 只经 `SessionConversationAssembly`。
- [ ] 周主线、掌握度、token 的真实程度均有后端证据；缺失内容用空/默认和非阻塞提示表示。

### 执行顺序

1. Codex 完成 A1，先锁定安全的 UI 状态输出。
2. Track B 可并行完成 B1、B2、B4，始终使用 Fake contract，不等待真实 token 或掌握度能力。
3. Codex 完成 A2、A3；只补现有读取能力已经支持的真实聚合。
4. B3 与 A4 合并：确认宿主能提供 session id 后才接入“当前会话”；不能提供则隐藏它。
5. 最后由 Codex 集成、跑全量回归；Track B 做视觉与无障碍验收。

这样划分后，前端可随时整体重做视觉和交互；只要不改变三类输入输出契约，会话算法、教学策略、LLM 和数据层工作均不受影响。
