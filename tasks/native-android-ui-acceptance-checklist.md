# Native Android UI Acceptance Checklist

Date: 2026-07-01
Task: `NATIVE-UX-001`

## 1. Purpose

This checklist turns the native Android UI design system into concrete acceptance checks for `NATIVE-UX-002` through `NATIVE-UX-010`. It is a planning and verification guide, not implementation evidence by itself.

Use this checklist during future UI work to decide what to test, screenshot, and record. Do not update `tasks/native-legacy-coverage-registry.md` from this checklist alone. Registry status changes require real implementation plus automated or device evidence.

## 2. Result Vocabulary

Use these labels in future verification notes:

| Result | Meaning |
|---|---|
| Pass | Checked on the current implementation with evidence |
| Fail | Checked and does not meet the criterion |
| Blocked | Cannot be checked because dependency or device is unavailable |
| Not applicable | Criterion does not apply to this task or screen |

Every `Pass` needs a file, test command, screenshot path, or device note. Every `Blocked` needs a reason and next owner.

## 3. Global Product Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-G-001 | UX-002..006 | First screen is a usable product surface, normally Sessions, not a hero or marketing page | Screenshot or UI test |
| UI-G-002 | UX-002..006 | Empty first-run state offers product actions: create session, configure model, import backup where available | Screenshot or UI state test |
| UI-G-003 | UX-002..006 | PWA/WebView surfaces are not used as native replacement UI | Code review and screenshot |
| UI-G-004 | UX-002..006 | UI does not claim replacement readiness or PWA exit | Copy review |
| UI-G-005 | UX-002..006 | Deferred actions have visible handoff or recovery copy | UI state test or screenshot |

## 4. Token And Component Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-T-001 | UX-002 | Light and dark Material 3 color schemes exist | Code review and screenshot |
| UI-T-002 | UX-002 | Semantic colors exist for success, warning, info, error, disabled, parser status, and generation status | Code review |
| UI-T-003 | UX-002 | Typography uses theme roles rather than one-off screen-local sizes for new shared components | Code review |
| UI-T-004 | UX-002 | Spacing, shape, elevation, and touch target tokens are available to feature modules | Code review |
| UI-T-005 | UX-002 | Shared components exist for state panels, status strips, badges, cards, dialogs, sheets, chips, and destructive confirmations | Code review or component tests |
| UI-T-006 | UX-002 | Cards use 8dp radius or less unless a sheet/dialog pattern requires otherwise | Screenshot and code review |
| UI-T-007 | UX-002 | No card is nested inside another card in new shared layouts | Screenshot and code review |
| UI-T-008 | UX-002 | Icon-only actions have content descriptions | Accessibility or code review |

## 5. Navigation And Shell Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-N-001 | UX-002, UX-003 | Primary destinations are Sessions, Chat, Context, Sources, Settings | Screenshot or UI test |
| UI-N-002 | UX-002, UX-003 | About/diagnostics is a Settings subroute, not a crowded primary destination | Screenshot or navigation test |
| UI-N-003 | UX-002, UX-003 | Bottom navigation or rail keeps 48dp targets and readable labels on small screens | Screenshot or layout inspection |
| UI-N-004 | UX-002, UX-003 | System back closes modal/sheet before leaving the screen | Device or UI test |
| UI-N-005 | UX-002, UX-003 | Chat back returns to Sessions and Context back returns to Chat | Device or UI test |
| UI-N-006 | UX-003..006 | Background or generation result does not force navigation to another session | Unit or device test |

## 6. Sessions And Chat Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-C-001 | UX-003 | Sessions screen shows search/filter, create action, and recognizable session cards | Screenshot or UI test |
| UI-C-002 | UX-003 | Session actions include open, rename, pin, delete, export, and avatar path or explicit deferred state | Screenshot or UI state test |
| UI-C-003 | UX-003 | New session flow covers templates, preset import, custom profile, and source handoff path | Screenshot or UI state test |
| UI-C-004 | UX-003 | Chat header shows active session and context hub action | Screenshot |
| UI-C-005 | UX-003 | Timeline renders user and AI/student messages with clear role labels | Screenshot or UI test |
| UI-C-006 | UX-003 | Quote preview, quote cancel, and quoted message rendering remain visible at large text | Screenshot or UI test |
| UI-C-007 | UX-003 | Composer remains visible with IME open and navigation bar present | Device screenshot |
| UI-C-008 | UX-003 | Generation states cover pending, streaming or waiting, failed, no-model, unsupported vision, and idle | UI state tests |
| UI-C-009 | UX-003 | Message actions are reachable without text overlap on small screens | Screenshot |
| UI-C-010 | UX-003 | Blank send is disabled or blocked without side effects | UI state test |

## 7. LLM Settings Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-L-001 | UX-003 | LLM profiles are grouped separately from ordinary preferences | Screenshot |
| UI-L-002 | UX-003 | Presets, model, base URL, key, capability hints, save, activate, test, and delete are represented | Screenshot or UI test |
| UI-L-003 | UX-003 | API key input is masked and not echoed in profile cards or diagnostics | Screenshot and code review |
| UI-L-004 | UX-003 | Connection test result has success, failure, and no-profile states | UI state test |
| UI-L-005 | UX-003 | Disabled save/test/delete states show a reason or clear context | UI state test or screenshot |

## 8. Import, Export, Wipe, And First-Launch Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-I-001 | UX-004 | Import flow follows select, detect, validate, dry-run, mode, confirm, write, result | UI flow test or screenshots |
| UI-I-002 | UX-004 | Import modes are append, overwrite, and new space | Screenshot or UI test |
| UI-I-003 | UX-004 | Overwrite mode has strong destructive confirmation | Screenshot or UI test |
| UI-I-004 | UX-004 | Dry-run summary shows source file, schema, counts, warnings, errors, and API key handling | Screenshot |
| UI-I-005 | UX-004 | Result report shows inserted, skipped, failed, target space, and next actions | Screenshot |
| UI-I-006 | UX-004 | Export choices include session, graph snapshot, full backup, and preset where supported | Screenshot |
| UI-I-007 | UX-004 | Export copy states that key material is excluded or redacted by default | Copy review |
| UI-I-008 | UX-004 | Data wipe has confirmation, result, and safe empty-state return | UI test or screenshots |
| UI-I-009 | UX-004 | First-launch import prompt is scoped to replacement path and is not default preview launch | Code review or build variant note |

## 9. Context Hub, Graph, And Sources Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-M-001 | UX-005, UX-007 | Context hub includes overview, graph, anchors, notes, errors, and session settings | Screenshot or IA spec |
| UI-M-002 | UX-005, UX-007 | Evidence links show source/message relationship and provide a return path | UI state test or spec |
| UI-M-003 | UX-005, UX-007 | Empty, loading, error, and permission/deferred states are specified for each context section | UI state matrix |
| UI-GR-001 | UX-005, UX-008 | Graph is native Canvas/Compose, not WebView | Code review |
| UI-GR-002 | UX-005, UX-008 | Graph supports pan, zoom, select/deselect, detail sheet, related jumps, and reset/fit controls | Device test or interaction spec |
| UI-GR-003 | UX-005, UX-008 | Graph has empty, loading, large, invalid, and edit/review states | State matrix |
| UI-S-001 | UX-005, UX-009 | Source library shows title, type, parser status, processed time, snippets/chunks, and linked session/anchor when available | Screenshot |
| UI-S-002 | UX-005, UX-009 | Parser statuses distinguish supported, partial, deferred, unsupported, and failed | State copy matrix |
| UI-S-003 | UX-005, UX-009 | Partial, deferred, unsupported, or failed files remain visible and recoverable | UI state test |
| UI-S-004 | UX-005, UX-009 | Image source and chat attachment capability states are separate and clear | UI state test or copy matrix |

## 10. Accessibility And Adaptivity Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-A-001 | UX-002..010 | Primary touch targets are at least 48dp | Layout inspection or screenshot |
| UI-A-002 | UX-002..010 | TalkBack reading order follows visual order for main flows | Accessibility inspection |
| UI-A-003 | UX-002..010 | Icon-only controls have content descriptions | Code review |
| UI-A-004 | UX-002..010 | Large font scale does not clip button labels, cards, composer, or bottom navigation | Screenshot |
| UI-A-005 | UX-002..010 | Dark mode screenshots are captured for representative screens | Screenshot |
| UI-A-006 | UX-003, UX-004 | IME does not obscure chat composer or focused form fields | Device screenshot |
| UI-A-007 | UX-002..010 | Navigation bar and display cutout/safe area do not obscure controls | Device screenshot |
| UI-A-008 | UX-005, UX-008 | Graph has a non-canvas semantic detail/list access path | Spec, code review, or accessibility test |

## 11. Device And Screenshot Matrix

Future screenshot evidence should include:

| Surface | Small phone | Main phone | Modern emulator | Dark mode | Large text | IME |
|---|---|---|---|---|---|---|
| Sessions | Required by UX-003 | Required by UX-003 | Preferred | Required | Required | Not applicable |
| Chat and composer | Required by UX-003 | Required by UX-003 | Preferred | Required | Required | Required |
| LLM settings | Required by UX-003 | Required by UX-003 | Preferred | Required | Required | Required for focused fields |
| Import/export/wipe | Required by UX-004 | Required by UX-004 | Preferred | Required | Required | Required for forms |
| Context hub | Required by UX-010 | Required by UX-010 | Preferred | Required | Required | Not applicable |
| Graph | Required by UX-010 | Required by UX-010 | Preferred | Required | Required | Not applicable |
| Sources/parser status | Required by UX-010 | Required by UX-010 | Preferred | Required | Required | Not applicable |

If a device is unavailable, record the unavailable-device note instead of implying the check passed.

## 12. Registry Safety Checks

| ID | Applies to | Criterion | Evidence expected |
|---|---|---|---|
| UI-R-001 | UX-001..010 | Do not mark a legacy row `verified` from design docs alone | Diff review |
| UI-R-002 | UX-002..010 | Registry row updates include code evidence and verification evidence | Diff review |
| UI-R-003 | UX-002..010 | P0 rows remain blockers unless verified or explicitly waived | Registry validator |
| UI-R-004 | UX-006, UX-010 | UI QA notes do not approve replacement readiness while functional blockers remain | Review finding |
