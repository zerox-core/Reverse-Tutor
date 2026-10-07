# Findings & Decisions

## Requirements
- Use the pasted plan as the controller workflow.
- Split later work into two isolated lines: product experience and memory architecture.
- Main controller should maintain plan, delegate work, validate, merge in order, and avoid direct large feature edits.
- Do not push, tag, release, or build APK unless explicitly requested.
- Any APK/mobile behavior must update `static/app/index.html`.

## Research Findings
- Repository root is `F:\xw\reverse-tutor`.
- Current branch is `main`.
- `git rev-parse --git-dir` and `--git-common-dir` both returned `.git`, so the main checkout is not already a linked worktree.
- Existing dirty baseline touches five files: mobile version metadata, `static/app/index.html`, and two tests.
- Existing dirty baseline adds visible thinking summary/dots, queued user message ordering fixes, graph fullscreen styling, and process-node filtering in mobile graph behavior.
- `AGENTS.md` and `docs/CODEX_HANDOFF.md` emphasize dual implementation: Python backend for tests/API and `static/app/index.html` for APK/PWA behavior.
- Full regression exposed one baseline issue: Android/package versions were `0.19.1-test.1` / code `39`, but `static/app/index.html` still advertised `0.19.0` / code `38`.

## Technical Decisions
| Decision | Rationale |
|----------|-----------|
| Keep controller plan files separate from baseline checkpoint | They describe this coordination session and were not part of the pre-existing mobile baseline. |
| Run mobile persistence and process summary visibility tests first | These are the targeted tests named by the pasted product workstream and directly cover the dirty baseline. |
| Use manual git worktrees if checkpoint succeeds | The user-provided plan specifies exact paths and branch names. |

## Issues Encountered
| Issue | Resolution |
|-------|------------|
| Pasted text initially rendered as mojibake in PowerShell | Re-read with UTF-8 byte decoding. |
| Version metadata mismatch across Android/package/PWA | Synchronized the PWA constants and startup text in `static/app/index.html`. |
| Product and memory both touched `static/app/index.html` | Git auto-merged cleanly; controller verified the combined prompt, KG filtering, and UI entry points. |
| Memory branch preserved an old `slice(-30)` test by adding a frontend comment | Controller removed the comment and updated the old test to assert the new `RECENT_PROMPT_MESSAGE_LIMIT = 12` behavior. |

## Integration Findings
- Main now contains product commit `0c8b95b`, memory merge commit `62905c2`, and integration cleanup commit `05f9bdd`.
- Product line changed mobile/PWA product experience in `static/app/index.html` and added `tests/test_mobile_product_experience.py`.
- Memory line changed backend memory/KG behavior in `db.py`, `engine.py`, `kg_extractor.py`, `static/app/index.html`, and added `tests/test_memory_architecture.py`.
- Final regression passed with 359 tests after both lines were merged.
- Browser smoke of the merged PWA found no relevant app console errors; the only warning was the existing Tailwind CDN production warning.
- APK was not built, pushed, tagged, or released.

## Read-Only Audit Findings
- Product worktree audit passed all 10 original product checklist items and its targeted tests (`64 passed`).
- Memory worktree audit passed the original memory checklist and its targeted tests (`46 passed`); no mistaken `F:\xw\tests\test_memory_architecture.py` remains.
- Main integration audit passed its checklist and targeted tests (`110 passed`); product and memory markers are both present in `static/app/index.html`.
- Product audit found a client-readable default LLM API-key-like literal in `static/app/index.html`; this is a delivery/security risk even if not introduced by the product commit.
- Product audit found preset import sanitation is shallow blocklist-based rather than strict allowlist/schema-based.
- Memory audit found KG data is returned by memory API but not separately categorized in the front-end memory panel.
- Memory audit found `RUNTIME_MEMORY_HINT_MAX_ITEMS` is not enforced globally, although `RUNTIME_MEMORY_HINT_MAX_CHARS` still bounds final prompt size.
- Integration audit notes main remains local-only (`ahead 26`) because push was intentionally not performed.

## Phase 7 Remediation Findings
- The client-readable default GLM key was removed from `static/app/index.html`; the backend `llm.py` default free-provider key was also removed so free mode only activates when `FREE_LLM_API_KEY` is explicitly set.
- Preset import now uses an allowlist sanitizer instead of copying raw JSON then deleting known-dangerous fields.
- Imported preset settings are reduced to known strategy keys before `ENGINE.normalizeStrategySettings`, preventing nested unknown sensitive fields from surviving inside settings.
- Imported external resources are limited to sanitized HTTP(S) `{title, url}` links.
- Runtime memory hints now count all hint item lines against `RUNTIME_MEMORY_HINT_MAX_ITEMS` in both backend and PWA implementations, while preserving the existing character cap.
- The PWA memory panel now includes a `kg_nodes` tab and filters to semantic KG node kinds before rendering.
- Current verification evidence: 55 targeted tests passed, 178 related tests passed, full regression passed with 365 tests, key-pattern grep found no matches, and browser smoke found no app errors.
- Read-only review found no Critical issues. Its Important findings were addressed:
  - PWA runtime memory hint order now matches backend priority under the global item cap: preset/profile traits/preferences, then mastery/error memory, then related KG context and prerequisite gaps.
  - Imported preset settings now use value-level validation for enums, booleans, bounded integers, and bounded blacklist entries before normalization.
- The review Minor about KG kind variants was addressed by normalizing memory-panel KG kinds before filtering.
- Final verification after review fixes: 57 targeted tests passed, 180 related tests passed, full regression passed with 367 tests, key-pattern grep found no matches, and browser smoke reload found no app errors.

## Resources
- `F:\xw\reverse-tutor\AGENTS.md`
- `F:\xw\reverse-tutor\docs\CODEX_HANDOFF.md`
- `F:\CodexHome\attachments\41d6bab0-85c1-4d27-9b9d-ab512af642b9\pasted-text.txt`

## Visual/Browser Findings
- No browser or image findings yet.

## Native Visual Baseline Findings (2026-07-18)
- User feedback: system fonts are allowed, but the native app still needs explicit baseline typography and layout rules. Do not treat bundled fonts as mandatory; control size, line height, weight, spacing, and text roles first.
- User feedback: several panels are too plain. Weak hairline borders and insufficient elevation reduce grouping and perceived hierarchy; audit restrained shadow, surface separation, selected/disabled/loading states, and card depth.
- Challenge detail is a visual regression candidate. Preserve the approved sheet background treatment instead of replacing it with a flat generic surface.
- External reference screenshots are inspiration for type hierarchy, list density, icon alignment, and panel depth; formal Figma remains the product source of truth.
- Current device connected: Huawei HMA-AL00; physical 1080x2244, display override 720x1496, density override 272 dpi.
- Fresh home UI hierarchy: the visible public-content card bounds `[56,215][665,416]` is exposed as `clickable=false, enabled=false` because the current offline state makes `canOpen=false`; the defect is that the disabled state is not visually distinct enough from an actionable card.
- Fresh challenge-detail screenshot: the fixed bottom join CTA covers the lower portion of the third rule card; the sheet has no top drag handle and the hero uses a flat light-blue surface.
- Fresh new-session sheet: the three mode cards are rendered as selection controls, but tapping the second or third card produces no state change. `NewSessionModeCard` has no `onClick` or selected-state owner, so this is a confirmed missing interaction (or a scope mismatch that must be made visually explicit).
- Fresh settings/LLM audit: the visible `+` add-configuration controls are wired to `onClick = {}` in `FormalLlmConfigurationScreen.kt`; tapping either the circular plus or the bordered add row leaves the screen unchanged. Settings toggles are also rendered through `FormalStaticToggle`, so they currently communicate state but do not expose a state-changing callback.
- Tapping the visible "挑战任务提醒" toggle on the Huawei device leaves it enabled and unchanged, confirming the settings switch is currently a static visual state rather than a working preference control.
- Weekly dashboard and settings are structurally close to the supplied references, but their 1dp borders and low/no elevation make sections read flatter than the reference hierarchy.
- Diagnostics page is close to the supplied reference, but the middle runtime metric wraps awkwardly inside its narrow column (`0 个会话 · 0 条消息`), breaking the three-column alignment.
- Diagnostics report generation and Android file export are functional on the Huawei device; the export action opened the system file picker with the expected diagnostics filename.
- Static code scan found 15 non-preview `onClick = {}` handlers across active/legacy native production surfaces plus static settings/update toggle components. Each must be mapped to an active route before implementation; not every no-op is automatically in the approved V1 scope.
- Current-Figma device flow passed on Huawei HMA-AL00. Workspace spatial navigation then passed all 9 focused device tests when rerun with the correct package name.
- No production UI changes made during the classification pass.

## Native Device QA Findings (2026-07-11)
- Connected device: Huawei HMA-AL00, Android 10, physical 1080x2244.
- The interrupted run left an override of 720x1496 at 320 dpi, producing a 360dp logical width; the prior user configuration was 720x1496 at 272 dpi, about 423dp wide.
- The user explicitly requested preserving the system-selected font. Do not force a bundled or generic font family.
- At 360dp, the session-home top-right "新建" pill is visibly clipped. UI hierarchy bounds confirm its fixed 390dp placement extends past the 360dp viewport.
- Session cards use fixed `358.dp` widths at `x = 16.dp`, also extending 14dp past a 360dp viewport.
- Chat uses fixed absolute bubble targets and a `386.dp` composer, so it needs width-aware constraints even though IME padding already keeps the composer above the keyboard.
- New-session custom mode is local composable state. The app-level BackHandler cannot see it, so system Back skips the template page unless the route handles Back while `customMode` is true.
- The responsive fix passed manual screenshots at 360dp and at the restored approximately 423dp width; clickable UI hierarchy bounds did not reach or exceed the 720px right edge in the focused captures.
- Long-list scrolling produced 518 rendered frames with 0 janky frames and 5/10/11/13ms p50/p90/p95/p99 timings.
- The historical connected test suite is stale after the Figma redesign: all six tests still begin with English selectors such as `Sessions`, `New session`, `Open`, or `Sources`.
- A new focused current-Figma device test passes the home -> chat -> home -> new session -> custom session -> system Back -> template flow.

## Mobile Integration Contract Findings (2026-07-13)
- The user confirmed `mobile-native` + FastAPI + Room as the only new integration mainline; PWA/Capacitor is migration-only.
- Existing Android and FastAPI sync adapters translate between different shapes. Canonical V1 now freezes `envelopeId + entityId + accepted` for push item results.
- The approved custom world-tree UI cannot be represented safely by the legacy `role/goal/profileText` creation input; V1 adds a versioned local world-tree contract and Repository boundary.
- Student messages, world trees, source bodies, graphs, provider metadata, and secrets remain outside online sync by default.
- Community APIs are intentionally not frozen until the community page requirements and Figma design are approved.
- Contract validation passed for 12 HTTP paths, 24 OpenAPI schemas, 9 online fixtures, and a 7-section world-tree fixture.

## Sync And WorldTree Implementation Findings (2026-07-13)
- FastAPI and Android response decoding were already canonicalized by the parallel backend line; Android request encoding still omitted `envelopeId`, which would have caused FastAPI validation failure.
- WorldTree domain models and the payload codec were already present from the parallel data line. This pass completed the repository contract, Room entities, DAO, DataModule wiring, migration, and tests.
- Existing schema policy requires child user-data entities to carry `spaceId`, so both world-tree sections and source cross-references include it explicitly.
- The connected Huawei device was not available. Instrumentation sources compile, and migration SQL was validated against generated Room schema 4 with SQLite, but the on-device migration/helper tests remain a follow-up.
- Core model/domain/remote/data JVM tests and core data/remote lint passed.
