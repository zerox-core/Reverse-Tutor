# Native UI Stitch A2 Button Logic and State Revision

Date: 2026-07-04

Scope: native Android UI/UX design only. No Compose implementation changes in this step.

## Goal

Continue from the Stitch A1 button-logic revision and fix the remaining UX issues before implementation:

- keep the no-bottom-navigation direction
- preserve one-primary-action-per-screen
- clean up the chat composer tool row
- remove duplicated settings titles
- define the drawer expanded state
- define the destructive confirmation dialog

The native app remains a Chinese-first learning and reverse-teaching tool. PWA/Capacitor remains a migration source and is not being exited in this design pass.

## Stable UX Rules

1. Top-level screens use a top-left menu button for the drawer.
2. Detail/session screens use a back arrow.
3. The drawer is navigation only, not a place for business CTAs.
4. Each screen has at most one primary business action.
5. Secondary actions use chips, icon buttons, text buttons, row menus, or local panels.
6. Actions stay close to their object.
7. Destructive actions are isolated in a danger area and require confirmation.
8. Visible UI text is Chinese-first.
9. Touch targets remain at least 48dp, with at least 8dp spacing.

## Screen Decisions

### Sessions

Primary action: `新建会话`

Use one visible entry only, preferably a floating action button or one top-level button. Session rows open chat by default. Row-specific actions move into a row menu.

### Chat

Primary action: `发送`

The composer has three secondary chips: `引用`, `图片`, `资料`. These must be plain Chinese labels or unambiguous icons with labels. Avoid quote icons that render like numbers or symbols.

Preferred screenshot: `01-chat-a2-refined.png`

### Context / Graph

Default primary action: none.

`采纳` appears only inside a selected node, memory candidate, or review row. `编辑`, `忽略`, and `回到聊天` remain secondary local actions.

### Source Library

Primary action: `导入资料`

Source row actions such as `加入聊天`, `重处理`, and `更多` remain contextual and lower priority. Backup operations stay in Settings.

### Settings

Page-level primary action: none.

Top bar title is `设置`. The body starts with supporting copy or the first section, not another large repeated `设置` heading.

LLM actions (`新建配置`, `测试`, `设为默认`) stay in the LLM section. Data operations (`导出数据`, `云端备份`, `导入会话包`) stay in data management. `擦除本地数据` is isolated in `危险区域`.

### Drawer Expanded State

The drawer contains navigation only:

- `会话`
- `上下文`
- `资料库`
- `Memory`
- `设置`

The current destination uses a muted selected state. Drawer background is light, with a scrim over the underlying page. No `新建会话`, `导入资料`, or other business CTA appears in the drawer.

### Destructive Confirmation

`擦除本地数据` opens a confirmation dialog:

- title: `擦除本地数据？`
- body explains local sessions, context, Memory, source index, and model config may be deleted
- actions: `取消` and `确认擦除`
- `确认擦除` is red and visually primary within the dialog
- the dialog uses a scrim and does not nest cards

## Stitch Output

Project: `3132646461749921577`

A1 screenshots:

- `mobile-native/build/stitch-screens-a1-button-logic/01-sessions-a1.png`
- `mobile-native/build/stitch-screens-a1-button-logic/02-chat-a1.png`
- `mobile-native/build/stitch-screens-a1-button-logic/03-context-a1.png`
- `mobile-native/build/stitch-screens-a1-button-logic/04-sources-a1.png`
- `mobile-native/build/stitch-screens-a1-button-logic/05-settings-a1.png`

A2 screenshots:

- `mobile-native/build/stitch-screens-a2-ux-states/01-chat-a2-refined.png`
- `mobile-native/build/stitch-screens-a2-ux-states/02-settings-a2.png`
- `mobile-native/build/stitch-screens-a2-ux-states/03-drawer-a2.png`
- `mobile-native/build/stitch-screens-a2-ux-states/04-wipe-confirm-a2.png`
- `mobile-native/build/stitch-screens-a2-ux-states/screens.json`

## Review Result

Accepted:

- A2 chat composer now uses clean Chinese chips: `引用`, `图片`, `资料`.
- `发送` remains the only chat primary action.
- Settings no longer repeats a large page title under the top bar.
- Drawer is navigation-only and uses a selected state for the current page.
- Destructive confirmation uses a scrim and a red `确认擦除` action.

Remaining design detail for implementation:

- Final native implementation should use real Material/Compose icons and accessibility labels.
- The drawer bottom `导入/导出` item should route to Settings/Data Management, not perform import/export directly.
- Confirmation dialog copy should be wired to the actual local data erase behavior without changing backend/data contracts.

## Native Prototype Landing

Implemented in the first native landing pass:

- App shell uses a navigation drawer instead of bottom navigation.
- Top-level pages use `菜单`; detail pages use `返回`.
- Drawer contains navigation only: `会话`, `上下文`, `资料库`, `Memory`, `设置`.
- Drawer `导入/导出` entry routes to data management instead of performing import/export directly.
- Chat composer uses secondary chips: `引用`, `图片`, `资料`.
- Chat `发送` remains the only primary composer action.
- Settings body no longer repeats the page title under the top bar.
- Settings has a separated `危险区域` with `擦除本地数据`.
- Wipe confirmation uses `擦除本地数据？`, `取消`, and red `确认擦除`.

Verification completed:

- `:app:testDebugUnitTest --tests com.reversetutor.preview.shell.AppNavigationStateTest`
- `:feature:chat:testDebugUnitTest --tests com.reversetutor.feature.chat.ChatUiStateTest -Pkotlin.incremental=false`
- `:feature:settings:testDebugUnitTest --tests com.reversetutor.feature.settings.SettingsFoundationModelTest`
- `:app:compileDebugKotlin`
- `:app:assembleDebug`

Verification pending:

- Installing `app-debug.apk` and taking device screenshots. Current ADB device list is empty and no local `emulator` command is available.

## Implementation Boundary

When approved, UI implementation should stay in:

- `mobile-native/app/src/main/java/com/reversetutor/preview/theme`
- `mobile-native/app/src/main/java/com/reversetutor/preview/ui`
- `mobile-native/app/src/main/java/com/reversetutor/preview/shell`
- `mobile-native/feature/chat`
- `mobile-native/feature/memory`
- `mobile-native/feature/sources`
- `mobile-native/feature/settings`

Do not modify frozen backend/protocol/data contracts during the UI pass.
