# ADworkflo Verification Commands: Native Android Migration

Use the smallest deterministic check that proves the task acceptance criteria. If a command is skipped, record the reason in `.adworkflow/verification_result.json`.

## Planning Artifact Checks

```powershell
Test-Path .\tasks\prd-native-android-migration.md
Test-Path .\tasks\native-legacy-entry-inventory.md
Test-Path .\tasks\arch-native-android-migration.md
Test-Path .\tasks\project-native-android-migration.md
Test-Path .\tasks\todo-native-android-migration.md
Test-Path .\.adworkflow\architecture_manifest.json
Test-Path .\.adworkflow\execution_plan.json
```

## Product Doc Analysis

```powershell
$env:ADWORKFLO_SKILL_ROOT='F:\CodexHome\skills\ADworkflo'
py -3 $env:ADWORKFLO_SKILL_ROOT\scripts\analyze_project_plan.py --project 'F:\xw\reverse-tutor' --docs 'F:\xw\reverse-tutor\tasks\prd-native-android-migration.md' 'F:\xw\reverse-tutor\tasks\native-legacy-entry-inventory.md' 'F:\xw\reverse-tutor\tasks\arch-native-android-migration.md' 'F:\xw\reverse-tutor\tasks\project-native-android-migration.md' 'F:\xw\reverse-tutor\tasks\todo-native-android-migration.md' --update-profile
```

## Existing Python Baseline

Run when a task changes Python reference logic, protocol validation helpers, fixtures, or import/export tests:

```powershell
py -m pytest -q --ignore=tests/test_project_homepage.py
```

Targeted Python tests:

```powershell
py -m pytest tests/test_xxx.py -v
```

## Native Android Commands

Use after `mobile-native/` exists.

From `F:\xw\reverse-tutor\mobile-native`:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat test
.\gradlew.bat lint
```

Module-specific examples once modules exist:

```powershell
.\gradlew.bat :core:protocol:test
.\gradlew.bat :core:data:test
.\gradlew.bat :core:llm:test
.\gradlew.bat :feature:chat:test
.\gradlew.bat :feature:memory:test
.\gradlew.bat :feature:sources:test
.\gradlew.bat :feature:settings:test
```

Instrumentation/device checks only when a device or emulator is available and the task requires it:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## APK Policy

Do not build signed APKs unless the user explicitly requests it in the active turn.

When requested for internal preview validation:

```powershell
.\gradlew.bat :app:assembleDebug
```

Release or official replacement builds require a dedicated package/release task, signing checklist, and explicit user approval.

## Prohibited Verification Behavior

- Do not call real LLM providers from automated tests.
- Do not mutate remote services.
- Do not modify signing files as part of verification.
- Do not mark device validation as passed without recording device/emulator name, Android version, tested flows, and result.
