# PROJECT: Native Android Migration

## 1. Purpose

This document defines the working rules for the native Android migration of Reverse Tutor. It is the project-level contract for implementation agents, reviewers, and release checks.

This is not a feature wish list. Product scope lives in `tasks/prd-native-android-migration.md`. Architecture lives in `tasks/arch-native-android-migration.md`. Legacy parity gates live in `tasks/native-legacy-entry-inventory.md`. This PROJECT document answers:

- Where work is allowed to happen.
- Which branches and artifacts are expected.
- What must not be changed without approval.
- How module work is verified before integration.
- When the old PWA/APK path can be retired.

## 2. Project Authority Order

When documents conflict, use this order:

1. `AGENTS.md` repository hard rules.
2. User decisions recorded in `tasks/prd-native-android-migration.md`.
3. Architecture decisions in `tasks/arch-native-android-migration.md`.
4. Release gates in `tasks/native-legacy-entry-inventory.md`.
5. This PROJECT document.
6. `tasks/todo-native-android-migration.md`.
7. ADworkflo task specs and worker state for the active module.

If a lower-level task spec conflicts with PRD or ARCH, stop and update the task spec rather than silently changing the product direction.

## 3. Non-Negotiable Product Boundaries

- Native Android is the future mobile mainline.
- Python remains the protocol, API, tooling, and test baseline. It is not a second mobile client.
- PWA/Capacitor remains a transition client, export source, and behavior reference until native parity is verified.
- The native app must be implemented with real Android technology, not a WebView wrapper around the current PWA.
- The supported migration path is export-file import. Direct IndexedDB scraping is not the primary path.
- Internal native development uses `com.reversetutor.preview`.
- Official replacement builds use `com.reversetutor.app` only after explicit replacement readiness approval.
- PWA removal from APK release path requires explicit approval after parity evidence exists.

## 4. Repository Boundaries

### 4.1 Existing Lines

`mobile/`

- Existing Capacitor/PWA Android packaging line.
- Must remain installable during migration.
- Do not change signing, `applicationId`, package identity, or release key unless the user explicitly asks.
- Do not use this path for native Android implementation work.

`static/app/`

- Existing PWA behavior reference and export source.
- May be read for parity analysis.
- Should not receive major new mobile-only features once native foundation is viable, unless a separate PRD approves it.

Python files such as `server.py`, `engine.py`, `db.py`, `retrieval.py`, and tests:

- Protocol/API/test baseline.
- May provide fixtures and validation logic for import/export, memory, graph, and role behavior.
- Must not become a runtime dependency for first-phase Android app usability.

### 4.2 New Native Line

All native Android work belongs under:

```text
mobile-native/
```

Expected structure:

```text
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
    device-validation/
```

The exact Android module folder layout may evolve, but the ownership boundaries in ARCH must remain recognizable.

## 5. Native Technology Standard

Use the following stack unless a later ARCH update approves a change:

- Kotlin
- Jetpack Compose
- Material 3
- Room
- DataStore
- WorkManager
- Android Keystore-backed secret storage
- Android file picker and share/save intents
- Native notifications
- Native Compose Canvas or equivalent native drawing/input for graph interaction

Do not introduce a cross-platform UI layer, Kotlin Multiplatform shared core, or WebView-based replacement implementation in the first migration phase.

## 6. Gradle Module Rules

Baseline modules:

- `:app`
- `:core:protocol`
- `:core:model`
- `:core:data`
- `:core:llm`
- `:feature:chat`
- `:feature:memory`
- `:feature:sources`
- `:feature:settings`

Layering rules:

- Feature modules can depend on core modules.
- Core modules cannot depend on feature modules.
- Compose screens do not call DAOs, file parsers, or LLM clients directly.
- Repositories own mapping and transaction boundaries.
- Import/export writes go through one transaction coordinator.
- `:core:protocol` must remain testable outside UI.

Do not create many tiny feature modules before there is a real need. The migration should stay moderately modular, not fragmented.

## 7. Package And Signing Boundaries

### 7.1 Internal Native Builds

- Package id: `com.reversetutor.preview`
- Can be installed beside the current PWA/APK.
- Used for phase APKs and true-device validation.
- May use preview naming and phase version labels.

### 7.2 Official Replacement Builds

- Package id: `com.reversetutor.app`
- Must use the official signing identity when replacing the old APK.
- Must pass cover-install validation.
- Must show first-launch import guidance.
- Must be approved before any package switch.

### 7.3 Forbidden Without Explicit Approval

- Do not modify `mobile/android/app/release.jks`.
- Do not modify existing alias `reverse-tutor`.
- Do not modify existing PWA `applicationId com.reversetutor.app`.
- Do not generate replacement APKs, release tags, or public packages without explicit user request.

## 8. Branch Strategy

Main migration branches:

- `native/integration`
- `native/android-foundation`
- `native/local-data`
- `native/chat-main-flow`
- `native/llm-runtime`
- `native/background-jobs`
- `native/import-export`
- `native/memory-graph`
- `native/sources`
- `native/settings-profile`
- `native/legacy-entry-parity`
- `native/package-release`

Rules:

- Module branches are developed and verified before merging into `native/integration`.
- Shared protocol and data changes must be reviewed before feature branches depend on them broadly.
- High-risk modules require reviewer evidence before integration.
- Phase APKs are produced from integration milestones only when explicitly requested.
- Do not push branches or create tags unless the user explicitly asks.

Recommended milestone branch names:

- `native/integration-phase-1-foundation`
- `native/integration-phase-2-core-loop`
- `native/integration-phase-3-background`
- `native/integration-phase-4-import`
- `native/integration-phase-5-memory-graph-sources`
- `native/integration-phase-6-replacement-readiness`

## 9. ADworkflo Artifact Rules

The native migration is classified as a large ADworkflo project.

Required first-layer docs:

- `tasks/prd-native-android-migration.md`
- `tasks/native-legacy-entry-inventory.md`
- `tasks/arch-native-android-migration.md`
- `tasks/project-native-android-migration.md`
- `tasks/todo-native-android-migration.md`

Required ADworkflo artifacts before implementation:

- `.adworkflow/ADWORKFLOW_PROFILE.json`
- `.adworkflow/architecture_manifest.json`
- `.adworkflow/permissions.md`
- `.adworkflow/verification_commands.md`
- `.adworkflow/module_skills.md`
- `.adworkflow/execution_plan.json`
- `.adworkflow/task_specs/<task_id>.json` for active module work
- `.adworkflow/context_manifest.json` for worker context
- `.adworkflow/worker_state.json` for current task state
- `.adworkflow/verification_result.json` for verification evidence

Every implementation module must have:

- Task goal
- Non-goals
- Legacy entry IDs covered
- Module owner
- Branch name
- Acceptance criteria
- Test expectations
- Device validation expectations
- Risk level
- Required outputs

## 10. Permissions

Allowed during planning:

- Read existing source, tests, docs, and build files.
- Create or update planning documents under `tasks/`.
- Initialize/update `.adworkflow/` artifacts.
- Generate task specs and execution plans.

Allowed during implementation after task specs exist:

- Create and edit files under `mobile-native/`.
- Add Android tests under `mobile-native/`.
- Add protocol fixtures where required.
- Add Python tests or fixture validators when the task is explicitly about shared protocol compatibility.

Requires explicit user approval:

- Building signed APKs.
- Installing APKs on a device.
- Changing official package id behavior.
- Modifying existing `mobile/` release packaging.
- Modifying signing files or release aliases.
- Removing PWA/Capacitor APK path.
- Creating tags, pushing branches, or publishing releases.
- Introducing cloud sync or server-required Android runtime behavior.

Forbidden unless a new PRD approves it:

- Making Python mandatory for first-phase Android runtime.
- Using direct IndexedDB scraping as the primary migration path.
- Exporting API keys by default.
- Storing API keys as plain DataStore or Room strings.
- Wrapping the PWA as the native replacement UI.
- Calling real LLM providers from automated tests.

## 11. Feature Parity Policy

`tasks/native-legacy-entry-inventory.md` is the release-gate source of truth.

Rules:

- Every `P0` legacy entry must have a native implementation or explicit owner-approved waiver before replacement release.
- Every `P1` legacy entry must have a native path, reduced version, or documented deferral with no data loss.
- Missing `P0` coverage blocks replacement release.
- Internal preview APKs may be partial, but they must not be described as the PWA replacement.
- Release gate status must be updated as modules complete.

## 12. Data And Import Rules

Spaces are user-visible partitions.

Native import must support:

- Append to current space.
- Overwrite current space after explicit confirmation.
- Import into a new visible space.

Import requirements:

- Validate schema before writing.
- Show dry-run summary before destructive writes.
- Record `ImportBatch`.
- Write through a transaction coordinator.
- Skip invalid records with visible warnings where possible.
- Never silently import unsafe unknown fields.
- Exclude or redact key material by default.
- Be safe to re-run without uncontrolled duplication.

## 13. LLM Runtime Rules

Phase one native Android must call configured providers directly.

Required provider behavior:

- Profile validation.
- Connection test.
- Streaming or pending-state support where provider allows it.
- Capability flags such as vision support.
- Clear failure states.
- No real provider calls from automated tests.

Python proxy/API support may be designed as a future optional strategy, but must not block first-phase Android usability.

## 14. Source Parsing Rules

Supported legacy file types must have visible status:

- Existing export JSON
- PDF
- DOCX
- TXT
- Markdown
- HTML
- PPTX
- EPUB
- Images

Parser statuses:

- `supported_local`
- `partial_local`
- `queued_for_future_api`
- `unsupported`
- `failed`

Simple formats should be local where practical. Complex formats may reserve a future Python/API-assisted slot, but the user must see the status clearly.

## 15. Graph Rules

The native graph must use Android-native drawing and input handling.

Replacement parity must cover or explicitly waive:

- Pan
- Zoom
- Select/deselect
- Node detail sheet
- Related chat/source jump
- Semantic fragment/card review
- Node edit/save where legacy supports it
- Global graph browsing
- Per-session graph browsing
- Empty, loading, large, and invalid graph states

Graph data must follow shared node/edge protocols and persist in Room.

## 16. Verification Rules

### 16.1 Automated Tests

Task specs must require automated tests for:

- Protocol validation.
- Import/export mapping.
- Database schema and migrations.
- LLM parser and streaming behavior.
- Background job state.
- Graph state and layout behavior.
- Source parsing.
- Destructive data actions.

Repository Python tests still use:

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

Android-specific commands are defined in `.adworkflow/verification_commands.md` once the native project exists.

### 16.2 Device Validation

Every phase APK must record:

- APK version/name/package id.
- Device or emulator name.
- Android version.
- Tested flows.
- Result.
- Screenshots or failure notes.

Minimum replacement readiness matrix:

- Owner's main Android phone.
- One older/lower-end Android physical device, or lower Android version target if no device is available.
- One modern Android emulator.

### 16.3 Review Gates

Reviewer evidence is required before integration for:

- Import/export.
- Local data schema and migrations.
- API key storage.
- Background jobs.
- Graph interaction.
- Package/release switch.
- Source parsers.

## 17. Phase APK Policy

Each phase should produce an internal APK and device validation record, but Codex must not build or sign APKs unless explicitly requested in that turn.

When the user requests a phase APK, the implementation agent must:

1. Confirm active branch and version label.
2. Run required tests.
3. Build preview APK with `com.reversetutor.preview`.
4. Record validation evidence.
5. Report artifact path and residual risk.

## 18. Reporting Standard

Every module completion summary should include:

- Branch or task id.
- Files changed.
- Legacy IDs covered.
- Tests run and results.
- Device validation status.
- Review status if required.
- Remaining blockers or waivers needed.

Do not claim replacement readiness until the parity gate and device matrix are complete.

## 19. Open Project Decisions

- Exact first supported legacy export schema version.
- Exact older/lower-end Android validation device or Android version.
- Exact local parser libraries for PDF, DOCX, PPTX, and EPUB.
- Whether proactive behavior ships in the first native replacement release or is disabled with an explicit no-op state.
- Final official replacement signing and package-switch runbook.
