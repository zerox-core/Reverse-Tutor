# NATIVE-FRONTEND-COMPLETE-001 Requirements

## Problem

The current native Android frontend is visually consistent with the approved theme but behaves like a partial static prototype. Key controls are missing behavior, the home screen has low information density, LLM management is incomplete, navigation is fragmented, and the product does not expose a complete end-to-end workflow.

## Expected Outcome

Deliver a complete native frontend interaction system covering:

- four-page horizontal workspace
- functional session home
- configurable learning dashboard widgets
- multi-provider LLM connections and model switching
- global search
- complete session creation and settings
- attachment-aware concurrent chat
- plans and weekly summaries
- graph overview and full-screen graph
- import/export
- diagnostics, token usage, and APK updates
- complete loading, empty, error, retry, disabled, and persistence states

## Confirmed Product Decisions

- Preserve the current visual theme.
- Treat the current branch as a new architecture refactor. When old branch implementation contracts conflict with the new design, the new design takes precedence.
- Preserve old versions as migration inputs and behavioral references, not as frozen source-level API or Room schema constraints.
- Use a hybrid local-first architecture: Android owns local learning execution, while Python provides optional online enhancement, activities, synchronization, and release metadata.
- Apply entity ownership: local learning data is device-owned, online activities are server-owned, and explicitly shared state uses versioned bidirectional synchronization.
- Use the interaction-shell rebuild approach rather than incremental screen patching.
- Horizontal order is weekly dashboard, session home, global graph, community.
- Default page is session home.
- Community is a static unavailable page.
- Global menu contains only models/settings, import/export, and about/diagnostics.
- Home top bar contains menu, search, and new session.
- Challenge is reachable only through pull-down from the top of home.
- Page indicator is tiny, non-clickable, and stops active prompting after the first swipe.
- Dashboard widgets can be reordered, hidden, and restored, but not freely resized.
- Global search spans sessions, messages, sources, memory, graph, and plans.
- LLM credentials are stored once per connection and reused by multiple models.
- Support OpenAI-compatible, Anthropic-compatible, and Gemini-native protocols.
- Model testing is optional.
- Model capability unknown states do not interrupt chat.
- Do not expose advanced generation parameters.
- Chat supports concurrent independent runs and dependency-aware follow-ups.
- Replies remain bound to their originating user message.
- Voice input and session archive are excluded.
- Import/export never includes API keys.
- Diagnostics include token usage and in-app APK updates.

## Acceptance

Acceptance criteria, migration rules, error states, performance expectations, and device coverage are defined in:

`docs/superpowers/specs/2026-07-11-native-complete-interaction-frontend-design.md`

Three-layer responsibilities, online synchronization ownership, cross-layer contracts, and sub-agent file boundaries are defined in:

`docs/superpowers/specs/2026-07-11-native-three-layer-hybrid-architecture-design.md`

## 2026-07-26 Clarified Interaction Contract (Overrides Earlier Ambiguities)

The following decisions are the implementation contract for the native Android frontend. They refine or supersede earlier four-page/static-prototype assumptions without changing frozen data, protocol, or PWA boundaries.

### Workspace

- Use five circular pages: `本周学习 → 首页 → 全局图谱 → 社区 → 全局设置`.
- Cold start and process-recreated launch enter Home. If the process remains alive after a short background pause, restore the prior page while releasing heavy graph/image resources.
- Community is presentation-only: no post, comment, like, favorite, share, refresh, or feed business interaction.
- Global settings is a real final workspace page, not only a menu sheet.
- Hide spatial indicators at rest; reveal during drag and fade after settling. Inner horizontal controls consume gestures before workspace paging.
- Challenge pull-down uses a high threshold, locks the outer pager until content reaches bottom, and returns Home only after a second upward gesture from the bottom.
- Graph defaults to workspace paging; explicit canvas entry enables pan/zoom and Back exits canvas mode.

### Sessions And Templates

- Session cards support tap, long press, rename, pin/unpin, delete with confirmation and five-second Undo. Export remains a deferred placeholder.
- Avatar display has global and per-session switches; global hidden takes precedence. Card height grows about 12% with stable content reflow.
- Built-in preset edits create personal copies. Favorites are complete configuration snapshots.
- Creating a session clones a snapshot and enters chat with learner role and opening message. Existing sessions never follow later favorite edits; session edits never write back to favorites.
- Drafts save on lifecycle boundaries, live in a 20-item Draft Box, and are promoted/removed from ordinary drafts after successful creation.

### Chat And Sources

- The fixed chat header contains Back, identity/settings, Search, and Graph. Search is a full query page; source management remains in session settings.
- Composer uses a stable 44dp Send/Stop control, local draft persistence, attachment previews, retryable failure states, and safe-area/IME handling.
- Long press supports copy, quote, `记住这条`, locate source, and delete. Rich text, code, formula, image, and invalid-source states remain readable and recoverable.
- Source actions are grouped: reselect, unlink current session, and delete file. Unlink never deletes the managed file; delete shows cross-reference impact and requires explicit confirmation.
- Source replacement creates immutable revisions; existing sessions remain on their old revision.

### Weekly Learning Components

- Fixed top information bar; two-column snapping grid with exactly `1×1`, `2×1`, `1×2`, `2×2` sizes.
- Long press enters edit mode with Android-style restrained shake. Components support drag, remove-to-library, source selection, four-size selection, collision snapping, and local persistence.
- Default components are Today Plan, Weekly Mainline, Weak Points, and wide Token Usage. Token shows a fixed seven-day horizontal window with Input/Output/Cache layers.
- Component library is compact/collapsible with Learning, Data, Session, and Developer-curated Decoration groups.

### Memory And Graph Boundary

- Long-term memory is an underlying layer accessed through the temporary `记住这条` editor; no permanent memory manager is added to normal navigation.
- `关系` is a cross-memory evolution layer, not a peer category. It does not generate or update the learning graph.
- Single-session graph has `当前进度` and `全部路线` layers. Visible graph content is based on conversation-unlocked topics plus coarse stages/obvious source chapters; detailed graph algorithms remain deferred and must first reference the existing PWA graph algorithm.

### Global UX Contract

- Every surface distinguishes loading, empty, offline, failure, and permission-denied states. Recoverable failures retain input and provide local Retry.
- Respect 44–48dp targets, Android locale/24-hour settings, safe areas, reduced-motion preferences, and contrast/color-vision requirements.

### Scope Boundaries

- Do not modify `static/app/index.html`, `mobile/`, frozen native core data/protocol/LLM/SecretStore modules, or backend contracts in this frontend task.
- Do not implement export formats, long-term-memory automation, graph algorithm details, or community business interactions in this phase.
