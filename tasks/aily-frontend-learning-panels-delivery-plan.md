# Learning Panels Frontend Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成并单独提交学习概览副屏与会话辅助面板的 Track B 表现层，使 Codex 可以在不改视觉文件的前提下审计、修复并接入生产宿主。

**Architecture:** 页面只接收 `LearningOverviewUiState` 与 `SessionConversationContract`，所有数据和行为通过回调离开 Compose。Track B 不触碰 Port 实现、ViewModel、Factory、app/wiring 或冻结数据层；Codex 负责这些 Track A 文件与最终集成。

**Tech Stack:** Kotlin、Jetpack Compose、`core:design`、JUnit、Compose semantics。

---

## 全局边界（不可违反）

- 只可修改或提交以下 5 个文件：
  - `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPanel.kt`
  - `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPresentation.kt`
  - `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt`
  - `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/LearningOverviewPanelStateTest.kt`
  - `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt`
- 不得修改或暂存 `LearningOverviewViewModel.kt`、`LearningOverviewViewModelTest.kt`、`HybridAppGraph.kt`、`HybridFrontendPortAdapters.kt`、`SessionConversationAssembly.kt`、`LearningOverviewPortAdapters.kt`、任何 `app/shell` 文件、任何 `core/*` 文件。
- UI 不得直接调用 Repository、DAO、Entity、Database、SecretStore、`ChatGenerationInput`、Provider DTO；不得将异常、URL、Authorization、`sk-`、模型名或原始 warning 显示给用户。
- 不得新建 Room schema、DAO、migration、协议字段或临时假数据；空/部分数据必须按 state 原样呈现。
- 不得 push、不得 amend 既有提交、不得用 `git add .`。

### Task 1: Frontend contract delivery

**Files:**

- Modify only the five Track B files listed in “全局边界”。

- [ ] **Step 1: Read the interfaces and confirm no boundary drift**

Read:

```text
mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewViewModel.kt
mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationContract.kt
mobile-native/core/domain/src/main/java/com/reversetutor/core/domain/LearningOverviewContracts.kt
tasks/native-learning-overview-frontend-handoff.md
```

Confirm the UI consumes only these inputs and callbacks:

```kotlin
LearningOverviewPanel(
    state: LearningOverviewUiState,
    onRefresh: () -> Unit,
    onChangeScope: (LearningOverviewScope) -> Unit,
    onOpenWeekly: () -> Unit,
    onOpenWeakPoint: (WeakPointContract) -> Unit,
    currentSessionId: String?
)

SessionAssistantPanel(
    contract: SessionConversationContract?,
    onInteraction: (SessionAssistantInteraction) -> Unit,
    modifier: Modifier = Modifier
)
```

- [ ] **Step 2: Add or retain failing-state assertions before changing a behavior**

The final tests must assert all of the following concrete contracts:

```kotlin
// current session may only use a real ID
assertEquals(listOf("session-42"), capturedScope.sessionIds)

// no current session means the control is absent; [] is never emitted
assertFalse(renderedLabels.contains("当前会话"))
assertFalse(dispatchedScopes.any { it.sessionIds?.isEmpty() == true })

// panel failure content is display-safe
assertFalse(projectedText.contains("https://"))
assertFalse(projectedText.contains("Authorization"))
assertFalse(projectedText.contains("sk-"))
```

- [ ] **Step 3: Complete the presentation without changing data semantics**

Keep all six overview states visible and deterministic:

| 输入状态 | 必须呈现 |
|---|---|
| `isLoading` | 加载/骨架，不展示伪造数值 |
| `errorMessage != null` | 固定安全错误与重试入口 |
| `isNoData` | 空状态与刷新入口 |
| `warnings.isNotEmpty()` 且仍有数据 | 非阻塞“部分学习数据暂不可用”与可用卡片并存 |
| 正常数据 | 进度、计划、主线、薄弱点、token、更新时间；空区块显示“暂无记录”或隐藏 |
| `currentSessionId` 非空白 | “当前会话”发送 `listOf(currentSessionId)`；否则不显示该选择项 |

所有可点击控件必须提供稳定 `testTag` 和中文 `contentDescription`，最小触控高度 48dp。不得通过坐标点击作为唯一测试方式。

- [ ] **Step 4: Run frontend verification**

Run from `F:\xw\reverse-tutor-newmp\mobile-native`:

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :feature:chat:testDebugUnitTest :feature:chat:lint --console=plain
git -C F:\xw\reverse-tutor-newmp diff --check
```

Expected: `BUILD SUCCESSFUL`; no lint errors; no whitespace errors. Do not run device tests in this delivery task.

- [ ] **Step 5: Make the isolated Track B commit**

From `F:\xw\reverse-tutor-newmp`, stage exactly these paths:

```powershell
git add -- `
  mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPanel.kt `
  mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/LearningOverviewPresentation.kt `
  mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt `
  mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/LearningOverviewPanelStateTest.kt `
  mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt
git diff --cached --name-only
git commit -m "feat(chat-ui): deliver contract-driven learning panels"
git status --short --branch
```

Expected staged-file list equals the five paths above. Do not include ViewModel, app/wiring, task documents, capability requests, or any `core` path. Do not push.

## Aily handoff report format

Return only:

1. Commit hash.
2. Exact modified-file list.
3. Two verification command results.
4. Any behavior intentionally left for Codex’s production-host integration.

Codex will then audit the commit, repair issues if any, commit Track A separately, and only then attach both panels to the approved production host.
