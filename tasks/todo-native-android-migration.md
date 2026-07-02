# TODO: Native Android Migration

## 1. Purpose

This TODO document turns the PRD, legacy entry inventory, ARCH, and PROJECT rules into phased module work. It is an audit checklist and ADworkflo input, not a code implementation.

Global execution rule:

- Work proceeds by module branches and ADworkflo task specs.
- Each phase may produce an internal preview APK only when explicitly requested.
- The first public replacement release is blocked until all `P0` legacy entries and approved `P1` paths are verified.

## 2. Task ID Format

Use:

```text
NATIVE-P<phase>-<number>
```

Example:

```text
NATIVE-P2-004
```

Each implementation task spec must include:

- Module owner.
- Branch name.
- Legacy entry IDs.
- Dependencies.
- Acceptance criteria.
- Automated tests expected.
- Device validation expected.
- Required ADworkflo outputs.

## 3. Phase Gates

| Phase | Name | Integration Gate |
|---|---|---|
| 0 | Planning and workflow setup | First-layer docs and ADworkflo artifacts exist |
| 1 | Native foundation | Preview app launches and core modules compile |
| 2 | Core user loop | User can create session, configure model, chat, and persist history |
| 3 | Background and reliability | Generation jobs and notifications are isolated and recoverable |
| 4 | Import/export compatibility | Legacy export data imports through append, overwrite, and new-space modes |
| 5 | Memory, graph, and sources | Context hub, native graph, and source import reach agreed parity target |
| 6 | Entry parity and PWA exit readiness | Replacement gate, device matrix, and migration runbook are complete |

## 4. Phase 0: Planning And Workflow Setup

### NATIVE-P0-001: Finalize First-Layer Documents

- Module owner: PM/Architecture
- Branch: documentation-only
- Dependencies: none
- Legacy IDs: all IDs tracked by inventory
- Scope:
  - Ensure PRD, legacy inventory, ARCH, PROJECT, and TODO exist.
  - Keep strategic decisions dated and traceable.
  - Keep PWA, Python, and native responsibilities clear.
- Acceptance criteria:
  - `tasks/prd-native-android-migration.md` exists.
  - `tasks/native-legacy-entry-inventory.md` exists.
  - `tasks/arch-native-android-migration.md` exists.
  - `tasks/project-native-android-migration.md` exists.
  - `tasks/todo-native-android-migration.md` exists.

### NATIVE-P0-002: Initialize ADworkflo

- Module owner: ADworkflo orchestration
- Branch: documentation-only
- Dependencies: NATIVE-P0-001
- Legacy IDs: all IDs as execution metadata
- Scope:
  - Initialize `.adworkflow/`.
  - Generate/update architecture manifest.
  - Create permissions, verification commands, module skill routing, and execution plan.
- Acceptance criteria:
  - `.adworkflow/ADWORKFLOW_PROFILE.json` exists.
  - `.adworkflow/architecture_manifest.json` exists.
  - `.adworkflow/permissions.md` exists.
  - `.adworkflow/verification_commands.md` exists.
  - `.adworkflow/module_skills.md` exists.
  - `.adworkflow/execution_plan.json` exists.

### NATIVE-P0-003: Prepare Protocol Fixture Strategy

- Module owner: `:core:protocol`
- Branch: `native/protocol-fixtures`
- Dependencies: NATIVE-P0-002
- Legacy IDs: LEG-010, LEG-031, LEG-032, LEG-043
- Scope:
  - Identify first fixture files needed from current PWA exports and Python reference behavior.
  - Define fixture categories for session export, full backup, graph snapshot, preset, LLM profile, and import errors.
  - Record which fixtures are required before implementation starts.
- Acceptance criteria:
  - Task spec lists fixture names and schema versions.
  - Import/export tasks know which fixtures are blocking.
  - No real user data is committed as a fixture.

## 5. Phase 1: Native Foundation

### NATIVE-P1-001: Scaffold Native Android Project

- Module owner: `:app`
- Branch: `native/android-foundation`
- Dependencies: Phase 0
- Legacy IDs: LEG-001
- Scope:
  - Create `mobile-native/` Gradle project.
  - Configure Kotlin, Compose, Material 3, app module, and baseline core/feature modules.
  - Use internal package id `com.reversetutor.preview`.
- Acceptance criteria:
  - Native project compiles.
  - App launches into a Compose screen.
  - Existing `mobile/` PWA package config remains untouched.
- Automated tests:
  - Gradle assemble/check command once project exists.
- Device validation:
  - Launch smoke on emulator or device when preview APK is requested.

### NATIVE-P1-002: App Shell, Theme, And Navigation Host

- Module owner: `:app`
- Branch: `native/android-foundation`
- Dependencies: NATIVE-P1-001
- Legacy IDs: LEG-001, LEG-002, LEG-039, LEG-040
- Scope:
  - Add top-level navigation destinations.
  - Add Material 3 theme foundation.
  - Add Android back behavior model for modals, chat, context hub, and session list.
- Acceptance criteria:
  - Main destinations exist as native routes.
  - Back behavior follows ARCH navigation rules.
  - Empty destinations are clearly marked as internal preview placeholders.

### NATIVE-P1-003: Core Model And Room Schema Foundation

- Module owner: `:core:model`, `:core:data`
- Branch: `native/local-data`
- Dependencies: NATIVE-P1-001
- Legacy IDs: LEG-005, LEG-012, LEG-020, LEG-026, LEG-031, LEG-032, LEG-034
- Scope:
  - Add domain models for spaces, sessions, messages, LLM profiles, memory, graph, sources, jobs, and import batches.
  - Add initial Room schema and DAO boundaries.
  - Add migration versioning policy.
- Acceptance criteria:
  - Space is a first-class user-visible domain concept.
  - User data entities include `spaceId` where relevant.
  - Data layer compiles independently from UI.
- Automated tests:
  - Entity mapping and DAO smoke tests.
  - Migration baseline test once schema exists.

### NATIVE-P1-004: Settings Foundation

- Module owner: `:feature:settings`, `:core:data`
- Branch: `native/settings-profile`
- Dependencies: NATIVE-P1-002, NATIVE-P1-003
- Legacy IDs: LEG-003, LEG-004, LEG-037, LEG-038, LEG-044
- Scope:
  - Add settings destination.
  - Add DataStore-backed theme/avatar/memo placeholders.
  - Add about/diagnostics placeholder with native app metadata.
- Acceptance criteria:
  - Settings route is reachable.
  - Local preferences persist across app restart.
  - PWA install hints are not carried into native about UI.

### NATIVE-P1-005: Legacy Coverage Registry

- Module owner: QA/release
- Branch: `native/legacy-entry-parity`
- Dependencies: NATIVE-P1-002
- Legacy IDs: LEG-001 to LEG-044
- Scope:
  - Create native coverage checklist from `tasks/native-legacy-entry-inventory.md`.
  - Track status: not started, in progress, implemented, verified, waived, blocked.
- Acceptance criteria:
  - Every P0/P1 item has a native owner and planned verification surface.
  - Missing P0 items are visible as release blockers.

### NATIVE-P1-006: Phase 1 Preview Validation

- Module owner: QA/release
- Branch: `native/integration-phase-1-foundation`
- Dependencies: NATIVE-P1-001 through NATIVE-P1-005
- Legacy IDs: LEG-001, LEG-002, LEG-039
- Scope:
  - Integrate foundation tasks.
  - Build internal APK only when requested.
  - Record launch/navigation validation.
- Acceptance criteria:
  - Phase 1 integration compiles.
  - Validation record exists if APK is built.

## 6. Phase 2: Core User Loop

### NATIVE-P2-001: Session List And Session Actions

- Module owner: `:feature:chat`, `:core:data`
- Branch: `native/chat-main-flow`
- Dependencies: Phase 1 data and navigation
- Legacy IDs: LEG-005, LEG-006, LEG-007, LEG-038
- Scope:
  - Session list with search/filter, pin, status, unread, and avatar support.
  - Rename, delete, pin, export entry, avatar actions.
  - Proactive state display if enabled; explicit disabled state if deferred.
- Acceptance criteria:
  - Session CRUD actions persist.
  - Destructive actions require confirmation.
  - UI covers P0 session actions.
- Automated tests:
  - Repository/DAO tests for session actions.

### NATIVE-P2-002: New Session And Templates

- Module owner: `:feature:chat`, `:feature:settings`, `:core:protocol`
- Branch: `native/chat-main-flow`
- Dependencies: NATIVE-P2-001
- Legacy IDs: LEG-008, LEG-009, LEG-010, LEG-011, LEG-043
- Scope:
  - Built-in template create flow.
  - Custom profile form.
  - Preset import/export contract.
  - Initial source selection entry or immediate post-create import.
- Acceptance criteria:
  - User can create built-in and custom sessions.
  - Preset JSON is validated before use.
  - Missing source import implementation is represented by a safe handoff to sources module.
- Automated tests:
  - Preset fixture validation.
  - Profile persistence test.

### NATIVE-P2-003: Chat Timeline, Composer, Quote, And Actions

- Module owner: `:feature:chat`, `:core:data`
- Branch: `native/chat-main-flow`
- Dependencies: NATIVE-P2-001
- Legacy IDs: LEG-012, LEG-013, LEG-014, LEG-015, LEG-018, LEG-041
- Scope:
  - Message list with user/assistant bubbles.
  - Text composer and send action.
  - Image draft preview/cancel.
  - Quote reply and persisted quote context.
  - Message action sheet for quote, note, regenerate/archive/delete where applicable.
  - IME-safe layout.
- Acceptance criteria:
  - Messages persist and render after restart.
  - Quote context is stored and visible.
  - Keyboard does not obscure composer or latest message.
- Device validation:
  - Chat input and IME behavior on at least one Android target.

### NATIVE-P2-004: LLM Profiles And Secure Key Storage

- Module owner: `:feature:settings`, `:core:llm`, `:core:data`
- Branch: `native/llm-runtime`
- Dependencies: NATIVE-P1-003, NATIVE-P1-004
- Legacy IDs: LEG-026, LEG-027, LEG-028, LEG-031
- Scope:
  - Provider presets.
  - Protocol/base URL/model/key/capability fields.
  - Multiple profiles with save, switch, delete.
  - Android Keystore-backed key storage adapter.
  - Redacted or excluded key export behavior.
- Acceptance criteria:
  - API keys are not plain Room/DataStore fields.
  - Connection test can run through mock/provider abstraction.
  - Export never includes key material by default.
- Automated tests:
  - Profile validation fixtures.
  - Secret redaction tests.

### NATIVE-P2-005: Direct Provider Runtime And Generation Lifecycle

- Module owner: `:core:llm`, `:feature:chat`, `:core:data`
- Branch: `native/llm-runtime`
- Dependencies: NATIVE-P2-003, NATIVE-P2-004
- Legacy IDs: LEG-016, LEG-018, LEG-042
- Scope:
  - OpenAI-compatible strategy.
  - Anthropic-compatible strategy.
  - Streaming or pending reply state.
  - Queue/coalescing model.
  - Explicit no-model or local fallback state.
  - Provider capability handling for images.
- Acceptance criteria:
  - Android can chat without Python runtime.
  - Tests use mocks and never call real LLM providers.
  - Stale generation tokens are modeled before background phase.
- Automated tests:
  - Streaming parser tests.
  - Queue/stale-token unit tests.
  - No-config behavior fixture.

### NATIVE-P2-006: Phase 2 Preview Validation

- Module owner: QA/release
- Branch: `native/integration-phase-2-core-loop`
- Dependencies: NATIVE-P2-001 through NATIVE-P2-005
- Legacy IDs: LEG-005 to LEG-018, LEG-026 to LEG-028, LEG-041, LEG-042, LEG-043
- Scope:
  - Integrate core loop tasks.
  - Build internal APK only when requested.
  - Validate create session -> configure model -> chat -> persist history.
- Acceptance criteria:
  - Core loop works in preview build.
  - Validation record exists if APK is built.

## 7. Phase 3: Background And Reliability

### NATIVE-P3-001: WorkManager Generation Jobs

- Module owner: `:feature:chat`, `:core:data`, `:core:llm`
- Branch: `native/background-jobs`
- Dependencies: Phase 2 generation lifecycle
- Legacy IDs: LEG-016, LEG-017
- Scope:
  - Persist generation jobs.
  - Run queued work through WorkManager.
  - Recover, cancel, fail, or discard safely after app restart.
- Acceptance criteria:
  - Job state survives process restart.
  - Deleted-session jobs do not write messages.
  - Session-switch results do not attach to wrong UI.
- Automated tests:
  - Job state transition tests.
  - Deleted-session isolation test.

### NATIVE-P3-002: Notifications And Background Settings

- Module owner: `:feature:chat`, `:feature:settings`, `:app`
- Branch: `native/background-jobs`
- Dependencies: NATIVE-P3-001
- Legacy IDs: LEG-017, LEG-029, LEG-030
- Scope:
  - Completion/failure notifications.
  - Notification settings link/surface.
  - Proactive settings decision path.
- Acceptance criteria:
  - User-visible notification appears for completed or failed background generation where permission allows.
  - Disabled notification state is handled clearly.
- Device validation:
  - Notification permission and delivery on a real device or emulator.

### NATIVE-P3-003: Diagnostics And Error Records

- Module owner: `:feature:settings`, `:core:data`, `:core:llm`
- Branch: `native/background-jobs`
- Dependencies: NATIVE-P3-001, NATIVE-P3-002
- Legacy IDs: LEG-023, LEG-028, LEG-044
- Scope:
  - Store error/diagnostic records for failed provider calls and background jobs.
  - Provide diagnostic copy/export surface.
- Acceptance criteria:
  - User can inspect relevant failure details without seeing secrets.
  - Diagnostic export redacts sensitive fields.

### NATIVE-P3-004: Phase 3 Preview Validation

- Module owner: QA/release
- Branch: `native/integration-phase-3-background`
- Dependencies: NATIVE-P3-001 through NATIVE-P3-003
- Legacy IDs: LEG-016, LEG-017, LEG-023, LEG-028, LEG-029, LEG-030, LEG-044
- Scope:
  - Integrate background and reliability tasks.
  - Build internal APK only when requested.
  - Validate background reply, notification, cancellation, and error state.
- Acceptance criteria:
  - Background result isolation is verified.
  - Validation record exists if APK is built.

## 8. Phase 4: Import / Export Compatibility

### NATIVE-P4-001: Versioned Protocol Schemas

- Module owner: `:core:protocol`
- Branch: `native/import-export`
- Dependencies: NATIVE-P0-003, Phase 1 data model
- Legacy IDs: LEG-010, LEG-031, LEG-032, LEG-043
- Scope:
  - Define export/import schema versions.
  - Define validation result format.
  - Define unknown-field behavior.
  - Add fixture loader utilities.
- Acceptance criteria:
  - Protocol schemas cover session export, full backup, graph snapshot, preset, LLM profile, and import batch result.
  - Fixtures include success, warning, and invalid cases.
- Automated tests:
  - Schema validation fixture tests.

### NATIVE-P4-002: Native Import Pipeline

- Module owner: `:feature:settings`, `:core:data`, `:core:protocol`
- Branch: `native/import-export`
- Dependencies: NATIVE-P4-001
- Legacy IDs: LEG-032
- Scope:
  - Android file picker for exported JSON.
  - Read, detect, validate, dry-run, confirm, write, and report.
  - Create `ImportBatch`.
- Acceptance criteria:
  - Invalid file does not write data.
  - Partial invalid records are skipped with visible warnings where safe.
  - Import never requires direct IndexedDB access.
- Automated tests:
  - Import transaction tests.
  - Invalid fixture tests.

### NATIVE-P4-003: Append, Overwrite, And New-Space Modes

- Module owner: `:core:data`, `:feature:settings`
- Branch: `native/import-export`
- Dependencies: NATIVE-P4-002
- Legacy IDs: LEG-032
- Scope:
  - Append to current space.
  - Overwrite current space with confirmation.
  - Create new visible imported space.
  - Idempotency and duplicate handling.
- Acceptance criteria:
  - All three modes are user-visible and tested.
  - Overwrite has destructive confirmation.
  - Re-running import does not cause uncontrolled duplicates.
- Automated tests:
  - Mode-specific transaction tests.
  - Duplicate/idempotency tests.

### NATIVE-P4-004: Native Export Flows

- Module owner: `:feature:settings`, `:core:protocol`, `:core:data`
- Branch: `native/import-export`
- Dependencies: NATIVE-P4-001, Phase 2 data
- Legacy IDs: LEG-006, LEG-010, LEG-031
- Scope:
  - Export current session.
  - Export global graph snapshot.
  - Export full backup.
  - Export preset.
  - Use Android share/save intents.
- Acceptance criteria:
  - Export payloads validate against protocol fixtures.
  - Key material is excluded or redacted by default.

### NATIVE-P4-005: Destructive Wipe

- Module owner: `:feature:settings`, `:core:data`
- Branch: `native/import-export`
- Dependencies: Phase 1 data foundation
- Legacy IDs: LEG-033
- Scope:
  - Clear all local data with explicit warning.
  - Preserve app shell stability after wipe.
- Acceptance criteria:
  - Wipe is confirmed and auditable.
  - App returns to empty default state.
- Automated tests:
  - Data wipe repository test.

### NATIVE-P4-006: First-Launch Import Prompt

- Module owner: `:app`, `:feature:settings`
- Branch: `native/package-release`
- Dependencies: NATIVE-P4-002
- Legacy IDs: LEG-032
- Scope:
  - Design replacement-build first-launch import guidance.
  - Keep inactive for `com.reversetutor.preview` unless preview testing needs it.
- Acceptance criteria:
  - Replacement build can guide users to import exported backup.
  - No cover-install behavior is claimed before package readiness tests.

### NATIVE-P4-007: Phase 4 Preview Validation

- Module owner: QA/release
- Branch: `native/integration-phase-4-import`
- Dependencies: NATIVE-P4-001 through NATIVE-P4-006
- Legacy IDs: LEG-010, LEG-031, LEG-032, LEG-033
- Scope:
  - Integrate import/export tasks.
  - Build internal APK only when requested.
  - Validate import modes with representative fixtures.
- Acceptance criteria:
  - Append, overwrite, and new-space import modes pass.
  - Validation record exists if APK is built.

## 9. Phase 5: Memory, Graph, And Sources

### NATIVE-P5-001: Context Hub Shell

- Module owner: `:feature:memory`, `:app`
- Branch: `native/memory-graph`
- Dependencies: Phase 1 navigation and data
- Legacy IDs: LEG-019, LEG-024
- Scope:
  - Session context hub entry from chat.
  - Tabs or destinations for graph, anchors, notes, errors, and session settings.
- Acceptance criteria:
  - Context hub is reachable from active chat.
  - Session settings and persona warning path are represented.

### NATIVE-P5-002: Anchors, Notes, And Errors

- Module owner: `:feature:memory`, `:core:data`, `:feature:sources`
- Branch: `native/memory-graph`
- Dependencies: NATIVE-P5-001
- Legacy IDs: LEG-021, LEG-022, LEG-023
- Scope:
  - Anchor/requirement list and add/delete flow.
  - Notes created from messages, edit, delete.
  - Error/misconception list with linked evidence.
- Acceptance criteria:
  - Memory records persist and link back to messages/sources where applicable.
  - Create-from-message actions work from chat.

### NATIVE-P5-003: Native Knowledge Graph Engine

- Module owner: `:feature:memory`, `:core:data`
- Branch: `native/memory-graph`
- Dependencies: NATIVE-P5-001, graph data model
- Legacy IDs: LEG-020, LEG-025
- Scope:
  - Native Compose Canvas or equivalent graph renderer.
  - Layout state, pan, zoom, hit testing, selection, details.
  - Persist node positions where needed.
  - Per-session and global graph modes.
- Acceptance criteria:
  - Graph is not WebView-based.
  - Pan, zoom, select, detail sheet, and related jump flows work.
  - Large/empty/loading/invalid graph states are handled.
- Automated tests:
  - Graph state/layout tests.
- Device validation:
  - Gesture and graph detail test on touch device/emulator.

### NATIVE-P5-004: Graph Edit And Review Parity

- Module owner: `:feature:memory`, `:core:data`
- Branch: `native/memory-graph`
- Dependencies: NATIVE-P5-003
- Legacy IDs: LEG-020, LEG-025
- Scope:
  - Node edit/save where legacy supports it.
  - Semantic fragment/card review.
  - Related source/chat jump.
- Acceptance criteria:
  - All graph parity checklist items are implemented or explicitly waived.
  - Missing P0 graph behavior blocks replacement release.

### NATIVE-P5-005: Source Library And Simple Local Parsers

- Module owner: `:feature:sources`, `:core:data`
- Branch: `native/sources`
- Dependencies: Phase 1 data foundation
- Legacy IDs: LEG-011, LEG-021, LEG-034, LEG-036
- Scope:
  - Android file picker.
  - Source storage, metadata, parser status, source chunks.
  - Local parsing for TXT and Markdown.
  - HTML local parsing/sanitization if feasible in selected library.
  - Source cards/snippets and reprocess entry.
- Acceptance criteria:
  - Imported source is visible even if parsing is partial or failed.
  - Parser status is user-visible.
  - Source chunks are available to chat/context modules where supported.
- Automated tests:
  - Parser fixture tests for TXT/Markdown and selected HTML path.

### NATIVE-P5-006: Images And Complex Parser Status

- Module owner: `:feature:sources`, `:feature:chat`, `:core:llm`
- Branch: `native/sources`
- Dependencies: NATIVE-P5-005, NATIVE-P2-005
- Legacy IDs: LEG-018, LEG-034, LEG-035
- Scope:
  - Image source import and chat attachment storage.
  - Capability-aware image use with LLM providers.
  - Explicit support status for PDF, DOCX, PPTX, and EPUB.
  - Reserve Python/API-assisted parser slots where local parser is deferred.
- Acceptance criteria:
  - Image attachments work with capability checks.
  - Every legacy source type has explicit status.
  - Unsupported/partial files do not disappear silently.

### NATIVE-P5-007: Memory And Source Context In Chat

- Module owner: `:feature:chat`, `:feature:memory`, `:feature:sources`, `:core:llm`
- Branch: `native/memory-graph`
- Dependencies: NATIVE-P5-002, NATIVE-P5-005
- Legacy IDs: LEG-012, LEG-021, LEG-022, LEG-023, LEG-036
- Scope:
  - Feed relevant memory/source evidence into turn request protocol.
  - Render citations or source references in chat.
  - Keep protocol aligned with Python reference behavior where applicable.
- Acceptance criteria:
  - Chat can display source/evidence references.
  - Missing source evidence degrades gracefully.
- Automated tests:
  - Turn request fixture tests.

### NATIVE-P5-008: Phase 5 Preview Validation

- Module owner: QA/release
- Branch: `native/integration-phase-5-memory-graph-sources`
- Dependencies: NATIVE-P5-001 through NATIVE-P5-007
- Legacy IDs: LEG-019 to LEG-025, LEG-034 to LEG-036
- Scope:
  - Integrate memory, graph, and sources tasks.
  - Build internal APK only when requested.
  - Validate graph gestures, context hub, notes, anchors, and source import.
- Acceptance criteria:
  - Native graph parity checklist is updated.
  - Source type status matrix is updated.
  - Validation record exists if APK is built.

## 10. Phase 6: Entry Parity And PWA Exit Readiness

### NATIVE-P6-001: Full Legacy Entry Audit

- Module owner: QA/release
- Branch: `native/legacy-entry-parity`
- Dependencies: Phases 1 to 5
- Legacy IDs: LEG-001 to LEG-044
- Scope:
  - Audit every P0/P1 legacy item against native implementation.
  - Mark implemented, verified, waived, or blocked.
  - Record any approved reduced behavior.
- Acceptance criteria:
  - No unapproved missing P0 item remains.
  - P1 deferrals have no data-loss risk and are documented.

### NATIVE-P6-002: Replacement Package Readiness

- Module owner: package/release
- Branch: `native/package-release`
- Dependencies: NATIVE-P6-001, NATIVE-P4-006
- Legacy IDs: LEG-001, LEG-030, LEG-032, LEG-044
- Scope:
  - Prepare official package id switch plan.
  - Define signing and cover-install checklist.
  - Verify first-launch import guidance for replacement path.
- Acceptance criteria:
  - Package switch runbook exists.
  - Cover-install validation is planned and later executed only with explicit approval.
  - Existing PWA signing assets are not changed during planning.

### NATIVE-P6-003: Regression And Device Matrix

- Module owner: QA/release
- Branch: `native/integration-phase-6-replacement-readiness`
- Dependencies: NATIVE-P6-001
- Legacy IDs: all P0/P1 IDs
- Scope:
  - Run automated tests required by module task specs.
  - Validate on owner's main phone, lower-end/older target, and modern emulator.
  - Record screenshots or failure notes.
- Acceptance criteria:
  - Device matrix is complete.
  - Failed flows are either fixed or marked release blockers.

### NATIVE-P6-004: Migration Guide

- Module owner: PM/Architecture, settings/import
- Branch: `native/package-release`
- Dependencies: NATIVE-P4-007, NATIVE-P6-001
- Legacy IDs: LEG-031, LEG-032, LEG-044
- Scope:
  - Write user migration guide: export from old version, install/launch native, import backup, choose import mode.
  - Explain what is not migrated, especially API keys.
- Acceptance criteria:
  - Guide is understandable for existing users.
  - Guide matches actual app behavior.

### NATIVE-P6-005: PWA Exit Decision

- Module owner: PM/Architecture
- Branch: documentation-only
- Dependencies: NATIVE-P6-001 through NATIVE-P6-004
- Legacy IDs: all IDs
- Scope:
  - Decide whether PWA/Capacitor exits APK release path.
  - Record approved timing and rollback plan.
- Acceptance criteria:
  - User explicitly approves PWA exit.
  - Native replacement evidence is complete.
  - Existing users have documented migration path.

## 11. High-Risk Task Review Requirements

The following tasks require review before integration:

- NATIVE-P1-003: Core model and Room schema foundation.
- NATIVE-P2-004: LLM profiles and secure key storage.
- NATIVE-P2-005: Direct provider runtime and generation lifecycle.
- NATIVE-P3-001: WorkManager generation jobs.
- NATIVE-P4-001: Versioned protocol schemas.
- NATIVE-P4-002: Native import pipeline.
- NATIVE-P4-003: Import modes.
- NATIVE-P4-004: Export flows.
- NATIVE-P5-003: Native knowledge graph engine.
- NATIVE-P5-005: Source library and simple local parsers.
- NATIVE-P5-006: Images and complex parser status.
- NATIVE-P6-002: Replacement package readiness.

## 12. Initial Task Spec Creation Order

Create task specs in this order:

1. NATIVE-P1-001
2. NATIVE-P1-002
3. NATIVE-P1-003
4. NATIVE-P1-004
5. NATIVE-P1-005
6. NATIVE-P2-001
7. NATIVE-P2-004
8. NATIVE-P2-005
9. NATIVE-P4-001
10. NATIVE-P4-002
11. NATIVE-P5-003
12. NATIVE-P6-001

This order gives implementation workers enough structure without forcing all downstream specs to be frozen before foundation decisions are tested.

## 13. Open TODO Decisions

- Select exact Android dependency injection approach: Hilt or lightweight manual DI.
- Select exact Android HTTP client and serialization libraries.
- Select local parser libraries for PDF, DOCX, PPTX, and EPUB.
- Decide proactive conversation behavior for first native replacement release.
- Decide whether no-model fallback should mimic legacy mock or become a clearer no-model state.
- Confirm first supported export JSON schema version.
- Confirm the older/lower-end Android validation target.
