# Native First-Launch Import Prompt Runbook

Date: 2026-07-01
Task: `NATIVE-P4-006`

## Purpose

The first-launch import prompt is for a future approved replacement build. It should guide existing users to import an export JSON from the current PWA/Capacitor version without implying that the internal preview APK is replacement-ready.

## Preview Build Rule

- Internal preview package: `com.reversetutor.preview`.
- The prompt is implemented but disabled by default in preview builds.
- Do not switch package id, signing, or cover-install behavior in this task.

## Replacement Build Gate

Enable the prompt only after Phase 6 approval confirms:

- Official package/signing runbook is approved.
- P0 legacy parity rows are verified or explicitly waived.
- Migration guide is published.
- Device matrix covers import, export, wipe, and first-launch prompt flows.
- User explicitly approves PWA/Capacitor exit or replacement.

## Prompt Behavior

- Show only on first launch of an approved replacement build when the user has not already seen the prompt.
- Confirm action routes to the native Import/export screen.
- Dismiss action hides the prompt for that launch/session and should become persisted when replacement-build preferences are finalized.
- Prompt copy must state that API keys are not migrated and must be re-entered in LLM profiles.

## Supported Migration Path

- Supported path: export JSON from existing version, then import JSON in native Android.
- Do not use direct IndexedDB scraping as the primary path.
- Do not import API keys by default.

## P4-007 Validation Notes

Device validation should verify:

- Prompt appears only when replacement-build flag/state is enabled.
- Confirm opens Import/export.
- Dismiss leaves the user in the normal app entry.
- Copy mentions export JSON and API key non-migration.
- No package id or signing behavior changes in preview APK.
