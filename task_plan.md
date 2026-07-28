# Task Plan: Reverse Tutor Parallel Workstreams

## Goal
Coordinate two isolated workstreams for Reverse Tutor: product experience fixes and memory architecture improvements, then verify and merge them in the main controller workspace without pushing, tagging, or building APK unless requested.

## Current Phase
Phase 12

## Phases

### Phase 1: Baseline Discovery And Checkpoint
- [x] Read user-provided work plan.
- [x] Read project handoff guidance (`AGENTS.md`, `docs/CODEX_HANDOFF.md`).
- [x] Inspect repository structure and dirty working tree.
- [x] Run targeted validation for the existing dirty baseline.
- [x] If validation is acceptable, create a local checkpoint commit for existing dirty changes only.
- **Status:** complete

### Phase 2: Worktree Setup
- [x] Create `F:\xw\reverse-tutor-exp` on branch `work/product-experience`.
- [x] Create `F:\xw\reverse-tutor-memory` on branch `work/memory-architecture`.
- [x] Confirm both worktrees start from the checkpoint commit.
- [x] Run lightweight baseline tests in both worktrees.
- **Status:** complete

### Phase 3: Parallel Delegation
- [x] Start or dispatch the product experience line with the copied product brief.
- [x] Start or dispatch the memory architecture line with the copied memory brief.
- [x] Ensure both lines know APK behavior lives in `static/app/index.html`.
- [x] Monitor both background threads and collect final reports.
- **Status:** complete

### Phase 4: Integration And Verification
- [x] Review each line's changes and test report.
- [x] Merge product line first.
- [x] Merge memory line second.
- [x] Resolve conflicts, especially `static/app/index.html`, `engine.py`, `server.py`, `db.py`, and `tests/`.
- [x] Run `py -m pytest -q --ignore=tests/test_project_homepage.py`.
- **Status:** complete

### Phase 5: Final Handoff
- [x] Report progress using the user's template.
- [x] Do not push, tag, release, or build APK unless the user explicitly asks.
- **Status:** complete

### Phase 6: Read-Only Completion Audit
- [x] Dispatch product worktree audit sub-agent.
- [x] Dispatch memory worktree audit sub-agent.
- [x] Dispatch main integration audit sub-agent.
- [x] Cross-check audit findings against local evidence.
- **Status:** complete

### Phase 7: Audit Finding Remediation
- [x] Add regression coverage for the client default key, preset import sanitization, runtime memory hint item limits, and KG memory-panel visibility.
- [x] Confirm the new tests fail against the audited implementation.
- [x] Remove the client-readable default API key and keep LLM config behavior coherent.
- [x] Replace preset import blocklist sanitization with an allowlist/schema sanitizer.
- [x] Enforce runtime memory hint item limits in both backend and PWA implementations.
- [x] Surface semantic KG nodes in the PWA memory panel.
- [x] Run targeted tests, full regression, and browser smoke for the changed PWA surface.
- [x] Collect read-only review sub-agent result and address any blocking findings.
- **Status:** complete

### Phase 8: Natural Conversation Routing And Input Coalescing
- [x] Add a lightweight `turn_route` classifier for PWA turns.
- [x] Add a `chat_light` LLM path that keeps role/profile/recent context but skips V3 teaching post-processing.
- [x] Preserve `study_full` for explanations, correction, source/image, review, and evidence-bearing learning turns.
- [x] Add a short user-input coalescing window with hard limits to combine rapid consecutive messages without infinite waiting.
- [x] Keep streaming boundaries strict: once output starts, later messages remain queued for the next turn.
- [x] Ensure queued messages recover after refresh and do not get stuck behind post-processing.
- [x] Run targeted tests, related regression, and PWA browser smoke.
- **Status:** complete

### Phase 9: Native Device Layout And Interaction Tuning
- [x] Recover the interrupted device QA context and preserve the user's system-font preference.
- [x] Confirm the connected Huawei HMA-AL00 and reproduce the 360dp clipping issue.
- [x] Make the session home responsive without changing its approved visual hierarchy.
- [x] Make the chat bubbles, composer chips, and composer responsive to narrow widths and IME.
- [x] Make system Back return from custom-session mode to the template page before leaving creation.
- [x] Build, install, and run focused 360dp and original-density device regression.
- [x] Record screenshots, frame timing, verification results, and restore the original display density.
- **Status:** complete

### Phase 10: Mobile Native Integration Contract V1
- [x] Confirm `mobile-native` + FastAPI + Room as the only new integration mainline.
- [x] Audit current Kotlin repository, model, remote API, FastAPI route, and content-contract state.
- [x] Define data ownership and page-to-capability boundaries.
- [x] Write the integration guide, Canonical OpenAPI, online mocks, world-tree schema, and world-tree mock.
- [x] Validate YAML, JSON Schema, online fixtures, and the world-tree fixture.
- **Status:** complete

### Phase 11: Sync And WorldTree Blocker Implementation
- [x] Verify FastAPI canonical sync response and Android legacy-response rejection.
- [x] Add the missing Android `envelopeId` request encoding test and implementation.
- [x] Add WorldTree repository contract, Room entities, DAO, codec integration, and DataModule entry.
- [x] Add V3→V4 migration and exported Room schema 4.
- [x] Add JVM, Android instrumentation, migration, and repository tests.
- [x] Run core JVM tests, lint, instrumentation compilation, Python API tests, and static migration/schema validation.
- [ ] Execute the two new instrumentation tests when an Android device is connected.
- **Status:** complete with device follow-up

### Phase 12: Native Visual Baseline And Real-Device UX Audit
- [x] Record the user's visual feedback as the current native UI acceptance baseline.
- [x] Keep system font support; define explicit typography roles for title, section title, card title, body, metadata, status, and controls.
- [x] Audit字号、行高、字重、字间距、段间距、卡片内边距、分组留白和窄屏换行 behavior on the Huawei device.
- [x] Improve panel hierarchy where surfaces are too flat: visible but restrained borders, stronger elevation/shadows, clear selected/disabled/loading states, and consistent corner treatment.
- [x] Restore the approved challenge-detail sheet background, scrim, hero treatment, top handle, and bottom CTA relationship; treat any visual regression as a P0 issue.
- [ ] Inventory every visible affordance into implemented, stateful, intentionally static, or missing; complete only the interactions represented by the approved design.
- [x] Capture current-device screenshots and UI hierarchy evidence before changing production UI.
- [x] Run focused real-device interaction checks for home, weekly panel, settings, diagnostics, challenge detail, join/retry/close, and key settings actions.
- [x] Summarize findings and prioritize the first implementation slice before editing production code.
- **Status:** in_progress

#### Phase 12 Decisions
- System fonts remain allowed; the product must not force a single bundled font globally.
- Visual consistency comes from explicit type/layout tokens and per-role text metrics, not a blanket font-size increase.
- Formal Figma remains the source of truth. External app screenshots are references for hierarchy, density, icon alignment, and panel depth only.
- Flat panels, weak borders, and missing elevation are visual defects when they reduce grouping or affordance clarity.
- The Huawei HMA-AL00 is connected for this audit; current display override is 720x1496 at 272 dpi and must be recorded with every visual result.

#### Phase 12 Implementation Slice 1
- Added semantic typography, stronger border, and restrained panel/elevation tokens while retaining the system font family and global type multiplier.
- Applied clearer panel depth to home public content, home session cards, weekly cards, settings account/sections, and challenge detail surfaces.
- Restored the challenge detail drag handle and cyan/lavender hero treatment; increased bottom content clearance so the fixed CTA no longer overlaps the third rule card.
- Kept learning mode available and made review/companion modes visibly disabled because those flows remain outside V1.
- Replaced the two settings static toggles with DataStore-backed interactions and confirmed persistence after process restart.
- Replaced both LLM add no-ops with a password-masked editor that saves through `LlmProfileRepository` and refreshes the profile list.
- Debug APK installed on Huawei HMA-AL00. Verification: related JVM tests, app lint, app assemble, one DataStore device test, one responsive flow test, and nine spatial navigation tests all passed.
- Current status remains in progress pending the user's physical-device acceptance pass and the remaining full affordance inventory.

#### Phase 12 Implementation Slice 2: Material And Density Correction
- [x] Remove explicit outer-frame shadows from challenge detail and activity announcement surfaces; use fill, spacing, and subtle separation for an iOS-like material hierarchy.
- [x] Remove card-like shadow/outline treatment from the challenge hero and rule rows while preserving the approved cyan/lavender theme.
- [x] Apply the same cyan/lavender theme background to the activity announcement hero shown in the formal reference.
- [x] Reduce home session-row height, internal padding, and hard double-edge treatment without removing title, status, time, pin, or navigation behavior.
- [x] Add RED/GREEN layout-presentation tests, build a Debug APK, and verify all three surfaces on Huawei HMA-AL00 at 720x1496 / 272 dpi.

Assumptions for Slice 2:
- "Apple-like" means low-noise material surfaces with zero explicit card shadow on the target dialogs; it does not mean copying iOS controls or changing Android navigation behavior.
- Color remains localized to challenge-themed hero content; the whole home/settings background stays unchanged.
- This slice changes presentation and spacing only. Challenge state, activity timing, session data, and persistence contracts are out of scope.

#### Phase 12 Implementation Slice 2 Results
- Added zero-elevation presentation contracts for the challenge detail sheet, hero, rule rows, and fixed footer; removed explicit border/shadow treatment from content cards and kept a soft grouped sheet background.
- Added the same cyan/lavender gradient used by the approved challenge hero to `ActivityAnnouncementDialog`; removed the dialog frame border and explicit shadow, leaving the scrim and clean white material surface.
- Reduced regular/pinned home session rows to 62/70dp, reduced internal horizontal/trailing reserves, removed row elevation, and made the list wrap its content instead of reserving a fixed 322dp block.
- RED/GREEN tests passed for all three presentation contracts.
- `:feature:chat:testDebugUnitTest`, `:app:testDebugUnitTest`, `:app:lintDebug`, and `:app:assembleDebug` passed.
- Huawei real-device screenshots: `mobile-native/qa/phase12-slice2-home.png`, `phase12-slice2-challenge-detail.png`, and `phase12-slice2-ready.png`.
- `FigmaResponsiveDeviceTest` plus 9 `WorkspaceSpatialNavigationDeviceTest` cases passed after the change.
- The existing formal screenshot fixture was attempted but rejected at 272dpi because its fixed 663x1503px fixture exceeds the available 720x1496px display by 7px; no device display override was changed.
- Debug APK was reinstalled and left on the activity announcement dialog for user acceptance testing.

#### Phase 12 Implementation Slice 3: Home Session Height Rebalance
- [x] Increase regular/pinned home session rows from 62/70dp to 68/76dp after physical-device feedback that Slice 2 became too compact.
- [x] Keep the reduced padding, zero elevation, content-wrapping list, and existing text/navigation behavior from Slice 2.
- [x] Run RED/GREEN layout tests, rebuild the Debug APK, reinstall it, and verify the home surface on a physical device without display overrides. RED/GREEN tests and the Debug build passed; the APK was installed on a Huawei Mate 60 and verified at its native 1216x2688 / 520 dpi configuration.

#### Phase 12 Visual Feedback Intake: Preset Detail And Custom World Tree
- [ ] Visual-only pass: strengthen the real-device color contrast so the rendered palette matches the approved design reference instead of appearing washed out.
- [ ] Visual-only pass: increase detail-page typography for normal phone viewing distance, then rebalance line height and spacing so text remains readable without making the identity card oversized.
- [ ] Visual-only pass: reduce the oversized preset identity card while preserving the hierarchy of title, learner profile, schedule, and avatar.
- [ ] Visual-only pass: make the story illustration a real horizontal carousel with visible active/inactive indicators and swipe affordance; do not connect persistence or protocol behavior yet.
- [ ] Visual-only pass: show consistent secondary-edit affordances on preset detail and custom world-tree fields, including edit icon, pressed state, and return/save presentation without backend wiring.
- [ ] Visual-only pass: hide both home spatial indicators at rest, reveal/highlight them during vertical or horizontal dragging, then fade them after interaction settles.
- [ ] Visual-only pass: make the public-interest card visibly interactive with pressed/loading/offline/available states and a clear navigation affordance; defer online-content protocol changes.
- [ ] Visual-only pass: add a stable avatar frame to home session rows without reducing title/summary space below readable limits.
- [ ] Visual-only pass: add long-press feedback and an Apple-like bottom action sheet for rename, pin/unpin, export, and delete; use destructive styling plus confirmation for delete.
- [ ] Visual-only pass: rebalance the chat composer height, send-button size, right inset, clipping, and shadow so the circular action remains centered and fully contained above the navigation safe area.
- [ ] Visual-only pass: give the chat header's world-tree/settings and overflow icons distinct labels, pressed states, and destinations in the interaction specification.
- [ ] Visual-only pass: add a persistent top-left back control to the global graph page and a visible canvas-mode state so users can tell whether gestures will pan the graph or page the workspace.
- [ ] Scope boundary: defer editor data models, persistence, session creation changes, and backend protocol contracts until the complete visual feedback list is approved.
- [ ] Scope boundary: defer repository mutations for rename/pin/delete, export generation/sharing, and public-content backend navigation until the interaction presentation is approved.
- [ ] Deferred interaction fix: after a successful challenge join, close the detail route, navigate to the home session surface, and deliver a one-shot challenge-context event that opens the new challenge-session sheet. The current implementation only navigates to `Sessions`; it cannot reach the sheet's local visibility state.
- [ ] Deferred interaction fix: route the chat header world-tree/settings entry to the active session settings stack. It currently reuses `onOpenContextHub`, while the overflow button has an empty callback.
- [ ] Deferred gesture fix: default global-graph drags to workspace paging; enter graph pan/zoom mode only after an explicit canvas tap, and provide a clear way to exit canvas mode. The current full-size canvas consumes all pointer movement even when the graph is empty, while normal horizontal pager input is disabled on `GlobalGraph`.
- [ ] Deferred gesture fix: raise the home-to-challenge pull/snap threshold and increase the return-to-home capture range so short downward drags rebound instead of opening the challenge page. The current dedicated pull threshold is only 72dp and the vertical pager uses its default fling/snap behavior.

### Phase 13: Frontend Interaction Completion Sub-TODO

#### Confirmed Scope And Acceptance Rules
- [ ] Add the public-interest article page to the complete frontend interaction scope; the home public-interest card must remain openable when the server is offline.
- [ ] Make frontend mutations take effect locally and immediately, including session rename, pin/unpin, delete, draft-field editing, and session-setting changes.
- [ ] Use the confirmed horizontal workspace order: `副屏 → 首页 → 全局图谱 → 社区 → 全局设置`.
- [ ] Make the horizontal workspace circular instead of stopping at the global-settings page.
- [ ] Keep the implemented session long-press actions stateful: rename, pin/unpin, and delete must visibly affect the current local UI; Export remains a clearly deferred placeholder.
- [ ] Treat empty/loading/offline states as designed interactive states rather than plain static text.

#### Challenge Page Gesture State Machine
- [ ] Reset the challenge activity list to its top position every time the user enters the challenge page from home.
- [ ] Do not allow a newly entered challenge page to appear in the middle or at the bottom because of retained list or pager state.
- [ ] Keep the outer home/challenge pager locked while the challenge content has not reached its bottom boundary.
- [ ] Permit returning to the session home only after all challenge content has been shown and the activity list has reached the bottom.
- [ ] Prevent an unfinished gesture from leaving the UI suspended between the challenge page and the session home.
- [ ] Ensure challenge-detail overlays lock the underlying challenge list and the outer vertical pager.

#### Chat Header, Sources, Settings, And Export
- [ ] Audit the duplicated `资料来源` entry in the chat header/overflow menu and the session-settings stack; assign each entry a distinct destination and purpose.
- [ ] Keep existing export entry affordances as visibly deferred placeholders; do not route them into an incomplete range or format flow.
- [x] Defer manual time-range selection, export preview, formats, media handling, and backend/export protocol work to the separate export phase.

#### First-Use Mock Session
- [ ] When the app is used for the first time and no local session exists, create one curated Mock learning session instead of showing an empty session list.
- [ ] Configure the Mock session with a deliberate title, learner identity, opening message, and prepared dialogue lines suitable for demonstrating the product.
- [ ] Keep the Mock session editable, pinnable, exportable, and deletable through the same frontend interactions as a user-created session.

#### Empty Graph Experience
- [ ] Replace the empty graph's plain static text with a real graph-canvas empty state.
- [ ] Use a white canvas background with a low-contrast gray-cyan dot matrix inspired by the supplied Stitch-style reference.
- [ ] Size the drawable canvas area from the number and spread of graph nodes instead of treating the visible viewport as a fixed canvas boundary.
- [ ] Add visible touch/drag feedback to the canvas so the user can tell when graph interaction has been captured.
- [ ] Place an artistic centered text panel on the empty canvas with the exact copy: `当前会话信息过少，再多聊会天吧`.

#### Offline Public-Interest Article
- [ ] Keep the offline public-interest card clickable and navigate to a real fallback article page.
- [ ] Show the confirmed offline message: `当前未加载新公益哦，尽情等待`.
- [ ] Provide a working back interaction from the offline article page to the session home.

#### Pending Clarifications Before Implementation
- [ ] Confirm whether `副屏` is the existing weekly-learning page or a new surface with different content.
- [ ] Confirm whether circular paging is bidirectional and whether `全局设置 → 副屏` uses a continuous page animation.
- [ ] Confirm the exact Mock session title, learner identity, and prepared dialogue, or authorize product-authored copy.
- [ ] Confirm whether deleting the initial Mock session permanently suppresses automatic recreation.
- [x] Confirm the final chat overflow structure and the distinct responsibilities of `资料与引用`, `会话设置`, and `世界树`; detailed destinations are fixed in Round 15.
- [ ] Confirm export presets, custom-date behavior, selectable content categories, and supported formats (`Markdown`, `JSON`, `PDF`).
- [ ] Confirm whether graph drag feedback uses a touch-following ring, grabbed-state scaling, mouse-pointer changes, or a combination.
- [ ] Confirm the final dot-matrix density behavior and whether dots become sparser toward the canvas edges.
- [ ] Confirm whether the offline public-interest page needs a full article skeleton and retry action in addition to the confirmed message.

#### Clarification Round 2: Confirmed Frontend Decisions
- [x] Confirm `副屏` as the existing weekly-learning page.
- [ ] Enrich the weekly-learning page selectively: remove repeated challenge/progress panels, keep only a useful default subset of components, and expose the remaining components through long-press add/remove interactions.
- [ ] Add a local emoji/decorative asset library for weekly-page background and ornamental content; decoration remains a frontend-local presentation feature.
- [x] Confirm bidirectional circular paging between `副屏` and `全局设置`.
- [x] Confirm the challenge-entry reset and bottom-boundary gesture rules from the first clarification round.
- [x] Seed the first-use Mock session with the title `欢迎来到反转家教`, learner role `小六子`, and opening line `老师老师，第一节课我来教你，以后你就要好好来教我啦。`.
- [ ] Make `资料与引用` a fast query surface for locating source material and message references, not a duplicate configuration page; configuration remains in the session settings tree.
- [ ] Make every visible settings-tree affordance interactive, including all basic information shown in preset detail windows.
- [ ] Make the preset detail favorite label a real local toggle with a visible lit/unlit state.
- [ ] Add a top-right draft-box control to the custom world-tree page; show auto-saved draft configurations in a popup without creating a separate draft page.
- [ ] Add a randomize action that locally recombines world-tree content for preview/editing.
- [ ] Make `新增自定义栏目` create a real local editable column and expose its edit/delete state.
- [ ] Add quick-option tags below each editable field; support built-in tags, user-created tags, and persistent local reuse of custom tags.
- [x] Use touch/drag light feedback for graph interaction without a visible touch ring.
- [x] Use evenly distributed gray-cyan dots on the white graph background; do not apply edge-density gradients.
- [x] Keep the offline public-interest article as a complete article-shaped page, including its structural sections and back interaction.

#### Export Format Recommendation Pending Confirmation
- [x] Superseded by Round 4: do not design or implement export formats in the current frontend-completion scope.
- **Status:** deferred to a separate export clarification phase.

#### Clarification Round 3: Weekly Canvas, Query, And Rendered Export
- [x] Keep the weekly page's top information bar fixed and outside component editing.
- [ ] Convert every remaining weekly-page item into a movable component that supports reordering, removal, addition, and constrained resizing.
- [ ] Enter weekly edit mode by long-pressing any component.
- [ ] Show a bottom sliding component tray in edit mode containing components not currently placed on the page.
- [ ] Allow users to drag components directly from the bottom tray into the weekly page.
- [ ] Add automatic snapping for component position and size so components cannot remain between valid grid slots.
- [ ] Support exactly four component size variants: `正常`, `宽增长`, `长增长`, and `整体放大型`; do not support arbitrary free resizing.
- [ ] Apply the same four size variants to framed cards and borderless text/decorative components.
- [ ] Treat the screenshot layout as the reference composition of two normal components plus one wide component.
- [ ] Break weekly-learning details into independently placeable components, including `主线`, `薄弱点`, and `未完成问题` rather than hard-coding all information into one panel.
- [ ] When a data-bearing component is first added, immediately open a source-selection popup.
- [ ] Let each component use either the global aggregate source or one/more individually selected sessions.
- [ ] Support multi-select session sources and save the source choice when the user taps outside the popup.
- [ ] Allow reopening component settings by long press so source sessions can be selected again.
- [ ] Keep the emoji/decorative library as a developer-owned local asset library; do not expose it as an in-app user picker.
- [ ] Permit generated image assets to be added to the developer decoration library and referenced by weekly-page presentation components.
- [x] Confirm the draft-box popup supports restore, delete, saved-time display, and automatic draft saving.
- [x] Confirm random world-tree composition changes only local preview state and supports undo before overwriting a saved draft.
- [ ] Categorize reusable quick tags by semantic purpose; do not reuse one global tag with different meanings across unrelated fields.
- [ ] Implement `资料与引用` as a categorized query surface with `会话`, `图谱`, and `资料` result panels.
- [ ] When a query has multiple matches, show a categorized selection panel before navigating to the selected message, graph node, or source item.
- [ ] Replace raw Markdown/JSON as the primary user export with a rendered, browse-friendly document.
- [ ] Include chat records, inline images, learner/teacher identity, timestamps, and selected time range in the rendered export.
- [ ] Retain structured data only as an optional secondary export or internal recovery format, not the primary browsing experience.

#### Clarification Round 3: Remaining Decisions
- [ ] Confirm whether the four component sizes map to grid spans `1×1`, `2×1`, `1×2`, and `2×2`.
- [ ] Confirm the exact weekly component catalog and which components are visible by default.
- [ ] Confirm whether a newly added data component defaults to global aggregation or always requires explicit source selection.
- [ ] Resolve the long-press gesture split between entering edit mode, dragging a component, and reopening component settings.
- [ ] Confirm the categories and ownership scope used for persistent custom quick tags.
- [x] Deferred by Round 4: choose the rendered export carrier in a later export-specific phase.
- [x] Deferred by Round 4: decide image embedding, file-size handling, and document navigation later.

#### Clarification Round 4: Confirmed Weekly And Mock Behavior
- [x] Map the four weekly component sizes to fixed two-column grid spans: `正常=1×1`, `宽增长=2×1`, `长增长=1×2`, and `整体放大型=2×2`.
- [x] Use the proposed weekly default subset and add a `Token 用量` panel as another default `宽增长` component.
- [ ] Add a compact daily-consumption visualization to the Token component.
- [ ] Present Token usage as a layered tree structure with three visually distinct branches: `输出`, `输入`, and `缓存`.
- [x] Confirm the edit-mode gesture contract: long press enters edit mode, drag repositions, tap opens settings, corner controls remove/resize, and tapping empty space saves and exits.
- [ ] Add Android-style component shake feedback while weekly edit mode is active; allow implementation judgment on whether the whole page receives a restrained scale change.
- [x] Allow a data-bearing weekly component to remain placed with no session source selected.
- [ ] Show a deliberate empty-source state inside an unconfigured component instead of silently substituting unrelated global data.
- [x] Remove rendered export design and implementation from the current frontend-completion scope; revisit the entire export module in a later clarification phase.
- [x] Keep the first-use Mock session as a real persistent local placeholder used by both design previews and normal user flows.
- [ ] Preserve Mock messages and identity when the user leaves the chat and re-enters; do not rebuild transient Mock content from screen-local state.
- [x] Categorize quick tags primarily by color and semantic group, with minimal explanatory copy and orderly alignment.
- [ ] Keep tag layouts compact and visually consistent across all editable preset/custom-world-tree fields.

#### Round 4 Scope Update
- [ ] Remove HTML/PDF/JSON export carrier decisions, image embedding, file-size policy, and export table-of-contents work from the active frontend implementation checklist.
- [ ] Keep only existing export entry affordances as deferred placeholders until the export module is clarified separately.

#### Clarification Round 5: Confirmed Weekly Component Mechanics
- [x] Use the current `待解决问题` component dimensions as the canonical `正常` size reference.
- [ ] Derive the other three component sizes by proportional width/height scaling from that reference instead of inventing unrelated dimensions.
- [x] Confirm the default weekly layout includes `今日计划`, `本周主线`, `薄弱点`, and the wide `Token 用量` component, with other components stored in the bottom library.
- [x] Show a fixed seven-day Token chart window by default.
- [ ] Allow horizontal chronological scrolling into earlier Token dates while preserving the fixed seven-day viewport.
- [ ] Do not provide an all-time overview, date jump control, calendar picker, or expanded full-history chart in the Token component.
- [ ] Expand all three Token levels inside the component rather than opening another page or bottom sheet.
- [ ] Use distinct colors for `总消耗`, `输入`, `输出`, and `缓存`, and add a compact information badge for metric meaning.
- [x] Confirm an unconfigured data component opens its source-selection popup when tapped.
- [x] Confirm Mock persistence rules: create only for first-use empty state, persist through navigation/restart, allow normal chat continuation, and never recreate after explicit deletion.
- [x] Defer any Mock conversation-generation algorithm; the current frontend scope only seeds and preserves fixed Mock identity/messages.
- [x] Confirm weekly edit-mode exits: tap empty space to save, system Back to discard the active layout edit, and horizontal page navigation to auto-save before leaving.
- [x] Confirm the component library categories: `学习`, `数据`, `会话`, and `装饰`.
- [ ] Present component categories as compact collapsible groups in a shallow bottom drawer inspired by the Android app drawer/bottom-page pattern.
- [ ] Let users select a category, reveal its detailed component choices, and drag a selected component directly from the drawer into a valid snapped page slot.
- [x] Confirm developer asset directories for decorations, emojis, and backgrounds; generated assets are curated locally before conversion to Android resources.

#### Clarification Round 5: Developer Asset Library Paths
- [ ] Create and document `design-assets/decorations/`.
- [ ] Create and document `design-assets/emojis/`.
- [ ] Create and document `design-assets/backgrounds/`.
- [ ] Keep runtime UI independent of image-generation services; the app only consumes curated packaged assets.

#### Clarification Round 6: Confirmed Preset And Custom World-Tree Editing
- [x] Make every listed preset-detail field editable: name, learner identity/avatar/profile, goal/tags, schedule, scope/modules/counts, story stages/text/illustrations, connected sources, and custom supplemental fields.
- [x] Open a dedicated editor page or popup from each information card; do not place raw text inputs directly inside compact detail cards.
- [ ] Add categorized quick-option tags to every dedicated preset/custom editor, matching the normal field-editing experience.
- [ ] Refresh the detail card immediately after an editor save.
- [x] Apply favorite/bookmark behavior to custom world-tree configurations.
- [ ] Persist a favorited custom configuration as a complete snapshot, including every field, custom column, story/multimodal reference, and connected source/material selection.
- [x] Confirm a 20-item draft-box capacity with restore, rename, delete, saved-time display, copy-as-new, and oldest-unfavorited eviction.
- [x] Do not auto-save on an input debounce or after a fixed typing pause.
- [ ] Auto-save only on meaningful lifecycle/navigation boundaries: leaving an editor, switching fields/configurations, leaving the custom page, and application backgrounding.
- [x] Limit current random composition to local combinations of learner role, learning goal, and learning plan.
- [ ] Keep story text, story structure, and multimodal images completely outside the current random-composition pool.
- [ ] Generate a local random preview first and preserve the previously confirmed apply/undo interaction before replacing current role/goal/plan values.
- [x] Confirm quick-tag interactions: built-in tags are immutable, user tags can be added/renamed/deleted, semantic colors stay stable, tags can be reordered, and tapping toggles selection.

#### Clarification Round 6: Favorite Scope To Reconfirm
- [ ] Confirm whether built-in preset favorites remain supported in addition to complete custom-configuration favorites, or whether favorite/bookmark is exclusively a custom-world-tree feature.

#### Clarification Round 6: Custom Column Control Still Open
- [ ] Decide whether every new custom column uses one simple text-and-tags editor, or whether users choose an input control such as text, number, date, switch, or source reference.

#### Clarification Round 7: Confirmed Custom Fields, Favorites, And Tags
- [x] Use one consistent custom-column editor in the current frontend: column name, text content, and categorized quick tags.
- [ ] Preserve custom-column semantics so a later long-term-memory/automation layer can interpret them after session creation.
- [ ] Defer algorithmic automation such as background notifications and proactive session messages to a later memory/backend phase.
- [x] Support favorites for both built-in presets and complete custom world-tree configurations.
- [ ] Keep built-in preset favorite behavior extensible because preset content will receive a separate detailed optimization phase.
- [x] Allow a favorited custom configuration to be edited again rather than treating favorites as immutable snapshots.
- [x] Remove illustration controls from the current custom-world-tree panel; custom story content is manual text only.
- [x] Defer story randomization, story-generation constraints, and any multimodal illustration algorithm while retaining local role/goal/plan randomization.
- [x] Show up to eight tags per semantic type in the collapsed state without imposing a total custom-tag count limit.
- [ ] Give every tag type a fixed-height collapsed viewport with horizontal scrolling.
- [ ] Add an expand/collapse control above each tag-type section; expanded state shows all tags in that type.
- [ ] Allow long-press drag reordering inside a tag type.
- [ ] Allow dragging custom tags between semantic types to reclassify them.
- [ ] Allow users to create new tag types on the phone.
- [ ] When one or more tag sections are expanded, system Back collapses all expanded tag sections before navigating away.
- [x] Do not add a long-press shortcut for creating a new draft from the draft-box icon.
- [ ] Treat any edited but unpublished custom configuration as a draft automatically.

#### Clarification Round 7: Recommended Source Reference Model
- [ ] Copy imported/uploaded files into app-managed local storage and assign each source a stable Source ID; do not depend solely on the external original file path.
- [ ] Store Source IDs and selection relationships inside favorite/custom configurations instead of duplicating source bytes per favorite.
- [x] Bind every existing session/template reference to an immutable Source revision: replacing source content creates a new revision, while old sessions continue using the old revision without an upgrade selector or automatic migration.
- [ ] Do not silently add/remove sources in a favorite when a live session's source selection changes.
- [ ] Never copy a live session's source selection or settings back into its favorite template automatically; any future explicit template-update action must show a difference preview and require confirmation.
- [ ] Keep favorite configurations independently editable and leave favorite import/export for the deferred export module.

#### Clarification Round 7: Remaining Decisions
- [x] Confirm the recommended app-managed Source ID model and explicit favorite-update behavior.
- [x] Replace ambiguous `发布` semantics with session creation: a working configuration is a draft until creation succeeds, then becomes non-draft.
- [x] Keep the random button active for local role/goal/plan composition only.
- [x] Confirm normal exit/navigation auto-saves without a save-confirmation dialog.
- [ ] Require confirmation only for destructive or replacement operations: deleting a draft, deleting a custom column, and overwriting a favorite configuration.

#### Clarification Round 8: Source, Draft Promotion, And Tag Grouping
- [x] Adopt the app-managed source-copy model with stable Source IDs and explicit favorite-configuration updates.
- [x] Do not retain a custom configuration as a draft after it successfully creates a session.
- [ ] Promote the active draft to a non-draft configuration when session creation succeeds and remove it from the ordinary draft-box list.
- [ ] Preserve any independently favorited configuration according to favorite rules even when its working draft is promoted during session creation.
- [x] Do not require every custom tag to belong to a type/group; ungrouped tags are valid.
- [ ] Use a visual color palette control for tag-group color selection instead of requiring users to enter or describe a color.
- [ ] When an ungrouped tag is dragged onto another ungrouped tag, open a create-group popup using those two tags as the initial members.
- [x] Confirm system Back collapses all expanded tag groups before leaving the editor.
- [x] Keep only long-term-memory-compatible field structure in the current frontend; do not implement notification/message automation controls yet.

#### Clarification Round 9: Favorite Isolation, Query, And Chat Actions
- [x] Preserve an independently favorited configuration after it creates a session; creating a session removes only the active working draft from the ordinary draft box.
- [x] Use a fixed palette of 12 low-saturation color swatches for tag-group colors, automatically preferring an unused color and showing no color-name input.
- [x] After session creation succeeds, navigate directly into the created chat and immediately show the selected learner role plus the configured opening message.
- [x] Create every session from a complete configuration snapshot of the selected built-in/custom favorite template, including fields, settings, custom columns, and Source ID selections.
- [x] Enforce bidirectional isolation after creation: later favorite-template edits never mutate existing sessions, and later session setting/source changes never mutate the favorite template.
- [ ] Permit template replacement from an existing session only through a future explicit action with a complete difference preview and destructive-replacement confirmation; no background or implicit synchronization is allowed.
- [x] Implement `资料与引用` as a full query page rather than a configuration duplicate, with top-level `会话`, `图谱`, and `资料` categories.
- [ ] Show result summaries and source identity; when several results match, require selection before navigating to and highlighting the original message, graph node, or source item.
- [x] Add the confirmed message long-press actions: `复制`, `引用回复`, `保存为长期记忆`, `定位关联资料`, and `删除消息`.
- [x] Keep export out of the message long-press menu and defer all export interaction design.
- [x] Add chat attachment actions for `选择图片`, `选择应用内资料`, `拍照`, and `查看本会话已关联资料`.
- [ ] Show a pre-send attachment strip with thumbnails/type indicators, upload/readiness state, and per-item removal.
- [x] Clarify Source file replacement/version behavior, long-term-memory save confirmation, derived-data deletion behavior, attachment limits, and permission/error/empty states before Phase 13 implementation begins.

#### Clarification Round 10: Source Revisions, Memory, Attachments, And Query Recovery
- [x] When source content is replaced, create a new immutable revision; existing chat windows remain bound to their old revision by default.
- [x] Do not expose a source-version choice or silently upgrade an existing chat window; a new revision is used only when a template/session source is explicitly edited later.
- [x] Open a confirmation editor before `保存为长期记忆`; let the user revise the memory text, category, and associated source before committing it.
- [x] When deleting a message that produced memory or graph data, show the affected derived items and offer `仅删除消息` or `同时删除关联内容`.
- [x] Default message deletion to `仅删除消息`; retained derived items show that their original message has been deleted instead of silently losing provenance.
- [x] Limit one outgoing chat message to nine attachments and limit each selected image to 20 MB.
- [x] Treat app-managed source attachments as references rather than duplicate file copies, and exclude their original byte size from the image limit calculation.
- [x] Keep failed attachments visible with `重试` and `移除`, and disable Send until every failed item is retried successfully or removed.
- [x] On camera-permission denial, remain in the chat, explain the missing permission, and offer `重新授权` and `前往系统设置`; cancellation creates no draft attachment.
- [x] Provide distinct empty states for no query results and invalid/missing source files.
- [x] Let users reselect a missing source file without automatically deleting its Source record or silently changing unrelated references.
- [x] Restore the prior query, selected category, result scroll position, and filters when returning from a located message/node/source.
- [x] Briefly highlight the located target after navigation without persistent flashing.

#### Clarification Round 11: Memory Evolution, Reply, Draft, And Search Scope
- [x] Provide seven recognized long-term-memory categories: `身份`, `事实`, `偏好`, `目标`, `计划`, `约束`, and `待跟进`.
- [x] Model `关系` as an independent layer across all memory categories, not as an eighth peer category; preserve how people, facts, goals, plans, and events evolve so a later reasoning layer can reconstruct development rather than seeing only the latest value.
- [x] Require memory text and category in the pre-save editor; allow multiple associated messages/sources and expose the existing categorized quick-tag system.
- [x] Do not merge or overwrite a similar memory record.
- [x] Preserve every similar, changed, or conflicting memory as an independent evolution record and connect it through the relationship/evolution structure.
- [ ] Clarify the user-facing relationship types, automatic/manual linking behavior, and edit/delete effects before implementing the memory editor.
- [x] Limit each outgoing message to one quoted source message.
- [ ] Show the quoted-message summary above the composer, allow removal and tap-to-locate, and display `原消息已删除` if the source message no longer exists.
- [x] Persist text and unsent attachments as a separate local composer draft for each session.
- [ ] Restore the composer draft after page changes, backgrounding, and application restart; clear it only after successful send or explicit user clearing.
- [x] Show a five-second Undo affordance after message deletion.
- [ ] When deletion included memory/graph derivatives, Undo restores the message and every deleted relationship atomically.
- [x] Default `资料与引用` queries to the current session and provide an `全部会话` scope toggle without clearing the search text.
- [x] Preserve attachment order in the sent message, allow long-press drag reordering in the preview strip, and support mixed image/source-reference attachments.

#### Clarification Round 12: Relationship Layer And User-Facing Graph Boundary
- [x] Define the cross-memory relationship types as `关联`, `延续`, `更新`, `因果`, `依赖`, and `冲突`.
- [x] Allow one memory record to have multiple typed relationships to prior records.
- [x] Do not perform automatic relationship inference in the current frontend phase.
- [x] When a user explicitly saves a memory, list recent records from the same category so the user can select zero, one, or multiple predecessors and assign relation types; no predecessor creates a new chain root.
- [x] Preserve relationship evolution chronologically; a future inspectable view uses a timeline with the latest record emphasized and older records collapsible.
- [x] Distinguish `修正记录` from `新增演进`: correction fixes erroneous wording/data without claiming a real-world change, while evolution preserves the prior record and creates a related node.
- [x] When deleting a relationship node, warn about affected edges, delete only that node and its edges by default, preserve other memory records, and provide five-second Undo.
- [x] Treat long-term memory as an underlying algorithm/data layer rather than a normal user-facing management destination.
- [x] Do not add a permanent `会话设置 → 长期记忆` manager in the current frontend scope.
- [x] Do not render every long-term-memory record in either the global graph or a single-session graph.
- [x] Keep global and single-session graphs focused on user-readable learning-path visualization; defer exact visible node types, relationship simplification, and curation rules to a graph-algorithm phase.
- [x] Reconcile the memory action with the hidden data layer by using the user-facing label `记住这条`, a temporary confirmation editor, and no permanent memory-management destination.

#### Clarification Round 13: Two-Layer Learning Graph And Algorithm Boundary
- [x] Keep `记住这条` in the message menu while hiding the internal long-term-memory terminology.
- [x] In the temporary memory editor, show text/category first and place predecessor/relationship controls in a collapsed `关联上下文` section.
- [x] After saving, expose only an `已记住` state on the source message; reopening it allows source-scoped correction, evolution, and deletion without adding a global memory manager.
- [x] Give each single-session graph two user-facing layers: `当前进度` and `全部路线`.
- [x] In `当前进度`, emphasize the user's actually unlocked topics and the concise relationships needed to understand present learning progress.
- [x] In `全部路线`, show completed and incomplete routes together.
- [x] Build incomplete-route scaffolding only from large learning stages and clearly structured chapters/sections in connected source materials; do not pretend that every future node is algorithmically known.
- [x] Unlock real topic nodes through conversation progress and preserve a restrained amount of evolution relationships; avoid verbose relationship labels and dense explanatory text.
- [x] Aggregate same-subject paths across sessions in the global graph while retaining the originating-session marker for traceability.
- [x] Keep the long-term-memory algorithm independent from graph generation and updates; raw or summarized long-term memory must not become the graph's source of truth.
- [x] Confirm graph gestures: tap a node for details, long press a node to drag, double tap empty canvas for fit-to-view, pinch to zoom, and retain Back plus `回到中心` controls.
- [ ] Defer exact graph node taxonomy, route-generation rules, evolution-edge selection, node-detail actions, and deletion semantics to the dedicated graph-algorithm plan below.

#### Clarification Round 14: Session Home Cards And Long-Press Actions
- [x] Implement `重命名`, `置顶/取消置顶`, and `删除` in the session long-press sheet; retain Export only as a deferred placeholder.
- [x] Prefill the current title in the rename dialog, require 1–30 non-whitespace characters, and allow duplicate titles across sessions.
- [x] Place pinned sessions above unpinned sessions and order them by most recent pin time; do not cap or manually reorder pinned sessions in the current scope.
- [x] Before deletion, show the session title and affected local data; after deletion, provide five-second Undo.
- [x] Delete only the selected session's messages, settings, composer draft, and session graph state; never delete shared managed Source files or favorite templates.
- [x] Permanently suppress automatic Mock recreation after explicit Mock-session deletion; Undo restores the complete Mock session and suppression state atomically.
- [x] On session-card long press, apply restrained target-card scale feedback plus one haptic response while keeping the action sheet visually stable.
- [x] Default to the learner-role avatar; when absent, generate a low-saturation initial avatar from the session title.
- [x] Allow avatar display to be disabled per session; when disabled, reflow card content and leave no empty avatar slot.
- [x] Keep avatar edits and visibility settings inside the session snapshot so they never mutate the favorite template.
- [x] Increase the current session-card height by approximately 12 percent, within the confirmed 10–15 percent range, while reducing ineffective internal whitespace.
- [x] Show avatar/optional reflow, title, learner role, latest-message summary, time, and pin state in a stable card layout.
- [x] Do not add horizontal swipe actions to session cards because that gesture belongs to workspace paging; retain tap-to-open and long-press actions.

#### Clarification Round 15: Session Settings Navigation And Persistence
- [x] Retain both avatar-visibility controls: a global switch hides avatars across all session cards, while a per-session switch hides only that session; the global hidden state takes precedence.
- [x] Make the chat header avatar/title a working entry to `会话设置` and restore the prior chat scroll position when returning.
- [x] Structure session settings as `基本资料`, `学习目标与计划`, `对话策略`, `资料管理`, `世界树配置`, and `危险操作`.
- [x] Keep `资料与引用` exclusively for searching, filtering, locating, and returning to conversation/graph/source results.
- [x] Keep `资料管理` exclusively for adding, removing, reselecting, inspecting file state, and changing Source ID selections for the current session.
- [x] Save switches, segmented controls, and tag selections immediately.
- [x] Save text fields on Done, field/section change, Back/navigation, or application backgrounding; do not persist on every keystroke.
- [x] Before saving changes to role, goal, personality, or interaction habits, show a concise difference summary.
- [x] Apply those identity/profile changes only to future conversation behavior; never rewrite history, favorite templates, or existing source relationships.
- [x] Edit only the current session's independent world-tree snapshot from `世界树配置`; expose every confirmed field and custom column without automatic favorite-template updates.
- [x] Keep `删除当前会话` in `危险操作` and reuse the same confirmation plus five-second Undo behavior as the home long-press action.
- [x] Do not add `恢复默认设置` or `清空聊天记录` in the current frontend scope.

#### Clarification Round 16: Settings Subpages And Source Action Grouping
- [x] Use an iOS-style grouped settings index with compact summaries and dedicated subpages; do not place every field in one long form, and keep the subpage header fixed.
- [x] Put session title, learner display name, learner role, avatar/visibility, personality, and interaction habits in `基本资料`.
- [x] Do not edit the already-sent opening message from session settings because it is part of immutable chat history.
- [x] Put primary goal, deadline, learning scope, modules, stage milestones, weekly plan, and current state in `学习目标与计划`, with categorized quick tags in each editor.
- [x] Keep six conversation-strategy controls: feedback intensity, probing intensity, scaffolding intensity, correction persistence, review frequency, and speaking tone.
- [x] Render the first three strategy values as five-step sliders and the latter three as segmented controls.
- [x] If the app backgrounds while unconfirmed core-profile changes are open, preserve only the temporary form state and restore the difference-confirmation step on return; do not silently apply behavior changes.
- [x] When a missing/invalid source is reselected, bind only the explicitly edited session to the new immutable Source revision; all other sessions and favorites retain their old revision.
- [x] Reuse the creation flow's field editors and quick tags inside an active session's world-tree settings, but omit draft-box, favorite, and random-composition controls.
- [x] Place `取消本会话引用` and `删除资料文件` together in one source-detail action group near the active source context rather than scattering these related actions across pages.
- [x] Visually and semantically distinguish unlink from file deletion, show reference impact before deletion, and apply the confirmed cross-reference warning plus Undo behavior below.

#### Clarification Round 17: Source Detail, Deletion Impact, And Recovery
- [x] Show source name, type, read/parse state, current-session reference state, total referencing sessions/templates, and last-used time on each source card.
- [x] Open a source-detail page with content preview above one grouped action area containing `重新选择文件`, `取消本会话引用`, and red-styled `删除资料文件`.
- [x] If other sessions or favorites reference the file, show the complete impact list and require `确认使这些引用失效` before enabling deletion; never cascade silently.
- [x] Apply current-session unlink immediately and provide five-second Undo without deleting the managed file, Source record, or any other reference.
- [x] After confirmed file deletion, retain bytes during the five-second Undo window; on timeout, finalize deletion and mark every affected reference invalid.
- [x] Store a source display-name edit as a per-session alias only; do not rename the managed library file or alter other sessions/favorites.
- [x] Preview PDF, supported documents, and images when possible; otherwise show file metadata, parse state, and `使用其他应用打开` instead of a blank viewer.
- [x] Filter source lists by `全部`, `正常`, `处理中`, and `失效`, support name search, and sort by most recent use by default.

#### Clarification Round 18: Chat Composer, Send State, And Scroll Stability
- [x] Use a `+` icon beside the composer to open a bottom action sheet with `选择图片`, `选择应用内资料`, `拍照`, and `查看本会话资料`.
- [x] Auto-grow the composer from one to five lines, then scroll internally; mobile keyboard Enter inserts a newline and only the Send control submits.
- [x] Render Send as a stable 44-by-44 circular up-arrow icon button so state changes never resize or shift the composer.
- [x] Disable/dim Send when text and attachments are empty or when an attachment has an unresolved failure; enable it for valid text or ready attachments.
- [x] Insert the outgoing message into the stream immediately with local delivery state and reserve a stable assistant-generation placeholder to prevent layout jumps.
- [x] During assistant generation, turn the Send control into a Stop icon; allow continued draft editing but prevent submission until generation finishes or stops.
- [x] Preserve partial assistant output after Stop, label it `已停止`, and provide `继续生成` without deleting the partial response.
- [x] On send/generation failure, show a concise inline reason and `重试`; retry the same logical message rather than adding a duplicate bubble.
- [x] Auto-follow new content only when the reader is already near the bottom.
- [x] While the reader is reviewing history, retain scroll position and show an `有新消息` floating control that returns to the bottom on tap.

#### Clarification Round 19: Message Layout, Actions, Media, And Rich Content
- [x] Use approximately 16sp body text with about 1.5 line height; align teacher/user messages right and learner-role messages left without shrinking text for dense content.
- [x] When avatars are disabled, reflow left-side message content without reserving an empty avatar column.
- [x] Insert lightweight date separators and reveal exact time plus delivery state temporarily when a message is tapped instead of showing metadata under every bubble.
- [x] Keep the stable long-press action sheet with `复制`, `引用回复`, `记住这条`, `定位关联资料`, and `删除消息`; apply only restrained scale feedback to the target message.
- [x] Delete only the selected message, not adjacent question/answer messages, while preserving the confirmed reference/memory/graph impact warning.
- [x] Copy message body as plain text; give code and formula blocks their own Copy controls and exclude image/source attachments from clipboard text.
- [x] Open images in a full-screen viewer with pinch zoom, pan, save-to-device, and system Share; on load failure show Retry without reserving a large blank region.
- [x] Render app-managed sources as compact attachment cards that open Source detail; preserve the original display name for invalid references and expose `资料已失效` plus reselect.
- [x] Render Markdown headings, lists, quotes, tables, code, and math with dedicated readable styles; allow wide tables/code to scroll horizontally without reducing body font size.

#### Clarification Round 20: Chat Header, Destinations, And Back Stack
- [x] Keep only a Back arrow on the left side of the chat header; it returns to the session home.
- [x] Show avatar, session title, and learner role in the center identity region and make the entire region open session settings.
- [x] Put Search and Graph icon buttons on the right; Search opens `资料与引用` and Graph opens the current-session graph, with accessible labels/tooltips instead of a text `脉络` button.
- [x] Keep world-tree configuration inside session settings and do not duplicate it in the chat header.
- [x] Remove technical `mock/model-name` status from the chat header and expose it in global settings; show only concise in-chat status when the model is offline or a call fails.
- [x] Give the header stable dimensions; ellipsize long titles to one line and reveal the full title in session settings.
- [x] Restore message scroll, composer draft, quote, and attachment state after returning from Search, Graph, or Settings.
- [x] Apply Back in this order: close active dialog/action sheet, leave the current child page, dismiss the keyboard, then return from chat to session home.
- [x] Keep the header fixed during message scrolling and add only a restrained divider shadow; do not collapse or animate the title size.

#### Clarification Round 21: Weekly Component Catalog, Grid, And Persistence
- [x] Keep a fixed weekly header outside edit mode showing `本周学习`, the current week range, active data scope, and compact overall progress.
- [x] Include `今日计划`, `本周主线`, `薄弱点`, `待解决问题`, `未完成任务`, `已完成里程碑`, `Token 用量`, `学习时长`, `完成率`, `最近会话`, `置顶会话`, `当前挑战`, and curated decoration components in the library.
- [x] Default only `今日计划`, `本周主线`, `薄弱点`, and wide `Token 用量` onto the page; keep all other components in the bottom library.
- [x] Use a two-column snapping grid with only the confirmed `1×1`, `2×1`, `1×2`, and `2×2` spans.
- [x] Put Remove at the component's top-right edit control and open a four-option size selector from the bottom-right resize control; do not support free pixel resizing.
- [x] Resolve drag/resize collisions by shifting affected components into the nearest valid subsequent slots, show a live placement placeholder, and use boundary feedback plus restrained haptics for invalid positions.
- [x] Removing a component returns it to the library without deleting learning data or its previous source selection; re-adding restores that selection.
- [x] Limit `装饰` to reviewed packaged backgrounds, dividers, headings, and images from the developer asset library; allow placement/constrained sizing but no runtime generation or user emoji picker.
- [x] Persist component order, size, source, and visibility locally across restart and workspace paging.
- [x] Reflow the same two-column layout for different device widths and ensure no component can remain outside the visible page bounds.

#### Clarification Round 22: Weekly Component Content And Destinations
- [x] In normal mode, tapping a component opens its content destination; in edit mode, tapping opens layout/source settings and long press/drag repositions it.
- [x] Make `今日计划` a real checklist with add, edit, delete, completion toggle, and long-press ordering; reflect changes immediately in the component summary.
- [x] Open dedicated item lists from `本周主线`, `薄弱点`, and `待解决问题`; show source session plus update time and allow navigation to the related session, graph, or source.
- [x] Use one underlying task model for `未完成任务` and `已完成里程碑`; completion/restoration moves the item between views without duplicating it.
- [x] Open lightweight trend dialogs from `学习时长` and `完成率`.
- [x] Keep Token history inside its fixed seven-day component viewport with chronological horizontal scrolling and no all-history destination.
- [x] Open chats directly from `最近会话` and `置顶会话`; long press uses the same unified session action sheet.
- [x] Open the challenge activity page from `当前挑战` and enforce the confirmed top-reset, bottom-boundary, and outer-pager lock state machine.
- [x] Let the weekly header choose global aggregate or multiple sessions as a default data scope for newly added/unconfigured components only.
- [x] Never overwrite a component's explicitly configured source selection when the weekly default scope changes.

#### Clarification Round 23: New Session Hub, Presets, And Creation Lifecycle
- [x] Open a new-session hub from Home `+` with segmented `内置预设`, `收藏`, and `自定义` views; keep Draft Box as a top-right popup in Custom rather than a fourth page.
- [x] Show preset name, learner role, primary goal, plan summary, source count, and favorite state on compact preset cards.
- [x] Open full preset detail on card tap and require an explicit `使用此预设` action before entering creation confirmation.
- [x] Keep packaged built-in presets non-destructive; editing one creates an editable personal copy while preserving the original built-in preset.
- [x] Edit only the favorite template when updating a favorite and leave all existing sessions unchanged; new sessions clone the favorite's current snapshot.
- [x] Organize Custom into `基本资料`, `目标与计划`, `对话策略`, `世界树`, `资料`, and `自定义栏目`, with a compact completion summary rather than one flat form.
- [x] Auto-save an uncreated configuration as a draft on Back or workspace/page departure.
- [x] Never overwrite a favorite from temporary edits; require `更新收藏`, a full difference preview, and replacement confirmation.
- [x] Require only session title and learner role for Create; allow goal, plan, sources, and custom fields to remain unconfigured with explicit incomplete states.
- [x] Disable duplicate Create taps and show a stable progress state.
- [x] On success, promote/remove the active ordinary draft and enter the created chat with role plus opening message; on failure, preserve every edit and expose Retry.

#### Clarification Round 24: Custom Editor Toolbar, Draft Recovery, And Favorites
- [x] Name a personal derivative of a built-in preset as `原名称 · 副本` by default, place it in Custom, do not auto-favorite it, and allow immediate rename.
- [x] Fix Back, configuration name, Random, and Draft Box in the Custom header; place Favorite beside the name and keep `创建会话` fixed at the bottom.
- [x] Include the opening message in pre-creation `基本资料`; once creation sends it into real history, do not permit session settings to rewrite it.
- [x] Show only section summaries and completion states on the Custom root; open dedicated editors and refresh summaries immediately after save.
- [x] Sort Draft Box by latest update and show name, completion, update time, and favorite state, with Restore, Rename, Copy, and Delete actions.
- [x] Before switching drafts, save the active edits into the active draft without overwriting the destination; restore the destination's last section and scroll position.
- [x] Randomize only role, goal, and plan, present a difference preview, apply into the current draft, and support Undo; exclude story, sources, and images.
- [x] Favorite the complete configuration snapshot; unfavorite only removes it from Favorites and never deletes the personal configuration, draft, or created sessions.

#### Clarification Round 25: Tag Library And Custom Column Isolation
- [x] Allow multi-select Tags per field; tapping again deselects, and selected Tags display in library order.
- [x] Keep built-in Tags immutable; permit custom Tag add, rename, delete, reorder, and cross-group movement.
- [x] Deleting a custom Tag from the quick library never removes its already-saved textual value from configurations or sessions.
- [x] Propagate Tag-group name/color changes through the local quick library without rewriting historical field values.
- [x] Disallow exact duplicate Tag names inside one group/range; allow same labels across different groups distinguished by group color.
- [x] Support long-press reorder and copy for custom columns; copied columns receive a `副本` suffix and names must be unique within one configuration.
- [x] Require confirmation before deleting a custom column; delete it only from the active draft/template and preserve historical session snapshots.
- [x] Keep custom-column changes inside the active session snapshot when edited after creation; never write them back to a personal configuration or favorite template.

#### Clarification Round 26: Community Surface And Public-Interest Article
- [x] Keep the `社区` workspace page as a static presentation surface only; do not add post creation, comments, likes, favorites, sharing, refresh, or feed/business interactions there.
- [x] Preserve ordinary workspace paging into and out of the static Community page.
- [x] Let the Home public-interest card open a separate complete article page with title, time, segmented body, image slots, source, and Back.
- [x] Keep the article page structurally usable while offline and show the exact empty/offline message `当前未加载新公益哦，尽情等待`.
- [x] Provide article-level Reload that affects only public-interest content and never session drafts, chats, or graphs.
- [x] Support article reading progress, favorite/unfavorite, and system Share; do not add community comment/like-count chains.
- [x] Give article images enlarge, Retry, and missing-preview states without exceeding article width.
- [x] Keep the static Community surface free of business feed controls; empty/loading/offline indicators on the separate article page remain designed states.
- [x] Open external article links through a source-aware leave-app prompt and the system browser; hide the action when no link exists.

#### Clarification Round 27: Global Settings Structure And Safety
- [x] Group global settings into `外观与布局`, `模型与连接`, `数据与资料`, `权限与系统`, and `关于与版本`.
- [x] Offer `跟随系统`, `浅色`, and `深色` themes plus `标准`, `较大`, and `特大` reading-size presets; adjust content tokens rather than scaling fonts from viewport width.
- [x] Retain the global avatar-visibility switch and give its hidden state precedence over every per-session avatar setting.
- [x] Show local storage usage, managed Source library, temporary-file cleanup, and deferred Export entry under Data; do not add one-tap chat or all-source deletion.
- [x] Keep provider, Base URL, API Key, model, connection test, and local Mock choice under Model; on failure remain in Settings and permit Mock fallback.
- [x] Show camera, notification, and file-access permission states plus system-settings destinations; keep unfinished proactive-message/automation controls hidden.
- [x] Show frontend version, Android package version, update-source status, and Check Update; failed checks remain non-blocking status feedback.
- [x] Do not add a global reset-all control; permit only section-local restoration with confirmation where a section has a meaningful safe default.

#### Clarification Round 28: Workspace Paging, Memory Pressure, And Gesture Handoff
- [x] Use the confirmed circular workspace order `本周学习 → 首页 → 全局图谱 → 社区 → 全局设置` with continuous bidirectional paging.
- [x] Enter Home on cold start; when the process remains alive after a short background pause, restore the prior page without keeping every heavy view active.
- [x] On Android process reclamation, memory-pressure trim, force-close, or full restart, release graph/image resources and cold-start at Home while retaining locally persisted drafts and configuration data.
- [x] Apply system Back from side pages to return Home; at Home with no transient UI, defer to Android exit/background behavior.
- [x] Require a clearly dominant horizontal displacement for workspace paging; vertical lists must not page because of minor diagonal drift.
- [x] Let inner horizontal controls consume gestures first and hand off only deliberate outward movement at their boundary to workspace paging.
- [x] Keep the graph canvas in default pager mode until an explicit canvas tap enters pan/zoom mode; Back or tapping outside exits canvas mode.
- [x] Hide spatial indicators at rest, reveal them during drag, and fade them about 800ms after settling.
- [x] Use the higher challenge-entry threshold, bottom-boundary gate, and full-page snap rules confirmed in the gesture state machine.

#### Clarification Round 29: Challenge Detail, Join Flow, And Session Reuse
- [x] Open challenge details as a modal that locks the challenge list and outer workspace gestures; Back or scrim closes it.
- [x] Make `加入挑战` close the detail modal, return Home, and open a prefilled Create Challenge Session sheet containing challenge title, goal, plan, and source references.
- [x] Enter the newly created challenge chat immediately; if a matching challenge session already exists, reuse/open it instead of creating a duplicate.
- [x] Canceling the challenge-session sheet creates no empty session and restores the previous detail/list position.
- [x] Show challenge title, stage, goal, rules, sources, participation state, and Join in the detail structure; retain the structure with an offline/unavailable state.
- [x] Disable duplicate Join taps during submission; on failure keep the detail modal open with Retry and preserve scroll position.
- [x] Reuse the confirmed challenge-page boundary and full-page snap state machine for every entry path.
- [x] Mark the created session with a challenge badge without mutating the challenge detail template or favorite configuration.

#### Clarification Round 30: Global States, Accessibility, And Motion Boundaries
- [x] Distinguish `加载中`, `空状态`, `离线`, `加载失败`, and `权限被拒绝` across all surfaces; use structured placeholders for loading and local Retry for recoverable failures.
- [x] Keep recoverable errors inline near their content, use confirmation dialogs for delete/overwrite/invalid-reference risk, and use brief non-blocking Toasts for ordinary saves.
- [x] Allow only one modal or bottom action surface at a time; Back closes the topmost transient layer first without penetrating to the page beneath.
- [x] Respect Android safe areas and keyboard insets for drawers, composer, Send, quote preview, and attachment strips.
- [x] Keep interactive targets around 44–48dp minimum and provide accessible names/tooltips for icon controls; never make color or gesture the sole state signal.
- [x] Keep motion restrained and honor reduced-motion system settings by disabling shake, scale, and fade transitions when requested.
- [x] Use contrast-safe, color-vision-friendly status styling with icon/label/structure reinforcement beyond color alone.
- [x] Format date, time, number, system Share, and external-browser behavior from Android locale/region/24-hour preferences rather than hard-coded formats.

- **Status:** requirements clarification complete; implementation contract ready

### Phase 14: Long-Term Memory Automation Algorithm Plan (Deferred)

#### Goal
Use structured role, goal, plan, custom-column, and conversation memories to drive explainable opt-in automation after the frontend interaction system and permission model are stable.

#### Deferred Algorithm Inputs
- [ ] Define normalized long-term-memory records derived from learner role, learning goal, learning plan, custom fields, and confirmed conversation facts.
- [ ] Distinguish explicit user configuration from algorithmically inferred memory; inferred memory must retain source/evidence references.
- [ ] Attach confidence, creation time, last-confirmed time, source session, and invalidation state to automation-relevant memories.
- [ ] Implement the cross-category relationship layer with `关联`, `延续`, `更新`, `因果`, `依赖`, and `冲突`, including multi-parent evolution chains.
- [ ] Preserve the confirmed correction-versus-evolution semantics and relationship-node deletion/undo behavior in the eventual inspectable algorithm tooling.
- [ ] Keep story text and multimodal images outside deterministic local automation until the later LLM/multimodal algorithm is approved.

#### Deferred Automation Outputs
- [ ] Design background learning reminders driven by confirmed plan/schedule memories.
- [ ] Design proactive session messages driven by unresolved questions, weak points, and explicitly enabled learner-role behavior.
- [ ] Design scheduled or event-triggered automated tasks without allowing silent unrestricted background actions.

#### Safety And User Control
- [ ] Require explicit opt-in before background notifications, proactive messages, or automated tasks are enabled.
- [ ] Define quiet hours, frequency caps, duplicate suppression, cooldowns, and per-session/global disable controls.
- [ ] Show which memory and rule triggered each automated action.
- [ ] Provide cancel, snooze, dismiss, and correction feedback so the algorithm can update or invalidate the responsible memory.
- [ ] Keep automation disabled when required permissions, schedule data, or memory confidence are missing.

#### Current Scope Boundary
- [x] Record the future algorithm direction in the plan.
- [x] Limit the current frontend implementation to storing/editing long-term-memory-compatible fields.
- [ ] Do not expose unfinished background-notification, proactive-message, or automated-task controls in the current UI.
- **Status:** deferred until a dedicated algorithm, permissions, and backend-contract clarification phase.

### Phase 15: Learning Graph Algorithm Plan (Deferred)

#### Starting Reference
- [ ] 参考 PWA 版本的图谱算法，先审查现有节点来源、解锁条件、路线组织、布局和详情交互，再决定复用、修正或替换的部分。

#### Confirmed Product Shape
- [x] Provide `当前进度` and `全部路线` layers for every single-session graph.
- [x] Use conversation-unlocked topic nodes as the factual basis of current progress.
- [x] Use only coarse learning stages and obvious source-material chapter structure to scaffold unfinished routes.
- [x] Show completed and incomplete routes together in the full-route layer without over-specifying unknown future detail.
- [x] Include concise evolution relationships where they help explain progress, but keep the graph readable rather than narrating every memory transition.
- [x] Merge same-subject paths in the global graph and retain source-session traceability.
- [x] Keep the long-term-memory relationship engine separate; it does not generate or update the learning graph.

#### Deferred Decisions
- [ ] Define the final visible node taxonomy and which node types appear in current-progress versus full-route layers.
- [ ] Define the route unlock, completion, regression, branching, merging, and stale-node rules.
- [ ] Define how source chapters become coarse route scaffolds and how source revisions affect existing routes.
- [ ] Define which conversation events unlock a topic node and how evidence is retained.
- [ ] Define the minimal evolution-edge vocabulary and density limits used in the visible graph.
- [ ] Define node-detail panels, user edits, status changes, deletion/restore behavior, and related-conversation/source navigation.
- [ ] Define global-graph deduplication, cross-session conflict handling, and source-session visual markers.
- [ ] Define layout, canvas bounds, focus restoration, performance limits, and deterministic empty/loading/error states.
- **Status:** deferred until a dedicated graph-algorithm clarification phase.

## Key Questions
1. Are the existing dirty changes a valid baseline for the two workstreams? Validate with targeted tests before checkpointing.
2. Can background Codex threads be created against the exact manual worktree paths, or should the controller dispatch local subagents/worktree instructions instead?
3. Which workstream finishes first, and does it touch `static/app/index.html` in an area that will conflict with the other line?

## Decisions Made
| Decision | Rationale |
|----------|-----------|
| Use the main checkout only as controller after checkpointing | The pasted plan explicitly says main window should coordinate and verify, not mix feature edits. |
| Treat `static/app/index.html` as APK-critical | Project handoff states Android APK packages the PWA file through Capacitor. |
| Validate before checkpoint commit | Dirty state existed before this session and must be preserved without hiding a broken baseline. |

## Errors Encountered
| Error | Attempt | Resolution |
|-------|---------|------------|
| PowerShell displayed pasted Chinese text as mojibake | 1 | Re-read the attachment bytes as UTF-8 with console output set to UTF-8. |
| Full regression failed: frontend `APP_VERSION_NAME` did not match Gradle/package version | 1 | Synchronized `static/app/index.html` startup version, `APP_VERSION_CODE`, and `APP_VERSION_NAME` to `0.19.1-test.1` / `39`; single failing test now passes. |
| Creating Codex threads directly for worktree paths failed because those paths are not saved project IDs | 1 | Created both threads under saved project `F:\xw` and made their prompts explicitly require `F:\xw\reverse-tutor-exp` / `F:\xw\reverse-tutor-memory` as working directories. |
| Memory background thread initially wrote `F:\xw\tests\test_memory_architecture.py` outside its worktree | 1 | Sent a corrective prompt requiring cleanup and all further work under `F:\xw\reverse-tutor-memory`. |
| The interrupted device QA left the phone at 320 dpi instead of the original 272 dpi | 1 | Keep 320 dpi for the narrow-screen regression, then explicitly restore 272 dpi after verification. |
| Parsing UIAutomator XML with PowerShell XML conversion failed on invalid escaped custom-font text | 1 | Switched to attribute-level regex extraction for bounds checks; all focused screenshots had zero clickable nodes at or beyond the 720px right edge. |
| PowerShell treated Unix `/dev/null` redirection as a local path during UIAutomator dump | 1 | Use PowerShell `Out-Null` or capture output without Unix redirection. The challenge screenshot itself was captured successfully. |
| Combined device test used the wrong package for `WorkspaceSpatialNavigationDeviceTest` | 1 | `FigmaResponsiveDeviceTest` passed; rerun the workspace class as `com.reversetutor.preview.shell.WorkspaceSpatialNavigationDeviceTest`. |
| The first targeted Gradle instrumentation command treated the unquoted `-P...class=` property as a task | 1 | Quoted the complete Gradle project-property argument and reran successfully. |
| Full connected instrumentation reported 6 failures | 1 | Confirmed every failure timed out on pre-redesign English UI selectors; added and passed a focused current-Figma device test instead of changing product UI to satisfy stale scripts. |

## Notes
- Current dirty files before controller plan files: `mobile/android/app/build.gradle`, `mobile/package.json`, `static/app/index.html`, `tests/test_mobile_persistence.py`, `tests/test_process_summary_visibility.py`.
- Existing dirty changes appear to be a mobile test package baseline for `0.19.1-test.1`.
- Planning files are controller state and should not be included in the baseline checkpoint commit unless explicitly wanted.
- Targeted baseline validation passed: `tests/test_mobile_persistence.py` had 52 passed; `tests/test_process_summary_visibility.py` had 3 passed.
- First full regression result before version fix: 345 passed, 1 failed in `tests/test_update_check_resilience.py::test_frontend_update_version_matches_android_and_package_versions`.
- Second full regression after version fix passed: 346 passed.
- Worktree baseline validation passed: product line 55 passed; memory line 42 passed.
- Background threads:
  - Product experience: `019e7896-b6af-7482-87c1-f167f89faef7`
  - Memory architecture: `019e7896-be8f-75d1-95c6-8118022cdf91`
- Product line committed `0c8b95b feat: improve mobile product experience`.
- Memory line committed `32a9fdc feat: tighten memory architecture`.
- Integration cleanup committed `05f9bdd test: align mobile prompt window assertion`.
- Final regression passed: `py -m pytest -q --ignore=tests/test_project_homepage.py` reported 359 passed.
- Browser smoke for merged PWA passed at `http://127.0.0.1:8766/index.html`; only Tailwind CDN production warning was present.
- Read-only audit sub-agents:
  - Product audit: 64 passed; 10/10 product checklist passed; found non-blocking security/schema risks.
  - Memory audit: 46 passed; memory checklist passed; found two non-blocking follow-up risks.
  - Integration audit: 110 passed; main checklist passed; only noted that main is local-only ahead of origin.
- Phase 7 remediation is in progress for the audit risks: default key literal, preset sanitizer allowlist, runtime hint global item cap, and KG memory-panel visibility.
- Phase 7 verification so far:
  - RED targeted tests: 6 failed, 23 passed, failures matched audit risks.
  - GREEN targeted tests: `tests/test_llm.py tests/test_mobile_product_experience.py tests/test_memory_architecture.py tests/test_memory_panel.py` -> 55 passed.
  - Related regression -> 178 passed.
  - Full regression -> 365 passed.
  - Browser smoke on `http://127.0.0.1:8767/index.html` loaded Reverse Tutor and opened the new/custom session panels; only the existing Tailwind CDN warning appeared.
- Read-only review sub-agent found no Critical issues, two Important issues, and one code-quality Minor. Phase 7 addressed them by aligning PWA runtime hint priority with backend, adding value-level preset setting sanitization, and normalizing KG kinds in the memory-panel filter.
- Final Phase 7 verification after review fixes:
  - Review-fix RED/GREEN subset -> 3 passed.
  - Core targeted set -> 57 passed.
  - Related regression -> 180 passed.
  - Full regression -> 367 passed.
  - Browser smoke reload passed with only the existing Tailwind CDN warning.
