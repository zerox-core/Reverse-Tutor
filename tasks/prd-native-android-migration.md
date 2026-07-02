# PRD: Native Android Migration

## 1. Introduction / Overview

Reverse Tutor will introduce a real native Android application as the primary mobile product line. The current APK is based on a Capacitor-packaged PWA, where `static/app/index.html` carries UI, local state, LLM calls, memory, knowledge graph, and offline behavior in one large frontend file. This worked for fast iteration, but it is no longer the right long-term foundation for a mobile-first AI learning product.

The native Android line will use Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager, and Android-native background/notification capabilities. The goal is not to immediately delete existing work, but to create a controlled migration path:

- Android native becomes the future mobile mainline.
- Python remains as protocol/API/test baseline and a future mini-program or web service foundation.
- PWA/Capacitor becomes a transition layer for existing users, export compatibility, and behavior reference, then exits the APK release path after native parity is reached.

This work will be executed with ADworkflo: product requirements, architecture, TODO modules, task specs, multi-branch implementation, verification evidence, and staged integration.

## 2. Goals

- Establish a native Android product line that can eventually replace the Capacitor/PWA APK.
- Preserve the product's core identity: AI plays the student, asks questions, records learning evidence, and helps the user progress through teaching.
- Support all major product modules in the migration plan: chat, LLM settings, memory, knowledge graph, source import, export-data import, background generation, notifications, update/distribution readiness, and local-first persistence.
- Use modular ADworkflo execution so each module can be developed and verified on its own branch before integration.
- Avoid a three-client logic trap by treating Python as a protocol/API/test baseline, not as another mobile client implementation.
- Provide an import path for data exported by the existing version, without requiring direct IndexedDB migration.
- Define clear retirement criteria for the PWA/Capacitor APK path.

## 3. Strategic Decisions

### 3.1 Mobile Mainline

The native Android app is the future primary mobile app. New mobile-facing capabilities should be designed for native Android first once the native foundation is ready.

### 3.2 Python Role

Python remains valuable, but its role changes:

- API/server foundation for future mini-program, web, or cloud-backed features.
- Reference implementation for business rules and protocol behavior.
- Test baseline for conversation evaluation, memory updates, graph rules, import/export validation, and compatibility cases.
- Tooling layer for data format validation and migration fixtures.

Python is not intended to become a second mobile client.

### 3.3 PWA Role

PWA/Capacitor is a transition asset:

- Existing users can continue using it during migration.
- It provides export data for native Android import.
- It acts as a behavior reference while native modules reach parity.
- It should not receive major new product features after the native Android line becomes viable.

When native Android reaches defined parity, PWA should be removed from the APK release path.

### 3.4 Shared Protocol, Not Shared Code

The chosen architecture is "native Android mainline + shared business protocol + Python reference/API baseline." Android does not need to reuse Python or PWA code. Instead, all implementations must align on documented contracts:

- Session format
- Message format
- LLM provider/profile format
- Import/export JSON schema
- Memory item format
- Knowledge graph node/edge format
- Turn input/output format
- Background job state format
- Error code and validation format

This keeps Android fully native while preventing behavior drift.

### 3.5 Boundary And Development Decisions v1

Confirmed decisions as of 2026-06-30:

- Target users: the first native Android product is an upgrade path for existing PWA/APK users.
- Legacy positioning: the old PWA version should be gradually de-emphasized and stopped from receiving major feature updates.
- First public native release: must cover all existing major product entry points, so users do not experience the native release as a functional downgrade.
- Development model: implementation still proceeds module by module on separate branches; "all entry points covered" is a release gate, not a reason to do an unstructured one-shot rewrite.
- Minimum usable native loop for release: session list, chat, LLM settings, local persistence, background reply, notification, existing export-data import, and memory/knowledge graph browsing.
- Source import target: preserve the existing broad source-type ambition: JSON export, PDF, DOCX, TXT, Markdown, HTML, PPTX, EPUB, and images.
- Knowledge graph target: native graph should eventually match the old canvas interaction capability, not only provide read-only storage.
- Repository layout: native Android work should live in a new `mobile-native/` directory, separate from the current `mobile/` PWA/Capacitor project.
- Branching: use module branches such as `native/chat-main-flow`, merge verified work into `native/integration`.
- Required planning artifacts before implementation: PRD, ARCH, TODO, PROJECT, and ADworkflo task specs.
- Legacy import conflict handling: user can choose append, overwrite, or import into a new space.
- API key policy: first phase has no cloud sync for keys; keys are stored locally, and manual export/import must default to redacted or excluded keys.
- Python runtime role: Android should be usable without Python in the first phase by directly calling configured LLM providers; Python remains for protocol validation, tests, tooling, and future mini-program/API work.

### 3.6 Architecture And Release Decisions v2

Confirmed decisions as of 2026-06-30:

- Package strategy: internal native development should use a new package name so it can be installed beside the current PWA/APK. When the native app is ready to replace the old APK, it should switch to the official package identity and preserve the ability to cover-install for existing users.
- Replacement install behavior: the replacement build should support same-package cover install and show a first-launch import prompt that guides users to import an exported backup file.
- Migration boundary: the app should not try to automatically read old WebView/IndexedDB internals as the primary migration path. The supported path remains export-file import.
- LLM runtime strategy: Android direct provider calls are the primary first-phase runtime. Python proxy/API support should be kept as a future optional strategy, not a first-phase dependency.
- UI strategy: first native phase follows the old functional layout closely to reduce migration risk; a deeper mobile UX redesign can happen after parity is stable.
- Knowledge graph strategy: the first replacement release must target full parity with the old graph interaction capability.
- Source parsing strategy: Android parses what is practical locally; complex parsing should have an architecture slot for a future Python/API-assisted path. The native replacement release must still document which source types are fully local, partially supported, or waiting for server-assisted parsing.
- Verification strategy: each phase must produce an internal APK and true-device validation record. Automated tests remain expected where a module has domain logic, data migration, parser behavior, or regression risk, and should be specified in each ADworkflo task spec.
- Release rhythm: produce an internal APK after each phase, not after every small module and not only at final integration.

### 3.7 ARCH Defaults v3

Confirmed recommendations accepted as of 2026-06-30:

- Internal package id: use `com.reversetutor.preview` for native development and internal APKs.
- Official replacement package id: switch back to `com.reversetutor.app` only after explicit replacement readiness approval and cover-install verification.
- Gradle module strategy: use a moderately modular project, not a single `:app` and not extremely fine-grained feature modules. Baseline modules should include `:app`, `:core:protocol`, `:core:model`, `:core:data`, `:core:llm`, `:feature:chat`, `:feature:memory`, `:feature:sources`, and `:feature:settings`.
- Import "new space" strategy: make spaces a user-visible concept so append, overwrite, and new-space import choices are understandable and reversible.
- Graph implementation: use native Compose Canvas and Android input handling for graph interaction. Do not carry the old graph through WebView.
- Source parsing standard: simple formats should be local where feasible; complex formats can use a future Python/API-assisted slot, but every supported legacy file type must have an explicit status.
- Legacy entry inventory: create and maintain `tasks/native-legacy-entry-inventory.md` before writing ARCH, and use it as a release gate.
- Device validation matrix: use the owner's main phone, at least one older/low-end Android device or lower-version Android target, and one modern Android emulator.
- Document order: create/update legacy entry inventory first, then write ARCH, then PROJECT/TODO, then ADworkflo task specs.

## 4. User Stories

### US-001: Native Android App Shell

**Description:** As a mobile user, I want a native Android app shell so that the app feels stable, fast, and integrated with my phone.

**Acceptance Criteria:**

- [ ] App launches into a native Compose UI, not a WebView-hosted PWA.
- [ ] App has native navigation for all major legacy product entry points, including session list, chat, memory/graph, sources, settings, import/export, update/about, and diagnostics where applicable.
- [ ] App stores basic app settings locally through Android-native persistence.
- [ ] App can be installed and opened as a standalone Android app.
- [ ] Internal native builds can be installed beside the current PWA/APK during development.
- [ ] Replacement builds can later use the official package identity for cover-install after explicit approval.
- [ ] Internal package id is `com.reversetutor.preview`.
- [ ] Module branch includes verification evidence and reviewer notes.

### US-002: Native Chat Main Flow

**Description:** As a user, I want to create sessions and chat with the AI student in the native app so that the core Reverse Tutor experience works without the PWA.

**Acceptance Criteria:**

- [ ] User can create, rename, pin, delete, and open sessions.
- [ ] User can send messages and receive assistant replies.
- [ ] Chat preserves conversation history locally.
- [ ] Chat UI supports queued/pending reply state.
- [ ] Chat preserves the product behavior where AI acts as a student, questioner, examiner, or collaborator based on session profile.
- [ ] Native implementation follows the shared turn input/output protocol.

### US-003: Native LLM Configuration

**Description:** As a user, I want to configure model providers in the native app so that I can use my own API services.

**Acceptance Criteria:**

- [ ] User can add, edit, switch, and delete LLM profiles.
- [ ] Profiles support provider name, API type, base URL, model name, API key, and capability flags where needed.
- [ ] Sensitive fields are stored through an Android-appropriate secure or scoped persistence approach.
- [ ] API keys are not cloud-synced in the first phase.
- [ ] Manual configuration export/import excludes API keys by default or redacts them clearly.
- [ ] Imported profiles from existing export data are validated before use.
- [ ] Invalid provider configuration produces a clear native error state.
- [ ] Android can call configured LLM providers directly without requiring Python.
- [ ] Architecture allows a future optional Python proxy/API strategy without making it mandatory.

### US-004: Background Reply And Notification

**Description:** As a mobile user, I want replies to continue when I leave the app so that long AI generation does not block normal phone use.

**Acceptance Criteria:**

- [ ] Long-running generation can continue through Android-native background task handling.
- [ ] User sees a native notification when a background reply completes or fails.
- [ ] The app does not attach an old background result to the wrong session.
- [ ] Deleted sessions do not receive late assistant messages.
- [ ] Background job state follows the shared job protocol.

### US-005: Native Local Data Layer

**Description:** As a user, I want my sessions, messages, memory, graph, sources, and settings to persist locally so that the app remains local-first.

**Acceptance Criteria:**

- [ ] Structured data is stored in a native Android local database.
- [ ] Lightweight preferences are stored separately from relational data.
- [ ] Data model supports sessions, messages, memory items, graph nodes, graph edges, sources, LLM profiles, and background jobs.
- [ ] Database schema has versioning and migration strategy.
- [ ] Local data survives app restart.

### US-006: Existing Export Data Import

**Description:** As an existing user, I want to import data exported from the current version so that I can move useful history into the native app.

**Acceptance Criteria:**

- [ ] Native app can select or receive an exported JSON file from the existing version.
- [ ] Replacement build shows a first-launch import prompt after cover-install.
- [ ] Import validates schema version before writing data.
- [ ] Import supports at minimum sessions, messages, model settings where safe, memory items, graph nodes/edges, and sources when present in export.
- [ ] Import allows the user to choose append, overwrite, or import into a new isolated space.
- [ ] New-space import is exposed as a user-visible space/library concept rather than only an internal import batch.
- [ ] Import reports skipped or invalid records without crashing.
- [ ] Import never requires direct IndexedDB access.
- [ ] Import can be re-run safely without uncontrolled duplication.

### US-007: Native Memory And Knowledge Graph

**Description:** As a long-term user, I want memory and knowledge graph views in the native app so that previous learning evidence remains useful.

**Acceptance Criteria:**

- [ ] User can view memory summaries and relevant learning evidence.
- [ ] User can view graph nodes and relationships in a native interaction model that targets parity with the old canvas graph capability.
- [ ] User can open node details and inspect related messages or sources.
- [ ] User can use the full agreed legacy graph interaction set, including pan, zoom, select, detail inspection, relationship exploration, and any current edit/save operations designated in the legacy-entry inventory.
- [ ] Graph is implemented natively with Compose Canvas or equivalent Android-native drawing/input primitives, not a WebView wrapper.
- [ ] Native graph data follows the shared node/edge protocol.
- [ ] Empty, loading, large graph, and invalid graph states are handled.

### US-008: Native Source Import

**Description:** As a user, I want to import study materials into the native app so that chats can use local sources and evidence.

**Acceptance Criteria:**

- [ ] User can import files through Android-native file picker.
- [ ] Supported source types target parity with the existing product: PDF, DOCX, TXT, Markdown, HTML, PPTX, EPUB, and images.
- [ ] Imported sources are stored with title, type, URI or local reference, extracted text when available, and chunk metadata.
- [ ] Source parser status is explicit per file type: fully local, partially local, or future Python/API-assisted.
- [ ] Unsupported files produce a clear error state.
- [ ] Source import does not upload user files unless a later explicit cloud feature is approved.

### US-009: ADworkflo Modular Execution

**Description:** As the product owner, I want the migration developed by modules and branches so that each part can be verified before integration.

**Acceptance Criteria:**

- [ ] PRD, ARCH, PROJECT, and TODO documents exist before implementation.
- [ ] ADworkflo is initialized for the native Android migration.
- [ ] Each major module has a task spec and verification commands.
- [ ] Module branches are verified before merge into integration branch.
- [ ] Review findings and verification evidence are recorded.
- [ ] Each phase produces an internal APK and a true-device validation record.
- [ ] Phase validation covers the owner's main phone, one older/low-end Android target, and one modern Android emulator before replacement release.
- [ ] Module task specs define required automated tests for domain logic, data migration, parser behavior, and regression-prone areas.
- [ ] APK build, signing, release, and PWA retirement are separate controlled milestones.

### US-010: Legacy Entry Parity Release Gate

**Description:** As an existing user, I want the first public native Android release to include the major product entry points I already rely on so that upgrading does not feel like losing core capabilities.

**Acceptance Criteria:**

- [ ] Release checklist maps every major current PWA entry point to a native screen, native flow, or explicitly approved temporary replacement.
- [ ] Any missing legacy behavior is documented as a release blocker or approved non-blocker.
- [ ] Native first public release is not labeled as the user-facing replacement until entry parity is verified.
- [ ] ADworkflo integration branch contains verification evidence for each entry group.

## 5. Functional Requirements

- FR-1: The system must create a new native Android app line based on Kotlin and Jetpack Compose.
- FR-2: The native app must use Android-native navigation and UI components rather than relying on WebView for the main product experience.
- FR-3: The native app must support local sessions and message history.
- FR-4: The native app must support LLM profile configuration and provider switching.
- FR-5: The native app must support background generation and completion/failure notifications.
- FR-6: The native app must support local-first data storage for sessions, messages, memory, graph, sources, settings, and jobs.
- FR-7: The native app must support importing data exported from the current PWA/Capacitor version.
- FR-8: The native app must validate imported data before writing it to local storage.
- FR-9: The native app must support native source import through Android file selection.
- FR-10: The native app must define and follow shared protocol schemas for messages, sessions, LLM profiles, memory, graph, source metadata, and turn results.
- FR-11: The Python implementation must be treated as protocol/API/test baseline for relevant behavior.
- FR-12: PWA/Capacitor must remain available during transition but must have explicit retirement criteria.
- FR-13: The migration must be split into ADworkflo modules with branch-based verification.
- FR-14: Each module must define verification commands and acceptance evidence before integration.
- FR-15: The project must not require direct migration from IndexedDB; export-file import is the supported migration path.
- FR-16: The first user-facing native release must cover all major existing product entry points before replacing the PWA APK.
- FR-17: The native source import module must target existing source-type parity: PDF, DOCX, TXT, Markdown, HTML, PPTX, EPUB, and images.
- FR-18: The native knowledge graph module must target interaction parity with the existing graph experience.
- FR-19: The native import flow must support append, overwrite, and new-space import modes.
- FR-20: Android must directly support configured LLM provider calls in phase one and must not require Python to run the mobile product.
- FR-21: API keys must remain local in phase one and must be redacted or excluded by default during manual configuration export/import.
- FR-22: Internal native development builds must use a separate package name from the current PWA/APK until replacement readiness is approved.
- FR-23: The replacement native build must support official-package cover install and first-launch export-data import guidance.
- FR-24: The first native phase must follow the old functional layout closely; major UX redesign is deferred until after parity stabilization.
- FR-25: Each phase must produce an internal APK and a true-device validation record.
- FR-26: Complex source parsing may use a future Python/API-assisted path, but source-type support status must be explicit and user-visible.
- FR-27: Python proxy/API LLM calls may be added as an optional future strategy, but direct Android provider calls are required first.
- FR-28: Internal native APKs must use package id `com.reversetutor.preview`.
- FR-29: The native Android project must use a moderately modular Gradle structure with core and feature modules.
- FR-30: Import spaces must be a user-visible concept in the native app.
- FR-31: Native graph interaction must be implemented with Android-native drawing/input primitives rather than WebView.
- FR-32: `tasks/native-legacy-entry-inventory.md` must exist before ARCH is finalized and must drive replacement release gates.
- FR-33: Replacement readiness must be validated against the owner's main phone, one older/low-end Android target, and one modern Android emulator.

## 6. Non-Goals

- No immediate full rewrite in one branch.
- No direct IndexedDB extraction from installed legacy app data.
- No requirement to keep PWA and native Android feature development permanently parallel.
- No Kotlin Multiplatform shared-core implementation in the first migration phase.
- No cloud sync requirement unless a later PRD approves it.
- No API key cloud sync in the first native phase.
- No requirement for Python to be available for the native Android app to run.
- No automatic WebView/IndexedDB scraping as the primary migration path.
- No major UX redesign before the native app reaches functional parity.
- No WebView-based graph carry-over as the accepted native replacement implementation.
- No single-module Android architecture for the native mainline.
- No automatic app-store release, tag, or public distribution as part of this PRD.
- No removal of existing PWA/Capacitor APK path until native parity and import readiness are verified.

## 7. Architecture Considerations

### 7.1 Recommended Android Stack

- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: UI layer, domain layer, data layer
- Local database: Room
- Preferences/settings: DataStore
- Background work: WorkManager and native foreground/background service patterns where appropriate
- Networking: a typed HTTP client layer for LLM providers and future Python API integration
- Dependency injection: to be decided in ARCH, likely Hilt or a lightweight manual approach based on module size
- Testing: unit tests for domain/data logic, instrumentation or UI tests for critical flows, import/export fixture tests
- Packaging: internal native package id separated from current `com.reversetutor.app`; official package identity used only for approved replacement builds
- Module topology: `:app`, `:core:protocol`, `:core:model`, `:core:data`, `:core:llm`, `:feature:chat`, `:feature:memory`, `:feature:sources`, `:feature:settings`
- Internal package id: `com.reversetutor.preview`
- Graph rendering: Android-native Compose Canvas or equivalent native drawing/input layer

### 7.2 Proposed Native Modules

- `app-shell`: app entry, navigation, theming, top-level state
- `chat`: sessions, messages, message composer, streaming/pending states
- `llm-config`: provider profiles, validation, secure/scoped storage
- `llm-runtime`: provider requests, streaming, errors, retry and fallback semantics
- `background-jobs`: background generation, notifications, stale-result isolation
- `local-data`: Room schema, migrations, repositories
- `memory`: memory item display and updates
- `knowledge-graph`: graph data, node/edge display, graph detail interactions
- `sources`: file picker, parsing pipeline, source metadata, chunks
- `import-export`: legacy export importer, schema validation, duplicate handling
- `package-release`: internal package id, official package switch, cover-install readiness, signing checks
- `device-validation`: phase APK smoke matrix, true-device evidence, release blocker tracking
- `legacy-entry-parity`: map current PWA entry points to native screens and release-gate coverage
- `protocol`: shared JSON contracts and validation fixtures
- `settings`: app settings, privacy, diagnostics, update information
- `qa-release`: build verification, signing checklist, regression matrix

### 7.3 Python Boundary

Python remains outside the required native APK runtime in the first native phase. The Android app must be able to run by directly calling user-configured LLM providers and by using local storage. Python should provide:

- Protocol schema examples and validation fixtures.
- Server/API path for future mini-program or web clients.
- Future optional LLM proxy/API strategy.
- Future optional complex source parsing strategy.
- Reference behavior tests for core AI learning logic.
- Optional import/export validation tools.

### 7.4 PWA Retirement Boundary

PWA can be retired from APK release path only after:

- Native chat main flow is usable.
- Native LLM settings are usable.
- Native local data persistence is stable.
- Native background reply is verified.
- Native import from existing export data is verified.
- Native memory/graph/source views cover the agreed parity scope.
- Native source import covers the agreed legacy source types or has explicit owner approval for any temporary exception.
- Native screens cover all major existing PWA entry points or have explicit owner approval for any temporary exception.
- Existing users have a documented export/import path.

## 8. ADworkflo Execution Model

The native migration must be treated as a large project.

Execution flow:

1. Create PRD.
2. Create ARCH document with module boundaries and protocol contracts.
3. Create PROJECT document describing repository layout, branch strategy, and development rules.
4. Create TODO document with phased module checklist.
5. Initialize or update ADworkflo artifacts.
6. Generate architecture manifest.
7. Generate execution plan from TODO.
8. Create per-module task specs.
9. Implement modules on separate branches.
10. Verify module branches with recorded evidence.
11. Review medium/high risk modules before integration.
12. Integrate into a native Android milestone branch.
13. Build signed APK only when explicitly requested.

Recommended branch pattern:

- `native/integration`
- `native/android-foundation`
- `native/chat-main-flow`
- `native/local-data`
- `native/llm-runtime`
- `native/import-export`
- `native/memory-graph`
- `native/sources`
- `native/background-jobs`
- `native/legacy-entry-parity`
- `native/integration-milestone-1`

## 9. Phased Scope

### Phase 0: Product And Architecture Setup

- Finalize PRD.
- Create `tasks/native-legacy-entry-inventory.md`.
- Write ARCH, PROJECT, TODO.
- Define shared protocol schemas.
- Initialize ADworkflo.

### Phase 1: Native Foundation

- Create Android native project structure under `mobile-native/`.
- Add Compose app shell.
- Add navigation and theme.
- Add local data foundation.
- Add simple settings foundation.
- Add a current-PWA-entry inventory so every legacy entry point has a native owner.

### Phase 2: Core User Loop

- Session list.
- Chat screen.
- Local messages.
- LLM profile configuration.
- Direct Android-to-provider LLM call path.
- Pending and failed reply states.
- API key local storage and redacted manual config export/import behavior.

### Phase 3: Background And Reliability

- Background reply handling.
- Native notifications.
- Stale generation isolation.
- Deleted-session protection.
- Error diagnostics.

### Phase 4: Import Compatibility

- Define export JSON compatibility rules.
- Implement file import.
- Validate schema.
- Import sessions/messages/settings/memory/graph/sources where available.
- Support append, overwrite, and new-space import modes.
- Produce import summary and error report.

### Phase 5: Memory, Graph, Sources

- Native memory view.
- Native knowledge graph view targeting existing canvas interaction parity.
- Source import for PDF, DOCX, TXT, Markdown, HTML, PPTX, EPUB, and images.
- Source metadata and chunks.
- Relevant memory/source integration into chat context.

### Phase 6: Entry Parity And PWA Exit

- Compare native behavior against agreed PWA/Python baseline.
- Verify all major PWA entry points are represented in native Android.
- Run regression matrix.
- Document user migration path.
- Remove PWA from APK release path only after approval.

## 10. Success Metrics

- Native app can complete the core loop: create session -> configure model -> chat -> background reply -> persist history.
- Import from existing export data succeeds for representative sample files.
- Import conflict modes append, overwrite, and new-space are verified with fixtures.
- Native app covers all major current PWA entry points before it is presented as the replacement APK.
- Native source import handles the agreed legacy file types or explicitly records approved exceptions.
- Native graph supports the agreed parity interactions: pan, zoom, select, detail inspection, and relationship exploration.
- Each phase produces an internal APK and a true-device validation record.
- Replacement readiness is validated on the owner's main phone, one older/low-end Android target, and one modern Android emulator.
- No old generation result appears in a wrong or deleted session.
- PWA/Capacitor receives no new major mobile-only features after native MVP is accepted.
- Each migration module has task spec, verification evidence, and review result before integration.
- Native Android architecture supports future mini-program/API coexistence through shared protocol docs.

## 11. Risks And Mitigations

- Risk: Migration scope is too large.
  - Mitigation: ADworkflo module branches, phased milestones, no one-shot rewrite; entry parity is a release gate, not a single-branch implementation instruction.

- Risk: Android behavior drifts from existing product identity.
  - Mitigation: shared protocol, Python reference tests, PWA behavior comparison during transition.

- Risk: Import corrupts or duplicates user data.
  - Mitigation: schema validation, dry-run summary, idempotent import strategy, fixture tests.

- Risk: Background generation attaches to wrong sessions.
  - Mitigation: job/session token isolation and deleted-session checks as first-class acceptance criteria.

- Risk: PWA retirement happens too early.
  - Mitigation: explicit retirement checklist and user approval before removing PWA from APK release path.

- Risk: Full source and graph parity delays the first user-facing release.
  - Mitigation: keep internal module milestones separate from the first public replacement release; do not call a partial internal APK the PWA replacement.

- Risk: Internal package identity diverges from replacement package behavior.
  - Mitigation: maintain a dedicated package-release module and run explicit cover-install validation before switching to official package identity.

- Risk: Relying only on true-device validation misses data and parser regressions.
  - Mitigation: true-device validation is mandatory for phases, while ADworkflo task specs must still require automated tests for domain logic, migration, parser, and high-risk regression surfaces.

- Risk: User-visible spaces add product complexity.
  - Mitigation: introduce spaces first through import conflict handling only, then expand space management UI after users understand the migration use case.

## 12. Open Questions

- Which export JSON schema version should be declared as the first supported import contract?
- Which modules require human device testing before branch integration?
- What is the exact current-PWA entry inventory that must be covered before native replacement release?
- Which source types can be fully parsed on-device in phase one, and which need the future Python/API-assisted parsing slot?
- What exact legacy graph interactions and edit/save operations must be marked release-blocking?
- Which older/low-end Android device or Android version should be used for the physical-device validation slot?
