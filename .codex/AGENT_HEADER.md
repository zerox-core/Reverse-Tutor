# ADworkflo Agent Header

This project uses ADworkflo for AI-assisted engineering execution.

## Project Profile

- Project size: large
- Context strategy: architecture-first L2/codegraph after native implementation files exist
- Classification source: product docs explicit native Android migration
- Execution mode: orchestrator-with-workers-and-reviewers
- Expected complexity score: 34
- Current implementation target: `mobile-native/`
- Current task: `NATIVE-P1-001`
- Existing source files scanned: 72
- Approx source lines: 16763
- Languages detected now: java, javascript, python
- Planned language: kotlin

## Core Boundaries

1. Native Android is the future mobile mainline.
2. Python remains protocol/API/test baseline and future service foundation.
3. PWA/Capacitor remains transition/export source until native parity is approved.
4. Internal native package id is `com.reversetutor.preview`.
5. Official package id `com.reversetutor.app` is only for approved replacement builds.
6. Do not touch existing `mobile/` signing or application id without explicit approval.
7. Do not use WebView/PWA carry-over as the native replacement UI.
8. Do not make Python mandatory for first-phase Android runtime.
9. Do not use direct IndexedDB scraping as the primary migration path.

## Execution Rules

1. For non-trivial tasks, create or update `.adworkflow/task_spec.json` before implementation.
2. For native migration tasks, read the five planning docs before relying on codegraph.
3. Use `.adworkflow/execution_plan.json` for phase/module ordering.
4. Do not read the whole repository by default. Read `.adworkflow/context_manifest.json` first.
5. Existing `mobile/` files may be reference-only unless the task explicitly allows compatibility/export changes.
6. Use codegraph after enough `mobile-native/` implementation exists to make symbol/import/test lookup meaningful.
7. Worker count is unbounded by design and follows TODO module split plus ARCH dependencies.
8. After edits, update `.adworkflow/worker_state.json`.
9. Before claiming completion, update `.adworkflow/verification_result.json`.
10. For medium/high risk tasks, use review based on task spec, diff/change summary, verification result, and minimal context.
11. Do not use long chat history as handoff material. Use ADworkflo artifacts.

## Main Window Flow

When the user gives a development task in the main window:

1. Confirm whether the user is asking for planning, task-spec preparation, or code implementation.
2. If implementing, use the active task spec or create a new scoped task spec from TODO.
3. Run or update context preparation:

```powershell
py -3 $env:ADWORKFLO_SKILL_ROOT\scripts\prepare_context.py --project .
```

4. Read `.adworkflow/context_manifest.json` before implementation.
5. If `.adworkflow/module_skills.md` names a relevant module route, follow it before editing.
6. Implement only the scoped task.
7. If ARCH/TODO detail is missing, report the question and record the decision or fallback in `worker_state.json`.
8. Run commands from `.adworkflow/verification_commands.md` when applicable.
9. Update `worker_state.json`, `verification_result.json`, and `review_findings.json` when risk requires review.

## Local Files

- `.adworkflow/PROJECT.md`: current project operating state.
- `.adworkflow/architecture_manifest.json`: product-doc-based module, risk, and execution strategy analysis.
- `.adworkflow/execution_plan.json`: TODO-driven orchestration plan.
- `.adworkflow/task_specs/`: per-module task specs for workers.
- `.adworkflow/task_spec.json`: current task contract.
- `.adworkflow/context_raw.json`: raw retrieval evidence from codegraph or architecture docs.
- `.adworkflow/context_manifest.json`: scoped context for worker.
- `.adworkflow/worker_state.json`: compact worker state.
- `.adworkflow/verification_result.json`: verification evidence.
- `.adworkflow/review_findings.json`: structured review output.
- `.adworkflow/permissions.md`: project-local permission boundary.
- `.adworkflow/verification_commands.md`: preferred local verification commands.
- `.adworkflow/module_skills.md`: module-specific routing rules.
- `.adworkflow/review_checklist.md`: reviewer checklist.
- `.adworkflow/final_summary.template.md`: final summary template.
- `.adworkflow/artifacts/`: optional completed-task artifact archive.
- `.codegraph/config.json`: project codegraph configuration.
- `.codegraph/index.json`: generated lightweight codegraph index when available.
