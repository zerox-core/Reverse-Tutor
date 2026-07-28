# Android Baseline Closeout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current `Android` working tree into a committed, tested, pushed baseline, then synchronize that baseline into the Feishu frontend design branch without changing `main`.

**Architecture:** Keep the frozen repository, protocol, Room, and secret-storage boundaries intact. Remove the unused home-sheet launch request in favor of the existing `NewSessionPrefillRequest` flow, make chat presentation contracts drive production Compose UI, commit backend preference extensions separately, and keep unapproved design assets on the design branch.

**Tech Stack:** Kotlin 1.9.22, Jetpack Compose, JUnit 4, Gradle 8.2.1, Git/GitHub.

**Status:** Completed on 2026-07-29. `Android` passed 451 JVM tests, lint and `assembleDebug`; the verified baseline and design assets were synchronized to `feat/ux-polish-2026-07-27` while `main` remained unchanged.

---

### Task 1: Remove the unused new-session launch request

**Files:**
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/SessionsScreen.kt`
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/FormalHomeScreen.kt`
- Modify: `mobile-native/app/src/test/java/com/reversetutor/preview/shell/ChallengeRuntimeCoordinatorTest.kt`

- [ ] Delete `NewSessionLaunchContext` and `NewSessionLaunchRequest` from `SessionsScreen.kt`.
- [ ] Delete the unused `newSessionLaunchRequest` parameter and its consumed-request state/effect.
- [ ] Restore the home sheet to its single default presentation; challenge creation continues through `ChallengeSessionLaunchDecision.Create(NewSessionPrefillRequest)`.
- [ ] Delete the obsolete unique-home-sheet-request test and imports.
- [ ] Run `:feature:chat:testDebugUnitTest` and the challenge runtime test class.

### Task 2: Connect chat contracts to production UI

**Files:**
- Modify: `mobile-native/feature/chat/src/main/java/com/reversetutor/feature/chat/ReverseTeachingChatScreen.kt`
- Test: `mobile-native/feature/chat/src/test/java/com/reversetutor/feature/chat/ChatPresentationContractsTest.kt`

- [ ] Apply `ChatComposerLayout.Height`, `SendSize`, and `TrailingInset` to `ReverseTeachingComposer`.
- [ ] Add a production overflow button and menu to `ReverseTeachingChatHeader`.
- [ ] Dispatch `SessionSettings`, `Sources`, and `Export` through their existing callbacks.
- [ ] Remove unused-parameter suppressions from `onOpenSources` and `onExport`.
- [ ] Run `:feature:chat:testDebugUnitTest`.

### Task 3: Commit the mixed working tree intentionally

**Files:**
- Product: modified Kotlin production and test files under `mobile-native/`
- Workflow: `.adworkflow/`, `.codegraph/index.json`, `.codex/AGENT_HEADER.md`, `AGENTS.md`, root progress files, this plan
- Design-only: `design-assets/`, `.superpowers/brainstorm/`

- [ ] Commit formal design, challenge, home, and session creation changes together by explicit paths.
- [ ] Commit the frozen preference extension separately with its persistence tests.
- [ ] Commit LLM profile UI and repository wiring separately.
- [ ] Commit chat presentation contract wiring separately.
- [ ] Commit workflow and QA evidence separately.
- [ ] Do not use `git add -A`.
- [ ] Keep `design-assets/` and `.superpowers/brainstorm/` out of `Android`; transfer them to the Feishu design branch.

### Task 4: Verify the clean Android baseline

**Files:**
- Update: `.adworkflow/worker_state.json`
- Update: `.adworkflow/verification_result.json`
- Update: `.adworkflow/review_findings.json`

- [ ] Run module unit tests for app, design, data, chat, memory, and settings.
- [ ] Run `:app:lintDebug`.
- [ ] Run `:app:assembleDebug`.
- [ ] Record exact commands, results, APK size, and SHA-256.
- [ ] Confirm `git status --short` is clean before push.

### Task 5: Publish and synchronize branches

**Files:**
- Branch: `Android`
- Branch: `feat/ux-polish-2026-07-27`

- [ ] Push the verified `Android` branch.
- [ ] Create an isolated worktree for `feat/ux-polish-2026-07-27`.
- [ ] Merge `origin/Android` into the design branch without touching `main`.
- [ ] Apply and commit design-only assets in the design branch.
- [ ] Push the design branch.
- [ ] Verify `main` is unchanged and both active branches are clean/tracked.
