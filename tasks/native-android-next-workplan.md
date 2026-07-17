# Native Android Next Workplan

Date: 2026-07-02
Mode: ADworkflo goal-driven execution
Current baseline: Phase 4 import/export/prompt foundations are implemented, `NATIVE-P4-007` has HMA-AL00 device validation evidence for import modes/export readiness/wipe confirmation, `NATIVE-UX-004` has productized import/export/wipe/prompt UI implementation plus HMA-AL00 screenshot/XML evidence, `NATIVE-P5-001` has Context Hub shell evidence, `NATIVE-P5-005` has Source Library/local parser evidence, `NATIVE-P5-002` has memory note/anchor/error repository plus chat-note-to-context-hub evidence, `NATIVE-P5-003` has native graph engine/Canvas/select/detail/global-route evidence, `NATIVE-P5-006` has image source/chat attachment plus complex parser status evidence, `NATIVE-P5-004` has graph edit/review/evidence handoff evidence, and `NATIVE-P5-007` has memory/source context injection evidence.

## Current Gate Snapshot

- Coverage registry rows: `LEG-001` through `LEG-044`.
- Current status summary: `verified=4`, `in_progress=38`, `not_started=2`.
- Current P0 replacement blockers: `28`.
- Replacement readiness: not achieved.
- PWA exit decision: not eligible until Phase 6 evidence and explicit user approval.

## Immediate Execution Order

### 1. NATIVE-P2-006: Phase 2 Preview Validation

Reason: Phase 2 should be validated as a whole after session creation, chat persistence, profile settings, and generation lifecycle are connected.

Validation target:

- Create/open session.
- Configure/select profile or see no-model state.
- Send chat turn and receive fake-runtime assistant reply.
- Persist timeline across restart.
- Verify no stale reply attaches to the wrong session.
- Verify no real provider call is required for automated/device smoke.

Gate impact:

- Moves Phase 2 from individual task evidence to integrated preview evidence.
- Does not close replacement blockers that depend on WorkManager, live providers, import/export, graph/source parity, or P6 matrix.

### 2. NATIVE-UX-001 to NATIVE-UX-006: UI/UX Track

Reason: Functional migration is not enough. Android needs a product-level UI system, layout rules, component consistency, accessibility, and screenshot/device QA.

Order:

1. `NATIVE-UX-001`: Design system and UI acceptance checklist.
2. `NATIVE-UX-002`: Token-driven Compose theme and shared components.
3. `NATIVE-UX-003`: Sessions, Chat, composer, generation states, and LLM settings polish.
4. `NATIVE-UX-004`: Import/export, wipe, result, and first-launch prompt UI. Completed for the Phase 4 surfaces with step-wise import flow, dry-run/result summaries, export key-material copy, graph snapshot export choice, preset deferred state, wipe return affordance, unit tests, full build/lint verification, and HMA-AL00 screenshot/XML evidence.
5. `NATIVE-UX-005`: Context hub, graph, source library, parser status, and evidence UI, queued after Phase 5 Context/source foundations.
6. `NATIVE-UX-006`: Final UI QA matrix for dark mode, dynamic type, IME, touch targets, and screenshots.

Gate impact:

- Prevents feature modules from diverging visually.
- Does not replace functional task specs; it runs alongside them and verifies visual/product quality.

### 3. Phase 4 Import/Export Track

Recommended after Phase 2 validation because existing PWA users need a safe migration path before replacement can be discussed.

Order:

1. `NATIVE-P4-001`: Versioned protocol schemas and fixtures.
2. `NATIVE-P4-002`: Native import pipeline with validation/dry-run.
3. `NATIVE-P4-003`: Append, overwrite, and new-space import modes. Completed for repository/UI state, local JVM/build verification, and P4-007 device instrumentation coverage.
4. `NATIVE-P4-004`: Native export flows. Protocol builders, data-backed current-session/full-backup repository foundation, Android share/save delivery wiring, and P4-007 export readiness device instrumentation coverage are complete.
5. `NATIVE-P4-005`: Destructive wipe.
6. `NATIVE-P4-006`: First-launch import prompt. Implemented as replacement-build-gated prompt state and preview-disabled AppShell wiring with runbook notes.
7. `NATIVE-P4-007`: Phase 4 validation. Completed on HMA-AL00 / Android 10 through `Phase4ImportExportDeviceTest`; archived evidence should remain under the P4-007 artifact directory.

Gate impact:

- Main blockers still open after Phase 4 validation: `LEG-010`, `LEG-031`, `LEG-032`, plus export-related session coverage in `LEG-006`; `LEG-033` is closed by P4-007 wipe evidence.

### 4. Phase 5 Memory, Graph, And Sources Track

Recommended after import/export foundations, unless graph/source parity becomes the user's priority.

Order:

1. `NATIVE-P5-001`: Context hub shell. Completed for the native preview shell: active Chat now opens Context Hub, required surfaces are visible, graph/source parity is explicitly deferred, and HMA-AL00 device evidence exists.
2. `NATIVE-P5-005`: Source library and simple local parsers. Completed for native preview: Sources route, Android file-picker handoff, TXT/Markdown local parsing, sanitized HTML partial parsing, unsupported/failed visibility, source chunks/cards/snippets, reprocess action, bounded text read guard, JVM tests, and HMA-AL00 instrumentation evidence.
3. `NATIVE-P5-002`: Anchors, notes, and errors. Completed for native preview: memory repository, anchor/note/error persistence foundations, Context Hub real snapshot counts, Chat Note action, and HMA-AL00 evidence.
4. `NATIVE-P5-003`: Native knowledge graph engine. Completed for native preview: GraphRepository access, deterministic graph state/layout, invalid/empty/large states, native Compose Canvas rendering, pan/zoom/tap selection, node detail, Context Hub Graph route, Global graph route, JVM tests, and HMA-AL00 instrumentation evidence.
5. `NATIVE-P5-006`: Images and complex parser status. Completed for native preview: image picker metadata creates visible image sources, chat image attachments persist through the existing Room table, LLM image turns are gated on vision capability, PDF/DOCX/PPTX/EPUB/Image statuses are explicit, JVM/full build/lint verification passed, and `Phase5SourcesDeviceTest` passed on HMA-AL00 after the change.
6. `NATIVE-P5-004`: Graph edit and review parity. Completed for native preview: node label/status update path, NeedsReview/Approved statuses, session graph edit/review actions, memory-backed review cards, graph-to-chat/source target handoff labels, Global graph read-only review, JVM/full build/lint verification, and HMA-AL00 graph instrumentation evidence.
7. `NATIVE-P5-007`: Memory/source context in chat. Completed for native preview: bounded relevant memory/source context evidence, turn request/payload injection, visible Sources footer on assistant messages, graceful missing/partial evidence handling, JVM/full build/lint verification.
8. `NATIVE-P5-008`: Phase 5 validation.

Gate impact:

- Main blockers: `LEG-019` through `LEG-025`, `LEG-034`, plus P1 source/image rows `LEG-035` and `LEG-036`. `LEG-019` is in progress through shell/navigation evidence; `LEG-020` and `LEG-025` now have native graph engine plus edit/review foundation evidence; `LEG-021`, `LEG-022`, and `LEG-023` now have memory repository, Chat Note, and context-injection evidence; `LEG-034`, `LEG-035`, and `LEG-036` now have Source Library/parser/image attachment/source-context foundation evidence. None of the Phase 5 replacement blockers are closed yet.

### 5. Phase 3 Background And Reliability Track

Can start after P2 validation. Recommended before final replacement audit, and after generation lifecycle contracts are stable.

Order:

1. `NATIVE-P3-001`: WorkManager generation jobs.
2. `NATIVE-P3-002`: Notifications and background settings.
3. `NATIVE-P3-003`: Diagnostics and error records.
4. `NATIVE-P3-004`: Phase 3 validation.

Gate impact:

- Main blockers: `LEG-016`, `LEG-017`, `LEG-028`; watch rows `LEG-023`, `LEG-029`, `LEG-030`, `LEG-044`.

### 6. Phase 6 Replacement Readiness And PWA Exit

Cannot start as a completion claim until Phase 2, 3, 4, and 5 evidence exists.

Order:

1. `NATIVE-P6-001`: Full legacy entry audit.
2. `NATIVE-P6-002`: Replacement package readiness runbook.
3. `NATIVE-P6-003`: Regression and device matrix.
4. `NATIVE-P6-004`: Migration guide.
5. `NATIVE-P6-005`: PWA exit decision.

Hard rule:

- Do not retire PWA/Capacitor APK path until all P0 rows are verified or explicitly waived, migration guidance exists, device matrix is complete, and the user explicitly approves exit.

## Recommended Next Action

Continue with ADworkflo from the next unblocked batches:

1. Continue with `NATIVE-UX-005` Phase 5 Context/graph/source UI polish, then `NATIVE-P5-008` validation.
2. Keep `NATIVE-UX-005` queued behind Phase 5 Context/source foundations.
3. Keep `NATIVE-UX-006` as the final all-up UI QA gate after Phase 3, Phase 4, Phase 5, and Phase 5 UI QA evidence.
4. Keep PWA/Capacitor as migration/export source until Phase 6 evidence and explicit approval.
