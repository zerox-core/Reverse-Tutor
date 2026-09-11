# NEWMP-V1-018: Web search toggle (channel-built-in enable_search)

Date: 2026-09-11
Branch: newmp

## Goal

Let the user turn the LLM channel's built-in web search on/off from the app
settings, so chat replies can draw on fresh web data with zero infrastructure.
User decision (2026-09-11): v1 uses the channel-side switch only
(Bailian-compatible `enable_search` request param); a self-hosted search
service is deferred until the built-in-model + subscription phase.

## Channel passthrough verification (empirical, 2026-09-11)

- Direct test request against the configured compatible-mode endpoint
  (model qwen3.7-flash) with `enable_search: true` and a question about
  September 2026 events.
- Evidence the channel actually searches: `prompt_tokens` jumped 28 -> 5014
  for the same question, and the answer contained current (Sept 2026) news
  the base model cannot know.
- The compatible interface does NOT return a search-source list / citations —
  the future "回答出处" feature will need a native channel call or an
  independent search service (deferred with the user's approval).

## Implementation

- core:llm LlmGenerationLifecycle:
  - `LlmGenerationRequest` gains `webSearchEnabled: Boolean = false`.
  - `LlmGenerationPlanner.plan` gains `webSearchEnabled` param and forwards
    it into the planned request.
  - `OpenAiCompatibleGenerationRuntime.buildPayload` appends
    `"enable_search" to true` to the request body ONLY when the flag is set
    (absent when off, so other providers see an unchanged payload).
- core:data preferences (DataStore "reverse_tutor_app_preferences"):
  - `AppPreferences.webSearchEnabled` default **false** (token budget
    protection; web search inflates prompts ~180x in the verified sample).
  - Key `web_search_enabled` registered in `persistedNames`.
  - Repository adds `currentWebSearchEnabled()` (one-shot read) and
    `setWebSearchEnabled(enabled)`.
- core:data ChatGenerationRepository: constructor gains
  `webSearchPreference: suspend () -> Boolean = { false }`; consulted per
  `generateReply` call, so flipping the toggle takes effect on the next
  reply without rebuilding the graph. `generateSessionSummary`
  deliberately stays OFF (summaries do not need fresh web data).
- app wiring HybridAppGraph: hoists a single AppPreferencesRepository and
  passes `webSearchPreference = appPreferencesRepository::currentWebSearchEnabled`.
- BackgroundGenerationRepository builds LlmGenerationRequest directly with
  the default `webSearchEnabled = false` — background ops stay offline
  (documented behavior, no change made).
- feature/settings FormalSettingsScreen: new "联网搜索" row in the
  模型与连接 section (Public icon, toggle), `ToggleWebSearch` action
  wired to `onWebSearchChanged(!state.webSearchEnabled)`.
- app shell AppShell: passes `webSearchEnabled` down and persists changes
  through `appPreferencesRepository.setWebSearchEnabled` in a coroutine.

## Files changed

- core/llm LlmGenerationLifecycle.kt
- core/data ChatGenerationRepository.kt, AppPreferences.kt,
  AppPreferenceKeys.kt, AppPreferencesRepository.kt, DataModule.kt
- app HybridAppGraph.kt, AppShell.kt
- feature/settings FormalSettingsScreen.kt

## Tests

- LlmGenerationLifecycleTest +2 (12/12):
  `openAiPayloadCarriesEnableSearchOnlyWhenWebSearchEnabled` (enable_search
  present=true when on, absent when off),
  `plannerPropagatesWebSearchFlagIntoPlannedRequest`.
- FormalSettingsScreenModelTest: contract updated for the new row + action.
- AppPreferencesPolicyTest: default false + key in persistedNames.
- Full-project gradle test green (BUILD SUCCESSFUL, 465 tasks).

## Not done / follow-ups

- No emulator end-to-end run of toggle -> logged payload this round; unit
  tests cover payload shaping and planner propagation, channel passthrough
  was verified live. 建议真机/模拟器实际体验确认 UI 开关生效。
- 回答出处 (answer citations) remains blocked on the compatible interface
  (no source list returned); revisit at the deferred search-service phase.
- Next feature in the queue: 知识锚点 (old-engine parity check first).

## Follow-up 2026-09-11: toggle relocated above the chat input box

用户拍板（评论 7684290720114428898）：联网开关从设置页挪到聊天输入框正上方，
默认关闭、仅手动开启，开启前弹额度消耗确认框，设置页入口撤掉，其余不变。

Changes:

- ReverseTeachingChatScreen.kt:
  - new `WebSearchToggle` pill composable (globe icon + label, states
    联网搜索 / 联网搜索·已开启) rendered directly above ReverseTeachingComposer;
  - tapping when OFF opens an AlertDialog warning 联网搜索会大幅增加额度消耗
    (每次提问的消耗可能增加到原来的几十倍)，确认后才真正开启；tapping when ON
    closes immediately, no dialog;
  - new params webSearchEnabled / onWebSearchChange threaded through
    ChatRoute -> ChatScreen -> ReverseTeachingChatScreen.
- AppShell.kt: settings-page wiring removed; ChatRoute call site now feeds
  appPreferences.webSearchEnabled and persists via
  AppPreferencesRepository.setWebSearchEnabled.
- FormalSettingsScreen.kt: 联网搜索 row fully reverted (imports, UiState field,
  from() factory, both signature params, row spec, enum entry, icon map,
  callbacks map, forwarding line). Storage plumbing untouched.
- FormalSettingsScreenModelTest.kt: reverted expectations for the removed row.

Verification: settings file greps 0 WebSearch refs; full-project gradle test
BUILD SUCCESSFUL (465 tasks, 465 actionable: 42 executed).

Next feature proceeds with web search OFF by default: 知识锚点.
