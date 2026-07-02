# NATIVE-P5-007 Evidence

Task: Integrate memory and source context into chat turn protocol.

Completed scope:
- Added `LlmContextEvidence` to native LLM request planning and provider payload construction.
- Chat generation can pass bounded memory/source evidence to the planner.
- Assistant replies append a visible `Sources:` footer when evidence was provided.
- Chat context evidence builder filters by user text relevance, skips queued/unsupported/no-chunk sources, caps evidence to six items, truncates long bodies, and gracefully returns empty evidence when repositories are absent or fail.
- Source/memory context injection uses native repositories only; Python is not required.

Verification:
- Focused tests passed for `:core:llm`, `:core:data`, `:feature:chat`, and app Kotlin compile.
- Full verification passed: `.\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace`.
- APK badging confirmed package `com.reversetutor.preview`.

Residual risks:
- The `Sources:` footer represents context provided to the model, not strict model-emitted citation IDs.
- Exact clickable citations, row highlighting, and legacy cited_chunk_ids whitelist behavior remain follow-up work.
- Complex parser/image/PDF sources without text chunks are skipped until parser/vision extraction lands.
