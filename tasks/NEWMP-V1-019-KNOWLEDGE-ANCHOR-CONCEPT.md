# NEWMP-V1-019: Knowledge anchor (知识锚点) — concept & design draft

Date: 2026-09-12
Branch: newmp
Phase: **Concept + design proposal (v2)** — no entry point / UI / code until
the user approves the plan.
Feishu discussion doc: https://larkcommunity.feishu.cn/docx/BGNLdcB59oBT4CxSMWocJscAngb

## User's framing (2026-09-12, comment 7684334513253584101)

Two underlying algorithm systems, two user-visible surfaces:

- RAG (background knowledge base + retrieval) -> user-visible as the ANCHOR
  page: user uploads materials (initial upload + mid-chat upload), the app
  automatically indexes / chunks / composes / injects.
- Graph (vector graph carrying time-linear change) -> user-visible as the
  KNOWLEDGE GRAPH page. Out of scope this phase.

Top bar decisions: search stays (sessions + materials); "学习大脑" slot becomes
the anchor page entry; branch management is dropped for now — that slot is
reserved for the knowledge-graph entry later; branch gets its own layout in a
later phase.

## v2 concept

A knowledge anchor = a material "conscripted" into the AI's background
knowledge base. User uploads; the app automatically: 1) parses, 2) chunks,
3) indexes, 4) retrieves + composes the top fragments into the prompt each
turn (marked "trust the materials first").

## Design proposal (awaiting user approval)

1. Parsing v1: TXT/MD, PDF (text layer), images (vision). DOCX/PPTX/EPUB v2.
2. Chunking: natural paragraphs first; ~500-char cap per chunk; ~50-char
   overlap; each chunk records anchor id / index / source position.
3. Retrieval: v1 local keyword search (tokenize + tf scoring + fuzzy match,
   old-engine parity; fully local, explainable); v2 vector search via channel
   embedding API — behind a replaceable indexer interface.
4. Injection: per turn, retrieve top 3-5 chunks for the current user question,
   inject as "materials hit by current question (trust first)".
5. Storage: Room tables `anchor` (name/kind/imported-at/chunk-count/status)
   and `chunk` (anchor id/index/text/keywords).
6. Anchor page (replaces 学习大脑 top-bar entry): list per session window
   (name/type/chunks/imported-at), actions import / view / delete, empty state.
7. Top bar: search unchanged; 学习大脑 -> anchor page; branch management slot
   reserved for the knowledge-graph entry (phase 2).

## Old-engine parity facts (empirical, 2026-09-12)

Web engine static/app/index.html anchor system:

- Storage: IndexedDB store `anchors`, per chat window (sid); record shape
  `{ sid, kind, content, weight, created_at, ...extra }`.
- Kinds & weights: requirement 1.5 (also AI-extracted from
  `new_requirements` at 1.2), note 1.4, source 1.2, persona_change 2.6.
- Source anchors: imported PDF/DOCX/TXT/MD/HTML/PPTX/EPUB/images with stored
  text; per-turn snippet retrieval injects "当前问题命中的上传资料片段
  （优先相信）".
- Prompt injection heading: "主线锚（不可漂移）**永远不能违背**", sorted by
  weight desc via `format_anchors()`.
- Graph: `buildGraphData(masteries, ai, anchors)` — anchors, mastery, AI
  notes form the window graph.
- Deletion of a message cascades to its linked note/requirement anchors;
  source anchors survive.

This phase only ports the SOURCE anchor pipeline (RAG). The
requirement/note/persona_change kinds (mainline constraints, graph side)
are deferred with the knowledge graph phase.

## Open decisions (awaiting user)

1. v1 keyword retrieval OK (vector search in v2)?
2. v1 formats TXT/MD + PDF + images OK (DOCX/PPTX/EPUB later)?
3. Anchor page scoped to the current window first?
4. Injection cap 3-5 chunks per turn?
