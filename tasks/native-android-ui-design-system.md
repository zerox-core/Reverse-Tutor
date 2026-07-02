# Native Android UI Design System

Date: 2026-07-01
Task: `NATIVE-UX-001`
Scope: native Android product UI system for `mobile-native/`

## 1. Purpose

This document defines the product UI system for the native Android migration. It is a handoff artifact for `NATIVE-UX-002`, `NATIVE-UX-003`, `NATIVE-UX-004`, `NATIVE-UX-005`, and the later Phase 5 UI specs.

The native app is not a marketing page. The first screen must be a usable product surface, beginning with the session home and clear entry points into chat, settings, sources, and context. The design system must preserve the legacy product map while making the Android implementation feel native, stable, accessible, and easier to verify.

This document does not claim functional parity, does not update the legacy coverage registry, and does not approve replacement readiness.

## 2. Product Stance

Reverse Tutor is a local-first AI learning tool where the user teaches, tests, and corrects an AI student. The UI should make the learning loop obvious:

- Start or resume a learning session.
- Teach or challenge the AI in chat.
- See generation status without losing control of the current session.
- Capture learning evidence as notes, anchors, errors, sources, and graph relationships.
- Configure providers and data migration safely.

The first native phase follows the legacy functional layout closely. It may reduce visual noise and use Android-native patterns, but it must not remove or hide legacy P0 entry points before parity is verified.

## 3. Design Principles

1. Product first screen, not a landing page.
   - Launch to `Sessions`, not to a hero, explainer, or promotional panel.
   - Empty state still contains product actions: create session, import backup, configure model.

2. Migration-safe before redesign-heavy.
   - Existing users should recognize where sessions, chat, context, graph, sources, import/export, and settings live.
   - Major reorganization waits until after functional parity is stable.

3. Local-first and evidence-centered.
   - Data ownership, source status, API key handling, and import results must be visible.
   - The UI should never imply cloud sync, server parsing, or live provider calls when they are not active.

4. Native Android interaction.
   - Use Compose, Material 3, Android file picker, Android share/save intents, native notifications, and native back behavior.
   - Do not wrap PWA pages or WebView graph surfaces as replacement UI.

5. State honesty.
   - Pending, loading, no-model, failed, unsupported, deferred, destructive, and empty states need explicit copy and recovery actions.
   - Deferred work must appear as a safe handoff, not as silent disappearance.

6. Verification-ready UI.
   - Components need stable semantics, predictable sizing, and screenshot-friendly states.
   - Design choices must be checkable through Compose state tests, device smoke, screenshots, and accessibility inspection.

## 4. Information Architecture

### 4.1 App Hierarchy

```text
App launch
  -> Sessions
       -> New session
       -> Session actions
       -> Open chat
  -> Chat
       -> Message timeline
       -> Composer
       -> Generation state
       -> Message actions
       -> Session context hub
  -> Context hub
       -> Overview
       -> Graph
       -> Anchors
       -> Notes
       -> Errors
       -> Session settings
  -> Sources
       -> Source library
       -> File import
       -> Parser status
       -> Source snippets and reprocess
  -> Settings
       -> LLM profiles
       -> Import and export
       -> Data wipe
       -> Theme, avatar, global memo
       -> Notifications and proactive behavior
       -> About and diagnostics
```

`About` is a Settings subroute, not a primary bottom destination. `Import/export` belongs under Settings unless a replacement first-launch import prompt is active.

### 4.2 Top-Level Navigation

Primary destinations:

| Destination | Role | Legacy coverage |
|---|---|---|
| Sessions | Default first screen, resume/create/manage sessions | LEG-005 to LEG-011 |
| Chat | Active session conversation and composer | LEG-012 to LEG-018, LEG-041, LEG-042 |
| Context | Per-session evidence hub plus global graph entry | LEG-019 to LEG-025 |
| Sources | Source library, import, parser status | LEG-011, LEG-034 to LEG-036 |
| Settings | Provider profiles, data, preferences, diagnostics | LEG-003, LEG-004, LEG-026 to LEG-033, LEG-037, LEG-038, LEG-044 |

Small phones may show 4 bottom items plus a `More` or Settings overflow if labels would crowd. Main phones can show 5 items only if every item keeps a 48dp touch target and readable label at large text. Tablets may use a navigation rail.

### 4.3 Back Behavior

Android system back follows this order:

1. Close active dialog, menu, sheet, or snackbar action mode.
2. Close graph detail or source detail sheet.
3. Exit Context hub back to the active Chat.
4. Exit Chat back to Sessions.
5. From Sessions, allow app-level system exit.

Background results must never force navigation to a different session. A completed or failed job may show a notification, badge, or in-session state only after the destination verifies the session id.

### 4.4 First-Launch Import Prompt

The prompt is allowed only for the future official replacement path after package readiness approval. It is not the default `com.reversetutor.preview` launch screen.

If active, the first-launch prompt is a modal task over the product shell:

- Primary action: select exported backup.
- Secondary action: continue to Sessions.
- Required explanation: direct IndexedDB migration is not used; API keys are not imported by default.
- Required modes after file validation: append, overwrite, new space.

## 5. Screen Hierarchy

### 5.1 Sessions

Purpose: resume or create learning work.

Layout:

- Top app bar: product/session context, status, search or filter action, create action.
- Search field and filter chips below the bar.
- Session list with pinned-first ordering and compact metadata.
- Empty state with `New session`, `Import backup`, and `Configure model` actions.

Session card content:

- Title.
- Role or learning mode.
- Last activity or status.
- Badges for pinned, unread, proactive state, no-model risk, or deferred source handoff.
- Avatar only when global/session visibility allows it.

Session actions:

- Primary open action on the card.
- Overflow or long-press menu for rename, pin, delete, export, avatar.
- Destructive delete requires confirmation and describes whether data is archived or removed.

### 5.2 New Session

Purpose: create a recognizable legacy-compatible session.

Use a full-screen or large bottom-sheet flow when the form grows beyond a short dialog. Required sections:

- Built-in templates.
- Imported preset.
- Custom profile fields: title, role, goal, deadline, personality/profile text, tags, strategy settings, avatar controls.
- Initial source handoff or source selection entry.

Validation:

- Required fields show inline errors.
- Preset import validates schema and rejects key material.
- Source import deferral is visible and leads to Sources when available.

### 5.3 Chat

Purpose: complete the core Reverse Tutor loop.

Layout:

- Header: active session title, mode/status, context hub action.
- Generation state strip below header when pending, streaming, failed, no-model, or unsupported vision is active.
- Timeline fills remaining space.
- Composer is pinned to bottom, uses IME and navigation bar padding, and keeps latest message reachable.

Message bubbles:

- User messages align end; AI/student messages align start.
- Role label is short and stable.
- Quote context appears above the message body.
- Citations and evidence links appear below the relevant sentence or message body, not only in a hidden menu.
- Process/thinking summaries use a collapsible section if present.

Composer:

- Text field, send action, attachment action, quote chip, image draft chip.
- Send disabled for blank prompts with no hidden side effect.
- Image action checks provider capability before generation and reports unsupported vision clearly.

Message actions:

- Quote, note, regenerate, delete/archive.
- Note and regenerate must either perform the action or show a route-specific deferred/recovery state.
- Destructive delete/archive requires confirmation if data loss or irreversible behavior is possible.

### 5.4 Settings

Purpose: configure risk-bearing product behavior.

Settings is grouped, not a single flat form:

1. LLM profiles.
2. Import/export and data.
3. Appearance and avatar.
4. Global memo.
5. Notifications and proactive behavior.
6. About and diagnostics.

LLM profile rules:

- Presets are selectable cards or chips.
- Profile list shows active/inactive state, provider, model, endpoint summary, key status.
- API key entry uses password visual transformation and is never echoed into diagnostic copy.
- Test connection uses a clear result state and never appears to run live checks in automated tests.

Data actions:

- Import/export/wipe are visually separated from ordinary preferences.
- Wipe uses danger styling, typed confirmation or strong confirmation copy, and a result state.

### 5.5 Import And Export

Purpose: migrate and protect user data.

Import flow:

```text
Select file
  -> Detect schema
  -> Validate
  -> Dry-run summary
  -> Choose mode
  -> Confirm
  -> Write transaction
  -> Result report
```

Mode selector:

- Append to current space.
- Overwrite current space.
- Import into new space.

Dry-run summary:

- Source file name and schema.
- Records to insert, update, skip, or reject.
- API key handling.
- Warnings and blocking errors.

Result report:

- Counts by entity type.
- Skipped records and reasons.
- Target space.
- Next actions: open imported space, export report, return to Sessions.

Export flow:

- Current session.
- Global graph snapshot.
- Full backup.
- Preset.
- Android share/save target.
- Key material excluded or redacted by default.

### 5.6 Context Hub

Purpose: make learning evidence usable inside a session.

Context hub sections:

- Overview: session learning summary, active anchors, recent evidence, source status.
- Graph: per-session graph with native Canvas interaction.
- Anchors: requirements, source anchors, imported materials.
- Notes: notes created from messages and editable notes.
- Errors: misconception/error history with linked evidence.
- Session settings: persona, strategy, deadline, avatar, change warning.

Evidence placement:

- Every note, error, anchor, and graph detail should expose its linked message/source when available.
- Links back to chat or source detail must preserve current navigation state.

### 5.7 Graph

Purpose: inspect and edit learning relationships natively.

Required interaction states:

- Pan.
- Zoom.
- Select and deselect node.
- Edge affordance for relationship inspection.
- Detail sheet.
- Related chat/source jump.
- Semantic fragment/card review.
- Node edit/save where legacy supports it.
- Global graph and per-session graph modes.

Graph state rules:

- Empty graph: explain why and provide next action.
- Loading graph: show progress without blocking system back.
- Large graph: use clustering, search, or overview controls rather than over-drawing unreadable labels.
- Invalid graph: show recoverable error and diagnostic entry.

### 5.8 Sources

Purpose: import and inspect local learning materials.

Source library card:

- Title.
- Type.
- Parser status.
- Last processed time.
- Chunk/snippet count if available.
- Linked sessions or anchors.

Parser statuses:

| Status | Meaning | User action |
|---|---|---|
| supported_local | Parsed locally and usable | Open snippets, attach to chat |
| partial_local | Some text or metadata extracted | Review warnings, continue |
| queued_for_future_api | Requires future assisted parsing path | Keep file, retry when supported |
| unsupported | Type cannot be processed in current build | Remove or keep as reference |
| failed | Attempted parsing failed | Retry, inspect error, remove |

Files must remain visible after partial, deferred, unsupported, or failed outcomes. The app must not silently discard user-selected materials.

## 6. Design Tokens

### 6.1 Color Roles

The current preview seed uses:

- Primary: `#1E6B5E`
- Secondary: `#7B5E2E`
- Tertiary: `#315F8A`
- Surface: `#F7FAF8`
- Surface variant: `#E0E9E5`
- On surface: `#18211F`
- On surface variant: `#46534F`
- Outline: `#71817B`

`NATIVE-UX-002` should convert this into a complete light and dark Material 3 color scheme. Required semantic roles:

| Role | Use |
|---|---|
| primary | Main navigation selection, primary action, active learning state |
| secondary | Supporting status, settings groups, source/library accents |
| tertiary | Graph/source/evidence accents |
| error | Validation failures and destructive confirmations |
| warning | Partial parser, risky import, deferred capability |
| success | Completed import/export, saved profile, verified state |
| info | Diagnostics, neutral background job status |
| surface | App background and full-width sections |
| surfaceVariant | Cards, chips, grouped rows, quiet containers |
| outline | Dividers, input borders, chart/graph neutral strokes |

Dark mode must not be generated by simply dimming the light palette. It needs explicit contrast for bubbles, cards, chips, graph edges, parser badges, and disabled states.

### 6.2 Typography

Use Material type roles through `MaterialTheme.typography` rather than screen-local raw font sizes.

| Token | Use |
|---|---|
| displaySmall | Rare first-level empty/product title, not normal page headings |
| headlineMedium | Screen title on top-level pages |
| titleLarge | Section title, dialog title |
| titleMedium | Card title, settings group title |
| bodyLarge | Message text, main form text |
| bodyMedium | Supporting copy, metadata |
| labelLarge | Buttons and primary chips |
| labelMedium | Badges, compact labels |

Rules:

- Do not scale font size from viewport width.
- Dynamic type must reflow; fixed-height cards and bottom bars cannot clip text.
- Chat message text should remain readable at large font scale without overlapping action buttons.
- Truncate only metadata after meaningful content is preserved.

### 6.3 Spacing

Use a 4dp base scale.

| Token | Value | Use |
|---|---:|---|
| space1 | 4dp | Fine gaps, icon-label gap |
| space2 | 8dp | Chip gaps, compact row gap |
| space3 | 12dp | Composer internals, card internals |
| space4 | 16dp | Small-phone screen side padding |
| space5 | 20dp | Section gap |
| space6 | 24dp | Main-phone screen side padding |
| space8 | 32dp | Large section separation |

Screen padding:

- Small phone: 16dp.
- Main phone: 18dp to 24dp depending on density.
- Tablet/foldable: constrained content width for forms and detail panes.

### 6.4 Shape

| Token | Value | Use |
|---|---:|---|
| radiusSmall | 4dp | Badges, inner highlights |
| radiusMedium | 6dp | Buttons, chips, inputs where custom shape is needed |
| radiusCard | 8dp | Cards and grouped containers |
| radiusSheet | 16dp top corners | Modal bottom sheets only |

Do not nest cards inside cards. Page sections should be full-width surfaces or unframed layouts; cards are for repeated items, modals, and framed tools.

### 6.5 Size And Touch Targets

- Minimum interactive target: 48dp by 48dp.
- Compact icon buttons can draw smaller icons inside a 48dp target.
- Bottom navigation height must account for `navigationBarsPadding`.
- Composer minimum height must allow one-line input, quote chip, image chip, and send action without clipping.
- Graph controls must be reachable by touch and keyboard/accessibility actions where possible.

### 6.6 Elevation

Use elevation to clarify containment, not decoration.

| Level | Use |
|---|---|
| 0 | Page background, list body |
| 1 | Cards and quiet grouped rows |
| 2 | Top app bar |
| 3 | Composer, bottom navigation, sticky controls |
| 4+ | Dialogs, sheets, menus |

### 6.7 Iconography

Use Android Material icons or project-standard Compose vector icons for common actions:

- New/add.
- Search.
- Send.
- Attach image/file.
- Quote.
- More actions.
- Settings.
- Import/export.
- Delete.
- Close.
- Back.
- Graph.
- Source/file.

Every icon-only control needs a `contentDescription`. Unfamiliar icon-only actions need tooltip or visible label in menus/sheets.

### 6.8 Motion

Default motion tokens:

| Token | Duration | Use |
|---|---:|---|
| motionFast | 120ms | Press, chip selection, badge update |
| motionNormal | 180ms | Sheet/content transition, inline state change |
| motionSlow | 280ms | Screen transition, graph detail sheet |

Rules:

- Motion should confirm cause and effect; it should not hide slow operations.
- Respect system animator settings and reduced motion.
- Streaming/generation indicators should be calm and battery-conscious.
- Graph pan/zoom must feel direct, not springy.

## 7. Component Principles

### 7.1 App Shell

`NATIVE-UX-002` should define shared shell pieces:

- `ReverseTutorTheme` with light/dark schemes and typography.
- `ReverseTutorScaffold`.
- Top app bar with title, subtitle/status, and context actions.
- Adaptive navigation: bottom bar on phones, rail on wider screens.
- Shared dialog, bottom sheet, snackbar, and status strip styles.

### 7.2 Buttons And Actions

- Primary action per screen or flow.
- Secondary actions as text buttons or outlined buttons.
- Destructive action separated visually and confirmed.
- Long action labels must wrap or move to menus on small screens.
- Disabled controls need visible reason nearby or in supporting copy.

### 7.3 Cards And Lists

- Cards use `radiusCard` and stable padding.
- Avoid nested cards.
- Lists use stable keys and predictable row heights, but must expand for large text.
- Metadata uses chips or compact rows only when it remains readable.

### 7.4 Forms

- Group fields by task.
- Keep labels persistent.
- Validate inline.
- For sensitive fields, use password visual transformation and redaction in summaries.
- Use full-screen or bottom sheet flows for complex mobile forms, not cramped dialogs.

### 7.5 Status And Recovery

Common state pattern:

```text
State title
Cause
Impact
Recovery action
Diagnostic detail when useful
```

Required shared state components:

- Empty state.
- Loading state.
- Error state.
- No-model state.
- Unsupported capability state.
- Deferred feature state.
- Partial import/parser warning.
- Destructive confirmation.
- Success/result summary.

### 7.6 Copy Tone

Use plain product language:

- Say what happened.
- Say what the user can do next.
- Do not use marketing claims.
- Do not imply replacement readiness.
- Do not expose internal task ids in normal user-facing UI unless the screen is diagnostics.

## 8. Accessibility And Adaptivity

### 8.1 TalkBack And Semantics

- Every screen has a meaningful heading.
- Reading order matches visual order.
- Icon buttons have content descriptions.
- Message bubbles expose role, text, quote, and citations in order.
- Parser badges expose status and consequence.
- Graph nodes need semantic labels and alternate list/detail access for users who cannot use canvas gestures.

### 8.2 Dynamic Type

Must pass at large font scale:

- Sessions list.
- New session flow.
- Chat timeline and composer.
- Settings LLM profile form.
- Import dry-run/result.
- Context hub tabs.
- Source cards.

Rules:

- Avoid fixed heights for text containers.
- Let labels wrap or move into menus.
- Do not place critical actions only in horizontally scrolling text chips.

### 8.3 Dark Mode

Dark mode requires:

- Full color scheme.
- Bubble contrast.
- Parser badge contrast.
- Graph edge/node contrast.
- Dialog and sheet contrast.
- Disabled state legibility.

No screen can rely on screenshots from light mode to infer dark mode quality.

### 8.4 IME And Insets

Chat and form screens must:

- Use IME and navigation bar padding.
- Keep the focused field visible.
- Preserve cursor and draft state across simple navigation and rotation/recreation where current architecture supports it.
- Keep latest chat message reachable after send.
- Avoid bottom navigation overlapping the composer.

### 8.5 Small Screens

Small-phone rules:

- Avoid 5 wide text-only bottom navigation items if labels crowd.
- Move secondary actions into overflow menus or sheets.
- Prefer vertical stacking for forms.
- Keep destructive confirmations readable without horizontal scrolling.
- Ensure graph has reset/fit controls and a non-canvas detail/list fallback.

## 9. Handoff To UX Tasks

### 9.1 `NATIVE-UX-002`

Implement token-driven foundation:

- Complete `ReverseTutorTheme` with light and dark schemes.
- Add typography, spacing, shape, elevation, motion, and semantic color tokens.
- Add shared app shell/navigation components.
- Add shared state, dialog, sheet, button, chip, card, badge, and status strip components.
- Add UI state or screenshot tests where available.

This task should not rewrite every feature screen.

### 9.2 `NATIVE-UX-003`

Polish core-loop screens using UX-002 components:

- Sessions.
- New session flow.
- Chat timeline.
- Composer and IME behavior.
- Generation states.
- LLM profile settings.

Keep fake/runtime boundaries and no-model behavior honest. Do not add live provider calls.

### 9.3 `NATIVE-UX-004`

Design and implement import/export UI after Phase 4 protocol foundations:

- Import dry-run.
- Mode selector.
- Overwrite confirmation.
- Result report.
- Export type selector.
- First-launch import prompt for replacement path only.
- Data wipe confirmation/result.

Do not implement protocol validation outside Phase 4 ownership.

### 9.4 `NATIVE-UX-005`

Apply foundation to Phase 5 surfaces:

- Context hub shell.
- Graph surface states.
- Source library.
- Parser status.
- Evidence references.

Do not use WebView graph carry-over. Do not label deferred parser support as complete.

### 9.5 `NATIVE-UX-007` To `NATIVE-UX-010`

These tasks split Phase 5 detail work:

- `NATIVE-UX-007`: Context Hub IA and evidence placement.
- `NATIVE-UX-008`: Graph interaction state matrix.
- `NATIVE-UX-009`: Source parser status copy and recovery matrix.
- `NATIVE-UX-010`: Phase 5 UI QA with screenshots or explicit unavailable-device notes.

### 9.6 `NATIVE-UX-006`

Final UI QA is an all-up gate after Phases 3, 4, and 5. It cannot pass while unresolved P0 functional blockers remain and cannot approve replacement readiness by itself.

## 10. Evidence Rules

- A design document is not implementation evidence.
- A screenshot is UI evidence only for the exact build, device, screen, theme, font scale, and state shown.
- Coverage registry rows should change only when code and verification evidence exist.
- Device validation must record device/emulator name, Android version, tested flow, result, and screenshot or failure note.
- No signed or release APKs are built as part of this UI design-system task.
