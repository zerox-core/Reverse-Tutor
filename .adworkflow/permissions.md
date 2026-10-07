# ADworkflo Permissions: Native Android Migration

This file defines the project-local operating boundary for AI-assisted engineering tasks.

## Allowed During Planning

- Read existing source, tests, docs, and build files.
- Create or update planning documents under `tasks/`.
- Initialize or update `.adworkflow/` artifacts.
- Analyze product docs and generate architecture/execution planning artifacts.
- Prepare task specs and context manifests.

## Allowed During Implementation After Task Specs Exist

- Create and edit files under `mobile-native/`.
- Add Android unit, instrumentation, or UI tests under `mobile-native/`.
- Add protocol fixtures and fixture validators required by the active task.
- Add Python tests only when the task explicitly covers shared protocol compatibility, import/export validation, or reference behavior.
- Run local deterministic build/test commands.
- Install native debug/preview APKs on explicitly connected local devices for task validation, screenshot capture, or smoke testing.
- Update `.adworkflow/worker_state.json`, `.adworkflow/verification_result.json`, and task-specific artifacts.

## Require User Confirmation

- Building signed APKs.
- Installing signed, release, or official replacement APKs on a device.
- Changing official package identity behavior.
- Modifying existing `mobile/` release packaging.
- Modifying signing files or release aliases.
- Removing or disabling the PWA/Capacitor APK release path.
- Adding, upgrading, or replacing major dependencies.
- Running commands that call external production services or real LLM providers.
- Pushing branches, creating tags, or publishing releases.
- Introducing cloud sync or server-required Android runtime behavior.

## Forbidden By Default

- Reverting unrelated user changes.
- Editing `mobile/android/app/release.jks`.
- Changing existing alias `reverse-tutor`.
- Changing existing PWA `applicationId com.reversetutor.app`.
- Making Python mandatory for first-phase Android runtime.
- Using direct IndexedDB scraping as the primary migration path.
- Exporting API keys by default.
- Storing API keys as plain Room or DataStore strings.
- Wrapping the current PWA as the native replacement UI.
- Calling real LLM providers from automated tests.
- Claiming completion without verification evidence.

## Windows / Repository Rules

- Use `py`, not `python`.
- Use PowerShell commands.
- Do not use `git push` or tags unless explicitly requested.
- Do not touch signing or release package files unless the active task and user approval allow it.
