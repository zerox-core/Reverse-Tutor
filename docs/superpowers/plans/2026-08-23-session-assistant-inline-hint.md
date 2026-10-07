# Session Assistant Inline Hint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide a default-collapsed, italic light-gray “本轮学习提示” below the current AI reply. A tap opens the existing detail sheet without changing conversation business behavior.

**Architecture:** A new feature-local entry resolves visual anchoring from `SessionConversationContract`, owns only local expanded state, and delegates detail rendering to `SessionAssistantPanel`. The future Track A host owns placing the entry in the actual chat timeline. No Repository, coordinator, AppShell, wiring, core, contract, Room, network, or LLM code changes.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, `core:design`, JUnit 4, feature-chat lint/tests.

---

## Immutable scope

Only edit or create these files:

- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantReplyHint.kt` — new entry and pure visual anchor resolver.
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt` — detail-sheet-only visual refinement.
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantReplyHintTest.kt` — new resolver tests.
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantInteractionTest.kt` — new event mapping tests.
- `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt` — extend safe detail-state tests.

Read first:

- `docs/superpowers/specs/2026-08-23-session-assistant-inline-hint-design.md`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionConversationContract.kt`
- `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt`

Never modify `AppShell.kt`, any `app/wiring` file, `core/**`, Gradle files, Repository, DAO, Entity, Room, network, LLM, or data contract.

### Task 1: Define and test the visual anchor before creating the composable

**Files:**

- Create: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantReplyHintTest.kt`
- Create later: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantReplyHint.kt`

- [ ] **Step 1: Write failing JUnit tests**

Define tests for exactly these outcomes:

```kotlin
assertEquals(
    SessionAssistantHintAnchor.AfterAssistantMessage("assistant-42"),
    contract(assistantMessageId = "assistant-42").inlineHintAnchor()
)
assertEquals(
    SessionAssistantHintAnchor.TimelineEnd,
    contract(assistantMessageId = null, safeError = "暂时无法生成回复").inlineHintAnchor()
)
assertNull(SessionConversationContract.empty("s-empty").inlineHintAnchor())
assertFalse(SessionConversationContract.empty("s-empty").hasInlineLearningHint())
assertTrue(contract(action = SessionActionContract(type = "probe", knowledgePoint = "函数")).hasInlineLearningHint())
```

Use a fixture whose `GenerationUiContract` has `READY` when no `safeError` is supplied, and `ERROR` when one is supplied. The fixture must not call a repository or a ViewModel.

- [ ] **Step 2: Prove the tests fail before implementation**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*SessionAssistantReplyHintTest" --console=plain
```

Expected: test compilation fails because the anchor type and helper methods do not yet exist.

### Task 2: Implement the default-collapsed inline system hint

**Files:**

- Create: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantReplyHint.kt`
- Test: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantReplyHintTest.kt`

- [ ] **Step 1: Implement pure visual eligibility**

Put this exact feature-local type and helper shape in the new file:

```kotlin
internal sealed interface SessionAssistantHintAnchor {
    data class AfterAssistantMessage(val messageId: String) : SessionAssistantHintAnchor
    data object TimelineEnd : SessionAssistantHintAnchor
}

internal fun SessionConversationContract.hasInlineLearningHint(): Boolean =
    generation.assistantMessageId != null ||
        !generation.safeError.isNullOrBlank() ||
        generation.state !in setOf(GenerationState.IDLE, GenerationState.READY) ||
        action != null || evaluation != null || nextStep != null ||
        context.prerequisiteGaps.isNotEmpty() ||
        context.relatedMemory.isNotEmpty() ||
        context.sourceEvidence.isNotEmpty() ||
        context.historicalErrors.isNotEmpty() ||
        context.pendingReviewKnowledgePoints.isNotEmpty() ||
        context.warnings.isNotEmpty()

internal fun SessionConversationContract.inlineHintAnchor(): SessionAssistantHintAnchor? =
    if (!hasInlineLearningHint()) null
    else generation.assistantMessageId?.let(SessionAssistantHintAnchor::AfterAssistantMessage)
        ?: SessionAssistantHintAnchor.TimelineEnd
```

This checks only whether already-published fields exist. It must not calculate mastery, action, severity, graph edges, token use, or generation outcome.

- [ ] **Step 2: Implement the composable entry**

Create this public entry signature:

```kotlin
@Composable
fun SessionAssistantReplyHint(
    contract: SessionConversationContract?,
    onInteraction: (SessionAssistantInteraction) -> Unit,
    modifier: Modifier = Modifier
)
```

Its required behavior:

- Return when `contract == null` or `!contract.hasInlineLearningHint()`.
- Use `rememberSaveable(contract.sessionId, contract.turnId) { mutableStateOf(false) }`; it must start `false` for every new turn.
- Render one `Surface` with `testTag("session_assistant_inline_hint")`, Chinese `contentDescription = "查看本轮学习提示"`, and `defaultMinSize(minHeight = 48.dp)`.
- Render the literal “本轮学习提示” in `FontStyle.Italic` and `FormalColors.Muted`; an optional status dot uses only low-opacity `FormalColors.Muted`.
- Click only sets local `expanded = true`.
- While expanded, call `SessionAssistantPanel(contract, ...)`. For `DISMISS`, first set `expanded = false`, then forward exactly `DISMISS`; all other interactions forward unchanged.

Only import Compose primitives and existing `core:design` tokens. Do not use gradients, blur, infinite transitions, or a nested scroll container.

- [ ] **Step 3: Run the resolver tests**

Run the Task 1 command again.

Expected: `BUILD SUCCESSFUL`.

### Task 3: Refine the existing sheet into a detail micro-panel

**Files:**

- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt`
- Modify: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantPanelStateTest.kt`

- [ ] **Step 1: Add a failing safe-detail test**

Add these assertions to the existing state suite:

```kotlin
val ready = contract(
    state = GenerationState.READY,
    assistantText = "对话原文只属于聊天流。"
).toPanelState()
assertEquals("对话原文只属于聊天流。", ready.assistantText)
assertNull(ready.generationLabel)

val error = contract(
    state = GenerationState.ERROR,
    safeError = "暂时无法生成回复"
).toPanelState()
assertEquals("生成失败", error.generationLabel)
assertFalse(error.generationLabel.orEmpty().contains("https://"))
assertFalse(error.generationLabel.orEmpty().contains("sk-"))
```

- [ ] **Step 2: Apply the detail-only rendering rules**

Keep `SessionAssistantPanel(contract, onInteraction, modifier)` as its existing public API. Keep its one `ModalBottomSheet`, its root `verticalScroll`, the existing safe-warning mapper, and the existing five interaction routes.

Make these changes:

- Header title is `本轮学习提示`; show its knowledge-point subtitle only for non-blank data.
- Remove the `assistantText` display card from `GenerationSection`; it duplicates the chat reply.
- Render generation status and the already-safe error only when non-blank.
- Keep action plus next-step hint in one compact section.
- Hide evaluation, prerequisite, memory, source, historical-error, pending-review, and warning sections when their real field is absent.
- Keep the sheet clean with `FormalColors`, `FormalShapes`, and `LocalFormalTypeScale`; no dashboard grid, graph preview, progress bar, glass effect, thick borders, or looping animation.
- Preserve the 48dp controls and semantic tags. Change dismiss description to `关闭本轮学习提示`.

- [ ] **Step 3: Run the state suite**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*SessionAssistantPanelStateTest" --console=plain
```

Expected: `BUILD SUCCESSFUL`; all pre-existing safety tests still pass.

### Task 4: Lock event forwarding and accessibility names

**Files:**

- Create: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/SessionAssistantInteractionTest.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantPanel.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionAssistantReplyHint.kt`

- [ ] **Step 1: Write the event mapping test**

Expose the existing private mapper as an `internal` method named `toPanelInteractionForTest()`. Test this exact mapping:

```kotlin
assertEquals(SessionAssistantInteraction.RETRY, ConversationUiEventType.RETRY.toPanelInteractionForTest())
assertEquals(SessionAssistantInteraction.OPEN_CONTEXT, ConversationUiEventType.OPEN_CONTEXT.toPanelInteractionForTest())
assertEquals(SessionAssistantInteraction.OPEN_SOURCE, ConversationUiEventType.OPEN_SOURCE.toPanelInteractionForTest())
assertEquals(SessionAssistantInteraction.SHOW_EVALUATION, ConversationUiEventType.SHOW_EVALUATION.toPanelInteractionForTest())
assertEquals(SessionAssistantInteraction.DISMISS, ConversationUiEventType.DISMISS.toPanelInteractionForTest())
```

Do not expose Repository callbacks, event payloads, or domain objects.

- [ ] **Step 2: Verify exact semantic contracts in source**

| UI element | test tag | Chinese description |
|---|---|---|
| Inline hint | `session_assistant_inline_hint` | `查看本轮学习提示` |
| Dismiss | `session_assistant_dismiss` | `关闭本轮学习提示` |
| Retry | `session_assistant_retry` | `重试生成回复` |
| Evaluation | `session_assistant_show_evaluation` | `查看评估详情` |

All rows opening context/source must have stable tags and Chinese descriptions. Decorative icons use `contentDescription = null`.

- [ ] **Step 3: Run focused tests**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :feature:chat:testDebugUnitTest --tests "*SessionAssistantReplyHintTest" --tests "*SessionAssistantInteractionTest" --console=plain
```

Expected: `BUILD SUCCESSFUL`.

### Task 5: Track B acceptance and isolated commit

**Files:**

- Verify only: all Task 1–4 files.

- [ ] **Step 1: Run final module acceptance**

Run:

```powershell
$env:ANDROID_HOME = 'C:\Users\Lenovo\AppData\Local\Android\Sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
.\gradlew.bat :feature:chat:testDebugUnitTest :feature:chat:lint --console=plain
git diff --check
git diff --name-only -- mobile-native/core/model mobile-native/core/protocol mobile-native/core/llm mobile-native/core/data
```

Expected: test and lint pass; whitespace check is clean; frozen-path command has no output.

- [ ] **Step 2: Stage only Track B files**

Stage exactly the files listed in the immutable scope. Before committing, run `git diff --cached --name-only` and verify that no `app/`, `core/`, wiring, report, plan, or capability-request file appears.

- [ ] **Step 3: Commit and hand off**

Commit message:

```text
feat(chat-ui): add inline session learning hint
```

Do not push. Report the commit hash, test/lint evidence, frozen-path result, and that real timeline attachment remains Track A work.

## Track A handoff after the Track B commit

Track A later mounts `SessionAssistantReplyHint` after the real message whose ID equals `contract.generation.assistantMessageId`. When the ID is absent and `contract.hasInlineLearningHint()` is true, Track A mounts it in the existing end-of-timeline system-message slot. Track A also routes interactions to the existing Facade/Coordinator/navigation owner.

Track B must not implement this handoff. Host-level Compose/device verification occurs only after A4; it must follow the device-test hygiene in `F:\CodexHome\skills\reverse-tutor-development-guard\references\known-issues.md`.

## Plan self-review

- Covers the approved B placement, italic/light-gray entry, default collapse, detail micro-panel, empty-field hiding, failure safety, interaction-only callbacks, 48dp semantics, scrolling, and low-distraction visual constraints.
- Keeps every implementation edit in `feature/chat` and excludes all frozen/backend files.
- Defers only the real chat-timeline slot, which is explicitly owned by Track A.
