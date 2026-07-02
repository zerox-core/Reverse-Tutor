# ARCH: Native Android Migration

## 1. Architecture Purpose

This document turns the native Android migration PRD into an implementation architecture. It defines module boundaries, data ownership, protocol contracts, release gates, and verification surfaces for the new native Android line.

Inputs:

- `tasks/prd-native-android-migration.md`
- `tasks/native-legacy-entry-inventory.md`
- Existing PWA implementation in `static/app/index.html`
- Existing Python reference implementation and tests

Architectural goal:

Create a native Android mainline that can replace the current Capacitor/PWA APK without becoming another tangled implementation. Android should be fully native, Python should remain the protocol/API/test baseline, and PWA should become a transition/export source until replacement parity is reached.

## 2. Non-Negotiable Decisions

- Native project path: `mobile-native/`
- Internal package id: `com.reversetutor.preview`
- Official replacement package id: `com.reversetutor.app`, used only after replacement readiness approval
- UI stack: Kotlin + Jetpack Compose + Material 3
- Data stack: Room for relational/local-first data, DataStore for lightweight settings, Android Keystore-backed storage for secrets
- Background stack: WorkManager plus foreground-service patterns where the OS requires user-visible long-running work
- Graph implementation: native Compose Canvas or equivalent native drawing/input primitives, no WebView graph carry-over
- LLM phase-one runtime: Android direct provider calls; Python proxy/API is future optional
- Migration path: export-file import only; no primary IndexedDB scraping
- Import conflict model: append, overwrite, or new user-visible space
- Execution model: ADworkflo module branches, phase APKs, true-device validation, and task-level verification evidence

## 3. Repository Layout

```text
reverse-tutor/
  mobile/                         # Existing Capacitor/PWA transition line
  static/app/                     # Existing PWA app, export source and behavior reference
  server.py, engine.py, db.py     # Python reference/API/test baseline
  tests/                          # Python and PWA source-level regression tests
  tasks/
    prd-native-android-migration.md
    native-legacy-entry-inventory.md
    arch-native-android-migration.md
    project-native-android-migration.md        # future
    todo-native-android-migration.md           # future
  mobile-native/
    settings.gradle.kts
    build.gradle.kts
    app/
    core/
      protocol/
      model/
      data/
      llm/
    feature/
      chat/
      memory/
      sources/
      settings/
    qa/
      device-validation/           # optional docs/scripts, not necessarily an Android module
```

The existing `mobile/` directory must not be modified as part of native foundation work except for explicit compatibility/export tasks. The native line must not reuse WebView pages as primary UI.

## 4. Gradle Module Topology

### 4.1 App Module

`:app`

Responsibilities:

- Android application entry point
- Package id, signing/build variants, app icon, splash, navigation host
- Dependency injection root
- Top-level Compose theme
- Feature module wiring
- Internal package id and future official package id switch

Must not own:

- Business protocols
- Room entities beyond wiring
- LLM provider implementations
- Parser logic
- Graph layout engine internals

### 4.2 Core Modules

`:core:protocol`

Owns:

- Versioned import/export schemas
- Turn request/result contracts
- Background job state contracts
- Graph node/edge JSON contracts
- LLM profile contract
- Validation result models
- Fixture loader utilities for tests

Does not depend on Android UI.

`:core:model`

Owns:

- Domain models used across features
- Enums and value objects
- User-visible space model
- Session/message/memory/source/graph domain types

Does not own persistence annotations unless needed by mapping-only code.

`:core:data`

Owns:

- Room database
- Entities and DAO interfaces
- Repository implementations
- Local migrations
- DataStore-backed app preferences
- Secret storage adapter for API keys
- Import transaction coordinator

Depends on `:core:model` and `:core:protocol`.

`:core:llm`

Owns:

- Provider registry
- OpenAI-compatible client
- Anthropic-compatible client
- Streaming parser
- Capability inference
- Connection test/diagnostics
- Future Python proxy strategy interface, disabled by default

Depends on `:core:model`, `:core:protocol`, and networking libraries.

### 4.3 Feature Modules

`:feature:chat`

Owns:

- Session list UI
- Chat thread UI
- Composer, quote, image attachment, message actions
- Queue/pending/streaming presentation
- Chat ViewModels/use cases

`:feature:memory`

Owns:

- Context hub UI
- Anchors, notes, errors
- Per-session settings surface
- Knowledge graph UI and native graph interaction
- Global graph/insights screen

`:feature:sources`

Owns:

- Android file picker integration
- Source import flow
- Parser status display
- Source library/snippet/reprocess surfaces
- Image source import

`:feature:settings`

Owns:

- LLM profile UI
- Provider settings UI
- Data export/import/wipe UI
- Theme/avatar/global memo settings
- Update/about/diagnostics UI
- Proactive settings if enabled

## 5. Layering Rules

Use a simple Android architecture with UI, domain/use-case, and data layers.

```text
Compose Screen
  -> ViewModel
    -> UseCase / Coordinator
      -> Repository interface
        -> Room DAO / DataStore / LLM client / File parser
```

Rules:

- Compose screens render state and send events. They do not call DAOs, file parsers, or LLM clients directly.
- ViewModels expose immutable UI state and one-shot effects.
- Repositories own data mapping and transaction boundaries.
- Import/export writes must go through one transaction coordinator in `:core:data`.
- Feature modules can depend on core modules, but core modules cannot depend on feature modules.
- `:core:protocol` must stay stable and testable outside Android UI.

## 6. Data Architecture

### 6.1 User-Visible Spaces

Spaces are the top-level user-visible partition for native data.

Use cases:

- Default native space
- Imported legacy backup as a new space
- Append legacy data into current space
- Overwrite current space with imported data after explicit confirmation

Core fields:

```text
Space(
  id,
  name,
  kind,              # default | imported | archive
  createdAt,
  updatedAt,
  sourceImportId?
)
```

All user data that can belong to a library/session context should reference `spaceId`.

### 6.2 Core Tables

Initial Room schema should include:

- `spaces`
- `sessions`
- `messages`
- `message_attachments`
- `message_quotes`
- `llm_profiles`
- `session_settings`
- `anchors`
- `notes`
- `error_logs`
- `memory_items`
- `graph_nodes`
- `graph_edges`
- `sources`
- `source_chunks`
- `background_jobs`
- `import_batches`
- `export_records`

### 6.3 Entity Ownership

| Entity | Owning Module | Notes |
|---|---|---|
| Space | `:core:data` | User-visible partition, needed before import/export |
| Session | `:core:data` + `:feature:chat` | Chat owns UI; data owns persistence |
| Message | `:core:data` + `:feature:chat` | Supports quote, attachments, citations |
| LlmProfile | `:core:data` + `:core:llm` + `:feature:settings` | Key material stored separately |
| BackgroundJob | `:core:data` + `:feature:chat` | Job result must validate session still exists |
| Anchor | `:core:data` + `:feature:memory` | Includes requirements and source anchors |
| Note | `:core:data` + `:feature:memory` | Can originate from message action |
| ErrorLog | `:core:data` + `:feature:memory` | Linked message evidence |
| Source | `:core:data` + `:feature:sources` | File metadata and parser status |
| SourceChunk | `:core:data` + `:feature:sources` | Retrieval-ready chunks |
| GraphNode/GraphEdge | `:core:data` + `:feature:memory` | Native graph renders these |
| ImportBatch | `:core:data` + `:feature:settings` | Append/overwrite/new-space audit |

### 6.4 Key Storage

API keys must not be stored as plain DataStore strings.

Architecture:

- LLM profile metadata lives in Room.
- Secret values are stored through an Android Keystore-backed secret store.
- Export defaults to excluding or redacting keys.
- Import of keys is disabled unless a future PRD explicitly approves a secure key import flow.

## 7. Protocol Architecture

### 7.1 Versioned Protocols

Every cross-implementation contract must include:

- `schema`
- `version`
- `created_at` or equivalent timestamp when relevant
- Forward-compatible unknown-field handling
- Validation result with warnings and errors

Required protocol groups:

- `reverse_tutor_export_v1`
- `reverse_tutor_session_export_v1`
- `reverse_tutor_graph_snapshot_v1`
- `reverse_tutor_preset_v1`
- `native_turn_request_v1`
- `native_turn_result_v1`
- `native_background_job_v1`
- `native_graph_node_v1`
- `native_graph_edge_v1`
- `native_llm_profile_v1`

### 7.2 Python Boundary

Python is not required at runtime for Android phase one. Python should provide:

- JSON fixtures generated from existing export paths
- Schema validation examples
- Behavior reference tests for role discipline, memory, graph, and import/export
- Future optional API endpoints for mini-program or proxy use

### 7.3 PWA Boundary

PWA remains:

- Export source
- Behavior reference
- Transition client

PWA does not become a native module. Native Android must not wrap PWA screens as replacement UI.

## 8. LLM Runtime Architecture

### 8.1 Provider Strategies

`core:llm` exposes a strategy interface:

```text
LlmProviderStrategy
  - validateProfile(profile)
  - testConnection(profile)
  - streamTurn(request)
  - cancel(jobId)
```

Initial strategies:

- OpenAI-compatible chat/completions
- Anthropic-compatible messages
- Local/mock fallback or explicit no-model mode

Future strategy:

- Python proxy/API, disabled by default

### 8.2 Streaming And Queue

Chat generation should be modeled as jobs:

```text
GenerationJob(
  id,
  spaceId,
  sessionId,
  triggeringMessageIds,
  status,            # queued | running | completed | failed | cancelled | discarded
  providerProfileId,
  createdAt,
  startedAt?,
  completedAt?,
  error?
)
```

Rules:

- A job result must check that `sessionId` still exists.
- A job result must check it still belongs to the expected active generation token.
- If a session is deleted, pending/running jobs for that session become cancelled/discarded.
- If the user sends more messages after streaming starts, those messages remain queued for a later turn.

## 9. Background Work Architecture

Use WorkManager for reliable queued work. Use a foreground service pattern only when Android requires a user-visible long-running operation.

Background job responsibilities:

- Persist queued job before starting provider call
- Show notification for long-running work
- Import completed result into local database only if session still exists
- Surface completion/failure notification
- Provide diagnostic record for failures

Validation:

- Deleted-session result does not create a message
- Session-switch result does not render into the wrong UI
- App restart can recover or mark jobs safely

## 10. Import / Export Architecture

### 10.1 Import Modes

Native import supports:

- Append to current space
- Overwrite current space
- Create new space

All imports create an `ImportBatch`.

```text
ImportBatch(
  id,
  sourceFileName,
  sourceSchema,
  mode,
  targetSpaceId,
  startedAt,
  completedAt?,
  status,
  insertedCounts,
  skippedCounts,
  warnings,
  errors
)
```

### 10.2 Import Pipeline

```text
File picker
  -> read bytes/text
  -> detect schema
  -> validate
  -> dry-run summary
  -> user chooses append/overwrite/new space
  -> transaction write
  -> import result screen
```

Rules:

- Direct IndexedDB access is not a supported primary migration path.
- Import is idempotent where source ids are present.
- Unknown fields are tolerated but not blindly written into unsafe fields.
- API keys are excluded or redacted by default.
- Invalid rows are skipped with a visible warning, not fatal unless the whole file is invalid.

### 10.3 Export Pipeline

Native export should eventually support:

- Current session export
- Global graph snapshot
- Full backup
- Preset export

Exports use Android share/save intents. Key material is excluded/redacted by default.

## 11. Source Parsing Architecture

Source import must report per-type status.

| Type | Native phase-one target | Notes |
|---|---|---|
| Existing export JSON | Required | Protocol import |
| TXT | Required local parsing | Plain text |
| Markdown | Required local parsing | Strip or preserve lightweight markdown |
| Images | Required for chat attachment; source analysis TBD | Store image metadata; analysis depends on model |
| PDF | Local if feasible, otherwise explicit partial status | Complex parser risk |
| DOCX | Local if feasible, otherwise explicit partial status | Complex parser risk |
| HTML | Local if feasible with sanitization | Avoid unsafe script/content |
| PPTX | Future Python/API-assisted slot acceptable if local parser is too heavy | Must be visible to user |
| EPUB | Future Python/API-assisted slot acceptable if local parser is too heavy | Must be visible to user |

Parser status enum:

```text
ParserStatus = supported_local | partial_local | queued_for_future_api | unsupported | failed
```

No file should silently disappear from the user's perspective.

## 12. Knowledge Graph Architecture

### 12.1 Data

Graph data is persisted as `graph_nodes` and `graph_edges`.

Node fields:

```text
GraphNode(
  id,
  spaceId,
  sessionId?,
  kind,
  title,
  summary,
  sourceRefs,
  messageRefs,
  status,
  position?,
  createdAt,
  updatedAt
)
```

Edge fields:

```text
GraphEdge(
  id,
  spaceId,
  sessionId?,
  sourceNodeId,
  targetNodeId,
  kind,
  weight,
  sourceRefs,
  createdAt,
  invalidatedAt?
)
```

### 12.2 Rendering

Use a native graph engine inside `:feature:memory`.

Responsibilities:

- Layout calculation
- Pan/zoom transform state
- Node hit testing
- Edge drawing
- Selection state
- Bottom-sheet detail state
- Persist optional node positions if needed

The graph engine must be UI-testable at the state level even if full visual verification is done on-device.

### 12.3 Parity Gates

Replacement release must support or explicitly waive:

- Pan
- Zoom
- Select/deselect
- Bottom-sheet details
- Related chat/source jump
- Fragment/card review
- Node edit/save where legacy supports it
- Global graph browsing
- Per-session graph browsing
- Empty/loading/large/invalid states

## 13. UI Navigation Architecture

Top-level destinations:

- Sessions
- Chat
- Context hub
- Global graph/insights
- Sources
- Settings
- Import/export
- About/diagnostics

Context hub tabs:

- Graph
- Anchors
- Notes
- Errors
- Session settings

Navigation rules:

- Android system back first closes modal/sheet.
- Then it exits context hub to chat.
- Then it exits chat to session list.
- Then app-level back behavior applies.
- No background job result can force navigation to another session.

## 14. Legacy Entry Ownership Map

| Legacy Range | Native Owner | Notes |
|---|---|---|
| LEG-001 to LEG-004 | `:app`, `:feature:settings` | App shell, global drawer/settings, memo |
| LEG-005 to LEG-011 | `:feature:chat`, `:feature:sources`, `:feature:settings` | Session list, create flow, presets, initial sources |
| LEG-012 to LEG-018 | `:feature:chat`, `:core:llm`, `:core:data` | Chat, composer, quote, streaming, background |
| LEG-019 to LEG-025 | `:feature:memory`, `:core:data` | Context hub, graph, anchors, notes, errors |
| LEG-026 to LEG-030 | `:feature:settings`, `:core:llm` | Provider config, profiles, diagnostics, update/about |
| LEG-031 to LEG-036 | `:feature:settings`, `:feature:sources`, `:core:protocol`, `:core:data` | Import/export, source parsing |
| LEG-037 to LEG-044 | `:app`, `:feature:settings`, `:feature:chat` | Theme, avatar, back, keyboard, offline, templates, about |

ARCH consumer rule:

Every P0 and P1 item in `tasks/native-legacy-entry-inventory.md` must be represented in TODO and task specs. Missing P0 coverage is a release blocker.

### 14.1 Detailed Legacy Entry Module Map

| ID | Primary Module Owner | Data / Protocol Impact | Verification Surface |
|---|---|---|---|
| LEG-001 | `:app` | App state, package metadata | Device launch smoke |
| LEG-002 | `:app`, `:feature:chat`, `:feature:memory` | Navigation state | UI/device navigation check |
| LEG-003 | `:feature:settings` | Theme/avatar/memo preferences | UI state persistence |
| LEG-004 | `:feature:settings`, `:core:data` | Memo table or settings store | Edit/add/delete persistence |
| LEG-005 | `:feature:chat`, `:core:data` | Session, unread, pin fields | Session list UI and DAO tests |
| LEG-006 | `:feature:chat`, `:feature:settings` | Session mutation/export | Action sheet and destructive confirmations |
| LEG-007 | `:feature:chat`, `:feature:settings` | Proactive mode setting | Mode display and persistence |
| LEG-008 | `:feature:chat`, `:feature:settings` | Template/preset protocol | Create flow UI |
| LEG-009 | `:feature:chat`, `:core:data` | Session persona/settings | Form validation and persistence |
| LEG-010 | `:feature:settings`, `:core:protocol` | Preset schema | Fixture validation |
| LEG-011 | `:feature:sources`, `:feature:chat` | Pending source refs | Create/import flow |
| LEG-012 | `:feature:chat`, `:core:data` | Message, citation, process meta | Timeline rendering and fixtures |
| LEG-013 | `:feature:chat` | Draft attachment state | Composer device test |
| LEG-014 | `:feature:chat`, `:core:data` | Quote refs | Quote persistence test |
| LEG-015 | `:feature:chat`, `:feature:memory` | Message action side effects | Action sheet and data mutation tests |
| LEG-016 | `:feature:chat`, `:core:llm`, `:core:data` | Generation job protocol | Queue/stale result tests |
| LEG-017 | `:feature:chat`, `:core:data` | Background job state | WorkManager/device notification test |
| LEG-018 | `:feature:chat`, `:core:llm` | Attachment and capability fields | Multimodal capability fixture |
| LEG-019 | `:feature:memory`, `:app` | Navigation state | Context hub navigation |
| LEG-020 | `:feature:memory`, `:core:data` | Graph node/edge protocol | Graph state tests and device gesture test |
| LEG-021 | `:feature:memory`, `:feature:sources` | Anchor/source refs | Add/import/delete tests |
| LEG-022 | `:feature:memory`, `:feature:chat` | Note/message refs | Create-from-message and edit/delete tests |
| LEG-023 | `:feature:memory`, `:core:data` | ErrorLog/message refs | Evidence linking fixtures |
| LEG-024 | `:feature:memory`, `:feature:settings` | Strategy/persona settings | Settings persistence and warning flow |
| LEG-025 | `:feature:memory`, `:core:data` | Cross-session graph query | Global graph device test |
| LEG-026 | `:feature:settings`, `:core:llm` | LLM profile contract | Config validation fixtures |
| LEG-027 | `:feature:settings`, `:core:data` | LLM profile and secret refs | Profile CRUD and secret redaction tests |
| LEG-028 | `:feature:settings`, `:core:llm` | Diagnostic result protocol | Connection test mock/provider fixture |
| LEG-029 | `:feature:settings`, `:feature:chat` | Proactive settings | Explicit enable/disable decision test |
| LEG-030 | `:feature:settings`, `:app` | Version/update metadata | About/update surface device check |
| LEG-031 | `:feature:settings`, `:core:protocol` | Export schemas | Export fixture comparison |
| LEG-032 | `:feature:settings`, `:core:data`, `:core:protocol` | ImportBatch, spaces, schemas | Import mode transaction tests |
| LEG-033 | `:feature:settings`, `:core:data` | Destructive wipe | Confirmation and data wipe test |
| LEG-034 | `:feature:sources`, `:core:data` | Source/source chunk tables | Parser status and import tests |
| LEG-035 | `:feature:sources`, `:feature:chat` | Image source/attachment refs | File picker/device test |
| LEG-036 | `:feature:sources`, `:feature:memory` | Source preview/chunk refs | Snippet/reprocess UI |
| LEG-037 | `:feature:settings`, `:app` | Theme preferences | Theme persistence device check |
| LEG-038 | `:feature:settings`, `:feature:chat`, `:core:data` | Avatar fields | Avatar choose/clear/hide device test |
| LEG-039 | `:app` | Navigation stack | Back-stack device test |
| LEG-040 | `:app`, `:feature:chat` | Gesture state | Gesture conflict device test |
| LEG-041 | `:feature:chat` | Composer state | IME/device test |
| LEG-042 | `:core:llm`, `:feature:chat` | Fallback/no-model state | No-config behavior fixture and device check |
| LEG-043 | `:feature:chat`, `:feature:settings`, `:core:protocol` | Template protocol | Template creation fixture |
| LEG-044 | `:feature:settings`, `:app` | App/version/support metadata | About/diagnostics UI check |

## 15. Build, Package, And Release Architecture

Internal builds:

- Package id: `com.reversetutor.preview`
- Install beside current PWA APK
- Version names can include `native-preview` and phase identifiers
- Signed internal APKs are allowed only when requested

Replacement builds:

- Package id: `com.reversetutor.app`
- Must use the existing official signing identity if replacing the old APK
- Must pass cover-install validation
- Must show first-launch import guidance
- Must not retire PWA release path without approval

## 16. Verification Architecture

### 16.1 Automated Verification

Required by task spec when module includes:

- Protocol validation
- Import/export mapping
- Database migrations
- LLM parser/streaming behavior
- Background job state
- Graph layout state
- Source parsing
- Destructive data actions

### 16.2 Device Verification

Every phase APK must record:

- APK version/name/package id
- Device/emulator name
- Android version
- Tested flows
- Result
- Screenshots or failure notes

Minimum replacement readiness matrix:

- Owner's main phone
- One older/lower-end Android target
- One modern Android emulator

### 16.3 Review Gates

Medium/high risk modules require review before integration:

- Import/export
- Local data schema/migrations
- API key storage
- Background jobs
- Graph interaction
- Package/release switch
- Source parsers

## 17. ADworkflo Mapping

Project size: large.

Reason:

- Multiple Android modules
- Local database and migration rules
- Background jobs and notifications
- LLM streaming
- Source parsing
- Legacy import/export
- Release package switch
- Many P0/P1 parity gates

Initial ADworkflo path:

1. PRD complete
2. Legacy entry inventory complete
3. ARCH complete
4. PROJECT document
5. TODO document
6. Initialize or update ADworkflo artifacts
7. Generate architecture manifest
8. Generate execution plan
9. Create per-module task specs
10. Implement module branches

## 18. Architecture Risks

| Risk | Mitigation |
|---|---|
| Native scope becomes too large | Separate internal phase APKs from replacement release |
| Graph parity blocks all progress | Implement graph module in slices but keep replacement gate strict |
| Source parser complexity delays migration | Make parser status visible and reserve Python/API-assisted path |
| Package switch breaks cover install | Keep `package-release` ownership and dedicated validation |
| Import corrupts existing native data | Dry-run summary, transaction coordinator, import batch audit |
| Secrets leak through export | Separate key storage, export redaction by default |
| Android behavior drifts from Python/PWA | Shared protocol fixtures and reference tests |

## 19. Open Architecture Questions

- Exact first supported export schema version and compatibility policy
- Older/lower-end Android validation device or target version
- Exact local parser choices for PDF, DOCX, PPTX, EPUB
- Whether proactive conversation remains enabled in native replacement release
- Whether offline/mock mode should behave like legacy mock or become a clearer no-model state
- Final signing and package-switch procedure for official replacement APK
