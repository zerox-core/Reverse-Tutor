# Context Hub Route Design

## Decision

The session-level Context Hub is the single evidence entry point from Chat. It presents the existing overview, graph, anchors, notes, errors, and session-settings sections. The session-world-tree route remains available for graph-specific deep links; it is not the Chat Context Hub destination.

## Navigation

`Chat -> ContextHub` keeps Chat in the back stack, so system back returns to the active Chat instead of Sessions. The Context Hub's existing actions continue to open Chat, Sources, Session Settings, and Global Graph through the app shell. No Room schema, repository API, protocol, LLM, or PWA code changes are part of this work.

## Verification

Add a navigation-state regression test for `Chat -> ContextHub -> back -> Chat`. Replace the stale Phase 5 device route checks, which assert retired English placeholder UI, with checks against the current Chinese Context Hub contract. Keep graph, memory, and source coverage as separate tests; do not delete behavior checks merely to make the suite pass.
