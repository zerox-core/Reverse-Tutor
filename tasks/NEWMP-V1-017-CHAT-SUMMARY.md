# NEWMP-V1-017: Chat summary (early-history compression)

Date: 2026-09-11
Branch: newmp

## Goal

Old-main parity for the legacy engine's `maybe_summarize` early-dialogue
compression: once a conversation grows past a threshold, compress the early
messages into an AI-generated digest so the model keeps long-session context
without an unbounded prompt.

## Old behavior (engine.py maybe_summarize, verified)

- Counts user+assistant messages after the last summary cutoff.
- Under SUMMARY_THRESHOLD (30): skip.
- to_compress = everything except the most recent SUMMARY_KEEP_RECENT (12).
- LLM call with the SUMMARY_SYSTEM persona prompt; previous summary merged
  ("此前已有摘要，请整合、不要丢失老要点：... 新增对话：...").
- Stores summary_text + summarized_until_message_id + summarized_count
  (per-batch, NOT cumulative: engine.py L1145 stores len(to_compress)).
- Digest injected into the prompt as early-history summary.

## Implementation

- core:domain ConversationContextContracts: `earlyHistoryDigest` defaulted
  field + `SessionDigestContextPort` interface.
- core:domain ConversationContextAssembler: optional `digestPort`,
  `digestCap` (1200), digest read through `safeReadValue` degradation
  (failure -> warning + empty digest).
- core:data ChatGenerationRepository (frozen layer, one-off user-approved
  change): `generateSessionSummary(sessionId, promptText)` — persona-free
  summary entry point; resolves the session's execution profile, plans
  through LlmGenerationPlanner with a dedicated token (`summary-...`),
  non-streaming, no assistant message persisted, no chat-record writes.
  New `SessionSummaryOutcome` (Generated / ProviderFailed /
  NoModelConfigured / UnsupportedVision / BlankPrompt).
- app wiring: `SessionSummarizer` (pure Kotlin; threshold semantics
  identical to engine.py: 30/12, merge, missing-cutoff fallback to whole
  history), `SharedPreferencesSessionSummaryStore` (implements the new
  port so the assembler consumes it directly), triggered in
  SessionConversationAssembly.assembleContext before assembling.
- SessionPolicyInputMapper: digest emitted FIRST as Summary-kind evidence
  ("（已压缩的早期对话，其后为最近原文）" + digest).
- Session deletion clears the stored summary
  (HybridFrontendPortAdapters clearSessionSummary).

## Deviations from old (documented)

- Summary output is direct markdown bullets, not the old JSON
  {"summary": ...} envelope (the old parser dropped non-JSON failures).
- Provider failure is NOT stored as the summary text — the old engine
  stored the failure text as the summary (old defect, not replicated).
- Summarizer exceptions are swallowed (maybeSummarize never throws).
- No force-summarize endpoint (old server.py:369) — threshold trigger only.
- Only the production prepare path (BackgroundTurnPreparationCoordinator
  -> assembleContext) triggers summarization; the legacy executeTurn path
  does not.

## Tests

- ChatGenerationRepositoryTest 17/17 (3 new: summary generation without
  persisting an assistant message, no-model-configured, blank prompt).
- ConversationContextAssemblerTest 16/16 (2 new: digest port supply with
  cap, port failure degrades to warning + empty digest).
- SessionSummarizerTest 8/8 (new: threshold, recent-12 keep, previous
  summary merge, missing cutoff fallback, provider failure keeps old
  record, list failure no-throw, non-chat roles don't count, cap).
- SessionPolicyInputMapperTest 10/10 (1 new: digest emitted first).
- Full-project gradle test green (BUILD SUCCESSFUL).
