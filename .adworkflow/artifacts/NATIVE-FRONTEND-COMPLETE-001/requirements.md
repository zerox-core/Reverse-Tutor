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

