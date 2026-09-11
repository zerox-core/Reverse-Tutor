# NEWMP-V1-019: Knowledge anchor (知识锚点) — concept definition (discussion draft)

Date: 2026-09-12
Branch: newmp
Phase: **Concept only** (per user instruction: no entry point / UI / code yet)
Feishu discussion doc: https://larkcommunity.feishu.cn/docx/BGNLdcB59oBT4CxSMWocJscAngb

## Goal

Define what a "knowledge anchor" (知识锚点) IS, before building anything.
User decision (2026-09-12 comment): start the knowledge-anchor feature, but do
NOT build the entry point first — first make "what this thing is" clear for
everyone.

## Concept (v1 draft)

A knowledge anchor is a piece of knowledge or agreement "pinned" out of the
chat: it has a name, a source, and a state. Once pinned, the AI student must
honor it in every later turn — it cannot drift.

Fields of an anchor:

- name: what this knowledge is called (e.g. "增函数的判定")
- content: the distilled one-line definition/agreement/conclusion (not the raw chat text)
- source: which message or material it came from (jumpable)
- kind: knowledge point / agreement (teacher's rule) / material / note
- status: active / mastered / voided (corrected or overturned by the teacher)
- weight: importance — controls its ranking when injected into the prompt

Producers: AI auto-pins (old engine did this via per-turn `anchor_updates`,
e.g. new requirements spoken mid-chat become "requirement" anchors) and the
user manually pins.

Consumers: prompt injection each turn (marked non-driftable, sorted by weight),
review/due reminders, knowledge-graph nodes, mastery linkage (anchor = what
the knowledge IS; mastery ledger = how well it is learned).

Explicit non-goals: an anchor is not the chat log, not a mastery record, not a
bookmark/favorite (strength differs: an anchor must be obeyed by the AI).

## Old-engine parity findings (empirical, 2026-09-12)

Web engine static/app/index.html already has an anchor system. Facts:

- Storage: browser IndexedDB store `anchors`, keyed per chat window (sid).
- Record shape: `{ sid, kind, content, weight, created_at, ...extra }`.
- Kinds & weights: requirement 1.5 (goals/constraints/mainline, manual save or
  AI-extracted `new_requirements` at 1.2), note 1.4 (随笔 tied to a message),
  source 1.2 (imported PDF/DOCX/TXT/MD/HTML/PPTX/EPUB/images, with per-turn
  snippet retrieval), persona_change 2.6.
- Injection: `format_anchors()` renders them into the system prompt under the
  heading "主线锚（不可漂移）**永远不能违背**", sorted by weight desc; empty
  state says "无主线锚，按通用策略走".
- AI automation: every turn's JSON contract includes `anchor_updates: []`;
  the model proposes new requirement anchors from the teacher's speech.
- Graph: `buildGraphData(masteries, ai, anchors)` — anchors, mastery and AI
  notes together form the window graph; source anchors and notes become nodes.
- Deletion: deleting a message also deletes note/requirement anchors linked to
  it (`note_message_id` / `source_message_id` / `source_turn_id`); source-kind
  anchors survive.

## Open decisions (awaiting user, do not implement until answered)

1. Scope: only "knowledge point" anchors, or full old-engine set
   (agreement/material/note/persona_change)?
2. First producer: AI auto-pin or user manual pin?
3. Mastery linkage: anchor name strictly equal to the mastery-ledger
   knowledge-point name, or loosely linked?
