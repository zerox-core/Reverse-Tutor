# ADworkflo Review Checklist: Native Android Migration

Use review for medium/high risk tasks and for any change touching shared contracts, local data, secrets, background jobs, graph behavior, source parsing, package identity, or legacy parity gates.

## Review Inputs

- `.adworkflow/task_spec.json` or `.adworkflow/task_specs/<task_id>.json`
- `.adworkflow/context_manifest.json`
- Patch or changed-file summary
- `.adworkflow/verification_result.json`
- Relevant PRD/ARCH/PROJECT/TODO sections
- Legacy IDs covered by the task

## Required Checks

- Acceptance criteria are fully covered.
- Non-goals were respected.
- Existing unrelated user changes were not reverted.
- `mobile/` package/signing boundaries were respected unless explicitly approved.
- `mobile-native/` remains the native implementation target.
- Python is not made mandatory for first-phase Android runtime.
- No WebView/PWA carry-over is used as replacement UI.
- API keys are not stored or exported unsafely.
- Import/export behavior validates schemas and handles errors visibly.
- Background jobs cannot write into wrong or deleted sessions.
- Graph/source behavior updates parity/status checklists where applicable.
- Verification matches risk level and skipped checks are justified.

## High-Risk Modules

- Local data schema and migrations.
- Import/export protocol and transaction writes.
- API key storage and redaction.
- Direct LLM runtime and streaming.
- Background generation jobs and notifications.
- Native graph interaction.
- Source parsers.
- Package id, signing, cover-install, and PWA exit.
