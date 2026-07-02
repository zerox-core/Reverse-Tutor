# Native Android UI/UX Roadmap

Date: 2026-07-01

Purpose: make UI quality a first-class migration track. Functional parity is required, but it is not sufficient for replacement readiness.

Current UX baseline:

- `NATIVE-UX-001` is complete as a documentation/specification task.
- Design system: `tasks/native-android-ui-design-system.md`
- Acceptance checklist: `tasks/native-android-ui-acceptance-checklist.md`
- No Kotlin source, coverage registry status, ADworkflo active state, P2-006 evidence, PWA files, signing files, or old mobile packaging files were changed by UX-001.
- UX-001 provides direction for later implementation but is not implementation or replacement-readiness evidence.

## Strategy

- Keep first native release close to the legacy functional layout so existing users can migrate without relearning the product.
- Use native Android layout, Material 3, Compose state, and Android interaction patterns rather than WebView/PWA carry-over.
- Build a shared design system before polishing every screen, so modules do not diverge.
- Verify UI on real screens with screenshots, dynamic type, dark mode, IME, and touch-target checks.

## Task Track

| Task | Status | Scope | Depends On | Output |
|---|---|---|---|---|
| `NATIVE-UX-001` | Complete as spec | Design system, screen hierarchy, component inventory, UI acceptance checklist | `NATIVE-P2-006` | `tasks/native-android-ui-design-system.md`, `tasks/native-android-ui-acceptance-checklist.md` |
| `NATIVE-UX-002` | Ready for implementation | Compose theme tokens, adaptive shell, and shared components | `NATIVE-UX-001` | Patch + component/UI state tests |
| `NATIVE-UX-003` | Waiting on UX-002 | Sessions, Chat, composer, generation states, and LLM settings polish | `NATIVE-UX-002`, Phase 2 | Patch + screenshots or unavailable-device notes |
| `NATIVE-UX-004` | Waiting on UX-002 and Phase 4 foundations | Import/export, wipe, import results, and replacement-path first-launch prompt | `NATIVE-UX-002`, Phase 4 | Patch or page overrides + state screenshots |
| `NATIVE-UX-005` | Waiting on UX-002 and Phase 5 surfaces | Context hub, graph, sources, parser status, and evidence references | `NATIVE-UX-001`, `NATIVE-UX-002`, `NATIVE-P5-001`, `NATIVE-P5-005` | Patch or page overrides + state matrix |
| `NATIVE-UX-007` | Waiting on UX-005 and Phase 5 context work | Context Hub IA, navigation hierarchy, object grouping, evidence-reference placement | `NATIVE-UX-005`, `NATIVE-P5-001`, `NATIVE-P5-002`, `NATIVE-P5-007` | IA spec + screen flows |
| `NATIVE-UX-008` | Waiting on UX-005 and graph engine work | Graph interaction spec for pan, zoom, selection, detail sheets, edit affordances, review parity | `NATIVE-UX-005`, `NATIVE-P5-003`, `NATIVE-P5-004` | Interaction spec + graph state matrix |
| `NATIVE-UX-009` | Waiting on UX-005 and source parser work | Source library and parser status UI for supported, partial, deferred, unsupported, failed states | `NATIVE-UX-005`, `NATIVE-P5-005`, `NATIVE-P5-006` | Parser status UI spec + copy matrix |
| `NATIVE-UX-010` | Waiting on Phase 5 implementation/specs | Phase 5 UI QA for Context Hub, graph, sources, parser status, evidence-reference flows | `NATIVE-UX-007`, `NATIVE-UX-008`, `NATIVE-UX-009`, `NATIVE-P5-008` | Phase 5 UI QA matrix + screenshots |
| `NATIVE-UX-006` | Final UI gate later | Final UI QA matrix before replacement readiness | Phase 3, 4, 5, UX pages, `NATIVE-UX-010` | UI QA matrix + reviewer check |

## Execution Plan Sync

- `NATIVE-UX-001` now provides the design-system and acceptance-checklist inputs that downstream UX workers should read before touching UI files.
- `NATIVE-UX-002` is the only next task that should create shared tokens and components. It should not rewrite all feature screens in one pass.
- `NATIVE-UX-003`, `NATIVE-UX-004`, and `NATIVE-UX-005` apply the UX-002 foundation to feature surfaces in separate slices.
- `NATIVE-UX-007`..`NATIVE-UX-010` are synchronized into `.adworkflow/execution_plan.json` because the existing UX track already has batch structure and task spec paths suitable for follow-up UI/UX refinement work.
- The Phase 5 detail spec batch follows UX core pages and Phase 5 graph-context integration. `NATIVE-UX-010` follows the detail specs and Phase 5 validation before the all-up `NATIVE-UX-006` final UI QA gate.

## Baseline Rules

- The first screen is a usable product screen, normally Sessions. Do not add a marketing or hero landing page.
- Bottom navigation must not exceed usable density on small devices; secondary destinations can move to grouped flows when needed.
- Primary touch targets must be at least 48dp.
- Chat composer must remain visible with the IME open.
- Dark mode and large text must be tested, not inferred.
- Error, no-model, pending, and disabled states must have clear copy and recovery action.
- Import/export and wipe flows need stronger confirmation and result summaries than ordinary settings.
- Graph UI must be native Canvas/Compose interaction with pan, zoom, selection, and detail sheet.
- Source parser status must be explicit: supported, partial, deferred, unsupported, or failed.
- A design document is not coverage evidence. Update the coverage registry only after real code and verification evidence exist.

## Replacement Gate

`NATIVE-UX-006` cannot pass while unresolved P0 functional blockers remain. UI polish can improve internal preview quality, but it does not authorize PWA exit or official package replacement.
