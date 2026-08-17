# Native Legacy Entry Inventory

## Purpose

This document lists the current PWA/APK user-visible entry points that the native Android replacement must account for. It is a product and release-gate artifact, not an implementation plan.

Replacement rule:

- `P0`: must exist before native Android is presented as the PWA/APK replacement.
- `P1`: may ship with a simpler native version, but must have a documented path and no data loss.
- `P2`: can be deferred or removed if explicitly approved.

Source inspected:

- `static/app/index.html`
- Current PRD: `tasks/prd-native-android-migration.md`

## P6-001 Code Audit Notes (2026-08-17)

Full legacy entry code audit completed. Key findings per gate:

- **P0 (28 blockers)**: All have native code implementations and JVM test evidence. Device tests deferred.
- **P1 (16 watch items)**: Code exists for most; several remain not_started (LEG-040) or placeholder-only (LEG-037).
- **Freeze layer**: Zero modifications to core/model, core/protocol, core/llm, core/data.
- **No new contract gaps**: All native implementations use Repository/Facade/Coordinator patterns.

Audit report: `tasks/native-p6-001-legacy-audit.md`


## Release Gate Summary

The first public native replacement release must cover:

- Session list and session management
- New session creation, built-in presets, preset import, custom profile, avatar, and initial source selection
- Chat thread, composer, image attachment, quote, streaming/pending/queued states, and message actions
- Context hub: graph, anchors, notes, errors, and per-session settings
- Global graph/insights entry
- LLM configuration, provider presets, profile management, connection test, and diagnostics
- Data export/import/wipe flows, with native import modes: append, overwrite, new space
- Source import for legacy file types or explicit per-type status
- Background reply and notification settings/behavior
- Update/about/release diagnostics appropriate to native Android
- Global theme/avatar/memo controls or approved native replacements

## Inventory

| ID | Legacy Entry | Current Capability | Native Replacement Requirement | Gate |
|---|---|---|---|---|
| LEG-001 | Launch / splash | App splash with icon and title before main UI. | Native splash and app startup state; no blank WebView transition. | P0 |
| LEG-002 | Top header | Shows app/session title, mode/status, new-session button, learning-context button, graph search when browsing. | Native top app bar with equivalent context actions. | P0 |
| LEG-003 | Global sidebar | Theme selection, global avatar visibility, segmented global memo panel. | Native global settings drawer or settings screen covering theme, avatar visibility, and memo entry. | P1 |
| LEG-004 | Global memo | Segmented local memo text blocks with add/delete gestures. | Native memo surface, with at least view/edit/add/delete and local persistence. | P1 |
| LEG-005 | Session home | Session list, search/filter chips, pinned sessions, status badges, unread state, avatar visibility. | Native session list with search/filter, pin, status, unread, and avatar support. | P0 |
| LEG-006 | Session card actions | Swipe/long-press actions: pin, delete, rename, export, avatar management. | Native session action sheet or swipe actions covering pin/delete/rename/export/avatar. | P0 |
| LEG-007 | Session proactive state | Per-session online/offline/sleep state visible in list/header. | Native state display and controls for proactive mode if proactive behavior remains enabled. | P1 |
| LEG-008 | New session quick flow | Quick create from built-in presets, imported preset, or custom mode. | Native create-session entry with built-in presets, preset import, and custom profile path. | P0 |
| LEG-009 | Custom session profile | Title, role, goal, deadline, personality, long profile text, tags, strategy settings, avatar, hide avatar. | Native profile creation/edit form covering these fields. | P0 |
| LEG-010 | Preset import/export | JSON preset import, preview, save-only, create, customize; session preset export. | Native preset import/export with schema validation and safe setting sanitization. | P0 |
| LEG-011 | Initial source selection | Add source files while creating a session. | Native create-session source attachment or immediate post-create import. | P1 |
| LEG-012 | Chat thread | Message list, assistant/user bubbles, rich text, math/science rendering, cited sources, thinking/process summaries. | Native chat timeline with core bubble rendering, citations, and process-summary equivalent. | P0 |
| LEG-013 | Chat composer | Text input, send, image pick, image draft preview/cancel. | Native composer with text, send, image attachment, preview/cancel. | P0 |
| LEG-014 | Quote reply | Long-press message quote, quote preview, clear quote, send with quote context. | Native message quote flow and persisted quote context. | P0 |
| LEG-015 | Message actions | Long-press action sheet for quote, note, regenerate/archive/delete-style actions where present. | Native message action sheet covering current destructive and context actions. | P0 |
| LEG-016 | Streaming and queue | Streaming bubbles, pending queue, continuous input coalescing, stale-generation isolation. | Native generation lifecycle with streaming/pending/queue and stale result isolation. | P0 |
| LEG-017 | Background replies | Android background generation import, notifications, notification settings link. | Native background job implementation and notification status/settings surface. | P0 |
| LEG-018 | Chat image understanding | Image attachment sent to multimodal-capable models with fallback/error behavior. | Native image attachment pipeline and provider capability handling. | P1 |
| LEG-019 | Context hub entry | "脉络" entry from chat opens per-session context. | Native session context hub accessible from chat. | P0 |
| LEG-020 | Context graph | Per-session knowledge graph, pan/zoom/select, bottom sheet details, source/chat jump, node editor. | Native graph with full agreed legacy interaction and edit/save parity. | P0 |
| LEG-021 | Context anchors | Add requirements/anchors, import files/images, reprocess sources, delete anchors, grouped display. | Native anchors/source-mainline surface with add/import/reprocess/delete. | P0 |
| LEG-022 | Context notes | Notes created from chat messages, list, edit, delete. | Native notes view with create-from-message, edit, delete, local persistence. | P0 |
| LEG-023 | Context errors | Error log/misconception history, recurrence count, linked episode snippets, resolved state. | Native error/misconception view with linked message evidence. | P1 |
| LEG-024 | Context settings | Per-session strategy sliders/selectors, core self, persona edit warning, deadline status, avatar controls. | Native per-session settings with persona, strategy, deadline, avatar, and change-warning behavior. | P0 |
| LEG-025 | Global graph / insights | Global graph across sessions, summary counters, source/note/link counts, read-only node review. | Native global graph/insights entry, at least equivalent graph and summary review. | P0 |
| LEG-026 | LLM provider configuration | Provider presets, OpenAI/Anthropic protocol selection, base URL, model, key, vision hint. | Native LLM config with provider presets, protocol, endpoint validation, model/key, capability hint. | P0 |
| LEG-027 | LLM profiles | Save, switch, delete multiple LLM profiles. | Native profile manager with local secure/scoped key handling and redacted export. | P0 |
| LEG-028 | LLM diagnostics | Connection test, diagnostic modal, copy diagnostic text. | Native test connection and diagnostic detail/copy flow. | P0 |
| LEG-029 | Proactive settings | Global proactive controls and hidden per-session controls for online/offline/sleep. | Native proactive settings if proactive behavior ships; otherwise explicit product decision to disable. | P1 |
| LEG-030 | Update settings | Current version, auto-check, check update, background download/install handoff. | Native update/about surface appropriate to actual distribution channel. | P1 |
| LEG-031 | Data export | Export current session, global graph snapshot, full backup, save full backup to file manager. | Native export with equivalent payload types and Android share/save intents. | P0 |
| LEG-032 | Data import | Import JSON backup currently overwrites all data. | Native import with append, overwrite, and new-space modes; no direct IndexedDB migration. | P0 |
| LEG-033 | Data wipe | Clear all local data with confirmation. | Native destructive wipe with clear warning and recovery guidance. | P0 |
| LEG-034 | Source file import | PDF, DOCX, TXT, Markdown, HTML, PPTX, EPUB through anchors/context/new-session flows. | Native file picker plus parser-status matrix for each legacy type. | P0 |
| LEG-035 | Image source import | PNG/JPEG/WebP images as source materials. | Native image source import and storage; analysis behavior depends on model/provider support. | P1 |
| LEG-036 | Parsed source previews | Source cards/snippets, reprocess source file, cite/jump behavior. | Native source library snippets, metadata, reprocess path where parser supports it. | P1 |
| LEG-037 | Theme system | Multiple visual themes and texture treatment. | Native theme selection or approved reduced theme set. | P1 |
| LEG-038 | Avatar system | Global avatar visibility, per-session avatar choose/clear/hide. | Native per-session avatar support and global visibility control. | P0 |
| LEG-039 | Android back behavior | Native back delegates to in-app navigation: close modals/sheets, context, chat, session list. | Native back stack and modal dismissal parity. | P0 |
| LEG-040 | Edge/swipe behavior | In-app back gestures and session swipe actions. | Native gesture behavior where platform appropriate; no conflict with system navigation. | P1 |
| LEG-041 | Keyboard handling | Chat composer adjusts to soft keyboard and preserves cursor/input behavior. | Native IME-safe composer layout and message tail visibility. | P0 |
| LEG-042 | Offline/mock behavior | App can run without configured LLM by using mock/fallback behaviors. | Native local-first fallback or explicit no-model empty state; behavior must be decided in ARCH. | P0 |
| LEG-043 | Built-in templates | Built-in scenario templates for school, exam, work, language, habit, skill learning. | Native built-in templates and future template update strategy. | P0 |
| LEG-044 | App diagnostics/about | Version text, QQ group, local data notice, PWA install hints. | Native about/diagnostics screen with version, support channel, local data notice; no PWA install hints. | P1 |

## Source Type Status Matrix

Updated for `NATIVE-P5-008` Phase 5 preview validation. This records current native preview behavior only; it does not close Phase 6 source parity.

| Source Type | Current Legacy Support | Native Target | Phase 5 Native Preview Status | Replacement Risk |
|---|---|---|---|---|
| Existing export JSON | Full backup/session/graph/preset import-export | Native import/export protocol | Handled by Phase 4 import/export flows; Source Library keeps JSON exports as `queued_for_future_api` reference material rather than parsing them as sources. | Keep import/export parity in LEG-031/LEG-032 Phase 6 validation. |
| PDF | Import as source | Local if feasible, otherwise parser-status fallback | `queued_for_future_api`; file remains visible and recoverable, with no local text chunks yet. | P0 source parity remains blocked until complex parser/device matrix is complete. |
| DOCX | Import as source | Local if feasible, otherwise parser-status fallback | `queued_for_future_api`; file remains visible and recoverable, with no local text chunks yet. | P0 source parity remains blocked until complex parser/device matrix is complete. |
| TXT | Import as source | Local text extraction | `supported_local`; local chunks/snippets and reprocess path are available. | Needs Phase 6 citation/highlight parity. |
| Markdown | Import as source | Local text extraction | `supported_local`; lightweight Markdown syntax is stripped into local chunks/snippets. | Needs Phase 6 citation/highlight parity. |
| HTML | Import as source | Local text extraction/sanitization | `partial_local`; script/style/comment markup is stripped and warnings are shown. | Needs richer parser validation before replacement. |
| PPTX | Import as source | Local if feasible, otherwise parser-status fallback | `queued_for_future_api`; file remains visible and recoverable, with no local text chunks yet. | P0 source parity remains blocked until complex parser/device matrix is complete. |
| EPUB | Import as source | Local if feasible, otherwise parser-status fallback | `queued_for_future_api`; file remains visible and recoverable, with no local text chunks yet. | P0 source parity remains blocked until complex parser/device matrix is complete. |
| Images | Image source and chat attachment | Native image storage and model-capability handling | Source Library stores image records as `queued_for_future_api`; chat attachments persist URI/mime/name/sourceId and generation is gated by vision capability. | Real multimodal provider execution and dedicated image-picker device smoke remain pending. |

## Graph Parity Checklist

Updated for `NATIVE-P5-008` Phase 5 preview validation. The replacement release should not be approved until this list is completed or explicitly waived.

| Capability | Phase 5 Native Preview Status | Evidence | Replacement Risk |
|---|---|---|---|
| Pan graph canvas | Implemented in native Compose Canvas transform gestures. | `KnowledgeGraphPanel.NativeGraphCanvas`; `Phase5GraphDeviceTest`; `KnowledgeGraphUiStateTest`. | Needs broader Phase 6 gesture/device matrix. |
| Zoom graph canvas | Implemented in native Compose Canvas transform gestures. | `KnowledgeGraphPanel.NativeGraphCanvas`; `Phase5GraphDeviceTest`; `KnowledgeGraphUiStateTest`. | Needs broader Phase 6 gesture/device matrix. |
| Reset/fit graph view | Basic `Reset view` control is present. | `Phase5GraphDeviceTest` checks control visibility. | Fit-to-selection and clustering controls remain follow-up. |
| Select node | Implemented by canvas tap and non-canvas node list chips. | `KnowledgeGraphUiState.hitTest`; `Phase5GraphDeviceTest`. | Exact large-graph selection QA remains Phase 6. |
| Deselect node | Implemented through clear/close detail and chip reselect behavior. | `KnowledgeGraphPanel.GraphNodeDetail`; `KnowledgeGraphUiState.withSelection`. | Needs final interaction QA. |
| Bottom-sheet node detail | Implemented as native inline detail panel, not a modal bottom sheet. | `KnowledgeGraphPanel.GraphNodeDetail`; `Phase5GraphDeviceTest`. | Sheet-vs-inline parity decision remains open before replacement. |
| Expand/collapse detail where applicable | Not implemented as a separate expand/collapse state. | Current detail panel always renders available details. | Requires Phase 6 parity decision or waiver. |
| Related chat/source jump | Implemented as route handoff labels and navigation targets. | `ContextHubRoute` graph evidence callbacks; `ChatRoute`/`SourcesRoute` evidence target labels; `Phase5GraphDeviceTest`. | Exact target row highlighting and clickable citation parity remain open. |
| Semantic fragment/card review | Partial: memory-backed review cards and NeedsReview actions exist. | `GraphLayoutNode.reviewCards`; `Phase5GraphDeviceTest`. | Full legacy semantic deck/review parity remains open. |
| Node edit/save where currently available | Implemented for session graph node labels and review status actions. | `GraphRepository.updateNode`; `KnowledgeGraphPanel.GraphNodeDetail`; `Phase5GraphDeviceTest`. | Schema richness and broader edit parity remain Phase 6 work. |
| Global graph read-only browsing | Implemented as a native global graph route with read-only review. | `GlobalGraphRoute`; `Phase5GraphDeviceTest`. | True global insights/aggregation remain open. |
| Per-session graph browsing | Implemented as session route display using current graph snapshot. | `ContextHubRoute`; `Phase5GraphDeviceTest`. | True session-scoped graph projection remains open. |
| Empty, loading, large graph, and invalid graph states | Implemented in `KnowledgeGraphUiState`; UI explains native interaction and node-list fallback. | `KnowledgeGraphUiStateTest`; Context Hub state tests. | Needs screenshot/device matrix for large/invalid states. |

## Device Validation Matrix

Minimum matrix for replacement readiness:

- Owner's main Android phone
- One older or lower-end Android physical device, or a lower Android version target if no device is available
- One modern Android emulator

Every phase APK should record:

- APK version/name/package id
- Device or emulator name
- Android version
- What was tested
- Pass/fail result
- Screenshots or notes for failures

2026-08-17 follow-up: `Pixel_8_Pro` AVD (`emulator-5554`) passed the Phase 5 integration suite (11 tests) and the Phase 2 core-loop suite (3 tests). This adds modern-emulator evidence only; main-phone, older-version/small-screen, and manual TalkBack coverage remain open.

## Next ARCH Inputs

ARCH must consume this inventory and map each `P0` and `P1` item to:

- Native module owner
- Data model impact
- Protocol impact
- UI surface
- Verification command or device validation step
- Release gate status
