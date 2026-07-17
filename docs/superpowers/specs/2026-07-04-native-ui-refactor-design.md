# Native Android UI Refactor Design

Date: 2026-07-04
Scope: native Android UI/UX refactor for `mobile-native/`
Figma file: https://www.figma.com/design/GwFBajpcwoVTu52g74g4zq
Primary draft node: `10:2` (`精修 v2 / Native UI Spec`)

## Status

This document records the approved design direction after the Figma-first restart.
It does not approve PWA exit, replacement readiness, release signing, package-name changes, or pushing to `origin/main`.

PWA/Capacitor remains the migration source until Phase 6 and explicit approval.

## Design Inputs

- Original PWA mobile flow: direct product shell, sessions first, compact chat, context tabs, graph sheet, source import, settings.
- Existing native UI: Compose shell and feature screens already exist, but visible labels and actions are largely English and the bottom navigation exposes too many destinations.
- Existing native docs:
  - `tasks/native-android-ui-design-system.md`
  - `tasks/native-ui-ux-roadmap.md`
  - `tasks/native-android-ui-acceptance-checklist.md`
- Figma design-system discovery:
  - Target file has no subscribed local design libraries or local variables.
  - Material 3 Design Kit is available and was used as the design reference for Android-native structure.
  - The v2 draft uses project token mapping instead of pretending imported library instances are already wired.

## Core Direction

Native Android remains the product direction. The UI should use PWA-proven workflows and density, but be rebuilt with Compose, Material 3 structure, Android insets, native file picking, native back behavior, and native graph Canvas.

The first screen is `会话`, not a welcome page or marketing page.

Top-level navigation is:

```text
会话 -> 聊天 -> 脉络 -> 资料 -> 设置
```

`全局图谱`, `导入/导出`, and `关于诊断` are not primary bottom destinations. They become sub-entries:

- Global graph: inside `脉络`.
- Import/export: inside `设置 > 迁移与数据`.
- About/diagnostics: inside `设置 > 外观、记忆与诊断`.

## Figma V2 Screens

### 1. 会话

Purpose: resume or create learning work.

Required UI:

- Top bar: `Reverse Tutor`, subtitle `会话列表 · 本地优先`, action `新建`.
- Status strip: session count, no-model risk, source availability.
- Search field: `搜索会话、知识点、导入资料`.
- Filters: `全部`, `置顶`, `待配置`, `含资料`.
- Session cards with title, role/goal, unread/source/graph metadata, and actions.
- Empty state still exposes `新建会话`, `导入备份`, `配置模型`.

Implementation target:

- Refactor `SessionsScreen.kt` copy to Chinese.
- Use shared card, chip, status strip, and action components.
- Keep repository calls unchanged.

### 2. 聊天

Purpose: support the core teacher-to-student learning loop.

Required UI:

- Top bar uses active session title and action `脉络`.
- No-model state is explicit and recoverable.
- Timeline keeps role labels short: `学生 AI`, `我`.
- Message actions remain available but should move toward compact action menus where needed.
- Context evidence strip shows source hit status.
- Composer includes quote chip, image chip, text field, image action, and send action.
- Placeholder: `作为老师，回复学生 AI…`.

Implementation target:

- Refactor `ChatScreen.kt` copy and layout.
- Preserve `ChatGenerationRepository`, background generation, memory note creation, image draft, quote, and context evidence behavior.
- Use IME and navigation-bar padding so bottom navigation and composer do not overlap.

### 3. 脉络

Purpose: combine Context Hub and graph review into one session-centered workspace.

Required UI:

- Top bar: `学习脉络`.
- Sections: `概览`, `图谱`, `锚点`, `随笔`, `错因`, `设置`.
- Active session status strip.
- Native graph workspace with pan, zoom, select, and fit-view control.
- Node detail sheet with status, evidence links, and actions:
  - `回顾聊天`
  - `打开资料`
  - `编辑纠正`

Implementation target:

- Refactor `ContextHubScreen.kt` visible copy and hierarchy.
- Keep `KnowledgeGraphPanel.kt` and graph repository contracts intact unless the change is strictly UI-level.
- Global graph should be reachable from this area, not bottom nav.

### 4. 资料

Purpose: keep imported local learning materials inspectable and usable.

Required UI:

- Top bar: `资料库`, action `导入`.
- Status strip explains local retention.
- Actions: `添加文件`, `添加图片`, `重试失败`.
- Parser status chips:
  - `已解析`
  - `部分解析`
  - `等待能力`
  - `失败`
- Source cards must stay visible even when parsing is partial, deferred, unsupported, or failed.

Implementation target:

- Refactor `SourcesScreen.kt` copy and card density.
- Preserve source import, reprocess, highlighted evidence target, and repository behavior.
- Do not hide unsupported files.

### 5. 设置

Purpose: group risk-bearing configuration in predictable sections.

Required groups:

- `模型配置`
  - Provider preset, Base URL, model, API Key state, test connection, save.
- `迁移与数据`
  - `导入备份`, `导出数据`, `清空本地数据`.
  - Overwrite import and wipe require confirmation.
- `外观、记忆与诊断`
  - Theme, avatar visibility, global memo, about diagnostics, background tasks.

Implementation target:

- Refactor `SettingsFoundationScreen.kt` copy and grouping.
- Move import/export and about from primary bottom nav into settings sub-entry flow.
- Keep `NativeImportRepository`, `NativeExportRepository`, `LocalDataWipeRepository`, preferences, and SecretStore behavior intact.

## Shared Shell Rules

Primary bottom navigation:

```text
会话 / 聊天 / 脉络 / 资料 / 设置
```

Back behavior:

```text
sheet/dialog/menu -> current screen
脉络 -> 聊天
聊天 -> 会话
会话 -> system exit
```

Top bars use Chinese screen context and one primary action. Avoid exposing internal route names in normal UI.

## Visual System

Use the existing native token direction:

- Primary: `#1E6B5E`
- Secondary: `#7B5E2E`
- Tertiary: `#315F8A`
- Surface: `#F7FAF8`
- Surface variant: `#E0E9E5`
- Warning, success, error, disabled states from `ReverseTutorTokens.kt`

Shape rules:

- Cards and repeated items: 8dp radius.
- Buttons and chips: 6dp to 8dp radius.
- Do not nest cards inside cards.
- Keep page sections as direct surfaces or grouped rows.

Typography:

- Figma draft uses Inter only as a structural placeholder.
- Android implementation should use system Chinese font through `MaterialTheme.typography`.
- Do not scale font size by viewport width.

## Copy Rules

- Visible product copy is Chinese first.
- Do not leave primary buttons as `Open`, `Send`, `Settings`, `Sources`, `Delete`, or similar English labels.
- Use direct recovery language:
  - `未配置模型`
  - `等待能力`
  - `部分解析`
  - `清空本地数据`
  - `导入备份`
- Do not imply cloud sync, full parser support, PWA exit, or replacement readiness.

## Implementation Boundaries

Expected UI edit areas:

```text
mobile-native/app/src/main/java/com/reversetutor/preview/theme
mobile-native/app/src/main/java/com/reversetutor/preview/ui
mobile-native/app/src/main/java/com/reversetutor/preview/shell
mobile-native/feature/chat
mobile-native/feature/memory
mobile-native/feature/sources
mobile-native/feature/settings
```

Do not casually modify:

```text
mobile-native/core/data/*
mobile-native/core/model/*
mobile-native/core/protocol/*
mobile-native/core/llm/*
Room schema / entities / DAOs
BackgroundGenerationRepository
NativeImportRepository
NativeExportRepository
```

Any backend change needs separate justification and should not be hidden inside UI refactor work.

## Acceptance Checks

Before considering the UI refactor complete:

- App opens directly to `会话`.
- Bottom navigation has only five Chinese primary destinations.
- Chat composer and bottom navigation do not overlap with IME open.
- All primary labels and empty/error/status states are Chinese.
- No-model, parser status, source retention, import/export, and wipe states are honest.
- Dynamic type does not clip key text.
- Dark mode contrast is checked, not inferred.
- Debug build installs on device/emulator.
- Screenshots are captured for the five primary destinations.

## Next Implementation Slice

Recommended order:

1. App shell and navigation labels.
2. Shared UI components: top bar, bottom nav, status strip, chips, action buttons, cards.
3. Sessions screen.
4. Chat screen and composer.
5. Context Hub and graph shell.
6. Source Library.
7. Settings grouping and sub-entry navigation.

Stop before changing business/data layers unless a UI screen cannot compile without a narrowly scoped adapter.
