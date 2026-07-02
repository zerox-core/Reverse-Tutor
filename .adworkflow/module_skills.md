# ADworkflo Module Skills: Native Android Migration

Use this file to route module work. These are not separate reusable Codex skills yet; they are project-local module rules derived from `tasks/arch-native-android-migration.md` and `tasks/project-native-android-migration.md`.

## Routing Format

```text
module: <module-or-domain-name>
skill: <project-local rule set or existing skill>
when: <when the main window should use it>
inputs: <task_spec/context_manifest/files needed>
outputs: <expected artifact or implementation output>
verification: <preferred checks>
```

## Module Routes

module: native-app-shell
skill: tasks/arch-native-android-migration.md sections 3, 4, 13, 15
when: app entry, package id, navigation host, theme, back behavior, phase APK shell
inputs: task_spec, context_manifest, PROJECT, ARCH, legacy IDs LEG-001, LEG-002, LEG-039, LEG-040
outputs: patch, worker_state.json, verification_result.json
verification: Gradle assemble/check when project exists; launch/navigation device smoke when APK requested

module: native-protocol
skill: tasks/arch-native-android-migration.md section 7; tasks/todo-native-android-migration.md Phase 4
when: import/export schemas, turn request/result, graph node/edge contracts, LLM profile contracts, fixture validation
inputs: task_spec, Python/PWA export references, protocol fixtures, legacy IDs LEG-010, LEG-031, LEG-032, LEG-043
outputs: patch, fixture list, worker_state.json, verification_result.json
verification: schema/fixture tests; no real user data committed

module: native-data
skill: tasks/arch-native-android-migration.md sections 6, 10
when: Room schema, repositories, migrations, spaces, import transaction coordinator, destructive wipe
inputs: task_spec, data model docs, protocol contracts, legacy IDs LEG-005, LEG-012, LEG-020, LEG-031, LEG-032, LEG-033, LEG-034
outputs: patch, migration notes, worker_state.json, verification_result.json
verification: DAO tests, migration tests, import transaction tests

module: native-llm
skill: tasks/arch-native-android-migration.md section 8
when: provider profiles, direct Android provider calls, streaming parser, diagnostics, vision capability handling, no-model fallback
inputs: task_spec, provider contract, LLM profile protocol, legacy IDs LEG-016, LEG-018, LEG-026, LEG-027, LEG-028, LEG-042
outputs: patch, worker_state.json, verification_result.json
verification: mocked provider tests, streaming parser tests, profile validation tests; never real LLM in automated tests

module: native-chat
skill: tasks/arch-native-android-migration.md sections 8, 13, 14
when: session list, session actions, new session flow, templates, chat timeline, composer, quote, message actions
inputs: task_spec, data repositories, LLM runtime interface, legacy IDs LEG-005 to LEG-018, LEG-041, LEG-043
outputs: patch, worker_state.json, verification_result.json
verification: UI state tests where possible, repository tests, device IME/chat smoke when APK requested

module: native-background
skill: tasks/arch-native-android-migration.md section 9
when: WorkManager generation jobs, notifications, cancellation, stale/deleted session isolation
inputs: task_spec, job protocol, data repositories, LLM runtime, legacy IDs LEG-016, LEG-017, LEG-029, LEG-030
outputs: patch, worker_state.json, verification_result.json
verification: job state tests, deleted-session isolation tests, notification device validation

module: native-memory-graph
skill: tasks/arch-native-android-migration.md sections 12, 13, 14; tasks/native-legacy-entry-inventory.md graph checklist
when: context hub, anchors, notes, errors, native graph, global graph, graph edit/review parity
inputs: task_spec, graph data protocol, memory data model, legacy IDs LEG-019 to LEG-025
outputs: patch, graph parity notes, worker_state.json, verification_result.json
verification: graph state/layout tests, touch gesture device validation, parity checklist update

module: native-sources
skill: tasks/arch-native-android-migration.md section 11
when: Android file picker, source library, local parsers, parser status, source chunks, image source import
inputs: task_spec, parser status matrix, source data model, legacy IDs LEG-011, LEG-018, LEG-021, LEG-034, LEG-035, LEG-036
outputs: patch, parser status update, worker_state.json, verification_result.json
verification: parser fixture tests, source import transaction tests, file picker device smoke

module: native-settings
skill: tasks/arch-native-android-migration.md sections 4, 10, 15
when: settings UI, LLM profiles UI, import/export/wipe UI, theme/avatar/memo, diagnostics/about/update
inputs: task_spec, DataStore/Room contracts, package metadata, legacy IDs LEG-003, LEG-004, LEG-026 to LEG-033, LEG-037, LEG-038, LEG-044
outputs: patch, worker_state.json, verification_result.json
verification: preference persistence tests, redaction tests, destructive confirmation checks

module: native-package-release
skill: tasks/project-native-android-migration.md sections 7, 17; AGENTS.md signing rules
when: preview package id, official package switch, signing, cover-install, phase APK records, PWA exit
inputs: task_spec, PROJECT, AGENTS.md, legacy parity matrix, device validation records
outputs: runbook, validation record, worker_state.json, verification_result.json
verification: package id inspection, signing checklist, cover-install validation only after explicit approval

module: native-qa-parity
skill: tasks/native-legacy-entry-inventory.md; tasks/project-native-android-migration.md sections 11, 16
when: LEG-001 to LEG-044 audit, phase validation, replacement readiness matrix
inputs: task_spec, legacy inventory, module verification results, device records
outputs: coverage matrix update, worker_state.json, verification_result.json
verification: no missing P0 without waiver; device matrix complete before replacement readiness
