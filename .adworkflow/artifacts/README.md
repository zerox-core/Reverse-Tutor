# ADworkflo Task Artifacts

Use this directory to archive completed task artifacts when a task is important enough to preserve.

Recommended structure:

```text
.adworkflow/artifacts/
  <task_id>/
    task_spec.json
    execution_plan.json
    context_raw.json
    context_manifest.json
    worker_state.json
    verification_result.json
    review_findings.json
    final_summary.md
```

The files directly under `.adworkflow/` are the current working artifacts. Archive them here when the task is completed or when the main window needs a stable handoff record.
