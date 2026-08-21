# NATIVE-P2-007 API Fact Map

> Authority: source code on `F:\xw\reverse-tutor-newmp` (`newmp` branch), read
> 2026-08-21. Source is the sole authority for signatures — no guessing from
> docs. This map is the basis for the adapter/wiring layer added in
> NATIVE-P2-007.

## 1. Scope

This document inventories the **real public signatures** of the existing
(rozen) repositories that the non-frozen adapter/wiring layer must delegate to,
the domain ports each adapter implements, and the honest capability gaps that
the wiring does NOT fake.

Frozen (must not be modified): `core/model`, `core/protocol`, `core/llm`,
`core/data/*Repository`, `core/data/local` (Room schema/DAO/migration),
`core/data/preferences`, `SecretStore`, any Compose UI/graph/PWA/Capacitor.

## 2. ChatGenerationRepository (rozen · core/data/llm)

```
suspend fun generateReply(
    input: ChatGenerationInput,
    nowEpochMillis: Long,
    isTokenCurrent: (LlmGenerationToken) -> Boolean,
    canPersistResult: suspend () -> Boolean = { true }
): ChatGenerationOutcome
```

- **Assistant persistence is owned by the frozen repo**: `generateReply` saves
  the assistant message via `messageRepository.saveMessage(...)` **inside** the
  call, gated by `canPersistResult`. The non-frozen adapter does NOT
  re-persist (see §4).
- `isTokenCurrent` is a permissive pass in the wiring — real staleness is
  enforced through the `canPersistResult` guard the coordinator supplies
  (built from the persistence port).

### ChatGenerationInput (rozen · core/data/llm)

```
data class ChatGenerationInput(
    val sessionId: String,
    val userMessageId: String,
    val userText: String,
    val token: LlmGenerationToken,
    val modelBindingId: String? = null,
    val capabilities: … = null,
    val quoteExcerpt: String? = null,
    val imageAttachments: List<…> = emptyList(),
    val contextEvidence: List<LlmContextEvidence> = emptyList()
)
```

**No field for `SessionPolicyOutput`** (action / evaluation / processSummary).
The adapter maps context-evidence strings to `LlmContextEvidence` (body = real
text) but **cannot** inject policy. See `capability-requests/P3-session-policy-context.md`.

### ChatGenerationOutcome (rozen · core/data/llm)

```
sealed interface ChatGenerationOutcome {
    data class Generated(val assistantMessageId: String)
    data class ProviderFailed(val message: String)
    object NoModelConfigured
    object UnsupportedVision
    object BlankPrompt
    object Stale
}
```

`ProviderFailed.message` may carry provider text (URL/key/Authorization). The
adapter routes it through `SessionTurnContracts.safeGenerationFailureCode(...)`,
which discards the raw message and returns a constant safe code — no leak.

### LlmGenerationToken / LlmContextEvidence (rozen · core/llm)

```
@JvmInline value class LlmGenerationToken(val value: String)
data class LlmContextEvidence(
    val id: String, val title: String, val body: String,
    val kind: String,
    val sourceMessageId: String? = null,
    val sourceId: String? = null
)
```

## 3. Port → Repository mapping

| Domain port | Adapter | Frozen source (seam/delegate) | Notes |
|---|---|---|---|
| `ChatGenerationPort` | `ChatGenerationPortAdapter` | `ChatGenerationRepository.generateReply` | Functional-seam `generate`; maps `GenerationRequest`→`ChatGenerationInput` (no policy); maps outcome→`GenerationOutcome`; `readAssistantText` reads back persisted assistant via `MessageRepository.listMessages`. |
| `SessionTurnPersistencePort` | `SessionTurnPersistencePortAdapter` | 5 functional seams | `saveUserMessage`→`MessageRepository.sendUserMessage`; `acceptAssistantResult` = confirmation only (frozen repo already persisted); `isSessionDeleted`→`ConversationRunRepository.isSessionDeleted`; `isTokenCurrent` permissive; `isTurnCompleted`→`findLatestRun(turnId)?.isTerminal`. |
| `MessageContextPort` | `MessageContextPortAdapter` | `MessageRepository.listMessages(sessionId)` | `.takeLast(limit)` → `ContextMessage(role.name.lowercase())`. |
| `MemoryContextPort` | `MemoryContextPortAdapter` | `MemoryRepository.snapshot(spaceId).items` | Filters out `MemoryItemKind.Error`; space-scoped (see §5). |
| `ErrorContextPort` | `ErrorContextPortAdapter` | `MemoryRepository.snapshot(spaceId).errors` | Filters out resolved; `code ?: title`. |
| `GraphContextPort` | `GraphContextPortAdapter` | `GraphRepository.snapshot(GraphScope.Session(sessionId))` | `Ready.snapshot.nodes`; filters by `GraphNodeKind.Requirement` / `GraphNodeStatus.NeedsReview`. |
| `SourceContextPort` | `SourceContextPortAdapter` | `SourceRepository.listSourcesWithChunks(spaceId)` | `entry.source` + `entry.chunks.firstOrNull()?.text`; space-scoped. |
| `LearningOverviewSessionPort` | `LearningOverviewSessionPortAdapter` | `SessionRepository.listSessions(spaceId)` | Counts non-archived `TutorSession`. |
| `LearningOverviewPlanPort` | `LearningOverviewPlanPortAdapter` | `LearningRepositoryImpl.listTasks(spaceId)` | Filters out `Cancelled`; maps `StudyPlanTask`→`TodayPlanTask`. |
| `LearningOverviewProgressPort` | `LearningOverviewProgressPortAdapter` | — | Returns default `LearningProgressContract()` (gap, §6). |
| `LearningOverviewThreadPort` | `LearningOverviewThreadPortAdapter` | — | Returns `emptyList()` (gap, §6). |
| `LearningOverviewWeakPointPort` | `LearningOverviewWeakPointPortAdapter` | `MemoryRepository.snapshot(spaceId).errors` | groupBy `code ?: title` → `WeakPointContract`. |
| `LearningOverviewTokenPort` | `LearningOverviewTokenPortAdapter` | — | Returns default `TokenUsageOverviewContract()` (gap, §6). |

## 4. Assistant persistence honesty

The frozen `ChatGenerationRepository` owns assistant persistence: it saves the
assistant message inside `generateReply`, gated by `canPersistResult`. The
non-frozen `SessionTurnPersistencePortAdapter.acceptAssistantResult` is a
**confirmation only** (returns `true`) — re-saving would duplicate frozen
persistence semantics. The coordinator's `canPersistResult` guard
(`isTokenCurrent(token) && !isSessionDeleted(sessionId)`) is the real
staleness/deletion gate passed through to the frozen repo. (Final-report §7.2.)

## 5. Scoping caveats (honest, not faked)

- `MemoryRepository.snapshot(spaceId)` and `SourceRepository.listSourcesWithChunks(spaceId)`
  are **space-scoped**, not session-scoped. The adapters faithfully return the
  space's data and do not fabricate session-level filtering.
- `GraphRepository.snapshot(GraphScope.Session(sessionId))` IS session-scoped
  (resolves session → space → session nodes).
- `MessageRepository.listMessages(sessionId)` IS session-scoped.

## 6. Capability gaps (documented, not faked)

The frozen layer currently exposes:
- **No weekly-mainline read model** — `LearningInsightRepository.findWeeklySummary`
  requires `weekStartEpochMillis`/`sourceRevision`/`generatorVersion`; it is a
  point lookup, not a "list weekly threads" API. → `LearningOverviewThreadPortAdapter`
  returns `emptyList()`.
- **No mastery-progress aggregation** — no read API aggregates mastered/total
  knowledge points. → `LearningOverviewProgressPortAdapter` returns default.
- **No token-usage aggregate read** — `TokenUsageRepository.saveUsage` is
  write-only; no read/aggregate. → `LearningOverviewTokenPortAdapter` returns
  default.

These adapters return empty/default; the coordinator's `safeRead` turns that
into explicit `NoData` / empty categories, never a failure.

## 7. Composition root

`SessionConversationAssembly` (package `com.reversetutor.preview.wiring.session`)
is a Compose-free entry point:

```
UI ─► SessionConversationAssembly.runTurn/overview
       ─► SessionConversationFacade ─► ConversationSessionCoordinator
            ─► ConversationContextAssembler → context port adapters → Repositories
            ─► ChatGenerationPortAdapter   → ChatGenerationRepository
            ─► SessionTurnPersistencePortAdapter → MessageRepository / run repo
```

UI may **only** consume `SessionConversationContract` and `LearningOverviewContract`.
It must not reach DAOs, Entities, `ReverseTutorDatabase`, `SecretStore`, or
protocol DTOs.

## 8. Testability note

The context/overview adapters delegate to concrete (final, Room-coupled)
repositories and cannot be mocked without a mocking library. JVM tests
therefore exercise the adapter semantics at two honest levels:
- `ChatGenerationPortAdapter` / `SessionTurnPersistencePortAdapter` — tested
  via functional seams (controlled fakes).
- `ConversationContextAssembler` — tested with fake port implementations
  (interfaces) covering the empty-data / failure-distinction semantics
  (scenario 5).
