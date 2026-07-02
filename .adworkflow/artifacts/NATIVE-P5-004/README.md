# NATIVE-P5-004 Evidence

Task: Implement graph edit and review parity.

Completed scope:
- Node label/status update path added through `GraphRepository.updateNode`.
- `GraphNodeStatus` now includes `NeedsReview` and `Approved`.
- Session graph detail can edit node label, mark nodes for review, approve, archive, hide, and restore when relevant.
- Global graph is explicitly read-only review.
- Graph nodes resolve `sourceMemoryId` through `MemoryItem` evidence into review cards and concrete chat/source target IDs.
- Chat/Sources receive evidence target labels when opened from graph evidence.

Verification:
- Focused graph/data/memory/app compile checks passed.
- Full verification: `.\gradlew.bat test lint :app:assembleDebug --no-daemon --stacktrace`.
- Device smoke: `Phase5GraphDeviceTest` on HMA-AL00 / Android 10.
- APK badging: `com.reversetutor.preview`.

Residual risks:
- The graph schema is still minimal and space-scoped; it does not yet persist sessionId, summary, source/message refs JSON, updatedAt, positions, or review metadata.
- Semantic fragment/card deck is represented by evidence review cards, not full legacy card browse.
- Chat/source evidence handoff displays target IDs but does not yet scroll/highlight the exact row.
- Full graph parity remains blocked until Phase 6 parity/device matrix.
